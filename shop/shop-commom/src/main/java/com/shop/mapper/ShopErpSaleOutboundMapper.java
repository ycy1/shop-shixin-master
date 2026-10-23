package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopErpSaleOutbound;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 销售出库单 Mapper接口（写法同 ShopErpPurchaseInMapper：列表和详情都走自定义 SQL 联表带名称）
 */
@Mapper
public interface ShopErpSaleOutboundMapper extends BaseMapper<ShopErpSaleOutbound> {

    /**
     * 分页查询出库单：关键字跨出库单号/订单号/备注/下单人昵称/商品名，条件写在 XML 里
     * （代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写）
     */
    IPage<ShopErpSaleOutbound> selectSaleOutboundPage(IPage<ShopErpSaleOutbound> page,
            @Param("q") ShopErpSaleOutbound query);

    /**
     * 单条出库单（带订单号、下单人、订单状态、已出库总数，不带明细——明细由 ShopErpSaleOutboundItemMapper 另查）
     */
    ShopErpSaleOutbound selectSaleOutboundById(@Param("id") Long id);

    /**
     * 一张销售单名下的全部出库单（来源=2 那一档，形状照 ShopErpPurchaseInMapper#selectReceiptsByPurchaseId）：
     * 「销售单」页那颗「出库单」弹窗用，一次给全、不分页，明细由调用方批量另填
     */
    List<ShopErpSaleOutbound> selectSaleOutboundsBySaleId(@Param("saleId") Long saleId);
}
