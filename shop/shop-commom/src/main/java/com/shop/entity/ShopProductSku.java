package com.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品SKU表（与 shop_product 1:N，本表由原来的 shop_product_detail 改过来）。
 *
 * 分档口径：**整件商品的事**留在 shop_product（名称、分类、品牌、门店、主图、上下架 status、
 * 首页轮播 carousel_enable），**这个规格的事**在本表（单价、重量、库存预警、积分两格、能不能单卖）。
 * 商品新增/修改表单只填 SPU，规格由商品列表操作列那颗「SKU管理」弹窗逐条录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_product_sku")
@ApiModel(value = "商品SKU")
public class ShopProductSku extends BaseEntity {

    @ApiModelProperty(value = "所属商品ID(shop_product.id)")
    private Long productId;

    @ApiModelProperty(value = "SKU编码，唯一；新增时不传则由系统生成（SKU_+时间戳）")
    private String skuCode;

    // 存的是 shop_product_spec.id 串（规格值，不是规格名），写入前由服务层按 id 升序归一化，
    // 这样 {红,L} 和 {L,红} 是同一个规格组合。空串 = 这条是无规格的默认SKU
    @ApiModelProperty(value = "规格值id数组(,分隔，命中 shop_product_spec.id)，空串=默认规格")
    private String specIds;

    // 表单不填这一格：服务层按 specIds 从规格字典现拼，拼出来是什么就是什么
    @ApiModelProperty(value = "规格文本快照（服务层按 specIds 拼，下单/单据明细抄这一串）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String specText;

    // 表单上这一格现在收的是整组图（sku_images），这一列由服务层在每次保存时抄组首那张：
    // 购物车、销售单、下单快照读的都是它（COALESCE(sku_image, main_image)），所以多图不用回头改那些地方
    @ApiModelProperty(value = "该规格的主图URL(=sku_images 组首那张，服务层刷)；NULL=沿用商品的 main_image")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String skuImage;

    // 和 shop_product.images 同一档做法（MySQL json 列，实体侧按项目约定映射成 String）
    @ApiModelProperty(value = "规格轮播图，JSON数组字符串：[{\"title\":\"\",\"url\":\"\"}]；组首那张同步进 sku_image")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String skuImages;

    // 同样是 json 列 + 可空，所以也钉 IGNORED（清空整组要能落库）。和上面那组的区别是它不派生任何列：
    // 轮播图要有组首当封面（sku_image），详情图只是详情页「商品详情」那一块从上往下铺的一列长图
    @ApiModelProperty(value = "商品详情图，JSON数组字符串：[{\"title\":\"\",\"url\":\"\"}]；前台详情页图文那一块用")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String detailImages;

    @ApiModelProperty(value = "单价")
    private BigDecimal productPrice;

    // 冗余列（列表/详情直接读它，不去 SUM 订单）：唯一写入方是支付回调，按这一单每个规格买的件数
    // 条件增量累加，见 shop-api 的 ShopOrderPaidServiceImpl#createOutbound + ShopProductSkuMapper#increaseSalesCount。
    // SKU 表单改不动它（没在这一格），退款这一版也不冲减；商品页那一格按 SPU 把名下 SKU 全加起来
    @ApiModelProperty(value = "销量(冗余，支付回调按订单明细件数累加)")
    private Integer salesCount;

    // 可空：这个规格没称过就是不知道多重，留 NULL 让下单汇总时跳过它（凑 0 会算出"这单不要重量费"的假结论）
    @ApiModelProperty(value = "单件重量(kg)，下单时按 Σ(重量×数量) 算配送单的重量；NULL=没称重")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal weight;

    @ApiModelProperty(value = "安全库存，低于此值预警补货")
    private Integer safetyStock;

    // 可空列必须显式声明 IGNORED：全局 field-strategy=not_empty 会把 null 从 UPDATE 的 SET 里整个跳过，
    // 否则前端清空后无法保存
    @ApiModelProperty(value = "最大库存，高于此值预警积压；null 表示不设上限")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer maxStock;

    @ApiModelProperty(value = "是否开启库存预警：1是 0否")
    private Integer stockWarnEnable;

    // 两格都是"每件"口径，只是现在这个"件"是一个具体规格
    @ApiModelProperty(value = "买这个规格可获得的积分（下单后发放），0=不发积分")
    private Integer gainPoints;

    @ApiModelProperty(value = "兑换这个规格所需的积分，0=不能用积分兑换")
    private Integer exchangePoints;

    // 和 SPU 上的 status 是两个维度：status 管这件商品前台显不显示，这一列管这个规格能不能下单
    @ApiModelProperty(value = "是否可销售：1可销售 0不可销售（与上架/下架无关）")
    private Integer saleEnable;

    @ApiModelProperty(value = "该商品内的规格排序")
    private Integer sort;
}
