package com.shop.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 积分账户（营销活动 -> 积分管理）：一个用户一行，装的是"这个人现在有多少分"
 *
 * 这张表是账不是单据（同 shop_erp_inventory）：没有新增/修改/删除接口，一行账要由第一次积分变动带出来。
 * 后台这一页只有一条写路 ——「修改」那颗按钮调的是"调整积分"（POST /activity/points/adjust，
 * 收 方向/数量/原因 三格，增加那一头再多收一格这批分的过期时间），表单里直接填这五个数是没有的：手工建一条"某人 1000 分"的账，
 * 流水里没有对应的进出，账和流水就对不上了。
 *
 * 五个数都是**存的**，读的时候不做减法：因为 balance 要被占（下单先冻在 frozen_points，
 * 支付才真扣走），存着才占得住。口径和活动的曝光量（现算）相反、和优惠券的发行总量/已发放（存的）一致。
 * balance = total_points − used_points − frozen_points 这条恒等式由写入方在同一个事务里保证。
 *
 * userId 是"这些积分是谁的"（sys_user.id 里的消费者），不是 create_by 那个"谁建的账"
 * （同购物车、用户券那两张表）；这一页也故意不按它收窄 —— 营销活动那几页都是平台侧看全部。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_activity_points")
@ApiModel(value = "积分账户")
public class ShopActivityPoints extends BaseEntity {

    @ApiModelProperty(value = "用户ID（sys_user.id），一个用户一行；这些积分是谁的，不是谁建的账")
    private Long userId;

    @ApiModelProperty(value = "累计获得积分（只由积分变动加减，表单改不了：送分加、退货把为这批货送的分收回去就减）")
    private Integer totalPoints;

    @ApiModelProperty(value = "累计消耗积分（兑换/抵扣/过期那几档，只由积分变动加）")
    private Integer usedPoints;

    @ApiModelProperty(value = "当前可用积分 = 累计获得 − 累计消耗 − 冻结中")
    private Integer balance;

    @ApiModelProperty(value = "冻结积分（下单锁定、支付完成才真扣走，取消订单退回来）")
    private Integer frozenPoints;

    @ApiModelProperty(value = "即将过期积分（按流水里 expire_time 快到的那些获取行算）")
    private Integer expirePoints;

    // 以下都不是本表列：XML 联 sys_user 带出来给列表回显用（同用户券发放记录那两格）
    @ApiModelProperty(value = "用户昵称（联表回填，非本表列）")
    @TableField(exist = false)
    private String userName;

    @ApiModelProperty(value = "手机号（联表回填，非本表列）")
    @TableField(exist = false)
    private String mobile;

    // 只用于查询条件（同优惠券那一页的 keyword）：这一页没有可改的表单，实体就是查询参数
    @ApiModelProperty(value = "查询：用户昵称/账号/手机号关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;
}
