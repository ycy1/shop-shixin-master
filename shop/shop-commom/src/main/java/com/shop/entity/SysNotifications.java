package com.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.io.Serializable;

@Data
@TableName("sys_notifications")
@ApiModel(value = "消息通知表对象")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SysNotifications implements Serializable {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty(value = "通知的唯一标识，自增主键")
    private Long id;

    @ApiModelProperty(value = "来自用户id")
    private Long fromUserId;

    @ApiModelProperty(value = "业务id")
    private Long businessId;

    @ApiModelProperty(value = "业务类型（NotificationTypeEnum 那八档，字典 notification_business_type）")
    private String businessType;

    @ApiModelProperty(value = "消息分类 1-系统消息 2-互动消息，由 businessType 派生（字典 notification_category）")
    private Integer category;

    @ApiModelProperty(value = "通知的标题")
    private String title;

    @ApiModelProperty(value = "通知的具体内容")
    private String message;

    @ApiModelProperty(value = "推送对象（JSON：{\"user\":[],\"dept\":[],\"role\":[]}）；前台可见性已不看这一格，只留作后台回显发送范围")
    private String noticePush;

    @ApiModelProperty(value = "1-全员可见，发送时不展开接收人；0-定向，接收人已展开进 sys_notifications_receiver")
    private Integer isBroadcast;

    @ApiModelProperty(value = "发送批次标识（32位uuid，重新发送时更新，用于标识子表记录的所属发送批次）")
    private String sendCode;

    @ApiModelProperty(value = "删除标记，0 未删除，1 已删除")
    private Integer delFlag;

    @ApiModelProperty(value = "通知关联的链接，可为空")
    private String link;

    @TableField(exist = false)
    @ApiModelProperty(value = "待筛选的业务类型列表（逗号分隔，不入库）")
    private String businessTypes;

    @TableField(exist = false)
    @ApiModelProperty(value = "已读状态（查询过滤/管理端修改用，非主表字段，实际存 sys_notifications_receiver.is_read）")
    private Integer isRead;

    @TableField(exist = false)
    @ApiModelProperty(value = "是否重新发送（true=重新发送时生成新的 send_code 并清空子表记录，不入库）")
    private Boolean send;

    @TableField(exist = false)
    @ApiModelProperty(value = "发送人昵称（查询过滤用，不入库）")
    private String fromNickname;

    @ApiModelProperty(value = "通知的创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
