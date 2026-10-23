package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopIcon;

import java.util.List;

/**
 * 图标库表 服务类
 */
public interface ShopIconService extends IService<ShopIcon> {

    /**
     * 分页查询图标（含所属类目名称）
     */
    IPage<ShopIcon> selectPage(ShopIcon query);

    /**
     * 查询启用状态的图标选项，供选择器组件使用（不分页）
     *
     * @param categoryId 所属类目，为空时返回全部启用图标
     */
    List<ShopIcon> listOptions(Long categoryId);

    /**
     * 新增图标
     */
    void insert(ShopIcon icon);

    /**
     * 修改图标
     */
    void update(ShopIcon icon);

    /**
     * 批量删除图标
     */
    void deleteByIds(List<Long> ids);
}
