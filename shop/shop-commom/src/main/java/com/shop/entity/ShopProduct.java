package com.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品主表(SPU)，只映射 shop_product 自己的列。
 *
 * 规格在 shop_product_sku，与本表是 1:N（一条商品下可以有好几个规格）。上下架状态和首页轮位
 * 是「这件商品」的属性、在本表，单价与库存参数是「这个规格」的、在 SKU 表。
 * 具体读写由 ShopProductServiceImpl（SPU）和 ShopProductSkuServiceImpl（SKU）各自负责，
 * 删商品时服务层在一个事务里把名下 SKU 一起软删。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_product")
@ApiModel(value = "商品(SPU)")
public class ShopProduct extends BaseEntity {

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

    // 可空列且"取消品牌"是正常操作：不加 IGNORED，全局 field-strategy=not_empty 会把这个 null 跳过
    @ApiModelProperty(value = "品牌id")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long brandId;

    // 同上，NULL 表示不限定门店
    @ApiModelProperty(value = "所属门店id，NULL=不限定门店")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long storeId;

    @ApiModelProperty(value = "主图URL")
    private String mainImage;

    // 这件商品「用哪几组规格值开SKU」的标签：前台详情页的规格选择器按这一串列候选，
    // 每个被选中的组合落到 shop_product_sku 是一条 SKU
    @ApiModelProperty(value = "规格id数组(,分隔，命中 shop_product_spec.id)")
    private String productSpecs;

    @ApiModelProperty(value = "轮播图，JSON数组字符串：[{\"title\":\"\",\"url\":\"\"}]")
    private String images;

    @ApiModelProperty(value = "排序")
    private Integer sort;

    // 这两格原来是 SKU（旧 detail 表）上的列：上下架是整件商品的事、首页轮位也是逐件商品选的，
    // 所以搬到 SPU。前台能不能看见这件商品只看 status，跟某个规格能不能下单（SKU.sale_enable）无关
    @ApiModelProperty(value = "状态：1上架 0下架")
    private Integer status;

    @ApiModelProperty(value = "是否轮播：1参与首页轮播位 0不参与")
    private Integer carouselEnable;

    // 文创商品是这家店自己的周边，所以必须挂在一个 store_id 上（判据在商品服务的 validate 里）；
    // 这一列 NOT NULL 且表单永远送 0/1，不是「能清空」的那类可空列，所以不挂 FieldStrategy.IGNORED
    @ApiModelProperty(value = "是否文创商品：1文创商品（必填所属门店） 0普通商品")
    private Integer culturalEnable;

    // 创建人只在新增时写：NEVER 让这一列永不进 UPDATE 的 SET，表单里带什么都改不动
    @ApiModelProperty(value = "创建人用户ID")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long userId;
}
