package com.shop.vo.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@ApiModel(value = "智能体返回对象")
public class AgentVo {

    @ApiModelProperty(value = "智能体ID")
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

    @ApiModelProperty(value = "配置类型: default/database/document")
    private String configType;

    @ApiModelProperty(value = "知识源配置ID")
    private Long configId;

    @ApiModelProperty(value = "知识源名称")
    private String configSourceName;

    @ApiModelProperty(value = "模型类型")
    private String modelType;

    @ApiModelProperty(value = "温度参数")
    private BigDecimal temperature;

    @ApiModelProperty(value = "最大输出token数")
    private Integer maxTokens;

    @ApiModelProperty(value = "检索返回的文档数量")
    private Integer retrievalTopK;

    @ApiModelProperty(value = "关联的工具列表(JSON数组)")
    private String tools;

    @ApiModelProperty(value = "状态: 0-禁用, 1-启用")
    private Integer status;

    @ApiModelProperty(value = "是否公开: 0-私有, 1-公开")
    private Integer isPublic;

    @ApiModelProperty(value = "总会话数")
    private Integer totalConversations;

    @ApiModelProperty(value = "总消息数")
    private Integer totalMessages;

    @ApiModelProperty(value = "创建者用户ID")
    private Long createdBy;

    @ApiModelProperty(value = "创建时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;

    @ApiModelProperty(value = "修改时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime updateTime;
}
