package com.shop.api.controller.common;

import com.shop.common.Result;
import com.shop.controller.BaseCommController;
import com.shop.dto.park.ShopParkTicketBuyDto;
import com.shop.service.ShopParkTicketBuyService;
import com.shop.vo.trendy.ShopParkTicketBuyVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author xxj
 * @title ShopParkTicketCommonController
 * @description 主题公园购票公共接口（web 和小程序两端共用的那一条动作：买票）
 */
@Api(tags = "trendy 主题公园购票公共接口")
@RestController
@RequestMapping("/park/ticket")
@RequiredArgsConstructor
public class ShopParkTicketCommonController extends BaseCommController {

    private final ShopParkTicketBuyService shopParkTicketBuyService;

    /**
     * 买一张（或几张）票。
     *
     * 路径：POST /api/comm/park/ticket/buy（前端调 comm 要写双前缀，request 的 baseURL 会再叠一层 /api）。
     * 放 /api/comm 不放 /api/web：这是两端共用的**动作**，和抽盒同一条口径；
     * 后台那一路的「代客出票」不走这里 —— 那一颗是运营替某个用户出票，能点名给谁，这条不能。
     *
     * 给谁由登录态决定（请求体里没有 userId 这一格），落到名下的形状、票码、票面二维码
     * 都走出票那一条（shop-commom 的 ShopParkTicketIssueService，全平台只有那一处写用户票表）。
     * 这一条要登录态：/api/** 不过 Sa-Token 拦截器，鉴权在服务里自己取，取不到报「请先登录后再购票」。
     * 只报钱不扣钱：返回里的 amount 是这一单该付多少，扣余额/走支付还没接（口径同抽盒）。
     */
    @PostMapping("/buy")
    @ApiOperation(value = "购买园区门票（一次一张或几张，一张票落一行）")
    public Result<ShopParkTicketBuyVo> buy(@RequestBody ShopParkTicketBuyDto form) {
        return Result.success(shopParkTicketBuyService.buy(form));
    }
}
