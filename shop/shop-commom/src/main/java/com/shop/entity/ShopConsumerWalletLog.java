package com.shop.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 钱包流水（钱包管理列表那一行点开的「流水」弹窗）：一次余额变动一行，只插不改不删
 *
 * 这张表是 shop_consumer_wallet 那本账的凭证页：账上只有当前数（balance 那一格），
 * 谁在什么时候因为哪张单把钱从多少改成多少，全在这一张表里。
 * 所以只有查询接口 —— 手工插一条"某人多了 100 块"的记录，账和流水就对不上了
 *（写法同积分流水、库存流水）。
 *
 * 和积分流水差的一处：这张表**继承 BaseEntity**。那份 DDL 有 update_by / update_time / is_deleted 三列
 *（积分流水没有，所以那一本自己不声明），继承了就有逻辑删除那一刀 —— 自定义 SQL 里要手写 `is_deleted = 0`。
 *
 * amount 是带符号的（收入为正、支出为负），balance 记的是**这一笔之后**账户上的可用余额：
 * 上一行的 balance 接下一行的 amount 就是这条账的来龙去脉，所以这两个数必须由写流水的那一步一起给
 *（同一笔动作占钱/还钱时，account 上动的是哪一格和流水里记的那一笔可能不是一回事 ——
 *  下单冻结走的是 balance → frozen_balance，流水那一行记的是这一笔被占住多少，账户上的 balance 确实掉了）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_consumer_wallet_log")
@ApiModel(value = "用户钱包流水")
public class ShopConsumerWalletLog extends BaseEntity {

    @ApiModelProperty(value = "用户ID（sys_user.id）")
    private Long userId;

    @ApiModelProperty(value = "变动金额，正为收入，负为支出")
    private BigDecimal amount;

    @ApiModelProperty(value = "变动后余额（写流水的那一步和账户表同一个事务里算出来）")
    private BigDecimal balance;

    @ApiModelProperty(value = "业务类型：1充值 2消费 3退款 4提现 5奖励 6冻结 7解冻 8管理员调整（字典 wallet_biz_type）")
    private Integer bizType;

    @ApiModelProperty(value = "关联业务ID（订单ID、退款单ID等，按 bizType 指向不同的表，所以库上没有外键）")
    private Long bizId;

    @ApiModelProperty(value = "关联业务单号（冗余，同出库单/配送单/积分流水那一路的口径）")
    private String bizNo;

    @ApiModelProperty(value = "说明（这一笔为什么进/出这些钱，给人看的）")
    private String description;

    // 不是本表列：XML 联 sys_user 带出来，弹窗顶上那行标题要说"这是谁的流水"（同积分流水的昵称）
    @ApiModelProperty(value = "用户昵称（联表回填，非本表列）")
    @TableField(exist = false)
    private String userName;
}
