package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopDeliveryCompany;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 物流公司表 Mapper接口
 */
@Mapper
public interface ShopDeliveryCompanyMapper extends BaseMapper<ShopDeliveryCompany> {

    /**
     * 分页查询物流公司：一个关键字模糊匹配名称/编码/简称，支持服务按"任一支持"筛
     */
    IPage<ShopDeliveryCompany> selectCompanyPage(IPage<ShopDeliveryCompany> page,
                                                       @Param("q") ShopDeliveryCompany query);
}
