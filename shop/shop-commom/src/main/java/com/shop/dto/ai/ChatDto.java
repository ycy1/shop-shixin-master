package com.shop.dto.ai;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "聊天请求参数")
public class ChatDto {

    @ApiModelProperty(value = "会话ID（传则续聊，不传则新建会话）")
    private Long conversationId;

    @ApiModelProperty(value = "智能体ID（新建会话时必传）")
    private Long agentId;

    @ApiModelProperty(value = "用户消息内容")
    private String content;

    @ApiModelProperty(value = "消息类型: text, image, file")
    private String messageType;

    @ApiModelProperty(value = "启用的工具列表(JSON数组)，不传则使用智能体默认工具")
    private String tools;
}
