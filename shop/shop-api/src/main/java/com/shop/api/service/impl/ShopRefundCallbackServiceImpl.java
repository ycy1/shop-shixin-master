package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.api.service.ShopOrderCouponService;
import com.shop.api.service.ShopOrderDeliveryService;
import com.shop.api.service.ShopOrderPointsService;
import com.shop.api.service.ShopOrderWalletService;
import com.shop.api.service.ShopRefundCallbackService;
import com.shop.dto.refund.ShopRefundCallbackDto;
import com.shop.entity.ShopErpInventory;
import com.shop.entity.ShopErpPurchaseIn;
import com.shop.entity.ShopErpPurchaseInItem;
import com.shop.entity.ShopErpWarehouse;
import com.shop.entity.ShopOrderItem;
import com.shop.entity.SysOrder;
import com.shop.entity.SysRefund;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopErpInventoryMapper;
import com.shop.mapper.ShopErpPurchaseInItemMapper;
import com.shop.mapper.ShopErpPurchaseInMapper;
import com.shop.mapper.ShopErpWarehouseMapper;
import com.shop.mapper.ShopOrderItemMapper;
import com.shop.mapper.SysOrderMapper;
import com.shop.mapper.SysRefundMapper;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.TransactionUtils;
import com.shop.vo.refund.ShopRefundCallbackVo;
import com.shop.vo.refund.ShopRefundItemVo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 退款结果通知 服务实现
 *
 * 一次调用做完七件事（全在一个事务里，要么全成要么全不算）：
 * ① sys_refund 3 退款中（后台审核通过那一刻就是这一档）→ 4 退款成功，补 refund_time、落第三方退款单号；
 * ② 按建单时存在 sys_refund.extra 里的那份行快照，回写 shop_order_item.returned_quantity，
 *    某一行的累计已退数到了购买数量，顺带把它的 ship_status 推成 2 已退货；
 *    没到购买数量（退了一部分）推成 4 部分退货 —— 两档都是退货轨道上的状态，
 *    一旦这行退过货，发货那侧的回写（0 → 3、3 → 1）就不再推它，和 2 一样；
 * ③ 整单每一行都退满了，才把 sys_order.status 1/2 → 4 已退款（部分退的主单不动，
 *    因为"已退款"在这一版是整单状态，进度只看各行）；推成 4 的那一刻，这一单名下还停在
 *    0 待派单 / 1 待接单 的配送单跟着置 5 已取消（货都不发了，别让它在抢单池里挂着）；
 * ④ 按发货仓建 inbound_source=1 线上退货 的入库单，状态 0 待入库，明细的已入库数一件没加；
 * ⑤ 跟着 ③ 的结论返券：这一单**这次**被推成 4 已退款（也就是整单退满）才把核销在它上面的优惠券退回用户，
 *    部分退款一张都不返 —— 券是按整单买的，退两件货就把券退回，等于买家拿同样的钱留了货又白得一张券。
 *    返券走 ShopOrderCouponService#giveBack(orderId) 那一头（和取消订单退券是同一条路，按券上的 order_id 捞），
 *    这一单没用过券就是 0，不报错；
 * ⑥ 退积分：这一批退掉的货，两头一起清 —— 当初为它们扣掉的分（shop_order_item.used_points）按比例还回
 *    买家账户（已消耗 → 可用，记一笔「8 抵扣」的正流水），当初为它们送出去的分收回来
 *    （读明细行上的 gain_points 快照 —— 送分时钉下去的当时档位，商品后来改过档位也不受影响；
 *    累计获得 → 可用一起减，记一笔「2 购物」的负流水，已花掉的部分能收多少收多少）。
 *    和 ⑤ 不一样，这一拍**不等整单退满** ——
 *    分是按件花的、也是按件送的，退两件就还两件那一份、收两件那一份，几批退完正好把这一行的分退干净
 *    （取整方式见服务实现）。
 *    这一单没用过分、或者退的那几行本来一分没花，就是 0，不报错；商品没设 gain_points 也就没有要收的。
 * ⑦ 退余额：这一单的支付方式是 4 余额（下单选的就是它、支付回调也对过账）时，把这一张退款单的实退金额
 *    退回买家的可用余额（累计消费 → 可用余额，记一笔「3 退款」的流水），走 ShopOrderWalletService#giveBack。
 *    和 ⑥ 一样**不等整单退满** —— 余额付的是这一单的实付，退几件就还那一件那一份钱，几批退完正好把这一单付的全还上；
 *    微信 / 支付宝 / 银行卡那三档这一拍直接跳过（钱从原来的渠道出去，退款也不该出现在钱包流水里）。
 *    钱包上线之前支付的老单压根没有账户行，那一路 refundToBalance 返回 0 行、这边报 0 元不报错：
 *    「退款」这件事在退款单那一头已经记下了，这一拍只是把它记进账，账上行都没有也不该卡住整张退款单的回写。
 *
 * 只记账不动库存 —— 这是这一版的既定口径，和出库那条路是镜像的：支付回调只建"待出库"的出库单，
 * 仓库在「商品出库」页点「出库」才真减总库存；退款回调只建"待入库"的入库单，
 * 仓库在「商品入库」页点「入库」才真加总库存。所以"钱退了但货一直没退到仓"这段时间里，
 * 账上就是"退款已完成、货在途中"，库存行一分不动。要放开得另做退货入库单超时提醒，别在这里偷偷加库存。
 *
 * 为什么退回来的货要**一家仓一张单**：入库单是把仓库记在主表上的（shop_erp_purchase_in.warehouse_id，
 * 一张单只往一家仓入），而出库单是记在明细上的。所以一张退款单退了两个仓的货，就只能建两张。
 * 归仓按当初出货的那一家走（shop_order_item.warehouse_id）：退货是原路退回，
 * 不重新分仓 —— 重新挑一家"就近有货的仓"毫无意义，货是买家寄回来的，寄到哪儿由地址说了算不由距离说了算。
 *
 * 入库单价取的是这一行商品在**那家仓此刻的加权成本价**（shop_erp_inventory.cost_price），
 * 不是这一行的实付单价。因为入库动作用它重算加权平均成本（ShopErpInventoryServiceImpl#inbound）：
 * 卖价一般高于成本，按卖价入库会把这一批货的成本抬高一大截，账面利润当场失真。
 * 只有那一行库存记录读不到（老数据、或者被人工删了）才退回用实付单价，
 * 那种情况下宁可留个偏高的成本，也别插一条 0 元入库把成本拉成 0。
 *
 * 幂等和支付回调同理，用的是"状态条件更新"：网关重试、后台重复点，第二次的
 * WHERE status = 3 不成立，一行都没改，就直接把库里已经建好的那几张入库单原样报回去（created=false），
 * 既不会把 returned_quantity 加两遍、也不会重复建单，第五拍的券、第六拍的分和第七拍退的余额更不会返第二遍。
 */
