package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopStoreInvestment;
import com.shop.service.ShopStoreInvestmentService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 门店招商（门店管理 -> 门店招商）
 *
 * 读接口按项目惯例只做登录校验（Sa-Token 拦截器兜全路径），权限只压在 add/update/delete 上；
 * 状态是表单里选的一个下拉、由 update 整行提交，所以没有单独的审核端点。
 */
@Api(tags = "门店招商")
@RestController
@RequestMapping("/shop/investment")
@RequiredArgsConstructor
public class ShopStoreInvestmentController {

    private final ShopStoreInvestmentService shopStoreInvestmentService;

    @GetMapping("/list")
    @ApiOperation(value = "获取招商线索列表（平台账号看全部，不按负责专员收窄）")
    public Result<IPage<ShopStoreInvestment>> list(ShopStoreInvestment investment) {
        return Result.success(shopStoreInvestmentService.selectPage(investment));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取一条招商详情（修改弹窗和只读详情弹窗共用）")
    public Result<ShopStoreInvestment> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopStoreInvestmentService.selectInvestmentById(id));
    }

    @GetMapping("/byStore/{storeId}")
    @ApiOperation(value = "按门店取它来源那条招商线索（门店列表的「招商信息」弹窗）；这家店不是招商来的返回 null")
    public Result<ShopStoreInvestment> getByStore(@PathVariable("storeId") Long storeId) {
        return Result.success(shopStoreInvestmentService.selectByStoreId(storeId));
    }

    @GetMapping("/options")
    @ApiOperation(value = "招商线索候选（门店表单那格「招商信息」下拉，不分页）：只给状态 0 待审核/1 已签约、"
            + "且还没被任何门店占用的那几条；传 storeId 时把这家门店自己已经绑的那条也放回来，修改弹窗才回显得出来")
    public Result<List<ShopStoreInvestment>> options(@RequestParam(required = false) Long storeId) {
        return Result.success(shopStoreInvestmentService.listOptions(storeId));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加招商线索：编号由系统生成（INVEST_+时间戳），意向类型/线索来源/状态不传时补库里的默认值")
    @OperationLogger("添加招商线索")
    @SaCheckPermission("shop:investment:add")
    public Result<Long> add(@RequestBody ShopStoreInvestment investment) {
        shopStoreInvestmentService.insert(investment);
        // save() 会把自增主键回填到入参实体
        return Result.success(investment.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改招商线索：状态在这一次整行提交（0待审核/1已签约/2已取消/3已失效），"
            + "签约时间/过期时间都是表单里可直接改的字段")
    @OperationLogger("修改招商线索")
    @SaCheckPermission("shop:investment:update")
    public Result<Void> edit(@RequestBody ShopStoreInvestment investment) {
        shopStoreInvestmentService.update(investment);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除招商线索：只有 0 待审核 的能删，已经签约/取消/失效的留档不动")
    @OperationLogger("删除招商线索")
    @SaCheckPermission("shop:investment:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopStoreInvestmentService.deleteByIds(ids);
        return Result.success();
    }
}
