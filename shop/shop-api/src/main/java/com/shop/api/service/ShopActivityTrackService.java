package com.shop.api.service;

import com.shop.dto.activity.ShopActivityTrackDto;

/**
 * 活动埋点 服务接口（前台报一次曝光 / 一次点击）
 *
 * 为什么写在 shop-api：这一条和下单、接单、报名那几路一样是"前台触发的写"，走 /api/comm
 * （ShopActivityCommonController，POST /api/comm/activity/track），
 * shop-api 只依赖 shop-commom，摸得到 shop_activity_click 的 mapper，也摸不到后台那份 ShopActivityService。
 *
 * 曝光和点击共用这一个方法：两件事落的是同一张流水表，只差 event_type 那一格，
 * 分成两颗端点会把同一套校验抄两遍（他要的口径：一个接口、传事件类型区分）。
 */
public interface ShopActivityTrackService {

    /**
     * 记一行曝光/点击流水到 shop_activity_click。
     *
     * 谁触发的不认参数：取当前登录态，未登录就落 NULL（那一列本来就允许空，游客看活动是常态）。
     * 只挡两件事：这条活动得真在（已被删掉的活动记下来只会成为指向空活动的孤儿），
     * 事件类型得是 1 曝光 / 2 点击 这两档之一。
     *
     * 故意不挡活动的投放状态（未开始 / 已结束 / 已取消照样收）：状态是后台手动推的那一格，
     * 而"这一刻有人看到了 / 有人点了"是当场的事实，判据同门店培训的签到。
     * 也不做防刷和去重：一次调用一行，曝光量就是次数。
     *
     * @param form 埋点参数（activityId + eventType）
     */
    void track(ShopActivityTrackDto form);
}
