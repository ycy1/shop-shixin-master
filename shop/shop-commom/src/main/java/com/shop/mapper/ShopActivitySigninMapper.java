package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopActivitySignin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 用户签到记录 Mapper接口
 *
 * 后台这一页读的是两条：按用户分组的列表（一人一行，几个数是 GROUP BY 现算的）
 * 和日历弹窗里某个人某个月的签到明细。
 * 写入只有签到这一个动作（前台正常签到、后台补签，两份都在 shop-api 的 ShopSigninService 里），
 * 而它必须"落这一行 + 加积分账 + 记积分流水"三件在同一个事务里做完 ——
 * 只写签到行不给分，积分流水里就缺一笔「签到」；只给分不写行，这一页的日历和账对不上。
 */
@Mapper
public interface ShopActivitySigninMapper extends BaseMapper<ShopActivitySignin> {

    /**
     * 分页查签到列表（一个用户一行：签到天数、补签次数、累计分、最近一天）
     *
     * 那一行上的「连续天数」不在这条 SQL 里（currentContinuous 由 service 逐行现算，见 SigninUtils）
     */
    IPage<ShopActivitySignin> selectSigninPage(IPage<ShopActivitySignin> page, @Param("q") ShopActivitySignin query);

    /**
     * 查某个人某个月的签到记录（一个月最多 31 行，日历弹窗一次全带出来，不分页）
     *
     * @param month yyyy-MM，按 sign_date 筛（不是 sign_time —— 补签那行的归属日在它自己那个月里）
     */
    List<ShopActivitySignin> selectMonthRecords(@Param("userId") Long userId, @Param("month") String month);

    /**
     * 倒着读某个人最近的几个签到日（最多 {@link com.shop.utils.SigninUtils#MAX_LOOK_BACK_DAYS} 条），只为现算「连着第几天」
     *
     * 只回日期这一列、不走实体：这一串不进任何页面，算完一个数就丢。
     * 写入那一路不用这一条（它一趟还要读累计天数和那一天签过没有，见 {@link #selectSignDatesAround}）。
     *
     * @param upto 回看的起点（含这一天）—— 列表按那一组的最近签到日传、日历按今天传
     */
    List<LocalDate> selectRecentSignDates(@Param("userId") Long userId, @Param("upto") LocalDate upto);

    /**
     * 签到/补签落库之前那一趟：三个数一次读回来（这一天签过没有、名下总共几行、它之前最近的那几格日子）
     *
     * 这三件事原来是三条 SQL（判重 selectOne + 数累计 selectCount + 数连续 {@link #selectRecentSignDates}），
     * 合并发是因为 MySQL 在这台公网机器上，签到这一条链最贵的就是来回数 —— 每一发都是一个来回。
     *
     * 回的是这个人名下的行（没有就回空表，所以「一行都还没签」要从空表读出来），每一行只带两格：
     * signDate（那一天的日子）和 signCount（名下总行数，由 COUNT(*) OVER () 盖在每一行上、不受只回 7 行的影响）。
     * 排序把 signDate 那天和它之前的日子排在比它晚的那些之前，所以<b>第一行等于传进来的那一天就是签过了</b>；
     * 比 signDate 晚的那些只有补签会碰到（补一条过去的日子，名下当然有更新的行），它们排在后面、
     * 由调用方挑掉，不参与「连着第几天」那串。
     *
     * 这一条读在新行落库<b>之前</b>，所以连续那一串要由调用方把 signDate 自己接在最前
     *（和原来落完之后读回来的那一串同形，天数上限都是 {@link com.shop.utils.SigninUtils#MAX_LOOK_BACK_DAYS}）。
     */
    List<ShopActivitySignin> selectSignDatesAround(@Param("userId") Long userId, @Param("signDate") LocalDate signDate);
}
