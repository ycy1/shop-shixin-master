package com.shop.dto.activity;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 活动埋点参数（前台报一次曝光或一次点击）
 *
 * 就这两格，其余都由服务端自己定：谁触发的取当前登录态（未登录就落 NULL，这一列本来就允许空），
 * IP / IP 归属地 / 操作系统从请求里现取，时间靠 create_time 的库默认值。
 * 理由是这一条路不鉴权（谁都能调），能由参数写的字段就等于谁都能填 ——
 * 传个别人的 user_id 进来，记录就记到他头上了。
 */
@Data
@ApiModel(value = "活动埋点参数")
public class ShopActivityTrackDto {

    @ApiModelProperty(value = "活动ID(shop_activity.id)，必填")
    private Long activityId;

    @ApiModelProperty(value = "事件类型：1曝光 2点击（字典 activity_event_type），必填")
    private Integer eventType;
}
