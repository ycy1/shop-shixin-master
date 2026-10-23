package com.shop.rules.coupon;

import com.shop.annotation.RuleGroup;
import com.shop.entity.ShopActivityCouponUser;
import com.shop.utils.CouponUtils;
import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Fact;
import org.jeasy.rules.annotation.Rule;
import org.springframework.stereotype.Component;

/**
 * 券类型 2 折扣券：命中金额 ×（1 − 折扣率）。face_value 存的是折扣率，所以九折券抵的是这一段的 10%。
 *
 * 基数就是前面那条共用判据认下的 base（这张券命中那些行的小计合计，原价口径），不是整单货值。
 * 折扣率空着、或者脏到大于 1（折出负数，CouponUtils 里抹平成 0）都抵不出钱 —— 拒掉这一张，
 * 而不是留个负数下去把金额算成加价（那一刀和三档金额规则共用，见 {@link CouponCalcFact#applyDiscount}）。
 */
@Component
@Rule(name = "折扣券", description = "2 折扣：命中金额 ×（1 − 折扣率），受最高抵扣封顶", priority = 30)
@RuleGroup(key = RuleGroup.COUPON)
public class DiscountCouponRule {

    @Condition
    public boolean isRateCoupon(@Fact(CouponCalcFact.KEY) CouponCalcFact calc) {
        Integer type = calc.getCoupon().getCouponType();
        return calc.passed() && type != null && type == CouponUtils.TYPE_DISCOUNT;
    }

    @Action
    public void discountByRate(@Fact(CouponCalcFact.KEY) CouponCalcFact calc) {
        ShopActivityCouponUser coupon = calc.getCoupon();
        calc.applyDiscount(CouponUtils.rateDiscount(calc.getBase(), coupon.getFaceValue(), coupon.getMaxDiscount()));
    }
}
