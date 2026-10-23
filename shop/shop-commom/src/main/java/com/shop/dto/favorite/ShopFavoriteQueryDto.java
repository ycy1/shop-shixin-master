package com.shop.dto.favorite;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 收藏分页查询参数
 *
 * 分页参数（pageNum/pageSize）由 PageUtil 直接从请求里取，不在此处声明。
 */
@Data
@ApiModel(value = "收藏查询参数")
public class ShopFavoriteQueryDto {

    @ApiModelProperty(value = "收藏对象名称/商品编码关键字")
    private String keyword;

    @ApiModelProperty(value = "消费者用户ID")
    private Long userId;

    @ApiModelProperty(value = "收藏类型：1商品 2门店 3品牌")
    private Integer targetType;

    @ApiModelProperty(value = "收藏对象ID（要和 targetType 一起传：同一个数字在三种类型下指向不同的东西）")
    private Long targetId;

    @ApiModelProperty(value = "收藏时间范围-开始")
    private String beginTime;

    @ApiModelProperty(value = "收藏时间范围-结束")
    private String endTime;
}
