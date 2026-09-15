package com.shop.wx.controller.mp;

import com.shop.common.Result;
import lombok.AllArgsConstructor;
import me.chanjar.weixin.common.bean.result.WxMediaUploadResult;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.material.*;
import org.apache.commons.io.FileUtils;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static me.chanjar.weixin.common.api.WxConsts.MediaFileType.IMAGE;
import static me.chanjar.weixin.common.api.WxConsts.MediaFileType.VIDEO;


/**
 * @author xxj
 * @title WxMediaController
 * @date 2025/7/28 17:49
 * @description TODO 素材管理
 */
@AllArgsConstructor
@RestController
@RequestMapping("/wx/media/{appid}")
public class WxMediaController {
    private final WxMpService wxService;

    // 上传临时 素材
    @PostMapping("/uploadTempMedia")
    public Result<String> upload(@PathVariable String appid) throws WxErrorException {
        WxMediaUploadResult result = wxService.switchoverTo(appid).getMaterialService().mediaUpload(
            "image",
            new File("C:\\Users\\Lenovo\\Desktop\\test\\lj.png"));
        return Result.success(result.getMediaId());
    }

    // 下载临时素材
    @GetMapping("/getTempMedia")
    public String download(@PathVariable String appid) throws WxErrorException, IOException {
        File out = new File("C:\\Users\\Lenovo\\Desktop\\test\\22.png");
        File file = wxService.switchoverTo(appid).getMaterialService().mediaDownload("QdM0Jeneg5kcvFM61W-aXfjkgIPoqsHpgh2JbDcE1DgKkrAMGzn5ZcObmvgKxR0T");
        FileCopyUtils.copy(file, out);
        FileUtils.delete(file);
        return out.getPath();
    }

    // 上传永久素材 微信服务器会对其进行压缩
    @PostMapping("/addMaterial")
    public Result<WxMpMaterialUploadResult> addMaterial(@PathVariable String appid) throws WxErrorException {
        WxMpMaterialUploadResult result = wxService.switchoverTo(appid).getMaterialService().materialFileUpload(
            VIDEO, new WxMpMaterial("sp.mp4", new File("C:\\Users\\Lenovo\\Desktop\\test\\sp.mp4"), "抖音视频", "视频简介"));
        return Result.success(result);
    }

    // 下载永久素材
    @GetMapping("/materialImageOrVoiceDownload")
    public String materialImageOrVoiceDownload(@PathVariable String appid) throws WxErrorException, IOException {
        String mediaId = "M_3Aq3YsqIk4ptIhgeozIvHVemtyq7UQ8Qr24HLYn2BmBuHaDbhfDQpANJxthqmv";
        InputStream result = wxService.switchoverTo(appid).getMaterialService().materialImageOrVoiceDownload(mediaId);
        File out = new File("C:\\Users\\Lenovo\\Desktop\\test\\33.png");
        FileUtils.copyInputStreamToFile(result, out);
        return out.getPath();
    }

    // 获取永久视频素材
    @GetMapping("/materialVideoInfo")
    public Result<WxMpMaterialVideoInfoResult> materialVideoInfo(@PathVariable String appid) throws WxErrorException {
        String mediaId = "M_3Aq3YsqIk4ptIhgeozIpXWBNJ9t2GUsB4qa2vxjDL9kqBHJxoEIZ2V1v4SwWSn";
        WxMpMaterialVideoInfoResult result = wxService.switchoverTo(appid).getMaterialService().materialVideoInfo(mediaId);
        return Result.success(result);
    }

    // 获取永久图文素材
    @GetMapping("/materialNewsInfo")
    public Result<WxMpMaterialNews> materialNewsInfo(@PathVariable String appid) throws WxErrorException {
        String mediaId = "M_3Aq3YsqIk4ptIhgeozIpXWBNJ9t2GUsB4qa2vxjDL9kqBHJxoEIZ2V1v4SwWSn";
        WxMpMaterialNews result = wxService.switchoverTo(appid).getMaterialService().materialNewsInfo(mediaId);
        return Result.success(result);
    }

    // 素材总数
    @GetMapping("/materialCount")
    public Result<WxMpMaterialCountResult> materialCount(@PathVariable String appid) throws WxErrorException {
        WxMpMaterialCountResult result = wxService.switchoverTo(appid).getMaterialService().materialCount();
        return Result.success(result);
    }

    // 批量获取永久素材
    @GetMapping("/materialFileBatchGet")
    public Result<WxMpMaterialFileBatchGetResult> materialFileBatchGet(@PathVariable String appid) throws WxErrorException {
        WxMpMaterialFileBatchGetResult result = wxService.switchoverTo(appid).getMaterialService().materialFileBatchGet(IMAGE, 0, 20);
        return Result.success(result);
    }

    // 批量获取永久图文素材
    @GetMapping("/materialNewsBatchGet")
    public Result<WxMpMaterialNewsBatchGetResult> materialNewsBatchGet(@PathVariable String appid) throws WxErrorException {
        WxMpMaterialNewsBatchGetResult result = wxService.switchoverTo(appid).getMaterialService().materialNewsBatchGet( 0, 20);
        return Result.success(result);
    }

    // 删除永久素材
    @DeleteMapping("/materialDelete")
    public Result<Boolean> materialDelete(@PathVariable String appid) throws WxErrorException {
        String mediaId = "M_3Aq3YsqIk4ptIhgeozIpXWBNJ9t2GUsB4qa2vxjDL9kqBHJxoEIZ2V1v4SwWSn";
        boolean result = wxService.switchoverTo(appid).getMaterialService().materialDelete(mediaId);
        return Result.success(result);
    }






}
