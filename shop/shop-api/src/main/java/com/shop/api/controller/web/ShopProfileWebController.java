package com.shop.api.controller.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.api.service.ShopAccountWriteService;
import com.shop.api.service.ShopAddressWriteService;
import com.shop.api.service.ShopBadgeWriteService;
import com.shop.api.service.ShopFavoriteWriteService;
import com.shop.api.service.ShopProfileReadService;
import com.shop.common.Result;
import com.shop.controller.BaseWebController;
import com.shop.dto.address.ShopAddressQueryDto;
import com.shop.dto.address.ShopAddressSaveDto;
import com.shop.dto.browse.ShopBrowseQueryDto;
import com.shop.dto.favorite.ShopFavoriteQueryDto;
import com.shop.dto.favorite.ShopFavoriteSaveDto;
import com.shop.dto.order.ShopOrderQueryDto;
import com.shop.dto.user.UpdatePwdDTO;
import com.shop.entity.ShopActivityBadgeUser;
import com.shop.entity.ShopActivityCouponUser;
import com.shop.entity.ShopActivityPoints;
import com.shop.entity.ShopActivityPointsLog;
import com.shop.entity.ShopActivitySignin;
import com.shop.entity.ShopConsumerWallet;
import com.shop.entity.ShopConsumerWalletLog;
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
 * @author xxj
 * @title ShopProfileWebController
 * @description 个人中心 Web 接口（商城前台看自己名下的账号信息/优惠券/徽章/积分/钱包/订单/地址/收藏/足迹/签到，外加改自己的密码和基本资料那两颗）
 */
@Api(tags = "个人中心 Web 接口")
@RestController
@RequestMapping("/shop/profile")
@RequiredArgsConstructor
public class ShopProfileWebController extends BaseWebController {

    private final ShopProfileReadService shopProfileReadService;

    /**
     * 这一包里的写入：收货地址的增/改/删、收藏的删与「收藏」按钮那一颗 toggle、
     * 自己这一格的密码和基本信息那八格、名下一枚徽章的「是否展示」，
     * 判据都在那几份 service 里，这里只转一手。
     */
    private final ShopAddressWriteService shopAddressWriteService;

    private final ShopFavoriteWriteService shopFavoriteWriteService;

    private final ShopAccountWriteService shopAccountWriteService;

    private final ShopBadgeWriteService shopBadgeWriteService;

    /**
     * 基础信息：sys_user 上自己那一行 + 名下角色，「个人中心」菜单第一块要的就是这一条。
     *
     * 路径：GET /api/web/shop/profile/account（一次接口、不做分页 —— 一张账号一行）。
     * 没有 id 参数，读的就是登录 id 那一行：前台能点名别人，就等于把别人的手机号邮箱摊开任人查。
     * 返回里没有 password 那一格（服务层读出来先清掉，实体上是 NON_NULL，所以它不进 JSON）。
     */
    @GetMapping("/account")
    @ApiOperation(value = "我的基础信息（账号那一行 + 角色；不含密码）")
    public Result<SysUserProfileVo> account() {
        return Result.success(shopProfileReadService.account());
    }

    /**
     * 修改密码：基础信息那一块上那颗「修改密码」。
     *
     * 路径：PUT /api/web/shop/profile/password，请求体只认 oldPassword + newPassword 两格
     *（复用后台那份 UpdatePwdDTO；改的是 token 背后那一个账号，参数里根本没有 userId 这一格）。
     * 旧密码在服务端比对，对不上回「旧密码错误」；这一条和后台那一路的分别是去掉了 ADMIN 替别人改那一档。
     */
    @PutMapping("/password")
    @ApiOperation(value = "修改自己的密码（要带旧密码）")
    public Result<Void> updatePassword(@RequestBody UpdatePwdDTO form) {
        shopAccountWriteService.updatePassword(form);
        return Result.success();
    }

