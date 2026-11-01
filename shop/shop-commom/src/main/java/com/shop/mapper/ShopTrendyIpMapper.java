package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.entity.ShopTrendyIp;
import org.apache.ibatis.annotations.Mapper;

/**
 * 潮玩IP表 Mapper接口
 *
 * 没有自定义 SQL：分页查询不需要联表回写字段（名下系列数是服务层按 ip_id 分组数出来的），
 * 走 BaseMapper 才能自动带上全局逻辑删除条件（口径同 ShopTrendyMapper）。
 */
@Mapper
public interface ShopTrendyIpMapper extends BaseMapper<ShopTrendyIp> {
}
