package com.shop.api.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.dto.address.ShopAddressQueryDto;
import com.shop.dto.browse.ShopBrowseQueryDto;
import com.shop.dto.favorite.ShopFavoriteQueryDto;
import com.shop.dto.order.ShopOrderQueryDto;
import com.shop.vo.address.ShopAddressVo;
import com.shop.vo.browse.ShopBrowseVo;
import com.shop.vo.favorite.ShopFavoriteVo;
import com.shop.vo.order.ShopOrderVo;
import com.shop.vo.user.SysUserProfileVo;
import com.shop.entity.ShopActivityBadgeUser;
import com.shop.entity.ShopActivityCouponUser;
import com.shop.entity.ShopActivityPoints;
import com.shop.entity.ShopActivityPointsLog;
import com.shop.entity.ShopActivitySignin;
import com.shop.entity.ShopConsumerWallet;
import com.shop.entity.ShopConsumerWalletLog;

import java.util.List;

/**
 * 个人中心读 服务接口（Web 前台：个人信息 / 我的订单 / 收货地址 / 用户收藏 / 浏览足迹 / 签到记录）
 *
 * 为什么写在 shop-api：这几张表后台那一套是「代客管理」，服务层第一句按账号类型收窄 ——
 * 平台/门店/ERP 那些账号看的是全部，只有消费者账号才被压到自己名下。前台没有这种身份，
 * 复用就会变成「谁带 token 进来都能捞别人的单」。这一包只做一件事：按登录 id 收窄，一次只给一个格。
 *
 * 只有读：地址/收藏/足迹要改动都走后台或后面另开的写接口，这里不塞；
 * 账号那一行能改的那两件事（基本信息那八格、密码）都不在这份里，在 ShopAccountWriteService；
 * 签到那两个动作（签今天、补签）也不在这份里，它们在 shop-api 的 ShopSigninService，
 * 前台走 /api/comm 那两条，这里只把日历要画的那几格读出来。
 */
public interface ShopProfileReadService {

    /**
     * 自己的基本信息（sys_user 那一行 + 名下角色），给「个人信息」那一块下面的「基础信息」tab。
     *
     * 不给 id 参数：这一格画的就是登录者自己，能让前台点名别人的 id 就等于把别人的手机号、
     * 邮箱、地区、注册时间摊开给人查（口径同下面 signinMonth 那一句）。
     * 不复用 /api/auth/info：那一份 source 不是 admin 时 roles 是空的，而且它连 password 一起带回来。
     * 密码那一格在返回前清掉（SysUser 上是 NON_NULL，清了就不出现在 JSON 里）。
     */
    SysUserProfileVo account();

    /**
     * 名下优惠券（「个人信息」那一块下面的「优惠券」tab）：一行一张券，面额那几格是发放时定格的快照。
     *
     * @param query 状态可挑（0未使用 1已使用 2已过期 3已作废），userId 由服务端覆盖成登录 id
     */
    IPage<ShopActivityCouponUser> couponPage(ShopActivityCouponUser query);

    /**
     * 名下徽章（「个人信息」那一块下面的「徽章」tab）：一行一枚，名称/文字/图标那三格是发放时定格的快照。
     *
     * 走的是后台「徽章管理 → 获取记录」那一条 SQL（ShopActivityBadgeUserMapper.xml 的 selectRecordPage），
     * 那边按账号类型收窄（平台账号看全平台、消费者只看自己），这一条不给那个口子：userId 服务端覆盖成登录 id，
     * 能点名别人就等于看别人拿了哪些徽章。
     *
     * @param query 获得来源（1系统发放 … 5门店发放）和状态（1有效 0失效 2已过期）可挑，badgeId 也可挑
     */
    IPage<ShopActivityBadgeUser> badgePage(ShopActivityBadgeUser query);

    /**
     * 名下积分账户那一行（「积分」tab 上面那一格）：一条账都没获得过就回 null，不是数据丢了。
     *
     * 不给 id 参数，也不给关键字 —— 这一格只有一个事实：登录者自己现在有多少分。
     * 那五个数是存的、读时不做现算（口径同后台积分管理那页），所以这里只是把那几列搬出来。
     */
    ShopActivityPoints pointsAccount();

    /**
     * 名下积分流水（「积分」tab 下面那一张表）：一行一笔变动，最新一笔在前。
     *
     * @param query 业务类型可挑（1签到 … 10管理员调整），userId 由服务端覆盖成登录 id
     */
    IPage<ShopActivityPointsLog> pointsLogPage(ShopActivityPointsLog query);

    /**
     * 名下钱包账户那一行（「钱包」tab 上面那一格）：一分钱都没进过账就没有这一行，回 null 不是数据丢了。
     *
     * 不给 id 参数 —— 这一格只有一个事实：登录者自己现在有多少钱。
     * 那六个数是存的、读时不做现算（恒等式由写入方在同一个事务里守住，口径同后台钱包管理那页）。
     */
    ShopConsumerWallet walletAccount();

    /**
     * 名下钱包流水（「钱包」tab 下面那一张表）：一行一笔余额变动，最新一笔在前。
     *
     * 只有读：这一张表是凭证，由余额支付/取消解冻/退款退回那几路和账户行同一个事务里带出来。
     *
     * @param query 业务类型可挑（1充值 2消费 3退款 …），userId 由服务端覆盖成登录 id
     */
    IPage<ShopConsumerWalletLog> walletLogPage(ShopConsumerWalletLog query);

    /**
     * 名下订单，一行一张订单（不带明细行）。
     *
     * @param query 关键字/状态/时间区间可挑，userId 由服务端覆盖成登录 id
     */
    IPage<ShopOrderVo> orderPage(ShopOrderQueryDto query);

    /**
     * 名下某一张订单的详情：主表字段 + 名下全部商品明细行。
     *
     * @param id 订单 id；不在自己名下的一律当不存在（分了档就等于让调用方拿 id 试出谁名下有过这一单）
     */
    ShopOrderVo orderDetail(Long id);

    /** 名下收货地址 */
    IPage<ShopAddressVo> addressPage(ShopAddressQueryDto query);

    /** 名下收藏，targetName 由 SQL 按 targetType 联商品/门店/品牌现带 */
    IPage<ShopFavoriteVo> favoritePage(ShopFavoriteQueryDto query);

    /** 名下浏览足迹，按最近一次浏览时间倒序 */
    IPage<ShopBrowseVo> browsePage(ShopBrowseQueryDto query);

    /**
     * 名下某一个月签过的那几天（日历弹窗一次全带，一个月最多 31 行，所以不分页）。
     *
     * @param month yyyy-MM，留空按当月
     */
    List<ShopActivitySignin> signinMonth(String month);
}
