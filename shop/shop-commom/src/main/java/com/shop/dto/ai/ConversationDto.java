package com.shop.dto.ai;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "会话新增/修改参数")
public class ConversationDto {

    @ApiModelProperty(value = "会话ID（修改时传）")
    private Long id;

    @ApiModelProperty(value = "所属智能体ID")
    private Long agentId;

    @ApiModelProperty(value = "会话标题")
    private String title;

    @ApiModelProperty(value = "会话配置(JSON)")
    private String config;

    @ApiModelProperty(value = "上下文窗口大小")
    private Integer contextWindow;

    @ApiModelProperty(value = "是否置顶")
    private Integer isPinned;
}