@Service
@RequiredArgsConstructor
public class ShopRefundCallbackServiceImpl implements ShopRefundCallbackService {

    private final SysRefundMapper sysRefundMapper;

    private final SysOrderMapper sysOrderMapper;

    private final ShopOrderItemMapper shopOrderItemMapper;

    /** 捞这一单的商品行只用这一处（退满判定和归仓用的同一批行），和统一下单、退货同一份取数 */
    private final ShopOrderSupport shopOrderSupport;

    private final ShopErpPurchaseInMapper purchaseInMapper;

    private final ShopErpPurchaseInItemMapper purchaseInItemMapper;

    /** 入库单价要从库存行抄当前加权成本价，只读不写（加库存是仓库点「入库」那一步的事） */
    private final ShopErpInventoryMapper shopErpInventoryMapper;

    /** 只在重复通知那一路用：库里那几张单只有 warehouse_id，报回去要带仓名 */
    private final ShopErpWarehouseMapper shopErpWarehouseMapper;

    /** 整单退完时把这一单名下还没人接手的配送单一起收掉（见 pushOrderRefunded） */
    private final ShopOrderDeliveryService orderDeliveryService;

    /** 整单退完时把这一单用掉的优惠券退回用户手上（见 giveBackCoupons，和取消订单退券是同一条路） */
    private final ShopOrderCouponService orderCouponService;

    /** 这一批退掉的货：当初为它们扣掉的分按比例还回买家账户，当初为它们送的分收回来（见 ShopOrderPointsService#giveBack） */
    private final ShopOrderPointsService orderPointsService;

    /** 这一单是余额支付的话，实退金额退回买家可用余额（见 ShopOrderWalletService#giveBack） */
    private final ShopOrderWalletService orderWalletService;

    /** 退款状态（字典 refund_status）：只有 3 退款中（后台审核通过那一刻）能登记结果，终态那几档一律不重做 */
    private static final int REFUND_STATUS_REFUNDING = 3;

    private static final int REFUND_STATUS_SUCCESS = 4;

