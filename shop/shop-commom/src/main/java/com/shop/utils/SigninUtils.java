package com.shop.utils;

import java.time.LocalDate;
import java.util.List;

/**
 * 签到连续的算法：这一张表上**没有**存连续天数那一列，数是从 sign_date 现算出来的。
 *
 * 为什么不存：补签可以补在一段连续日子中间的那一天，存着的写法只改那一行、不把之后的行重接
 *（那等于把已经发出去的分按新连档重算），于是日历上后面那些行上存的数全成了旧账。
 * 现算就没这个洞 —— 读的就是「谁在哪一天有过一行」这一件事实本身。
 *
 * 和 DeliveryFeeUtils 一样住在 shop-commom：动这个数的是两条互相看不见的路 ——
 * 后台「签到管理」那一份只读（列表那一格 + 日历那一个月），前台签到/后台补签在 shop-api 写，
 * 而「连着第几天」只要抄成两份，日历上说的和积分流水那条说明就对不上。
 *
 * 这里只做纯算式（给一串日期就出一个数），一次查库都没有：那串日期由调用方按
 * {@link #MAX_LOOK_BACK_DAYS} 条读好传进来。
 */
public class SigninUtils {

    /**
     * 往前回看的天数上限：连续天数最多报到 7。
     *
     * 这一格同时是查询的条数上限（SQL 里那句 LIMIT 用的是同一个数），所以回看再多也不会多读一行。
     * 定这么短是因为这一页真正要看的只有"还连着没有"（活动奖励那一档还没定，
     * 更长的连档将来要改的也就是这一个数 + 那一条 LIMIT）。
     */
    public static final int MAX_LOOK_BACK_DAYS = 7;

    private SigninUtils() {
    }

    /**
     * 连着第几天：从这一串里**最晚**的那一天往前逐日接，接不上就停。
     *
     * 传进来必须是倒序（最新的在第一个），也就是 `ORDER BY sign_date DESC LIMIT {@link #MAX_LOOK_BACK_DAYS}`
     * 读回来的那一串；这里不排序、不去重，谁查谁负责给成那个样子。
     * 空串是 0（这个人一行签到都没有）；只有最晚那一条也是 1（它自己那一天，前一天没有行）。
     */
    public static int continuousStreak(List<LocalDate> signDatesDesc) {
        if (signDatesDesc == null || signDatesDesc.isEmpty()) {
            return 0;
        }
        int streak = 1;
        LocalDate previous = signDatesDesc.get(0);
        for (int i = 1; i < signDatesDesc.size(); i++) {
            LocalDate current = signDatesDesc.get(i);
            if (current == null || !current.equals(previous.minusDays(1))) {
                break;
            }
            streak++;
            previous = current;
        }
        // 读回来的那一串最多就 MAX_LOOK_BACK_DAYS 条，这一刀是防着调用方自己传长了进来
        return Math.min(streak, MAX_LOOK_BACK_DAYS);
    }
}
