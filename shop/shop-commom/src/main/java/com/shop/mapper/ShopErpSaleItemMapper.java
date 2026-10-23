package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.entity.ShopErpSaleItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对外销售单明细 Mapper接口
 */
@Mapper
public interface ShopErpSaleItemMapper extends BaseMapper<ShopErpSaleItem> {

    /**
     * 按销售单查明细（一行是一条规格：先联 shop_product_sku 带规格文本，再由它中转联 shop_product
     * 带出名称/编码；这条规格或所属商品被删过的那行名称为空，前端显示占位）
     */
    List<ShopErpSaleItem> selectItemsBySaleId(@Param("saleId") Long saleId);
}
