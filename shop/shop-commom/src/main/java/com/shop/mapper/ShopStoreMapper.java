package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopStore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 门店表 Mapper接口
 */
@Mapper
public interface ShopStoreMapper extends BaseMapper<ShopStore> {

    /**
     * 分页查询门店：名称/编码一个关键字模糊匹配两个字段
     */
    IPage<ShopStore> selectStorePage(IPage<ShopStore> page, @Param("q") ShopStore query);
}
