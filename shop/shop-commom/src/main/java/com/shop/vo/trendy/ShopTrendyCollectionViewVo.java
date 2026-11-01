package com.shop.vo.trendy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图鉴（潮玩专区 /trendy/collection）的一行：一条潮玩系列 + 我在这条系列上的收集进度 + 集齐给什么
 *
 * 一行是<b>系列</b>，不是款式：款式那一叠在商品详情和后台系列页看，这一页要看的是「整套集到哪了」。
 * 分子分母都是现算的（口径同定时判定和后台「集齐进度」那两条，三处共用一套谓词，不然页面说「还差一格」
 * 而领取那一步却判他够了，那种对不上没法解释）；recordId 那一格是领过没有的唯一凭据（领取记录表上
 * (trendy_id, user_id) 唯一，所以一人一条系列最多联出一行）。
 *
 * rewardText 不是表上的列：由读的那一层按 reward_type 那一档现把积分那一格念成「N 分」、
 * 券和实物回捞名字（和后台系列列表那一格同一份口径）。
 */
@Data
@ApiModel(value = "潮玩图鉴的一行（系列 + 我的收集进度 + 集齐奖励）")
public class ShopTrendyCollectionViewVo {

    @ApiModelProperty(value = "潮玩系列ID(shop_trendy.id)")
    private Long trendyId;

    @ApiModelProperty(value = "系列名称")
    private String trendyName;

    @ApiModelProperty(value = "系列封面（没图由页面占位）")
    private String coverImage;

    @ApiModelProperty(value = "系列说明")
    private String description;

    @ApiModelProperty(value = "这条系列当前的规格总数（进度那一句的分母；一个规格都没绑就是 0，那种永远集不齐）")
    private Integer totalSku;

    @ApiModelProperty(value = "我名下抽到过的、属于这条系列的规格种类数（同一种抽几次只算一种，空奖不算；没登录是 0）")
    private Integer collectedSku;

    @ApiModelProperty(value = "集齐奖励类型：1积分 2优惠券 3实物（字典 trendy_reward_type），NULL/别的值=这条系列还没配奖励")
    private Integer rewardType;

    @ApiModelProperty(value = "集齐奖励值（积分数/券模板ID/实物商品ID），页面只按它判「配没配」，念的是 rewardText")
    private String rewardValue;

    @ApiModelProperty(value = "集齐奖励的展示文案（服务层按类型回捞名字拼出来，没配奖励是空）")
    private String rewardText;

    @ApiModelProperty(value = "领取记录ID(shop_trendy_collect_record.id)，NULL=这一笔还没领")
    private Long recordId;

    @ApiModelProperty(value = "领的那一刻（还没领是 NULL）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime collectTime;

    @ApiModelProperty(value = "兑现说明：领到的是什么，或者当时为什么没发出去（没领是 NULL）")
    private String rewardDesc;

    @ApiModelProperty(value = "这一行现在能不能点「领取」：凑满了、这条系列的奖励配齐了、而且还没领过（没登录也给 true，"
            + "页面上那颗点了由接口回「请先登录…」那句话）")
    private Boolean canClaim;
}
