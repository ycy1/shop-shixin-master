package com.shop.vo.trendy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.entity.ShopTrendy;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条潮玩系列上，某个人的集齐进度（后台「集齐进度」那一弹窗的一行）
 *
 * 一人一行，由抽取记录表按 user_id 分组现算出来（口径同「用户签到」那一页的列表：
 * 进度这种数跟着抽盒一起动，存一格就要有一条写入方回头刷它，而现在唯一的写入方是定时任务）。
 *
 * 两件事分开看，所以有两组列：
 * · collectedSku / totalSku —— 手上现在凑到几个、这套一共几个（两个都是<b>当前</b>的数，
 *   系列后来加了新规格，这一行就会从「已抽齐」退回「差几个」，这是想要的口径）；
 * · recordId / collectTime / rewardDesc —— 那一笔集齐奖励领过没有、什么时候领的、兑现成什么了
 *   （这一组是 {@link ShopTrendy} 那条系列第一次判过线那一刻<b>定格</b>的快照，之后改配置不回头刷它）。
 */
@Data
@ApiModel(value = "潮玩系列集齐进度")
public class ShopTrendyCollectProgressVo {

    @ApiModelProperty(value = "用户ID(sys_user.id)")
    private Long userId;

    @ApiModelProperty(value = "用户昵称（联 sys_user 带出）")
    private String userName;

    @ApiModelProperty(value = "用户手机号（联 sys_user 带出）")
    private String mobile;

    @ApiModelProperty(value = "名下抽到过的、属于这条系列的规格种类数（同一规格抽几次只算一种，空奖不算）")
    private Integer collectedSku;

    @ApiModelProperty(value = "这条系列当前的规格总数（分母）")
    private Integer totalSku;

    @ApiModelProperty(value = "集齐领取记录ID(shop_trendy_collect_record.id)，NULL=还没领过这一笔")
    private Long recordId;

    @ApiModelProperty(value = "集齐判定通过的时间（没领过是 NULL）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime collectTime;

    @ApiModelProperty(value = "兑现时的奖励类型快照（字典 trendy_reward_type）")
    private Integer rewardType;

    @ApiModelProperty(value = "兑现说明：发出去的是什么，或者为什么没发出去")
    private String rewardDesc;
}
