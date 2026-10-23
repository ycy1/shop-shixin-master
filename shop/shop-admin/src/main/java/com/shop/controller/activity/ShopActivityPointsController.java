package com.shop.controller.activity;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.dto.activity.ShopActivityPointsAdjustDto;
import com.shop.entity.ShopActivityPoints;
import com.shop.entity.ShopActivityPointsLog;
import com.shop.service.ShopActivityPointsLogService;
import com.shop.service.ShopActivityPointsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 积分管理（营销活动 -> 积分管理）
 *
 * 两个读接口按项目惯例只做登录校验（Sa-Token 拦截器兜全路径），不压权限；
 * activity:points:record 和 activity:points:detail 那两条只给前端收按钮用
 *（详情读的就是列表那一行已经带出来的五个数，不再开一条接口）。
 *
 * 唯一的写入口是「修改」那一颗（POST /activity/points/adjust）：它调的是积分，不是那五个账面数字 ——
 * 账户表是账（一行一个用户、五个数）、流水表是凭证（只插不改不删），两边的数必须由同一笔变动在
 * 同一个事务里一起写，表单直接填五个数就会出现"账上多了 1000 分、流水里什么都没有"
 *（写法同商品库存 + 库存流水那一对：那一页也只能改成本价那几个参数，库存数改不了）。
 * 所以这一颗压的是真权限。
 * 签到/购物/兑换那些加减分的路径不在这一个控制器里，在统一下单和支付回调那一路（shop-api）。
 */
@Api(tags = "积分管理")
@RestController
@RequestMapping("/activity/points")
@RequiredArgsConstructor
public class ShopActivityPointsController {

    private final ShopActivityPointsService shopActivityPointsService;

    /** 「积分流水」那颗按钮的明细，读的是另一张表（流水），所以是第二个 service（同优惠券那页的 /record/list） */
    private final ShopActivityPointsLogService shopActivityPointsLogService;

    @GetMapping("/list")
    @ApiOperation(value = "获取积分账户列表（联表带出昵称和手机号；累计获得/累计消耗/可用/冻结/即将过期读的都是库里存的那五格，可用积分多的排前面）")
    public Result<IPage<ShopActivityPoints>> list(ShopActivityPoints query) {
        return Result.success(shopActivityPointsService.selectPage(query));
    }

    /**
     * 这一条走的是另一张表（shop_activity_points_log），所以路径挂在 /activity/points 下面但由
     * ShopActivityPointsLogService 实现 —— 和优惠券那页的 /record/list 同一个形状：明细跟着主数据那一页给入口。
     */
    @GetMapping("/record/list")
    @ApiOperation(value = "获取某个用户的积分流水（列表页那颗「积分流水」弹窗，按人分页、最新一笔在前，只读；"
            + "points 带符号，正为获取负为消耗，balance 是这一笔之后的余额）")
    public Result<IPage<ShopActivityPointsLog>> recordList(ShopActivityPointsLog query) {
        return Result.success(shopActivityPointsLogService.selectRecordPage(query));
    }

    /**
     * 表单只收「给谁 / 加还是减 / 多少分 / 为什么（+ 增加那一头这批分几时过期）」，
     * 返回调整后这个用户的可用积分（前端拿它刷列表那一行）。
     * 扣减走的是带余额闸门的原子 UPDATE（可用积分不够就 0 行改动、整笔回滚），加分才允许把第一行账建出来。
     */
    @PostMapping("/adjust")
    @ApiOperation(value = "调整用户积分（页面那颗「修改」）：方向 + 数量 + 原因一起收，增加那一头多收一格过期时间（落流水的 expire_time），"
            + "增加走累计获得+可用一起加、扣减走可用→累计消耗，都是带闸门的原子 UPDATE；"
            + "改账和记流水在同一个事务里，流水那一条 biz_type=10 管理员调整")
    @OperationLogger("调整用户积分")
    @SaCheckPermission("activity:points:adjust")
    public Result<Integer> adjust(@RequestBody ShopActivityPointsAdjustDto form) {
        return Result.success(shopActivityPointsService.adjust(form));
    }
}
