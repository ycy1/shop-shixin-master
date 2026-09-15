package com.shop.wx.service.impl;

import com.google.gson.Gson;
import com.shop.entity.SysOrder;
import com.shop.wx.config.WxPayConfig;
import com.shop.wx.enums.wxpay.WxApiType;
import com.shop.wx.enums.wxpay.WxNotifyType;
import com.shop.wx.enums.wxpay.WxTradeState;
import com.shop.wx.service.OrderWxService;
import com.shop.wx.service.WxPayService;
import com.shop.wx.utils.HttpUtils;
import com.shop.wx.utils.WechatPay2ValidatorForRequest;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author xxj
 * @title WxPayServiceImpl
 * @date 2026/9/14 11:21
 * @description TODO
 */
@Service
@Slf4j
@AllArgsConstructor
public class WxPayServiceImpl implements WxPayService {

    private final WxPayConfig wxPayConfig;
    private final CloseableHttpClient wxPayClient;
    private final Verifier verifier;
    private final ReentrantLock lock = new ReentrantLock();

    private final OrderWxService sysOrderWxService;

//    @Resource
//    @Qualifier("wxPayNoSignClient")
//    private CloseableHttpClient wxPayNoSignClient;

    @Override
    public Map<String, Object> jsapiPay(SysOrder sysOrder) throws IOException {
        SysOrder orderInfo = sysOrderWxService.createOrder(sysOrder);
        log.info("调用统一下单接口API");
        //请求URL
        HttpPost httpPost = new HttpPost(wxPayConfig.getDomain().concat(WxApiType.JSAPI_PAY.getType()));
        // 请求body参数
        HashMap<Object, Object> paramsMap = new HashMap<>();
        paramsMap.put("appid",wxPayConfig.getAppid());
        paramsMap.put("mchid",wxPayConfig.getMchId());
        paramsMap.put("description",orderInfo.getOrderNo());
        paramsMap.put("out_trade_no",orderInfo.getOrderNo());
        paramsMap.put("notify_url",wxPayConfig.getNotifyDomain().concat(WxNotifyType.NATIVE_NOTIFY.getType()));

        HashMap<Object, Object> amountMap = new HashMap<>();
        amountMap.put("total",orderInfo.getTotalAmount().multiply(new BigDecimal(100)).intValue());
        amountMap.put("currency","CNY");
        paramsMap.put("amount",amountMap);
        String paramsJson = new Gson().toJson(paramsMap);

        log.info("请求参数：{}" ,paramsJson);
        StringEntity entity = new StringEntity(paramsJson,"utf-8");
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
            Map fromMap = new Gson().fromJson(bodyStr, Map.class);
            Object prepay_id = fromMap.get("prepay_id");
//            orderInfo.setCodeUrl(codeUrl.toString());
//            orderInfoService.saveCodeurl(orderInfo);
            //返回map
            Map<String,Object> result=new HashMap<>();
            result.put("prepay_id",prepay_id);
            result.put("orderNo",orderInfo.getOrderNo());
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            response.close();
//            wxPayClient.close();
        }
    }

    @Override
    public String jsapiNotify(HttpServletRequest req, HttpServletResponse res) throws Exception {

        HashMap<Object, Object> resultMap = new HashMap<>();

        String body = HttpUtils.readData(req);
        Map fromJson = new Gson().fromJson(body, Map.class);
        String reqId = (String) fromJson.get("id");
        log.info("支付通知id：{}",reqId);
        log.info("支付通知完整信息：{}",body);

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
        //订单后续的处理
        processOrder(fromJson);
        res.setStatus(200);
        resultMap.put("code","SUCCESS");
        resultMap.put("message","成功");
        return new Gson().toJson(resultMap);
    }

    @Override
    public Map queryOrderByNo(String orderNo) throws IOException {
        log.info("订单回查：{}",orderNo);
        //请求URL
        String url=wxPayConfig.getDomain().concat(String.format(WxApiType.ORDER_QUERY_BY_NO.getType(),orderNo));
        HttpGet httpGet = new HttpGet(url);

//        StringEntity entity = new StringEntity("","utf-8");
//        entity.setContentType("application/json");
//        httpGet.setEntity(entity);
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
            return fromMap;
        } finally {
            response.close();
//            wxPayClient.close();
        }
    }

    @Override
    public void cancel(String orderNo) throws IOException {
        log.info("订单取消：{}",orderNo);
        //请求URL
        String url=wxPayConfig.getDomain().concat(String.format(WxApiType.CLOSE_ORDER_BY_NO.getType(),orderNo));
        log.info("订单关闭：{}，url：{}",orderNo,url);
        HttpPost httpPost = new HttpPost(url);
        //请求body
        HashMap<Object, Object> paramsMap = new HashMap<>();
        paramsMap.put("mchid",wxPayConfig.getMchId());
        StringEntity entity = new StringEntity(new Gson().toJson(paramsMap),"utf-8");
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");

        //完成签名并执行请求
        CloseableHttpResponse response = wxPayClient.execute(httpPost);
        try {
            int statusCode = response.getStatusLine().getStatusCode();
//            String bodyStr = EntityUtils.toString(response.getEntity());
            if (statusCode == 204) {
                log.info("success");
            } else {
                log.info("failed,resp code = {}",statusCode);
                throw new IOException("request failed");
            }

        } finally {
            response.close();
//            wxPayClient.close();
        }
    }


    public void processOrder(Map<String, Object> bodyMap) throws Exception {
        log.info("处理订单");
        String event_type = (String) bodyMap.get("event_type"); // 事件类型

        String plainText = decryptFromResource(bodyMap);
        Map<String, Object> plainTextMap = new Gson().fromJson(plainText, HashMap.class);
        String orderNo = (String)plainTextMap.get("out_trade_no"); //订单号
        String trade_state = (String)plainTextMap.get("trade_state"); // 交易状态
        if (WxTradeState.SUCCESS.getType().equals(trade_state)){
            log.info("支付成功");
        }

        if(lock.tryLock()){
            try {
                //判断该订单是否重复处理
//                String orderStatus=orderInfoService.getOrderStatus(orderNo);
//                if(!OrderStatus.NOTPAY.getType().equals(orderStatus)){
//                    return;
//                }
//                //更新订单
//                orderInfoService.updateStatusByOrderNo(orderNo,OrderStatus.SUCCESS);
//                //添加支付记录
//                paymentInfoService.createPaymentInfo(plainText);
            }finally {
                lock.unlock();
            }
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
}
