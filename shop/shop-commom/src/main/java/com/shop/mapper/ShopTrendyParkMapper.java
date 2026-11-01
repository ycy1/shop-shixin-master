package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopTrendyPark;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 主题公园园区表 Mapper接口
 *
 * 列表走自定义 XML 只为一件事：一个关键字同时搜园区名称和编号（两个 OR 条件在 LambdaQueryWrapper 里
 * 拼不出这种"括号内 OR 不串到上面筛选"的形状，口径同 ShopStoreMapper#selectStorePage）。
 * 写的一律走 BaseMapper。
 */
@Mapper
public interface ShopTrendyParkMapper extends BaseMapper<ShopTrendyPark> {

    /**
     * 分页查询园区：keyword 模糊匹配名称/编号，ipId、status 精确匹配
     */
    IPage<ShopTrendyPark> selectParkPage(IPage<ShopTrendyPark> page, @Param("q") ShopTrendyPark query);
}
