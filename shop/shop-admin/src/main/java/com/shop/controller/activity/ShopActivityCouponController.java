package com.shop.controller.activity;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.dto.activity.ShopActivityCouponGiveDto;
import com.shop.entity.ShopActivityCoupon;
import com.shop.entity.ShopActivityCouponUser;
import com.shop.service.ShopActivityCouponService;
import com.shop.service.ShopActivityCouponUserService;
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
 * 优惠券（营销活动 -> 优惠券）
 *
 * 读接口按项目惯例只做登录校验（Sa-Token 拦截器兜全路径），权限只压在 add/update/delete/give 上；
 * 状态是表单里选的一个下拉、由 update 整行提交，所以没有单独的发布/结束/取消端点。
 *
 * 这一页多一条写入路：「发放」（POST /activity/coupon/give）。它和活动那一页相反 —— 活动的曝光/点击是前台埋点写的、
 * 后台只读，这一页是运营在后台把券发到用户手上、并占掉这张券的发行额度，所以 give 压的是真权限。
 * 用户自己领券那一条前台链路这一期还没接（没有 /api/comm 那一路）；下单核销已经接上了，
 * 在统一下单和取消订单那两路里（shop-api 的 ShopOrderCouponService）。
 */
@Api(tags = "优惠券管理")
@RestController
@RequestMapping("/activity/coupon")
@RequiredArgsConstructor
public class ShopActivityCouponController {

    private final ShopActivityCouponService shopActivityCouponService;

    /** 「发放记录」那颗按钮的明细，读的是另一张表（用户券），所以是第二个 service（同活动那页的 /record/list） */
    private final ShopActivityCouponUserService shopActivityCouponUserService;

    @GetMapping("/list")
    @ApiOperation(value = "获取优惠券列表（联表带出适用门店名和负责的运营昵称；发行总量/已发放/已使用是库里存的额度数，"
            + "可发放量由前端按这两格算）")
    public Result<IPage<ShopActivityCoupon>> list(ShopActivityCoupon coupon) {
        return Result.success(shopActivityCouponService.selectPage(coupon));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取一条优惠券详情（修改弹窗和只读详情弹窗共用，scope_value 已拆成逗号分隔的 scopeIds）")
    public Result<ShopActivityCoupon> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopActivityCouponService.selectCouponById(id));
    }

    /**
     * 这一条走的是另一张表（shop_activity_coupon_user），所以路径挂在 /activity/coupon 下面但由
     * ShopActivityCouponUserService 实现 —— 和活动那页的 /record/list 同一个形状：明细跟着主数据那一页给入口。
     */
    @GetMapping("/record/list")
    @ApiOperation(value = "获取一张券的发放记录（列表页那颗「发放记录」弹窗，按券分页、最新一条在前，只读）")
    public Result<IPage<ShopActivityCouponUser>> recordList(ShopActivityCouponUser query) {
        return Result.success(shopActivityCouponUserService.selectRecordPage(query));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加优惠券：券码不传由系统生成（这张表的券码靠服务层查重，库上还没建唯一键）；"
            + "状态一律落 1 未开始，已发放/已使用那两格表单说不动")
    @OperationLogger("添加优惠券")
    @SaCheckPermission("activity:coupon:add")
    public Result<Long> add(@RequestBody ShopActivityCoupon coupon) {
        shopActivityCouponService.insert(coupon);
        // save() 会把自增主键回填到入参实体
        return Result.success(coupon.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改优惠券：状态在这一次整行提交（1未开始/2发放中/3已结束/4已取消），"
            + "发行总量能改，已发放/已使用那两格改不动（只有「发放」会加已发放）")
    @OperationLogger("修改优惠券")
    @SaCheckPermission("activity:coupon:update")
    public Result<Void> edit(@RequestBody ShopActivityCoupon coupon) {
        shopActivityCouponService.update(coupon);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除优惠券：只删还没发出去过的那几张，发到用户手上的那些行还指着这张模板")
    @OperationLogger("删除优惠券")
    @SaCheckPermission("activity:coupon:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopActivityCouponService.deleteByIds(ids);
        return Result.success();
    }

    @PostMapping("/give")
    @ApiOperation(value = "发放优惠券：给选中的每个用户发 quantity 张（不传按一人一张，面额那几格按模板当场快照，券码按已发放数排号），"
            + "占额度是一条带总量校验的原子 UPDATE（加数 = 人数 × 每人张数），占不到就整笔回滚报「可发放量不够」")
    @OperationLogger("发放优惠券")
    @SaCheckPermission("activity:coupon:give")
    public Result<Integer> give(@RequestBody ShopActivityCouponGiveDto form) {
        return Result.success(shopActivityCouponService.give(form));
    }
}
