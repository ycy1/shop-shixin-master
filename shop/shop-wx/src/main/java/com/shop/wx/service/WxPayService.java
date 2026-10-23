package com.shop.wx.service;

import com.shop.dto.order.ShopOrderCreateDto;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

public interface WxPayService {

    /**
     * jsapi 下单：先调统一下单服务（ShopOrderCreateService，/api/comm/shop/order/create 同一个）
     * 把订单主表 + 明细落库（顺带按收货地址坐标分仓并锁定库存），再用它返回的订单号和实付金额去微信下单。
     *
     * 整个过程一个事务：微信那一步失败时刚落库的订单一并回滚，库里不留待支付的孤单，
     * 那批被锁住的库存也一起还回可用量。
     */
    Map<String, Object> jsapiPay(ShopOrderCreateDto form) throws IOException;


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
