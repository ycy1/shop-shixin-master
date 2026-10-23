package com.shop.controller.erp;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.entity.ShopSupplierProduct;
import com.shop.service.ShopSupplierProductService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "供应商商品管理")
@RestController
@RequestMapping("/erp/supplierProduct")
@RequiredArgsConstructor
public class ShopSupplierProductController {

    private final ShopSupplierProductService shopSupplierProductService;

    @GetMapping("/list")
    @ApiOperation(value = "获取供货关系列表（供应商账号只看自己名下那几家供应商的）")
    public Result<IPage<ShopSupplierProduct>> list(ShopSupplierProduct query) {
        return Result.success(shopSupplierProductService.selectPage(query));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取供货关系详情（额外按 sku_id 带出规格文本，给修改弹窗回显）")
    public Result<ShopSupplierProduct> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopSupplierProductService.selectRelationById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加供货关系（商品名称/分类由服务端按商品回填）")
    @OperationLogger("添加供应商商品")
    @SaCheckPermission("erp:supplierProduct:add")
    public Result<Long> add(@RequestBody ShopSupplierProduct relation) {
        shopSupplierProductService.insert(relation);
        // save() 会把自增主键回填到入参实体
        return Result.success(relation.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改供货关系（供应商和规格这两列定死了一条关系，改不了，只改供货参数）")
    @OperationLogger("修改供应商商品")
    @SaCheckPermission("erp:supplierProduct:update")
    public Result<Void> edit(@RequestBody ShopSupplierProduct relation) {
        shopSupplierProductService.update(relation);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除供货关系")
    @OperationLogger("删除供应商商品")
    @SaCheckPermission("erp:supplierProduct:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopSupplierProductService.deleteByIds(ids);
        return Result.success();
    }
}
