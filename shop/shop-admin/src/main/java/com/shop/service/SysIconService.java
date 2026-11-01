package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.SysIcon;

import java.util.List;

/**
 * 图标库表 服务类
 */
public interface SysIconService extends IService<SysIcon> {

    /**
     * 分页查询图标（含所属类目名称）
     */
    IPage<SysIcon> selectPage(SysIcon query);

    /**
     * 查询启用状态的图标选项，供选择器组件使用（不分页）
     *
     * @param iconCategory 图标分类（1系统图标 2商品分类 3其他），为空时不限分类
     * @param categoryId   所属商品类目，为空时不限类目
     */
    List<SysIcon> listOptions(Integer iconCategory, Long categoryId);

    /**
     * 新增图标
     */
    void insert(SysIcon icon);

    /**
     * 修改图标
     */
    void update(SysIcon icon);

    /**
     * 批量删除图标
     */
    void deleteByIds(List<Long> ids);
}
