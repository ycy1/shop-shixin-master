package com.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 营销徽章定义（营销活动 -> 徽章管理）
 *
 * 一枚徽章在这一张表上是"门槛 + 奖励"：reach_count 是达标线，points / coupon_ids 是达标之后一并兑现的东西。
 * 发到某个人手上才变成 shop_activity_badge_user 那一行（带三格快照）。
 *
 * 和活动、优惠券那几页一样不按账号类型收窄（平台看全部）：徽章是平台发的活动物资。
 *
 * 这一张表没有编号列（徽章靠 badge_name 认，名称故意不查重 —— 同一档活动做几枚同名徽章是常态，
 * 口径同活动名称、供应商名称），所以主键之外没有唯一键。
 *
 * badge_type 决定"谁来判达标、拿什么数去比 reach_count"，四条路是：
 * 1 门店 —— 支付回调触发，比这个人在**那一家店**（target_id = shop_store.id）买过的件数；
 * 4 消费 —— 同一次触发，比这个人在**全平台**买过的件数，同一把算式、不绑店；
 * 3 签到 —— 由定时任务批量判（不在签到那一步同步发），比的是累计签到天数；
 * 2 活动 —— 这一期没有自动判定（活动结束那一条定时任务还没做），只能靠列表上那颗「下发」手工发。
 * reach_count 留 0（或留空）时**自动链路一枚都不发**：达标线是 0 就等于"买过一件就成群发出去"，
 * 那种误配置宁可当成"这一枚只走人工下发"。
 *
 * status 是启用/禁用两档、不是流转状态（和活动那一列的 1未开始…4已取消 不是一套），
 * 所以禁用的徽章既不参与自动判定、也不在「下发」的可选范围内；
 * 列表按 status DESC 打头（启用的排前面，同仓库、门店那一类页的口径）。
 *
 * coupon_ids 存的是逗号分隔的券模板 id（shop_activity_coupon.id），不是 json —— 库里那一列是 varchar(50)，
 * 由下发那一步逐个兑现成用户名下的用户券（占模板的发行额度）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_activity_badge")
@ApiModel(value = "营销徽章定义")
public class ShopActivityBadge extends BaseEntity {

    /** 徽章类型（字典 badge_type）：1 门店 —— 绑一家店，比这个人在那家店买过的件数 */
    public static final int TYPE_STORE = 1;

    /** 徽章类型：2 活动 —— 绑一条活动（target_id = shop_activity.id），本期只能手工下发 */
    public static final int TYPE_ACTIVITY = 2;

    /** 徽章类型：3 签到 —— 不绑目标，比的是累计签到天数 */
    public static final int TYPE_SIGNIN = 3;

    /** 徽章类型：4 消费 —— 不绑目标，比这个人在全平台买过的件数 */
    public static final int TYPE_CONSUME = 4;

    /** 状态（字典 badge_status）：1 启用 —— 参与自动判定，也在「下发」的可选范围内 */
    public static final int STATUS_ENABLED = 1;

    /** 状态：0 禁用 —— 整枚徽章收起来，已经发到用户手上的那些行不动 */
    public static final int STATUS_DISABLED = 0;

    @ApiModelProperty(value = "徽章名称（这张表靠它认徽章，故意不查重）")
    private String badgeName;

    @ApiModelProperty(value = "徽章类型：1门店 2活动 3签到 4消费（字典 badge_type；达标判定按这一档分叉）")
    private Integer badgeType;

    @ApiModelProperty(value = "徽章文字（显示在徽章上的那几个字，如「新品」）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String badgeText;

    @ApiModelProperty(value = "徽章图标URL（后台表单那格是单图上传）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String badgeIcon;

    @ApiModelProperty(value = "优先级，数字越大越优先（一个人名下同时有多枚时，前端按这一格挑最突出的那枚）")
    private Integer priority;

    // 只有 1门店 / 2活动 两档用得上这一格，签到和消费两类本来就没有目标，所以它是可空又可清的
    //（改徽章类型就是要清掉它：从门店改成消费，那一格不该继续指着原来那家店）
    @ApiModelProperty(value = "目标ID：类型 1 存 shop_store.id、类型 2 存 shop_activity.id；类型 3/4 留 NULL")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long targetId;

    @ApiModelProperty(value = "达标线：门店/消费两类比的是买过的件数，签到那类比的是累计签到天数；"
            + "留 0 或留空时自动链路一枚都不发（只走手动下发）")
    private Long reachCount;

    @ApiModelProperty(value = "可获得积分（达标时连同徽章一起兑现，记一笔「11 徽章发放」的积分流水；0=不发分）")
    private Integer points;

    @ApiModelProperty(value = "可获优惠券（券模板ID 逗号分隔，如 12,15；达标时逐个发到这个人手上、各占一张发行额度）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String couponIds;

    @ApiModelProperty(value = "状态：1启用 0禁用（字典 badge_status）")
    private Integer status;

    @ApiModelProperty(value = "说明")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String description;

    // 目标名字一律联表现取，不在这张表里冗余快照（同活动、优惠券那两个 storeName）：
    // 门店改了名、活动换了标题要跟着变，而且 target_id 那一格按 badge_type 指向两张不同的表，
    // 两条 LEFT JOIN 各自钉住类型，同一次查询里最多只有一条能命中
    @ApiModelProperty(value = "目标名称（类型是门店/活动时联表带出，非本表列；其余类型是 NULL）")
    @TableField(exist = false)
    private String targetName;

    @ApiModelProperty(value = "已发放人数（按 shop_activity_badge_user 数出来带出，非本表列）")
    @TableField(exist = false)
    private Integer issuedUserCount;

    // 只有定时任务那一条批量判定 SQL 会带这一格（一枚徽章一次带回好几个够线的人，一个人一行），
    // 其余读路全是 null。不叫 userId 是为了不和"这枚徽章是谁建的"混起来 —— 创建人在 BaseEntity 的 create_by 上
    @ApiModelProperty(value = "这一行该发给谁（sys_user.id，非本表列；只有签到徽章的定时判定那条 SQL 带出）")
    @TableField(exist = false)
    private Long hitUserId;

    // 查询用的两个临时字段（同优惠券那页的关键字/类型/状态三格；这一张表没有可筛的业务日期，
    // 所以没有 startFrom/startTo 那一档）
    @ApiModelProperty(value = "查询：徽章名称/文字关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;
}
