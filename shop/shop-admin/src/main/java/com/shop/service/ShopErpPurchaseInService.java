package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopErpPurchaseIn;
import com.shop.entity.ShopErpPurchaseItem;

import java.util.List;

/**
 * 入库单 服务接口（主子表：一张单 + 若干明细，一起存一起取，形状同采购单）
 */
public interface ShopErpPurchaseInService extends IService<ShopErpPurchaseIn> {

    /**
     * 分页查询入库单（列表不带明细）
     *
     * @param query 查询条件（keyword/purchaseId/supplierId/warehouseId/status/userId/beginTime/endTime）
     * @return 入库单分页
     */
    IPage<ShopErpPurchaseIn> selectPage(ShopErpPurchaseIn query);

    /**
     * 入库单详情，带明细
     *
     * @param id 入库单id
     * @return 入库单
     */
    ShopErpPurchaseIn selectPurchaseInById(Long id);

    /**
     * 一张采购单名下所有的入库单，每张连明细一起带出来（给采购单页那个「入库单」弹窗用：
     * 列表接口不带明细，逐张调详情会打成 N+1，所以这里主表一次查、明细按 receipt_id 一次批量查再分组）。
     *
     * 只读、不改任何数据；ERP 账号只能看自己操作的那些（先按采购单的 user_id 挡一次，再按入库单的 user_id 过滤）
     *
     * @param purchaseId 采购单id
     * @return 入库单列表（新单在前，每张带 items），一张还没建就返回空列表
     */
    List<ShopErpPurchaseIn> selectPurchaseInByPurchase(Long purchaseId);

    /**
     * 选采购单时带出的入库明细候选：把这张采购单的明细行原样给出，每行另带 availableQuantity
     * （可入库数量 = 该商品在采购单里的采购数量合计 − 这张采购单下**其它**入库单已登记的量，
     * 同一商品的几行按行序分摊）。口径和服务端 validate 一致，所以默认值不会一提交就超量。
     *
     * @param purchaseId       采购单id
     * @param excludeReceiptId 正在修改的那张入库单：它自己已登记的量不能算成别人占的额度，新增时传 null
     * @return 采购明细行（含商品名/编码、采购数量、已入库数量、可入库数量）
     */
    List<ShopErpPurchaseItem> selectPurchaseItems(Long purchaseId, Long excludeReceiptId);

    /**
     * 新增入库单：单号不传由系统生成，供应商按采购单回填，金额/数量按明细重算，明细跟着主表一起插
     *
     * @param purchaseIn 主表 + items
     */
    void insert(ShopErpPurchaseIn purchaseIn);

    /**
     * 修改入库单：明细整单重存（旧的软删、新的重插），供应商跟着采购单重取，金额/数量重算
     *
     * @param purchaseIn 主表 + items
     */
    void update(ShopErpPurchaseIn purchaseIn);

    /**
     * 批量删除（逻辑删除，明细跟着一起软删）
     *
     * @param ids 入库单id集合
     */
    void deleteByIds(List<Long> ids);

    /**
     * 执行入库（列表上那个「入库」按钮）：按明细行的「本次入库数」给库存加账、刷加权成本价，
     * 累加这单的已入库数量和来源采购单明细的已入库数量，再把两边的状态按进度往前推。
     *
     * 允许一张单分几次入：请求体只认 (明细行id, 本次入库数, 本次单价)，其余都以库里的行为准。
     *
     * @param purchaseIn 只有 id 和 items（每行 id + thisQuantity + price）有意义
     */
    void receive(ShopErpPurchaseIn purchaseIn);

    /**
     * 按入库进度重算来源采购单的状态，由入库单的增 / 改 / 删和入库动作带出来（采购单改明细时也调一次）。
     *
     * 口径：每个商品的采购量都被这张单下的入库单登记满 → 4 待入库；实际入满（已入库数 ≥ 采购数）→ 2 已入库；
     * 还有商品没登记完 → 1 已审核。0 待审核 / 3 已退货不参与推导，原样留着。
     *
     * @param purchaseId 采购单id，为 null 直接返回（没有来源采购单的入库单不影响任何状态）
     */
    void refreshPurchaseStatus(Long purchaseId);
}
