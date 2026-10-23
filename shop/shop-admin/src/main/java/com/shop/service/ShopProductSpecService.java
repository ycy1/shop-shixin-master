package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopProductSpec;

import java.util.List;

/**
 * 商品规格表 服务类
 */
public interface ShopProductSpecService extends IService<ShopProductSpec> {

    /**
     * 分页查询规格（含所属类目名称）
     */
    IPage<ShopProductSpec> selectPage(ShopProductSpec query);

    /**
     * 查询启用状态的规格选项，供商品表单的下拉框使用（不分页）
     *
     * @param categoryId 所属类目，为空时返回全部启用规格
     */
    List<ShopProductSpec> listOptions(Long categoryId);

    /**
     * 新增规格
     */
    void insert(ShopProductSpec spec);

    /**
     * 修改规格
     */
    void update(ShopProductSpec spec);

    /**
     * 批量删除规格
     */
    void deleteByIds(List<Long> ids);
}
