package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopTrendyParkPhoto;

import java.util.List;

/**
 * 园区照片 服务类
 *
 * 一家园区名下的照片，一张一行（来源/审核状态/精选/标签都挂在行上）。
 * 前台园区详情只读审核通过的那几行，读的那一路在 shop-api 的潮玩专区读服务里，不在这里。
 */
public interface ShopTrendyParkPhotoService extends IService<ShopTrendyParkPhoto> {

    /**
     * 分页查询照片
     *
     * @param query parkId、ipId、sourceType、status、isFeatured 精确匹配，keyword 模糊匹配描述/标签
     */
    IPage<ShopTrendyParkPhoto> selectPage(ShopTrendyParkPhoto query);

    /**
     * 标签候选：读的是 Redis 里那一串词表（RedisConstants.PARK_PHOTO_TAG_KEY），不是字典、也不按当前列表数。
     * 那一串空着时先从库里已在用的标签灌一遍当起点，之后就以 Redis 那份为准；
     * 表单里新敲的词在保存时进这一串，所以它只会长，不会因为照片被删而缩。
     */
    List<String> listTagOptions();

    /**
     * 按 id 查询照片详情
     */
    ShopTrendyParkPhoto detail(Long id);

    /**
     * 新增照片（所属 IP 按园区带过来，不是人填的）
     */
    void insert(ShopTrendyParkPhoto photo);

    /**
     * 修改照片（换园区时所属 IP 跟着重带）
     */
    void update(ShopTrendyParkPhoto photo);

    /**
     * 审核：一次把「审核状态 + 审核意见」两格推到 1 通过 / 2 拒绝 / 3 已隐藏，并给上传人发一条结论消息。
     *
     * 单开这一条而不是让页面整行提交「修改」：这一表上有四格是可清空列（所属IP、上传人、描述、审核意见），
     * 一个只想点「通过」的请求带不齐那一行，走整行提交就是把那几格抹成 NULL。
     * 落库走 Mapper 那一句专用 UPDATE（audit_msg 要能写成 NULL，见 ShopTrendyParkPhotoMapper#auditPhoto）；
     * 挂事务是给通知用的：结论没落成为别发，作者收到「已通过」而照片还在待审核，比不发更糟。
     *
     * @param auditMsg 审核意见，可空（空串按没写处理，落库是 NULL）；上限是那一列的 255 字
     */
    void audit(Long id, Integer status, String auditMsg);

    /**
     * 批量删除照片（没有别的表引用它，直接删）
     */
    void deleteByIds(List<Long> ids);
}