    private static final int REFUND_STATUS_FAILED = 5;

    /** 订单状态（字典 order_status）：1 已支付 2 已完成 → 4 已退款 */
    private static final int ORDER_STATUS_PAID = 1;

    private static final int ORDER_STATUS_FINISHED = 2;

    private static final int ORDER_STATUS_REFUNDED = 4;

    /** 入库单状态（字典 purchase_in_status）：0 待入库 —— 回调建出来的单恒是这个值 */
    private static final int RECEIPT_STATUS_PENDING = 0;

    /** 只用来把 sys_refund.extra 那份行快照读回来（写进去的是统一退货那一侧） */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** "这一拍这次没动钱"的那一个数（失败、重复通知、以及不是余额支付的那一单都报它，和真退的钱一样定两位小数） */
    private static final BigDecimal NO_MONEY = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopRefundCallbackVo callback(ShopRefundCallbackDto form) {
        //退款单、明细行、入库单要一起改，事务压根没开就别往下写：只成一半的账对不上
        TransactionUtils.requireActive("退款回调");
        SysRefund refund = resolveRefund(form);
        boolean success = form.getSuccess() == null || form.getSuccess();
        // 库里这一格已经是审核人最终定过的数（audit 时可以改少），回调再传一个不一样的就是两头对不上，
        // 拒掉让人去查是哪一笔弄错了；人工确认那类调用拿不到渠道金额，不传就跳过这道校验
        assertAmountMatches(refund, form);

        if (!success) {
            return markFailed(refund, form);
        }
        return markSuccess(refund, form);
    }

    /**
     * 失败那一路：3 → 5 退款失败，把原因记下来就结束。
     * 明细行、订单、库存一律不动 —— 钱没退出去，货也就还没退，退了的部分留着让人改完再发一次通知。
     * 已经记过终态的（重复通知）不改任何东西，把库里那一档原样报回去：
     * 库里是 4 就说明这笔其实已经成了，一条晚到的"失败"通知不该把它说成失败。
     */
    private ShopRefundCallbackVo markFailed(SysRefund refund, ShopRefundCallbackDto form) {
        String reason = form.getFailReason() == null || form.getFailReason().isBlank()
                ? "渠道退回失败（未提供原因）" : form.getFailReason().trim();
        int updated = sysRefundMapper.update(null, new LambdaUpdateWrapper<SysRefund>()
                .set(SysRefund::getStatus, REFUND_STATUS_FAILED)
                .set(SysRefund::getFailReason, reason)
                .eq(SysRefund::getId, refund.getId())
                .eq(SysRefund::getStatus, REFUND_STATUS_REFUNDING));
        if (updated == 0) {
            assertCanFinish(refund);
            // 库里已经是终态：重复通知，或者这笔其实早就成功了，原样报回去
            return voOf(refund, refund.getStatus(), refund.getFailReason());
        }
        return voOf(refund, REFUND_STATUS_FAILED, reason);
    }

    /** 失败那一档的返回体单独抽出来：翻成功和"本来就是终态"两路只差状态和原因两格 */
    private ShopRefundCallbackVo voOf(SysRefund refund, int status, String failReason) {
        ShopRefundCallbackVo vo = baseVo(refund, status);
        vo.setFailReason(failReason);
        vo.setReturnedItemCount(0);
        vo.setReturnedQuantity(0);
        vo.setGiveBackCouponCount(0);
        vo.setGiveBackPoints(0);
        vo.setGiveBackBalance(NO_MONEY);
        return vo;
    }

