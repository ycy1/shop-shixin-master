package com.shop.controller.activity;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopActivity;
import com.shop.entity.ShopActivityClick;
import com.shop.service.ShopActivityService;
import com.shop.service.ShopActivityClickService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 活动（营销活动 -> 活动管理）
 *
 * 读接口按项目惯例只做登录校验（Sa-Token 拦截器兜全路径），权限只压在 add/update/delete 上；
 * 状态是表单里选的一个下拉、由 update 整行提交，所以没有单独的投放/结束/取消端点。
 * 统计那三格（曝光量/点击量/点击率）这一页只读不写：往 shop_activity_click 里记曝光和点击的是前台那条
 * POST /api/comm/activity/track（ShopActivityCommonController），这一页只把它数出来给人看。
 */
@Api(tags = "活动管理")
@RestController
@RequestMapping("/activity")
@RequiredArgsConstructor
public class ShopActivityController {

    private final ShopActivityService shopActivityService;

    /** 「查看记录」那颗按钮的明细，读的是另一张表（流水），所以是第二个 service */
    private final ShopActivityClickService shopActivityClickService;

    @GetMapping("/list")
    @ApiOperation(value = "获取活动列表（联表带出门店名，曝光量和点击量按 shop_activity_click 现算、点击率由这两个数除出来，"
            + "权重大的在前、同权重最新一条在前）")
    public Result<IPage<ShopActivity>> list(ShopActivity activity) {
        return Result.success(shopActivityService.selectPage(activity));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取一条活动详情（修改弹窗和只读详情弹窗共用）")
    public Result<ShopActivity> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopActivityService.selectActivityById(id));
    }

    /**
     * 这一条走的是另一张表（shop_activity_click 的流水），所以路径挂在 /activity 下面但由
     * ShopActivityClickService 实现 —— 和库存那页的 /log/list 同一个形状：明细跟着主数据那一页给入口。
     */
    @GetMapping("/record/list")
    @ApiOperation(value = "获取活动的曝光/点击明细（列表页那颗「查看记录」弹窗，按活动分页、时间倒序，只读）")
    public Result<IPage<ShopActivityClick>> recordList(ShopActivityClick query) {
        return Result.success(shopActivityClickService.selectRecordPage(query));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加活动：这张表没有编号列，活动就靠名称认；状态一律落 1 未开始，"
            + "活动位/活动类型/跳转类型/权重不传时补库里的默认值")
    @OperationLogger("添加活动")
    @SaCheckPermission("activity:add")
    public Result<Long> add(@RequestBody ShopActivity activity) {
        shopActivityService.insert(activity);
        // save() 会把自增主键回填到入参实体
        return Result.success(activity.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改活动：状态在这一次整行提交（1未开始/2投放中/3已结束/4已取消），"
            + "第一次进 2 时服务端盖投放时间，之后不覆盖；统计那三格库里没有列，是读的时候现算的")
    @OperationLogger("修改活动")
    @SaCheckPermission("activity:update")
    public Result<Void> edit(@RequestBody ShopActivity activity) {
        shopActivityService.update(activity);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除活动：只有从没投放过的能删，投放过的名下挂着点击记录，留档不动")
    @OperationLogger("删除活动")
    @SaCheckPermission("activity:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopActivityService.deleteByIds(ids);
        return Result.success();
    }
}
