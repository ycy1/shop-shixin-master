package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.entity.ShopOrderItem;
import com.shop.entity.SysOrder;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopOrderItemMapper;
import com.shop.mapper.SysOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 下单 / 支付回调 / 退货 这三路共用的几段查询和算法（都在 shop-api 这一侧，后台那些 service 用不上）。
 *
 * 放在这里只为去掉抄第二遍、第三遍会抄错的那两处：找单（orderId 优先、orderNo 兜底）和「这一行实付多少」。
 * 业务判据（哪张单能动、这一步允许什么状态）各走各的，不往这儿收。
 */
@Component
@RequiredArgsConstructor
public class ShopOrderSupport {

    private final SysOrderMapper sysOrderMapper;

    private final ShopOrderItemMapper shopOrderItemMapper;

    /** 行实付要减掉积分抵的那一份，而那一份得按 sys_config 的汇率折 —— 折算只有 ShopPointsSupport 那一份 */
    private final ShopPointsSupport shopPointsSupport;

    /**
     * 按 orderId 或 orderNo 找那一张订单，两个都没给、或者找不到都直接拒。
     * selectById / selectOne 都带全局逻辑删除，删掉的订单不该再被下单回调、退货这些动作改到
     */
    public SysOrder requireOrder(Long orderId, String orderNo) {
        SysOrder order;
        if (orderId != null) {
            order = sysOrderMapper.selectById(orderId);
        } else if (orderNo != null && !orderNo.isBlank()) {
            order = sysOrderMapper.selectOne(new LambdaQueryWrapper<SysOrder>()
                    .eq(SysOrder::getOrderNo, orderNo.trim()));
        } else {
            throw new ServiceException("请指明是哪张订单（orderId 或 orderNo）");
        }
        if (order == null) {
            throw new ServiceException("订单不存在或已被删除");
        }
        return order;
    }

    /** 这一单名下的全部商品行，按建单顺序（下单时是一行一行插的，所以 id 升序就是页面上那几行的顺序） */
    public List<ShopOrderItem> orderLines(Long orderId) {
        return shopOrderItemMapper.selectList(new LambdaQueryWrapper<ShopOrderItem>()
                .eq(ShopOrderItem::getOrderId, orderId)
                .orderByAsc(ShopOrderItem::getId));
    }

    /**
     * 这一行订单明细实付了多少 = 小计 − 这一行的优惠 − 这一行积分抵的钱。和统一下单落 sys_order.pay_amount 同一口径，
     * 所以逐行相加正好等于这一单收到的钱 —— 出库单按它结成本、退款单按它退钱，两边才是同一份数。
     *
     * 积分那一份按 sys_config 那一档汇率现折（下单时算的就是同一个式子，见 ShopPointsSupport#moneyOf）：
     * 它不进 discount_amount（那一格只记优惠券，营销账和积分账要分开看），但一样是把钱从这一行上扣走的，
     * 所以退款不能把它退成现金、出库也不能把它算进成本。
     *
     * @param row   已经拼好的行名前缀（"第 2 行商品「XX」"），报错时直接接在后面
     * @param doing 这一步在做什么，拼进报错文案（"无法生成出库单" / "无法计算退款金额"）
     */
    public BigDecimal paidAmount(ShopOrderItem line, String row, String doing) {
        if (line.getTotalAmount() == null) {
            throw new ServiceException(row + "缺少成交金额，" + doing);
        }
        BigDecimal discount = line.getDiscountAmount() == null ? BigDecimal.ZERO : line.getDiscountAmount();
        BigDecimal paid = line.getTotalAmount().subtract(discount).subtract(shopPointsSupport.moneyOf(line.getUsedPoints()));
        if (paid.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException(row + "的优惠和积分抵扣大于成交金额，" + doing);
        }
        return paid.setScale(2, RoundingMode.HALF_UP);
    }
}
