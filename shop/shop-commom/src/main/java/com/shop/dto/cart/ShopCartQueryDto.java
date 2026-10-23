package com.shop.dto.cart;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 购物车分页查询参数
 *
 * 分页参数（pageNum/pageSize）由 PageUtil 直接从请求里取，不在此处声明。
 */
@Data
@ApiModel(value = "购物车查询参数")
public class ShopCartQueryDto {

    @ApiModelProperty(value = "商品名称/编码/规格文本关键字")
    private String keyword;

    @ApiModelProperty(value = "消费者用户ID（消费者账号登录时后端会覆盖为本人 id）")
    private Long userId;

    @ApiModelProperty(value = "商品分类id")
    private Long categoryId;

    @ApiModelProperty(value = "门店id")
    private Long storeId;

    @ApiModelProperty(value = "是否选中：1选中 0未选中")
    private Integer selected;

    @ApiModelProperty(value = "加入购物车时间范围-开始")
    private String beginTime;

    @ApiModelProperty(value = "加入购物车时间范围-结束")
    private String endTime;
}
