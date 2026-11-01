package com.shop.api.controller.common;

import com.shop.common.Result;
import com.shop.controller.BaseCommController;
import com.shop.service.ShopTrendyBoxDrawService;
import com.shop.vo.trendy.ShopTrendyBoxDrawVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author xxj
 * @title ShopTrendyBoxCommonController
 * @description 盲盒公共接口（web 和小程序两端共用的那一条动作：抽盒）
 */
@Api(tags = "trendy 盲盒公共接口")
@RestController
@RequestMapping("/trendy/box")
@RequiredArgsConstructor
public class ShopTrendyBoxCommonController extends BaseCommController {

    private final ShopTrendyBoxDrawService shopTrendyBoxDrawService;

    /**
     * 抽一趟盒。
     *
     * 路径：POST /api/comm/trendy/box/{boxId}/draw（前端调 comm 要写双前缀，request 的 baseURL 会再叠一层 /api）。
     * 放 /api/comm 不放 /api/web：这是两端共用的**动作**，不是纯前台读接口（口径同签到、加购那几条）。
     *
     * 只收盒的 id：谁抽的按登录态取（参数能点名别人就等于替别人抽），
     * 本趟抽几个也不由页面传 —— 单抽/抽签按盒配的 draw_count、端盒按「还有余量的款式各一个」，
     * 一番赏同样按 draw_count 但改成按顺序发赏，四种类型的差别都在服务层那一份策略里
     *（见 ShopTrendyBoxDrawServiceImpl 的类注释）。
     *
     * 这一条要登录态：/api/** 不过 Sa-Token 拦截器，所以鉴权在服务里自己取，取不到报「请先登录后再抽盒」。
     * 只报钱不扣钱：返回里的 cost 是这一趟该付多少，扣余额/走支付还没接。
     *
     * wholeBox 是页面上那颗「购买整盒」（不传 = 按盒的类型抽一趟）：整盒 = 还有余量的款式各一件、
     * 按盒上配的端盒价结、进车是一整盒一行。抽签那一档不发实物、当场拒；盒没配端盒价也当场拒
     *（判据都在服务层那一份里，见 ShopTrendyBoxDrawService#draw）。
     */
    @PostMapping("/{boxId}/draw")
    @ApiOperation(value = "抽盲盒（一趟；本趟抽几个由盒的类型和配置决定，wholeBox=true 是要一整盒）")
    public Result<ShopTrendyBoxDrawVo> draw(@PathVariable("boxId") Long boxId,
                                            @RequestParam(value = "wholeBox", required = false, defaultValue = "false")
                                            boolean wholeBox) {
        return Result.success(shopTrendyBoxDrawService.draw(boxId, wholeBox));
    }
}
