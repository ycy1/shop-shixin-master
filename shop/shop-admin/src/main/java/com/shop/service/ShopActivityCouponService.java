package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.dto.activity.ShopActivityCouponGiveDto;
import com.shop.entity.ShopActivityCoupon;

import java.util.List;

/**
 * 优惠券模板 服务接口（营销活动 -> 优惠券）
 */
public interface ShopActivityCouponService extends IService<ShopActivityCoupon> {

    /**
     * 分页查询优惠券
     *
     * @param query 查询条件（keyword/couponType/status/startFrom/startTo）
     * @return 优惠券分页，门店名和运营昵称已联表带出，scope_value 也顺手拆成 scopeIds 给表单回显用
     */
    IPage<ShopActivityCoupon> selectPage(ShopActivityCoupon query);

    /**
     * 单条优惠券（修改弹窗回显、只读详情弹窗都走它）
     *
     * @param id 券id
     * @return 优惠券
     */
    ShopActivityCoupon selectCouponById(Long id);

    /**
     * 新增：状态强制落 1 未开始，券码不传则系统生成；issued_count/used_count 这两格表单说不动（实体钉 NEVER）
     *
     * @param coupon 优惠券
     */
    void insert(ShopActivityCoupon coupon);

    /**
     * 修改：状态在这一次整行提交；已发放/已使用那两格不跟着请求体走
     *
     * @param coupon 优惠券
     */
    void update(ShopActivityCoupon coupon);

    /**
     * 批量逻辑删除：只删还没发出去过的那几张（已经发到用户手上的券，用户券表那些行还指着这张模板）
     *
     * @param ids 主键集合
     */
    void deleteByIds(List<Long> ids);

    /**
     * 发放：给一批用户各插一行用户券（面额那几格按模板当场快照，券码按 issued_count 排号）。
     *
     * 占额度是一条带总量校验的原子 UPDATE，占不到就整笔回滚报「可发放量不够」，所以并发发放不会把券发超。
     *
     * @param form 发放参数（couponId + userIds + remark）
     * @return 实际发出去的行数（同一批里重复的账号已去重）
     */
    int give(ShopActivityCouponGiveDto form);
}
