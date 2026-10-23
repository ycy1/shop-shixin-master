package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.api.service.ShopProfileReadService;
import com.shop.dto.address.ShopAddressQueryDto;
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
import com.shop.entity.SysUser;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopActivityBadgeUserMapper;
import com.shop.mapper.ShopActivityCouponUserMapper;
import com.shop.mapper.ShopActivityPointsLogMapper;
import com.shop.mapper.ShopActivityPointsMapper;
import com.shop.mapper.ShopConsumerAddressMapper;
import com.shop.mapper.ShopConsumerBrowseMapper;
import com.shop.mapper.ShopConsumerFavoriteMapper;
import com.shop.mapper.ShopConsumerWalletLogMapper;
import com.shop.mapper.ShopConsumerWalletMapper;
import com.shop.mapper.ShopOrderItemMapper;
import com.shop.mapper.ShopActivitySigninMapper;
import com.shop.mapper.SysRoleMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import com.shop.utils.SigninUtils;
import com.shop.vo.address.ShopAddressVo;
import com.shop.vo.browse.ShopBrowseVo;
import com.shop.vo.favorite.ShopFavoriteVo;
import com.shop.vo.order.ShopOrderVo;
import com.shop.vo.user.SysUserProfileVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @author xxj
 * @title ShopProfileReadServiceImpl
 * @description 个人中心那一页的读：六块列表先要登录态、再把 userId 钉成登录 id，剩下全交给现成的 XML；
 *              基础信息和积分账户那两格一个账号一行，只有「钉登录 id」这一句是共用的
 */
@Service
@RequiredArgsConstructor
public class ShopProfileReadServiceImpl implements ShopProfileReadService {

    private final ShopOrderItemMapper shopOrderItemMapper;

    private final ShopConsumerAddressMapper shopConsumerAddressMapper;

    private final ShopConsumerFavoriteMapper shopConsumerFavoriteMapper;

    private final ShopConsumerBrowseMapper shopConsumerBrowseMapper;

    /** 日历那一张：某个人某个月签过的那几天，读的是后台签到管理那页同一条 SQL */
    private final ShopActivitySigninMapper shopActivitySigninMapper;

    /**
     * 「个人信息」那一块下面几格读的那几张表：用户券一张、用户徽章一张、积分账户一张、积分流水一张。
     * SQL 全部复用后台那几条（优惠券的「发放记录」、徽章管理的「获取记录」、积分管理的列表和「积分流水」），这里只把归属钉死。
     */
    private final ShopActivityCouponUserMapper shopActivityCouponUserMapper;

    private final ShopActivityBadgeUserMapper shopActivityBadgeUserMapper;

    private final ShopActivityPointsMapper shopActivityPointsMapper;

    private final ShopActivityPointsLogMapper shopActivityPointsLogMapper;

    /**
     * 「钱包」那一格读的两张表：账户一行、流水一张。
     * 流水复用后台钱包管理那页「流水」弹窗那条 SQL（ShopConsumerWalletLogMapper.xml 的 selectRecordPage），
     * 账户跟积分账户一样按 userId 单查一行 —— 一人一行，uk_user 钉住了。
     */
    private final ShopConsumerWalletMapper shopConsumerWalletMapper;

    private final ShopConsumerWalletLogMapper shopConsumerWalletLogMapper;

    /** 基础信息那两块：sys_user 那一行，和它名下那几个角色名（角色名 SQL 和用户管理那页同一条） */
    private final SysUserMapper sysUserMapper;

    private final SysRoleMapper sysRoleMapper;

    /** 月份那一格的格式（和前端传过来的是同一个串，留空按当月） */
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * 基础信息那一格：形状和后台「个人信息」那一格一样（sys_user 那一行 + 名下角色名），所以复用 SysUserProfileVo。
     *
     * 为什么不用前端已经在读的 /api/auth/info：那一份 source 不是 admin 时 roles 是空的，
     * 而它连 password 一起带回来（BeanCopy 整个 SysUser），这一页不需要也不该看它。
     * 密码这一格在返回前清掉 —— SysUser 上钉的是 NON_NULL，清了它就不出现在 JSON 里。
     */
    @Override
    public SysUserProfileVo account() {
        Long loginUserId = LoginUserUtils.requireLoginUser("后查看基础信息").getId();
        SysUser user = sysUserMapper.selectById(loginUserId);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        user.setPassword(null);
        return SysUserProfileVo.builder()
                .sysUser(user)
                .roles(sysRoleMapper.selectRolesByUserId(loginUserId))
                .build();
    }

