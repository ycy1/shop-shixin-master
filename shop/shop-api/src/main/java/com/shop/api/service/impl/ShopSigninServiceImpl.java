package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.api.service.ShopSigninService;
import com.shop.dto.activity.ShopActivitySigninMakeupDto;
import com.shop.dto.user.LoginUserInfo;
import com.shop.entity.ShopActivityPoints;
import com.shop.entity.ShopActivityPointsLog;
import com.shop.entity.ShopActivitySignin;
import com.shop.entity.SysUser;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopActivityPointsLogMapper;
import com.shop.mapper.ShopActivityPointsMapper;
import com.shop.mapper.ShopActivitySigninMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.SigninUtils;
import com.shop.utils.TransactionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户签到 写入实现（两个动作怎么分流见接口 {@link ShopSigninService}）
 *
 * 签到给多少分、这批分几天过期，读的是 sys_config 的 points_config 里 **signin** 那一档
 *（{@link ShopPointsSupport}，那一档里 points_exchange 这个键的含义是"签一天给几分"，不是汇率）。
 * 前台签到和后台补签**给得一样多** —— 这一页存在的意义是"那天没签，今天补上"，
 * 让补签少给或让后台手工填积分，日历和积分流水就各说一套（口径同积分管理那页只收 方向/数量/原因）。
 *
 * 达档奖励走同一份配置： signin 那一档里的 bonus 数组是「累计签到满 N 天多给 M 分」，
 * 累计天数就是名下有几行（{@link #totalDays}），所以补一条历史行同样把这一格往前推一天 ——
 * 补签算累计这一条是他拍的口径。奖励合进这一次的分里一起落 points 列、一起进账户，不另记一笔流水。
 *
 * 签到徽章（比的正是上面那一个累计天数）不在这一刻判了：2026-10-18 起搬到定时任务里一轮算完，
 * 见 {@code ShopActivityBadgeGrantService#grantSigninBadges}（sys_job 上那条 badgeTask 触发）。
 * 原来它挂在签到这条链的末尾，要在同一个事务里多跑取候选、落徽章行、兑现分和券好几趟，
 * 而这一档比的是"累计签了几天"，本来就按天才有变化 —— 晚一轮再发没有任何影响。
 *
 * 账户那五个数一概不自己加减：加分走 ShopActivityPointsMapper 那条带闸门的原子 UPDATE，
 * 0 行说明这个人还没有第一行账，由这一笔把账建出来（写法同支付回调送分那一路），
 * 流水里「变动后余额」读的是改完再捞回来的那一版，下一笔才接得上。
 *
 * 判据（两人共用一套）：一个人一天一行（uk_user_date 是唯一那道闸，落库前那趟读只为把话说成人话，
 * 判重读的就是那一趟的第一行）、正常签到只签今天、补签只补今天之前（那一天还没过完就把签补上，等于替将来那一格作保）。
 * 两边唯一的差别是本人那一头多一道 {@link #SELF_MAKEUP_DAYS} 窗口，替别人补那一路不限。
 * 重复点不写重：真撞上 uk_user_date 也兜成同一句话。
 *
 * 这一条链的耗时全在来回数上（MySQL 在公网那台机器上，一发一个来回），所以能搭一趟的绝不发两趟：
 * 落库前那一趟一次带回「那一天签过没有 + 名下几行 + 它之前最近那几格」，人还在那一道只数一行。
 *
 * 这一张表上不存"连续第几天"那一列（原来有 continuous_days，已删）：数是从 sign_date 现算出来的，
 * 读法见 {@link SigninUtils} 那条线 —— 这里只发一趟（{@link ShopActivitySigninMapper#selectSignDatesAround}，
 * 判重和累计天数搭同一趟），落库前读、由 {@link #streakDates} 把这一次的那一天接回串头。
 * 数出来只给两件事用：积分流水那句说明、返回给前端的那一行。写在库里的那个数是旧账：
 * 补签补在一段连续日子中间那天，之后的那些行谁都不去回头改。
 *
 * 正常签到不挡账号类型：谁登录谁签（消费者、门店、ERP 都一样）。要收成"只有消费者档能签"，
 * 在 {@link #sign()} 里加一道账号类型判据就行。
 */
@Service
@RequiredArgsConstructor
public class ShopSigninServiceImpl implements ShopSigninService {

    /** 这一张表本身的写入只走这一份：落行、查有没有签过、算连续天数都问它 */
    private final ShopActivitySigninMapper shopActivitySigninMapper;

    /** 签到给分要动账户表，走那条带闸门的原子增量（没账就建第一行账），这里不碰那五个数 */
    private final ShopActivityPointsMapper shopActivityPointsMapper;

    /** 落了行必须同时留一笔积分流水（biz_type=1 签到），不然账户上多出来的那几分没人说得清从哪来 */
    private final ShopActivityPointsLogMapper shopActivityPointsLogMapper;

    /** 给分之前要确认这个人还在（第一次给分要把账户的第一行账建出来，不能建给一个不存在的人） */
    private final SysUserMapper sysUserMapper;

    /** 分值和有效期的唯一来源（sys_config 的 points_config 里 signin 那一档） */
    private final ShopPointsSupport shopPointsSupport;

    /** 签到类型（字典 sign_type）：1 正常签到 —— 前台本人点的那一颗写出来的 */
    private static final int SIGN_TYPE_NORMAL = 1;

    /** 签到类型：2 补签 —— 后台日历上点没签的过去日子写出来的 */
    private static final int SIGN_TYPE_MAKEUP = 2;

    /** 变动类型（字典 points_biz_type）：1 签到 */
    private static final int BIZ_TYPE_SIGNIN = 1;

    /**
     * 本人补签的窗口（天）：只允许补今天往前数这些天之内的日子。
     *
     * 只有本人这一头受这一道挡：不封顶就能把历史空日子全补一遍刷积分。
     * 后台替别人补那一路（{@link #makeup}）刻意不限 —— 那是人工兜底的动作，有人在那儿判断。
     * 前端 SigninCalendar 组件里钉了同一个数用来把够不着的那些格子画成不能点，改数两处一起改。
     */
    private static final int SELF_MAKEUP_DAYS = 7;

    /**
     * 前台本人签到：签今天这一格，归属日就是日历上的今天，所以 sign_date = sign_time 的那一天
     *（这两格分家是在补签那一路，见 {@link #makeup}）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopActivitySignin sign() {
        LoginUserInfo login = LoginUserUtils.requireLoginUser("后再签到");
        return write(login.getId(), LocalDate.now(), SIGN_TYPE_NORMAL);
    }

    /**
     * 后台补签：表单上只有"给谁、补哪一天"两格能填，签到类型/积分/连续天数/签到时间都是服务端算的
     *
     * 补下来的那一行，归属日记成被补的那一天（sign_date），sign_time 记的是"什么时候补的" ——
     * 这两格在补签这一行上天生不是一个日子，日历按 sign_date 画格子，所以 9 月补的 8 月那一签落在 8 月那格上。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopActivitySignin makeup(ShopActivitySigninMakeupDto form) {
        if (form == null || form.getUserId() == null) {
            throw new ServiceException("请指明给哪个用户补签");
        }
        LocalDate signDate = form.getSignDate();
        if (signDate == null) {
            throw new ServiceException("请指明补签哪一天");
        }
        if (!signDate.isBefore(LocalDate.now())) {
            throw new ServiceException("补签只能补今天之前(" + LocalDate.now() + " 以前)的日子，那一天还没过完，要签的话请到前台自己签");
        }
        return write(form.getUserId(), signDate, SIGN_TYPE_MAKEUP);
    }

    /**
     * 本人补签：日历上点自己名下没签过的过去日子
     *
     * 只有"给谁"这一格是这里填的（取登录 id，参数点名别人不算），其余判据一概交给 {@link #makeup}
     *（那一天早于今天、还没签过、类型落 2 补签、积分和连续天数服务端算），同一套规则不挡第二次。
     * 这里只多加一道本人特有的窗口：{@link #SELF_MAKEUP_DAYS} 天以前的日子补不了。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopActivitySignin makeupSelf(LocalDate signDate) {
        LoginUserInfo login = LoginUserUtils.requireLoginUser("后补签");
        if (signDate != null && signDate.isBefore(LocalDate.now().minusDays(SELF_MAKEUP_DAYS))) {
            throw new ServiceException("只能补最近 " + SELF_MAKEUP_DAYS + " 天内的日子，更早的签到请联系客服");
        }
        ShopActivitySigninMakeupDto form = new ShopActivitySigninMakeupDto();
        form.setUserId(login.getId());
        form.setSignDate(signDate);
        return makeup(form);
    }

    /**
     * 签到这一动作的三件落地：落这一行 + 给积分 + 记流水，一个事务里做完
     *
     * 判据三句：人还在、那一天还没签过、事务开着。任何一件落空都整体回滚 ——
     * 行了没给分、给了分没有行，日历和积分账就各说一套。
     */
    private ShopActivitySignin write(Long userId, LocalDate signDate, int signType) {
        TransactionUtils.requireActive(signType == SIGN_TYPE_MAKEUP ? "补签" : "签到");
        // 人还在：这一张表上没有逻辑删除那一列，删号是真删，所以只数一行（把整张 sys_user 拖过来是给
        // 公网那条链多背一次传输）。给分要把账户的第一行账建出来，不能建给一个不存在的人。
        if (countUser(userId) == 0) {
            throw new ServiceException(signType == SIGN_TYPE_MAKEUP
                    ? "用户不存在，这一天的签没法补" : "当前登录账号已经不存在了，签不上");
        }
        // 三件判据一次读回来（那一天签过没有、名下几行、它之前最近那几格）—— 这一条链在公网 MySQL 上，
        // 来回数就是它的耗时，所以这里不发三条 SQL。读在新行落库之前，所以 signDate 自己那一格由下面接回去。
        List<ShopActivitySignin> recent = shopActivitySigninMapper.selectSignDatesAround(userId, signDate);
        if (!recent.isEmpty() && signDate.equals(recent.get(0).getSignDate())) {
            throw new ServiceException(signType == SIGN_TYPE_MAKEUP
                    ? signDate + " 这一天已经签过了，不用补" : "今天已经签过了，明天再来");
        }

        // 累计签到天数：名下已有的行数 + 这一次。补签同样算一天 —— 它落的就是"那天签过"的那一行，
        // 账上就是多签了一天，所以达档奖励对前台签到和后台补签一视同仁（同一套判据、同一个算式）。
        // 先数再落库：这一行的 points 要写这一次实到的分（基础 + 达档），insert 之后再数就晚了。
        // 判重那道 uk_user_date 已经在 insert 上兜着，同一天签两次只会有一次数得出天数。
        int cumulativeDays = totalDays(recent) + 1;
        int bonus = shopPointsSupport.signinBonusPoints(cumulativeDays);
        int points = shopPointsSupport.signinPoints() + bonus;
        ShopActivitySignin record = new ShopActivitySignin();
        record.setUserId(userId);
        record.setSignDate(signDate);
        record.setSignTime(LocalDateTime.now());
        record.setSignType(signType);
        record.setPoints(points);
        try {
            shopActivitySigninMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 判重和落库之间同一刻有人（或另一台实例）签了同一天：uk_user_date 挡住了，说成中文
            throw new ServiceException(signType == SIGN_TYPE_MAKEUP
                    ? signDate + " 这一天已经签过了，不用补" : "今天已经签过了，明天再来");
        }
        // 连续天数用的是上面那一趟读回来的日子（这张表上没这一列，数出来只给流水那句说明和返回给前端的那一行用）：
        // 那一趟在新行之前读，所以把 signDate 自己接在最前，形状和"落完之后再读一次"完全一样
        int continuous = SigninUtils.continuousStreak(streakDates(recent, signDate));
        record.setCurrentContinuous(continuous);

        int balance = givePoints(userId, points);
        ShopActivityPointsLog log = new ShopActivityPointsLog();
        log.setUserId(userId);
        log.setPoints(points);
        log.setBalance(balance);
        log.setBizType(BIZ_TYPE_SIGNIN);
        log.setBizId(record.getId());
        // biz_no 留空：签到这一档没有单号，谁签的/谁补的记在流水的 create_by，为什么补记在说明里
        log.setDescription((signType == SIGN_TYPE_MAKEUP ? "后台补签 " : "签到 ") + signDate
                + "：连续第 " + continuous + " 天，获得 " + points + " 分"
                + (bonus > 0 ? "（累计签到满 " + cumulativeDays + " 天，达档奖励 " + bonus + " 分）" : ""));
        log.setExpireTime(LocalDateTime.now().plusDays(shopPointsSupport.signinExpireDays()));
        shopActivityPointsLogMapper.insert(log);
        return record;
    }

    /**
     * 给这一笔分，返回改完之后账上的可用积分
     *
     * 增量那条 UPDATE 对没有账的人是 0 行改动（不报错），所以拿返回值分清"改到了行"还是"要建第一行账"；
     * 建账撞 uk_user（同一刻两个动作第一次给这个人加分）不报错，改走增量，别把已有那行账覆盖掉。
     * 这一张表的行由第一次变动带出来，谁都不单独开账（口径同商品库存那张账）。
     */
    private int givePoints(Long userId, int points) {
        if (shopActivityPointsMapper.gainPoints(userId, points) == 0) {
            ShopActivityPoints account = new ShopActivityPoints();
            account.setUserId(userId);
            account.setTotalPoints(points);
            account.setUsedPoints(0);
            account.setBalance(points);
            account.setFrozenPoints(0);
            // expire_points 留 0：那一格是"即将过期积分"，要等过期那一条链（本期没有定时任务）才算
            account.setExpirePoints(0);
            try {
                shopActivityPointsMapper.insert(account);
            } catch (DuplicateKeyException e) {
                if (shopActivityPointsMapper.gainPoints(userId, points) == 0) {
                    throw new ServiceException("积分账户写入失败，请重新签到");
                }
            }
        }
        ShopActivityPoints after = shopActivityPointsMapper.selectOne(new LambdaQueryWrapper<ShopActivityPoints>()
                .eq(ShopActivityPoints::getUserId, userId)
                .last("LIMIT 1"));
        return after == null || after.getBalance() == null ? 0 : after.getBalance();
    }

    /** 这个人还在不在（sys_user 没有逻辑删除那一列，删号是真删，所以数一行就够） */
    private long countUser(Long userId) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getId, userId));
        return count == null ? 0 : count;
    }

    /**
     * 名下已经有几行签到记录 = 累计签到天数（达档阶梯读的就是这个数）
     *
     * 那一格是 SQL 里 COUNT(*) OVER () 盖在每一行上的，不受只回 7 行影响；一行都还没签时那一趟回空表，
     * 所以这里空表当 0。只看行数、不看日子：补一条三年前的历史行同样算"多签了一天"，
     * 这就是"累计"和"连续"的分别 —— 连续那一头最多报到 7 天（见 {@link SigninUtils}），
     * 30/90/180/365 那几档在连续口径下永远踩不到。
     */
    private int totalDays(List<ShopActivitySignin> recent) {
        if (recent.isEmpty()) {
            return 0;
        }
        Integer total = recent.get(0).getSignCount();
        return total == null ? 0 : total;
    }

    /**
     * 数「连着第几天」的那一串日子，倒序、以 signDate 开头：
     * 那一趟读在新行落库之前（所以串里没有 signDate 自己），又把比它晚的那些挑掉
     *（补签会读到名下更新的日子，那些不属于这次回看），剩下的就是原来"落完再读一次"读回来的那一串。
     */
    private List<LocalDate> streakDates(List<ShopActivitySignin> recent, LocalDate signDate) {
        List<LocalDate> dates = new ArrayList<>();
        dates.add(signDate);
        for (ShopActivitySignin row : recent) {
            LocalDate day = row.getSignDate();
            if (day != null && day.isBefore(signDate)) {
                dates.add(day);
            }
        }
        return dates;
    }
}
