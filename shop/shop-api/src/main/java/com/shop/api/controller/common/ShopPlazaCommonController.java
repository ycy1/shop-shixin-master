package com.shop.api.controller.common;

import com.shop.api.service.ShopPlazaWriteService;
import com.shop.common.Result;
import com.shop.controller.BaseCommController;
import com.shop.dto.plaza.ShopPlazaPublishDto;
import com.shop.dto.plaza.ShopPlazaShareDto;
import com.shop.dto.plaza.ShopPlazaUpdateDto;
import com.shop.entity.ShopPlazaMessage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author ycy
 * @title ShopPlazaCommonController
 * @description 广场公共接口（前台两端都会做的五个动作：发布、改自己那条、删自己那条、点赞、记一次分享）
 */
@Api(tags = "广场 公共接口")
@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopPlazaCommonController extends BaseCommController {

    private final ShopPlazaWriteService shopPlazaWriteService;

    /**
     * 发布一条广场消息：正文是富文本组件产出的 HTML（图片内嵌在标签里），落库一律 0 待审核。
     *
     * 路径：继承 BaseCommController 会被 WebMvcConfig 加上 /api/comm 前缀，
     * 即 POST /api/comm/shop/plaza/publish（前端调 comm 要写双前缀，request 的 baseURL 会再叠一层 /api）。
     * 这一条 Web 和 App 共用，所以不放 web/app 任何一包：同一个动作在两处写两遍判据，早晚对不上。
     *
     * 发完不会立刻出现在广场上，也还不给分 —— 后台「广场消息 → 审核」判成 1 已发布 那一刻才上广场，
     * 并在同一个事务里给发布者 10 积分（补一行「发布广场消息」的积分流水）。
     * 页面上要说清这一句，否则用户会以为发失败了。
     *
     * 参数里没有 userId 那一格 —— 能发的只有自己（登录态由 service 自己取，/api/** 不过 Sa-Token 拦截器，
     * 取不到就报「请先登录后再发布广场消息」）。
     */
    @PostMapping("/plaza/publish")
    @ApiOperation(value = "发布广场消息（本人发；回刚落库的那一行，状态是待审核，审核通过才上广场并给 10 积分）")
    public Result<ShopPlazaMessage> publish(@RequestBody ShopPlazaPublishDto form) {
        return Result.success(shopPlazaWriteService.publish(form));
    }

    /**
     * 改自己发的那一条：参数比发布多一个 id，其余五格同发布（正文照样打回 0 待审核）。
     *
     * 路径：POST /api/comm/shop/plaza/update。个人中心「广场消息」那一档里的「修改」走这一条，
     * 待审核的和被拒绝的也改得动 —— 改完重新排队，审核结论变了正文也就得重新看一遍。
     * 只能改自己的：归属由 service 拿登录 id 硬比，别人那条一律回「这条消息不存在或已经被删除」。
     */
    @PostMapping("/plaza/update")
    @ApiOperation(value = "修改广场消息（本人改；改完打回待审核，重新通过才再上广场）")
    public Result<Void> update(@RequestBody ShopPlazaUpdateDto form) {
        shopPlazaWriteService.update(form);
        return Result.success();
    }

    /**
     * 删自己发的那一条：软删，任意审核状态都删得动，名下收到的赞/评/分享不回收，已发的积分也不收回。
     *
     * 路径：POST /api/comm/shop/plaza/delete/{id}（写法同评论那条 POST /api/comm/shop/comment/delete/{id}）。
     * 这一颗不放后台那一条链上：后台删的是「平台收掉这条内容」，这里删的是「作者自己撤回」，
     * 判据都是「本人」，所以和发布/点赞一样两端共用。
     */
    @PostMapping("/plaza/delete/{id}")
    @ApiOperation(value = "删除广场消息（本人删；只收自己那一条，回同一句「不存在或已被删除」）")
    public Result<Void> delete(@PathVariable("id") Long id) {
        shopPlazaWriteService.delete(id);
        return Result.success();
    }

    /**
     * 点赞那颗按钮：一颗管两个方向，点过就取消、没点过就点上，回按完之后的状态。
     *
     * 路径：POST /api/comm/shop/plaza/like/{id}（按消息 id 点，一人一次由库里那道 uk_message_user 保证）。
     * 必须登录 —— 点赞记的是「哪个人点的」，游客没有这一格可记；未登录点它，
     * 前端挂的是 data-open-login（同那颗收藏按钮），根本走不到这里。
     * 只有广场上看得见的那一条点得到（待审核的点了也没地方显示那个数）。
     */
    @PostMapping("/plaza/like/{id}")
    @ApiOperation(value = "点赞/取消点赞（本人一次，回按完之后的状态：true 已点赞 false 已取消）")
    public Result<Boolean> toggleLike(@PathVariable("id") Long id) {
        return Result.success(shopPlazaWriteService.toggleLike(id));
    }

    /**
     * 记一次分享：往 shop_plaza_share 插一行留痕，只插不改不删也不判重复（同一个人复制链接三次就是三行）。
     *
     * 路径：POST /api/comm/shop/plaza/share。广场上的「分享数」由这张表现算，所以这一条不回写任何计数列。
     * 本期不做真接微信/QQ 的分享 SDK：网页端点「复制链接」记 5、App 端转发记 1/2，
     * 渠道那一格由前端点名，不传按 5。
     */
    @PostMapping("/plaza/share")
    @ApiOperation(value = "记录一次分享（本人分享，参数只有消息id和渠道；分享数读的时候现算）")
    public Result<Void> share(@RequestBody ShopPlazaShareDto form) {
        shopPlazaWriteService.share(form);
        return Result.success();
    }
}
