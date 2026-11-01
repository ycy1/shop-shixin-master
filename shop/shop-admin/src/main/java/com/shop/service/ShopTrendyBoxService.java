package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopTrendyBox;
import com.shop.entity.ShopTrendyBoxUser;

import java.util.List;

/**
 * 盲盒表 服务类
 */
public interface ShopTrendyBoxService extends IService<ShopTrendyBox> {

    /**
     * 分页查询盲盒
     *
     * @param query boxName 作为模糊关键字，trendyId、storeId、boxType、status 精确匹配
     */
    IPage<ShopTrendyBox> selectPage(ShopTrendyBox query);

    /**
     * 新增盲盒（编号由服务端生成）
     */
    void insert(ShopTrendyBox box);

    /**
     * 修改盲盒
     */
    void update(ShopTrendyBox box);

    /**
     * 批量删除盲盒：连带软删它名下的款式（款式是这只盒的组成部分，没有独立存在的意义）
     */
    void deleteByIds(List<Long> ids);

    /**
     * 分页查抽取记录（列表里那棵「抽取记录」用）
     *
     * @param query boxId、userId、isHidden 精确匹配，都可空（不传 boxId 就是看全部盒的流水）
     */
    IPage<ShopTrendyBoxUser> pageRecords(ShopTrendyBoxUser query);
}
