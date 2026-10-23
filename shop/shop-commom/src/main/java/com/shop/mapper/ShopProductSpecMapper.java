package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopProductSpec;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商品规格表 Mapper接口
 */
@Mapper
public interface ShopProductSpecMapper extends BaseMapper<ShopProductSpec> {

    /**
     * 分页查询规格，联表带出所属类目名称
     */
    IPage<ShopProductSpec> selectSpecPage(IPage<ShopProductSpec> page, @Param("q") ShopProductSpec query);
}
