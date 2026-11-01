package com.shop.dto.park;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 园区照片投稿入参（用户在自己的账号下传一张图给某家园区）
 *
 * 三格里只有「哪张图」和「一句图注」是人说的：
 * 挂哪家园区的 IP、来源（钉死 2 用户上传）、上传人（登录态）、审核状态（钉死 0 待审核）、
 * 是不是精选、排序、标签（用户投稿不带标签，留 "[]"，运营审核时再补）一律由服务端定 ——
 * 让人自填 status=1 就等于自己给自己过审。
 */
@Data
@ApiModel(value = "园区照片投稿入参")
public class ShopParkPhotoSubmitDto {

    @ApiModelProperty(value = "园区ID(shop_trendy_park.id)", required = true)
    private Long parkId;

    @ApiModelProperty(value = "图片 URL（先走 POST /api/file/uploadImage 把图传上去，这一条不收文件）", required = true)
    private String imageUrl;

    @ApiModelProperty(value = "图注一句话，选填，最多 64 个字")
    private String description;
}
