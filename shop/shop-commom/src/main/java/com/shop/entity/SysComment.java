package com.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * 评论（一条评论一行，商品/门店/品牌/广场四类共用这一张表）
 *
 * 一张表挂四样对象：business_id 指向哪张表由 comment_type 定（1商品→shop_product.id 是 SPU 那一层、
 * 2门店→shop_store.id、3品牌→shop_brand.id、4广场→shop_plaza_message.id），
 * 一列指四张表所以库上不建外键，名称也不在这儿冗余 ——
 * 后台列表按 comment_type 现联表带出，口径同 shop_consumer_favorite.target_id。
 *
 * 层级不限：parent_id 为 NULL 就是顶层评论，回复挂在被回复的那一行下面（回复的回复再回复，想多深有多深）；
 * reply_user_id 记被回复人，页面上那句「回复 @某人」读它。删一行会连名下整棵子树一起软删。
 *
 * 表情和图片都不另建行、也不另建列：emoji 就是正文里的普通字符，图片以 `[图片](https://…)` 这一串语法
 * 内嵌在同一格 content 里，跟着它一起存（这一格是 utf8mb4，4 字节字符存得住）。
 * 画的时候两端各自把 content 拆成「文本段 + 图片段」交替渲染：文本段仍是纯文本插值，
 * 任何时候不走 v-html —— 拆串是为了把 URL 交给 img 的 src，不是为了把正文当 HTML。
 *
 * 写入只有两条路：前台「发表/回复/点赞/删自己那一条」和后台「编辑/删除」，各自的 service 各写各的。
 * 这一张表没有 create_by/update_by 那两格留痕列（sys_* 老表本来就没有，不像 shop_* 那样六个共用字段），
 * 谁写的看 user_id，什么时候写的看 create_time。
 *
 * like_count 是**存**的数：前台「点赞/取消点赞」那一条链路按带 GREATEST 的原子加减改它，
 * 所以它和 sys_comment_like 里的行数一致；后台「编辑评论」那一格也允许直接改，改了就和流水对不上，
 * 要重新对齐跑一次「按 sys_comment_like 全表回写」那条对账 UPDATE。
 *
 * isDeleted 这一格会触发 MyBatis-Plus 的全局逻辑删除（application.yml 的 logic-delete-field 按字段名接管）：
 * BaseMapper 的查询自动带 is_deleted = 0、removeByIds 自动变成 UPDATE，
 * 但 SysCommentMapper.xml 里那几条自定义 SQL 不走这套，谓词都得手写 —— 加联表时别漏。
 */
@Data
@TableName("sys_comment")
@ApiModel(value = "评论对象")
public class SysComment implements Serializable {

    /** 评论类型（字典 comment_type）：1 商品 —— business_id 是 shop_product.id（SPU 那一层，不分规格） */
    public static final int TYPE_PRODUCT = 1;

    /** 评论类型：2 门店 —— business_id 是 shop_store.id */
    public static final int TYPE_STORE = 2;

    /** 评论类型：3 品牌 —— business_id 是 shop_brand.id */
    public static final int TYPE_BRAND = 3;

    /** 评论类型：4 广场 —— business_id 是 shop_plaza_message.id（只对已上广场的那几条开放，判据在广场写服务里） */
    public static final int TYPE_PLAZA = 4;

    /** 是否置顶：1 置顶 —— 列表排序把它打头，只有顶层评论用得着 */
    public static final int STICK_YES = 1;

    @TableId(type = IdType.AUTO)
    @ApiModelProperty(value = "评论主键ID，自增唯一标识")
    private Integer id;

    @ApiModelProperty(value = "关联对象ID：按 commentType 分叉指向 shop_product / shop_store / shop_brand / shop_plaza_message 的主键（不建外键）")
    private Long businessId;

    @ApiModelProperty(value = "发表评论的用户ID(sys_user.id)，由服务端按登录态钉")
    private Long userId;

    @ApiModelProperty(value = "被回复人用户ID(sys_user.id)，NULL=不是对着某个人说话")
    private Long replyUserId;

    @ApiModelProperty(value = "父评论ID(sys_comment.id)，NULL=顶层评论；回复挂在被回复那一行下面，层数不限")
    private Integer parentId;

    @ApiModelProperty(value = "评论类型：1商品 2门店 3品牌 4广场（字典 comment_type）")
    private Integer commentType;

    @ApiModelProperty(value = "评论内容（纯文本 + 内嵌：emoji 是正文字符、图片写成 [图片](url)；前台拆串画、不走 v-html）。"
            + "长度由服务端校验：文字部分非空且不超 500 字，图最多 9 张")
    private String content;

    @ApiModelProperty(value = "点赞数（存的数，由点赞那条链原子加减，和 sys_comment_like 的行数一致）")
    private Integer likeCount;

    @ApiModelProperty(value = "是否置顶：1置顶 0不置顶")
    private Integer isStick;

    @ApiModelProperty(value = "来源IP（服务端现取，不信前端）")
    private String ip;

    @ApiModelProperty(value = "IP归属地（服务端按内存里那份 xdb 解析）")
    private String ipSource;

    @ApiModelProperty(value = "浏览器（服务端从 User-Agent 判）")
    private String browser;

    @ApiModelProperty(value = "操作系统（服务端从 User-Agent 判）")
    private String systemOs;

    @ApiModelProperty(value = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @ApiModelProperty(value = "逻辑删除：0未删除 1已删除（后台那颗删除写的就是这一格，奖励和已发出去的内容都不回收）")
    private Integer isDeleted = 0;
}