    /**
     * 修改基本信息：基础信息那一块上那颗「修改」（判据抄后台 /sys/user/myProfile，那条不在 /api/** 下、前台够不着）。
     *
     * 路径：PUT /api/web/shop/profile/account，和上面那条读的是同一路径换动词（形状同收货地址那一对）。
     * 请求体收的是 SysUser，但服务端只搬头像/昵称/手机号/邮箱/性别/所在地区/详细地址/经纬度/个性签名那几格，
     * id 钉成登录 id —— 所以账号、账号类型、状态、角色、密码传什么都不认，这一颗也不用勾权限。
     * 头像那一格送的是已经传好的图片 URL（图走的是现成的 POST /api/file/uploadImage，这一条不收文件）。
     * 全局 field-strategy=not_empty：这几格改得了、清不掉（和后台那一格同一个表现）。
     */
    @PutMapping("/account")
    @ApiOperation(value = "修改自己的基本信息（只认那八格；id 由服务端钉成登录 id）")
    public Result<Void> updateAccount(@RequestBody SysUser form) {
        shopAccountWriteService.updateProfile(form);
        return Result.success();
    }

    /**
     * 我的订单：一行一张订单，不含商品明细行。
     *
     * 路径：继承 BaseWebController 会被 WebMvcConfig 加上 /api/web 前缀，即 GET /api/web/shop/profile/order/list。
     * 这几条都不放 /api/comm：comm 收的是「两端同一个动作」，这一页只有前台一个使用方。
     * 也不直接给前台调后台那套 /shop/orderItem/** —— 那侧是按账号类型收窄的（平台/门店/ERP 看的是全部），
     * 前台没有身份这一说，只能按登录 id 收窄。
     *
     * SaTokenConfigure 把 /api/** 整体排在登录拦截之外，所以「必须登录」这条不能指望拦截器，
     * 由服务层第一句 requireLoginUser 当场拒掉；请求参数里就算传了 userId 也会被覆盖成登录 id。
     * 分页走 pageNum/pageSize 查询串，和商品列表、后台列表同一套（PageUtil 直接从请求里取）。
     */
    @GetMapping("/order/list")
    @ApiOperation(value = "我的订单（只看自己名下的，一行一张订单）")
    public Result<IPage<ShopOrderVo>> orderList(ShopOrderQueryDto query) {
        return Result.success(shopProfileReadService.orderPage(query));
    }

    /**
     * 订单详情：主表字段 + 名下全部商品明细行，列表上那颗「详情」点开的就是这一条。
     *
     * 路径：GET /api/web/shop/profile/order/{id}。
     * 别人的单、被人删过的单、根本没这个 id —— 都回同一句「订单不存在」，
     * 不为它们分档，否则调用方拿错误信息就能试探出这一单挂在谁名下（口径同商品详情）。
     */
    @GetMapping("/order/{id}")
    @ApiOperation(value = "我的订单详情（主表 + 明细行；不在自己名下的一律按不存在处理）")
    public Result<ShopOrderVo> orderDetail(@PathVariable("id") Long id) {
        return Result.success(shopProfileReadService.orderDetail(id));
    }

    /**
     * 收货地址：默认地址是 isDefault=1，同一账号下互斥由服务层保证
     */
    @GetMapping("/address/list")
    @ApiOperation(value = "我的收货地址（只看自己名下的）")
    public Result<IPage<ShopAddressVo>> addressList(ShopAddressQueryDto query) {
        return Result.success(shopProfileReadService.addressPage(query));
    }

    /**
     * 新增收货地址：前台「添加地址」那一颗。
     *
     * 路径：POST /api/web/shop/profile/address。
     * 只有前台一个使用方（后台那套写入口在 /shop/address/**，压 shop:address:* 权限、能在表单里挑消费者），
     * 所以不放 /api/comm —— comm 收的是两端同一个动作，这一条两边要收的字段根本不一样。
     *
     * 参数里那个 userId 不认：服务层第一句就把它覆盖成登录 id，
     * 「这个账号的第一条地址直接当默认」「设默认先清别的默认」两条判据也都在服务层，
     * 前端只管把表单上的 isDefault 传过来。改和删那两路在下面（PUT 同一条路径、DELETE 带 id）。
     */
    @PostMapping("/address")
    @ApiOperation(value = "新增收货地址（只往自己名下加；前端存好后整块重拉这一列）")
    public Result<ShopAddressVo> addAddress(@RequestBody ShopAddressSaveDto form) {
        return Result.success(shopAddressWriteService.create(form));
    }

