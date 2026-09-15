package com.shop.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.entity.SysOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 订单主表 Mapper接口
 */
@Mapper
public interface SysOrderMapper extends BaseMapper<SysOrder> {

    IPage<SysOrder> selectOrderPage(@Param("page") Page<SysOrder> page, @Param("query")SysOrder query);
} 