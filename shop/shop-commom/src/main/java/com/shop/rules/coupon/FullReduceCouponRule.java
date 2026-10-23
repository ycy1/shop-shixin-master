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
 * 券类型 1 满减券：抵的就是面额那一格（face_value 存的是减免金额），受 max_discount 封顶。
 *
 * 认领档位写在 @Condition 上，算式一次调用挂在 @Action 里 —— 这一档要改算法只动这一条和
 * {@link CouponUtils#amountDiscount}，不会牵到别的档。
 *
 * 抵不出正数就拒掉这一张（那一刀和三档金额规则共用，见 {@link CouponCalcFact#applyDiscount}）：
 * 静默抵 0 就是"券没收掉、钱也没给抵"，买家页面上看到的金额和真收的钱对不上。
 * 门槛不在这一条判（它和券是哪档无关，归前面那条共用判据）。
 */
@Component
@Rule(name = "满减券", description = "1 满减：抵面额那一格，受最高抵扣封顶", priority = 20)
@RuleGroup(key = RuleGroup.COUPON)
public class FullReduceCouponRule {

    @Condition
    public boolean isFullReduce(@Fact(CouponCalcFact.KEY) CouponCalcFact calc) {
        Integer type = calc.getCoupon().getCouponType();
        return calc.passed() && type != null && type == CouponUtils.TYPE_FULL_REDUCE;
    }

    @Action
    public void discountByFaceValue(@Fact(CouponCalcFact.KEY) CouponCalcFact calc) {
        ShopActivityCouponUser coupon = calc.getCoupon();
        calc.applyDiscount(CouponUtils.amountDiscount(coupon.getFaceValue(), coupon.getMaxDiscount()));
    }
}
