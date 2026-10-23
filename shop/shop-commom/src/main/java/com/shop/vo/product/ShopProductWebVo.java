package com.shop.vo.product;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品 Web 端视图（/api/web/shop/product 那两条读接口专用）
 *
 * 故意不继承 ShopProduct、也不搬后台 ShopProductVo 的字段：这一条不带登录态也能读，
 * 后台那份里的成本价、安全/最大库存、预警开关、创建人和审计字段一起吐出去就是泄露
 * （进货价 + 这一行是谁建的），所以这里只留「给消费者看」的那一格才算数。
 * 要加字段先问一句：这一格能不能给外人看。
 */
@Data
@ApiModel(value = "商品 Web 端视图")
public class ShopProductWebVo {

    @ApiModelProperty(value = "商品id")
    private Long id;

    @ApiModelProperty(value = "商品编码(SPU编码)")
    private String productCode;

    @ApiModelProperty(value = "商品名称")
    private String productName;

    @ApiModelProperty(value = "副标题/卖点")
    private String subTitle;

    @ApiModelProperty(value = "商品描述（详情用；列表也带着，同一份 SQL 不为两种视图分列）")
    private String productDesc;

    @ApiModelProperty(value = "商品分类id")
    private Long categoryId;

    @ApiModelProperty(value = "所属类目名称（联表带出）")
    private String categoryName;

    @ApiModelProperty(value = "品牌id")
    private Long brandId;

    @ApiModelProperty(value = "品牌名称（联表带出）")
    private String brandName;

    @ApiModelProperty(value = "品牌LOGO（联表带出）")
    private String brandLogo;

    @ApiModelProperty(value = "所属门店id，NULL=不限定门店")
    private Long storeId;

    @ApiModelProperty(value = "所属门店名称（联表带出）")
    private String storeName;

    @ApiModelProperty(value = "主图URL")
    private String mainImage;

    @ApiModelProperty(value = "轮播图，JSON数组字符串：[{\"title\":\"\",\"url\":\"\"}]")
    private String images;

    @ApiModelProperty(value = "规格id数组(,分隔)，库里那一列原样")
    private String productSpecs;

    /**
     * 上面那串 id 现查出来的规格行，只有详情那条给（列表不为它多跑一条 SQL），没挂或全被禁用就是空列表。
     * 页面上展示的是这一份，不是 productSpecs 那串数字。
     */
    @ApiModelProperty(value = "商品规格（详情用）")
    private List<ShopProductSpecWebVo> specs;

    /**
     * 这件商品名下的在售 SKU，同样只有详情那条给。详情页的规格选择器按这一份列按钮，
     * 加购/下单传的是这里面的 id（不是商品 id）。价格、销量、库存、积分都按这一条规格算。
     */
    @ApiModelProperty(value = "商品规格明细（详情用）")
    private List<ShopProductSkuWebVo> skus;

    @ApiModelProperty(value = "最低价（名下所有SKU的单价取最小，查询时现算）")
    private BigDecimal priceMin;

    @ApiModelProperty(value = "最高价（名下所有SKU的单价取最大，查询时现算）")
    private BigDecimal priceMax;

    // 销量和可用库存不在商品这一层给：销量记在每一条规格上，库存更是「一个 SKU × 一个仓」那一格的账，
    // 摊成一件商品一个数会写着「有货」而缺货的正是人选中的那一档。前端读 skus 里那一条的 salesCount/availableStock。

    @ApiModelProperty(value = "状态：1上架（能被查到的只有 1，0 下架一律不出现）")
    private Integer status;

    @ApiModelProperty(value = "是否轮播：1参与首页轮播位 0不参与（列表默认不筛它，由页面决定哪块位置放哪些商品）")
    private Integer carouselEnable;

    @ApiModelProperty(value = "是否文创商品：1文创商品 0普通商品（门店详情页商品区那两档 tab 用它分）")
    private Integer culturalEnable;
}
