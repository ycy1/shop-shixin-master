package com.shop.vo.notifications;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 当前用户的未读汇总：前台消息中心两档 tab 上的角标 + 入口那颗红点，一次请求全给到。
 *
 * 为什么合成一个对象而不是三格散着返回：库在公网，一趟往返的开销比这三条 count 本身大；
 * 红点只看 total，两档 tab 各看自己那一格，都由同一条 SQL 的 SUM(CASE) 算出来。
 */
@Data
public class NotificationUnreadVo {

    @ApiModelProperty(value = "未读总数（入口红点）")
    private Long total;

    @ApiModelProperty(value = "系统消息未读数（category=1）")
    private Long systemCount;

    @ApiModelProperty(value = "互动消息未读数（category=2）")
    private Long interactCount;
}