    /**
     * 成功那一路：翻状态 → 回写明细行 → 整单退满推主单 → 按仓建退货入库单 → 整单退完返券 → 按比例退积分 → 余额支付那一路退余额。
     * 顺序是"先拿到这一单的归属权，再动别的表"，重复通知在第一步就止步。
     */
    private ShopRefundCallbackVo markSuccess(SysRefund refund, ShopRefundCallbackDto form) {
        LocalDateTime now = LocalDateTime.now();
        String thirdNo = thirdNo(refund, form);
        int updated = sysRefundMapper.update(null, new LambdaUpdateWrapper<SysRefund>()
                .set(SysRefund::getStatus, REFUND_STATUS_SUCCESS)
                .set(SysRefund::getRefundTime, now)
                .set(thirdNo != null, SysRefund::getThirdRefundNo, thirdNo)
                // 成功这一路把"上一次登记过的失败原因"清掉：人工重试前先失败过一次是常事，
                // 留着那句会让下一个看单的人以为这笔还没成
                .set(SysRefund::getFailReason, null)
                .eq(SysRefund::getId, refund.getId())
                .eq(SysRefund::getStatus, REFUND_STATUS_REFUNDING));
        if (updated == 0) {
            assertCanFinish(refund);
            // 重复通知（已经是 4 了）：拿回库里那几张入库单，明细和主单都不再动
            return replayVo(refund);
        }

        ShopRefundCallbackVo vo = baseVo(refund, REFUND_STATUS_SUCCESS);
        vo.setRefundTime(now);
        vo.setThirdRefundNo(thirdNo);

        List<ShopRefundItemVo> items = parseItems(refund);
        Map<Long, ShopOrderItem> lineMap = orderLines(refund.getOrderId());
        // 先全部校验、再落笔：任何一行对不上就整单回滚，不留"退了三个仓里的第一个仓"这种半截账
        items.forEach(item -> assertLineRefundable(lineMap.get(item.getOrderItemId()), item));

        int quantity = 0;
        for (ShopRefundItemVo item : items) {
            ShopOrderItem line = lineMap.get(item.getOrderItemId());
            if (shopOrderItemMapper.increaseReturnedQuantity(line.getId(), item.getQuantity()) == 0) {
                // 建单到回调之间这一行又被退过一批（并发、或人工改过数据），可退数不够了
                throw new ServiceException("商品行「" + line.getProductName() + "」还能退的件数已经不够 "
                        + item.getQuantity() + " 件（这一单之外有人动过它），已中止处理");
            }
            quantity += item.getQuantity();
        }
        vo.setReturnedItemCount(items.size());
        vo.setReturnedQuantity(quantity);
        Integer orderStatus = pushOrderRefunded(refund.getOrderId());
        vo.setOrderStatus(orderStatus);
        vo.setReceipts(createReceipts(refund, items, lineMap));
        vo.setGiveBackCouponCount(giveBackCoupons(refund.getOrderId(), orderStatus));
        // 退积分和返券的判据不一样：券只整单退满才返，分是退几件就还几件那一份、也收几件那一份（按行比例，见 ShopOrderPointsService#giveBack）。
        // 传的是 lineMap 里**回写之前**的那几行 —— 上面的 increaseReturnedQuantity 只改库不改内存对象，
        // 所以这一头拿到的是"这一行以前退过几件"，本次那一批由它自己加上去算比例；抵扣和赠送那两格积分也从这几行上读
        vo.setGiveBackPoints(orderPointsService.giveBack(refund, lineMap, items));
        // 第七拍跟着第六拍走：它看的也是"这一批退了多少钱"，不是"整单退满了没"（判据见下面的方法）
        vo.setGiveBackBalance(giveBackBalance(refund));
        return vo;
    }

    /**
     * 第七拍：这一单的支付方式是 4 余额时，把这一张退款单的实退金额退回买家的可用余额。
     *
     * 判据读的是主单上 pay_type 那一格（下单选余额支付时落的、支付回调对过账才留下的），
     * 不是退款单上的字段 —— 一张单只用一种方式付，钱从哪儿出去就得退回哪儿。
     * 退的是 refund.actual_refund_amount（审核人最终定的那一个数），所以分几批退就走几次、每次退那一批的钱，
     * 和第六拍退积分是同一个口径；余额支付付的是整单实付，几批退完正好把这一单付的全还上。
     * 微信 / 支付宝 / 银行卡那三档直接跳过：那些钱没进过钱包，退款也不该出现在钱包流水里。
     */
    private BigDecimal giveBackBalance(SysRefund refund) {
        SysOrder order = sysOrderMapper.selectById(refund.getOrderId());
        if (order == null) {
            return NO_MONEY;
        }
        return orderWalletService.giveBack(order, refund, refund.getActualRefundAmount());
    }

    /**
     * 第五拍：这一单**这次**被推成 4 已退款（整单退满）才返券，部分退款不返 ——
     * 券是按整单买的，退两件货就把券退回，等于买家花同样的钱留了货又白得一张券。
     * 判据取 pushOrderRefunded 的返回值而不是建单时存的 refund_type：那一个才是"这几行现在真的全退满了"。
     * giveBack 按券上的 order_id 捞「1 已使用」那几张做 CAS 退回（和取消订单退券同一条路），
     * 这一单没用过券就是 0，不报错；分几批退完的，返券落在把主单推成 4 的最后那一批上。
     */
    private int giveBackCoupons(Long orderId, Integer orderStatus) {
        if (orderStatus == null || orderStatus != ORDER_STATUS_REFUNDED) {
            return 0;
        }
        return orderCouponService.giveBack(orderId);
    }

