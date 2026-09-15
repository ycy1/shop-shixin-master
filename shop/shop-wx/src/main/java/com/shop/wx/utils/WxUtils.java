package com.shop.wx.utils;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author xxj
 * @title WxUtils
 * @date 2026/2/1 16:50
 * @description TODO
 */
public class WxUtils {
    public static void main(String[] args) {
        String html = "<section style=\"font-family: -apple-system-font, BlinkMacSystemFont, Helvetica Neue, PingFang SC, Hiragino Sans GB, Microsoft YaHei UI, Microsoft YaHei, Arial, sans-serif; font-size: 17px; line-height: 1.8; color: #333; max-width: 100%; word-break: break-word; overflow-wrap: break-word; text-align: justify;\">\n" +
                "\t<h2 style=\"font-size: 20px; font-weight: bold; margin: 25px 0 12px; padding-left: 10px; border-left: 4px solid #07c160; line-height: 1.4; color: #000000;\">小程序端</h2>\n" +
                "\t<ul style=\"margin: 15px 0; padding-left: 30px;\">\n" +
                "\t\t<li style=\"margin-bottom: 8px; line-height: 1.7;\">open-type=\"chooseAvatar\" 调起微信头像事件</li>\n" +
                "\t</ul>\n" +
                "\t<div style=\"position: relative; margin: 20px 0;\">\n" +
                "\t\t<div style=\"position: absolute; top: 0; right: 0; background: #07c160; color: white; padding: 3px 10px; font-size: 12px; border-radius: 0 0 0 4px;\">html</div>\n" +
                "\t\t<pre style=\"display: block; overflow-x: auto; padding: 0.5em; color: #24292e; background: #f6f8fa; font-family: SFMono-Regular, Consolas, Liberation Mono, Menlo, Courier, monospace; font-size: 12px; line-height: 0.5;\">\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t&lt;button class=\"bottom_btn text-[#4CAF50];\" open-type=\"chooseAvatar\" @chooseavatar=\"onChooseAvatar\"&gt;\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t&lt;i class=\"icon-weixin iconfont text-5xl flex justify-center items-center h-full\" /&gt;\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t&lt;/button&gt;\n" +
                "\t\t\t</code>\n" +
                "\t\t</pre>\n" +
                "\t</div>\n" +
                "\t<ul style=\"margin: 15px 0; padding-left: 30px;\">\n" +
                "\t\t<li style=\"margin-bottom: 8px; line-height: 1.7;\">调用uni.login获取code</li>\n" +
                "\t</ul>\n" +
                "\t<div style=\"position: relative; margin: 20px 0;\">\n" +
                "\t\t<div style=\"position: absolute; top: 0; right: 0; background: #07c160; color: white; padding: 3px 10px; font-size: 12px; border-radius: 0 0 0 4px;\">js</div>\n" +
                "\t\t<pre style=\"display: block; overflow-x: auto; padding: 0.5em; color: #24292e; background: #f6f8fa; font-family: SFMono-Regular, Consolas, Liberation Mono, Menlo, Courier, monospace; font-size: 12px; line-height: 0.5;\">\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t/**\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t* 获取微信登录凭证\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t* @returns Promise 包含微信登录凭证(code)\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t*/\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\texport function getWxCode() {\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\treturn new Promise&lt;UniApp.LoginRes&gt;((resolve, reject) =&gt; {\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tuni.login({\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tprovider: 'weixin',\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tsuccess: res =&gt; resolve(res),\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tfail: err =&gt; reject(new Error(err)),\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t})\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t})\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t}\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t/**\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t* 微信登录\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t*/\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tconst wxLogin = async (userInfo) =&gt; {\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tlet res = null;  // 后台返回的登录信息\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t// #ifdef MP-WEIXIN\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t// 获取微信小程序登录的code\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tconst data = await getWxCode()\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t// 自定义后端请求 这里Java实现\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tres = await _wxAppletLogin(data.code, userInfo)\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t// #endif\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\ttoast.success('登录成功')\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t// console.log('后台授权信息', res)\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t// 登录成功后缓存用户信息\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tawait getUserInfo(res)\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tif (res.data.token) {\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tuni.reLaunch({\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\turl: '/pages/user/index'\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t})\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t}\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\treturn res\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t}\n" +
                "\t\t\t</code>\n" +
                "\t\t</pre>\n" +
                "\t</div>\n" +
                "\t<ul style=\"margin: 15px 0; padding-left: 30px;\">\n" +
                "\t\t<li style=\"margin-bottom: 8px; line-height: 1.7;\">调用微信服务器拿到userInfo</li>\n" +
                "\t</ul>\n" +
                "\t<div style=\"position: relative; margin: 20px 0;\">\n" +
                "\t\t<div style=\"position: absolute; top: 0; right: 0; background: #07c160; color: white; padding: 3px 10px; font-size: 12px; border-radius: 0 0 0 4px;\">java</div>\n" +
                "\t\t<pre style=\"display: block; overflow-x: auto; padding: 0.5em; color: #24292e; background: #f6f8fa; font-family: SFMono-Regular, Consolas, Liberation Mono, Menlo, Courier, monospace; font-size: 12px; line-height: 0.5;\">\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tpublic LoginUserInfo appletLogin(String code, JSONObject userInfo) {\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tString url = \"https://api.weixin.qq.com/sns/jscode2session?appid=\" + wechatProperties.getAppletAppId()\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t+ \"&amp;secret=\" + wechatProperties.getAppletSecret() + \"&amp;js_code=\" + code + \"&amp;grant_type=authorization_code\";\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tString result = HttpUtil.get(url);\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tcom.alibaba.fastjson.JSONObject jsonObject = JSON.parseObject(result);\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tString openid = jsonObject.getString(\"openid\");\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tif (openid == null) {\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tthrow new ServiceException(\"登录失败\");\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t}\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t/************************* 自定义逻辑 ****************************************/\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t// 查询用户\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tSysUser user = userMapper.selectByUsername(openid);\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tif (user == null) {\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tString ip = IpUtil.getIp();\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tString avatar = avatarList[(int) (Math.random() * avatarList.length)];\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tuser = SysUser.builder()\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t.username(openid)\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t.openid(openid)\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t.password(UUID.randomUUID().toString())\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t.loginType(LoginTypeEnum.APPLET.getType())\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t.lastLoginTime(LocalDateTime.now())\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t.ipLocation(IpUtil.getIp2region(ip))\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t.ip(ip)\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t.status(Constants.YES)\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t.nickname(\"Wechat_\"+getRandomString(6))\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t.avatar(userInfo.getString(\"avatarUrl\"))\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t.sex(0)\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t.build();\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tuserMapper.insert(user);\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t//添加用户角色信息\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tthis.insertRole(user);\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t}else {\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tif (user.getStatus() == Constants.NO) {\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tthrow new ServiceException(\"账号已被禁用，请联系管理员\");\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t}\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t}\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t// 构建后端Token\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tLoginUserInfo loginUserInfo = BeanCopyUtil.copyObj(user, LoginUserInfo.class);\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tStpUtil.login(loginUserInfo.getId());\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tStpUtil.getSession().set(Constants.CURRENT_USER, loginUserInfo);\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tloginUserInfo.setToken(StpUtil.getTokenValue());\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\treturn loginUserInfo;\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t}\n" +
                "\t\t\t</code>\n" +
                "\t\t</pre>\n" +
                "\t</div>\n" +
                "\t<h2 style=\"font-size: 20px; font-weight: bold; margin: 25px 0 12px; padding-left: 10px; border-left: 4px solid #07c160; line-height: 1.4; color: #000000;\">APP端</h2>\n" +
                "\t<ul style=\"margin: 15px 0; padding-left: 30px;\">\n" +
                "\t\t<li style=\"margin-bottom: 8px; line-height: 1.7;\">onGetUserInfo调起APP事件</li>\n" +
                "\t</ul>\n" +
                "\t<div style=\"position: relative; margin: 20px 0;\">\n" +
                "\t\t<div style=\"position: absolute; top: 0; right: 0; background: #07c160; color: white; padding: 3px 10px; font-size: 12px; border-radius: 0 0 0 4px;\">html</div>\n" +
                "\t\t<pre style=\"display: block; overflow-x: auto; padding: 0.5em; color: #24292e; background: #f6f8fa; font-family: SFMono-Regular, Consolas, Liberation Mono, Menlo, Courier, monospace; font-size: 12px; line-height: 0.5;\">\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t&lt;!-- #ifdef APP-PLUS --&gt;\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t&lt;button class=\"bottom_btn text-[#4CAF50]\" @click=\"onGetUserInfo\"&gt;\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t&lt;i class=\"icon-weixin iconfont text-5xl flex justify-center items-center h-full\" /&gt;\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t&lt;/button&gt;\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t&lt;!-- #endif --&gt;\n" +
                "\t\t\t</code>\n" +
                "\t\t</pre>\n" +
                "\t</div>\n" +
                "\t<ul style=\"margin: 15px 0; padding-left: 30px;\">\n" +
                "\t\t<li style=\"margin-bottom: 8px; line-height: 1.7;\">参考 [HTML5中国产业联盟](https://www.html5plus.org/doc/zh_cn/oauth.html#plus.oauth.getServices) plus.oauth 方法</li>\n" +
                "\t\t<li style=\"margin-bottom: 8px; line-height: 1.7;\">wxLogin(userInfo) 方法同上</li>\n" +
                "\t</ul>\n" +
                "\t<div style=\"position: relative; margin: 20px 0;\">\n" +
                "\t\t<div style=\"position: absolute; top: 0; right: 0; background: #07c160; color: white; padding: 3px 10px; font-size: 12px; border-radius: 0 0 0 4px;\">js</div>\n" +
                "\t\t<pre style=\"display: block; overflow-x: auto; padding: 0.5em; color: #24292e; background: #f6f8fa; font-family: SFMono-Regular, Consolas, Liberation Mono, Menlo, Courier, monospace; font-size: 12px; line-height: 0.5;\">\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tconst onGetUserInfo = () =&gt; {\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t// #ifdef APP-PLUS\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tconsole.log(\"协议\", check.value)\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tif (!check.value) {\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\ttoast.error('请先同意协议!')\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\treturn\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t}\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\ttry {\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\ttoast.loading(\"登录中...\")\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\toverlay.value = true\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tvar appid = plus.runtime.appid;\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tconsole.log('应用的 appid 为：' + appid);\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tvar weixinOauth = null;\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tplus.oauth.getServices(function (services) {\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tfor (var i in services) {\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tvar service = services[i];\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t// 获取微信登录对象\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tif (service.id == 'weixin') {\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tweixinOauth = service;\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tbreak;\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t}\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t}\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tweixinOauth.login(async function (res) {\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t// 授权成功，weixinOauth.authResult 中保存授权信息\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tlet userInfo = res.target.userInfo\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tconst dow = await http.download(userInfo.headimgurl)\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tconsole.log('下载成功', dow)\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tuserInfo.avatarUrl = await setUserAvatar(dow.tempFilePath)\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tconst result = await wxLogin(userInfo)\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tconsole.log(\"登录结果\", result)\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\ttoast.success(\"登录成功\")\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\toverlay.value = false\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t}, function (err) {\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t// 登录授权失败\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t// err.code是错误码\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t})\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t}, function (err) {\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t// 获取 services 失败\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t})\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t} catch (error) {\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\ttoast.close()\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\toverlay.value = false\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t}\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t// #endif\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t}\n" +
                "\t\t\t</code>\n" +
                "\t\t</pre>\n" +
                "\t</div>\n" +
                "\t<h2 style=\"font-size: 20px; font-weight: bold; margin: 25px 0 12px; padding-left: 10px; border-left: 4px solid #07c160; line-height: 1.4; color: #000000;\">获取微信昵称</h2>\n" +
                "\t<ul style=\"margin: 15px 0; padding-left: 30px;\">\n" +
                "\t\t<li style=\"margin-bottom: 8px; line-height: 1.7;\">参考官方 [获取头像昵称](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/userProfile.html)</li>\n" +
                "\t\t<li style=\"margin-bottom: 8px; line-height: 1.7;\">type=\"nickname\" 获取昵称事件</li>\n" +
                "\t</ul>\n" +
                "\t<div style=\"position: relative; margin: 20px 0;\">\n" +
                "\t\t<div style=\"position: absolute; top: 0; right: 0; background: #07c160; color: white; padding: 3px 10px; font-size: 12px; border-radius: 0 0 0 4px;\">html</div>\n" +
                "\t\t<pre style=\"display: block; overflow-x: auto; padding: 0.5em; color: #24292e; background: #f6f8fa; font-family: SFMono-Regular, Consolas, Liberation Mono, Menlo, Courier, monospace; font-size: 12px; line-height: 0.5;\">\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\t&lt;wd-input label=\"昵称\" type=\"nickname\" label-width=\"100px\" prop=\"nickname\" clearable v-model=\"model.nickname\"\n" +
                "\t\t\t</code>\n" +
                "\t\t\t<code style=\"display: flex; position: relative; white-space:nowrap;line-height: 1.5;letter-spacing: 0.05em;word-spacing: 0.1em;font-family:inherit;\">\n" +
                "\t\t\t\tplaceholder=\"请输入昵称\" :rules=\"[{ required: true, message: '请填写昵称' }]\" /&gt;\n" +
                "\t\t\t</code>\n" +
                "\t\t</pre>\n" +
                "\t</div>\n" +
                "\t<h2 style=\"font-size: 20px; font-weight: bold; margin: 25px 0 12px; padding-left: 10px; border-left: 4px solid #07c160; line-height: 1.4; color: #000000;\">效果</h2>\n" +
                "\t<img myType=\"wxImg\" src=\"http://182.92.85.80/group1/M00/00/04/tlxVUGjIH8KAbsnSAAAcnPr3KPc98.webp\" title=\"获取微信头像\" alt=\"image.png\" style=\"max-width: 100%; height: auto; display: block; margin: 20px auto;\">\n" +
                "\t<img myType=\"wxImg\" src=\"http://182.92.85.80/group1/M00/00/04/tlxVUGjIIBSAQDYWAAAqUCGcKC072.webp\" title=\"获取微信昵称\" alt=\"image.png\" style=\"max-width: 100%; height: auto; display: block; margin: 20px auto;\">\n" +
                "\t\t\t<p style=\"margin: 0 0 15px; text-align: justify;\">End</p>\n" +
                "\t\t</section>";

        List<JsonObject> imageSrc = getImageSrc(html);
        System.out.println(imageSrc);
    }


