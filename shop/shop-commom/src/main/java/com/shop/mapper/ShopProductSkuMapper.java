package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.dto.product.ShopProductSkuQueryDto;
import com.shop.entity.ShopProductSku;
import com.shop.vo.product.ShopProductSkuVo;
import com.shop.vo.product.ShopProductSkuWebVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品SKU表 Mapper接口
 */
@Mapper
public interface ShopProductSkuMapper extends BaseMapper<ShopProductSku> {

    /**
     * 某个商品下的全部 SKU（后台那颗「SKU管理」弹窗用的，不分页），
     * 商品编码/名称/主图和分类名、品牌名、门店名由联表带出，可用库存和成本价按这一条 SKU 现汇总。
     * 逻辑删除的条件在 XML 里手写（自定义 SQL 不走 MP 的自动接管）。
     */
    List<ShopProductSkuVo> selectByProductId(@Param("productId") Long productId);

    /**
     * SKU 分页：给「按规格挑一行」的入口用（商品库选择器、优惠券适用范围）。
     * 要跨商品筛，所以联 shop_product 出编码/名称，分类、门店、上下架都在商品那一侧。
     */
    IPage<ShopProductSkuVo> selectSkuPage(IPage<ShopProductSkuVo> page, @Param("q") ShopProductSkuQueryDto query);

    /**
     * 前台详情页规格选择器那一份：只给能公开的那几格（不含成本价、预警参数和留痕字段），
     * 口径同 ShopProductMapper 里 webColumns 分成两份列清单的理由。
     * sale_enable 不在这里筛 —— 下架商品在读商品那一步已经被挡掉了，而「这个规格不给单卖」
     * 是跟着这一格给出去、由页面决定怎么表现（摆灰那颗按钮），后台的判据同 ShopProductWebVo 那条注释。
     */
    List<ShopProductSkuWebVo> selectWebSkusByProductId(@Param("productId") Long productId);

    /**
     * 销量累加：SKU 表上的 sales_count 是冗余列（列表/详情直接读它，不去 SUM 订单），
     * 由支付回调按这一单每个规格买走的件数加一次（见 shop-api 的 ShopOrderPaidServiceImpl#createOutbound）。
     *
     * 走条件增量而不是「读旧值 +1 写回」：两个买家同时付款时后一个会盖掉前一个，这条不加锁也不会丢。
     * 现在按 skuId 命中：订单明细那一行本来就是一个具体规格，不用再像 1:1 时代那样先按商品合并。
     *
     * @return 影响行数，0 = 这条 SKU 不在了（历史上被删过的规格才会有），由调用方决定要不要管
     */
    int increaseSalesCount(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);
}
