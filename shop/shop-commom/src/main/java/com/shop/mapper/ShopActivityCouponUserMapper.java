package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopActivityCouponUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 用户优惠券 Mapper接口
 *
 * 写入有三条路：后台「发放」（ShopActivityCouponServiceImpl#give，插行）、统一下单核销
 *（{@link #redeem}）、以及统一下单取消订单时把券退回去（{@link #giveBack}）；
 * 读是列表页那颗「发放记录」弹窗的明细，要按 user_id 联 sys_user 带出昵称和手机号。
 * 面额那几格本表有快照，不联模板 —— 联了就会把后来改过的面额显示到已经发出去的券上。
 */
@Mapper
public interface ShopActivityCouponUserMapper extends BaseMapper<ShopActivityCouponUser> {

    /**
     * 分页查某一张券的发放记录（按状态可选筛，最新一条在前）
     */
    IPage<ShopActivityCouponUser> selectRecordPage(IPage<ShopActivityCouponUser> page,
                                                   @Param("q") ShopActivityCouponUser query);

    /**
     * 核销这一张券：0 未使用 → 1 已使用，同时把用在哪个订单上（id + 号）和使用时间一起写上。
     *
     * 走的是 UPDATE，WHERE 带着 status = 0 —— 这就是并发那一道的闸：同一张券被两笔订单同时提交时，
     * 两条 UPDATE 抢同一行的锁，后到的那条发现 status 已经不是 0，一行都更新不到，服务层按返回 0 报"这张券刚被别的订单用掉"，
     * 整单回滚。所以不需要"先查状态再改"（那样两步之间正好有缝）。
     *
     * 归属（user_id）不在这里再判一次：那一步在服务层算钱之前已经判过，而这一列在系统里没有任何写入方会改它。
     *
     * @param id      用户券ID（shop_activity_coupon_user.id）
     * @param orderId 落在 sys_order.id
     * @param orderNo 落在 sys_order.order_no（冗余，同明细行那两列的口径）
     * @param useTime 使用时间，一次下单里用掉的几张共用同一个时间
     */
    int redeem(@Param("id") Long id, @Param("orderId") Long orderId,
               @Param("orderNo") String orderNo, @Param("useTime") LocalDateTime useTime);

    /**
     * 反核销（退券）：{@link #redeem} 的原路退回 —— 1 已使用 → 0 未使用，
     * 并把"用在哪个订单上"那三格（order_id / order_no / use_time）一起清空，这张券回到用户手上还能再用。
     *
     * 只有统一下单取消那一路会调它（见 shop-api 的 ShopOrderCouponService#giveBack）：
     * 券是买家的资产，单子没成交就不该收走它。
     *
     * WHERE 里 id + order_id + status = 1 三个一起压：这一张确实是这笔订单用掉的、而且还没被人动过。
     * 同一单的两次取消并发提交时，后到的那条读到的是 status 已经变回 0，一行都更新不到，
     * 服务层按返回 0 报错回滚（宁可让用户刷新，也不能把 used_count 减两遍）。
     *
     * @param id      用户券ID（shop_activity_coupon_user.id）
     * @param orderId 要退出哪张订单（sys_order.id）
     */
    int giveBack(@Param("id") Long id, @Param("orderId") Long orderId);

    /**
     * 过期扫描（定时任务 expireTask.expireUserCoupons 走这一条，一天一次）：
     * 0 未使用 且 过期时间已经过了的券 → 2 已过期。
     *
     * status = 0 压在 WHERE 里 ⇒ 已经用掉的、已经作废的绝不回头改状态，重复跑只补当天新进过窗口的那几张。
     * 一条 UPDATE 收全平台，别按人捞（形状同用户票那张表的 markExpired）。
     *
     * 用券那条链上"拿 expire_time 现判过期"的那一处仍然保留（这一条是按天扫的，
     * 刚过期的那几个小时里状态还是 0，不能让一张过期的券抵掉钱）—— 任务只是把账面摊平，不是取代现判。
     *
     * @param now 判过期的基准时刻（任务里传当前时间）
     * @return 这次落成已过期的张数
     */
    int markExpired(@Param("now") LocalDateTime now);
}