    /**
     * 找单：refundId 优先，没给才按 refundNo 查（渠道回调里带的就是下单时给出去的那个商户退款单号）。
     * selectById / selectOne 都带全局逻辑删除，删掉的退款单不该再被回写
     */
    private SysRefund resolveRefund(ShopRefundCallbackDto form) {
        if (form == null) {
            throw new ServiceException("缺少退款通知参数");
        }
        SysRefund refund;
        if (form.getRefundId() != null) {
            refund = sysRefundMapper.selectById(form.getRefundId());
        } else if (form.getRefundNo() != null && !form.getRefundNo().isBlank()) {
            refund = sysRefundMapper.selectOne(new LambdaQueryWrapper<SysRefund>()
                    .eq(SysRefund::getRefundNo, form.getRefundNo().trim()));
        } else {
            throw new ServiceException("请指明是哪张退款单（refundId 或 refundNo）");
        }
        if (refund == null) {
            throw new ServiceException("退款单不存在或已被删除");
        }
        if (refund.getStatus() == null) {
            throw new ServiceException("退款单「" + refund.getRefundNo() + "」状态为空，无法处理退款结果");
        }
        return refund;
    }

    private void assertAmountMatches(SysRefund refund, ShopRefundCallbackDto form) {
        if (form.getRefundAmount() == null || refund.getActualRefundAmount() == null) {
            return;
        }
        BigDecimal given = form.getRefundAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal expected = refund.getActualRefundAmount().setScale(2, RoundingMode.HALF_UP);
        if (given.compareTo(expected) != 0) {
            throw new ServiceException("退款金额 " + given + " 与退款单「" + refund.getRefundNo()
                    + "」的实退金额 " + expected + " 不一致，已拒绝处理");
        }
    }

    /**
     * 状态条件更新没成立、又不是"已经处理过了"的那些：说明这张单压根没走到能登记结果的那一步。
     * 0 待审核 是最常见的一种 —— 报出去让人先去「退款管理」里审核，别绕过审核直接把钱记成退了。
     */
    private void assertCanFinish(SysRefund refund) {
        if (refund.getStatus() != REFUND_STATUS_SUCCESS && refund.getStatus() != REFUND_STATUS_FAILED) {
            throw new ServiceException("退款单「" + refund.getRefundNo() + "」现在是 " + refund.getStatus()
                    + " 档，只有「退款中」（后台审核通过后那一档）的退款单能登记退款结果");
        }
    }

    /**
     * 重复通知：这一单已经记成退款成功了，把库里那几张退货入库单原样报回去（created=false），
     * 明细行不再加第二遍、单子也不再建第二张。已退件数不重算，因为这一笔的贡献已经在库里那几行里了。
     */
    private ShopRefundCallbackVo replayVo(SysRefund refund) {
        ShopRefundCallbackVo vo = baseVo(refund, REFUND_STATUS_SUCCESS);
        vo.setRefundTime(refund.getRefundTime());
        vo.setReturnedItemCount(0);
        vo.setReturnedQuantity(0);
        // 这一路没走到第五、六拍（幂等在状态那一步就止步了），券和分都是第一次通知时返的，这里报 0 表示"这次没返"
        vo.setGiveBackCouponCount(0);
        vo.setGiveBackPoints(0);
        vo.setGiveBackBalance(NO_MONEY);
        SysOrder order = sysOrderMapper.selectById(refund.getOrderId());
        vo.setOrderStatus(order == null ? null : order.getStatus());
        vo.setReceipts(receiptVos(existingReceipts(refund.getId()), false));
        return vo;
    }

    /** 这一单名下的全部明细行（要按行判退满、还要拿 warehouse_id 归仓），按 id 索引 */
    private Map<Long, ShopOrderItem> orderLines(Long orderId) {
        if (orderId == null) {
            throw new ServiceException("退款单没有关联订单，无法回写订单明细");
        }
        List<ShopOrderItem> lines = shopOrderSupport.orderLines(orderId);
        if (lines.isEmpty()) {
            throw new ServiceException("这张退款单对应的订单没有商品明细，无法回写");
        }
        return lines.stream().collect(Collectors.toMap(ShopOrderItem::getId, Function.identity()));
    }

