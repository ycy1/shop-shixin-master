package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shop.api.service.ShopOrderCancelService;
import com.shop.api.service.ShopOrderCouponService;
import com.shop.api.service.ShopOrderDeliveryService;
import com.shop.api.service.ShopOrderPointsService;
import com.shop.api.service.ShopOrderWalletService;
import com.shop.entity.ShopOrderItem;
import com.shop.entity.SysOrder;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopErpInventoryMapper;
import com.shop.mapper.ShopOrderItemMapper;
import com.shop.mapper.SysOrderMapper;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.TransactionUtils;
import com.shop.vo.order.ShopOrderCancelVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 取消订单 服务实现（六件事和"只认未支付"那道闸见接口 {@link ShopOrderCancelService}）
 *
 * 写的顺序是**先翻主单状态，再退库存、券、积分和余额**：主单那一句带 WHERE status = 0，是这一趟唯一的闸门
 *（同一单被点两次、或者正好和支付回调撞上，只有先落的那一句改得动）。闸门放在最前面，
 * 后面几步就只会被成功的那一趟执行一次 —— 反过来先还库存再改状态，两趟并发就会把同一批锁定还两遍，
 * 那一列会直接减成负数（unlockStock 的 WHERE 挡得住，但那时主单已经翻过去了、整单要回滚，不如一开始就别让它抢到）。
 *
 * 库存还回去按**明细行自己那一家仓**逐行还，不按商品汇总：下单是逐行分仓的（同一商品拆两行可能分到两家仓），
 * 支付回调改派时也是逐行挪的（ShopOrderPaidServiceImpl#routeWarehouse），所以
 * shop_order_item.warehouse_id 这一列本身就是"这一行的货锁在哪家仓"的唯一说法。
 */
@Service
@RequiredArgsConstructor
public class ShopOrderCancelServiceImpl implements ShopOrderCancelService {

    private final SysOrderMapper sysOrderMapper;

    private final ShopOrderItemMapper shopOrderItemMapper;

    private final ShopErpInventoryMapper shopErpInventoryMapper;

    /** 第四拍：这一单核销掉的券原路退回（判据只有券上落的 order_id） */
    private final ShopOrderCouponService shopOrderCouponService;

    /** 这一拍把下单冻住的那一笔积分整笔解冻还回可用（这一单没成交，分不该花） */
    private final ShopOrderPointsService shopOrderPointsService;

    /** 这一拍把下单占住的那一笔余额整笔退回可用（这一单没成交，钱不该花；只认 pay_type=4 那一档） */
    private final ShopOrderWalletService shopOrderWalletService;

    /** 下单时就跟着这张订单建好的那条配送单，订单没了它也得跟着没 */
    private final ShopOrderDeliveryService shopOrderDeliveryService;

    /** 订单状态（字典 order_status）：0 待支付 —— 只有这一档是"还没成交，随便取消" */
    private static final int ORDER_STATUS_UNPAID = 0;

    /** 订单状态：3 已取消 —— 取消之后落这一档 */
    private static final int ORDER_STATUS_CANCELLED = 3;

