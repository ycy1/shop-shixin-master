package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopConsumerWallet;

/**
 * 钱包账户 服务接口（后台这一页只有一条读）
 *
 * 这一张表是账不是单据（同积分账户、商品库存）：那五个数只能由钱包变动加加减减带出来，
 * 所以这里不开 add/update/delete，也**没有**「修改」那颗按钮 ——
 * 表单直接填余额会出现"账上多了 100 块、流水里什么都没有"，积分那一本的调整这一轮没照搬过来。
 * 变动全在统一下单/退款那一路（shop-api），后台这一页只看不改。
 */
public interface ShopConsumerWalletService extends IService<ShopConsumerWallet> {

    /**
     * 分页查钱包账户（列表页那一张表，昵称和手机号已联表带出，正常状态在前、可用余额多的排前面）
     *
     * @param query 查询条件（userId/status/keyword）
     */
    IPage<ShopConsumerWallet> selectPage(ShopConsumerWallet query);
}
