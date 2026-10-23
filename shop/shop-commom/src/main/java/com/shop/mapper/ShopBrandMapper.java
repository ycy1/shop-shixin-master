package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.entity.ShopBrand;
import org.apache.ibatis.annotations.Mapper;

/**
 * 品牌表 Mapper接口
 *
 * 没有自定义 SQL：分页查询不需要联表回写字段，走 BaseMapper 才能自动带上全局逻辑删除条件。
 */
@Mapper
public interface ShopBrandMapper extends BaseMapper<ShopBrand> {
}
