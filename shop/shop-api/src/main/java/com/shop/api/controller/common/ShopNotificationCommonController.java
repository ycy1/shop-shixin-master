package com.shop.api.controller.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.api.service.ShopNotificationReadService;
import com.shop.common.Result;
import com.shop.controller.BaseCommController;
import com.shop.vo.notifications.NotificationUnreadVo;
import com.shop.vo.notifications.NotificationsListVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author ycy
 * @title ShopNotificationCommonController
 * @description 我的消息 公共接口（Web 消息中心、App 消息页、后台铃铛三端共用）
 */
@Api(tags = "消息通知 公共接口")
@RestController
@RequestMapping("/shop/notification")
@RequiredArgsConstructor
public class ShopNotificationCommonController extends BaseCommController {

    private final ShopNotificationReadService shopNotificationReadService;

    /**
     * 我的消息那一页：当前登录用户可见的消息分页，未读的打头。
     *
     * 路径：继承 BaseCommController 会被 WebMvcConfig 加上 /api/comm 前缀，
     * 即 GET /api/comm/shop/notification/list（前端调 comm 要写双前缀，request 的 baseURL 会再叠一层）。
     * 放 comm 而不是 web/app 各一条：三端读的是同一批行、同一套可见性判据，一条读路分成两条写，早晚对不上。
     *
     * category 可空（两档一起）；分页吃查询串里的 pageNum/pageSize，由 PageUtil 直接从请求里取，和后台列表同一套。
     * 行上带 businessType + businessId，跳转由各端自己拼路由 —— link 那一格刻意留空，
     * 服务端存任何一家前端的路径都是错的（Web 的路由和 App 的不是一套）。
     */
    @GetMapping("/list")
    @ApiOperation(value = "我的消息列表（未读打头；category=1 系统 / 2 互动，不传两档一起）")
    public Result<IPage<NotificationsListVo>> list(@RequestParam(value = "category", required = false) Integer category) {
        return Result.success(shopNotificationReadService.page(category));
    }

    /**
     * 未读汇总：一趟给回「总数 + 系统/互动各多少」三格。
     *
     * 路径：GET /api/comm/shop/notification/unread。
     * 为什么合成一趟而不是三个接口：MySQL 在那台公网机器上，一发一个来回，
     * 红点只看 total、两档 tab 各看一格，三条 count 本来就在同一条 SQL 里算得完（SUM(CASE)）。
     * 铃铛和红点都只在打开下拉/进个人中心时读一次，没有轮询。
     */
    @GetMapping("/unread")
    @ApiOperation(value = "未读汇总（总数 + 两档各多少，红点和 tab 角标共用这一趟）")
    public Result<NotificationUnreadVo> unread() {
        return Result.success(shopNotificationReadService.unread());
    }

    /**
     * 点开一条就算已读：只动当前用户那一行 receiver，别人看到的还是未读。
     *
     * 路径：POST /api/comm/shop/notification/read/{id}。幂等 —— 重复点同一颗只是把 read_time 刷成 now，
     * 不会插出第二行（uk_message_user 挡着），所以前端不需要防连点。
     */
    @PostMapping("/read/{id}")
    @ApiOperation(value = "标记单条已读（幂等）")
    public Result<Void> read(@PathVariable("id") Long id) {
        shopNotificationReadService.read(id);
        return Result.success();
    }

    /**
     * 全部已读：只清当前这一档，消息中心两档 tab 上那颗按钮管不到另一档。
     *
     * 路径：POST /api/comm/shop/notification/readAll?category=2；category 不传就是两档一起清（后台铃铛那颗「全部已读」）。
     */
    @PostMapping("/readAll")
    @ApiOperation(value = "本档全部已读（category 可空 = 两档一起）")
    public Result<Void> readAll(@RequestParam(value = "category", required = false) Integer category) {
        shopNotificationReadService.readAll(category);
        return Result.success();
    }

    /**
     * 删一条：软删的是「我这条消息的接收记录」（is_deleted=1），只对当前用户隐藏。
     *
     * 路径：POST /api/comm/shop/notification/delete/{id}。
     * 后台那条真删（sys_notifications.del_flag）在另一路上，这里做不到 —— 消息是发给一批人的，
     * 其中一个人删得掉就等于把别人的通知也抹了。
     */
    @PostMapping("/delete/{id}")
    @ApiOperation(value = "删除本人的一条消息（仅对自己隐藏）")
    public Result<Void> delete(@PathVariable("id") Long id) {
        shopNotificationReadService.delete(id);
        return Result.success();
    }

    /**
     * 清空全部：一条 INSERT..SELECT 把名下可见的那一批全铺成已删，和单条 delete 同样是「只对自己隐藏」。
     *
     * 路径：POST /api/comm/shop/notification/deleteAll?category=1；category 不传两档一起清（页头铃铛那颗「全部清空」）。
     * 为什么补这一条而不是让前端按 id 循环调 delete：库在公网，一次一个来回，名下几十条就是几十趟，
     * 而且循环只能清掉面板捞到的那几页 —— 名字写着「全部」却留着一堆没删干净，比慢更糟。
     */
    @PostMapping("/deleteAll")
    @ApiOperation(value = "清空本人名下消息（category 可空 = 两档一起；仅对自己隐藏）")
    public Result<Void> deleteAll(@RequestParam(value = "category", required = false) Integer category) {
        shopNotificationReadService.deleteAll(category);
        return Result.success();
    }
}
