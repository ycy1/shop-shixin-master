package com.shop.controller;


import com.shop.dto.user.LoginUserInfo;
import com.shop.utils.LoginUserUtils;

/**
 * @author xxj
 * @title BaseCommController
 * @date 2025/8/4 11:10
 * @description TODO 公共接口父类  /api/comm
 */

// WebConfig.configurePathMatch 中指定所有继承 BaseMpController 的类添加前缀
public class BaseCommController {
    // 其他公共接口实现...  super.getLoginUserInfo();

    /**
     * 当前登录账号（未登录返回 null）。取值口径和 LoginUserUtils 是同一份，别在这再抄一遍强转：
     * session 里存的不是 LoginUserInfo（老的快照结构）时那边按 null 处理，这里也一样。
     *
     * 只有这种系统级的公共能力（登录态、数据范围）放这儿。业务判据（哪张单能动、钱怎么算）留在服务层：
     * 下单、退货这些动作还有服务层的第二个调用方（小程序支付走 WxPayServiceImpl 直接调 service），
     * 校验收在基类里会漏掉那一路。
     */
    public LoginUserInfo getLoginUserInfo() {
        return LoginUserUtils.getLoginUser();
    }
}
