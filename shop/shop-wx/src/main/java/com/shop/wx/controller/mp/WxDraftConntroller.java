package com.shop.wx.controller.mp;

import lombok.AllArgsConstructor;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.draft.WxMpDraftInfo;
import me.chanjar.weixin.mp.bean.draft.WxMpDraftList;
import org.springframework.web.bind.annotation.*;

/**
 * @author xxj
 * @title WxDraftConntroller
 * @date 2025/7/30 14:49
 * @description TODO 草稿管理 发布
 */
@AllArgsConstructor
@RestController
@RequestMapping("/wx/draft/{appid}")
public class WxDraftConntroller {
    private final WxMpService wxService;

    @PostMapping("/addDraft")
    public String addDraft(@PathVariable String appid) throws WxErrorException {
        return wxService.switchoverTo(appid).getDraftService().addDraft("测试","测试内容","M_3Aq3YsqIk4ptIhgeozIvHVemtyq7UQ8Qr24HLYn2BmBuHaDbhfDQpANJxthqmv");
    }

    @GetMapping("/getInfo")
    public WxMpDraftInfo getInfo(@PathVariable String appid) throws WxErrorException {
        return wxService.switchoverTo(appid).getDraftService().getDraft("M_3Aq3YsqIk4ptIhgeozIu4eO_4fgr1ZtxK9qyc7j6G7M6eQMCnA8zRMxS-qmTYf");
    }

    @GetMapping("/batchget")
    public WxMpDraftList batchget(@PathVariable String appid) throws WxErrorException {
        return wxService.switchoverTo(appid).getDraftService().listDraft(0, 20,0);
    }

    @GetMapping("/count")
    public Long count(@PathVariable String appid) throws WxErrorException {
        return wxService.switchoverTo(appid).getDraftService().countDraft();
    }

    @DeleteMapping("/delete")
    public Boolean delete(@PathVariable String appid) throws WxErrorException {
        return wxService.switchoverTo(appid).getDraftService().delDraft("");
    }

    /**
     * 发布能力
     * @param appid
     */
    @PostMapping("/publish")
    public String publish(@PathVariable String appid) throws WxErrorException {
        return wxService.switchoverTo(appid).getFreePublishService().submit("M_3Aq3YsqIk4ptIhgeozIu4eO_4fgr1ZtxK9qyc7j6G7M6eQMCnA8zRMxS-qmTYf");
    }
}
