package com.shop.wx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import com.shop.entity.SysOrder;
import com.shop.entity.SysRefund;
import com.shop.mapper.SysRefundMapper;
import com.shop.utils.GeneratorNoUtils;
import com.shop.wx.config.WxPayConfig;
import com.shop.wx.enums.wxpay.WxApiType;
import com.shop.wx.enums.wxpay.WxNotifyType;
import com.shop.wx.enums.wxpay.WxRefundStatus;
import com.shop.wx.service.OrderWxService;
import com.shop.wx.service.WxRefundService;
import com.shop.wx.utils.HttpUtils;
import com.shop.wx.utils.WechatPay2ValidatorForRequest;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author xxj
 * @title SysRefundWxServiceImpl
 * @date 2026/9/14 11:33
 * @description TODO
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundWxServiceImpl extends ServiceImpl<SysRefundMapper, SysRefund> implements WxRefundService {

    private final WxPayConfig wxPayConfig;
    private final CloseableHttpClient wxPayClient;
    private Verifier verifier;

    private final ReentrantLock lock = new ReentrantLock();

    private final OrderWxService orderWxService;
    @Override
    public SysRefund createRefund(String orderNo, String reason) throws  IOException{
        //1.根据订单号生成退款订单
        SysOrder orderInfo = orderWxService.getOne(new QueryWrapper<SysOrder>().lambda().eq(SysOrder::getOrderNo, orderNo));
        SysRefund refundInfo = new SysRefund();
        refundInfo.setOrderNo(orderNo);//订单编号
        refundInfo.setRefundNo(GeneratorNoUtils.getRefundNo());//退款单编号
        refundInfo.setRefundAmount(orderInfo.getPayAmount());//原订单金额(分)
        refundInfo.setActualRefundAmount(orderInfo.getPayAmount());
        refundInfo.setRefundReason(reason);//退款原因
        this.save(refundInfo);
        //2.调用微信平台退款接口
        log.info("调用退款接口API");
        //请求URL
        HttpPost httpPost = new HttpPost(wxPayConfig.getDomain().concat(WxApiType.DOMESTIC_REFUNDS.getType()));
        // 请求body参数
        HashMap<Object, Object> paramsMap = new HashMap<>();
        paramsMap.put("out_trade_no", orderNo);//订单编号
        paramsMap.put("out_refund_no", refundInfo.getRefundNo());//退款单编号
        paramsMap.put("reason",reason);//退款原因
        paramsMap.put("notify_url",
                wxPayConfig.getNotifyDomain().concat(WxNotifyType.REFUND_NOTIFY.getType()));//退款通知地址
        Map amountMap = new HashMap();
        amountMap.put("refund", refundInfo.getActualRefundAmount());//退款金额
        amountMap.put("total", refundInfo.getRefundAmount());//原订单金额
        amountMap.put("currency", "CNY");//退款币种
        paramsMap.put("amount", amountMap);
        //将参数转换成json字符串
        String jsonParams = new Gson().toJson(paramsMap);
        log.info("请求参数 ===> {}" , jsonParams);
        StringEntity entity = new StringEntity(jsonParams,"utf-8");
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");

        //完成签名并执行请求
        CloseableHttpResponse response = wxPayClient.execute(httpPost);
        try {
            int statusCode = response.getStatusLine().getStatusCode();
            String bodyStr = EntityUtils.toString(response.getEntity());
            if (statusCode == 200) {
                log.info("success,return body = {}" , bodyStr);
            } else if (statusCode == 204) {
                log.info("success");
            } else {
                log.info("failed,resp code = {},return body = {}",statusCode, bodyStr);
                throw new IOException("request failed");
            }
            Map<String,String> fromMap = new Gson().fromJson(bodyStr, HashMap.class);
            //3.更新订单状态及新退款单
//            orderInfoService.updateStatusByOrderNo(orderNo,
//                    OrderStatus.REFUND_PROCESSING);
//            refundInfoService.updateRefund(fromMap);
        } finally {
            response.close();
        }

        //4.返回退款订单
        return refundInfo;
    }

    @Override
    public String refundNotify(HttpServletRequest req, HttpServletResponse res) throws Exception {
        log.info("微信平台退款回调");
        HashMap<Object, Object> resultMap = new HashMap<>();

        String body = HttpUtils.readData(req);
        Map fromJson = new Gson().fromJson(body, Map.class);
        String reqId = (String) fromJson.get("id");
        log.info("退款通知id：{}",reqId);
        log.info("退款通知完整信息：{}",body);

        //验证微信发送过来的req 签名的验证
        WechatPay2ValidatorForRequest validator = new WechatPay2ValidatorForRequest(verifier, reqId,body);
        if (!validator.validate(req)) {
            log.error("通知验签失败");
            //失败应答
            res.setStatus(500);
            resultMap.put("code", "ERROR");
            resultMap.put("message", "通知验签失败");
            return new Gson().toJson(resultMap);
        }
        //处理退款单
        processRefund(fromJson);
        res.setStatus(200);
        resultMap.put("code","SUCCESS");
        resultMap.put("message","成功");
        return new Gson().toJson(resultMap);
    }

    @Override
    public Map queryRefundByNo(String refundNo) throws IOException {
        log.info("退款订单回查：{}",refundNo);
        //请求URL
        String url=wxPayConfig.getDomain().concat(String.format(WxApiType.DOMESTIC_REFUNDS_QUERY.getType(),refundNo));
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Accept", "application/json");

        //完成签名并执行请求
        CloseableHttpResponse response = wxPayClient.execute(httpGet);
        try {
            int statusCode = response.getStatusLine().getStatusCode();
            String bodyStr = EntityUtils.toString(response.getEntity());
            if (statusCode == 200) {
                log.info("success,return body = {}" , bodyStr);
            } else {
                log.info("failed,resp code = {},return body = {}",statusCode, bodyStr);
                throw new IOException("request failed");
            }
            Map fromMap = new Gson().fromJson(bodyStr, Map.class);
            String tradeState = (String) fromMap.get("status");
            //查询本地数据库退款状态
//            String refundStatus = refundInfoService.getRefundStats(refundNo);
//            if(tradeState.equals(WxRefundStatus.SUCCESS.getType()) && refundStatus.equals(WxRefundStatus.SUCCESS.getType())){
//                return R.ok(refund);
//            }
            return fromMap;
        } finally {
            response.close();
        }
    }

    private String decryptFromResource(Map<String, Object> bodyMap) throws Exception{
        log.info("密文解密");
        Map<String,String> resourceMap = (Map)bodyMap.get("resource");
        String ciphertext = resourceMap.get("ciphertext");
        String nonce = resourceMap.get("nonce");
        String associatedData = resourceMap.get("associated_data");
        log.info("密文：{}",ciphertext);
        AesUtil aesUtil = new AesUtil(wxPayConfig.getApiV3Key().getBytes(StandardCharsets.UTF_8));
        String plainText = aesUtil.decryptToString(associatedData.getBytes(StandardCharsets.UTF_8)
                , nonce.getBytes(StandardCharsets.UTF_8), ciphertext);
        log.info("明文：{}",plainText);
        return plainText;
    }
    public void processRefund(Map bodyMap) throws Exception {
        log.info("处理订单");
        String plainText = decryptFromResource(bodyMap);
        Map<String, String> plainTextMap = new Gson().fromJson(plainText, HashMap.class);
        String orderNo = plainTextMap.get("out_trade_no"); //订单号
        String refundStatus = plainTextMap.get("refund_status"); //退款号

        if(lock.tryLock()){
            try {
//                //判断该订单是否重复处理
//                String orderStatus=orderInfoService.getOrderStatus(orderNo);
//                if(!OrderStatus.REFUND_PROCESSING.getType().equals(orderStatus)){
//                    return;
//                }
//                //更新订单
//                if(refundStatus.equals(WxRefundStatus.SUCCESS.getType())){
//                    orderInfoService.updateStatusByOrderNo(orderNo,OrderStatus.REFUND_SUCCESS);
//                }else if(refundStatus.equals(WxRefundStatus.ABNORMAL.getType())){
//                    orderInfoService.updateStatusByOrderNo(orderNo,OrderStatus.REFUND_ABNORMAL);
//                }
//                //同步退款单记录
//                refundInfoService.updateRefund(plainTextMap);
            }finally {
                lock.unlock();
            }
        }


    }
}
