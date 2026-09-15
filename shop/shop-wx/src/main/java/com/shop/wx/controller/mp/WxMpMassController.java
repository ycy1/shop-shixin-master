package com.shop.wx.controller.mp;

import lombok.AllArgsConstructor;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.WxMpMassOpenIdsMessage;
import me.chanjar.weixin.mp.bean.result.WxMpMassSendResult;
import me.chanjar.weixin.mp.bean.template.WxMpTemplate;
import me.chanjar.weixin.mp.bean.template.WxMpTemplateData;
import me.chanjar.weixin.mp.bean.template.WxMpTemplateMessage;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static me.chanjar.weixin.common.api.WxConsts.MassMsgType.TEXT;

/**
 * @author xxj
 * @title WxMpMassController
 * @date 2025/7/28 17:10
 * @description TODO 群发消息
 */
@AllArgsConstructor
@RestController
@RequestMapping("/wx/mass/{appid}")
public class WxMpMassController {
    private final WxMpService wxService;

    @PostMapping("/send")
    public String send(@PathVariable String appid) throws WxErrorException {
        WxMpMassOpenIdsMessage massMessage = new WxMpMassOpenIdsMessage();
        massMessage.setMsgType(TEXT);
        massMessage.setContent("消息内容");
        massMessage.getToUsers().add("oz_6lvqqXFdJIclcKVlXugnk5QtQ");
        WxMpMassSendResult massResult = wxService.switchoverTo(appid).getMassMessageService().massOpenIdsMessageSend(massMessage);
        return massResult.getMsgId();
    }

    /**
     * 模板消息
     */
    @GetMapping("/allTemplate")
    public List<WxMpTemplate> allTemplate(@PathVariable String appid) throws WxErrorException {
        return wxService.switchoverTo(appid).getTemplateMsgService().getAllPrivateTemplate();
    }

//    @PostMapping("/addTemplate")
//    public String addTemplate(@PathVariable String appid) throws WxErrorException {
//        return wxService.switchoverTo(appid).getTemplateMsgService().addTemplate("47123", Arrays.asList("时间", "地点", "金额"));
//    }

    @PostMapping("/sendTemplate")
    public String sendTemplate(@PathVariable String appid) throws WxErrorException {
        WxMpTemplateMessage templateMessage = WxMpTemplateMessage.builder()
                .toUser("oz_6lvqqXFdJIclcKVlXugnk5QtQ")
                .templateId("fP3uy3P8D_5iAnzLz9CDpK7-ZHBQnxlZoIxMwFRsotE")
                .url("www.baidu.com")
                .build();
        templateMessage.addData(new WxMpTemplateData("name01",  "测试", "#FF0000"));
        templateMessage.addData(new WxMpTemplateData("date01",  "2025-07-28", "#FF0000"));
        templateMessage.addData(new WxMpTemplateData("thing01", "上海", "#FF0000"));
        templateMessage.addData(new WxMpTemplateData("amount01", "100.00", "#FF0000"));
        return wxService.switchoverTo(appid).getTemplateMsgService().sendTemplateMsg(templateMessage);
    }

    @DeleteMapping("/deleteTemplate")
    public boolean deleteTemplate(@PathVariable String appid) throws WxErrorException {
        String templateId = "u2ljezAIgWUjOhoo8-cd1JUHpgM7-37myZbdoAvTHCM";
        return wxService.switchoverTo(appid).getTemplateMsgService().delPrivateTemplate(templateId);
    }





}
