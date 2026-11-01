package com.shop.api.controller.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.api.service.ShopAccountWriteService;
import com.shop.api.service.ShopAddressWriteService;
import com.shop.api.service.ShopProfileReadService;
import com.shop.common.Result;
import com.shop.controller.BaseAppController;
import com.shop.dto.address.ShopAddressQueryDto;
import com.shop.dto.address.ShopAddressSaveDto;
import com.shop.dto.browse.ShopBrowseQueryDto;
import com.shop.dto.favorite.ShopFavoriteQueryDto;
import com.shop.dto.order.ShopOrderQueryDto;
import com.shop.entity.ShopActivityBadgeUser;
import com.shop.entity.ShopActivityCouponUser;
import com.shop.entity.ShopActivityPoints;
import com.shop.entity.ShopActivityPointsLog;
import com.shop.entity.ShopActivitySignin;
import com.shop.entity.ShopConsumerWallet;
import com.shop.entity.ShopConsumerWalletLog;
import com.shop.entity.ShopParkUserTicket;
import com.shop.entity.SysUser;
import com.shop.vo.address.ShopAddressVo;
import com.shop.vo.browse.ShopBrowseVo;
import com.shop.vo.favorite.ShopFavoriteVo;
import com.shop.vo.order.ShopOrderVo;
import com.shop.vo.user.SysUserProfileVo;
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
 * @author ycy
 * @title ShopProfileAppController
 * @description 个人中心 App 端接口（小程序 / App 前台看自己名下那几块：账号、订单、收藏、足迹、券、徽章、积分、钱包）
 */
@Api(tags = "个人中心 App 端接口")
@RestController
@RequestMapping("/shop/profile")
@RequiredArgsConstructor
public class ShopProfileAppController extends BaseAppController {

    private final ShopProfileReadService shopProfileReadService;

    private final ShopAccountWriteService shopAccountWriteService;

    private final ShopAddressWriteService shopAddressWriteService;

    /**
     * 这一份大部分是读，而且每条都是把 Web 端那一条转手过来（同一个 shopProfileReadService 的同一个方法）。
     *
     * 为什么不直接让 App 打 /api/web：分包口径是「App 的读接口一律在 /api/app 下」，两端各一份路径、
     * 判据只有一处 —— 判据分叉只会变成「小程序能看见别人的单」这类查不动的账。
     * 也不放 /api/comm：comm 收的是两端同一个**动作**，这一页只有前台一个使用方。
     * 写口子有下面这几条：PUT /account（App 的「修改基础信息」页）、地址那三条增删改（App 的「地址管理」页），
     * 改密码、收藏 toggle 那几条 App 页面还没铺，铺到哪条再补哪条，不提前开一堆没人调的口子。
     *
     * SaTokenConfigure 把 /api/** 整体排在登录拦截之外，所以「必须登录」不能指望拦截器 ——
     * 由服务层第一句 requireLoginUser 当场拒掉；参数里就算传了 userId 也会被覆盖成登录 id。
     * 分页走 pageNum/pageSize 查询串（PageUtil 直接从请求里取）。
     */
    @GetMapping("/account")
    @ApiOperation(value = "我的基础信息（账号那一行 + 角色；不含密码，要登录）")
    public Result<SysUserProfileVo> account() {
        return Result.success(shopProfileReadService.account());
    }

    /**
     * 修改自己的基本信息：App「个人信息 → 基础信息」那颗「修改」。
     *
     * 路径：PUT /api/app/shop/profile/account，和上面那条读的是同一路径换动词（形状同 Web 端那一对）。
     * 判据一条都不在这另写：请求体虽然收 SysUser，服务端只搬头像/昵称/手机号/邮箱/性别/所在地区/详细地址/
     * 经纬度/个性签名那几格，id 钉成登录 id —— 账号、账号类型、状态、角色、密码传什么都不认（见
     * ShopAccountWriteServiceImpl#updateProfile，抄的是后台 /sys/user/myProfile 那一份）。
     * 头像那一格送的是已经传好的图片 URL（图走现成的 POST /api/file/uploadImage，这一条不收文件）。
     */
    @PutMapping("/account")
    @ApiOperation(value = "修改自己的基本信息（只认那九格；id 由服务端钉成登录 id）")
    public Result<Void> updateAccount(@RequestBody SysUser form) {
        shopAccountWriteService.updateProfile(form);
        return Result.success();
    }

