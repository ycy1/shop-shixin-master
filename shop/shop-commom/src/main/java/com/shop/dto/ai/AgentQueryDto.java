package com.shop.dto.ai;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "智能体查询参数")
public class AgentQueryDto {

    @ApiModelProperty(value = "智能体名称（模糊搜索）")
    private String name;

    @ApiModelProperty(value = "模型类型")
    private String modelType;

    @ApiModelProperty(value = "状态: 0-禁用, 1-启用")
    private Integer status;

    @ApiModelProperty(value = "是否公开")
    private Integer isPublic;
}