    /**
     * 读 sys_refund.extra 那份"退哪几行、各退几件、各退多少钱"的快照。
     * 只有统一退货接口建出来的单才有这一格：后台手工录的、或者这一版之前的老退款单里存的是别的内容
     * （或者压根没存），这种单子不知道该往哪几行加，报出去让人查，绝不能猜一个"整单全退"把行数错。
     */
    private List<ShopRefundItemVo> parseItems(SysRefund refund) {
        if (refund.getExtra() == null || refund.getExtra().isBlank()) {
            throw new ServiceException("退款单「" + refund.getRefundNo()
                    + "」没有登记退货商品行（不是统一退货接口建的），无法回写订单明细");
        }
        List<ShopRefundItemVo> items;
        try {
            items = JSON.readValue(refund.getExtra(), new TypeReference<List<ShopRefundItemVo>>() {
            });
        } catch (JsonProcessingException e) {
            throw new ServiceException("退款单的退货商品行解析失败，已中止处理");
        }
        if (items == null || items.isEmpty()) {
            throw new ServiceException("退款单「" + refund.getRefundNo() + "」的退货商品行是空的，无法回写订单明细");
        }
        return items;
    }

    /** 这一行到底退不退得动：行要在、数量要正、这次退的不能超过还能退的（建单之后被人动过就报出来） */
    private void assertLineRefundable(ShopOrderItem line, ShopRefundItemVo item) {
        if (line == null) {
            throw new ServiceException("退款单里的商品行 " + item.getOrderItemId() + " 已经不在这张订单里了（被删过？），已中止处理");
        }
        if (item.getQuantity() == null || item.getQuantity() <= 0) {
            throw new ServiceException("商品「" + line.getProductName() + "」的退货数量异常，已中止处理");
        }
        int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
        int returned = line.getReturnedQuantity() == null ? 0 : line.getReturnedQuantity();
        if (returned + item.getQuantity() > quantity) {
            throw new ServiceException("商品「" + line.getProductName() + "」的退货数量超过可退数（共 " + quantity
                    + " 件，已退 " + returned + " 件，这次要退 " + item.getQuantity() + " 件），已中止处理");
        }
    }

    /**
     * 每一行都退满了才把主单推成 4 已退款；还留着能退的行就一分不动（部分退款的主单还是原状态）。
     * 判据用库里的实时行状态而不是建单时算的那个 refund_type：那一份是"当时以为会退满"，
     * 中间别人再退一批、或者人工改过明细，都以现在这几行为准。
     * 条件更新（1/2 → 4）挡住"这期间买家那侧把单子取消了"：推不动就算了，主单停在原状态。
     */
    private Integer pushOrderRefunded(Long orderId) {
        List<ShopOrderItem> lines = shopOrderSupport.orderLines(orderId);
        boolean allReturned = lines.stream().allMatch(line -> {
            int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
            int returned = line.getReturnedQuantity() == null ? 0 : line.getReturnedQuantity();
            return returned >= quantity;
        });
        SysOrder order = sysOrderMapper.selectById(orderId);
        if (order == null) {
            return null;
        }
        if (!allReturned) {
            return order.getStatus();
        }
        int updated = sysOrderMapper.update(null, new LambdaUpdateWrapper<SysOrder>()
                .set(SysOrder::getStatus, ORDER_STATUS_REFUNDED)
                .eq(SysOrder::getId, orderId)
                .in(SysOrder::getStatus, Arrays.asList(ORDER_STATUS_PAID, ORDER_STATUS_FINISHED)));
        if (updated != 0) {
            // 整单退完了 = 这一单不再有要送出去的货。配送单还停在 0 待派单 / 1 待接单的，一并置 5 已取消，
            // 别让送货员在抢单池里看见一趟根本没货可取的配送；已经在路上的（2 及以后）那边看得见，不归这里动
            orderDeliveryService.cancelByOrder(orderId, "来源订单已整单退款");
        }
        return updated == 0 ? order.getStatus() : ORDER_STATUS_REFUNDED;
    }

