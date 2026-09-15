package com.shop.vo.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel(value = "会话返回对象")
public class ConversationVo {

    @ApiModelProperty(value = "会话ID")
    private Long id;

    @ApiModelProperty(value = "所属智能体ID")
    private Long agentId;

    @ApiModelProperty(value = "智能体名称")
    private String agentName;

    @ApiModelProperty(value = "智能体头像")
    private String agentAvatar;

    @ApiModelProperty(value = "会话标题")
    private String title;

    @ApiModelProperty(value = "会话摘要")
    private String summary;

    @ApiModelProperty(value = "累计使用的token数")
    private Integer totalTokensUsed;

    @ApiModelProperty(value = "消息总数")
    private Integer messageCount;

    @ApiModelProperty(value = "状态: 0-已结束, 1-活跃, 2-归档")
    private Integer status;

    @ApiModelProperty(value = "是否置顶")
    private Integer isPinned;

    @ApiModelProperty(value = "最后活跃时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime lastActiveAt;

    @ApiModelProperty(value = "创建时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;
}
