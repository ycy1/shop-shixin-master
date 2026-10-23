package com.shop.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 配送时长估算：把配送单上那一格 distance（km）按**送货员的交通工具**折成分钟数。
 *
 * 为什么在 shop-commom 而不是各自写在两个 service 里：算这一格的有两处，而且互相看不见 ——
 * 「配送管理」手工建单/修改在 shop-admin（ShopDeliveryServiceImpl#fillEstimate），
 * 送货员接单在 shop-api（ShopDeliveryAcceptServiceImpl，那边只依赖 commom 的 mapper）。
 * 速度表抄两份的话，改一处忘一处，同一张单接单前后估出来的数还会自相矛盾。
 * 和算距离那段是同一个道理：球面算法只在 SQL 里一份，折算规则也只在这里一份。
 */
public class DeliveryTimeUtils {

    /** 取货、装车、交接的固定耗时（分钟）：和路上耗时无关，所以不管什么交通工具都加这一笔 */
    public static final int PICKUP_MINUTES = 10;

    /** 交通工具（字典 rider_vehicle_type）：1 电动车 2 摩托车 3 自行车 4 汽车 */
    public static final int VEHICLE_E_BIKE = 1;

    public static final int VEHICLE_MOTORCYCLE = 2;

    public static final int VEHICLE_BICYCLE = 3;

    public static final int VEHICLE_CAR = 4;

    /**
     * 各交通工具的市区**实际**均速（km/h）：取的是含红绿灯、绕路、找门牌的平均速度，不是表定极速。
     * 汽车在市区常常比电动车还慢，所以它只按 30 算，别再往上调。
     * 本项目的 distance 是两点间的球面直线距离（没接路网 API），本身就偏乐观，
     * 所以这些数只当"给收货人一个大概几点能到"的参考，别拿它考核送货员。
     */
    private static final Map<Integer, BigDecimal> SPEED_KMH = Map.of(
            VEHICLE_E_BIKE, new BigDecimal("15"),
            VEHICLE_MOTORCYCLE, new BigDecimal("25"),
            VEHICLE_BICYCLE, new BigDecimal("10"),
            VEHICLE_CAR, new BigDecimal("30"));

    /** 没填交通工具、或者填了个字典里没有的值时的兜底速度：按电动车算 */
    private static final BigDecimal DEFAULT_SPEED_KMH = SPEED_KMH.get(VEHICLE_E_BIKE);

    private DeliveryTimeUtils() {
    }

    /**
     * 路上耗时 + 固定取货时间 = 预计配送时长（分钟，向上取整；凑不出整数不如报个偏大的数）。
     * distance 为 null 表示这一单两个地点凑不齐、算不出距离，那预计时长也跟着空着 ——
     * 传回 null 而不是 0，前端才能把"还没法定"和"马上就到"区分开。
     */
    public static Integer estimateMinutes(BigDecimal distanceKm, Integer vehicleType) {
        if (distanceKm == null) {
            return null;
        }
        // Map.of 那种不可变 Map 连 get(null) 都会抛 NPE，所以先自己判空再查（没填交通工具是正常状态）
        BigDecimal speed = vehicleType == null ? DEFAULT_SPEED_KMH
                : SPEED_KMH.getOrDefault(vehicleType, DEFAULT_SPEED_KMH);
        int minutes = distanceKm.multiply(BigDecimal.valueOf(60))
                .divide(speed, 0, RoundingMode.CEILING).intValue() + PICKUP_MINUTES;
        return Math.max(minutes, 0);
    }
}
