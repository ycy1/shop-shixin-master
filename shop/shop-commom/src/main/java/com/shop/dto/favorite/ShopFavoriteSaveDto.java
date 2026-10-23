package com.shop.dto.favorite;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏新增/修改参数
 *
 * 只收这五个：对象名称、消费者昵称都是查询时联表带出的回显字段，不在这里声明，客户端传了也会被忽略。
 */
@Data
@ApiModel(value = "收藏新增/修改参数")
public class ShopFavoriteSaveDto {

    @ApiModelProperty(value = "收藏记录id（修改时传）")
    private Long id;

    @ApiModelProperty(value = "消费者用户ID")
    private Long userId;

    @ApiModelProperty(value = "收藏类型：1商品 2门店 3品牌")
    private Integer targetType;

    @ApiModelProperty(value = "收藏对象ID（按 targetType 指向对应表）")
    private Long targetId;

    @ApiModelProperty(value = "收藏时间，不传按当前时间记（新增时同一消费者同一对象已有收藏的话，只刷新这一列）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime favoriteTime;
}
