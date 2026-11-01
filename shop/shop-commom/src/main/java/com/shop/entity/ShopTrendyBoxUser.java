package com.shop.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 盲盒抽取记录表
 *
 * 一行 = 抽中的一次。款式那三格（名称/图/是否隐藏）是**快照**：款式后来改名、换图、被删掉，
 * 历史记录还得说清「当时抽到的是什么」，所以这张表读的时候不联款式表。
 *
 * 流水表，只插不改 —— update_time 那一格只是 BaseEntity 的形状要求。
 * create_by 由 MyMetaObjectHandler 盖成当前登录用户，和 user_id 同值（user_id 是业务归属，
 * 不能靠留痕列，因为后台补录/代抽那一档将来可能两者不同）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_trendy_box_user")
@ApiModel(value = "盲盒抽取记录")
public class ShopTrendyBoxUser extends BaseEntity {

    @ApiModelProperty(value = "盲盒ID(shop_trendy_box.id)")
    private Long boxId;

    @ApiModelProperty(value = "抽中的盲盒商品ID(shop_trendy_box_item.id)")
    private Long itemId;

    @ApiModelProperty(value = "SKU ID，NULL=这款没挂具体规格（空奖/只展示）")
    private Long skuId;

    @ApiModelProperty(value = "抽取用户ID(sys_user.id)")
    private Long userId;

    @ApiModelProperty(value = "款式名称（快照）")
    private String styleName;

    @ApiModelProperty(value = "款式图片（快照）")
    private String styleImage;

    @ApiModelProperty(value = "是否隐藏款：1是 0否")
    private Integer isHidden;

    // 下面两格不是本表列，后台「抽取记录」那一屏按当页 id 一次查回来；抽取时间直接读 create_time
    @TableField(exist = false)
    @ApiModelProperty(value = "抽取用户名称（服务层回填，非本表列）")
    private String userName;

    @TableField(exist = false)
    @ApiModelProperty(value = "盲盒名称（服务层回填，非本表列，跨盒查记录时用来认行）")
    private String boxName;
}
