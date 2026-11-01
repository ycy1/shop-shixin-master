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
 * 钱包账户（消费者管理 -> 钱包管理）：一个用户一行，装的是"这个人现在有多少钱"
 *
 * 这张表是账不是单据（同积分账户 shop_activity_points、商品库存 shop_erp_inventory）：
 * 没有新增/修改/删除接口，一行账要由第一次真收钱的动作带出来（现在就是充值那一条），uk_user 钉住一人一行。
 *
 * 六个数都是**存的**，读的时候不做减法：balance 要被占（下单选余额支付先冻进 frozen_balance，
 * 支付成功才真扣走、取消订单退回来），存着才占得住。
 * 恒等式由写入方在同一个事务里守住：可用 + 冻结 + 累计消费 + 累计提现 = 累计充值 + 赠送，
 * 那种"读回来减一下再写回去"的现算口径不要改用 —— 一旦有人改账没记流水，账面和流水就断了。
 * 说人话的四条：
 * ① 充值：balance + 累计充值 一起加；② 消费结转：冻结 −、累计消费 +（balance 不动，下单冻结那一步已经减过）；
 * ③ 退款退回：balance +、累计消费 −；④ 提现：balance −、累计提现 +（钱从这一本整个挪出去）。
 *
 * gift_balance（充值送的部分）本期**只有列、没有写入方**：充值那一条只收本金、不送
 *（"充多少送多少"的口径还没定），所以它恒为 0.00，
 * 花的时候也不参与"先花赠送还是先花本金"的排序 —— 那个口径等他点名再定。
 *
 * userId 是"这些钱是谁的"（sys_user.id 里的消费者），不是 create_by 那个"谁建的账"；
 * 后台这一页平台/门店/ERP 账号看全部，只有消费者账号收窄到自己那一行（见 service）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_consumer_wallet")
@ApiModel(value = "钱包账户")
public class ShopConsumerWallet extends BaseEntity {

    @ApiModelProperty(value = "用户ID（sys_user.id），一个用户一行；这些钱是谁的，不是谁建的账")
    private Long userId;

    @ApiModelProperty(value = "可用余额（能拿来支付的这一格，下单冻结时从这里面挪走）")
    private BigDecimal balance;

    @ApiModelProperty(value = "冻结余额（下单选余额支付时锁定，支付成功结转、取消订单退回）")
    private BigDecimal frozenBalance;

    @ApiModelProperty(value = "累计充值（充值那一路加进来的总数，和 balance 一起加同一笔）")
    private BigDecimal totalRecharge;

    @ApiModelProperty(value = "累计消费（余额支付成功结转进来的那部分，退款会把它减回去）")
    private BigDecimal totalConsume;

    @ApiModelProperty(value = "累计提现（提现那一路扣出去的总数，从 balance 里挪走同一笔）")
    private BigDecimal totalWithdraw;

    @ApiModelProperty(value = "赠送余额（充值送的部分，本期没有写入方、恒为 0）")
    private BigDecimal giftBalance;

    @ApiModelProperty(value = "状态：1正常 0冻结（字典 wallet_status；冻结的账户占不到钱，余额支付那一拍会挡）")
    private Integer status;

    // 以下都不是本表列：XML 联 sys_user 带出来给列表回显用（同积分账户、用户券那两格）
    @ApiModelProperty(value = "用户昵称（联表回填，非本表列）")
    @TableField(exist = false)
    private String userName;

    @ApiModelProperty(value = "手机号（联表回填，非本表列）")
    @TableField(exist = false)
    private String mobile;

    // 只用于查询条件（同积分账户那一格）：这一页没有可改的表单，实体就是查询参数
    @ApiModelProperty(value = "查询：用户昵称/账号/手机号关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;
}
