package com.shop.dto.cart;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 购物车新增/修改参数
 *
 * 只收这四个：分类/品牌/门店由服务层按 skuId 从这条规格所属的商品上带出，
 * 商品名、单价、规格文本、消费者昵称这些回显字段不在这里声明，客户端传了也会被忽略。
 */
@Data
@ApiModel(value = "购物车新增/修改参数")
public class ShopCartSaveDto {

    @ApiModelProperty(value = "购物车记录id（修改时传）")
    private Long id;

    @ApiModelProperty(value = "消费者用户ID")
    private Long userId;

    @ApiModelProperty(value = "商品SKU ID，加购选的是具体规格")
    private Long skuId;

    @ApiModelProperty(value = "数量")
    private Integer quantity;

    @ApiModelProperty(value = "是否选中：1选中 0未选中")
    private Integer selected;
}
