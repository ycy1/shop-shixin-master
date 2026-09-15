package com.shop.wx.controller.pay;

import com.shop.common.Result;
import com.shop.wx.service.WxRefundService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@Slf4j
@Api(tags = "退款管理")
@CrossOrigin
@RequestMapping("/api/refund-info")
public class WxRefundController {

    @Resource
    private WxRefundService refundWxService;

    @ApiOperation("申请退款")
    @RequestMapping("refunds/{orderNo}/{reason}")
    public Result refunds(@PathVariable String orderNo, @PathVariable String reason) throws IOException {
        log.info("申请退款：{}",orderNo);
        return Result.success(refundWxService.createRefund(orderNo,reason));
    }

    @RequestMapping("query-refund-status/{refundNo}")
    @ApiOperation("退款状态查询")
    public Result queryRefundStats(@PathVariable String refundNo) throws IOException {
        //调用远程微信平台 按退款订单号查询退款状态
        return Result.success(refundWxService.queryRefundByNo(refundNo));
    }

    @RequestMapping("refunds/notify")
    @ApiOperation("微信平台退款回调")
    public Result<String> refundNotify(HttpServletRequest req, HttpServletResponse res) throws Exception {
        log.info("微信平台退款回调");
        return Result.success(refundWxService.refundNotify(req,res));
    }
}
