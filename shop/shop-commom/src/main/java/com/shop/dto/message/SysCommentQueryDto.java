package com.shop.dto.message;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel(value = "评论查询条件")
public class SysCommentQueryDto {

    @ApiModelProperty(value = "评论对象：商品/门店/品牌/广场消息四张表的主键；后台从商品/门店页跳过来时带这一格，要和 commentType 一起点（id 会撞号）")
    private Long businessId;

    @ApiModelProperty(value = "评论类型：1商品 2门店 3品牌 4广场（字典 comment_type）")
    private Integer commentType;

    @ApiModelProperty(value = "用户昵称")
    private String nickname;

    @ApiModelProperty(value = "回复人昵称")
    private String replyNickname;

    @ApiModelProperty(value = "评论内容")
    private String content;

    @ApiModelProperty(value = "评论时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime createTime;
}