    /**
     * 修改收货地址：列表上那颗「编辑」点开的还是同一个弹窗，改完 PUT 回来。
     *
     * 路径：PUT /api/web/shop/profile/address（id 在请求体里，形状同后台那份 update，不走路径参数）。
     * 归属只认库里那一行的 user_id：别人的、已删的、没这个 id 一律回同一句「收货地址不存在」，
     * 参数里那个 userId 不认（挪不到别人名下），也点不到别人的地址簿。
     * 整条覆盖式提交：area_code / area_zh / tag / 邮编 / 经纬度这几格不传就是清成空（实体上钉的 IGNORED）。
     */
    @PutMapping("/address")
    @ApiOperation(value = "修改自己的收货地址（整条覆盖；不在自己名下的一律按不存在处理）")
    public Result<ShopAddressVo> updateAddress(@RequestBody ShopAddressSaveDto form) {
        return Result.success(shopAddressWriteService.update(form));
    }

    /**
     * 删自己名下的一条：列表每行那颗「删除」。
     *
     * 路径：DELETE /api/web/shop/profile/address/{id}（一条一个 id，不像后台那一路收 id 数组做批量）。
     * 归属判据同修改那一句 —— 别人的、已删的、没这个 id 都回同一句「收货地址不存在」；
     * 库里是逻辑删除（行还在，只是 is_deleted=1），删掉的正好是默认那条不自动补选，
     * 下单时没默认地址就让用户自己挑（这两条都同后台那份 deleteByIds）。
     */
    @DeleteMapping("/address/{id}")
    @ApiOperation(value = "删除自己的收货地址（逻辑删除；不在自己名下的一律按不存在处理）")
    public Result<Void> deleteAddress(@PathVariable("id") Long id) {
        shopAddressWriteService.delete(id);
        return Result.success();
    }

    /** 收藏：targetType 1 商品 2 门店 3 品牌，被收藏那一样叫什么由 SQL 联出来。
     *  详情页右侧那颗收藏按钮问「这一个我收藏过没」也走这一条，带 targetType + targetId 再钉 pageSize=1，
     *  userId 照样被服务层覆盖成登录 id，点不到别人的收藏。 */
    @GetMapping("/favorite/list")
    @ApiOperation(value = "我的收藏（只看自己名下的，商品/门店/品牌共用一张表）")
    public Result<IPage<ShopFavoriteVo>> favoriteList(ShopFavoriteQueryDto query) {
        return Result.success(shopProfileReadService.favoritePage(query));
    }

    /**
     * 删自己名下的一条收藏：「我的收藏」列表每行那颗「删除」。
     *
     * 路径：DELETE /api/web/shop/profile/favorite/{id}（一条一个 id，不像后台那一路收 id 数组做批量）。
     * 归属只认库里那一行的 user_id：别人的、已删的、没这个 id 一律回同一句「收藏记录不存在」，
     * 库里是逻辑删除（行还在，只是 is_deleted=1）。删这一行不动别的行，所以前端把这一行就地摘掉就行，
     * 不像收货地址那样要整块重拉（那一路要重拉是因为默认互斥会改到别的行）。
     */
    @DeleteMapping("/favorite/{id}")
    @ApiOperation(value = "删除自己的收藏（逻辑删除；不在自己名下的一律按不存在处理）")
    public Result<Void> deleteFavorite(@PathVariable("id") Long id) {
        shopFavoriteWriteService.delete(id);
        return Result.success();
    }

    /**
     * 收藏 / 取消收藏：商品详情、门店详情右侧那颗按钮，一颗粒按钮管两个方向。
     *
     * 路径：POST /api/web/shop/profile/favorite/toggle，请求体只认 targetType + targetId 两格
     *（复用后台那份 ShopFavoriteSaveDto，但 id、userId、favoriteTime 在这一颗上都不认 —— 页面只有「这一个」这一个事实，
     * 收藏行的 id 是列表那一路才拿得到的；归属钉成登录 id；收藏时间就是点按钮这一刻，没有补录的入口）。
     * 返回点完之后的状态：true 已收藏、false 已取消，按钮画实心还是空心直接看这一格，不用再回读一遍列表。
     */
    @PostMapping("/favorite/toggle")
    @ApiOperation(value = "收藏或取消收藏自己名下的一个对象（返回点完之后的状态）")
    public Result<Boolean> toggleFavorite(@RequestBody ShopFavoriteSaveDto form) {
        return Result.success(shopFavoriteWriteService.toggle(form.getTargetType(), form.getTargetId()));
    }

