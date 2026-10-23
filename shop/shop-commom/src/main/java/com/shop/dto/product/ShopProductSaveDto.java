package com.shop.dto.product;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 商品新增/修改参数（SPU 粒度）
 *
 * 字段与前端表单平铺对应：同名属性由服务层拷进 ShopProduct 落 shop_product。
 * 重构后这一份只有「这件商品」的格 —— 单价、规格、重量、库存预警、积分两格、能不能单卖
 * 都跟着具体规格走，在 ShopProductSkuSaveDto 里，商品表单不收（列表操作列那颗「SKU管理」弹窗里录）。
 * 类目名、汇总出来的价格区间/销量/库存这些回显字段不在这里声明，客户端传了也会被忽略。
 */
@Data
@ApiModel(value = "商品新增/修改参数")
public class ShopProductSaveDto {

    @ApiModelProperty(value = "商品id（修改时传）")
    private Long id;

    @ApiModelProperty(value = "商品编码(SPU编码)，唯一；新增时不传则由系统生成（PRODUCT_+时间戳）")
    private String productCode;

    @ApiModelProperty(value = "商品名称")
    private String productName;

    @ApiModelProperty(value = "副标题/卖点")
    private String subTitle;

    @ApiModelProperty(value = "商品描述")
    private String productDesc;

    @ApiModelProperty(value = "商品分类id")
    private Long categoryId;

    @ApiModelProperty(value = "品牌id，不关联品牌时传 null")
    private Long brandId;

    @ApiModelProperty(value = "所属门店id，不限定门店时传 null")
    private Long storeId;

    @ApiModelProperty(value = "主图URL")
    private String mainImage;

    @ApiModelProperty(value = "规格id数组(,分隔，这件商品用哪几组规格值开SKU)")
    private String productSpecs;

    @ApiModelProperty(value = "轮播图，JSON数组字符串")
    private String images;

    @ApiModelProperty(value = "排序")
    private Integer sort;

    @ApiModelProperty(value = "状态：1上架 0下架")
    private Integer status;

    @ApiModelProperty(value = "是否轮播：1参与首页轮播位 0不参与；新增不传就是 0（不给一建出来就占首页位置）")
    private Integer carouselEnable;

    @ApiModelProperty(value = "是否文创商品：1文创商品（这时所属门店必填） 0普通商品；新增不传就是 0")
    private Integer culturalEnable;
}
