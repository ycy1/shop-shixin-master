package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopTrendyIp;

import java.util.List;

/**
 * 潮玩IP表 服务类
 */
public interface ShopTrendyIpService extends IService<ShopTrendyIp> {

    /**
     * 分页查询潮玩IP（含名下系列数）
     *
     * @param query ipName 作为模糊关键字，status / licenseType / isHot 精确匹配
     */
    IPage<ShopTrendyIp> selectPage(ShopTrendyIp query);

    /**
     * 查询启用状态的潮玩IP，供系列表单的「所属IP」下拉使用（不分页）
     */
    List<ShopTrendyIp> listOptions();

    /**
     * 把一批潮玩系列绑到某个IP上（ipId 传 null 就是解绑）
     *
     * 只动 shop_trendy.ip_id 这一列，别的列一概不碰。
     */
    void bindTrendies(List<Long> trendyIds, Long ipId);

    /**
     * 新增潮玩IP
     */
    void insert(ShopTrendyIp ip);

    /**
     * 修改潮玩IP
     */
    void update(ShopTrendyIp ip);

    /**
     * 批量删除潮玩IP，名下还绑着系列时拒绝
     */
    void deleteByIds(List<Long> ids);
}
