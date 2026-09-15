package com.shop.service;

import com.shop.entity.SysOrder;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;

/**
 * 订单主表 服务接口
 */
public interface SysOrderService extends IService<SysOrder> {
    /**
     * 查询订单主表分页列表
     */
    IPage<SysOrder> selectPage(SysOrder sysOrder);

    /**
     * 新增订单主表
     */
    boolean insert(SysOrder sysOrder);

    /**
     * 修改订单主表
     */
    boolean update(SysOrder sysOrder);

    /**
     * 批量删除订单主表
     */
    boolean deleteByIds(List<Long> ids);
}
