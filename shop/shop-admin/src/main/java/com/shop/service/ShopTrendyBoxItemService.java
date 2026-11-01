package com.shop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopTrendyBoxItem;

import java.util.List;

/**
 * 盲盒商品（款式）表 服务类
 */
public interface ShopTrendyBoxItemService extends IService<ShopTrendyBoxItem> {

    /**
     * 某只盲盒名下的款式（不分页，给盲盒列表那棵「盲盒商品」弹层用）
     */
    List<ShopTrendyBoxItem> listByBox(Long boxId);

    /**
     * 新增款式（剩余数量按 总数-已抽 现算）
     */
    void insert(ShopTrendyBoxItem item);

    /**
     * 修改款式
     */
    void update(ShopTrendyBoxItem item);

    /**
     * 批量删除款式
     */
    void deleteByIds(List<Long> ids);
}
