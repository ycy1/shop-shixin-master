package com.shop.dto.browse;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 浏览足迹新增/修改参数
 *
 * 只收这四个：商品名称、单价、消费者昵称都是查询时联表带出的回显字段，不在这里声明，客户端传了也会被忽略。
 */
@Data
@ApiModel(value = "浏览足迹新增/修改参数")
public class ShopBrowseSaveDto {

    @ApiModelProperty(value = "足迹记录id（修改时传）")
    private Long id;

    @ApiModelProperty(value = "消费者用户ID")
    private Long userId;

    @ApiModelProperty(value = "商品ID")
    private Long productId;

    @ApiModelProperty(value = "浏览时间，不传按当前时间记（新增时同一消费者同一商品已有足迹的话，只刷新这一列）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime browseTime;
}
