package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopActivityCouponUser;

/**
 * 用户优惠券 服务接口（后台这一页只读）
 *
 * 写入只有「发放」那一条路，而它长在 ShopActivityCouponService 上（占发行额度和插用户券必须同一个事务），
 * 所以这里不开 add/update/delete，只给列表页那颗「发放记录」弹窗提供分页读。
 */
public interface ShopActivityCouponUserService extends IService<ShopActivityCouponUser> {

    /**
     * 分页查某一张券的发放记录（列表页那颗「发放记录」弹窗）
     *
     * @param query 查询条件（couponId/status/userId/couponCode）
     * @return 用户券分页，昵称和手机号已联表带出
     */
    IPage<ShopActivityCouponUser> selectRecordPage(ShopActivityCouponUser query);
}
