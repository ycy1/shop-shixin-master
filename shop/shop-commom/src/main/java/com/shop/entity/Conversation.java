package com.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("ai_conversations")
@ApiModel(value = "会话对象")
public class Conversation implements Serializable {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty(value = "会话ID")
    private Long id;

    @ApiModelProperty(value = "所属智能体ID")
    private Long agentId;

    @ApiModelProperty(value = "用户ID")
    private Long userId;

    @ApiModelProperty(value = "会话标题")
    private String title;

    @ApiModelProperty(value = "会话摘要")
    private String summary;

    @ApiModelProperty(value = "会话级配置: {temperature, max_tokens, system_prompt_override}")
    private String config;

    @ApiModelProperty(value = "上下文窗口大小（最近N轮）")
    private Integer contextWindow;

    @ApiModelProperty(value = "累计使用的token数")
    private Integer totalTokensUsed;

    @ApiModelProperty(value = "消息总数")
    private Integer messageCount;

    @ApiModelProperty(value = "状态: 0-已结束, 1-活跃, 2-归档")
    private Integer status;

    @ApiModelProperty(value = "是否置顶")
    private Integer isPinned;

    @ApiModelProperty(value = "最后活跃时间")
    private LocalDateTime lastActiveAt;

    @TableField(fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "修改时间")
    private LocalDateTime updateTime;

    @ApiModelProperty(value = "软删除时间")
    private LocalDateTime deletedAt;
}
