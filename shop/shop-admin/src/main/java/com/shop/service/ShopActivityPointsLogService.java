package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopActivityPointsLog;

/**
 * 用户积分流水 服务接口（只有查询，写法照库存流水 / 活动曝光点击记录那两页）
 *
 * 流水必须由一次真实的积分变动带出来，所以这里没有新增/修改/删除 —— 手工插一条"某人得了 100 分"，
 * 账户表上那个数就对不上了。
 */
public interface ShopActivityPointsLogService extends IService<ShopActivityPointsLog> {

    /**
     * 分页查某个用户的积分流水（列表页那颗「积分流水」弹窗）
     *
     * @param query 查询条件（userId/bizType）
     */
    IPage<ShopActivityPointsLog> selectRecordPage(ShopActivityPointsLog query);
}
