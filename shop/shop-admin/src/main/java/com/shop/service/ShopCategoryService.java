package com.shop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopCategory;

import java.util.List;

/**
 * 商品类目表 服务类
 */
public interface ShopCategoryService extends IService<ShopCategory> {

    /**
     * 查询类目树
     */
    List<ShopCategory> listCategoryTree(ShopCategory query);

    /**
     * 新增类目
     */
    void addCategory(ShopCategory category);

    /**
     * 修改类目
     */
    void updateCategory(ShopCategory category);

    /**
     * 批量删除类目
     */
    void deleteCategory(List<Long> ids);
}
