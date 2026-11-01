package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopTrendyPark;

import java.util.List;

/**
 * 主题公园园区 服务类
 */
public interface ShopTrendyParkService extends IService<ShopTrendyPark> {

    /**
     * 分页查询园区
     *
     * @param query keyword 模糊匹配名称/编号，ipId、status 精确匹配
     */
    IPage<ShopTrendyPark> selectPage(ShopTrendyPark query);

    /**
     * 营业中的园区选项（不分页，供票种表单、出票表单那格「所属园区」下拉使用）
     */
    List<ShopTrendyPark> listOptions();

    /**
     * 按 id 查询园区详情
     */
    ShopTrendyPark detail(Long id);

    /**
     * 新增园区（编号由服务端生成）
     */
    void insert(ShopTrendyPark park);

    /**
     * 修改园区
     */
    void update(ShopTrendyPark park);

    /**
     * 批量删除园区（名下还有票种时拦住，让人先去票种那页处理）
     */
    void deleteByIds(List<Long> ids);
}
