package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.api.service.ShopOrderCouponService;
import com.shop.entity.ShopActivityCoupon;
import com.shop.entity.ShopActivityCouponUser;
import com.shop.entity.ShopCategory;
import com.shop.entity.ShopOrderItem;
import com.shop.entity.ShopProduct;
import com.shop.enums.CouponRejectEnum;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopActivityCouponMapper;
import com.shop.mapper.ShopActivityCouponUserMapper;
import com.shop.mapper.ShopCategoryMapper;
import com.shop.rules.coupon.CouponCalcFact;
import com.shop.utils.CouponUtils;
import com.shop.utils.DateUtil;
import com.shop.utils.TransactionUtils;
import lombok.RequiredArgsConstructor;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngine;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 下单用券 服务实现（判据与四拍顺序见接口 {@link ShopOrderCouponService}）
 *
 * 分成三块，各管一段：
 * - "这一张券过不过"在规则组 com.shop.rules.coupon：一条与券类型无关的判据（归属、状态、过期、活动、
 *   适用范围、门槛）+ 四条券类型规则（1 满减 / 2 折扣 / 4 代金 / 3 免邮），按 @Rule 的 priority 从小到大跑，
 *   每张券由自己那一档认领、把折出来的数挂回事实；规则只往事实上写一个拒绝码（CouponRejectEnum），
 *   里面既没有算式、也没有中文。
 * - "怎么筛命中行、每一档怎么折"在 CouponUtils，只有那一份。
 * - 这里留下规则做不了的几件事：一次读库把输入备齐（券、模板、分类树）、拿 CouponUtils 算好命中行和它的合计、
 *   把逐张券的钱**摊到明细行上**、核销/退券，再按那个拒绝码把拒用的理由说给买家听（rejectText）。
 *   摊行必须在服务层，因为"这一行还剩多少可抵"是跨券的状态 —— 规则一次只看得见一张券，看不见前一张摊走了多少。
 *
 * 规则不抛异常（引擎是 skipOnFirstFailedRule(false)，一条规则失败并不保证异常传得到调用方）；
 * 这里读到拒绝码就当场抛出去，整单回滚。
 *
 * 这一份只管"这一单里这几张券怎么算钱、算完怎么核销"，一张券本身怎么发出去、面额怎么定，
 * 是后台 shop-admin 那一份的事（ShopActivityCouponServiceImpl），两边共用的是 CouponUtils 里那段折算。
 *
 * 面额那几格（券类型/面额/门槛/最高抵扣/适用范围类型）读的是**用户券上的快照**，
 * 只有范围里那份具体名单（scope_value）和适用门店（store_id）回模板上读 —— 用户券表没存这两格。
 * 于是有一条要知道的后果：模板改了名单，已发出去的券跟着新名单筛（券类型和面额不会跟着变）。
 */
@Service
@RequiredArgsConstructor
public class ShopOrderCouponServiceImpl implements ShopOrderCouponService {

    /** 一次下单最多几张券：这是公开接口的入口，给个上限防止一次塞进几百个 id（同 MAX_ITEM_COUNT 那一刀） */
    private static final int MAX_COUPON_COUNT = 20;

    /** 金额统一保留两位，和库里的 decimal(10,2) 对齐 */
    private static final int MONEY_SCALE = 2;

    private final ShopActivityCouponUserMapper shopActivityCouponUserMapper;

    /** 只用它读模板的范围名单和适用门店，另外核销时要按模板汇总加 used_count */
    private final ShopActivityCouponMapper shopActivityCouponMapper;

    /** 判"指定分类"要把分类的下级全捞出来（他定的口径：券发在父分类上，下级分类的商品也能抵） */
    private final ShopCategoryMapper shopCategoryMapper;

    private final RulesEngine rulesEngine;

    /** 用券那一组规则（com.shop.rules.coupon）：字段名对上 @Bean("couponRules")，因为 Rules 这个类型有 order/points/coupon 三个 bean */
    private final Rules couponRules;

