package com.shop.controller.shop;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopDeliveryCompany;
import com.shop.service.ShopDeliveryCompanyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "物流公司管理")
@RestController
@RequestMapping("/shop/logistics")
@RequiredArgsConstructor
public class ShopDeliveryCompanyController {

    private final ShopDeliveryCompanyService shopDeliveryCompanyService;

    @GetMapping("/list")
    @ApiOperation(value = "获取物流公司列表")
    public Result<IPage<ShopDeliveryCompany>> list(ShopDeliveryCompany company) {
        return Result.success(shopDeliveryCompanyService.selectPage(company));
    }

    @GetMapping("/options")
    @ApiOperation(value = "获取启用状态的物流公司选项（供表单下拉使用，不分页）")
    public Result<List<ShopDeliveryCompany>> options() {
        return Result.success(shopDeliveryCompanyService.listOptions());
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取物流公司详情")
    public Result<ShopDeliveryCompany> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopDeliveryCompanyService.getById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加物流公司")
    @OperationLogger("添加物流公司")
    @SaCheckPermission("shop:logistics:add")
    public Result<Long> add(@RequestBody ShopDeliveryCompany company) {
        shopDeliveryCompanyService.insert(company);
        // save() 会把自增主键回填到入参实体
        return Result.success(company.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改物流公司")
    @OperationLogger("修改物流公司")
    @SaCheckPermission("shop:logistics:update")
    public Result<Void> edit(@RequestBody ShopDeliveryCompany company) {
        shopDeliveryCompanyService.update(company);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除物流公司")
    @OperationLogger("删除物流公司")
    @SaCheckPermission("shop:logistics:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopDeliveryCompanyService.deleteByIds(ids);
        return Result.success();
    }
}
