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
 * 券类型 4 代金券：无门槛就是面额那一格直接抵，受 max_discount 封顶（算式和满减共用 {@link CouponUtils#amountDiscount}）。
 *
 * 这一条还兼管库里那几档之外的脏券类型（包括 face_value 那格压根没值的老数据）：
 * 脏值按金额券处理而不是报错 —— 面额缺失最坏就是抵 0 元，由 {@link CouponCalcFact#applyDiscount} 那道判据拒掉，不会多抵一分钱。
 * 口径和搬成档位规则之前那份 switch 的默认分支一字不差。
 *
 * 所以 @Condition 是"前面三档都没认领"而不是"type == 4"：档位规则之间要正好盖满、互不重叠，
 * 一张券只能被一条挂上抵额，挂两次数就会打架。
 */
@Component
@Rule(name = "代金券", description = "4 代金：抵面额那一格（认不出来的券类型也按这一档走）", priority = 40)
@RuleGroup(key = RuleGroup.COUPON)
public class VoucherCouponRule {

    @Condition
    public boolean isAmountVoucher(@Fact(CouponCalcFact.KEY) CouponCalcFact calc) {
        if (!calc.passed()) {
            return false;
        }
        Integer type = calc.getCoupon().getCouponType();
        return type == null || (type != CouponUtils.TYPE_FULL_REDUCE && type != CouponUtils.TYPE_DISCOUNT
                && !CouponUtils.isFreeShipping(type));
    }

    @Action
    public void discountByFaceValue(@Fact(CouponCalcFact.KEY) CouponCalcFact calc) {
        ShopActivityCouponUser coupon = calc.getCoupon();
        calc.applyDiscount(CouponUtils.amountDiscount(coupon.getFaceValue(), coupon.getMaxDiscount()));
    }
}
