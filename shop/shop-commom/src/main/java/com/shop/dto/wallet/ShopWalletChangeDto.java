package com.shop.dto.wallet;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 钱包充值 / 提现参数（前台「我的钱包」那两颗，走 comm 两端共用）
 *
 * 只有两格能填：这一笔多少钱、为什么（原样写进流水的说明）。
 * 账上那几个数一概不在参数里 —— 这一本是账，余额只能由充值进、提现出、下单占、退款还这些动作
 * 自己加加减减；表单让调用方直接填余额就等于允许"账上多了 500 块、流水里却什么都没有"，
 * 流水页上「变动后余额」那一列从此接不上（口径同管理员调整积分那颗）。
 *
 * userId 也不在参数里：动的是登录这个人自己那本账，由服务层取登录态钉死，
 * 前端传谁的 id 都不认（/api/** 不过 Sa-Token 拦截器，登录态是服务层自己取的）。
 */
@Data
@ApiModel(value = "钱包充值/提现参数")
public class ShopWalletChangeDto {

    @ApiModelProperty(value = "金额（元），必填、大于 0、最多两位小数。"
            + "提现时不能大过钱包里的可用余额（下单没付的单子占着的那一部分在 frozen_balance 里，提不到）")
    private BigDecimal amount;

    @ApiModelProperty(value = "说明（选填，原样写进流水的 description，是这一笔唯一的留痕处）；"
            + "留空由服务端按动作补一句默认的，例如：余额充值 / 余额提现")
    private String description;
}
