package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopActivityBadge;

import java.util.List;

/**
 * 营销徽章定义 服务接口（营销活动 -> 徽章管理）
 *
 * 这一份只管定义表那一张：运营在这儿定一枚徽章的门槛（badge_type + target_id + reach_count）
 * 和奖励（points + coupon_ids）。
 *
 * 发到用户手上的那三条写入路（支付回调判定、签到徽章那条定时任务、列表上那颗「下发」）都不在本模块，
 * 同住 shop-api 的 {@code ShopActivityBadgeGrantService}：判定和兑现只有一份，
 * 后台手动下发的那一枚和用户买满之后自动发的那一枚才会走同一套奖励、同一个唯一键。
 * 所以这里没有 give 方法，只有 CRUD —— 详情那一条读的还是本表（不联明细，见实现里的排序那一段）。
 */
public interface ShopActivityBadgeService extends IService<ShopActivityBadge> {

    /**
     * 分页查询徽章
     *
     * @param query 查询条件（keyword/badgeType/status）
     * @return 徽章分页，目标名称已联门店/活动带出，已获得人数按明细表数出来带出
     */
    IPage<ShopActivityBadge> selectPage(ShopActivityBadge query);

    /**
     * 单条徽章（修改弹窗回显和只读详情弹窗都走它）
     *
     * @param id 徽章id
     * @return 徽章
     */
    ShopActivityBadge selectBadgeById(Long id);

    /**
     * 新增：状态不传按 1 启用；target_id 按类型分叉（签到/消费两类由服务端清成 NULL）
     *
     * @param badge 徽章
     */
    void insert(ShopActivityBadge badge);

    /**
     * 修改：整行提交，可空的那几格（文字/图标/目标/券/说明）不传就清掉
     *
     * @param badge 徽章
     */
    void update(ShopActivityBadge badge);

    /**
     * 批量逻辑删除：只删还没发出去过的那几枚（已经发到用户手上的，明细表那些行还指着这一枚定义，
     * 判据同"发出去过的券删不掉"、"投放过的活动删不掉"）
     *
     * @param ids 主键集合
     */
    void deleteByIds(List<Long> ids);
}