    /**
     * 我的订单：一行一张订单，不含商品商品明细行（明细在下一条）。
     *
     * 路径：GET /api/app/shop/profile/order/list。
     */
    @GetMapping("/order/list")
    @ApiOperation(value = "我的订单（只看自己名下的，一行一张订单）")
    public Result<IPage<ShopOrderVo>> orderList(ShopOrderQueryDto query) {
        return Result.success(shopProfileReadService.orderPage(query));
    }

    /**
     * 订单详情：主表字段 + 名下全部商品明细行。
     *
     * 路径：GET /api/app/shop/profile/order/{id}。别人的单、被人删过的单、根本没这个 id ——
     * 都回同一句「订单不存在」，不为它们分档，否则拿错误信息就能试探出这一单挂在谁名下（口径同 Web 那一条）。
     */
    @GetMapping("/order/{id}")
    @ApiOperation(value = "我的订单详情（主表 + 明细行；不在自己名下的一律按不存在处理）")
    public Result<ShopOrderVo> orderDetail(@PathVariable("id") Long id) {
        return Result.success(shopProfileReadService.orderDetail(id));
    }

    /**
     * 我的收藏：targetType 1 商品 2 门店 3 品牌，被收藏那一样叫什么由 SQL 联出来。
     *
     * 路径：GET /api/app/shop/profile/favorite/list，带 targetType / targetId 再钉 pageSize=1 就是「这一个我收藏过没」。
     */
    @GetMapping("/favorite/list")
    @ApiOperation(value = "我的收藏（只看自己名下的，商品/门店/品牌共用一张表）")
    public Result<IPage<ShopFavoriteVo>> favoriteList(ShopFavoriteQueryDto query) {
        return Result.success(shopProfileReadService.favoritePage(query));
    }

    /**
     * 浏览足迹：一条一个商品，browseTime 是最近一次（第一次记录在 createTime 那一格）。
     *
     * 路径：GET /api/app/shop/profile/browse/list，按最近浏览时间倒序。
     */
    @GetMapping("/browse/list")
    @ApiOperation(value = "我的浏览足迹（只看自己名下的，按最近浏览时间倒序）")
    public Result<IPage<ShopBrowseVo>> browseList(ShopBrowseQueryDto query) {
        return Result.success(shopProfileReadService.browsePage(query));
    }

    /**
     * 我的签到记录：「我的」页头部那颗签到按钮要知道今天签过没有、连签几天。
     *
     * 路径：GET /api/app/shop/profile/signin/list?month=yyyy-MM（留空按当月）。一次给一个月签过的几天、不分页
     *（最多 31 行）。没签的日子不给行，由前端按今天/这一个月自己数。
     * 这一条只有读；两个写口子（签今天、本人补签）不在这里 —— 签到要「落一行 + 给积分 + 记流水」三件一个事务，
     * 那份写入 service 前台后台共用一套，所以留在 POST /api/comm/activity/signin（两端同一个动作）。
     */
    @GetMapping("/signin/list")
    @ApiOperation(value = "我的签到记录（某个月签过的那几天，month 传 yyyy-MM，留空按当月）")
    public Result<List<ShopActivitySignin>> signinList(@RequestParam(required = false) String month) {
        return Result.success(shopProfileReadService.signinMonth(month));
    }

