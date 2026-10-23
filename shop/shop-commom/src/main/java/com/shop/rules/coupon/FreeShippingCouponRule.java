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
 * 券类型 3 免邮券：这一档抵的不是货款，是那一单的配送费，所以它不写 discount，只把 freeShipping 挂回事实。
 *
 * 为什么这条不算钱：配送费要等建配送单那一步才算得出来（按门店坐标就近挑一条计价规则），
 * 那一刻还没到 —— 那一拍在调用方（ShopOrderCouponServiceImpl#offsetDeliveryFee），
 * 它拿这个标记把这张券从"待用"里取出来抵掉，不落明细行，也不回改配送单上那个数。
 * 那一拍抵多少 = min(那笔运费, 这张券的 max_discount)，算式是 CouponUtils#shippingDiscount（和金额档一样，封顶是整张券一次）。
 *
 * 也不判 NO_DISCOUNT：配送费可能本来就是 0（没配启用的规则，或命中了"实付满多少免配送费"那一档），
 * 那种情况调用方会把这张券留在用户手上、不核销，这不算用券失败。
 * 门槛和适用范围照旧由前面那条与券类型无关的判据管（免邮券也能设"满 X 元免邮"）。
 */
@Component
@Rule(name = "免邮券", description = "3 免邮：不折货款，只挂免邮标记，抵的是那一单刚算出来的配送费", priority = 50)
@RuleGroup(key = RuleGroup.COUPON)
public class FreeShippingCouponRule {

    @Condition
    public boolean isFreeShipping(@Fact(CouponCalcFact.KEY) CouponCalcFact calc) {
        ShopActivityCouponUser coupon = calc.getCoupon();
        return calc.passed() && CouponUtils.isFreeShipping(coupon.getCouponType());
    }

    @Action
    public void markFreeShipping(@Fact(CouponCalcFact.KEY) CouponCalcFact calc) {
        calc.setFreeShipping(true);
    }
}
