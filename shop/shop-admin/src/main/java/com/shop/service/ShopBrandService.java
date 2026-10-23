package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopBrand;

import java.util.List;

/**
 * 品牌表 服务类
 */
public interface ShopBrandService extends IService<ShopBrand> {

    /**
     * 分页查询品牌
     *
     * @param query brandName 作为模糊关键字，status、categoryId 精确匹配
     */
    IPage<ShopBrand> selectPage(ShopBrand query);

    /**
     * 查询启用状态的品牌，供商品表单的下拉使用（不分页）
     *
     * @param categoryId 传 null 返回全部启用品牌；否则返回该分类及其各级上级下的品牌 + 未分类的品牌
     */
    List<ShopBrand> listOptions(Long categoryId);

    /**
     * 新增品牌
     */
    void insert(ShopBrand brand);

    /**
     * 修改品牌
     */
    void update(ShopBrand brand);

    /**
     * 批量删除品牌，被商品引用时拒绝
     */
    void deleteByIds(List<Long> ids);
}
