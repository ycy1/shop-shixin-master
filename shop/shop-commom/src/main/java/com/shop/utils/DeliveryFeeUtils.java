package com.shop.utils;

import com.shop.entity.ShopDeliveryFeeRule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;

/**
 * 配送费计价：把一条 {@link ShopDeliveryFeeRule} + 这一单的距离/重量/货值折成那一笔钱。
 *
 * 为什么在 shop-commom 而不是写在后台那个 service 里：算这一格的有两条路，而且互相看不见 ——
 * 「统一下单」在 shop-api（ShopOrderDeliveryServiceImpl，那边只依赖 commom 的 mapper，摸不到后台的 service），
 * 「配送管理」里人工改配送费在 shop-admin。口径抄两份的话，改一处忘一处，
 * 同一张单下单时收的钱和后台看到的规则会对不上 —— 而钱是下单那一刻就跟货款一起收走的，
 * 事后发现算错只能退款，所以这一段必须只有一份。
 *
 * 挑规则（取"启用里优先级最高"那一条）不在这里：那是一次查库，
 * 走 ShopDeliveryFeeRuleMapper#selectTopRule，两条路调的是同一个方法。
 */
public class DeliveryFeeUtils {

    /** 金额统一保留两位，和库里的 decimal(10,2) 对齐 */
    private static final int MONEY_SCALE = 2;

    private DeliveryFeeUtils() {
    }

    /**
     * 减免先看：命中"距离够近"或"这单买得够贵"任一档就是 0 元，不再往里加里程/重量/时段费。
     * 规则为 null 表示库里一条启用的规则都没有 —— 那就是 0 元，不拦建单（去「配送费规则」配好再重新下单）。
     *
     * distance / weight / orderAmount 任一为 null 表示这一格拿不到数（两个地点缺坐标、商品没录重量、
     * 老订单没落金额），对应那一档跳过而不是按 0 参与比较：
     * 算不出距离不等于"距离 0 公里"，没称重量不等于"重量没超"，货值不明也不该白送一趟。
     */
    public static BigDecimal calculateFee(ShopDeliveryFeeRule rule, BigDecimal distance,
                                          BigDecimal weight, BigDecimal orderAmount) {
        if (rule == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        if (freeByDistance(rule, distance) || freeByAmount(rule, orderAmount)) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal fee = money(rule.getBaseFee());
        // 起步里程之外按公里收；算不出距离时这一档不参与，只收起步价
        fee = fee.add(overFee(distance, rule.getBaseDistance(), rule.getPerKmFee()));
        // 免费重量之外按公斤收；没填重量时同理跳过
        fee = fee.add(overFee(weight, rule.getWeightFree(), rule.getPerKgFee()));
        LocalTime now = LocalTime.now();
        if (inRange(now, rule.getPeakStart(), rule.getPeakEnd())) {
            fee = fee.add(money(rule.getPeakFee()));
        }
        if (inRange(now, rule.getNightStart(), rule.getNightEnd())) {
            fee = fee.add(money(rule.getNightFee()));
        }
        // weather_fee 这一档不参与计算：系统里没有任何天气数据来源（第三方接口/手工录入都没有），
        // 读了规则也只会永远加 0 —— 等有数据源了在这里补一句 inWeather 判断即可。
        return fee.max(BigDecimal.ZERO).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static boolean freeByDistance(ShopDeliveryFeeRule rule, BigDecimal distance) {
        return rule.getFreeDeliveryDistance() != null && distance != null
                && distance.compareTo(rule.getFreeDeliveryDistance()) <= 0;
    }

    private static boolean freeByAmount(ShopDeliveryFeeRule rule, BigDecimal orderAmount) {
        return rule.getFreeDeliveryAmount() != null && orderAmount != null
                && orderAmount.compareTo(rule.getFreeDeliveryAmount()) >= 0;
    }

    /**
     * "超出部分 × 单价"：actual 或 free 任一为 null 就算不出超出量，返回 0 跳过这一档。
     * 负数（还没到免费上限）也归 0，不会因为轻于 weightFree 就倒扣钱。
     */
    private static BigDecimal overFee(BigDecimal actual, BigDecimal free, BigDecimal unitPrice) {
        if (actual == null || free == null || unitPrice == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal extra = actual.subtract(free);
        return extra.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ZERO : extra.multiply(unitPrice);
    }

    /**
     * 当前时刻是否落在这个时段区间里。开始或结束没填就是没配这一档，返回 false。
     * 开始晚于结束表示跨零点（如夜间 21:00~06:00），那一段是"到点之前或起点之后"都算。
     */
    private static boolean inRange(LocalTime now, LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return false;
        }
        if (start.isAfter(end)) {
            return !now.isBefore(start) || !now.isAfter(end);
        }
        return !now.isBefore(start) && !now.isAfter(end);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
