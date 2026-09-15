package com.shop.wx.service;

import com.shop.entity.SysOrder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

public interface WxPayService {

    /**
     * 统一下单接口 创建订单
     */
    Map<String, Object> jsapiPay(SysOrder order) throws IOException;


    /**
     * 微信支付回调接口
     */
    String jsapiNotify(HttpServletRequest req, HttpServletResponse res) throws Exception;

    /**
     * 查询订单
     */
    Map queryOrderByNo(String orderNo) throws IOException;

    /**
     * 关闭订单
     */
    void cancel(String orderNo) throws IOException;

}
