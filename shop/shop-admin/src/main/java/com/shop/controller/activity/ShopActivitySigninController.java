package com.shop.controller.activity;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.api.service.ShopSigninService;
import com.shop.common.Result;
import com.shop.dto.activity.ShopActivitySigninMakeupDto;
import com.shop.entity.ShopActivitySignin;
import com.shop.service.ShopActivitySigninService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 签到管理（营销活动 -> 签到管理）
 *
 * 两个读接口按项目惯例只做登录校验（Sa-Token 拦截器兜全路径），不压权限；
 * activity:signin:record 那一条只给前端收「签到记录」那颗按钮用（日历读的就是这两条接口带回来的行）。
 *
 * 唯一的写入口是「补签」（POST /activity/signin/makeup）：它收的是"给谁、补哪一天"，
 * 签到类型/积分/连续天数/签到时间都由服务端算 —— 这一张表是"一人一天一行"的原始记录，
 * 手工插一行"某人那天签过"而不给分，积分流水里就缺一笔「签到」；只给分不写行，日历和账对不上。
 * 所以三件（落行、加积分账、记积分流水）在同一个事务里做完，这一颗压的是真权限。
 *
 * 写入那一份不在本模块（在 shop-api 的 ShopSigninService，前台消费者自己签到走的也是它，
 * 两边给的分一模一样），所以本模块的 service 只读；这一颗按钮调过去，权限和 @OperationLogger 仍压在这一层。
 */
@Api(tags = "签到管理")
@RestController
@RequestMapping("/activity/signin")
@RequiredArgsConstructor
public class ShopActivitySigninController {

    private final ShopActivitySigninService shopActivitySigninService;

    /** 签到动作的唯一实现（前台正常签到 + 后台补签共用那一份，本模块只调它） */
    private final ShopSigninService shopSigninService;

    @GetMapping("/list")
    @ApiOperation(value = "获取签到列表（一个用户一行：累计签到天数、其中补签次数、最近连续天数、累计获得积分、最近签到日期，"
            + "前几个数是 GROUP BY 现算的、连续天数由服务层逐行回数（这张表上不存那一列）；联表带出昵称和手机号，最近还签着的排前面）")
    public Result<IPage<ShopActivitySignin>> list(ShopActivitySignin query) {
        return Result.success(shopActivitySigninService.selectPage(query));
    }

    /**
     * 日历弹窗那一张表：某个人某一个月签过的那几天，一次全带（一个月最多 31 行，所以不分页）。
     * 筛的是归属日 sign_date，所以 8 月 31 日那天补的签会出现在 8 月那一页里。
     */
    @GetMapping("/record/list")
    @ApiOperation(value = "获取某个用户某个月的签到记录（列表页那颗「签到记录」弹窗，只读；"
            + "month 传 yyyy-MM，留空按当月）")
    public Result<List<ShopActivitySignin>> recordList(@RequestParam(required = false) Long userId,
                                                   @RequestParam(required = false) String month) {
        return Result.success(shopActivitySigninService.selectMonthRecords(userId, month));
    }

    @PostMapping("/makeup")
    @ApiOperation(value = "后台补签（日历里点某一天）：只收 给谁 + 补哪一天，那一天必须早于今天且还没签过；"
            + "签到类型落 2 补签、积分按 sys_config points_config 的 signin 那一档给、连续天数由服务端从这一天往前逐日回数（最多 7 天），"
            + "落行、加积分账、记积分流水（biz_type=1 签到）在同一个事务里")
    @OperationLogger("用户签到补签")
    @SaCheckPermission("activity:signin:makeup")
    public Result<ShopActivitySignin> makeup(@RequestBody ShopActivitySigninMakeupDto form) {
        return Result.success(shopSigninService.makeup(form));
    }
}
