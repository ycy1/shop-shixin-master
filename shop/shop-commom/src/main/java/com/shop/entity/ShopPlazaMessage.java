package com.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.entity.core.BaseEntity;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 广场消息（前台「广场」那一条流，后台 站点管理 → 广场消息）
 *
 * 这张表不做编号（DDL 里那条 uk_message_no 引用的列根本不存在，已按你定的删掉），前后台都按 id 认。
 * message_type 那一列整列删了：正文一律是富文本组件产出的 HTML，图片内嵌在标签里，
 * 没有「纯文本/图文/视频/链接/话题」这五档要分。
 *
 * user_id 是发布者，不是留痕（留痕那两格在 BaseEntity 的 create_by/update_by）：发布那一步由服务层钉成当前登录账号，
 * 实体钉 NEVER ⇒ 后台修改永远改不动这一格。
 *
 * 三个计数（like_count / comment_count / share_count）读的时候一律按明细表现算覆盖，
 * 库里这三列谁都不回写（口径同门店培训的 sign_count）：点赞插一行删一行、评论是软删，
 * 动作散在三个 service 里，靠回写迟早和明细对不上；现算就多三个子查询，永远不会错。
 * 三个都钉 NEVER ⇒ 后台一次「修改」不会把列表上读回来的旧数写回库里。
 *
 * 广场消息图 plaza_image 是发布人自己上传的图（可空、多张用英文逗号拼在这一格）：
 * 卡片配图取第一张，详情页在正文下方整组铺开；
 * 服务端不从正文抓图来补这一列，那条老路下线了也不复活。
 * link_url / link_title / link_cover 三列本来没人填，2026-10-21 删了。
 *
 * publish_time 只有审核通过那一刻由服务层补一次，之后不覆盖；表单里没有这一格，
 * 靠默认的 not_empty 策略——不传就不进 SET（同门店培训的 publish_time/finish_time）。
 *
 * content_text 是服务端从 content 剥标签算出来的摘要（搜索和列表预览读它），表单不收，
 * 所以和正文一起由写入方重算；列表那条 SQL 不取 content 那一列（正文只在详情里回）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_plaza_message")
@ApiModel(value = "广场消息")
public class ShopPlazaMessage extends BaseEntity {

    @ApiModelProperty(value = "发布用户ID（sys_user.id）；发布时服务端按登录账号钉，改不了")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long userId;

    @ApiModelProperty(value = "关联类型：0无关联 1商品 2门店 3品牌 4活动（字典 plaza_relation_type）")
    private Integer relationType;

    @ApiModelProperty(value = "关联对象ID，按 relationType 指向四张表；改成无关联时要能清空，用 IGNORED")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long relationId;

    @ApiModelProperty(value = "标题，可空")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String title;

    @ApiModelProperty(value = "富文本正文（HTML），必填")
    private String content;

    @ApiModelProperty(value = "纯文本摘要（服务端从正文剥标签算，表单不收）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String contentText;

    @ApiModelProperty(value = "广场消息图URL（发布人自己上传，多张用英文逗号拼在这一格，可空；表单不收就是清空）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String plazaImage;

    @ApiModelProperty(value = "是否置顶：1是 0否（只有后台改得动）")
    private Integer isTop;

    @ApiModelProperty(value = "是否推荐：1是 0否（只有后台改得动）")
    private Integer isRecommend;

    @ApiModelProperty(value = "点赞数（读的时候按 shop_plaza_like 现算覆盖，实体写不了）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer likeCount;

    @ApiModelProperty(value = "评论数（读的时候按 sys_comment 的 comment_type=4 现算覆盖，实体写不了）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer commentCount;

    @ApiModelProperty(value = "分享数（读的时候按 shop_plaza_share 现算覆盖，实体写不了）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer shareCount;

    @ApiModelProperty(value = "审核状态：0待审核 1已发布 2已拒绝（字典 plaza_audit_status）")
    private Integer auditStatus;

    @ApiModelProperty(value = "审核备注（拒绝时给发布者看的那一句）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String auditRemark;

    @ApiModelProperty(value = "状态：1正常 2已隐藏（字典 plaza_status；0 是历史值，删除走 is_deleted）")
    private Integer status;

    @ApiModelProperty(value = "发布时间（审核通过那一刻服务端补，之后不覆盖）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime publishTime;

    // 下面三格都不是本表列，只在两条读 SQL 的结果里带回来：
    // 发布者昵称联 sys_user，关联对象名按 relation_type 从四张表里挑那一格（名字一律不在这张表里冗余快照，同采购单主表）
    @ApiModelProperty(value = "发布者昵称（联表带出，非本表列；账号删了联不上，前端按 id 显示）")
    @TableField(exist = false)
    private String userName;

    @ApiModelProperty(value = "关联对象名称（联表带出，非本表列；对象删了就是空）")
    @TableField(exist = false)
    private String relationName;

    // 「我」在这一条上赞过没有：只有带着 userId 的那条列表 SQL 联 shop_plaza_like 本账号那一行带得回来，
    // 前台点赞那颗按钮靠它决定是「赞」还是「已赞」（不带着就只能点了才知道）
    @ApiModelProperty(value = "我是否赞过（联表带出，非本表列；没登录或没赞过是 false）")
    @TableField(exist = false)
    private Boolean selfLiked;

    // 查询用的临时字段（只用于查询条件，不是本表列）
    @ApiModelProperty(value = "查询：标题/摘要/发布者昵称关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;
}