    /**
     * 名下优惠券：SQL 是 ShopActivityCouponUserMapper.xml 的 selectRecordPage（后台优惠券那页「发放记录」同一条），
     * 面额那几格读本表发放时定格的快照，不联模板 —— 联了就会把后来改过的面额显示到已经到用户手上的券上。
     *
     * 后台那一条按账号类型分叉（平台/门店/ERP 看全部、消费者只看自己名下），这一条没有那种身份：
     * userId 无条件覆盖成登录 id，否则前台带一个 userId 就能捞别人的券。
     */
    @Override
    public IPage<ShopActivityCouponUser> couponPage(ShopActivityCouponUser query) {
        query.setUserId(LoginUserUtils.requireLoginUser("后查看我的优惠券").getId());
        return shopActivityCouponUserMapper.selectRecordPage(PageUtil.getPage(), query);
    }

    /**
     * 名下徽章：SQL 是 ShopActivityBadgeUserMapper.xml 的 selectRecordPage（后台徽章管理那页「获取记录」同一条），
     * 名称/文字/图标那三格读本表发放时定格的快照，不联定义表 —— 徽章后来改了名换了对这个人手上那枚不起作用。
     *
     * 后台那一条也按账号类型分叉（平台/门店/ERP 看全平台谁拿了什么、消费者只看自己名下），这一条没有那种身份：
     * userId 无条件覆盖成登录 id，否则前台带一个 userId 就能看别人拿了哪些徽章。
     */
    @Override
    public IPage<ShopActivityBadgeUser> badgePage(ShopActivityBadgeUser query) {
        query.setUserId(LoginUserUtils.requireLoginUser("后查看我的徽章").getId());
        return shopActivityBadgeUserMapper.selectRecordPage(PageUtil.getPage(), query);
    }

    /**
     * 积分账户那一行：一个用户一行，没获得过一分就没有这一行（账是由第一次积分变动带出来的，后台也不建账），
     * 所以这里回 null 由前端说成「还没有积分账户」，不报「积分不存在」那一句。
     */
    @Override
    public ShopActivityPoints pointsAccount() {
        Long loginUserId = LoginUserUtils.requireLoginUser("后查看我的积分").getId();
        return shopActivityPointsMapper.selectOne(new LambdaQueryWrapper<ShopActivityPoints>()
                .eq(ShopActivityPoints::getUserId, loginUserId)
                .last("LIMIT 1"));
    }

    /**
     * 积分流水：SQL 是 ShopActivityPointsLogMapper.xml 的 selectRecordPage（后台积分管理那行点开的「积分流水」同一条）。
     * 这一张表只插不改不删，所以前台也只有读 —— points 带符号（获取为正、消耗为负），balance 是那一笔之后的余额。
     */
    @Override
    public IPage<ShopActivityPointsLog> pointsLogPage(ShopActivityPointsLog query) {
        query.setUserId(LoginUserUtils.requireLoginUser("后查看我的积分流水").getId());
        return shopActivityPointsLogMapper.selectRecordPage(PageUtil.getPage(), query);
    }

    /**
     * 钱包账户那一行：一人一行，所以按登录 id 单查（同上面 pointsAccount 那一句的写法）。
     * 没用过余额支付、也没收过退款的那一些账号根本没有这一行，回 null 由前端说成「还没有钱包账户」，不报错误。
     */
    @Override
    public ShopConsumerWallet walletAccount() {
        Long loginUserId = LoginUserUtils.requireLoginUser("后查看我的钱包").getId();
        return shopConsumerWalletMapper.selectOne(new LambdaQueryWrapper<ShopConsumerWallet>()
                .eq(ShopConsumerWallet::getUserId, loginUserId)
                .last("LIMIT 1"));
    }

