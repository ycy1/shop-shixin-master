package com.shop.dto.ai;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
@ApiModel(value = "智能体新增/修改参数")
public class AgentDto {

    @ApiModelProperty(value = "智能体ID（修改时传）")
    private Long id;

    @ApiModelProperty(value = "智能体名称")
    private String name;

    @ApiModelProperty(value = "智能体描述")
    private String description;

    @ApiModelProperty(value = "头像URL")
    private String avatarUrl;

    @ApiModelProperty(value = "系统提示词")
    private String systemPrompt;

    @ApiModelProperty(value = "欢迎语/开场白")
    private String greetingMessage;

    @ApiModelProperty(value = "配置类型: default-默认无, database-数据库, document-文档")
    private String configType;

    @ApiModelProperty(value = "知识源配置ID")
    private Long configId;

    @ApiModelProperty(value = "模型类型: gpt-4, claude-3, qwen等")
    private String modelType;

    @ApiModelProperty(value = "温度参数")
    private BigDecimal temperature;

    @ApiModelProperty(value = "最大输出token数")
    private Integer maxTokens;

    @ApiModelProperty(value = "关联的知识库ID列表(JSON数组)")
    private String knowledgeBaseIds;

    @ApiModelProperty(value = "检索返回的文档数量")
    private Integer retrievalTopK;

    @ApiModelProperty(value = "关联的工具列表(JSON数组)")
    private String tools;

    @ApiModelProperty(value = "状态: 0-禁用, 1-启用")
    private Integer status;

    @ApiModelProperty(value = "是否公开: 0-私有, 1-公开")
    private Integer isPublic;
}