    @Override
    public Usage apply(Long userId, List<Long> couponIds, List<ShopOrderItem> items, Map<Long, ShopProduct> productBySku) {
        Usage usage = new Usage();
        List<Long> ids = couponIds == null ? List.of() : couponIds.stream()
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return usage;
        }
        // 用券是要写用户券表和模板表（第三拍 redeem）的，事务没开就别算：算完不落库，券没收掉、钱却抵了
        TransactionUtils.requireActive("核销优惠券");
        if (ids.size() > MAX_COUPON_COUNT) {
            throw new ServiceException("一单最多用 " + MAX_COUPON_COUNT + " 张优惠券");
        }

        // 请求体里那串 id 是前端给的，先按"在不在"过一遍；selectBatchIds 带全局逻辑删除，删掉的券捞不回来
        Map<Long, ShopActivityCouponUser> held = shopActivityCouponUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ShopActivityCouponUser::getId, Function.identity()));
        List<String> missing = ids.stream().filter(id -> !held.containsKey(id))
                .map(id -> "#" + id).collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw new ServiceException("这几张券不存在或已被删除：" + String.join("、", missing));
        }

        // 范围名单和适用门店只在模板上（用户券表那两格没存），一次捞回来；某张券的模板找不到交给规则报
        List<Long> templateIds = held.values().stream().map(ShopActivityCouponUser::getCouponId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, ShopActivityCoupon> templates = templateIds.isEmpty() ? Map.of()
                : shopActivityCouponMapper.selectBatchIds(templateIds).stream()
                .collect(Collectors.toMap(ShopActivityCoupon::getId, Function.identity()));

        // 分类树只在真有用到「指定分类」的券时才读一次全表（类目就几百行，一次捞平比按层递归查库少几次往返）
        boolean anyCategoryScope = held.values().stream()
                .anyMatch(coupon -> coupon.getScopeType() != null && coupon.getScopeType() == CouponUtils.SCOPE_CATEGORY);
        Map<Long, List<Long>> categoryIndex = anyCategoryScope ? loadCategoryIndex() : Map.of();

        LocalDateTime now = LocalDateTime.now();
        for (Long id : ids) {
            ShopActivityCouponUser coupon = held.get(id);
            ShopActivityCoupon template = templates.get(coupon.getCouponId());
            // 业务口径先跑完：这一张命中哪几行、那些行值多少钱。规则既不筛行也不算钱，只判现成的数
            List<ShopOrderItem> hit = CouponUtils.matchLines(coupon, template, items, productBySku, categoryIndex);
            // 一张券一次 fire：归属、状态、过期、活动、适用范围、门槛、抵不抵得出钱，全在那一组规则里判
            CouponCalcFact calc = new CouponCalcFact(coupon, template, hit, CouponUtils.baseOf(hit), userId, now);
            rulesEngine.fire(couponRules, calc.toFacts());
            if (calc.getRejectCode() != null) {
                throw new ServiceException(rejectText(calc));
            }
            if (calc.isFreeShipping()) {
                // 免邮券抵的是配送费，而配送费要等建配送单那一步才算得出来，先压着；
                // 一张订单只有一趟配送，这一堆里最后只会有第一张真被用掉（offsetDeliveryFee）
                usage.getPendingFreeShipping().add(coupon);
                continue;
            }
            if (calc.getDiscount() == null) {
                // 四档券类型规则一条都没认领这一张（新环境没扫到 com.shop.rules 那一包，或者券类型是那四档之外的脏值却没走到代金那一档）。
                // 走到这儿不能放过去 —— 后面拿 discount 摊行会静默算成 0 抵，钱就和买家看到的对不上了
                throw new ServiceException("优惠券「" + CouponUtils.couponName(coupon) + "」这一单没算出抵扣，请重新提交");
            }

            // 规则报的是"这张券最多能抵多少"，同一行不能抵成负数实付：还要拿这些行还剩多少可抵压一道顶
            //（小计 − 已经摊掉的优惠，这个数是前面几张券一笔笔写出来的，只有这儿看得见）
            BigDecimal room = sumRoom(calc.getHit());
            if (room.signum() <= 0) {
                throw new ServiceException("优惠券「" + CouponUtils.couponName(coupon) + "」能抵的那几行已经被前面那张券抵满了");
            }
            // 这一张最终抵下来的钱（核销只要"用掉这张券"，试算还得把这一张抵了多少单独报给买家）
            BigDecimal discount = calc.getDiscount().min(room);
            spread(calc, discount, usage);
            usage.addUsed(coupon, discount);
        }
        return usage;
    }

    @Override
    public BigDecimal offsetDeliveryFee(Usage usage, BigDecimal deliveryFee) {
        if (usage == null || usage.getPendingFreeShipping().isEmpty()) {
            return zero();
        }
        BigDecimal fee = CouponUtils.money(deliveryFee);
        if (fee.signum() <= 0) {
            // 这一笔配送费本来就是 0（没配启用的计价规则，或命中了"实付满多少免配送费"那一档）：
            // 没东西可抵就不把券花掉，它留在用户手上（也不核销）
            return zero();
        }
        // 一趟配送只消一张免邮券：第二张不会再把已经抵成 0 的配送费抵一遍，所以它也不核销
        ShopActivityCouponUser first = usage.getPendingFreeShipping().get(0);
        // 封顶：这张券的 max_discount 说的是它整单最多抵这么多（这一格空着就是把这趟运费全免）
        BigDecimal offset = CouponUtils.shippingDiscount(fee, first.getMaxDiscount());
        if (offset.signum() <= 0) {
            // 上限填成 0 这类脏数据：抵不出钱就别把券花掉，和"配送费本来就是 0"那一档一样留在用户手上（试算会报这张抵 0、不核销）
            return zero();
        }
        usage.addUsed(first, offset);
        return offset;
    }

    @Override
    public void redeem(Long orderId, String orderNo, Usage usage) {
        if (usage == null || usage.getEntries().isEmpty()) {
            return;
        }
        TransactionUtils.requireActive("核销优惠券");
        LocalDateTime useTime = LocalDateTime.now();
        // 按券模板汇总：一个人一单里可能用掉同一张模板下的两张满减券，那要一次把 used_count 加 2
        Map<Long, Integer> perTemplate = new LinkedHashMap<>();
        for (Usage.Entry entry : usage.getEntries()) {
            ShopActivityCouponUser coupon = entry.coupon();
            // status = 0 压在 WHERE 里就是并发那道闸：抢不到说明这张券刚被别的订单用掉，整单回滚让买家重新提交
            if (shopActivityCouponUserMapper.redeem(coupon.getId(), orderId, orderNo, useTime) == 0) {
                throw new ServiceException("优惠券「" + CouponUtils.couponName(coupon) + "」刚被其它订单用掉，请重新提交");
            }
            if (coupon.getCouponId() != null) {
                perTemplate.merge(coupon.getCouponId(), 1, Integer::sum);
            }
        }
        perTemplate.forEach((couponId, count) -> {
            if (shopActivityCouponMapper.increaseUsedCount(couponId, count) == 0) {
                throw new ServiceException("优惠券核销记账失败（券模板 " + couponId + "），请重新提交");
            }
        });
    }

    /**
     * 退券：按订单捞出当初核销掉的那几张，一张一张退回去，再按模板把 used_count 减回来。
     *
     * 只认券上落的 order_id（Usage 那份账本早就不在了），所以"这一单用过哪些券"的唯一说法就是
     * shop_activity_coupon_user 上那三格核销痕迹。免邮券也在这一串里——它当初真被用掉才会带上这个 order_id
     *（没抵成的那张压根没核销），所以退券不需要分清券类型，也不走那一组规则（规则管的是"这张券能抵多少钱"）。
     *
     * 逐张退而不是一条 UPDATE 全清：退一张要顺带把所属模板的 used_count 减一，
     * 而"减几条、每张属于哪个模板"只有这一趟读出来的数才知道。
     * 抢到这一步的调用方只可能是取消成功的那一趟（主单状态先翻成 3 才走到这里），
     * 所以真走到"一行都没退到"只剩数据被谁改过这一种可能，报出去整单回滚。
     */
    @Override
    public int giveBack(Long orderId) {
        if (orderId == null) {
            return 0;
        }
        // 这一拍两张表都要写，事务没开就别退：退一半比不退难看得多
        TransactionUtils.requireActive("退回优惠券");
        List<ShopActivityCouponUser> used = shopActivityCouponUserMapper.selectList(
                new LambdaQueryWrapper<ShopActivityCouponUser>()
                        // order_id 这格可空（没核销过的券都是 NULL），所以查询条件显式带上 status：
                        // 万一调用方传进来一张已被取消过的单号，也只退到真正还挂着它的那几张，不会误伤别的行
                        .select(ShopActivityCouponUser::getId, ShopActivityCouponUser::getCouponId,
                                ShopActivityCouponUser::getCouponName, ShopActivityCouponUser::getCouponCode)
                        .eq(ShopActivityCouponUser::getOrderId, orderId)
                        .eq(ShopActivityCouponUser::getStatus, CouponUtils.USER_STATUS_USED));
        if (used.isEmpty()) {
            return 0;
        }
        Map<Long, Integer> perTemplate = new LinkedHashMap<>();
        for (ShopActivityCouponUser coupon : used) {
            if (shopActivityCouponUserMapper.giveBack(coupon.getId(), orderId) == 0) {
                throw new ServiceException("优惠券「" + CouponUtils.couponName(coupon) + "」退回失败（这一张的状态刚被人改过），请重新提交");
            }
            if (coupon.getCouponId() != null) {
                perTemplate.merge(coupon.getCouponId(), 1, Integer::sum);
            }
        }
        perTemplate.forEach((couponId, count) -> {
            if (shopActivityCouponMapper.decreaseUsedCount(couponId, count) == 0) {
                throw new ServiceException("优惠券退回记账失败（券模板 " + couponId + "），请重新提交");
            }
        });
        return used.size();
    }

    /**
     * 把扁平类目表折成「父 id → 子 id 列表」的索引（一次读全表，写法同分类管理那页内存建树那一刀）。
     *
     * 只取 id 和 parent_id 两列：判下级不需要名称，也不带 shop_icon 那条联表。
     * 全局逻辑删除会替我把删掉的类目滤掉 —— 已经删掉的分类底下的商品不该再被券命中。
     * 这张表由调用方读好交给 CouponUtils#matchLines 展开（规则一次库都不查）。
     */
    private Map<Long, List<Long>> loadCategoryIndex() {
        Map<Long, List<Long>> index = new HashMap<>();
        shopCategoryMapper.selectList(new LambdaQueryWrapper<ShopCategory>()
                        .select(ShopCategory::getId, ShopCategory::getParentId))
                .forEach(node -> {
                    Long parentId = node.getParentId();
                    if (parentId == null || parentId == 0L) {
                        return;
                    }
                    index.computeIfAbsent(parentId, key -> new ArrayList<>()).add(node.getId());
                });
        return index;
    }

    /**
     * 规则只往事实上写了一个拒绝码，这一个方法把那个码说成买家看得懂的那句话 ——
     * 券名、金额、日期都得在这儿拼上，字句和搬进规则引擎之前一字不差（一次 fire 只算一张券，说的就是这一张）。
     */
    private String rejectText(CouponCalcFact calc) {
        ShopActivityCouponUser coupon = calc.getCoupon();
        String name = "优惠券「" + CouponUtils.couponName(coupon) + "」";
        return switch (calc.getRejectCode()) {
            case NOT_OWNER -> name + "不是下单人名下的，不能用";
            case NOT_UNUSED -> name + statusText(coupon.getStatus());
            case EXPIRED -> name + "已经在 " + DateUtil.formatLocalDateTime(coupon.getExpireTime(), DateUtil.YYYY_MM_DD) + " 过期，不能再用";
            case TEMPLATE_MISSING -> name + "的活动已经找不到，用不了";
            case TEMPLATE_CANCELED -> name + "这场活动已经取消，不能用";
            case SCOPE_UNKNOWN -> name + "的适用范围没法识别（" + coupon.getScopeType() + "），这一张用不了";
            case SCOPE_NO_HIT -> name + "在这一单里没有能抵扣的商品（它只抵" + scopeText(coupon.getScopeType()) + "）";
            case THRESHOLD_NOT_REACHED -> name + "要满 " + plain(coupon.getThresholdAmount()) + " 才能用，"
                    + "这一单在它适用范围内只有 " + plain(calc.getBase());
            case NO_DISCOUNT -> name + "算出来抵 0 元，这一张先别用";
        };
    }

    /** 用户券 status 那几档说人话：0 是能用的那一档，其余每一档各说一句（只在拒用文案里用，所以留在业务侧） */
    private String statusText(Integer status) {
        if (status == null) {
            return "状态不明，用不了";
        }
        if (status == CouponUtils.USER_STATUS_USED) {
            return "已经用过了";
        }
        if (status == CouponUtils.USER_STATUS_EXPIRED) {
            return "已经过期了";
        }
        if (status == CouponUtils.USER_STATUS_VOID) {
            return "已经作废了";
        }
        return "状态不对（" + status + "），用不了";
    }

    /** 四档适用范围各一句说法，用在"这一单里没有这张券能抵的商品"那一句里（四档各自怎么筛见 CouponUtils#matchLines） */
    private String scopeText(Integer scopeType) {
        if (scopeType == null || scopeType == CouponUtils.SCOPE_ALL) {
            return "全场商品";
        }
        if (scopeType == CouponUtils.SCOPE_PRODUCT) {
            return "指定的那几件商品";
        }
        if (scopeType == CouponUtils.SCOPE_CATEGORY) {
            return "指定分类（含下级分类）里的商品";
        }
        return "指定门店的商品";
    }

    /** 金额说给人看：末尾那几个用不上的 0 去掉（100.00 说成 100）；toPlainString 是为了别让 1E+2 这种写法跑进文案 */
    private String plain(BigDecimal value) {
        return CouponUtils.money(value).stripTrailingZeros().toPlainString();
    }

    /**
     * 一张券抵下来的钱摊到它命中的那些行上：按各行小计占命中行小计合计的比例分。
     *
     * 比例分一定留分厘的尾差，所以末行拿"这一张的钱减掉前面各行已经分到的"，
     * 这样 Σ 行优惠正好等于这一张抵的钱 —— 行实付（小计 − 优惠）是退款和出库结成本唯一的依据，摊不平就是两本账。
     *
     * 每一行还要拿它自己的剩余可抵（小计 − 前面几张券已经摊上去的优惠）压一道顶：两张券覆盖的行集合不一样时，
     * 比例算出来的那一份可能超过这一行还剩的额度，被压住的那一点顺延给还有额度的行；
     * 顺延到一行都不剩说明上面那两数被并发的谁改过，宁可不成交也不把明细摊成负实付（下单是一个事务，抛出去整单回滚）。
     */
    private void spread(CouponCalcFact calc, BigDecimal discount, Usage usage) {
        List<ShopOrderItem> lines = calc.getHit();
        BigDecimal base = CouponUtils.money(calc.getBase());
        BigDecimal left = discount;
        for (int i = 0; i < lines.size() && left.signum() > 0; i++) {
            ShopOrderItem line = lines.get(i);
            // 末行拿剩下的那一个数，其余行按小计占比（这一张抵的钱不会大过命中行的小计合计，所以占比不会大过这一行自己的小计）
            BigDecimal share = i + 1 == lines.size() ? left
                    : discount.multiply(CouponUtils.money(line.getTotalAmount())).divide(base, MONEY_SCALE, RoundingMode.HALF_UP);
            left = left.subtract(write(line, share.min(left).min(room(line)), usage));
        }
        for (ShopOrderItem line : lines) {
            if (left.signum() == 0) {
                break;
            }
            left = left.subtract(write(line, room(line).min(left), usage));
        }
        if (left.signum() > 0) {
            throw new ServiceException("优惠券「" + CouponUtils.couponName(calc.getCoupon()) + "」的抵扣没能摊完，请重新提交");
        }
    }

    /** 把摊到这一行上的那一份写进 discount_amount、同时累进这一单的货值券抵扣合计；抵 0 那一刀什么都不做，只把 0 报回去 */
    private BigDecimal write(ShopOrderItem line, BigDecimal take, Usage usage) {
        if (take.signum() <= 0) {
            return zero();
        }
        line.setDiscountAmount(CouponUtils.money(line.getDiscountAmount()).add(take));
        usage.addGoodsDiscount(take);
        return take;
    }

    /** 这些行还能抵多少（小计 − 已经摊上去的优惠），逐张券相加时靠它保证抵不出负数实付 */
    private BigDecimal sumRoom(List<ShopOrderItem> lines) {
        return lines.stream().map(this::room).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal room(ShopOrderItem line) {
        return CouponUtils.money(line.getTotalAmount()).subtract(CouponUtils.money(line.getDiscountAmount()))
                .max(BigDecimal.ZERO);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
