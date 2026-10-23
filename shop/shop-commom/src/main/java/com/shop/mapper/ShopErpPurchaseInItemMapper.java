package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.entity.ShopErpPurchaseInItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 入库单明细 Mapper接口
 */
@Mapper
public interface ShopErpPurchaseInItemMapper extends BaseMapper<ShopErpPurchaseInItem> {

    /**
     * 按入库单查明细（一行是一条规格：先联 shop_product_sku 带规格文本，再由它中转联 shop_product
     * 带出名称/编码；这条规格或所属商品被删过的那行名称为空，前端显示占位）
     */
    List<ShopErpPurchaseInItem> selectItemsByReceiptId(@Param("receiptId") Long receiptId);

    /**
     * 按几张入库单一次查全部明细（列同上，多带 receipt_id 给调用方分组）。
     * 一次捞几单的明细，免得逐单查打成一串 N+1
     */
    List<ShopErpPurchaseInItem> selectItemsByReceiptIds(@Param("receiptIds") List<Long> receiptIds);
}
