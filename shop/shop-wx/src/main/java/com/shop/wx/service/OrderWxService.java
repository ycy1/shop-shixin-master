package com.shop.wx.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.SysOrder;

/**
 * 订单主表 服务接口
 */
public interface OrderWxService extends IService<SysOrder> {

    /**
     * 新增订单主表
     */
    SysOrder createOrder(SysOrder sysOrder);

}