    /** 足迹：一条一个商品，浏览时间是最近一次（第一次记录在 create_time 那一格） */
    @GetMapping("/browse/list")
    @ApiOperation(value = "我的浏览足迹（只看自己名下的，按最近浏览时间倒序）")
    public Result<IPage<ShopBrowseVo>> browseList(ShopBrowseQueryDto query) {
        return Result.success(shopProfileReadService.browsePage(query));
    }

    /**
     * 签到记录：日历弹窗一个月看一次，一次把那个月签过的那几天全带回去（最多 31 行，所以不分页）。
     *
     * 路径：GET /api/web/shop/profile/signin/list?month=yyyy-MM（留空按当月）。
     * 这一条只有读，参数里也没有 user_id 那一格 —— 日历画的就是登录者自己那几行。
     * 两个写入口（签今天、补签过去某天）不在这份里，它们在 ShopActivityCommonController
     *（POST /api/comm/activity/signin、/api/comm/activity/signin/makeup，本人那一条同样不认参数点名的 userId）：
     * 签到要「落一行 + 给积分 + 记流水」三件一个事务，那份写入 service 前台后台共用一套，不在这再开第二个口径。
     *
     * 没签的日子接口不给行，由前端画成空格子；能不能补那句也在前端先收一次按钮，服务端再拒一次。
     */
    @GetMapping("/signin/list")
    @ApiOperation(value = "我的签到记录（某个月签过的那几天，month 传 yyyy-MM，留空按当月）")
    public Result<List<ShopActivitySignin>> signinList(@RequestParam(required = false) String month) {
        return Result.success(shopProfileReadService.signinMonth(month));
    }

    /**
     * 我的优惠券：「个人信息」那一块下面「优惠券」那个 tab，一行一张券。
     *
     * 路径：GET /api/web/shop/profile/coupon/list?status=（状态可选筛，0未使用 1已使用 2已过期 3已作废）。
     * 读的是用户券那张表（shop_activity_coupon_user），面额/门槛/上限那几格读的是发放时定格的快照，
     * 所以后台改了模板也不会串到已经发到手上的券上（口径同后台优惠券那页的「发放记录」，那一条 SQL 就在这儿复用）。
     * userId 这一格传什么都不认：服务层第一句覆盖成登录 id —— 能点名别人就等于看别人的券包。
     */
    @GetMapping("/coupon/list")
    @ApiOperation(value = "我的优惠券（只看自己名下的，一行一张券）")
    public Result<IPage<ShopActivityCouponUser>> couponList(ShopActivityCouponUser query) {
        return Result.success(shopProfileReadService.couponPage(query));
    }

    /**
     * 我的徽章：「个人信息」那一块下面「徽章」那个 tab，一行一枚。
     *
     * 路径：GET /api/web/shop/profile/badge/list?source=&status=（两档都可选筛：
     * source 1系统发放 2活动获得 3消费达标 4手动发放 5门店发放，status 1有效 0失效 2已过期）。
     * 读的是用户徽章那张表（shop_activity_badge_user），SQL 是后台「徽章管理 → 获取记录」那一条，
     * 名称/文字/图标三格读的是发放那一刻的快照 —— 后台改了徽章也不会串到已经发出去的那一枚上。
     * 过期那一句在本期是读的时候按 end_time 现判的（没有定时任务翻 status），所以前端不能只信 status。
     * userId 这一格传什么都不认：服务层第一句覆盖成登录 id —— 能点名别人就等于看别人拿了哪些徽章。
     */
    @GetMapping("/badge/list")
    @ApiOperation(value = "我的徽章（只看自己名下的，一行一枚，最新拿到的一枚在前）")
    public Result<IPage<ShopActivityBadgeUser>> badgeList(ShopActivityBadgeUser query) {
        return Result.success(shopProfileReadService.badgePage(query));
    }

