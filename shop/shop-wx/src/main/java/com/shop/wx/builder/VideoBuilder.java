package com.shop.wx.builder;

import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutVideoMessage;

/**
 * @author <a href="https://github.com/binarywang">Binary Wang</a>
 */
public class VideoBuilder extends AbstractBuilder {

    @Override
    public WxMpXmlOutMessage build(String content, WxMpXmlMessage wxMessage,
                                   WxMpService service) {
        WxMpXmlOutVideoMessage m = WxMpXmlOutMessage.VIDEO().mediaId(content)
            .fromUser(wxMessage.getToUser()).toUser(wxMessage.getFromUser())
            .build();
        return m;
    }

}
