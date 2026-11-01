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
 * 盲盒商品（一款 = 一个可能抽中的结果）
 *
 * 概率按小数存（0.0139 = 1.39%），同一个盲盒下启用中的款式概率合计不能超过 1，服务层拦。
 * remain_count 是 total_count - drawn_count 的派生值，服务层每次存都刷一遍，表单里那一格只读。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_trendy_box_item")
@ApiModel(value = "盲盒商品")
public class ShopTrendyBoxItem extends BaseEntity {

    @ApiModelProperty(value = "盲盒ID(shop_trendy_box.id)")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long boxId;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "SKU ID(shop_product_sku.id)，NULL=这一款还没挂具体规格")
    private Long skuId;

    @ApiModelProperty(value = "款式名称，如「隐藏款-星空」")
    private String styleName;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "款式图片 URL")
    private String styleImage;

    @ApiModelProperty(value = "是否隐藏款：1是 0否")
    private Integer isHidden;

    @ApiModelProperty(value = "概率值（小数，0.0139=1.39%）")
    private BigDecimal probability;

    @ApiModelProperty(value = "总数量，0=不限")
    private Integer totalCount;

    @ApiModelProperty(value = "已抽出数量（抽奖那侧维护，表单不给改）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer drawnCount;

    @ApiModelProperty(value = "剩余数量（= total_count - drawn_count，服务层每次存都刷）")
    private Integer remainCount;

    @ApiModelProperty(value = "排序")
    private Integer sort;

    @ApiModelProperty(value = "状态：1启用 0禁用")
    private Integer status;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "备注")
    private String remark;

    // 下面两格不是本表列，服务层按 sku_id 查回来给列表显示（形状同 ShopStore.brandName）
    @TableField(exist = false)
    @ApiModelProperty(value = "挂的规格文本（服务层回填，非本表列）")
    private String skuText;

    @TableField(exist = false)
    @ApiModelProperty(value = "挂的商品名称（服务层回填，非本表列）")
    private String productName;
}
