package com.shop.dto.product;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品SKU新增/修改参数
 *
 * 字段与表单平铺对应：同名属性由服务层拷进 ShopProductSku 落 shop_product_sku。
 * 销量、规格文本快照（specText）和留痕四件套不在这里声明 —— specText 由服务层按 specIds
 * 从规格字典现拼，销量只有支付回调写，客户端传了也不算数。
 * productId 新增时必填（这一条挂在哪个商品下），修改时改不动（服务的 update 按库里的行判归属）。
 */
@Data
@ApiModel(value = "商品SKU新增/修改参数")
public class ShopProductSkuSaveDto {

    @ApiModelProperty(value = "SKU id（修改时传）")
    private Long id;

    @ApiModelProperty(value = "所属商品id，新增时必填")
    private Long productId;

    @ApiModelProperty(value = "SKU编码，唯一；新增时不传则由系统生成（SKU_+时间戳）")
    private String skuCode;

    @ApiModelProperty(value = "规格值id数组(,分隔，命中 shop_product_spec.id)；不给就是默认规格")
    private String specIds;

    @ApiModelProperty(value = "规格轮播图，JSON数组字符串 [{\"title\":\"\",\"url\":\"\"}]；不传/传空数组=这一档不配规格图，沿用商品主图")
    private String skuImages;

    @ApiModelProperty(value = "商品详情图，JSON数组字符串 [{\"title\":\"\",\"url\":\"\"}]；不传/传空数组=这一档没有详情图文（前台那一块显示「暂无」）")
    private String detailImages;

    @ApiModelProperty(value = "单价")
    private BigDecimal productPrice;

    @ApiModelProperty(value = "单件重量(kg)；不传就是把这一格清掉（下单算配送重量时用）")
    private BigDecimal weight;

    @ApiModelProperty(value = "安全库存，低于此值预警补货")
    private Integer safetyStock;

    @ApiModelProperty(value = "最大库存，高于此值预警积压；传 null 表示不设上限（会把库里该列清成 NULL）")
    private Integer maxStock;

    @ApiModelProperty(value = "是否开启库存预警：1是 0否")
    private Integer stockWarnEnable;

    @ApiModelProperty(value = "买这个规格可获得的积分（每件，下单后发放），0=不发积分")
    private Integer gainPoints;

    @ApiModelProperty(value = "兑换这个规格所需的积分（每件），0=不能用积分兑换")
    private Integer exchangePoints;

    @ApiModelProperty(value = "是否可销售：1可销售 0不可销售（与商品的上架/下架无关）")
    private Integer saleEnable;

    @ApiModelProperty(value = "该商品内的规格排序")
    private Integer sort;
}
