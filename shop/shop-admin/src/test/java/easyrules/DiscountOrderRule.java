package easyrules;

import com.shop.annotation.RuleGroup;
import com.shop.entity.SysOrder;
import lombok.extern.slf4j.Slf4j;
import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Fact;
import org.jeasy.rules.annotation.Rule;
import org.jeasy.rules.api.Facts;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@Slf4j
@Rule(name = "优惠劵", description = "优惠劵", priority = 1)
@RuleGroup(key = RuleGroup.ORDER)
public class DiscountOrderRule {

    @Condition
    public boolean isCoupon(@Fact("payAmount") BigDecimal payAmount) {
         return payAmount.compareTo(new BigDecimal("500")) > 0;
//        return true;
    }


    /****
     * 计算优惠金额
     *
     * @param payAmount
     * @param facts
     */
    @Action(order = 0)
    public void onSale(@Fact("payAmount") BigDecimal payAmount, @Fact("sale") Double sale, @Fact("orderFact") SysOrder orderFact,Facts facts) {
        BigDecimal multiply = payAmount.multiply(new BigDecimal(sale))
                .setScale(2, RoundingMode.HALF_UP);
        orderFact.setPayAmount(multiply);
//        facts.put("payAmount", multiply);
        log.info("优惠劵{}，优惠{}后金额{}", payAmount, sale, multiply);
    }


    @Action(order = 1)
    public void onCoupon(@Fact("orderFact") SysOrder orderFact,Facts facts) {
        BigDecimal pay = orderFact.getPayAmount();
        BigDecimal multiply = pay.multiply(new BigDecimal("0.8"))
                .setScale(2, RoundingMode.HALF_UP);
        orderFact.setPayAmount(multiply);
//        facts.put("payAmount", multiply);
        log.info("满减{}，打8折后金额{}", pay, multiply);
    }
}