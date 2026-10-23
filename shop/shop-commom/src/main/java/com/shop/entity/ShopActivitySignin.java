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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户签到记录（营销活动 -> 签到管理）：一人一天一行，一次签到（或一次补签）落一行
 *
 * 这张表和积分账户一样是"账"不是单据：没有新增/修改/删除接口，行只能由签到动作写出来。
 * 写入方只有两个，都在 shop-api 的 ShopSigninService 里 —— 前台消费者自己签到
 *（POST /api/comm/activity/signin，落 1 正常签到）和后台日历里的补签
 *（POST /activity/signin/makeup，落 2 补签）；日历里看的就是本表这些行。
 *
 * sign_date 和 sign_time 是分开的两格，为的就是补签：补的是"哪一天"（sign_date 落到那个过去的日子），
 * 什么时候补的动作留在 sign_time 和 create_time 上。所以 uk_user_date(user_id, sign_date)
 * 是"一天一行"的判据，不是按时间戳去重。
 *
 * sign_type 只有 1 正常签到 / 2 补签两档（字典 sign_type）；points 是这一行拿到手的分（两档都从
 * sys_config points_config 的 signin 那一档取，所以补签和正常签到给得一样多）；
 * reward_json 这一轮没有写入方（连续签到的额外奖励还没定，先按"每次都是配置里那一个数"记天数）。
 *
 * userId 是"这一行是谁的签到"（sys_user.id 里的消费者），不是 create_by 那个"谁写的这一行"
 * —— 补签时这两个人正好不是一个：create_by 是点补签的后台账号，user_id 是被补的消费者（同购物车、积分账户）。
 * 这一页也故意不按 user_id 收窄：营销活动那几页都是平台侧看全部。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_activity_signin")
@ApiModel(value = "用户签到记录")
public class ShopActivitySignin extends BaseEntity {

    @ApiModelProperty(value = "用户ID（sys_user.id），这一行是谁的签到；不是补签的那个操作人")
    private Long userId;

    @ApiModelProperty(value = "签到归属日期（一天一行的判据；补签时是补的那一天，不是执行补签的那天）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD)
    private LocalDate signDate;

    @ApiModelProperty(value = "签到时间（正常签到=当刻；补签=补的那一刻）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime signTime;

    @ApiModelProperty(value = "签到类型：1正常签到 2补签（字典 sign_type）")
    private Integer signType;

    @ApiModelProperty(value = "本次获得积分（正常签到和补签都取 sys_config points_config 里 signin 那一档）")
    private Integer points;

    @ApiModelProperty(value = "额外奖励JSON，如 {\"couponId\":1,\"points\":10}（这一轮没有写入方）")
    private String rewardJson;

    /**
     * 连续签到天数：不是本表列，由服务层现算（算式见 {@link com.shop.utils.SigninUtils}）——
     * 从这个人最晚的那一天往前逐日回看，最多看 {@link com.shop.utils.SigninUtils#MAX_LOOK_BACK_DAYS} 天，遇断即停。
     * 三处都读这一格：列表那一行（按各组最近签到日算）、日历那一个月（按今天算，各行同一个数）、
     * 签到/补签返回的那一行（按刚落下的那一天算）。
     */
    @ApiModelProperty(value = "连续签到天数（非本表列：从最近那一天往前逐日回看、最多 7 天现算，遇断即停）")
    @TableField(exist = false)
    private Integer currentContinuous;

    // 以下都不是本表列：分组列表那一行的几个数是 GROUP BY user_id 现算出来的
    @ApiModelProperty(value = "列表：累计签到天数（COUNT(*)，非本表列）")
    @TableField(exist = false)
    private Integer signCount;

    @ApiModelProperty(value = "列表：其中补签次数（SUM(sign_type = 2)，非本表列）")
    @TableField(exist = false)
    private Integer makeupCount;

    @ApiModelProperty(value = "列表：签到累计获得积分（SUM(points)，非本表列）")
    @TableField(exist = false)
    private Integer totalPoints;

    @ApiModelProperty(value = "列表：最近签到日期（MAX(sign_date)，非本表列）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD)
    @TableField(exist = false)
    private LocalDate lastSignDate;

    // 以下由 XML 联 sys_user 带出来给列表回显用（同积分账户那两格）
    @ApiModelProperty(value = "用户昵称（联表回填，非本表列）")
    @TableField(exist = false)
    private String userName;

    @ApiModelProperty(value = "手机号（联表回填，非本表列）")
    @TableField(exist = false)
    private String mobile;

    // 只用于查询条件（同积分管理那一页的 keyword）：这一页没有可改的表单，实体就是查询参数
    @ApiModelProperty(value = "查询：用户昵称/账号/手机号关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;
}
