package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopActivityCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 优惠券模板 Mapper接口
 */
@Mapper
public interface ShopActivityCouponMapper extends BaseMapper<ShopActivityCoupon> {

    /**
     * 分页查询优惠券：关键字跨名称/券码两列搜、按有效期开始筛区间，还要联门店和运营取名（写法同活动那张表）
     */
    IPage<ShopActivityCoupon> selectCouponPage(IPage<ShopActivityCoupon> page, @Param("q") ShopActivityCoupon query);

    /**
     * 单条优惠券（列表那一条 SQL 去掉筛选条件、按 id 取），和分页两条路共用同一套联表
     */
    ShopActivityCoupon selectCouponById(@Param("id") Long id);

    /**
     * 发放占量：已发放数原子加 N，顺带把发行总量校验压在同一个 WHERE 里
     * （total_count=0 是不限，直接放行；限量的就要 issued_count + N 不能越过 total_count）。
     *
     * 返回 0 就是没占到（券不存在、或可发放量不够）—— 这一条走的是 UPDATE，两个并发发放拿到的是各自的行锁，
     * 谁也不会把对方数过的额度再用一遍，所以「发超」在这一道就被挡死了，不需要先查后写。
     */
    int increaseIssuedCount(@Param("couponId") Long couponId, @Param("count") Integer count);

    /**
     * 核销占量：已使用数原子加 N（统一下单用掉这张模板下的券时按模板汇总调一次，见 shop-api 的 ShopOrderCouponService）。
     *
     * 写法同 increaseIssuedCount：加数和上限压在同一个 WHERE 里，返回 0 就是没占到。
     * 这一条的上限是 issued_count —— 用掉的不能比发出去的多，正常数据永远过得去（每张券都是先占发行额度才到用户手上），
     * 留着它是为了挡住"绕过发放直接往用户券表插行"那类脏数据：那种情况下宁可让下单失败回滚，
     * 也别把 used_count 加成一个比已发放还大的数，那三格数字以后就没法对账了。
     */
    int increaseUsedCount(@Param("couponId") Long couponId, @Param("count") Integer count);

    /**
     * 退券还量：{@link #increaseUsedCount} 的原路退回 —— 取消订单把券退给买家时，按模板一次减回去
     *（见 shop-api 的 ShopOrderCouponService#giveBack）。
     *
     * 写法还是条件增量，闸门是 used_count >= #{count}：这一格本来只由上面那条加、由这一条减，
     * 减不动就是数不对（那张券的核销压根没记在这条模板上，或者被减了两遍），
     * 返回 0 行由服务层报错让整单回滚 —— 宁可取消失败，也别把三格计数减成一个对不上账的数。
     *
     * 刻意不校验 issued_count：发出去几张和这一格减多少没关系，那条上限只在加的方向有意义。
     */
    int decreaseUsedCount(@Param("couponId") Long couponId, @Param("count") Integer count);
}
