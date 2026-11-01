package com.shop.entity;

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
 * 用户徽章（一枚徽章发到一个人名上一行）
 *
 * 写入有三条路，都在 shop-api 的那一份发放服务里（{@code ShopActivityBadgeGrantService}）：
 * 两条定时任务（一轮判完全平台的签到徽章、一轮判完 1 门店 / 4 消费两类徽章）、后台列表上那颗「下发」手工发。
 * 这张表在后台只开三条写口子，都在「获取记录」弹窗里：那颗删除（抹掉一行，不动积分和券）、
 * 那一格「状态」的开关（只改 status 这一列）、那一格「是否展示」的开关（只改 is_show 这一列）—— 别的列一律不碰。
 * 前台还有第四条，也只有「是否展示」那一格：「个人信息 → 徽章」每行那颗「展示 / 收起」，
 * 判据同上只动 is_show，且只认登录 id 名下的那一行。
 * 没有新增/修改端点 —— 手工往这儿插一行等于凭空给一个人发一枚徽章，
 * 还顺带把徽章上那笔积分和那几张券兑现了。
 *
 * badge_name / badge_text / badge_icon 这三格是**快照**，和定义表同名但不是联表带出来的：
 * 发到手上那一刻定住形状，之后徽章改名、换图都不回头刷已经拿到的人（口径同用户券那六格快照）。
 * 有一格没快照下来：points / coupon_ids —— 兑现是在发放那一次就做完的动作（加分、建用户券），
 * 不是"以后随时能领"的东西，所以这两格不需要留在这一行上。
 *
 * uk_badge_user (badge_id, user_id) 是唯一那道闸：**一枚徽章一个人只有一行**。
 * 所以自动链路可以每次支付、每一轮定时任务都重跑一遍判定（达标了就发、已经发过就跳过），
 * 兑现也只跟着"第一次落到这个人手上"那一次做一次，不会因为网关重复推送多发一笔分。
 *
 * source 记的是"这枚徽章怎么到的用户手上"：签到落 1 系统发放、门店/消费落 3 消费达标、手工发落 4 手动发放，
 * 2 活动获得 和 5 门店发放 这两档本期没有写入方（活动那一条定时任务还没做）。
 * source_id 这一格现在三条写入路都留空（2026-10-18 起自动那两条全搬进定时任务）：
 * 一轮任务判的是全平台一批人，比的又是这个人名下的<b>存量</b>（签了几天、买过几件），
 * 没有"就是今天这一签 / 就是这一单"那一条记录可指。老行上这一格还有订单 id，认来源只认这一列的存量、别看空。
 *（要回看这个人签过哪几天去 shop_activity_signin、买过哪些单去 sys_order，都按 user_id 数。）
 *
 * userId 和 create_by 不是一回事：前者是"这枚徽章在谁手上"（消费者），后者是 MyMetaObjectHandler
 * 按当前登录用户强制盖的留痕列 —— 后台下发的那一批里两者通常不同（操作人是运营，持有人是用户）；
 * 自动那两条都跑在没有登录态的 quartz 线程上，那一行 create_by 就是 NULL，
 * 认来源只能看 source 和这一行是什么时候由哪一轮任务落的。
 *
 * end_time 留 NULL 是永久有效；这一张表上没有「已过期」那一档（2 已过期 2026-10-17 整条下线，
 * status 只有 1 有效 / 0 失效 两档，0 由后台「获取记录」那颗开关手工置），
 * 所以前端要判过期是拿 end_time 现比的（同用户券那边"过期是下单时拿 expire_time 现判"的做法）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_activity_badge_user")
@ApiModel(value = "用户徽章")
public class ShopActivityBadgeUser extends BaseEntity {

    /** 获得来源（字典 badge_source）：1 系统发放 —— 签到徽章落这一档（那五档里没有"签到"，不另加枚举值） */
    public static final int SOURCE_SYSTEM = 1;

    /** 获得来源：2 活动获得 —— 本期没有写入方（活动结束那一条分发链路还没做） */
    public static final int SOURCE_ACTIVITY = 2;