    /**
     * 我的优惠券：一行一张券，读用户券那张表（shop_activity_coupon_user）。
     *
     * 路径：GET /api/app/shop/profile/coupon/list?status=（0未使用 1已使用 2已过期 3已作废，留空不限）。
     * 面额/门槛/上限那几格读的是发放时定格的快照，所以后台改了模板也不会串到已经发到手上的券上。
     */
    @GetMapping("/coupon/list")
    @ApiOperation(value = "我的优惠券（只看自己名下的，一行一张券）")
    public Result<IPage<ShopActivityCouponUser>> couponList(ShopActivityCouponUser query) {
        return Result.success(shopProfileReadService.couponPage(query));
    }

    /**
     * 我的票种：一行一张主题公园的票，读用户票那张表（shop_trendy_park_user_ticket）。
     *
     * 路径：GET /api/app/shop/profile/park/ticket/list?status=（0未使用 1已核销 2已过期 3已退款，留空不限）
     * &beginVisitDate=&endVisitDate=（游玩日期区间，含两端，yyyy-MM-dd）。
     * 票名/类型/票价读的是出票那一刻定格的快照，所以后台改了票种也不会串到已经发到手上的票上；
     * qr_img 是票面二维码那张图的地址（闸机/客服扫的就是这一串票码）。
     *
     * userId 前端不传、传了也不认：服务层第一句覆盖成登录 id（口径同上面那条优惠券）。
     */
    @GetMapping("/park/ticket/list")
    @ApiOperation(value = "我的票种（只看自己名下的，一行一张票）")
    public Result<IPage<ShopParkUserTicket>> parkTicketList(ShopParkUserTicket query) {
        return Result.success(shopProfileReadService.parkTicketPage(query));
    }

    /**
     * 我的徽章：一行一枚，读用户徽章那张表（shop_activity_badge_user）。
     *
     * 路径：GET /api/app/shop/profile/badge/list?source=&status=（两档都可选筛）。
     * 名称/文字/图标三格读的是发放那一刻的快照；过期那一档本期是读的时候按 endTime 现判的
     *（没有定时任务翻 status），所以前端不能只信 status（口径同 Web 那一条）。
     */
    @GetMapping("/badge/list")
    @ApiOperation(value = "我的徽章（只看自己名下的，一行一枚，最新拿到的一枚在前）")
    public Result<IPage<ShopActivityBadgeUser>> badgeList(ShopActivityBadgeUser query) {
        return Result.success(shopProfileReadService.badgePage(query));
    }

    /**
     * 我的积分账户：那五个数读的都是库里存的那几格，不做现算。
     *
     * 路径：GET /api/app/shop/profile/points。一分都没获得过就没有这一行账，这一条回 null
     * 由页面说成「还没有积分账户」，不报错误。
     */
    @GetMapping("/points")
    @ApiOperation(value = "我的积分账户（库里存的那五个数；还没建账回 null）")
    public Result<ShopActivityPoints> points() {
        return Result.success(shopProfileReadService.pointsAccount());
    }

    /**
     * 我的积分流水：一行一笔变动、最新一笔在前。
     *
     * 路径：GET /api/app/shop/profile/points/log/list?bizType=（1签到 … 10管理员调整，留空不限）。
     * 只有读：这一张表是凭证，由签到/下单/退款那几路和账户行同一个事务里带出来。
     */
    @GetMapping("/points/log/list")
    @ApiOperation(value = "我的积分流水（只看自己名下的，最新一笔在前）")
    public Result<IPage<ShopActivityPointsLog>> pointsLogList(ShopActivityPointsLog query) {
        return Result.success(shopProfileReadService.pointsLogPage(query));
    }

    /**
     * 我的钱包账户：那几个数同样是库里存的、不做现算；一分钱都没进过账时回 null。
     *
     * 路径：GET /api/app/shop/profile/wallet。
     */
    @GetMapping("/wallet")
    @ApiOperation(value = "我的钱包账户（库里存的那几个数；还没建账回 null）")
    public Result<ShopConsumerWallet> wallet() {
        return Result.success(shopProfileReadService.walletAccount());
    }

