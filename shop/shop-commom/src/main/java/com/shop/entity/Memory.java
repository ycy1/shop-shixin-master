package com.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_memories")
@ApiModel(value = "记忆/消息对象")
public class Memory implements Serializable {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty(value = "记忆ID")
    private Long id;

    @ApiModelProperty(value = "所属会话ID")
    private Long conversationId;

    @ApiModelProperty(value = "所属智能体ID")
    private Long agentId;

    @ApiModelProperty(value = "用户ID")
    private Long userId;

    @ApiModelProperty(value = "角色: user, assistant, system, function")
    private String role;

    @ApiModelProperty(value = "消息内容")
    private String content;

    @ApiModelProperty(value = "消息类型: text, image, file, code等")
    private String messageType;

    @ApiModelProperty(value = "附件信息")
    private String attachments;

    @ApiModelProperty(value = "函数调用信息")
    private String functionCall;

    @ApiModelProperty(value = "函数返回结果")
    private String functionResponse;

    @ApiModelProperty(value = "父消息ID")
    private Long parentMessageId;

    @ApiModelProperty(value = "对话轮次编号")
    private Integer turnNumber;

    @ApiModelProperty(value = "该消息使用的token数")
    private Integer tokensUsed;

    @ApiModelProperty(value = "累计token数")
    private Integer tokensCumulative;

    @ApiModelProperty(value = "重要性评分")
    private BigDecimal importanceScore;

    @ApiModelProperty(value = "是否已被摘要压缩")
    private Integer isSummarized;

    @ApiModelProperty(value = "摘要压缩内容")
    private String summary;

    @ApiModelProperty(value = "提取的关键词列表")
    private String keywords;

    @ApiModelProperty(value = "实体识别结果")
    private String entities;

    @TableField(fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "修改时间")
    private LocalDateTime updateTime;

    @ApiModelProperty(value = "软删除时间")
    private LocalDateTime deletedAt;
}
