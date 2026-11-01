package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopTrendy;
import com.shop.vo.trendy.ShopTrendyCollectItemVo;
import com.shop.vo.trendy.ShopTrendyCollectProgressVo;

import java.util.List;

/**
 * 潮玩系列表 服务类
 */
public interface ShopTrendyService extends IService<ShopTrendy> {

    /**
     * 分页查询潮玩系列
     *
     * @param query trendyName 作为模糊关键字，status 精确匹配
     */
    IPage<ShopTrendy> selectPage(ShopTrendy query);

    /**
     * 某一条系列上的集齐进度（列表上那颗「集齐进度」弹窗）：抽过这条系列商品规格的人一人一行，
     * 带「凑到几个 / 这套一共几个」和那一笔奖励领过没有（领取时间 + 兑现说明）。
     *
     * 只读，而且不复用发放那一条判据 SQL —— 发放要的是"够线又没领过"的那几行，
     * 这一页要的是所有人（包括还差几个的），两套谓词混在一条里，改哪一头都会把另一头带歪。
     *
     * @param trendyId 哪一条系列，必填
     * @param keyword  按人的昵称/账号/手机号筛，可空
     */
    IPage<ShopTrendyCollectProgressVo> collectProgress(Long trendyId, String keyword);

    /**
     * 上一条里点开某一个人时的款式明细：这条系列下的全部规格，一格上标出他抽到过没有（不分页，一次全带）
     *
     * @param userId 是谁，必填 —— 没有"谁"就答不出差哪几格
     */
    List<ShopTrendyCollectItemVo> collectItems(Long trendyId, Long userId);

    /**
     * 查询启用状态的潮玩系列，供商品/品牌/门店表单的下拉使用（不分页）
     */
    List<ShopTrendy> listOptions();

    /**
     * 把一批商品绑到某个潮玩系列上（trendyId 传 null 就是解绑）
     *
     * 只动 shop_product.trendy_id 这一列，别的列一概不碰。
     */
    void bindProducts(List<Long> productIds, Long trendyId);

    /**
     * 新增潮玩系列
     */
    void insert(ShopTrendy trendy);

    /**
     * 修改潮玩系列
     */
    void update(ShopTrendy trendy);

    /**
     * 批量删除潮玩系列，被商品/品牌/门店任一表绑着时拒绝
     */
    void deleteByIds(List<Long> ids);
}
