package com.shop.api.controller.common;

import com.shop.api.service.ShopWalletService;
import com.shop.common.Result;
import com.shop.controller.BaseCommController;
import com.shop.dto.wallet.ShopWalletChangeDto;
import com.shop.entity.ShopConsumerWallet;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author xxj
 * @title ShopWalletCommonController
 * @date 2026/10/7 10:30
 */
@Api(tags = "shop 钱包公共接口（充值 / 提现）")
@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopWalletCommonController extends BaseCommController {

    private final ShopWalletService shopWalletService;

    /**
     * 余额充值：往自己那本钱包里收一笔钱 —— 可用余额和累计充值一起加这一笔，
     * 同一个事务里补一行「1 充值」的流水（金额为正、变动后余额读的是刚改过的那一版）。
     * 这个人第一次充值时，那一行账户也是这一趟建出来的（这张表没有"新增账户"的入口）。
     *
     * 路径：继承 BaseCommController 会被 WebMvcConfig 加上 /api/comm 前缀，
     * 即 POST /api/comm/shop/wallet/recharge（前端调 comm 要写双前缀）。
     * /api/** 不过 Sa-Token 拦截器，所以由服务层自己取登录态，取不到就报"请先登录后再充值余额"；
     * 入参里没有 userId 这一格，动的永远是登录这个人自己的账。
     *
     * 本期这一笔不涉及真收款的渠道：微信支付那条充值单链（订单类型 4）还没接，
     * 所以谁充的记在流水的 create_by、为什么充记在说明里，biz_id / biz_no 留空。
     */
    @PostMapping("/wallet/recharge")
    @ApiOperation(value = "余额充值（加可用余额和累计充值 + 一行充值流水）")
    public Result<ShopConsumerWallet> recharge(@RequestBody ShopWalletChangeDto form) {
        return Result.success(shopWalletService.recharge(form));
    }

    /**
     * 余额提现：把自己那本钱包里的可用余额取走一笔 —— 可用余额减、累计提现加同一笔，
     * 同一个事务里补一行「4 提现」的流水（金额为负）。
     * 下单没付的单子占着的那一部分在 frozen_balance 上，那一部分提不走；账户被冻住（status 0）也提不走。
     *
     * 路径：POST /api/comm/shop/wallet/withdraw。登录态和归属的口径同上面充值那一条。
     * 和充值相反：没有账户行就是没有钱可提，这一颗不会顺手建账。
     * 本期也不建提现单 —— 钱从账上核销掉、真给出去走的是线下那一路，要查谁什么时候提了多少翻流水那一行。
     */
    @PostMapping("/wallet/withdraw")
    @ApiOperation(value = "余额提现（减可用余额、加累计提现 + 一行提现流水）")
    public Result<ShopConsumerWallet> withdraw(@RequestBody ShopWalletChangeDto form) {
        return Result.success(shopWalletService.withdraw(form));
    }
}