    /**
     * 钱包流水：SQL 是 ShopConsumerWalletLogMapper.xml 的 selectRecordPage（后台钱包管理那行点开的「流水」同一条）。
     * 这一张表只插不改不删，前台也只有读 —— amount 带符号（收入为正、支出为负），balance 是这一笔之后的可用余额。
     */
    @Override
    public IPage<ShopConsumerWalletLog> walletLogPage(ShopConsumerWalletLog query) {
        query.setUserId(LoginUserUtils.requireLoginUser("后查看我的钱包流水").getId());
        return shopConsumerWalletLogMapper.selectRecordPage(PageUtil.getPage(), query);
    }

    /**
     * 订单：SQL 是 ShopOrderItemMapper.xml 的 selectOrderPage（一行一张，商品行数/门店名/配送单号那几格它自己带）。
     */
    @Override
    public IPage<ShopOrderVo> orderPage(ShopOrderQueryDto query) {
        query.setUserId(LoginUserUtils.requireLoginUser("后查看我的订单").getId());
        return shopOrderItemMapper.selectOrderPage(PageUtil.getPage(), query);
    }

    /**
     * 详情：主表那一条按 id 捞（同一份 orderColumns，行数/件数/门店名都跟着），明细另查一次填进去 ——
     * 弹窗下半那张表要的是名下全部行、不分页，所以不走列表那条 SQL。
     *
     * 归属判据只有一句：行上的 user_id 不是登录 id 就当这一单没有。
     * 不说「没权限看这一单」—— 那一句本身就是答案，等于告诉调用方这个 id 挂在谁名下
     *（和商品详情把下架/已删/没这个 id 收成同一句话一个道理）。
     */
    @Override
    public ShopOrderVo orderDetail(Long id) {
        Long loginUserId = LoginUserUtils.requireLoginUser("后查看订单详情").getId();
        ShopOrderVo order = id == null ? null : shopOrderItemMapper.selectOrderById(id);
        if (order == null || !loginUserId.equals(order.getUserId())) {
            throw new ServiceException("订单不存在");
        }
        order.setItems(shopOrderItemMapper.selectOrderItemsByOrderId(id));
        return order;
    }

    @Override
    public IPage<ShopAddressVo> addressPage(ShopAddressQueryDto query) {
        query.setUserId(LoginUserUtils.requireLoginUser("后查看收货地址").getId());
        return shopConsumerAddressMapper.selectAddressPage(PageUtil.getPage(), query);
    }

    @Override
    public IPage<ShopFavoriteVo> favoritePage(ShopFavoriteQueryDto query) {
        query.setUserId(LoginUserUtils.requireLoginUser("后查看我的收藏").getId());
        return shopConsumerFavoriteMapper.selectFavoritePage(PageUtil.getPage(), query);
    }

    @Override
    public IPage<ShopBrowseVo> browsePage(ShopBrowseQueryDto query) {
        query.setUserId(LoginUserUtils.requireLoginUser("后查看浏览足迹").getId());
        return shopConsumerBrowseMapper.selectBrowsePage(PageUtil.getPage(), query);
    }

    /**
     * 签到日历：一次给一个月签过的那几天，没签的日子由前端画成空格子。
     *
     * 这一条不给 userId 那一格任何入口（参数里根本没有）—— 日历看的就是自己，
     * 能点名别人就等于让前台拿别人的 id 捞别人的签到记录。
     * 筛的是归属日 sign_date：这个月补的那个签落在它自己那一天那一格上，所以跟着那个月走。
     * 返回的每一行上盖的是同一个数 —— "这个人到最近那一天为止连着签了几天"（这张表上不存这一列，
     * 从最晚那一天往前逐日回数、最多 7 天，算式见 SigninUtils）；前端只在日历上面那一排说一次，不逐格说。
     */
    @Override
    public List<ShopActivitySignin> signinMonth(String month) {
        Long loginUserId = LoginUserUtils.requireLoginUser("后查看签到记录").getId();
        String currentMonth = month == null || month.isBlank()
                ? LocalDate.now().format(MONTH_FORMAT) : month.trim();
        List<ShopActivitySignin> records = shopActivitySigninMapper.selectMonthRecords(loginUserId, currentMonth);
        int continuous = SigninUtils.continuousStreak(
                shopActivitySigninMapper.selectRecentSignDates(loginUserId, LocalDate.now()));
        records.forEach(item -> item.setCurrentContinuous(continuous));
        return records;
    }
}
