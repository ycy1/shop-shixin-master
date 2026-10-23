package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.entity.ShopCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品类目表 Mapper接口
 */
@Mapper
public interface ShopCategoryMapper extends BaseMapper<ShopCategory> {

    /**
     * 按条件查询扁平类目列表，联表 shop_icon 带出图标类型与内容
     */
    List<ShopCategory> selectCategoryList(@Param("q") ShopCategory query);
}
