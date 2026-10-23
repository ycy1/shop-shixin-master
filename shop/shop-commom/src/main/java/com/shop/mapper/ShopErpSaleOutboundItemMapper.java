package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.entity.ShopErpSaleOutboundItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 销售出库单明细 Mapper接口
 */
@Mapper
public interface ShopErpSaleOutboundItemMapper extends BaseMapper<ShopErpSaleOutboundItem> {

    /**
     * 按出库单查明细（一行是一条规格：先联 shop_product_sku 带规格文本、再由它中转联 shop_product 带名称/编码；
     * 联 shop_order_item 是为了优先拿下单那一份规格文本快照，只有采购退货那一路没有订单明细行才退回 SKU 当前文本；
     * 联 shop_erp_inventory 按「这一条规格 × 这一仓」带该仓可用库存当出库上限提示。
     * 规格/商品/订单明细被删过的那行名称为空，前端显示占位）
     */
    List<ShopErpSaleOutboundItem> selectItemsByOutboundId(@Param("outboundId") Long outboundId);

    /**
     * 按几张出库单一次查全部明细（列同上，多带 outbound_id 给调用方分组）。
     * 「销售单」页那颗「出库单」弹窗用它：一次捞几张单的明细，免得逐单查打成一串 N+1
     */
    List<ShopErpSaleOutboundItem> selectItemsByOutboundIds(@Param("outboundIds") List<Long> outboundIds);
}
