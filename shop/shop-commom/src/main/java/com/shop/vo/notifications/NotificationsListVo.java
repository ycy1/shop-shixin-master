package com.shop.vo.notifications;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author: quequnlong
 * @date: 2025/3/23
 * @description:
 */
@Data
public class NotificationsListVo {

    @ApiModelProperty(value = "通知的唯一标识，自增主键")
    private Long id;

    private String toNickname;

    private String fromNickname;

    @ApiModelProperty(value = "来自用户id")
    private Long fromUserId;

    @ApiModelProperty(value = "业务id")
    private Long businessId;

    @ApiModelProperty(value = "业务类型")
    private String businessType;

    @ApiModelProperty(value = "业务标题")
    private String businessTitle;

    @ApiModelProperty(value = "业务内容")
    private String businessMessage;

    @ApiModelProperty(value = "通知的标题")
    private String title;

    @ApiModelProperty(value = "通知的具体内容")
    private String message;

    @ApiModelProperty(value = "推送对象（JSON：{\"user\":[],\"dept\":[],\"role\":[]}，null 或全空=全员可见）")
    private String noticePush;

    @ApiModelProperty(value = "阅读数（当前发送批次的阅读记录条数）")
    private Long readCount;

    @ApiModelProperty(value = "标记通知是否已读，0 表示未读，1 表示已读（门户/App 端按当前用户计算）")
    private Integer isRead;

    @ApiModelProperty(value = "通知关联的链接，可为空")
    private String link;

    @ApiModelProperty(value = "删除标记，0 未删除，1 已删除")
    private Integer delFlag;

    @ApiModelProperty(value = "通知的创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
