package com.shop.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson.JSONObject;
import com.shop.common.Result;
import com.shop.dto.Captcha;
import com.shop.dto.EmailRegisterDto;
import com.shop.dto.LoginDTO;
import com.shop.entity.SysUser;
import com.shop.service.AuthService;
import com.shop.vo.QrLoginStateVo;
import com.shop.vo.QrLoginVo;
import com.shop.vo.user.SysUserVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import me.zhyd.oauth.model.AuthCallback;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import com.shop.dto.user.*;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequiredArgsConstructor
@Api(tags = "认证管理")
public class AuthController {

    private final AuthService authService;

    @RequestMapping("/api/auth/render/{source}")
    @ApiOperation(value = "获取第三方授权地址")
    public Result<String> renderAuth(HttpServletResponse response, @PathVariable String source) {
        return Result.success(authService.renderAuth(source));
    }

    @RequestMapping("/api/auth/callback/{source}")
    public void login(AuthCallback callback, @PathVariable String source, HttpServletResponse httpServletResponse) throws IOException {
        authService.authLogin(callback,source,httpServletResponse);
    }


    @ApiOperation(value = "用户登录")
    @PostMapping("/api/auth/login")
    public Result<LoginUserInfo> login(@Validated @RequestBody LoginDTO loginDTO) {
        return Result.success(authService.login(loginDTO));
    }

    @SaIgnore
    @ApiOperation(value = "获取滑块验证码")
    @GetMapping("/auth/getCaptcha")
    public Result<Captcha> getCaptcha() {
        return Result.success(authService.getCaptcha());
    }

    @ApiOperation(value = "用户登出")
    @PostMapping("/api/auth/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success(null);
    }

    @ApiOperation(value = "发送注册邮箱验证码")
    @GetMapping("/api/sendEmailCode")
    public Result<String> sendEmailCode(String email, Integer type) throws MessagingException {
        return Result.success(authService.sendEmailCode(email ,type));
    }

    @ApiOperation(value = "邮箱账号注册")
    @PostMapping("/api/email/register")
    public Result<Boolean> register(@RequestBody EmailRegisterDto dto){
        return Result.success(authService.register(dto));
    }

    @ApiOperation(value = "根据邮箱修改密码")
    @PostMapping("/api/email/forgot")
    public Result<Boolean> forgot(@RequestBody EmailRegisterDto dto){
        return Result.success(authService.forgot(dto));
    }

    @ApiOperation(value = "获取微信扫码登录验证码")
    @GetMapping("/api/wechat/getCode")
    public Result<String> getWechatLoginCode(){
        return Result.success(authService.getWechatLoginCode());
    }

    @ApiOperation(value = "获取微信扫码登录验证码")
    @GetMapping("/api/wechat/isLogin/{loginCode}")
    public Result<LoginUserInfo> getWechatIsLogin(@PathVariable String loginCode){
        return Result.success(authService.getWechatIsLogin(loginCode));
    }

    @ApiOperation(value = "微信小程序登录")
    @PostMapping("/api/wechat/appletLogin/{code}")
    public Result<LoginUserInfo> appletLogin(@PathVariable String code,@RequestBody JSONObject userInfo){
        return Result.success(authService.appletLogin(code,userInfo));
    }

    @ApiOperation(value = "微信授权登录")
    @PostMapping("/api/wechat/wechatAuthLogin")
    public Result<LoginUserInfo> wechatAuthLogin(@RequestBody JSONObject userInfo){
        return Result.success(authService.wechatAuthLogin(userInfo));
    }


    @GetMapping("/api/auth/info")
    public Result<SysUserVo> getUserInfo(@RequestParam(defaultValue = "admin") String source) {
        return Result.success(authService.getLoginUserInfo(source));
    }

    @PostMapping("/api/auth/updateProfile")
    public Result<Boolean> updateProfile(@RequestBody SysUser user) {
        return Result.success(authService.updateProfile(user));
    }

    @SaIgnore
    @ApiOperation(value = "生成扫码登录二维码")
    @GetMapping("/api/auth/qrcode/generate")
    public Result<QrLoginVo> generateQrLogin() {
        return Result.success(authService.generateQrLogin());
    }

    /**
     * 长轮询扫码登录状态。状态没变化时请求会挂起（最多 25 秒），
     * 所以前端这个请求的超时必须比 25 秒更长。
     */
    @SaIgnore
    @ApiOperation(value = "长轮询扫码登录状态")
    @GetMapping("/api/auth/qrcode/poll/{code}")
    public DeferredResult<Result<QrLoginStateVo>> pollQrLogin(@PathVariable String code) {
        return authService.pollQrLogin(code);
    }

    /**
     * 下面三个由 App 调用，走 /api/auth/** 的常规鉴权（SaTokenConfigure），
     * 即必须带上 App 自己的登录 token
     */
    @ApiOperation(value = "App 扫码（进入待确认）")
    @PostMapping("/api/auth/qrcode/scan/{code}")
    public Result<QrLoginStateVo> scanQrLogin(@PathVariable String code) {
        return Result.success(authService.scanQrLogin(code));
    }

    @ApiOperation(value = "App 确认登录，返回本次登录的用户信息")
    @PostMapping("/api/auth/qrcode/confirm/{code}")
    public Result<LoginUserInfo> confirmQrLogin(@PathVariable String code) {
        return Result.success(authService.confirmQrLogin(code));
    }

    @ApiOperation(value = "App 取消登录")
    @PostMapping("/api/auth/qrcode/cancel/{code}")
    public Result<Void> cancelQrLogin(@PathVariable String code) {
        authService.cancelQrLogin(code);
        return Result.success(null);
    }

}
