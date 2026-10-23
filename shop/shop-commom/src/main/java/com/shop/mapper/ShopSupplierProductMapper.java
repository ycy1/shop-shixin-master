package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopSupplierProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 供应商商品（供货关系）Mapper接口
 */
@Mapper
public interface ShopSupplierProductMapper extends BaseMapper<ShopSupplierProduct> {

    /**
     * 分页查询供货关系：关键字要跨商品名称/供货编码/规格文本/供应商名称搜，条件写在 XML 里
     * （代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写）。
     * 一行是一条供货规格（(supplier_id, sku_id) 唯一），所属商品的编码/名称由 shop_product_sku 中转联出来
     */
    IPage<ShopSupplierProduct> selectSupplierProductPage(IPage<ShopSupplierProduct> page,
            @Param("q") ShopSupplierProduct query);

    /**
     * 某个供应商「合作中」的供货规格，给采购单明细的商品下拉用，一行是一条规格
     * （这条规格或它所属商品被删过都不候选）
     *
     * @param supplierId 供应商id
     * @param keyword    商品名称/商品编码/规格文本/供货编码关键字，可为空
     * @param limit      最多返回多少条（卡的是规格行数，不是商品个数）
     */
    List<ShopSupplierProduct> selectProductOptionsBySupplier(@Param("supplierId") Long supplierId,
            @Param("keyword") String keyword, @Param("limit") Integer limit);
}
