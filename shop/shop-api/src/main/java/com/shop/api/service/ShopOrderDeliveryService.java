package com.shop.api.service;

import com.shop.entity.ShopErpSaleOutbound;
import com.shop.entity.ShopOrderItem;
import com.shop.entity.ShopDelivery;
import com.shop.entity.SysOrder;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单侧的配送单：下单建「0 待派单」那一条，支付回调把它换绑到出库单
 *
 * 他要的三句，按时间排："下单时直接生成待派单(包括重量 配送费和距离计算)的配送单这时候先绑定订单 /
 * 待支付回调后换绑出库单 / 订单表添加配送费字段，下单时订单表 订单总金额 把配送费也加进去"。
 *
 * 也就是说这一单的配送数据（重量、距离、配送费）在**买家点提交那一刻**就定死了 ——
 * 那时候还没有人站在货堆前，所以重量只能由 Σ(商品重量 × 数量) 算、发货点只能按下单分到的那家仓定；
 * 而配送费是要跟货款一起收的钱，收完就不能再随规则变（要改只能退款重下）。
 */
public interface ShopOrderDeliveryService {

    /**
     * 按这一单派生出那条配送单，**不落库**：下单要拿它算出来的配送费写进 sys_order 的金额，
     * 所以得先有数、再落主单，最后才 {@link #createOnOrder} 插这一行。
     *
     * 拿不到发货点/坐标时距离和重量会是空 —— 空就空着，不拦下单（那一单该不该卖出去，
     * 跟它的配送数据全不全没关系）；配送费在缺数据的那几档上退化成只收起步价。
     *
     * delivery_type 由这两个点是否同城决定（同城 1 平台配送 / 异地 3 快递发货），判不出同城留 1；
     * 它挑的是哪一档计价规则，所以先定类型再算钱。
     *
     * @param goodsAmount 这一单的货值实付（Σ 各行小计 − Σ 各行优惠），"实付满多少免配送费"那一档看的是它，
     *                    不是含了配送费的金额
     */
    ShopDelivery buildDraft(SysOrder order, List<ShopOrderItem> items, BigDecimal goodsAmount);

    /**
     * 把 {@link #buildDraft} 那一份落库，状态 0 待派单，绑在这张订单上。
     * 表上没有单独的订单列（他那句"不需要加 order_id/order_no，直接使用出库单号和出库id，这时候出库单还没有生成"）：
     * 订单的 id 和号就先写进 outbound_id / outbound_no 那一槽，下一个方法再把这一槽换成出库单的。
     * 在统一下单那个事务里调用：主单/明细/这一行同生共死。
     */
    void createOnOrder(SysOrder order, ShopDelivery draft);

    /**
     * 支付回调换绑：把这一单配送单上那一槽从「订单的两个值」覆盖成「出库单的两个值」（outbound_id / outbound_no），
     * 顺带按回调改派之后的发货仓刷一遍发货点和距离 —— 货实际从哪家仓出，取货地点就得是哪家。
     *
     * 找那一条要 id + 号两个一起配（只配 id 会把还没换绑的错认成同号出库单的配送单，见实现里的 findByBinding）。
     *
     * 配送费和费用规则 id **不动**：那笔钱买家已经连着货款付掉了（就在 sys_order.delivery_fee 里），
     * 事后按新距离重算等于偷偷改账，要改得走退款。所以这一单上可能出现"距离是改派后的、钱是改派前算的"，
     * 这是有意的。
     *
     * 这一单名下还没有配送单（统一下单建配送单这套上线之前下的老订单）时，当场补建一条并直接绑上出库单。
     * 已经确认过（状态不是 0 待派单）的那一条一概不碰。
     */
    void rebindToOutbound(SysOrder order, ShopErpSaleOutbound outbound);

    /**
     * 来源订单取消 / 关闭 / 整单退款时，把这一单名下那条还没人接手的配送单置成 **5 已取消**
     *（他要的口径："订单没支付或被取消时，配送单状态置为 5 已取消"）。
     *
     * 只翻 0 待派单 和 1 待接单 这两档 —— 货已经在送货员手上（2/3/4）就不归这里改，那是配送那一头的事；
     * 这一档也不硬拦支付回调（{@link #rebindToOutbound} 看到状态不是 0 就不动了）。
     * 查不到配送单、或者那条已经过了这两档，都安静地什么都不做：取消订单不该被一条配送单卡住。
     *
     * 留行不删：留着 5 已取消 这一条，「配送管理」里才查得到"这一单曾经要发、后来不发了"。
     *
     * @param orderId sys_order.id
     * @param reason  取消原因，落到 cancel_reason（为空时给一句兜底话）
     */
    void cancelByOrder(Long orderId, String reason);
}
