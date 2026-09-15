package com.shop.vo.comment;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@ApiModel(value = "评论信息")
public class SysCommentVO {

    @ApiModelProperty(value = "主键ID")
    private Integer id;

    @ApiModelProperty(value = "用户头像")
    private String avatar;

    @ApiModelProperty(value = "用户昵称")
    private String nickname;

    @ApiModelProperty(value = "回复人昵称")
    private String replyNickname;

    @ApiModelProperty(value = "评论类型")
    private Integer commentType;

    @ApiModelProperty(value = "评论内容")
    private String content;

    @ApiModelProperty(value = "父评论ID")
    private Integer parentId;

    @ApiModelProperty(value = "回复用户 ID")
    private Long replyUserId;

    @ApiModelProperty(value = "是否置顶")
    private Integer isStick;

    @ApiModelProperty(value = "点赞数")
    private Integer likeCount;

    @ApiModelProperty(value = "子评论列表")
    private List<SysCommentVO> children;

    @ApiModelProperty(value = "评论时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD, timezone = "GMT+8")
    private LocalDateTime createTime;
}
