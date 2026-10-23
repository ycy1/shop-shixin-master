package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopDeliveryRider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 送货员表 Mapper接口
 */
@Mapper
public interface ShopDeliveryRiderMapper extends BaseMapper<ShopDeliveryRider> {

    /**
     * 分页查询送货员：一个关键字模糊匹配姓名/编码/手机号
     */
    IPage<ShopDeliveryRider> selectRiderPage(IPage<ShopDeliveryRider> page, @Param("q") ShopDeliveryRider query);
}