    /**
     * 按发货仓一家一张建退货入库单：明细先在仓内按规格合并（同一规格分几行订单明细是常事，
     * 入库单是按规格入的，同一张单里两行同规格会让仓库那边看不出到底要入几件）。
     * 已有同一（退款单, 仓）的单子就拿回原单，不重建 —— 正常走不到这一支（幂等在状态那一步就止步了），
     * 留着是给"手工把某一张退款单改回退款中重跑"那种场景兜底。
     */
    private List<ShopRefundCallbackVo.Receipt> createReceipts(SysRefund refund,
                                                              List<ShopRefundItemVo> items,
                                                              Map<Long, ShopOrderItem> lineMap) {
        Map<Long, Map<Long, Group>> byWarehouse = new LinkedHashMap<>();
        for (ShopRefundItemVo item : items) {
            ShopOrderItem line = lineMap.get(item.getOrderItemId());
            if (line.getWarehouseId() == null) {
                throw new ServiceException("商品「" + line.getProductName() + "」这一行没有发货仓库，"
                        + "退货不知道该入哪一家仓，请先在订单明细里补上发货仓库");
            }
            byWarehouse.computeIfAbsent(line.getWarehouseId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(item.getSkuId(), k -> new Group()).add(item.getQuantity(), item.getAmount());
        }

        List<ShopRefundCallbackVo.Receipt> receipts = new ArrayList<>(byWarehouse.size());
        for (Map.Entry<Long, Map<Long, Group>> entry : byWarehouse.entrySet()) {
            receipts.add(createReceipt(refund, entry.getKey(), entry.getValue()));
        }
        return receipts;
    }

    private ShopRefundCallbackVo.Receipt createReceipt(SysRefund refund, Long warehouseId, Map<Long, Group> quantities) {
        ShopErpPurchaseIn exists = purchaseInMapper.selectOne(new LambdaQueryWrapper<ShopErpPurchaseIn>()
                .eq(ShopErpPurchaseIn::getRefundId, refund.getId())
                .eq(ShopErpPurchaseIn::getWarehouseId, warehouseId)
                .orderByAsc(ShopErpPurchaseIn::getId)
                .last("LIMIT 1"));
        if (exists != null) {
            return receiptVo(exists, warehouseName(exists.getWarehouseId()), false);
        }

        // 明细先算出来，才知道这张单的总件数和总金额（主表那两格是明细累加，跟后台手工建单同一口径）
        List<ShopErpPurchaseInItem> lines = new ArrayList<>(quantities.size());
        int totalQuantity = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map.Entry<Long, Group> entry : quantities.entrySet()) {
            Group group = entry.getValue();
            BigDecimal price = inboundPrice(entry.getKey(), warehouseId, group);
            BigDecimal amount = price.multiply(BigDecimal.valueOf(group.quantity)).setScale(2, RoundingMode.HALF_UP);
            ShopErpPurchaseInItem line = new ShopErpPurchaseInItem();
            // receiptId 等主表插完再回填
            line.setSkuId(entry.getKey());
            line.setQuantity(group.quantity);
            line.setReceivedQuantity(0);
            line.setPrice(price);
            line.setAmount(amount);
            lines.add(line);
            totalQuantity += group.quantity;
            totalAmount = totalAmount.add(amount);
        }

        ShopErpPurchaseIn doc = new ShopErpPurchaseIn();
        doc.setReceiptNo(GeneratorNoUtils.getPurchaseInNo());
        doc.setInboundSource(ShopErpPurchaseIn.SOURCE_ONLINE_RETURN);
        doc.setRefundId(refund.getId());
        doc.setWarehouseId(warehouseId);
        doc.setTotalQuantity(totalQuantity);
        doc.setTotalAmount(totalAmount);
        doc.setStatus(RECEIPT_STATUS_PENDING);
        doc.setRemark("退款单「" + refund.getRefundNo() + "」线上退货入库（订单 " + refund.getOrderNo() + "）");
        // purchase_id / supplier_id / user_id 一律留 NULL：这张单没有来源采购单、没有供应商，
        // 回调这一刻也没有后台登录人（谁在「商品入库」页点的「入库」，那边会补操作人）
        try {
            purchaseInMapper.insert(doc);
        } catch (DuplicateKeyException e) {
            // uk_receipt_no：单号是时间戳 + 三位随机，撞上的概率低，但撞上了整单回滚重来比改号简单
            throw new ServiceException("退货入库单号生成冲突，请重新处理退款通知");
        }
        for (ShopErpPurchaseInItem line : lines) {
            line.setReceiptId(doc.getId());
            purchaseInItemMapper.insert(line);
        }
        return receiptVo(doc, warehouseName(warehouseId), true);
    }

