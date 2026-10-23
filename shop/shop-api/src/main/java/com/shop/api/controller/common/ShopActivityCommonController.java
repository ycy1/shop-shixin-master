package com.shop.api.controller.common;


import com.shop.api.service.ShopActivityTrackService;
import com.shop.api.service.ShopSigninService;
import com.shop.common.Result;
import com.shop.controller.BaseCommController;
import com.shop.dto.activity.ShopActivityTrackDto;
import com.shop.dto.activity.ShopActivitySigninMakeupDto;
import com.shop.entity.ShopActivitySignin;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


/**
 * @author xxj
 * @title ShopPayCommonController
 * @date 2026/9/19 22:25
 */
@Api(tags = "activity 公共接口")
@RestController
@RequestMapping("/activity")
@RequiredArgsConstructor
public class ShopActivityCommonController extends BaseCommController {

    private final ShopActivityTrackService shopActivityTrackService;

    /** 签到这一张表的写入方（前台正常签到 + 后台补签）都在这一份 service 里 */
    private final ShopSigninService shopSigninService;

    /**
     * 活动埋点：前台 expose 一次、点一次都往 shop_activity_click 里记一行流水。
     *
     * 路径：继承 BaseCommController 会被 WebMvcConfig 加上 /api/comm 前缀，
     * 即 POST /api/comm/activity/track（前端调 comm 要写双前缀，request 的 baseURL 会再叠一层 /api）。
     *
     * 曝光和点击共用这一条，靠请求体里的 eventType 分（1 曝光 2 点击）：两张表是同一条流水，
     * 拆成两个端点只会把同一套校验抄两遍。
     *
     * /api/** 不过 Sa-Token 拦截器，所以这一条也不鉴权 —— 未登录照样收（user_id 那一格留 NULL），
     * 谁能触发、状态挡不挡、要不要防刷，全在 ShopActivityTrackServiceImpl 的类注释里。
     * 后台那三格统计（曝光量/点击量/点击率）不由这里回写，是读活动列表时按这张表现算的。
     */
    @PostMapping("/track")
    @ApiOperation(value = "活动埋点（曝光和点击共用一条，传 eventType 区分：1曝光 2点击；未登录也收）")
    public Result<Void> trackActivity(@RequestBody ShopActivityTrackDto form) {
        shopActivityTrackService.track(form);
        return Result.success();
    }

    /**
     * 用户签到：前台消费者给自己今天那一格签到，在 shop_activity_signin 里落一行 1 正常签到。
     *
     * 路径：POST /api/comm/activity/signin（前端调 comm 要写双前缀，request 的 baseURL 会再叠一层 /api）。
     * 没有参数也没有 user_id 那一格 —— 能签的只有自己，参数能点名别人就等于替别人签到
     *（登录态由 service 自己取，/api/** 不过 Sa-Token 拦截器，取不到就报"请先登录后再签到"）。
     *
     * 一天一行的判据是 sign_date（归属哪天），不是操作那一刻：后台日历上那颗「补签」写的是同一张表、
     * 走的是同一份 service，只是那一头的归属日由人填、类型落 2 补签。
     * 落行 + 给积分（sys_config points_config 的 signin 那一档）+ 记一笔「1 签到」积分流水，
     * 三件在一个事务里，判据全在 ShopSigninServiceImpl 的类注释里。
     */
    @PostMapping("/signin")
    @ApiOperation(value = "用户签到（本人签今天：一天一行，今天已签过会拒；回这一天的类型/积分/连续天数）")
    public Result<ShopActivitySignin> signin() {
        return Result.success(shopSigninService.sign());
    }

    /**
     * 本人补签：给自己名下没签过的过去某一天补一行 2 补签（后台「个人信息」页的日历用这一条）。
     *
     * 路径：POST /api/comm/activity/signin/makeup（前端调 comm 要写双前缀，request 的 baseURL 会再叠一层 /api）。
     * 请求体只有 signDate 那一格有用：userId 传什么都不认，能补的只有自己 ——
     * 和上面那条「签到」同一个道理，参数能点名别人就等于替别人签到，那一条在后台 /activity/signin/makeup，压的是 activity:signin:makeup 权限。
     *
     * 判据、给分、记流水全走 shop-api 那一份写入 service（和后台补签共用同一套），
     * 所以补出来的那一行和正常签到一样记分。
     * 只多一道本人特有的限制：只能补最近 7 天内的日子（不封顶就能把历史空日子全补一遍刷积分）。
     */
    @PostMapping("/signin/makeup")
    @ApiOperation(value = "本人补签（只收 signDate：必须是自己最近 7 天内没签过的过去日子；回补下来的那一行）")
    public Result<ShopActivitySignin> signinMakeup(@RequestBody ShopActivitySigninMakeupDto form) {
        return Result.success(shopSigninService.makeupSelf(form == null ? null : form.getSignDate()));
    }
}