    /**
     * 我的钱包流水：一行一笔余额变动、最新一笔在前，只有读。
     *
     * 路径：GET /api/app/shop/profile/wallet/log/list?bizType=（1充值 2消费 3退款 …，留空不限）。
     */
    @GetMapping("/wallet/log/list")
    @ApiOperation(value = "我的钱包流水（只看自己名下的，最新一笔在前）")
    public Result<IPage<ShopConsumerWalletLog>> walletLogList(ShopConsumerWalletLog query) {
        return Result.success(shopProfileReadService.walletLogPage(query));
    }

    /**
     * 我的收货地址：App「地址管理」那一页和结算页挑地址都读这一条。
     *
     * 路径：GET /api/app/shop/profile/address/list，转手的是 Web 端同一条（同一个 addressPage）。
     * userId 不认：服务层第一句覆盖成登录 id；分页吃 pageNum/pageSize 查询串，结算页只要前 50 条。
     * 「这一条的坐标用来就近分仓、算配送费」那套判据不在这里，全在下单那一路（ShopOrderSupport / 配送费规则）。
     */
    @GetMapping("/address/list")
    @ApiOperation(value = "我的收货地址（只看自己名下的，默认那条在前）")
    public Result<IPage<ShopAddressVo>> addressList(ShopAddressQueryDto query) {
        return Result.success(shopProfileReadService.addressPage(query));
    }

    /**
     * 新增收货地址：App「地址管理」页那颗「新增」。
     *
     * 路径：POST /api/app/shop/profile/address，形状、判据与 Web 那一条完全一致（同一个 ShopAddressWriteService#create）：
     * userId 被覆盖成登录 id，「这个账号的第一条地址直接当默认」「设默认先清别的默认」都在服务层。
     * areaZh 由前端按选中的行政区划代码解析后一起传（同后台、Web 那两份），邮编那格前台表单没铺。
     */
    @PostMapping("/address")
    @ApiOperation(value = "新增收货地址（只往自己名下加；前端存好后整块重拉这一列）")
    public Result<ShopAddressVo> addAddress(@RequestBody ShopAddressSaveDto form) {
        return Result.success(shopAddressWriteService.create(form));
    }

    /**
     * 修改收货地址：列表每行那颗「编辑」，整条覆盖式提交。
     *
     * 路径：PUT /api/app/shop/profile/address（id 在请求体里，不走路径参数，同 Web 那一条）。
     * 归属只认库里那一行的 user_id：别人的、已删的、没这个 id 一律回同一句「收货地址不存在」；
     * 地区 / 标签 / 经纬度这几格不传就是清成空（实体上钉的 IGNORED），所以前端每次都要把整张表单交上来。
     */
    @PutMapping("/address")
    @ApiOperation(value = "修改自己的收货地址（整条覆盖；不在自己名下的一律按不存在处理）")
    public Result<ShopAddressVo> updateAddress(@RequestBody ShopAddressSaveDto form) {
        return Result.success(shopAddressWriteService.update(form));
    }

    /**
     * 删自己名下的一条：列表每行那颗「删除」，一条一个 id（不像后台那一路收 id 数组做批量）。
     *
     * 路径：DELETE /api/app/shop/profile/address/{id}。库里是逻辑删除；删掉的正好是默认那条不自动补选，
     * 所以结算页删完地址要重新挑一颗 —— 这两条判据同 Web 那一条，前端删完整块重拉。
     */
    @DeleteMapping("/address/{id}")
    @ApiOperation(value = "删除自己的收货地址（逻辑删除；不在自己名下的一律按不存在处理）")
    public Result<Void> deleteAddress(@PathVariable("id") Long id) {
        shopAddressWriteService.delete(id);
        return Result.success();
    }
}
