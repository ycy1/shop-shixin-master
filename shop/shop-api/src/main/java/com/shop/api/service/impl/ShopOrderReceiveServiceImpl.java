package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shop.api.service.ShopOrderReceiveService;
import com.shop.entity.ShopOrderItem;
import com.shop.entity.ShopStore;
import com.shop.entity.SysOrder;
import com.shop.enums.NotificationTypeEnum;
import com.shop.event.NotificationEvent;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopOrderItemMapper;
import com.shop.mapper.ShopStoreMapper;
import com.shop.mapper.SysOrderMapper;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.NotificationsUtil;
import com.shop.vo.order.ShopOrderReceiveVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 订单确认收货 服务实现
 *
 * 为什么放在 shop-api：买家在自己那一端点「确认收货」，和后台「我的订单」详情弹窗里代客点同一颗按钮，
 * 落的必须是同一个状态，所以入口和统一下单、统一接单一样走 /api/comm（前端调 comm 要写双前缀，
 * 因为 request 的 baseURL 会再叠一层 /api，见 ShopPayCommonController 那几段的说明）。
 *
 * 鉴权同那一路：/api/** 不过 Sa-Token 拦截器，所以这里自己取登录态，取不到就报"请先登录后再确认收货"。
 * **谁能确认哪一单**判的是账号类型（口径同统一退货 ShopRefundCreateServiceImpl）：
 * 消费者账号只能确认自己名下的单，其它账号（平台/门店/ERP）算代客确认。
 *
 * 一次写一句：sys_order.status 1 已支付 → 2 已完成，带条件更新（读到的那两档没被人动过才改得动），
 * 所以两个人同时点同一单只有一个成功，另一个拿到的是"状态刚刚被人改过"。
 */
@Service
@RequiredArgsConstructor
public class ShopOrderReceiveServiceImpl implements ShopOrderReceiveService {

    private final SysOrderMapper sysOrderMapper;

    private final ShopOrderItemMapper shopOrderItemMapper;

    private final ShopStoreMapper shopStoreMapper;

    private final NotificationsUtil notificationsUtil;

    /** 订单状态（字典 order_status）：1 已支付 —— 只有这一档谈得上"收到货"（0 还没付钱，2 已经收过了） */
    private static final int ORDER_STATUS_PAID = 1;

    /** 订单状态：2 已完成 —— 确认收货之后落这一档 */
    private static final int ORDER_STATUS_FINISHED = 2;

    /**
     * 明细行上"货已经有结论了"的那几档（字典 order_ship_status）：1 已发货，以及退了货的
     * 4 部分退货 / 2 已退货。剩下 0 待发货、3 已出库 是货还没发出去，卡住不让收。
     */
    private static final List<Integer> RECEIVABLE_SHIP_STATUS = Arrays.asList(
            ShopOrderItem.SHIP_STATUS_SHIPPED, ShopOrderItem.SHIP_STATUS_PARTIAL_RETURNED,
            ShopOrderItem.SHIP_STATUS_RETURNED);

    @Override
    public ShopOrderReceiveVo receive(Long orderId) {
        if (orderId == null) {
            throw new ServiceException("请指明要确认收货的订单");
        }
        LoginUserUtils.requireLoginUser("后再确认收货");
        SysOrder order = sysOrderMapper.selectById(orderId);
        if (order == null) {
            throw new ServiceException("订单不存在或已被删除");
        }
        Integer status = order.getStatus();
        if (status == null || status != ORDER_STATUS_PAID) {
            throw new ServiceException(status != null && status == ORDER_STATUS_FINISHED
                    ? "这一单已经确认过收货了"
                    : "只有「已支付」的订单能确认收货，这一单现在的状态不行");
        }
        // 消费者账号只能动自己那一张；平台 / 门店 / ERP 账号算代客确认，收没收到货问的是订单的下单人
        if (!LoginUserUtils.inOwnScope(order.getUserId())) {
            throw new ServiceException("只能确认自己名下的订单");
        }
        List<ShopOrderItem> items = loadItems(order);
        assertReceivable(order, items);

        LocalDateTime now = LocalDateTime.now();
        // update(null, wrapper) 不走 MetaObjectHandler 的自动填充（那边只填实体参数），所以 update_time 自己写
        int updated = sysOrderMapper.update(null, new LambdaUpdateWrapper<SysOrder>()
                .set(SysOrder::getStatus, ORDER_STATUS_FINISHED)
                .set(SysOrder::getUpdateTime, now)
                .eq(SysOrder::getId, order.getId())
                // 状态也进 WHERE：读到「已支付」和写之间要是被退款回调推成 4、或者后台改了状态，这一句就不成立
                .eq(SysOrder::getStatus, ORDER_STATUS_PAID));
        if (updated == 0) {
            throw new ServiceException("这一单的状态刚刚被人改过（或已经确认过收货了），请刷新订单列表");
        }

        ShopOrderReceiveVo vo = new ShopOrderReceiveVo();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(ORDER_STATUS_FINISHED);
        vo.setReceiveTime(now);
        vo.setItemCount(items.size());
        publishReceiveNotice(order, items);
        return vo;
    }

