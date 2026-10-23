package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.dto.activity.ShopActivityPointsAdjustDto;
import com.shop.entity.ShopActivityPoints;

/**
 * 积分账户 服务接口（后台这一页：两条读 + 一颗「修改」）
 *
 * 这一张表是账不是单据（同商品库存），五个数只能由积分变动加加减减，所以：
 * 不开 add/update/delete —— 表单直接填那五个数会让账和流水对不上；
 * 唯一的写入口是 {@link #adjust}，它走带余额闸门的原子 UPDATE + 同事务补一行流水
 *（口径同优惠券占发行额度那一条，只是这边动的是账户上的分）。
 */
public interface ShopActivityPointsService extends IService<ShopActivityPoints> {

    /**
     * 分页查积分账户（列表页那一张表，昵称和手机号已联表带出，可用积分多的排前面）
     *
     * @param query 查询条件（userId/keyword）
     */
    IPage<ShopActivityPoints> selectPage(ShopActivityPoints query);

    /**
     * 管理员调整某个用户的积分（列表上那颗「修改」）：改账和记流水在同一件事里做完
     *
     * @return 调整后这个人的可用积分，前端拿它报"现在有多少分"
     */
    int adjust(ShopActivityPointsAdjustDto form);
}
