package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.api.service.ShopOrderPointsService;
import com.shop.entity.ShopActivityPoints;
import com.shop.entity.ShopActivityPointsLog;
import com.shop.entity.ShopOrderItem;
import com.shop.entity.ShopProductSku;
import com.shop.entity.SysOrder;
import com.shop.entity.SysRefund;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopActivityPointsLogMapper;
import com.shop.mapper.ShopActivityPointsMapper;
import com.shop.mapper.ShopOrderItemMapper;
import com.shop.mapper.ShopProductSkuMapper;
import com.shop.utils.CouponUtils;
import com.shop.utils.TransactionUtils;
import com.shop.vo.refund.ShopRefundItemVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用积分 服务实现（四拍走法和账户那五个数怎么动见接口 {@link ShopOrderPointsService}）
 *
 * 三块分工，和用券那一份是同一套形状：
 * - 分和钱之间怎么折在 {@link ShopPointsSupport}（汇率读 sys_config），这里不重写算式
 * - 账户怎么占、怎么还都在 ShopActivityPointsMapper 那七条条件增量里，判据压在 WHERE 的
 *   balance / frozen_points / used_points >= 这一笔 上，返回 0 行就是这一笔占不着（被别人抢了、
 *   或者已经被处理过一遍），报出去让整单回滚 —— 绝不做"读回来减一下再写回去"那种两步动作
 *   （唯一一条没有 >= 闸门的是退货收回送分那一条：那一笔本来就该"能收多少收多少"，不该卡住退款）
 * - 这里只留四件事：把逐行的判据跑完（一行一行说清是哪一行超了）、把每一笔变动记成流水、
 *   送分时把还没有账的那个人第一行账建出来、送完分把逐行那一份钉回 shop_order_item.gain_points
 *  （退款要收回当初送的分，读的是那一格当时的快照，不拿规格表现在的档位倒推）
 *
 * 流水只在**可用余额真的动了**的那几拍写（下单冻结 −、取消解冻 +、退款退抵扣 +、送分 +、退货收回送分 −）；
 * 支付结转那一拍只是把冻结改成已消耗，balance 一分没动，所以那边不插流水 ——
 * 流水页上"这一笔之后账户还有多少分"接得上下一笔，靠的就是这个规矩。
 */
@Service
@RequiredArgsConstructor
public class ShopOrderPointsServiceImpl implements ShopOrderPointsService {

    private final ShopActivityPointsMapper shopActivityPointsMapper;

    /** 每一笔账变动都要跟着记一行流水，同事务（只插不改不删，所以只有 insert） */
    private final ShopActivityPointsLogMapper shopActivityPointsLogMapper;

    /** 送分要读这一条规格上那个「每件可得多少分」，只读 */
    private final ShopProductSkuMapper shopProductSkuMapper;

    /** 送分时把「这一行送了几分」钉回明细行，退款收回送分读的就是那一格（这一列只有这一条链在写） */
    private final ShopOrderItemMapper shopOrderItemMapper;

    /** 汇率和有效期的唯一来源（sys_config 的 points_config） */
    private final ShopPointsSupport shopPointsSupport;

    /** 变动类型（字典 points_biz_type）：2 购物 —— 支付完成后按买的东西送分 */
    private static final int BIZ_TYPE_PURCHASE = 2;

    /** 变动类型：8 抵扣 —— 下单用分抵钱为负，取消/退款把分还回来为正，两头共用这一档（biz_id 都是订单ID） */
    private static final int BIZ_TYPE_DEDUCT = 8;

    private static final int MONEY_SCALE = 2;

