package com.shop.api.controller.common;

import com.shop.common.Result;
import com.shop.controller.BaseCommController;
import com.shop.dto.park.ShopParkPhotoSubmitDto;
import com.shop.entity.ShopTrendyParkPhoto;
import com.shop.service.ShopParkPhotoSubmitService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author xxj
 * @title ShopParkPhotoCommonController
 * @description 园区照片投稿公共接口（web 和小程序两端共用的那一条动作：用户上传照片）
 */
@Api(tags = "trendy 园区照片投稿公共接口")
@RestController
@RequestMapping("/park/photo")
@RequiredArgsConstructor
public class ShopParkPhotoCommonController extends BaseCommController {

    private final ShopParkPhotoSubmitService shopParkPhotoSubmitService;

    /**
     * 投一张照片。
     *
     * 路径：POST /api/comm/park/photo（前端调 comm 要写双前缀，request 的 baseURL 会再叠一层 /api）。
     * 图不在这一条里传：文件走全站那一条 POST /api/file/uploadImage（头像、广场动态、评论图都是它），
     * 传回来的 URL 再递给这一条 —— 收文件、压 webp、挑存储平台那套判据不必再抄一遍。
     *
     * 要登录态（服务里自己取，取不到报「请先登录后再上传照片」），来源钉 2 用户上传、状态钉 0 待审核：
     * 投稿不会直接上墙，等运营在后台「园区照片」里点通过。
     */
    @PostMapping
    @ApiOperation(value = "上传园区照片（进审核队列，不会直接展示）")
    public Result<ShopTrendyParkPhoto> submit(@RequestBody ShopParkPhotoSubmitDto form) {
        return Result.success(shopParkPhotoSubmitService.submit(form));
    }
}
