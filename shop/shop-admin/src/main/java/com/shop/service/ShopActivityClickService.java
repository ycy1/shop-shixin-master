package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopActivityClick;

/**
 * 活动曝光/点击记录 服务接口（后台这一页只读）
 *
 * 写入只有前台埋点那一条路：POST /api/comm/activity/track（shop-api 的 ShopActivityTrackService），
 * 两端同一个动作必须落在同一条写入路径上，所以这里不开 add/delete。
 */
public interface ShopActivityClickService extends IService<ShopActivityClick> {

    /**
     * 分页查某一条活动的曝光/点击明细（列表页那颗「查看记录」弹窗）
     *
     * @param query 查询条件（activityId/eventType/userId）
     * @return 流水分页，昵称已联表带出
     */
    IPage<ShopActivityClick> selectRecordPage(ShopActivityClick query);
}
