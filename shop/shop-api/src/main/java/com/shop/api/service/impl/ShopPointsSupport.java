package com.shop.api.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.entity.SysConfig;
import com.shop.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 积分的口径参数（1 元值多少分、积分几天过期、签到给多少分）和 分 ↔ 元 的折算，都在这一处（住在 shop-api）。
 *
 * 数存在 sys_config 的 points_config 那一行里，值是一段 JSON 数组、按第一格 name 分档，本类认 amount / signin 两档：
 *   name = amount 那一档：points_exchange = 100 → 100 分抵 1 元（也就是 1 元值 100 分）；expire_day = 365 → 购物送的分自获得起 365 天过期
 *   name = signin 那一档：points_exchange = 10  → 签到一次给 10 分（这一格里那一格装的是产出分，不是汇率 —— 键名跟着 amount 复用，含义按档走）；expire_day 同理是这批分的有效期
 * 这一档里还可以挂一段 bonus 数组 = 累计签到的达档奖励：[{"days":7,"points":20},…] → 累计签到满 7 天的那一次多给 20 分
 * （days 是「累计签到天数」不是「连续天数」—— 连续那条最多报到 7，30/90/180/365 四档在连续口径下永远够不到，
 * 而累计就是这张表上这个人名下的行数，补一条历史行也算一天，见 ShopSigninServiceImpl#write）
 * 后台「系统管理 → 参数管理」那一页改的就是这一格，运营随时会改，所以这一头每次都是现读库。
 * 不走 shop-admin 的 SysConfigService：shop-api 只依赖 shop-commom 摸不到它，
 * 而它那份还带 2 天 Redis 缓存、页面上改完值不会立刻生效 —— 汇率要 2 天多才作用到新单，
 * 买家在结算页看到的钱和提交时收的钱就不是同一个式子了。
 *
 * 读不出来、或者读出来是不认的值（空、0、负数、json 拆不开、整档缺失、bonus 那一段没挂），一律退回默认
 * 100 / 365 / 10 和默认那五档阶梯而不是报错：
 * 汇率填错顶多是抵扣比例不对，把整条下单链路拒掉才是真事故；这种脏数据该由参数管理那一页拦，
 * 不该由买家提交订单那一步拦。
 *
 * 折算为什么也放在这儿：同一份 used_points 要被三处折成钱 —— 下单算抵扣、行实付
 *（退款金额和出库成本都读它，见 {@link ShopOrderSupport#paidAmount}）、还有试算报给买家的那一份。
 * 三份必须出自同一个式子，抄两遍就会出现"结算页说抵 3 元、退款只退 2.99"。
 */
@Component
@RequiredArgsConstructor
public class ShopPointsSupport {

    private final SysConfigMapper sysConfigMapper;

    /** sys_config 里那一行的 key（同一个 key 只能有一行，多档配置并成数组、不另起 key） */
    private static final String CONFIG_KEY = "points_config";

    /** 数组里本类要的那两档：amount 折钱、signin 签到给分 */
    private static final String GROUP_AMOUNT = "amount";

    private static final String GROUP_SIGNIN = "signin";

    private static final String KEY_EXCHANGE = "points_exchange";

    private static final String KEY_EXPIRE_DAY = "expire_day";

    /** 兜底汇率：1 元 = 100 分（和建表脚本里那条初始配置一致） */
    private static final BigDecimal DEFAULT_EXCHANGE = new BigDecimal("100");

    /** 兜底有效期：365 天 */
    private static final int DEFAULT_EXPIRE_DAY = 365;

    /** 兜底签到分：一次 10 分（signin 那一档整档没了也照这个给，别让签到动作因为配置读不出来而失败） */
    private static final int DEFAULT_SIGNIN_POINTS = 10;

    /** signin 那一档里的达档奖励那一段（值本身是数组，和上面两个平铺键不同形，由 bonusTiers 单独拆） */
    private static final String KEY_BONUS = "bonus";

    /** 阶梯里每一档的两格 */
    private static final String BONUS_DAYS = "days";

    private static final String BONUS_POINTS = "points";

    /**
     * 兜底阶梯：累计签到满 7 / 30 / 90 / 180 / 365 天，那一次各多给 20 / 30 / 40 / 50 / 100 分。
     *
     * signin 那一档没挂 bonus、或者那一段整个拆不出来都按这套走 —— 参数管理那一页还没配阶梯时，
     * 签到照样能签、达档也照这五档发（这组数就是这一轮的口径）；理由和汇率兜底一样：
     * 配置脏了顶多是奖励发不对，不该把签到动作打出去。
     */
    private static final List<Tier> DEFAULT_BONUS = List.of(
            new Tier(7, 20), new Tier(30, 30), new Tier(90, 40), new Tier(180, 50), new Tier(365, 100));

    /** 金额统一保留两位，和库里的 decimal(10,2) 对齐 */
    private static final int MONEY_SCALE = 2;

    /**
     * 这一份参数在内存里摁多少毫秒。
     *
     * 折算是逐行调的（一行一次），一行一次库就把下单变成了 N 次查询；60 秒的窗口对"运营改了汇率"够用，
     * 对一次下单内的 100 行也就是读一次。窗口之内的新旧值差一会儿不要紧 —— 钱是下单那一刻定死落库的。
     */
    private static final long CACHE_MS = 60_000L;

    /** 读回来的那两档（一次读库两档一起拿），没读过时是 null */
    private volatile Params cached;

    private volatile long cachedAt;

    /** 1 元值多少积分（积分 ÷ 这一个数 = 抵多少钱） */
    public BigDecimal exchange() {
        return params().amount.exchange;
    }

    /** 积分自获得起多少天过期（送分那一刻拿它算流水行的 expire_time） */
    public int expireDays() {
        return params().amount.expireDays;
    }

    /** 签到一次给多少分（这是每次签到都有的基础分；达档多给的那一档由 {@link #signinBonusPoints} 加在它上面） */
    public int signinPoints() {
        return params().signin.points;
    }

    /** 签到那一批分自获得起多少天过期（落在积分流水那一行的 expire_time 上） */
    public int signinExpireDays() {
        return params().signin.expireDays;
    }

    /**
     * 累计签到满这么多天的那一次额外给几分（没踩中任何一档就是 0，加在每次签到那个基础分之上）。
     *
     * 判据是「这一次刚好把累计天数推到某一档」，所以每一档一辈子只发一次、也不用在哪张表上记"发到第几档了" ——
     * 累计天数每签一次只加 1，踩不中就是还没到。
     * 一个后果要说在前面：中途改档（比如把 7 改成 5）时，已经越过 5 的那些人这一档再也领不到；
     * 要补那一笔，走积分管理那一页的「修改」手工加，这里不做回溯。
     */
    public int signinBonusPoints(int cumulativeDays) {
        for (Tier tier : params().signin.bonus) {
            if (tier.days == cumulativeDays) {
                return tier.points;
            }
        }
        return 0;
    }

    /** 这么多积分抵多少钱（汇率 100 时 1 分就是 0.01 元，抵不出钱的 0 分和没用一个样） */
    public BigDecimal moneyOf(Integer points) {
        if (points == null || points <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return new BigDecimal(points).divide(exchange(), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 这么多钱最多能用掉几分（"这一行还剩多少钱可抵"要换算成分才说给买家听）。
     *
     * 取整往下：凑不满一分的那一点零头不该被报成"还能用 X 分"，
     * 上取整会报出一个实际抵不出来的额度，买家真填进来就被下单那一步的超额判据拒了，不如一开始少说一点。
     */
    public int pointsOf(BigDecimal money) {
        if (money == null || money.signum() <= 0) {
            return 0;
        }
        return money.multiply(exchange()).setScale(0, RoundingMode.FLOOR).intValue();
    }

    /** 取当前那两档（缓存过期就一次读库把两档一起读回来），读不出来的那一格按默认值 */
    private Params params() {
        Params hit = cached;
        if (hit != null && System.currentTimeMillis() - cachedAt < CACHE_MS) {
            return hit;
        }
        String json = configValue();
        Params params = new Params(
                new Rule(number(json, GROUP_AMOUNT, KEY_EXCHANGE), number(json, GROUP_AMOUNT, KEY_EXPIRE_DAY)),
                new Signin(number(json, GROUP_SIGNIN, KEY_EXCHANGE), number(json, GROUP_SIGNIN, KEY_EXPIRE_DAY),
                        bonusTiers(json, GROUP_SIGNIN)));
        cached = params;
        cachedAt = System.currentTimeMillis();
        return params;
    }

    /** 读出 points_config 那一行的原始值（没有这一行、或者值空着都返回 null，由下面折算成默认值） */
    private String configValue() {
        SysConfig config = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, CONFIG_KEY)
                .last("LIMIT 1"));
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return null;
        }
        return config.getConfigValue();
    }

    /**
     * 从 points_config 那段 JSON 数组里挑出指定那一档，按键名取出一个数。
     *
     * 值在库里是字符串（"100"），所以统一 getStr 再转数；任何一步拆不出来都返回 null 由调用方兜默认值 ——
     * 一段脏配置不该把下单打出一屏堆栈。整档缺失（比如 signin 那一档还没配）也走这一条返回 null。
     */
    private BigDecimal number(String json, String group, String key) {
        if (json == null) {
            return null;
        }
        try {
            JSONArray groups = JSONUtil.parseArray(json);
            for (Object item : groups) {
                JSONObject node = JSONUtil.parseObj(String.valueOf(item));
                if (!group.equals(node.getStr("name"))) {
                    continue;
                }
                String raw = node.getStr(key);
                return raw == null || raw.isBlank() ? null : new BigDecimal(raw.trim());
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /**
     * 从指定那一档里拆出 bonus 那一段阶梯（每项是 {"days":…,"points":…}）。
     *
     * 拆不出来的形状和上面那条一样：整档缺失、bonus 没挂、那一段根本不是数组 → 返回 null 由 Signin 兜默认五档。
     * 档与档之间混进脏值（缺 days、负数、拆不成数）只丢那一档、其余照收 —— 一处笔误不该把整条阶梯清掉。
     * 逐档不排序：给分那一路问的是「累计第 N 天踩不踩中某一档」，顺序不参与判断。
     */
    private List<Tier> bonusTiers(String json, String group) {
        if (json == null) {
            return null;
        }
        try {
            JSONArray groups = JSONUtil.parseArray(json);
            for (Object item : groups) {
                JSONObject node = JSONUtil.parseObj(String.valueOf(item));
                if (!group.equals(node.getStr("name"))) {
                    continue;
                }
                JSONArray raw = node.getJSONArray(KEY_BONUS);
                if (raw == null || raw.isEmpty()) {
                    return null;
                }
                List<Tier> tiers = new ArrayList<>();
                for (Object tierItem : raw) {
                    JSONObject tier = JSONUtil.parseObj(String.valueOf(tierItem));
                    BigDecimal days = number(tier, BONUS_DAYS);
                    BigDecimal points = number(tier, BONUS_POINTS);
                    if (days == null || points == null || days.signum() <= 0 || points.signum() <= 0) {
                        continue;
                    }
                    tiers.add(new Tier(days.intValue(), points.intValue()));
                }
                return tiers.isEmpty() ? null : tiers;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /** 手上已经有那一格了，只按键名取一个数；空着或拆不成数都返回 null，由上面那段把这一档丢掉 */
    private BigDecimal number(JSONObject node, String key) {
        String raw = node.getStr(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 一次读库拿回的那两档：amount 折钱、signin 签到给分 */
    private static final class Params {

        private final Rule amount;

        private final Signin signin;

        private Params(Rule amount, Signin signin) {
            this.amount = amount;
            this.signin = signin;
        }
    }

    /** amount 那一档，两格都在下单那一次用（汇率折钱、天数算送分什么时候过期）。脏值一律兜默认值，理由见类注释 */
    private static final class Rule {

        private final BigDecimal exchange;

        private final int expireDays;

        private Rule(BigDecimal exchange, BigDecimal expireDays) {
            this.exchange = exchange != null && exchange.signum() > 0 ? exchange : DEFAULT_EXCHANGE;
            this.expireDays = expireDays != null && expireDays.signum() > 0
                    ? expireDays.intValue() : DEFAULT_EXPIRE_DAY;
        }
    }

    /**
     * signin 那一档：points 是签到一次给多少分（这一档里的 points_exchange 装的是产出分，不是汇率），
     * expireDays 是这批分几天过期，bonus 是累计签到的达档奖励（整段没配就按 {@link #DEFAULT_BONUS} 那五档）。
     */
    private static final class Signin {

        private final int points;

        private final int expireDays;

        private final List<Tier> bonus;

        private Signin(BigDecimal points, BigDecimal expireDays, List<Tier> bonus) {
            this.points = points != null && points.signum() > 0 ? points.intValue() : DEFAULT_SIGNIN_POINTS;
            this.expireDays = expireDays != null && expireDays.signum() > 0
                    ? expireDays.intValue() : DEFAULT_EXPIRE_DAY;
            this.bonus = bonus != null ? bonus : DEFAULT_BONUS;
        }
    }

    /** 阶梯里的一档：累计签到满 days 天的那一次多给 points 分 */
    private static final class Tier {

        private final int days;

        private final int points;

        private Tier(int days, int points) {
            this.days = days;
            this.points = points;
        }
    }
}
