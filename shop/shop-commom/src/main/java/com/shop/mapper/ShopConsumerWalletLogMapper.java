package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopConsumerWalletLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 钱包流水 Mapper接口
 *
 * 只有读（列表页那颗「流水」弹窗）和 insert：这一张表是凭证，不改不删，
 * 写入方只有 shop-api 的钱包服务（下单冻结 / 取消解冻 / 退款退回），
 * 每一个都跟着账户表那一笔增量在同一个事务里（口径同积分流水、库存流水）。
 */
@Mapper
public interface ShopConsumerWalletLogMapper extends BaseMapper<ShopConsumerWalletLog> {

    /**
     * 分页查某个用户的钱包流水（bizType 可选筛，最新一笔在前）
     */
    IPage<ShopConsumerWalletLog> selectRecordPage(IPage<ShopConsumerWalletLog> page,
                                                  @Param("q") ShopConsumerWalletLog query);
}
