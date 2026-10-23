package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopErpSaleItem;
import com.shop.entity.ShopErpSaleOutbound;

import java.util.List;

/**
 * 销售出库单 服务接口（主子表：一张单 + 若干明细）
 *
 * 明细的行由来源单据决定，六档来源、四条建单的路：
 * 1 线上购买 —— 支付回调按订单明细自动生成（见 shop-api 的 ShopOrderPaidService）；
 * 2 销售出库 —— 本页那颗「新建」按对外销售单建（{@link #insert}）；
 * 3 采购退货 —— 「采购单」页那颗「采购退货」建（见 ShopErpPurchaseService#createPurchaseReturn）。
 * 另外 4 调拨出库 / 5 报损出库 / 6 其他 也从「新建」建（同一颗，只是那三路没有来源单据、商品逐行手挑）。
 * 所以这个接口只有 查 / 新建（2/4/5/6 那四档）/ 删，和「出库」「生成配送单」（弹框里点「确认」才派单）两个动作
 * （配送单不由这里建，统一下单那一刻就建好了）。这一版仍然没有「修改」：行是来源单据给的，
 * 建错了就删掉重建（只有一张货都没出过的单子删得掉，见 {@link #deleteByIds}）
 */
public interface ShopErpSaleOutboundService extends IService<ShopErpSaleOutbound> {

    /**
     * 分页查询出库单（列表不带明细）
     *
     * @param query 查询条件（keyword/orderId/outboundSource/status/userId/beginTime/endTime）
     * @return 出库单分页
     */
    IPage<ShopErpSaleOutbound> selectPage(ShopErpSaleOutbound query);

    /**
     * 出库单详情，带明细（明细连商品名/仓库名/规格/该仓可用库存一起带出，出库弹窗读的就是它）
     *
     * @param id 出库单id
     * @return 出库单
     */
    ShopErpSaleOutbound selectSaleOutboundById(Long id);

    /**
     * 一张销售单名下的全部出库单，每张连明细（items）一起返回（「销售单」页那颗「出库单」弹窗用）
     *
     * 只捞来源=2 销售出库 那一路（弹窗里看的就是"这张销售单出到哪一步了"），一次给全、不分页。
     * 和 {@link #selectPage} 同一个口径：刻意不按登录账号收窄 —— 出库单的 user_id 记的是"最后谁点的出库"，
     * 一张还没出库的单子那一格是 NULL，按它筛负责这张单的操作员就看不见自己刚建的单子了
     *
     * @param saleId 销售单id，必填
     * @return 出库单列表（每张带各自的明细）
     */
    List<ShopErpSaleOutbound> selectSaleOutboundBySale(Long saleId);

    /**
     * 「新建出库单」选销售单时带出的明细候选（每行给可出库数量：销售数量扣掉这张单下其它
     * 来源=2 的出库单已登记的量）
     *
     * @param saleId 销售单id，必填
     * @param excludeOutboundId 正在改的那张出库单id（这一版没有修改，恒为 null，留着和入库那边对齐口径）
     * @return 销售明细行（原样带 sale_id / quantity / shipped_quantity，另填 availableQuantity）
     */
    List<ShopErpSaleItem> selectSaleItems(Long saleId, Long excludeOutboundId);

    /**
     * 新建一张出库单（本页那颗「新建」）：来源认 2 销售出库 / 4 调拨出库 / 5 报损出库 / 6 其他 四档
     * （弹窗里下拉就是这四个，默认 2），请求体只认 outboundSource、orderId（销售单id，只有 2 那一路要）、
     * 每行的 (productId, warehouseId, quantity, costPrice) 和 remark，
     * 单号、总数、总金额、状态、order_no 一律服务端算（1 线上购买 由支付回调建、3 采购退货 由采购单页建，这两档挡掉）
     *
     * 可多次出库：2 那一路一张销售单名下能有几张这样的单子，上限是按商品汇总的「销售数量 − 名下已登记量」
     * （登记数占额度，不是实出数，否则两张单子能把同一批货出两遍）；4/5/6 那三路没有来源单据，
     * 也就没有额度这道闸，能出多少由出库那一步按仓库库存 CAS 说了算。
     * 建出来的是 0 待出库，货要等这边点「出库」才真离仓。
     *
     * @param form 主表（outboundSource 必填；来源=2 时 orderId 必填）+ items
     * @return 新建的出库单id
     */
    Long insert(ShopErpSaleOutbound form);

    /**
     * 按出库进度重算来源销售单的状态（出库单的 新建 / 删 和出库动作、销售单改单后各带一次）：
     * 每个商品的销售量都被名下出库单登记满了 → 2 待出库；实出数追平销售量 → 3 已出库；
     * 还有商品没登记满 → 退回 1 已审核。0 待审核 / 4 已取消 不参与推导（别替它们跳级）
     *
     * @param saleId 销售单id，为 null 直接返回
     */
    void refreshSaleStatus(Long saleId);

    /**
     * 批量删除（逻辑删除，明细跟着一起软删）
     *
     * 出过货的单子不给删：库存已经按这张单减过账、流水也记了，删单不会把货退回仓里
     *
     * @param ids 出库单id集合
     */
    void deleteByIds(List<Long> ids);

    /**
     * 执行出库（列表上那个「出库」按钮）：按明细行的「本次出库数」给库存减账，同时把下单时锁住的那份还掉，
     * 记库存流水（变动类型按来源分档：1 线上购买 → 3 下单出库、2 销售出库 → 2、3 采购退货 → 9）、
     * 刷仓库已用容量，累加这单的已出库数量，
     * 并把货出满的那几行订单明细标成「已出库」（shop_order_item.ship_status 0 → 3）。
     *
     * 允许一张单分几次出：请求体只认 (明细行id, 本次出库数, 出库成本价)，其余都以库里的行为准。
     * 不碰 sys_order.status：那一列没有「已出库/已发货」（2026-09-21 下线，按行记在明细上）。
     * 来源=1（线上购买）时成本价在支付回调就按订单实付金额定死了，前端那一格禁用、传了也不认。
     *
     * @param form 只有 id 和 items（每行 id + thisQuantity + costPrice）有意义
     */
    void outbound(ShopErpSaleOutbound form);

    /**
     * 把这张出库单绑着的那条配送单从「0 待派单」确认成「1 待接单」（列表上那颗「确认」）
     * （2026-09-21 他改的口径："待所有商品出库后 生成配送单 只是查询该出库单的配送单 / 确认后配送单改为待接单"）。
     *
     * 这里只转发：配送单不是这颗按钮建的（它在统一下单时建出来、支付回调换绑到这张出库单上），
     * 判据全在 ShopDeliveryService#confirmDelivery（这张单出满没有、绑没绑上配送单、
     * 那条还是不是 0 待派单），判不过直接抛异常说明原因（点按钮的人要看得到为什么）。
     *
     * @param id 出库单id
     */
    void confirmDelivery(Long id);
}
