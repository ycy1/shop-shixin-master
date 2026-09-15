package com.shop.vo.notifications;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息阅读记录（sys_notifications_receiver 关联表 + 阅读人信息）
 *
 * @author: quequnlong
 * @date: 2026/8/24
 * @description:
 */
@Data
public class NotificationsReceiverVo {

    @ApiModelProperty(value = "关联表记录id")
    private Long id;

    @ApiModelProperty(value = "消息id")
    private Long notificationId;

    @ApiModelProperty(value = "阅读人用户id")
    private Long userId;

    @ApiModelProperty(value = "阅读人昵称")
    private String nickname;

    @ApiModelProperty(value = "阅读人用户名")
    private String username;

    @ApiModelProperty(value = "是否已读，0 未读，1 已读")
    private Integer isRead;

    @ApiModelProperty(value = "阅读时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readTime;

    @ApiModelProperty(value = "是否删除（该接收人隐藏消息），0 否，1 是")
    private Integer isDeleted;
}
