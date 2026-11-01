package com.shop.api.controller.common;

import com.shop.common.Result;
import com.shop.controller.BaseCommController;
import com.shop.entity.ShopTrendyCollectRecord;
import com.shop.api.service.ShopTrendyCollectRewardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author xxj
 * @title ShopTrendyCollectionCommonController
 * @description 潮玩图鉴的「领取集齐奖励」—— 图鉴页（web /trendy/collection）上那一颗按钮
 *
 * 路径：继承 BaseCommController ⇒ POST /api/comm/trendy/collection/{trendyId}/claim，
 * 和抽盒那条 /api/comm/trendy/box/{boxId}/draw 同一个形状。放 /api/comm 不放 /api/web：这是要落库的**动作**，
 * 不是纯前台读接口（口径同抽盒、购票那两条）。
 *
 * 原来这一笔由定时任务一轮扫全平台自动发（trendyTask.grantCollectRewards），2026-10-30 撤掉那条任务、
 * 改成人来领：判据、幂等闸、兑现三档一分没改（见 ShopTrendyCollectRewardService），只是"什么时候到账"
 * 由调度器说了算变成由人点一下说了算 —— 上线前就集齐过的人不用补数据，他一进图鉴页就看到「可领取」。
 */
@Api(tags = "潮玩图鉴公共接口")
@RestController
@RequestMapping("/trendy/collection")
@RequiredArgsConstructor
public class ShopTrendyCollectionCommonController extends BaseCommController {

    private final ShopTrendyCollectRewardService shopTrendyCollectRewardService;

    /**
     * 领这一条系列的集齐奖励。
     *
     * 只收系列 id：谁领的按登录态取（能点名别人就等于替别人领），够没够线由服务层现判一遍 ——
     * 页面上那颗按钮只是「看起来能领」，绕过页面直接打接口也走同一句判定，不会多发一份。
     *
     * 回回来的是刚落库的那一行，reward_desc 那句就是"这一笔兑现成了什么"（积分到账 / 券进了卡包 /
     * 或者没发出去要找客服），页面直接把那句念给人听。
     */
    @PostMapping("/{trendyId}/claim")
    @ApiOperation(value = "领取集齐奖励（一人一条系列只领一次；没集齐、没配奖励、已领过各回一句话）")
    public Result<ShopTrendyCollectRecord> claim(@PathVariable("trendyId") Long trendyId) {
        return Result.success(shopTrendyCollectRewardService.claim(trendyId));
    }
}
