package com.shop.dto.ai;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "会话查询参数")
public class ConversationQueryDto {

    @ApiModelProperty(value = "所属智能体ID")
    private Long agentId;

    @ApiModelProperty(value = "用户ID")
    private Long userId;

    @ApiModelProperty(value = "状态: 0-已结束, 1-活跃, 2-归档")
    private Integer status;
}
