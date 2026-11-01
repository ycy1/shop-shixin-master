package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.entity.ShopTrendyBox;
import org.apache.ibatis.annotations.Mapper;

/**
 * 盲盒表 Mapper接口
 *
 * 没有自定义 SQL：列表要显示的系列名/门店名由服务层按当页 id 一次查回来（口径同 ShopStoreMapper），
 * 走 BaseMapper 才能自动带上全局逻辑删除条件。
 */
@Mapper
public interface ShopTrendyBoxMapper extends BaseMapper<ShopTrendyBox> {
}
