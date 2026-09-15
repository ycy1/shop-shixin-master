package com.shop.wx.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.SysRefund;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * 退款表 服务接口
 */
public interface WxRefundService extends IService<SysRefund> {


    /**
     * 新增退款表
     */
    SysRefund createRefund(String orderNo, String reason) throws IOException;

    /**
     * 微信支付退款回调接口
     */
    String refundNotify(HttpServletRequest req, HttpServletResponse res) throws Exception;

    /**
     * 按退款单号查询退款状态
     */
    Map queryRefundByNo(String refundNo) throws IOException;



}