    /**
     * 改一枚徽章的「是否展示」：「个人信息 → 徽章」每一行那颗「展示 / 收起」。
     *
     * 路径：PUT /api/web/shop/profile/badge/show，请求体只认 id + isShow 两格（复用后台那份实体，
     * 快照三格、有效期、状态在这一颗上都不认 —— 这一格说的是「这一枚要不要挂在账号卡下面给人看」，不是账）。
     * 上面那条列表**不按这一格筛**（收起来的徽章还得在这一格里看得见才能再放出来），
     * 只有账号卡下面那一排挂件读它：is_show=0 的那几枚不挂上去。
     * id 是前端给的，所以归属在服务层无条件比登录 id —— 不验就能拿别人的徽章行 id 替别人收徽章。
     * 为什么放 web 包不放 comm：这一颗只有前台一个使用方（后台同一格走 shop-admin 的 PUT /activity/badge/record/show）。
     */
    @PutMapping("/badge/show")
    @ApiOperation(value = "收起或展示自己名下一枚徽章（只动 is_show 一列，收起来的不再挂在账号卡下面）")
    public Result<Void> setBadgeShow(@RequestBody ShopActivityBadgeUser form) {
        shopBadgeWriteService.setShow(form.getId(), form.getIsShow());
        return Result.success();
    }

    /**
     * 我的积分账户：「积分」那个 tab 上面那一格（可用/累计获得/累计消耗/冻结中/即将过期/建账时间）。
     *
     * 路径：GET /api/web/shop/profile/points（一次一行，所以不分页、也没有 id 参数）。
     * 那五个数读的都是库里存的那几格，不做现算（恒等式由写入方在同一个事务里守住，同后台积分管理那页）；
     * 一分都没获得过就没有这一行账，这一条回 null 由前端说成「还没有积分账户」，不报错误。
     */
    @GetMapping("/points")
    @ApiOperation(value = "我的积分账户（库里存的那五个数；还没建账回 null）")
    public Result<ShopActivityPoints> points() {
        return Result.success(shopProfileReadService.pointsAccount());
    }

    /**
     * 我的积分流水：「积分」那个 tab 下面那一张表，一行一笔变动、最新一笔在前。
     *
     * 路径：GET /api/web/shop/profile/points/log/list?bizType=（业务类型可选筛，1签到 … 10管理员调整）。
     * 只有读：这一张表是凭证，由签到/下单/退款那几路和账户行同一个事务里带出来，前台后台都没有写入口。
     * userId 同样被服务层钉成登录 id。
     */
    @GetMapping("/points/log/list")
    @ApiOperation(value = "我的积分流水（只看自己名下的，最新一笔在前）")
    public Result<IPage<ShopActivityPointsLog>> pointsLogList(ShopActivityPointsLog query) {
        return Result.success(shopProfileReadService.pointsLogPage(query));
    }

    /**
     * 我的钱包账户：「钱包」那个 tab 上面那一格（可用余额/冻结中/赠送余额/累计充值/累计消费/累计提现/状态/建账时间）。
     *
     * 路径：GET /api/web/shop/profile/wallet（一次一行，所以不分页、也没有 id 参数）。
     * 那几个数读的都是库里存的那几格，不做现算（恒等式由写入方在同一个事务里守住，同后台钱包管理那页）；
     * 一分钱都没进过账就没有这一行，这一条回 null 由前端说成「还没有钱包账户」，不报错误。
     */
    @GetMapping("/wallet")
    @ApiOperation(value = "我的钱包账户（库里存的那几个数；还没建账回 null）")
    public Result<ShopConsumerWallet> wallet() {
        return Result.success(shopProfileReadService.walletAccount());
    }

    /**
     * 我的钱包流水：「钱包」那个 tab 下面那一张表，一行一笔余额变动、最新一笔在前。
     *
     * 路径：GET /api/web/shop/profile/wallet/log/list?bizType=（业务类型可选筛，1充值 2消费 3退款 …）。
     * 只有读：这一张表是凭证，由余额支付冻结/取消解冻/退款退回那几路和账户行在同一个事务里带出来，
     * 前台后台都没有写入口。userId 同样被服务层钉成登录 id。
     */
    @GetMapping("/wallet/log/list")
    @ApiOperation(value = "我的钱包流水（只看自己名下的，最新一笔在前）")
    public Result<IPage<ShopConsumerWalletLog>> walletLogList(ShopConsumerWalletLog query) {
        return Result.success(shopProfileReadService.walletLogPage(query));
    }
}