    /**
     * 签收提醒发两拨人：买家本人（自己下的单收到了，记一笔闭环）和发货门店的店长（这单的货从他那儿出的）。
     *
     * 一条事件、多个收件人 —— 一张单的明细可能出自几家店，店长去重后一起放进 userIds，
     * 而不是发 N 条几乎一样的消息（主表那一行也只落一条）。买家自己就是店长时不重复，
     * 门店没配 manager_id 的那几家直接跳过。这一颗不在事务里，靠监听器那句 fallbackExecution 兜住。
     */
    private void publishReceiveNotice(SysOrder order, List<ShopOrderItem> items) {
        List<Long> userIds = new ArrayList<>();
        if (order.getUserId() != null) {
            userIds.add(order.getUserId());
        }
        List<Long> storeIds = new ArrayList<>();
        for (ShopOrderItem item : items) {
            Long storeId = item.getStoreId();
            if (storeId != null && !storeIds.contains(storeId)) {
                storeIds.add(storeId);
            }
        }
        for (Long storeId : storeIds) {
            ShopStore store = shopStoreMapper.selectById(storeId);
            Long managerId = store == null ? null : store.getManagerId();
            if (managerId != null && !userIds.contains(managerId)) {
                userIds.add(managerId);
            }
        }
        if (userIds.isEmpty()) {
            return;
        }
        notificationsUtil.publish(NotificationEvent.builder()
                .type(NotificationTypeEnum.RECEIVE)
                .businessId(order.getId())
                .title("订单已签收")
                .message("订单「" + order.getOrderNo() + "」已确认收货，交易完成")
                .userIds(userIds)
                .build());
    }

    /** 这一单名下的商品行（只取判据和签收提醒要用的那五格，别把快照全捞回来） */
    private List<ShopOrderItem> loadItems(SysOrder order) {
        return shopOrderItemMapper.selectList(new LambdaQueryWrapper<ShopOrderItem>()
                .select(ShopOrderItem::getId, ShopOrderItem::getSkuId, ShopOrderItem::getProductName,
                        ShopOrderItem::getShipStatus, ShopOrderItem::getStoreId)
                .eq(ShopOrderItem::getOrderId, order.getId()));
    }

    /**
     * 收货前的那道闸：**这一单名下每一行商品都得"货已有结论"** —— 1 已发货，或者退了货
     * （4 部分退货 / 2 已退货）；还停在 0 待发货 / 3 已出库 的行是压根没发出去，拒。
     *
     * 为什么判得这么死：明细行那一档是统一接单写上去的（货交到送货员手上那一刻才算发出去，
     * 见 ShopDeliveryAcceptServiceImpl），所以"全都已发货"就是"发出去的货都到手上了"。
     * 退货那两档算放行（2026-09-22 他点头）：一行退了货就不会再补发货，卡着等于让整单永远停在
     * 「已支付」收不了尾。**整单退满那一张不会被误标成完成** —— 退款回调已经把它推成 4 已退款，
     * 上面那道"只有已支付能收"的状态闸先拦下。
     *
     * 行数为零（老数据、或手工建的主单没明细）也拒：一张没有商品的订单标"已完成"没有意义。
     */
    private void assertReceivable(SysOrder order, List<ShopOrderItem> items) {
        if (items.isEmpty()) {
            throw new ServiceException("订单「" + order.getOrderNo() + "」没有商品明细，无法确认收货");
        }
        List<String> notShipped = new ArrayList<>();
        for (ShopOrderItem item : items) {
            if (!RECEIVABLE_SHIP_STATUS.contains(item.getShipStatus())) {
                notShipped.add(StringUtils.defaultIfBlank(item.getProductName(),
                        "明细 #" + item.getId()));
            }
        }
        if (!notShipped.isEmpty()) {
            throw new ServiceException("这一单还有 " + notShipped.size() + " 行商品没发货（"
                    + String.join("、", notShipped) + "），全部「已发货」或「已退货」之后才能确认收货");
        }
    }
}