    /** 落到 shop_delivery.cancel_reason 那一格的话（口径同后台把订单状态改成 3 那一路） */
    private static final String DELIVERY_CANCEL_REASON = "来源订单已取消";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopOrderCancelVo cancel(Long orderId) {
        if (orderId == null) {
            throw new ServiceException("请指明要取消的订单");
        }
        LoginUserUtils.requireLoginUser("后再取消订单");
        SysOrder order = sysOrderMapper.selectById(orderId);
        if (order == null) {
            throw new ServiceException("订单不存在或已被删除");
        }
        assertCancellable(order);
        // 消费者账号只能动自己那一张；平台 / 门店 / ERP 账号点这一颗算代客取消（口径同确认收货）
        if (!LoginUserUtils.inOwnScope(order.getUserId())) {
            throw new ServiceException("只能取消自己名下的订单");
        }
        TransactionUtils.requireActive("取消订单");

        List<ShopOrderItem> items = shopOrderItemMapper.selectList(new LambdaQueryWrapper<ShopOrderItem>()
                .select(ShopOrderItem::getId, ShopOrderItem::getSkuId, ShopOrderItem::getProductName,
                        ShopOrderItem::getQuantity, ShopOrderItem::getWarehouseId, ShopOrderItem::getUsedPoints)
                .eq(ShopOrderItem::getOrderId, order.getId()));

        LocalDateTime now = LocalDateTime.now();
        // update(null, wrapper) 不走 MetaObjectHandler 的自动填充（那边只填实体参数），所以 update_time 自己写
        int updated = sysOrderMapper.update(null, new LambdaUpdateWrapper<SysOrder>()
                .set(SysOrder::getStatus, ORDER_STATUS_CANCELLED)
                .set(SysOrder::getUpdateTime, now)
                .eq(SysOrder::getId, order.getId())
                // 状态也进 WHERE：读到「待支付」和写之间要是被支付回调推成 1、或者后台改了状态，这一句就不成立
                .eq(SysOrder::getStatus, ORDER_STATUS_UNPAID));
        if (updated == 0) {
            throw new ServiceException("这一单的状态刚刚被人改过（或已经取消过了），请刷新订单列表");
        }

        int released = releaseStock(items);
        int couponCount = shopOrderCouponService.giveBack(order.getId());
        int releasedPoints = shopOrderPointsService.unfreeze(order, items);
        // 余额那一笔跟着同一批还回去（这一单压根没成交，占住的钱一分都没花出去）：
        // 要退多少钱读的是主单上 pay_type + pay_amount 那两格，不是明细行 —— 余额付的是整单实付
        BigDecimal releasedBalance = shopOrderWalletService.unfreeze(order);
        shopOrderDeliveryService.cancelByOrder(order.getId(), DELIVERY_CANCEL_REASON);

        ShopOrderCancelVo vo = new ShopOrderCancelVo();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(ORDER_STATUS_CANCELLED);
        vo.setCancelTime(now);
        vo.setItemCount(items.size());
        vo.setReleasedQuantity(released);
        vo.setCouponCount(couponCount);
        vo.setReleasedPoints(releasedPoints);
        vo.setReleasedBalance(releasedBalance);
        return vo;
    }

    /**
     * 只放行「0 待支付」，其余每一档各说一句人话（同一颗按钮在买家端和后台都会点到别的档上）。
     *
     * 已付款那几档为什么硬拦：钱已经收了，取消会把一张有交易的单标成"没成交"，
     * 货款两头都不认——要退钱得走统一退货那条链（建退款单 → 退款回调），那边会把该退的件数和金额记下来。
     */
    private void assertCancellable(SysOrder order) {
        Integer status = order.getStatus();
        if (status == null) {
            throw new ServiceException("订单「" + order.getOrderNo() + "」的状态不明，取消不了");
        }
        if (status == ORDER_STATUS_UNPAID) {
            return;
        }
        String why;
        switch (status) {
            case 1:
                why = "这一单已经付款了，要退钱请走退货退款，不能直接取消";
                break;
            case 2:
                why = "这一单已经完成收货，取消不了";
                break;
            case 3:
                why = "这一单已经取消过了";
                break;
            case 4:
                why = "这一单已经退过款了，取消不了";
                break;
            case 5:
                why = "这一单已经关闭了";
                break;
            default:
                why = "只有「待支付」的订单能取消，这一单现在的状态（" + status + "）不行";
                break;
        }
        throw new ServiceException(why);
    }

    /**
     * 把这一单锁住的可用库存逐行还回去（只减 locked_stock，总库存一动不动 —— 货从来没离开过货架）。
     *
     * 还的是下单时 lockStock 加上去的那一份，所以镜像条件也照抄那边：解锁那条 UPDATE 的 WHERE 压着
     * locked_stock >= 本行数量，返回 0 行说明这一家仓压根没这么多可还的（锁定被别处还掉了、
     * 或者行上的 warehouse_id 和实际锁的仓不是同一个）。那都是账对不上，报出去让整单回滚，
     * 绝不能跳过这一行继续往下还。
     *
     * warehouse_id 为空的老数据直接放过：那一单没走过就近分仓，本来也没锁过任何东西。
     *
     * @return 还回去的件数（给调用方回显，明细行数 × 各自数量，跳过的行不算进来）
     */
    private int releaseStock(List<ShopOrderItem> items) {
        int released = 0;
        for (ShopOrderItem item : items) {
            Integer quantity = item.getQuantity();
            if (item.getWarehouseId() == null || quantity == null || quantity <= 0) {
                continue;
            }
            String row = "商品「" + StringUtils.defaultIfBlank(item.getProductName(),
                    "明细 #" + item.getId()) + "」";
            if (shopErpInventoryMapper.unlockStock(item.getSkuId(), item.getWarehouseId(), quantity) == 0) {
                throw new ServiceException(row + "的库存还不清（锁在原来那家仓的那 " + quantity
                        + " 件已经对不上了），已中止取消");
            }
            released += quantity;
        }
        return released;
    }
}
