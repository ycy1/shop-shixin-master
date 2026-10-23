package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopDeliveryFeeRule;

import java.util.List;

/**
 * 配送费规则表 服务类
 */
public interface ShopDeliveryFeeRuleService extends IService<ShopDeliveryFeeRule> {

    /**
     * 分页查询配送费规则
     *
     * @param query keyword 模糊匹配规则名称，deliveryType、status 精确匹配；
     *              平台级配置，不按创建人/账号类型收窄（user_id 只留痕）
     */
    IPage<ShopDeliveryFeeRule> selectPage(ShopDeliveryFeeRule query);

    /**
     * 新增配送费规则
     */
    void insert(ShopDeliveryFeeRule rule);

    /**
     * 修改配送费规则
     */
    void update(ShopDeliveryFeeRule rule);

    /**
     * 批量删除配送费规则（逻辑删除）
     */
    void deleteByIds(List<Long> ids);
}
