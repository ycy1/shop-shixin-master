package com.shop.enums;

/**
 * 下单用券被拒掉的那几档原因：com.shop.rules.coupon 里的规则只写这个码，不写那句中文。
 *
 * 为什么要一个码：规则的职责是"这一张券过不过"，而**怎么把不过说出来是业务** ——
 * 那几句话要带券名、金额、日期，还得让买家看得懂，这些都不该长在规则类里。
 * 文案由 shop-api 的 ShopOrderCouponServiceImpl 按这个码拼，字句和搬进规则引擎之前一字不差。
 */
public enum CouponRejectEnum {

    /** 券的 user_id 不是下单人 */
    NOT_OWNER,

    /** status 不是 0 未使用（1 已使用 / 2 已过期 / 3 已作废） */
    NOT_UNUSED,

    /** expire_time 已经过了 */
    EXPIRED,

    /** 券模板那一行读不到 */
    TEMPLATE_MISSING,

    /** 券模板 status = 4 已取消 */
    TEMPLATE_CANCELED,

    /** scope_type 落在 1/2/3/4 之外（脏值） */
    SCOPE_UNKNOWN,

    /** 适用范围认得出，但这一单里一行都没命中 */
    SCOPE_NO_HIT,

    /** 命中行的小计合计够不上 threshold_amount */
    THRESHOLD_NOT_REACHED,

    /** 折算不出正数抵额（面额空着、或抵 0） */
    NO_DISCOUNT
}
