package com.shop.service;

import com.alibaba.fastjson.JSONObject;
import com.shop.common.Result;
import com.shop.dto.Captcha;
import com.shop.dto.EmailRegisterDto;
import com.shop.dto.LoginDTO;
import com.shop.dto.user.LoginUserInfo;
import com.shop.entity.SysUser;
import com.shop.vo.QrLoginStateVo;
import com.shop.vo.QrLoginVo;
import com.shop.vo.user.SysUserVo;
import me.zhyd.oauth.model.AuthCallback;
import org.springframework.web.context.request.async.DeferredResult;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface AuthService {

    /**
     * 用户登录
     */
    LoginUserInfo login(LoginDTO loginDTO);

    /**
     * 获取当前登录用户信息
     */
    SysUserVo getLoginUserInfo(String source);

    /**
     * 用户更新
     */
    Boolean updateProfile(SysUser user);

    /**
     * 发送注册邮箱验证码
     * @param email
     * @param type 1:注册 2:重置密码
     * @return
     */
    String sendEmailCode(String email, Integer type) throws MessagingException;

    /**
     * 邮箱账号注册
     * @param dto
     * @return
     */
    Boolean register(EmailRegisterDto dto);

    /**
     * 邮箱账号重置密码
     * @param dto
     * @return
     */
    Boolean forgot(EmailRegisterDto dto);

    /**
     *  获取微信扫码登录验证码
     * @return
     */
    String getWechatLoginCode();


    /**
     * 验证微信是否扫码登录
     * @param loginCode
     * @return
     */
    LoginUserInfo getWechatIsLogin(String loginCode);

    /**
     * 微信公众号登录
     * @param message
     * @return
     */
//    String wechatLogin(WxMpXmlMessage message);

    /**
     * 获取第三方授权地址
     * @param source
     * @return
     */
    String renderAuth(String source);

    /**
     * 第三方授权登录
     * @param source
     * @param httpServletResponse
     */
    void authLogin(AuthCallback callback,String source, HttpServletResponse httpServletResponse) throws IOException;

    /**
     * 小程序登录
     * @param code
     * @return
     */
    LoginUserInfo appletLogin(String code, JSONObject userInfo);


    LoginUserInfo wechatAuthLogin(JSONObject userInfo);


    /**
     * 获取滑块验证码
     * @return
     */
    Captcha getCaptcha();

    /**
     * 生成扫码登录二维码（1 分钟有效）
     */
    QrLoginVo generateQrLogin();

    /**
     * 长轮询扫码登录状态
     *
     * <p>状态没变化时把请求挂起，直到 App 扫码/确认或超时才返回。
     * 返回 {@code CONFIRMED} 时同时把该 code 消费掉（一次性）。
     */
    DeferredResult<Result<QrLoginStateVo>> pollQrLogin(String code);

    /**
     * App 扫码（进入"待确认"状态）
     */
    QrLoginStateVo scanQrLogin(String code);

    /**
     * App 确认登录，返回该次登录的用户信息（含 token）
     */
    LoginUserInfo confirmQrLogin(String code);

    /**
     * App 取消登录
     */
    void cancelQrLogin(String code);
}
