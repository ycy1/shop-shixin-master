package com.shop.service;

import com.shop.dto.park.ShopParkPhotoSubmitDto;
import com.shop.entity.ShopTrendyParkPhoto;

/**
 * 园区照片投稿（用户上传那一路，两端共用的那一条动作）
 *
 * 为什么收在 shop-commom：这一条和出票、抽盒是同一个形状 —— 落的都是「一张图挂到某家园区名下、
 * 排在审核队列里」那一行，web 和小程序两端点的都是同一个接口，判据只该有一份。
 * 后台「园区照片」那一页走的是另一条（shop-admin 的 ShopTrendyParkPhotoService）：那是运营自己加图，
 * 默认来源 1 官方上传、默认审核通过，和这里的「钉死 2 用户上传 + 0 待审核」正好是两套默认档，
 * 所以两条各写各的入口，共用的只有实体上那几格长度上限和状态/来源取值。
 *
 * 投稿进来的图**不会**直接出现在前台：详情页那圈和「全部照片」那一页都只读 status=1 通过的那几行，
 * 要等运营在后台点「通过」。
 */
public interface ShopParkPhotoSubmitService {

    /**
     * 投一张照片：谁投的按登录态记，状态钉 0 待审核。
     *
     * @param dto 哪家园区、哪张图、一句图注
     * @return 落下来的那一行（含 id；页面只用来弹「已提交，等审核」）
     */
    ShopTrendyParkPhoto submit(ShopParkPhotoSubmitDto dto);
}