    /**
     * 这一批货回仓的单价 = 这一条规格在那家仓此刻的加权成本价（照实抄，仓库点「入库」重算加权平均时等于没动它）。
     * 库存行读不到、或者那一格是空/负数，才退回用这一行的实付单价（退款金额 ÷ 件数）：
     * 那种货本来就没有成本记录，记 0 元会让这家仓的加权成本被拉下去，宁可偏高一档。
     */
    private BigDecimal inboundPrice(Long skuId, Long warehouseId, Group group) {
        if (skuId != null) {
            ShopErpInventory stock = shopErpInventoryMapper.selectOne(new LambdaQueryWrapper<ShopErpInventory>()
                    .select(ShopErpInventory::getCostPrice)
                    .eq(ShopErpInventory::getSkuId, skuId)
                    .eq(ShopErpInventory::getWarehouseId, warehouseId)
                    .last("LIMIT 1"));
            if (stock != null && stock.getCostPrice() != null
                    && stock.getCostPrice().compareTo(BigDecimal.ZERO) >= 0) {
                return stock.getCostPrice().setScale(2, RoundingMode.HALF_UP);
            }
        }
        return group.unitPaidPrice();
    }

    /**
     * 一家仓里同一个规格归的一堆：件数相加、这次的退款金额也相加。
     * 金额只在"库存行抄不到成本价"时才用作兜底单价，正常那一路用不上。
     */
    private static final class Group {

        private int quantity;

        private BigDecimal amount = BigDecimal.ZERO;

        void add(Integer quantity, BigDecimal amount) {
            this.quantity += quantity == null ? 0 : quantity;
            if (amount != null) {
                this.amount = this.amount.add(amount);
            }
        }

        /** 这一堆的实付单价，摊不到件数上（数量为 0）时给 0，别让入库单插进一条 NULL 单价的行 */
        BigDecimal unitPaidPrice() {
            if (quantity <= 0) {
                return BigDecimal.ZERO;
            }
            return amount.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
        }
    }

    /** 已经建出来的那几张（重复通知那一路），按仓归一遍好让报回去的顺序稳定 */
    private List<ShopErpPurchaseIn> existingReceipts(Long refundId) {
        return purchaseInMapper.selectList(new LambdaQueryWrapper<ShopErpPurchaseIn>()
                .eq(ShopErpPurchaseIn::getRefundId, refundId)
                .orderByAsc(ShopErpPurchaseIn::getId));
    }

    private List<ShopRefundCallbackVo.Receipt> receiptVos(List<ShopErpPurchaseIn> docs, boolean created) {
        Map<Long, String> names = warehouseNames(docs.stream()
                .map(ShopErpPurchaseIn::getWarehouseId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));
        return docs.stream().map(doc -> receiptVo(doc, names.get(doc.getWarehouseId()), created))
                .collect(Collectors.toList());
    }

    private ShopRefundCallbackVo.Receipt receiptVo(ShopErpPurchaseIn doc, String warehouseName, boolean created) {
        ShopRefundCallbackVo.Receipt receipt = new ShopRefundCallbackVo.Receipt();
        receipt.setReceiptId(doc.getId());
        receipt.setReceiptNo(doc.getReceiptNo());
        receipt.setWarehouseId(doc.getWarehouseId());
        receipt.setWarehouseName(warehouseName);
        receipt.setTotalQuantity(doc.getTotalQuantity());
        receipt.setCreated(created);
        return receipt;
    }

    private String warehouseName(Long warehouseId) {
        if (warehouseId == null) {
            return null;
        }
        ShopErpWarehouse warehouse = shopErpWarehouseMapper.selectById(warehouseId);
        return warehouse == null ? null : warehouse.getWarehouseName();
    }

    private Map<Long, String> warehouseNames(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return shopErpWarehouseMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ShopErpWarehouse::getId, ShopErpWarehouse::getWarehouseName, (a, b) -> a));
    }

    /** 抬头那几格：退款单本身的信息，成功和失败两路都要带 */
    private ShopRefundCallbackVo baseVo(SysRefund refund, int status) {
        ShopRefundCallbackVo vo = new ShopRefundCallbackVo();
        vo.setRefundId(refund.getId());
        vo.setRefundNo(refund.getRefundNo());
        vo.setStatus(status);
        vo.setActualRefundAmount(refund.getActualRefundAmount());
        vo.setThirdRefundNo(refund.getThirdRefundNo());
        return vo;
    }

    /** 回调带的第三方退款单号：不传就沿用库里那一格（建单时没有，审核时人工补过也有可能） */
    private String thirdNo(SysRefund refund, ShopRefundCallbackDto form) {
        if (form.getThirdRefundNo() != null && !form.getThirdRefundNo().isBlank()) {
            return form.getThirdRefundNo().trim();
        }
        return refund.getThirdRefundNo();
    }
}
