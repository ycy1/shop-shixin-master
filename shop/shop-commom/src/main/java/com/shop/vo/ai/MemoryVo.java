package com.shop.vo.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@ApiModel(value = "记忆/消息返回对象")
public class MemoryVo {

    @ApiModelProperty(value = "消息ID")
    private Long id;

    @ApiModelProperty(value = "所属会话ID")
    private Long conversationId;

    @ApiModelProperty(value = "角色: user, assistant, system")
    private String role;

    @ApiModelProperty(value = "消息内容")
    private String content;

    @ApiModelProperty(value = "消息类型: text, image, file, code")
    private String messageType;

    @ApiModelProperty(value = "附件信息")
    private String attachments;

    @ApiModelProperty(value = "父消息ID")
    private Long parentMessageId;

    @ApiModelProperty(value = "对话轮次编号")
    private Integer turnNumber;

    @ApiModelProperty(value = "该消息使用的token数")
    private Integer tokensUsed;

    @ApiModelProperty(value = "重要性评分")
    private BigDecimal importanceScore;

    @ApiModelProperty(value = "是否已被摘要压缩")
    private Integer isSummarized;

    @ApiModelProperty(value = "创建时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;
}
