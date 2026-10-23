package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopActivityPointsLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户积分流水 Mapper接口
 *
 * 读的是积分管理列表那行账户点开的「积分流水」弹窗（按用户分页、最新一笔在前）。
 * 写入方要等签到/购物/兑换那几路动作接上才有（这一版还没有），
 * 而且必须是"改账户 + 记流水"同一个事务里一起做 —— 只插流水不动账，这本账就对不上了。
 */
@Mapper
public interface ShopActivityPointsLogMapper extends BaseMapper<ShopActivityPointsLog> {

    /**
     * 分页查某个用户的积分流水（bizType 可选筛，最新一笔在前）
     */
    IPage<ShopActivityPointsLog> selectRecordPage(IPage<ShopActivityPointsLog> page,
                                                  @Param("q") ShopActivityPointsLog query);
}
