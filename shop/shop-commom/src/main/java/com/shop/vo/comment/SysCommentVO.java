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

    @ApiModelProperty(value = "评论对象：商品/门店/品牌/广场消息四张表的主键，由 commentType 决定指向哪一张")
    private Long businessId;

    @ApiModelProperty(value = "评论对象名称：列表按 commentType 现联四张表带出来的，表里没有这一格（广场那一档取标题，可空）")
    private String businessName;

    @ApiModelProperty(value = "用户头像")
    private String avatar;

    @ApiModelProperty(value = "用户昵称")
    private String nickname;

    @ApiModelProperty(value = "回复人昵称")
    private String replyNickname;

    @ApiModelProperty(value = "评论类型：1商品 2门店 3品牌 4广场（字典 comment_type）")
    private Integer commentType;

    @ApiModelProperty(value = "评论内容（图片以 [图片](url) 内嵌在这一串里，后台那一列拆开画缩略图）")
    private String content;

    @ApiModelProperty(value = "父评论ID，顶层评论为 null（回复只铺一层）")
    private Integer parentId;

    @ApiModelProperty(value = "回复用户 ID")
    private Long replyUserId;

    @ApiModelProperty(value = "是否置顶：1置顶，列表排序打头，只有后台能改")
    private Integer isStick;

    @ApiModelProperty(value = "点赞数（存下来的数，跟着点赞行的插删一起动）")
    private Integer likeCount;

    @ApiModelProperty(value = "子评论列表")
    private List<SysCommentVO> children;

    @ApiModelProperty(value = "评论时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD, timezone = "GMT+8")
    private LocalDateTime createTime;
}
