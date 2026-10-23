package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopErpPurchase;
import com.shop.entity.ShopErpSaleOutbound;
import com.shop.entity.ShopSupplierProduct;

import java.util.List;

/**
 * 采购单 服务接口（主子表：一张单 + 若干明细，一起存一起取）
 */
public interface ShopErpPurchaseService extends IService<ShopErpPurchase> {

    /**
     * 分页查询采购单（列表不带明细）
     *
     * @param query 查询条件（keyword/supplierId/status/beginTime/endTime；ERP 账号会被覆盖成只看自己负责的）
     * @return 采购单分页
     */
    IPage<ShopErpPurchase> selectPage(ShopErpPurchase query);

    /**
     * 采购明细的商品候选：只能选这家供应商在 shop_supplier_product 里标着「合作中」的那些商品，
     * 没建供货关系就没有候选（去「供应商商品」那页建）。
     *
     * @param supplierId 采购单上选的供应商id，必填
     * @param keyword    商品名称/商品编码/供货编码关键字，可为空
     * @return 供货关系行（productId/productName/productCode + supplyPrice/minOrderQty/leadTime）
     */
    List<ShopSupplierProduct> selectProductOptions(Long supplierId, String keyword);

    /**
     * 采购单详情，带明细
     *
     * @param id 采购单id
     * @return 采购单
     */
    ShopErpPurchase selectPurchaseById(Long id);

    /**
     * 新增采购单：单号不传由系统生成，金额/数量按明细重算，明细跟着主表一起插
     *
     * @param purchase 主表 + items
     */
    void insert(ShopErpPurchase purchase);

    /**
     * 修改采购单：明细整单重存（旧的软删、新的重插），金额/数量重算
     *
     * @param purchase 主表 + items
     */
    void update(ShopErpPurchase purchase);

    /**
     * 批量删除（逻辑删除，明细跟着一起软删）
     *
     * @param ids 采购单id集合
     */
    void deleteByIds(List<Long> ids);

    /**
     * 「采购退货」弹窗打开时取的那份草稿（不落库）：这张采购单能退哪些货、每行还能退多少。
     *
     * 返回的是一个还没入库的出库单壳子（outboundSource=3 采购退货、orderId/orderNo 里是这张采购单、
     * status=0 待出库），明细一行一个商品，每行带 receivedQuantity（已入库）和 returnableQuantity
     * （还能退 = 已入库 − 名下退货单已登记的量），quantity 给 0 由操作员填。
     * 一件能退的都没有就直接抛异常（弹窗不弹半截的）。
     *
     * @param purchaseId 采购单id，ERP 账号只能退自己负责的那张
     * @return 出库单草稿（带 items）
     */
    ShopErpSaleOutbound purchaseReturnDraft(Long purchaseId);

    /**
     * 「采购退货」弹窗点了确定：给这张采购单建一张来源=3 采购退货 的出库单（待出库，仓库那边点「出库」才真减库存）。
     *
     * 可多次退货：一张采购单名下能有几张这样的单子，每次建的都占额度（上限的口径见 {@link #purchaseReturnDraft}）。
     * 请求体只认每行的 (productId, warehouseId, quantity, costPrice) 和主表的 remark，
     * 单号、总数、总金额、状态一律服务端算。
     *
     * @param purchaseId 采购单id（路径上来的，不从请求体里认）
     * @param form       退货明细
     * @return 新建的出库单id
     */
    Long createPurchaseReturn(Long purchaseId, ShopErpSaleOutbound form);
}
