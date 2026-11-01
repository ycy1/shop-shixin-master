package com.shop.dto.product;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 商品SKU查询参数
 *
 * 分页参数（pageNum/pageSize）由 PageUtil 直接从请求里取，不在此处声明。
 * 商品页那颗「SKU管理」不走这一条（它按 productId 一次列出全部规格，不分页），
 * 这条是给「按规格挑一行」的那些入口用的：商品库选择器、单据明细候选。
 */
@Data
@ApiModel(value = "商品SKU查询参数")
public class ShopProductSkuQueryDto {

    @ApiModelProperty(value = "关键字：商品名称 / 商品编码 / SKU编码")
    private String keyword;

    @ApiModelProperty(value = "所属商品id")
    private Long productId;

    @ApiModelProperty(value = "商品分类id")
    private Long categoryId;

    @ApiModelProperty(value = "所属门店id")
    private Long storeId;

    @ApiModelProperty(value = "商品所属潮玩系列id：盲盒挂了系列时，挑规格只能在本系列的商品里挑")
    private Long sectionId;

    @ApiModelProperty(value = "只看可销售的规格：传 1 就滤掉 sale_enable=0 的")
    private Integer saleEnable;

    @ApiModelProperty(value = "只看已上架商品下的规格：传 1 就按 shop_product.status 筛")
    private Integer productStatus;

    // 数据范围：这个字段会随查询串绑进来，所以服务层一律覆盖（账号类型=门店填自己的ID、其它置空），
    // 不能靠它为空来判断身份
    @ApiModelProperty(hidden = true)
    private Long currentUserId;
}
