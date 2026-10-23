package com.shop.api.service;

import com.shop.dto.activity.ShopActivitySigninMakeupDto;
import com.shop.entity.ShopActivitySignin;

import java.time.LocalDate;

/**
 * 用户签到 写入服务（一人一天一行，两个动作都在这份里，前台和后台共用同一套落行 + 给分 + 记流水）
 *
 * 为什么住在 shop-api 而不是后台那一份 service 里：这一张表的写入方有两个（前台消费者自己点签到、
 * 后台日历上补签），两边给的分和记的流水**一模一样**，而 shop-admin 依赖 shop-api（反过来不通），
 * 所以公共那一份只能放这边，后台那颗「补签」调它 —— 同培训那张报名表的做法
 * （写入都在 shop-api 的 ShopTrainingSignService 里，后台只读、按钮调 comm）。
 *
 * 后台的读（按用户分组的列表、日历那一个月）仍然在 shop-admin 的 ShopActivitySigninService，两份不重叠。
 *
 * 这一张表不是单据，所以没有 add/update/delete：落一行 = 给一笔积分 + 记一笔「1 签到」流水，
 * 三件在一个事务里，只落行不给分（或只给分不落行）都算这笔账对不上。
 */
public interface ShopSigninService {

    /**
     * 前台本人签到（sign_type=1 正常签到）：签的是今天这一格，参数里不需要 user_id（能签的只有自己）
     *
     * @return 今天这一行（含这一天的类型/积分/连续天数，前端拿它回显「连续第 N 天，获得 X 分」）
     */
    ShopActivitySignin sign();

    /**
     * 后台补签（sign_type=2）：补的是今天之前某一天，那一天还没签过才给补
     *
     * 能补多远这里刻意不限：这一颗是操作员替用户兜底的动作，有人在那儿判断，不像本人那一头谁都能点。
     *
     * @return 补下来的那一行
     */
    ShopActivitySignin makeup(ShopActivitySigninMakeupDto form);

    /**
     * 本人补签（sign_type=2）：前台/后台日历上给自己补过去某一天，参数里不需要 user_id
     *（能补的只有自己，参数能点名别人就等于替别人签到 —— 那一条走 {@link #makeup}，压 activity:signin:makeup 权限）
     *
     * 判据和后台那颗一模一样（那一天必须早于今天、而且自己还没签过），因为这一颗只是把"给谁"那一格
     * 换成登录 id 再交给 {@link #makeup}，不另起一套口径。
     *
     * @return 补下来的那一行（含这一天的类型/积分/连续天数）
     */
    ShopActivitySignin makeupSelf(LocalDate signDate);
}
