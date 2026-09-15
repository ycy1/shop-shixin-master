package com.shop.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.entity.SysRefund;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 退款表 Mapper接口
 */
@Mapper
public interface SysRefundMapper extends BaseMapper<SysRefund> {

    Page<SysRefund> selectRefundPage(@Param("page") Page<SysRefund> page, @Param("query")SysRefund sysRefund);
} 