    /** 获得来源：3 消费达标 —— 1 门店 / 4 消费两类徽章由定时任务批量发 */
    public static final int SOURCE_CONSUME = 3;

    /** 获得来源：4 手动发放 —— 列表上那颗「下发」 */
    public static final int SOURCE_MANUAL = 4;

    /** 获得来源：5 门店发放 —— 位留着；本期"按门店判定"落的是 3 消费达标，不重复用这一档 */
    public static final int SOURCE_STORE = 5;

    /** 用户徽章状态（字典 user_badge_status）：1 有效 —— 发出去就落这一档（也是库里的默认值） */
    public static final int STATUS_VALID = 1;

    /** 状态：0 失效 —— 人工没收那一档，由后台「获取记录」弹窗里那颗开关写（不收积分和券，也不删这一行） */
    public static final int STATUS_INVALID = 0;

    /** 展示控制（字典 badge_show）：1 展示 —— 发出去就落这一档 */
    public static final int SHOW_YES = 1;

    /** 展示控制：0 隐藏 —— 两个口子会写：后台「获取记录」弹窗里那颗开关、前台「个人信息 → 徽章」每行那颗「收起」 */
    public static final int SHOW_NO = 0;

    @ApiModelProperty(value = "徽章ID(shop_activity_badge.id)")
    private Long badgeId;

    @ApiModelProperty(value = "获得用户ID(sys_user.id)")
    private Long userId;

    @ApiModelProperty(value = "徽章名称（发放时快照）")
    private String badgeName;

    @ApiModelProperty(value = "徽章文字（发放时快照）")
    private String badgeText;

    @ApiModelProperty(value = "徽章图标URL（发放时快照）")
    private String badgeIcon;

    @ApiModelProperty(value = "获得来源：1系统发放 2活动获得 3消费达标 4手动发放 5门店发放（字典 badge_source）")
    private Integer source;

    @ApiModelProperty(value = "来源业务ID（历史行上是 sys_order.id；2026-10-18 起三条写入路都不再填这一格，"
            + "自动那两条改由定时任务批量发、一轮没有可指的单据，手工下发本来也没有。这一列按 source "
            + "指向不同的表，所以库上没有外键）")
    private Long sourceId;

    @ApiModelProperty(value = "生效开始时间（发放那一刻服务端盖）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "生效结束时间，NULL=永久（下发弹窗里那一格留空就是永久）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime endTime;

    @ApiModelProperty(value = "状态：1有效 0失效（字典 user_badge_status；2 已过期 这一档 2026-10-17 已下线，"
            + "过期不看这一格、由前端拿 end_time 现比）。0 由后台「获取记录」弹窗里那一格那颗开关写；"
            + "读路是前台/APP 那两处「这一枚现在还算不算数」—— 失效的那枚还在列表里、还挂在账号卡下面，只是变灰")
    private Integer status;

    @ApiModelProperty(value = "是否展示：1展示 0隐藏（两个写入口都能改：后台「获取记录」弹窗里那一格、"
            + "前台「个人信息 → 徽章」每行那颗「展示 / 收起」）。读路只有顶部那一排挂件按这一格筛"
            + "（is_show=0 的不再挂在账号卡下面）；「我的徽章」那一列不筛 —— 收起来的还得在这一格里看得见才能放回来）")
    private Integer isShow;

    @ApiModelProperty(value = "排序（发放时抄定义表那一格的 priority）")
    private Integer sort;

    @ApiModelProperty(value = "获得用户昵称（联表带出，非本表列）")
    @TableField(exist = false)
    private String userName;

    @ApiModelProperty(value = "获得用户手机号（联表带出，非本表列）")
    @TableField(exist = false)
    private String mobile;

    @ApiModelProperty(value = "徽章类型（联定义表带出，非本表列；记录弹窗要说这枚是门店还是签到）")
    @TableField(exist = false)
    private Integer badgeType;

    // 查询用的临时字段：这一张表没有"编号"那种可搜的文本列，要按人筛就靠 userId
    //（服务层在消费者账号那一头把它覆盖成登录 id）
    @ApiModelProperty(value = "查询：徽章名称关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;
}
