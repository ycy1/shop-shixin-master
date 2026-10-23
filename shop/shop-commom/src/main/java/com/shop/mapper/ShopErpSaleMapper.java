package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopErpSale;
import com.shop.vo.product.ShopProductVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对外销售单 Mapper接口
 *
 * 列表和详情都走这里的自定义 SQL：门店名、仓库名、操作员昵称要联表带出来，
 * 而 BaseMapper 的 selectById 挂不上联表，两条路各写一条才不会一边有一边没有。
 */
@Mapper
public interface ShopErpSaleMapper extends BaseMapper<ShopErpSale> {

    /**
     * 分页查询销售单：关键字跨单号/备注/门店名称，条件写在 XML 里
     * （代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写）
     */
    IPage<ShopErpSale> selectSalePage(IPage<ShopErpSale> page, @Param("q") ShopErpSale query);

    /**
     * 单条销售单（带门店名、仓库名、操作员昵称，不带明细——明细由 ShopErpSaleItemMapper 另查）
     */
    ShopErpSale selectSaleById(@Param("id") Long id);

    /**
     * 销售单明细的商品候选，一行是一条规格：只看属于这家门店、且这条规格标了「可销售」
     * （shop_product_sku.sale_enable，和这件商品上不上架无关，所以不判 shop_product.status）。
     * 传了 warehouseId 再加一档：这个仓库里得有**这条规格**的可用库存（stock - locked_stock > 0），
     * 仓里没货的规格开进单子也出不了货。
     * 返回行带 skuId/specText/productPrice（单价是这一条规格的售价，当下拉选中后销售单价的默认值），
     * id 仍然是所属商品那一格
     * 走本模块的 SQL 而不是 /shop/product/list，是为了让只有进销存权限的 ERP 操作员也调得动
     */
    List<ShopProductVo> selectProductOptions(@Param("storeId") Long storeId,
            @Param("warehouseId") Long warehouseId, @Param("keyword") String keyword,
            @Param("limit") int limit);
}
