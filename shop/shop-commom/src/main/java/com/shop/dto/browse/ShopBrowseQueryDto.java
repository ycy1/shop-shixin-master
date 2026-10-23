package com.shop.dto.browse;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 浏览足迹分页查询参数
 *
 * 分页参数（pageNum/pageSize）由 PageUtil 直接从请求里取，不在此处声明。
 */
@Data
@ApiModel(value = "浏览足迹查询参数")
public class ShopBrowseQueryDto {

    @ApiModelProperty(value = "商品名称/编码关键字")
    private String keyword;

    @ApiModelProperty(value = "消费者用户ID")
    private Long userId;

    @ApiModelProperty(value = "浏览时间范围-开始（比的是 browse_time，不是 create_time）")
    private String beginTime;

    @ApiModelProperty(value = "浏览时间范围-结束")
    private String endTime;
}
