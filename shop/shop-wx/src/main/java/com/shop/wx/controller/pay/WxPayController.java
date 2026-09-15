package com.shop.wx.controller.pay;

import com.google.gson.Gson;
import com.shop.common.Result;
import com.shop.entity.SysOrder;
import com.shop.wx.service.WxPayService;
import com.shop.wx.utils.HttpUtils;
import com.shop.wx.utils.WechatPay2ValidatorForRequest;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@Api(tags = "微信支付管理")
@RequestMapping("/api/wx-pay")
@Slf4j
@CrossOrigin
@AllArgsConstructor
public class WxPayController {

    private final WxPayService wxPayService;

    @ApiOperation("jsapi下单")
    @RequestMapping("/jsapi/createOrder")
    public Result PayNative(@RequestBody SysOrder order) throws Exception{
        return Result.success(wxPayService.jsapiPay(order));
    }


    @RequestMapping("/jsapi/notify")
    @ApiOperation("微信平台支付回调接口")
    public Result nativeNotify(HttpServletRequest req, HttpServletResponse res) throws Exception{
        return Result.success(wxPayService.jsapiNotify(req,res));
    }

    @RequestMapping("orderStatus/{orderNo}")
    @ApiOperation("订单状态查询")
    public Result queryOrderStats(@PathVariable String orderNo) throws IOException {
        //调用远程微信平台 按商户订单号查询订单状态
        return Result.success(wxPayService.queryOrderByNo(orderNo));
    }

    @RequestMapping("/cancel/{orderNo}")
    @ApiOperation("订单关闭")
    public Result cancel(@PathVariable String orderNo) throws IOException {
        //调用微信平台关单接口
        wxPayService.cancel(orderNo);
        //更新数据库订单状态
//        orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.CANCEL);
        return Result.success();
    }

}
