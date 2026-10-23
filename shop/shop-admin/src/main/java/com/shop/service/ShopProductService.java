package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.dto.product.ShopProductQueryDto;
import com.shop.dto.product.ShopProductSaveDto;
import com.shop.entity.ShopProduct;
import com.shop.vo.product.ShopProductVo;

import java.util.List;

/**
 * 商品主表(SPU) 服务类
 *
 * 商品与 SKU(shop_product_sku) 是 1:N：本服务只管 shop_product 那一行（名称/分类/品牌/门店/主图/上下架/轮播），
 * 具体规格的读写在 ShopProductSkuService。只有两件是跨表的：新增时连带建一条默认 SKU、
 * 删除时把名下 SKU 一起软删，两次都在同一个事务里。
 * 查询条件用 ShopProductQueryDto，结果用 ShopProductVo，写入用 ShopProductSaveDto，
 * 实体 ShopProduct 只对应 shop_product 的列。
 */
public interface ShopProductService extends IService<ShopProduct> {

    /**
     * 分页查询商品（含价格区间、销量合计、可用库存、类目/品牌/门店名称，都是按名下 SKU 现汇总的）
     */
    IPage<ShopProductVo> selectPage(ShopProductQueryDto query);

    /**
     * 查询单个商品（汇总的那几格同上）
     */
    ShopProductVo selectProductById(Long id);

    /**
     * 新增商品，同时建一条没有具体规格的默认 SKU（这件商品在交易链上得先有个可挂的 id）
     */
    void insert(ShopProductSaveDto form);

    /**
     * 修改商品，只动 shop_product 的列，不碰名下任何一条 SKU
     */
    void update(ShopProductSaveDto form);

    /**
     * 批量删除商品，同时逻辑删除名下所有 SKU
     */
    void deleteByIds(List<Long> ids);
}
