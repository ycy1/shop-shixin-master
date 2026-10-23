package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopActivitySignin;

import java.util.List;

/**
 * 用户签到记录 服务接口（后台这一页：只有两条读）
 *
 * 这一张表是"一人一天一行"的原始记录，不是单据，所以这一份里不开 add/update/delete，
 * 也**不写任何一行**：签到这个动作（落行 + 给积分 + 记流水，三件一个事务）只住在 shop-api 的
 * ShopSigninService 里 —— 写入方有前台正常签到和后台补签两个，两边给的分一模一样，
 * 而 shop-admin 依赖 shop-api（反过来不通），所以公共那一份只能放那边，这颗「补签」调它。
 * 分工同门店培训那两张表：写入在 api，后台只读。
 */
public interface ShopActivitySigninService extends IService<ShopActivitySignin> {

    /**
     * 分页查签到列表（一个用户一行，几个数是 GROUP BY 现算的，最近还签着的排前面）
     *
     * @param query 查询条件（userId/keyword）
     */
    IPage<ShopActivitySignin> selectPage(ShopActivitySignin query);

    /**
     * 某个人某个月的签到记录（日历弹窗那一张表，一次全带不分页；month 传 yyyy-MM，留空按当月）
     */
    List<ShopActivitySignin> selectMonthRecords(Long userId, String month);
}
