package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopErpSale;
import com.shop.vo.product.ShopProductVo;

import java.util.List;

/**
 * 对外销售单 服务接口（主子表：一张单 + 若干明细，一起存一起取）
 *
 * 出库那一条链（按这张单建 来源=2 的出库单、把实出数回写进明细、按进度推状态）不在本服务里，
 * 归 {@link ShopErpSaleOutboundService}：那边只有它才知道库存和仓库，这一页只管"卖什么、卖多少、卖多少钱"
 */
public interface ShopErpSaleService extends IService<ShopErpSale> {

    /**
     * 分页查询销售单（列表不带明细）
     *
     * @param query 查询条件（keyword/storeId/warehouseId/status/beginTime/endTime；ERP 账号会被覆盖成只看自己负责的）
     * @return 销售单分页
     */
    IPage<ShopErpSale> selectPage(ShopErpSale query);

    /**
     * 销售明细的商品候选：**一行一个规格**（shop_product_sku），取这家门店（shop_product.store_id）
     * 名下、且这条规格标着「可销售」（shop_product_sku.sale_enable = 1）的那些；
     * 传了发货仓库再按那一条规格在那个仓的可用库存收窄一档
     *
     * @param storeId     销售单上选的门店id，必填
     * @param warehouseId 销售单上选的发货仓库id，可为空（为空就是"出库时再定仓"，不按库存收窄）
     * @param keyword     商品名称/商品编码/SKU编码关键字，可为空
     * @return 商品（id 是 SPU id，skuId/specText 是这一行选中的规格，mainImage + productPrice
     *         取自这一条规格，当销售单价的默认值）
     */
    List<ShopProductVo> selectProductOptions(Long storeId, Long warehouseId, String keyword);

    /**
     * 销售单详情，带明细
     *
     * @param id 销售单id
     * @return 销售单
     */
    ShopErpSale selectSaleById(Long id);

    /**
     * 新增销售单：单号不传由系统生成，金额/数量按明细重算，明细跟着主表一起插
     *
     * @param sale 主表 + items
     */
    void insert(ShopErpSale sale);

    /**
     * 修改销售单：明细整单重存（旧的软删、新的重插），金额/数量重算
     *
     * @param sale 主表 + items
     */
    void update(ShopErpSale sale);

    /**
     * 批量删除（逻辑删除，明细跟着一起软删）
     *
     * @param ids 销售单id集合
     */
    void deleteByIds(List<Long> ids);
}
