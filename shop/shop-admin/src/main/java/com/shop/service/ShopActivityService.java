package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopActivity;

import java.util.List;

/**
 * 活动 服务接口（营销活动 -> 活动管理）
 */
public interface ShopActivityService extends IService<ShopActivity> {

    /**
     * 分页查询活动
     *
     * @param query 查询条件（keyword/positionType/activityType/status/startFrom/startTo）
     * @return 活动分页，门店名已联表带出，曝光量和点击量按 shop_activity_click 现算（那一张表用 event_type 分两种事件）、
     *         点击率由这两个数除出来（曝光为 0 时是 NULL，前端显示 -）
     */
    IPage<ShopActivity> selectPage(ShopActivity query);

    /**
     * 单条活动（修改弹窗回显、只读详情弹窗都走它）
     *
     * @param id 活动id
     * @return 活动
     */
    ShopActivity selectActivityById(Long id);

    /**
     * 新增：状态强制落 1 未开始（开始投放是「修改」里把状态选成 2 那一步），
     * 统计那三格库里没有列（读的时候按流水表现算），请求体里带什么都不落
     *
     * @param activity 活动
     */
    void insert(ShopActivity activity);

    /**
     * 修改：状态在这一次整行提交；投放时间只在状态第一次流转到 2 时由服务端补，请求体里带什么都不认
     *
     * @param activity 活动
     */
    void update(ShopActivity activity);

    /**
     * 批量逻辑删除：只删从没投放过的那几条（投放过的活动名下挂着曝光/点击记录，删了就成了指向空活动的孤儿）
     *
     * @param ids 主键集合
     */
    void deleteByIds(List<Long> ids);
}
