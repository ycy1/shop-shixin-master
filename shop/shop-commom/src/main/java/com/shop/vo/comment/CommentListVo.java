package com.shop.vo.comment;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 前台评论区的一行（商品/门店/品牌/广场四处详情页、Web 与 App 两端共用这一份）
 *
 * 给出去的是页面上真要看的那几格：谁发的（昵称+头像）、说了什么（content 那一格，图片就内嵌在这一串里）、
 * 什么时候、几个人点过、我点没点。
 * sys_comment 上那四格留痕（ip / 浏览器 / 系统 + user_id）里只有归属地跟着出 ——
 * 访客 IP 印到评论区上不成道理，判据在 SysCommentMapper.xml 那段钉出来的列清单上。
 */
@Data
@ApiModel(value = "评论列表视图对象")
public class CommentListVo {

    @ApiModelProperty(value = "评论主键ID，自增唯一标识")
    private Integer id;

    @ApiModelProperty(value = "评论对象：商品/门店/品牌/广场消息四张表的主键，由 commentType 决定指向哪一张")
    private Long businessId;

    @ApiModelProperty(value = "评论类型：1商品 2门店 3品牌 4广场（字典 comment_type）")
    private Integer commentType;

    @ApiModelProperty(value = "父评论ID，顶层评论为 null（回复挂在被回复那一行下面，层数不限）")
    private Integer parentId;

    @ApiModelProperty(value = "发表评论的用户ID")
    private Long userId;

    @ApiModelProperty(value = "发表评论的用户昵称")
    private String nickname;

    @ApiModelProperty(value = "头像")
    private String avatar;

    @ApiModelProperty(value = "回复人昵称")
    private String replyNickname;

    @ApiModelProperty(value = "评论内容：纯文本 + 两样内嵌（emoji 是正文字符、图片写成 [图片](url)），三端各自拆串画、不走 v-html")
    private String content;

    @ApiModelProperty(value = "是否置顶：1置顶，列表排序把它打头，只有后台能改这一格")
    private Integer isStick;

    @ApiModelProperty(value = "点赞数：这一格是存下来的数（sys_comment_like 每点赞/取消一次由服务端加减），不是查询时现算的")
    private Integer likeCount;

    @ApiModelProperty(value = "来源IP的归属地（只到省市，前台页面上那句「IP属地」读它；访客 IP 本身不给前台）")
    private String ipSource;

    @ApiModelProperty(value = "评论时间（列表按它排，置顶那几条打头）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;

    @ApiModelProperty(value = "这一行名下的回复（递归 CTE 一趟捞回整棵子树，再按 parentId 一层层挂上来；顶层分页那一趟才有，最末端的行是空列表）")
    private List<CommentListVo> children;

    @ApiModelProperty(value = "当前登录用户是否已点过赞；未登录时恒为 false，前端画空心按钮并挂上登录门槛")
    private Boolean isLike;
}