    @Override
    public Usage apply(Long userId, List<ShopOrderItem> items) {
        BigDecimal money = zero();
        int points = 0;
        for (int i = 0; i < items.size(); i++) {
            ShopOrderItem line = items.get(i);
            Integer given = line.getUsedPoints();
            if (given == null) {
                continue;
            }
            String row = "第 " + (i + 1) + " 行商品「" + StringUtils.defaultIfBlank(line.getProductName(),
                    "明细 #" + line.getId()) + "」";
            if (given < 0) {
                throw new ServiceException(row + "使用的积分不能是负数");
            }
            if (given == 0) {
                continue;
            }
            // 一行最多把自己抵到 0，抵不出负数实付：还剩多少钱可抵看的是券摊完之后的那一格
            //（所以这一拍必须走在 ShopOrderCouponService#apply 之后）
            BigDecimal lineMoney = shopPointsSupport.moneyOf(given);
            BigDecimal room = CouponUtils.money(line.getTotalAmount())
                    .subtract(CouponUtils.money(line.getDiscountAmount()))
                    .max(BigDecimal.ZERO)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if (lineMoney.compareTo(room) > 0) {
                throw new ServiceException(row + "最多只能用 " + shopPointsSupport.pointsOf(room)
                        + " 分（这一行优惠之后只剩 " + room + " 元可抵，这一笔要抵 " + lineMoney + " 元）");
            }
            points += given;
            money = money.add(lineMoney);
        }
        if (points == 0) {
            return new Usage(0, zero());
        }
        // 用分是要写账户表和流水表的，事务没开就别算：算完不落库，分没冻住、钱却抵了
        TransactionUtils.requireActive("使用积分");
        // 先按行判、再判总额：逐行都够但总数超余额，也得在这儿拦住，不能让买家提交后才看到
        int balance = available(userId);
        if (balance < points) {
            throw new ServiceException("积分不够：这一单要用 " + points + " 分，账户上只有 " + balance + " 分");
        }
        return new Usage(points, money.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public void freeze(Long userId, SysOrder order, Usage usage) {
        if (usage == null || usage.points() <= 0) {
            return;
        }
        TransactionUtils.requireActive("冻结积分");
        // balance >= 这一笔 压在 WHERE 里就是并发那道闸：同一批分被两单同时抢，只有一趟改得动
        if (shopActivityPointsMapper.freezePoints(userId, usage.points()) == 0) {
            throw new ServiceException("积分「" + usage.points() + "」刚被别的订单用掉了，请重新提交");
        }
        log(userId, -usage.points(), BIZ_TYPE_DEDUCT, order.getId(), order.getOrderNo(),
                "下单抵扣 " + usage.points() + " 分（抵 " + usage.money() + " 元）", null);
    }

    @Override
    public void settle(SysOrder order, List<ShopOrderItem> lines) {
        int used = usedPoints(lines);
        if (used > 0) {
            TransactionUtils.requireActive("结转积分");
            // frozen >= 这一笔 挡的是"这一笔冻结已经被谁处理过了"（重复回调走不到这儿，走到就是账被人工动过）
            if (shopActivityPointsMapper.consumeFrozenPoints(order.getUserId(), used) == 0) {
                throw new ServiceException("订单「" + order.getOrderNo() + "」冻结的 " + used
                        + " 分对不上（账户上冻着的不是这个数），已中止处理");
            }
        }
        grant(order, lines);
    }

    @Override
    public int unfreeze(SysOrder order, List<ShopOrderItem> lines) {
        int points = usedPoints(lines);
        if (points == 0) {
            return 0;
        }
        TransactionUtils.requireActive("解冻积分");
        if (shopActivityPointsMapper.unfreezePoints(order.getUserId(), points) == 0) {
            throw new ServiceException("订单「" + order.getOrderNo() + "」抵扣的 " + points
                    + " 分退不回来（账户上冻着的不是这个数，这一笔大概已经被处理过了），已中止取消");
        }
        log(order.getUserId(), points, BIZ_TYPE_DEDUCT, order.getId(), order.getOrderNo(),
                "订单取消，下单抵扣的 " + points + " 分退回", null);
        return points;
    }

    @Override
    public int giveBack(SysRefund refund, Map<Long, ShopOrderItem> lineMap, List<ShopRefundItemVo> items) {
        int points = returnDeductedPoints(refund, lineMap, items);
        // 两拍一起做：先把当初为这批货扣掉的分还给买家，再把当初为这批货送出去的分收回来。
        // 只还不收的话，"买了送分 → 再退货"就是白得一笔分（钱退了、货退了、分还留着）
        reclaimGainedPoints(refund, lineMap, items);
        return points;
    }

    /** 退款第一拍：这一批退掉的货，把为它们扣掉的分从累计消耗还回可用（used → balance），记一笔「8 抵扣」的正流水 */
    private int returnDeductedPoints(SysRefund refund, Map<Long, ShopOrderItem> lineMap, List<ShopRefundItemVo> items) {
        int points = 0;
        for (ShopRefundItemVo item : items) {
            ShopOrderItem line = lineMap.get(item.getOrderItemId());
            points += shareOfReturned(line, item.getQuantity(), pointsOf(line == null ? null : line.getUsedPoints()));
        }
        if (points == 0) {
            return 0;
        }
        TransactionUtils.requireActive("退回抵扣积分");
        if (shopActivityPointsMapper.giveBackUsedPoints(refund.getUserId(), points) == 0) {
            throw new ServiceException("抵扣的 " + points + " 分退不回来（账户上累计消耗的积分比这个数少，"
                    + "这一单大概已经退过一遍了），已中止处理");
        }
        log(refund.getUserId(), points, BIZ_TYPE_DEDUCT, refund.getOrderId(), refund.getOrderNo(),
                "退货退回抵扣的积分（退款单 " + refund.getRefundNo() + "）", null);
        return points;
    }

    /**
     * 退款第二拍：把当初为这一批货送出去的分收回来（累计获得和可用一起减，见 Mapper 的 reclaimGainPoints），
     * 记一笔「2 购物」的**负**流水 —— 档位和送分共用，靠正负分方向，同「8 抵扣」那两头的做法。
     *
     * 收回的量读的是明细行上那一格 gain_points（支付回调送分时钉下去的快照），
     * 和抵扣那一格走同一个摊法 {@link #shareOfReturned}：这一行送的分 ×（这次退的件数 ÷ 购买件数）取整，
     * 再减掉以前那几批已经收过的部分。所以几批退完正好把这一行当初送的分收干净，不多也不少。
     * 老单子那一格是 0（这一列加上之前就已经支付过的），按 0 收、不追缴。
     *
     * 两个不回滚的软判据（钱该退还是得退，不能为了收分把退款卡住）：
     * - 收不满就收到可用为 0：这些分他多半已经花掉一部分（甚至拿去抵了别的单），能收多少收多少
     * - 没有账户行、可用本来就是 0 —— 一律少收或不收，不报错
     * 所以这一拍只可能少收，绝不可能把 balance 扣成负数（那条 SQL 用 LEAST 钉死了）。
     */
    private void reclaimGainedPoints(SysRefund refund, Map<Long, ShopOrderItem> lineMap,
                                     List<ShopRefundItemVo> items) {
        int toReclaim = 0;
        for (ShopRefundItemVo item : items) {
            ShopOrderItem line = lineMap.get(item.getOrderItemId());
            toReclaim += shareOfReturned(line, item.getQuantity(), pointsOf(line == null ? null : line.getGainPoints()));
        }
        if (toReclaim <= 0) {
            return;
        }
        TransactionUtils.requireActive("收回赠送积分");
        // 先按账上可用夹一次：夹完就是这次真要收的量，流水也记这个数
        //（SQL 里还有一层 LEAST 兜并发，收到 0 为止，两头都不会扣成负数）
        int reclaimed = Math.min(toReclaim, available(refund.getUserId()));
        if (reclaimed <= 0) {
            return;
        }
        if (shopActivityPointsMapper.reclaimGainPoints(refund.getUserId(), reclaimed) == 0) {
            // 账户行没了，或者可用刚被别的动作花光：这一笔一分也没收到，流水也不留
            return;
        }
        log(refund.getUserId(), -reclaimed, BIZ_TYPE_PURCHASE, refund.getOrderId(), refund.getOrderNo(),
                "退货收回购物赠送的 " + reclaimed + " 分（退款单 " + refund.getRefundNo() + "）", null);
    }

    /**
     * 这一行这次退掉的件数对应多少分：按件数比例取整，且只算"这次新退的那一段"。
     *
     * 抵扣那一格（used_points）和赠送那一格（gain_points）走的是同一个式子，所以只写这一处 ——
     * 两次取整相减（而不是"这一行剩余可退 × 比例"）才能保证分几批退完加起来正好等于那一格的数：
     * 一行 3 件用 100 分时逐批退就是 33 + 33 + 34，最后一批把尾差吃掉。
     * 传进来的 line 是回写之前的那一版（returned_quantity 还没加这一次），所以 after 自己算。
     */
    private int shareOfReturned(ShopOrderItem line, Integer quantity, int linePoints) {
        int total = line == null || line.getQuantity() == null ? 0 : line.getQuantity();
        if (linePoints <= 0 || total <= 0 || quantity == null || quantity <= 0) {
            return 0;
        }
        int before = line.getReturnedQuantity() == null ? 0 : line.getReturnedQuantity();
        int after = Math.min(before + quantity, total);
        return (int) ((long) linePoints * after / total) - (int) ((long) linePoints * before / total);
    }

    /** 明细行上那两格积分（抵扣 / 赠送）：老数据可能是空的，负数属于脏数据，一律按 0 说 */
    private int pointsOf(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    /**
     * 支付回调的送分：Σ 件数 × 这一条规格「每件可得多少分」，正数进账户、把逐行那一份钉回明细行，
     * 再记一笔「2 购物」的流水。
     *
     * 整单都是 0（这一批规格都没设 gain_points）就一条流水都不插、一次库都不写 —— 绝大多数单子中这一条，
     * 所以送分这一路对没配积分的店是零开销。
     * 账户还没有第一行账（这个人从来没得过分）时由这一笔把账建出来：这张表的行由第一次变动带出来，
     * 后台不建账（口径同商品库存那张账）。
     *
     * 回写 shop_order_item.gain_points 是给退款那一路留的：要收回当初送的分，读的是这一格**当时**的档位，
     * 不再拿 shop_product_sku.gain_points 倒推（规格后来改过档位，倒推出来的就不是当初送的那一份）。
     */
    private void grant(SysOrder order, List<ShopOrderItem> lines) {
        Map<Long, Integer> gainedByLine = gainPointsByLine(lines);
        if (gainedByLine.isEmpty()) {
            return;
        }
        int gained = gainedByLine.values().stream().mapToInt(Integer::intValue).sum();
        TransactionUtils.requireActive("发放积分");
        Long userId = order.getUserId();
        if (shopActivityPointsMapper.gainPoints(userId, gained) == 0) {
            ShopActivityPoints account = new ShopActivityPoints();
            account.setUserId(userId);
            account.setTotalPoints(gained);
            account.setUsedPoints(0);
            account.setBalance(gained);
            account.setFrozenPoints(0);
            // expire_points 留 0：那一格是"即将过期积分"，要等过期那一条链（这一期没有定时任务）才算
            account.setExpirePoints(0);
            try {
                shopActivityPointsMapper.insert(account);
            } catch (DuplicateKeyException e) {
                // uk_user：同一刻这个人第一次得分的两个动作撞上了，那就改走增量，别把已有那行账覆盖掉
                if (shopActivityPointsMapper.gainPoints(userId, gained) == 0) {
                    throw new ServiceException("积分账户写入失败，请重新处理支付通知");
                }
            }
        }
        gainedByLine.forEach(shopOrderItemMapper::updateGainPoints);
        log(userId, gained, BIZ_TYPE_PURCHASE, order.getId(), order.getOrderNo(),
                "购物获得 " + gained + " 分（订单 " + order.getOrderNo() + "）",
                LocalDateTime.now().plusDays(shopPointsSupport.expireDays()));
    }

    /**
     * 逐行算这一单该送几分：明细行 id → 这一行送的分（每件档位 × 这一行的件数）。
     *
     * 没得分的行不进这张表（既不回写也不占位），所以整单都是 0 时调用方看到的就是空表。
     */
    private Map<Long, Integer> gainPointsByLine(List<ShopOrderItem> lines) {
        List<Long> skuIds = lines.stream().map(ShopOrderItem::getSkuId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (skuIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<Long, Integer> perSku = gainPointsPerSku(skuIds);
        Map<Long, Integer> gained = new LinkedHashMap<>();
        for (ShopOrderItem line : lines) {
            Integer each = perSku.get(line.getSkuId());
            if (each == null || each <= 0 || line.getId() == null
                    || line.getQuantity() == null || line.getQuantity() <= 0) {
                continue;
            }
            gained.put(line.getId(), each * line.getQuantity());
        }
        return gained;
    }

    /**
     * 每一条规格各送多少分（shop_product_sku.gain_points，按「每件」口径，只是这个"件"现在是一条具体规格）。
     *
     * 只有送分这一头读它，算出来当场就钉回明细行那一格了 —— 退款收回不再回来查这里，
     * 因为这一列是可以被运营改的，改完之后倒推出来的数就不是当初送的那一份了。
     */
    private Map<Long, Integer> gainPointsPerSku(List<Long> skuIds) {
        return shopProductSkuMapper.selectBatchIds(skuIds).stream()
                .collect(Collectors.toMap(ShopProductSku::getId,
                        sku -> sku.getGainPoints() == null ? 0 : sku.getGainPoints(), (a, b) -> a));
    }

    /** 这几行下单时用掉多少分（一行的 used_points 就是它自己那一格，逐行相加就是这一单占住的量） */
    private int usedPoints(List<ShopOrderItem> lines) {
        return lines.stream().map(ShopOrderItem::getUsedPoints).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
    }

    /** 读这一个人的账户行（没有就是还没开账，调用方按可用 0 分处理） */
    private ShopActivityPoints account(Long userId) {
        if (userId == null) {
            return null;
        }
        return shopActivityPointsMapper.selectOne(new LambdaQueryWrapper<ShopActivityPoints>()
                .eq(ShopActivityPoints::getUserId, userId)
                .last("LIMIT 1"));
    }

    /** 账户上的可用积分（没有账户行、或者那一格是脏的空值都按 0 算） */
    private int available(Long userId) {
        ShopActivityPoints account = account(userId);
        return account == null || account.getBalance() == null ? 0 : account.getBalance();
    }

    /**
     * 记一笔流水：points 带符号（送分为正、抵扣为负），balance 记的是**这一笔之后**账户上的可用积分。
     *
     * 那个 balance 不自己算，读回来才和账户表是同一个数（同一事务里刚改过的那一版）。
     * 读不到行属于数据被删了，那种情况下宁可记 0 也别留 NULL —— 这一列 NOT NULL，插不进去会让整单回滚，
     * 而流水页要的是"这笔变动之后他还剩多少分"，账没了就是 0。
     */
    private void log(Long userId, int points, int bizType, Long bizId, String bizNo,
                     String description, LocalDateTime expireTime) {
        ShopActivityPoints account = account(userId);
        ShopActivityPointsLog row = new ShopActivityPointsLog();
        row.setUserId(userId);
        row.setPoints(points);
        row.setBalance(account == null || account.getBalance() == null ? 0 : account.getBalance());
        row.setBizType(bizType);
        row.setBizId(bizId);
        row.setBizNo(bizNo);
        row.setDescription(description);
        row.setExpireTime(expireTime);
        shopActivityPointsLogMapper.insert(row);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
