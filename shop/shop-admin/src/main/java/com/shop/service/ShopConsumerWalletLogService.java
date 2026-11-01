package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopConsumerWalletLog;

/**
 * 钱包流水 服务接口（只有查询，写法照积分流水 / 库存流水那两页）
 *
 * 流水必须由一次真实的余额变动带出来，所以这里没有新增/修改/删除 —— 手工插一条"某人多了 100 块"，
 * 账户表上那个数就对不上了。
 */
public interface ShopConsumerWalletLogService extends IService<ShopConsumerWalletLog> {

    /**
     * 分页查某个用户的钱包流水（列表页那颗「流水」弹窗）
     *
     * @param query 查询条件（userId/bizType）
     */
    IPage<ShopConsumerWalletLog> selectRecordPage(ShopConsumerWalletLog query);
}
