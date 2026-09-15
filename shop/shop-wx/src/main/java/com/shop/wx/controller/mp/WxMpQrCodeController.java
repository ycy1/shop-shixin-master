package com.shop.wx.controller.mp;

import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.result.WxMpQrCodeTicket;
import me.chanjar.weixin.mp.enums.WxMpApiUrl;
import org.apache.commons.io.FileUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;

/**
 * @author xxj
 * @title WxMpQrCodeController
 * @date 2025/7/29 16:12
 * @description TODO 二维码 短链接
 */
@AllArgsConstructor
@RestController
@RequestMapping("/wx/qrcode/{appid}")
public class WxMpQrCodeController {
    private final WxMpService wxService;

    @GetMapping("/createQr")
    public WxMpQrCodeTicket createQr(@PathVariable String appid) throws Exception {
        return wxService.switchoverTo(appid).getQrcodeService().qrCodeCreateTmpTicket("1", 1000);
    }

    @GetMapping("/qrCodeCreateLastTicket")
    public WxMpQrCodeTicket qrCodeCreateLastTicket(@PathVariable String appid) throws Exception {
        return wxService.switchoverTo(appid).getQrcodeService().qrCodeCreateLastTicket("1");
    }

    @GetMapping("/qrCodePicture")
    public String qrCodePicture(@PathVariable String appid) throws Exception {
        WxMpQrCodeTicket wxMpQrCodeTicket = new WxMpQrCodeTicket();
        wxMpQrCodeTicket.setTicket("gQGH8TwAAAAAAAAAAS5odHRwOi8vd2VpeGluLnFxLmNvbS9xLzAyYnBwbllzZi1hQi0xR3A5OE5FY2MAAgSxhYhoAwToAwAA");
        File out = new File("C:\\Users\\Lenovo\\Desktop\\test\\out.png");
        File file = wxService.switchoverTo(appid).getQrcodeService().qrCodePicture(wxMpQrCodeTicket);
        FileUtils.copyFile(file, out);
        FileUtils.delete(file);
        return  out.getPath();
    }

    // 短链接
    @PostMapping("/genShortGen")
    public String genShortGen(@PathVariable String appid) throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("long_data", "svsvdsvsdvdfv222SVDSF是DVD地方v");
        json.addProperty("expire_seconds", 10000);
        return wxService.switchoverTo(appid).post(WxMpApiUrl.Other.GEN_SHORTEN_URL, json.toString());
    }

    // 短链接解析
    @GetMapping("/genShortFetch")
    public String genShortFetch(@PathVariable String appid) throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("short_key", "vHvietWWgauLsXH");
        return wxService.switchoverTo(appid).post(WxMpApiUrl.Other.FETCH_SHORTEN_URL, json.toString());
    }

}
