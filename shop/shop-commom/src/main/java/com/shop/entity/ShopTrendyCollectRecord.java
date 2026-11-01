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
 * 潮玩系列集齐领取记录
 *
 * 一行 = 一个人集齐了一条系列，而且把那一笔奖励领了。它是这条链的<b>幂等闸</b>：集齐比的是存量
 *（这个人名下抽到过的规格种类数 vs 这条系列当前的规格总数），领取那一步现判一遍，
 * 没有这一行就会出现"同一条系列的奖励点一次发一份" —— 所以 uk_trendy_user 是这张表的核心，
 * 领取那一步先插这一行、插成功了才去兑现（撞唯一键就是"这一笔已经领过了"）。
 * 原来是定时任务一轮扫全平台、扫到就自动发（2026-10-30 撤了，改成图鉴页上那颗「领取奖励」），
 * 表的形状、判据、兑现三档都没变，只有"谁按下这一次"变了。
 *
 * reward_type / reward_value / sku_count 三格是**快照**：从 shop_trendy 上抄的（类型取值见
 * {@link ShopTrendy#REWARD_POINTS} 那三个常量），之后运营改了奖励、系列又加了新规格，
 * 都不回头刷已经发出去的那一行（口径同徽章明细、盲盒抽取记录）。
 *
 * 只插不改（除了一句兑现说明），后台没有这一张表的新增/修改/删除入口。
 * create_by 由 MyMetaObjectHandler 盖当前登录用户：这一张表现在只有"人来领"这一个写入方，
 * 所以那一格就是点按钮的那个人；认业务归属仍然只认 user_id（同其它 shop_* 表的口径）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_trendy_collect_record")
@ApiModel(value = "潮玩系列集齐领取记录")
public class ShopTrendyCollectRecord extends BaseEntity {

    @ApiModelProperty(value = "潮玩系列ID(shop_trendy.id)")
    private Long trendyId;

    @ApiModelProperty(value = "集齐的用户ID(sys_user.id)")
    private Long userId;

    @ApiModelProperty(value = "集齐那一刻该系列下的规格数（快照）")
    private Integer skuCount;

    @ApiModelProperty(value = "集齐判定通过的时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime collectTime;

    @ApiModelProperty(value = "奖励类型（发放那一刻的快照，字典 trendy_reward_type）")
    private Integer rewardType;

    @ApiModelProperty(value = "奖励值（发放那一刻的快照：积分数/券模板ID/实物商品ID）")
    private String rewardValue;

    @ApiModelProperty(value = "兑现出来的用户券ID(shop_activity_coupon_user.id)，积分那一档是 NULL")
    private Long couponUserId;

    @ApiModelProperty(value = "兑现说明：发出去的是什么，或者为什么没发出去")
    private String rewardDesc;

    // 下面两格不是本表列：判定那条 SQL 把"这一笔该发给谁、兑现什么"一起带回来时顺手带的
    @TableField(exist = false)
    @ApiModelProperty(value = "潮玩系列名称（服务层/判定SQL带出，非本表列）")
    private String trendyName;

    @TableField(exist = false)
    @ApiModelProperty(value = "用户名称（服务层回填，非本表列）")
    private String userName;
}
