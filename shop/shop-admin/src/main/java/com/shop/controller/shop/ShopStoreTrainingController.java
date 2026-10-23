package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopStoreTraining;
import com.shop.entity.ShopStoreTrainingMember;
import com.shop.service.ShopStoreTrainingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 门店培训（门店管理 -> 门店培训）
 *
 * 读接口按项目惯例只做登录校验（Sa-Token 拦截器兜全路径），权限只压在 add/update/delete 上；
 * 状态是表单里选的一个下拉、由 update 整行提交，所以没有单独的发布/结束端点。
 * 参训人员这一侧后台只读（/members 只是查）：报名和签到都走门店那一端的
 * POST /api/comm/store/training/sign 与 /training/attend/{trainingId}，
 * 两端同一个动作必须落在同一条写入路径上。
 */
@Api(tags = "门店培训")
@RestController
@RequestMapping("/shop/training")
@RequiredArgsConstructor
public class ShopStoreTrainingController {

    private final ShopStoreTrainingService shopStoreTrainingService;

    @GetMapping("/list")
    @ApiOperation(value = "获取培训列表（联表带出门店名/讲师名，已报名人数按参训人员表现算）")
    public Result<IPage<ShopStoreTraining>> list(ShopStoreTraining training) {
        return Result.success(shopStoreTrainingService.selectPage(training));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取一条培训详情（修改弹窗和只读详情弹窗共用）")
    public Result<ShopStoreTraining> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopStoreTrainingService.selectTrainingById(id));
    }

    @GetMapping("/members/{trainingId}")
    @ApiOperation(value = "按培训取参训人员列表（列表页那颗「培训人员」弹窗），按登记先后排")
    public Result<List<ShopStoreTrainingMember>> members(@PathVariable("trainingId") Long trainingId) {
        return Result.success(shopStoreTrainingService.selectMembers(trainingId));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加培训：编号不传由系统生成（TRAIN_+时间戳），状态一律落 0 待发布，"
            + "培训类型/培训方式/已报名人数不传时补库里的默认值")
    @OperationLogger("添加培训")
    @SaCheckPermission("shop:training:add")
    public Result<Long> add(@RequestBody ShopStoreTraining training) {
        shopStoreTrainingService.insert(training);
        // save() 会把自增主键回填到入参实体
        return Result.success(training.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改培训：状态在这一次整行提交（0待发布/1报名中/2已截止/3进行中/4已结束/5已取消），"
            + "第一次进 1 和 4 时服务端分别盖发布时间/结束时间，之后不覆盖")
    @OperationLogger("修改培训")
    @SaCheckPermission("shop:training:update")
    public Result<Void> edit(@RequestBody ShopStoreTraining training) {
        shopStoreTrainingService.update(training);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除培训：只有 0 待发布 的能删，发布过的可能已有报名记录，留档不动")
    @OperationLogger("删除培训")
    @SaCheckPermission("shop:training:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopStoreTrainingService.deleteByIds(ids);
        return Result.success();
    }
}
