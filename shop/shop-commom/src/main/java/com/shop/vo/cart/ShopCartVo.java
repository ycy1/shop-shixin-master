package com.shop.vo.cart;

import com.shop.entity.ShopConsumerCart;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 购物车列表/详情视图对象
 *
 * 商品名称、主图、规格文本、单价与各个关联名称都是联表带出的，不是 shop_consumer_cart 的列，
 * 所以只出现在 VO 里，不污染实体（这一行记的是某个规格：名称/主图在 shop_product 上，
 * 规格文本和单价在那条 shop_product_sku 上）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "购物车视图对象")
public class ShopCartVo extends ShopConsumerCart {

    @ApiModelProperty(value = "商品编码（联表带出）")
    private String productCode;

    @ApiModelProperty(value = "商品名称（联表带出）")
    private String productName;

    // 购物车一行挂的是 sku_id，前台「点这一行回商品详情页」要的是商品（SPU）id，两个不是一回事。
    // 口径同 ShopOrderItemVo.productId：由 sku_id 联到所属商品带出，规格被删过就是 NULL
    @ApiModelProperty(value = "所属商品ID（联 shop_product_sku.product_id 带出），前台点回详情页用")
    private Long productId;

    @ApiModelProperty(value = "商品主图（联表带出）")
    private String mainImage;

    @ApiModelProperty(value = "商品单价（取自这一行的 shop_product_sku.product_price）")
    private BigDecimal productPrice;

    // 购物车一行就是一个规格，列表上那一格得看出买的是哪个规格；规格文本在 SKU 上，购物车行里不冗余
    @ApiModelProperty(value = "规格文本（联 shop_product_sku 带出），NULL=这条是无规格的默认SKU")
    private String specText;

    /**
     * 这一条规格的可用库存（各仓 SUM(stock - locked_stock)），库存行一条都没有就是 0。
     * 前台购物车页数量那一格按它封顶、不够时印「仅剩 N 件」；库存只在下单那一刻才真的拦，
     * 加购和改数量都不过这道闸（口径同商品列表的 available_stock）。
     */
    @ApiModelProperty(value = "这一条规格的可用库存（联 shop_erp_inventory 现算），NULL=这条规格已经没了")
    private Integer availableStock;

    /**
     * 这一行现在还买不买得了：只看商品在不在架上（shop_product.status=1），在架才算 1。
     * 规格的 sale_enable 不参与 —— 那是 ERP 能不能拿它建销售单的开关，跟前台买不买得了两回事。
     * 前台车里的行下架了不把它从列表里抹掉（那样等于让用户以为车自己吞了货），
     * 画成「已下架」灰行、并且不计入选中和合计。
     */
    @ApiModelProperty(value = "这一行是否仍可购买：1可以 0不可以（商品已下架）")
    private Integer buyable;

    @ApiModelProperty(value = "商品分类名称（联表带出）")
    private String categoryName;

    @ApiModelProperty(value = "品牌名称（联表带出）")
    private String brandName;

    @ApiModelProperty(value = "品牌LOGO（联表带出）")
    private String brandLogo;

    @ApiModelProperty(value = "门店名称（联表带出）")
    private String storeName;

    @ApiModelProperty(value = "消费者昵称（联表带出）")
    private String nickname;

    @ApiModelProperty(value = "消费者用户名（联表带出）")
    private String username;
}