    /**
     * 获取图片src
     * @param html
     * @return
     */
    public static List<JsonObject> getImageSrc(String html) {
        if (html == null) return new ArrayList<>();
        List<JsonObject> srcList = new ArrayList<>();
        String pattern = "<img[^>]*myType\\s*=\\s*[\"']wxImg[\"'][^>]*>";
        Pattern r = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        Matcher m = r.matcher(html);
        while (m.find()) {
            JsonObject jsonObject = new JsonObject();
            String result = m.group();
            jsonObject.addProperty("img", result);
            // 获取src
            String srcPattern = "src\\s*=\\s*[\"']([^\"']+)[\"']";
            Pattern srcRegex = Pattern.compile(srcPattern, Pattern.CASE_INSENSITIVE);
            Matcher srcMatcher = srcRegex.matcher(result);
            if (srcMatcher.find()) {
                jsonObject.addProperty("url", srcMatcher.group(1));
            }
            // 获取title
            String titlePattern = "title\\s*=\\s*[\"']([^\"']+)[\"']";
            Pattern titleRegex = Pattern.compile(titlePattern, Pattern.CASE_INSENSITIVE);
            Matcher titleMatcher = titleRegex.matcher(result);
            if (titleMatcher.find()) {
                jsonObject.addProperty("title", titleMatcher.group(1));
                jsonObject.addProperty("name", titleMatcher.group(1));
            }
            // 获取alt
            String altPattern = "alt\\s*=\\s*[\"']([^\"']+)[\"']";
            Pattern altRegex = Pattern.compile(altPattern, Pattern.CASE_INSENSITIVE);
            Matcher altMatcher = altRegex.matcher(result);
            if (altMatcher.find()) {
                jsonObject.addProperty("introduction", altMatcher.group(1));
            }
            srcList.add(jsonObject);
        }
        return srcList;
    }
}
