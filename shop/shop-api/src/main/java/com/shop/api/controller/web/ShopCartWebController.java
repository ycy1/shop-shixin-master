package com.shop.api.controller.web;

import com.shop.api.service.ShopCartReadService;
import com.shop.api.service.ShopCartWriteService;
import com.shop.common.Result;
import com.shop.controller.BaseWebController;
import com.shop.dto.cart.ShopCartSaveDto;
import com.shop.vo.cart.ShopCartVo;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author xxj
 * @title ShopCartWebController
 * @description 购物车 Web 接口（商城前台：详情页那颗「加入购物车」+ 购物车那一页的数量、勾选、全选、删除）
 */
@Api(tags = "购物车 Web 接口")
@RestController
@RequestMapping("/shop/cart")
@RequiredArgsConstructor
public class ShopCartWebController extends BaseWebController {

    private final ShopCartReadService shopCartReadService;

    private final ShopCartWriteService shopCartWriteService;

    /**
     * 我的购物车：一行是一个规格，一次全给、不分页。
     *
     * 路径：继承 BaseWebController 会被 WebConfig 加上 /api/web 前缀，即 GET /api/web/shop/cart/list。
     * 这一包只有前台一个使用方（后台那套在 /shop/cart/**，压 shop:cart:* 权限、能挑消费者），
     * 也不放 /api/comm —— comm 收的是两端同一个动作，这一页没有后台那一半。
     *
     * 五条都要求登录：SaTokenConfigure 把 /api/** 整体排在登录拦截之外，所以「必须登录」不能指望拦截器，
     * 由服务层第一句 requireLoginUser 当场拒掉。参数里也没有 userId 这一格，能读能改的只有登录 id 名下那些行。
     */
    @GetMapping("/list")
    @ApiOperation(value = "我的购物车（一行一个规格，不分页）")
    public Result<List<ShopCartVo>> list() {
        return Result.success(shopCartReadService.myCart());
    }

    /**
     * 加入购物车：POST /api/web/shop/cart/add，请求体只认 skuId + quantity 两格
     *（复用后台那份 ShopCartSaveDto，但 id 和 userId 在这一颗上都不认 —— 页面上只有「这个规格我加几件」这一个事实）。
     *
     * 车上已经有这一条就在它上面叠数量，有一条删掉的就把那一行复活，都没有才建第一条，所以点第二次不会报错
     *（判据见 ShopCartWriteService#add）。返回不给东西：这一页的画法是先落库再重拉一遍列表，
     * 商品名、单价、规格文本都由列表那条联出来，前端不拼第二份。
     */
    @PostMapping("/add")
    @ApiOperation(value = "加入购物车（同一规格重复点是叠数量）")
    public Result<Void> add(@RequestBody ShopCartSaveDto form) {
        shopCartWriteService.add(form);
        return Result.success();
    }

    /**
     * 改自己名下的某一行：PUT /api/web/shop/cart/update，id 在请求体里（形状同后台那份 update，不走路径参数）。
     * 只认 quantity 和 selected 两格，都是局部更新 —— 不传那格就不动（不像收货地址那一路整条覆盖）。
     * 数量这一格不做库存上限，页面上的步进按列表带回的 availableStock 自己收着，真拦在下单那一刻。
     */
    @PutMapping("/update")
    @ApiOperation(value = "改数量或勾选（局部更新，只认这两格）")
    public Result<Void> update(@RequestBody ShopCartSaveDto form) {
        shopCartWriteService.update(form);
        return Result.success();
    }

    /**
     * 全选 / 取消全选：PUT /api/web/shop/cart/selectAll?selected=1（0 是全不选）。
     * 一次一句 UPDATE 按人刷完名下所有行，不让前端逐行 PUT —— 车上十几条就是十几颗请求。
     */
    @PutMapping("/selectAll")
    @ApiOperation(value = "全选或取消全选自己车里的全部行")
    public Result<Void> selectAll(@RequestParam("selected") Integer selected) {
        shopCartWriteService.selectAll(selected);
        return Result.success();
    }

    /**
     * 删掉某几行：DELETE /api/web/shop/cart/delete/{ids}，ids 是逗号分隔（一行那颗「删除」传一个，
     * 批量删除传一串，形状同后台那一路）。
     * 别人的 / 已删的 / 没这个 id 一律回同一句「购物车记录不存在」，只要有一条不全是自己的就整批不删。
     */
    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除自己的购物车记录（逻辑删除；不在自己名下的一律按不存在处理）")
    public Result<Void> remove(@PathVariable("ids") List<Long> ids) {
        shopCartWriteService.delete(ids);
        return Result.success();
    }
}
