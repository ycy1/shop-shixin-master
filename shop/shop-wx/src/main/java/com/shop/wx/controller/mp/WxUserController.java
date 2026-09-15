package com.shop.wx.controller.mp;

import com.shop.controller.BaseMpController;
import lombok.AllArgsConstructor;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.result.WxMpUser;
import me.chanjar.weixin.mp.bean.result.WxMpUserList;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author xxj
 * @title WxUserController
 * @date 2025/7/29 15:37
 * @description TODO 用户管理
 */
@AllArgsConstructor
@RestController
@RequestMapping(path = "/user/{appid}")
public class WxUserController extends BaseMpController {
    private final WxMpService wxService;

    @GetMapping("/userList")
    public WxMpUserList userList(@PathVariable String appid) throws Exception {
        return wxService.switchoverTo(appid).getUserService().userList();
    }

    @GetMapping("/userInfo")
    public WxMpUser userInfo(@PathVariable String appid) throws Exception {
        return wxService.switchoverTo(appid).getUserService().userInfo("oz_6lvqqXFdJIclcKVlXugnk5QtQ");
    }



}
