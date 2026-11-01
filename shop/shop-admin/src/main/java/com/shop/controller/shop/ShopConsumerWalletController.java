package com.shop.controller.shop;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.common.Result;
import com.shop.entity.ShopConsumerWallet;
import com.shop.entity.ShopConsumerWalletLog;
import com.shop.service.ShopConsumerWalletLogService;
import com.shop.service.ShopConsumerWalletService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 钱包管理（消费者管理 -> 钱包管理）
 *
 * 两条读接口按项目惯例只做登录校验（Sa-Token 拦截器兜全路径），不压权限；
 * shop:wallet:record 和 shop:wallet:detail 那两条只给前端收按钮用
 *（详情读的就是列表那一行已经带出来的那几个数，不再开一条接口）。
 *
 * 这一本**没有写接口**：账户表是账（一行一个用户、那几个数）、流水表是凭证（只插不改不删），
 * 钱被动用的地方只有统一下单/支付回调/取消/退款那一条链（shop-api），
 * 后台这一页改了账就会出现"账上多了 100 块、流水里什么都没有"（口径同商品库存、积分账户）。
 * 积分那一本有的「修改」（管理员调整）这一轮没照搬过来 —— 动钱的那颗按钮等他点名。
 *
 * 两处读都按账号类型收窄：消费者账号只看自己那一本（「个人信息」那页的钱包 tab 直接调这两条）。
 */
@Api(tags = "钱包管理")
@RestController
@RequestMapping("/shop/wallet")
@RequiredArgsConstructor
public class ShopConsumerWalletController {

    private final ShopConsumerWalletService shopConsumerWalletService;

    /** 「流水」那颗按钮的明细，读的是另一张表（流水），所以是第二个 service（同积分那页的 /record/list） */
    private final ShopConsumerWalletLogService shopConsumerWalletLogService;

    @GetMapping("/list")
    @ApiOperation(value = "获取钱包账户列表（联表带出昵称和手机号；可用余额/冻结余额/累计充值/累计消费/"
            + "累计提现/赠送余额读的都是库里存的那几格，正常状态在前、可用余额多的排前面）")
    public Result<IPage<ShopConsumerWallet>> list(ShopConsumerWallet query) {
        return Result.success(shopConsumerWalletService.selectPage(query));
    }

    /**
     * 这一条走的是另一张表（shop_consumer_wallet_log），所以路径挂在 /shop/wallet 下面但由
     * ShopConsumerWalletLogService 实现 —— 和积分那页的 /record/list 同一个形状：明细跟着主数据那一页给入口。
     */
    @GetMapping("/record/list")
    @ApiOperation(value = "获取某个用户的钱包流水（列表页那颗「流水」弹窗，按人分页、最新一笔在前，只读；"
            + "amount 带符号，正为收入负为支出，balance 是这一笔之后的余额）")
    public Result<IPage<ShopConsumerWalletLog>> recordList(ShopConsumerWalletLog query) {
        return Result.success(shopConsumerWalletLogService.selectRecordPage(query));
    }
}
