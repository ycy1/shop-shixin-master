package easyrules;

import com.shop.annotation.RuleGroup;
import com.shop.entity.SysOrder;
import lombok.extern.slf4j.Slf4j;
import org.jeasy.rules.annotation.*;
import org.jeasy.rules.api.Facts;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@Slf4j
@Rule(name = "满减积分", description = "满减赠送积分", priority = 1)
@RuleGroup(key = RuleGroup.POINTS)
public class CouponPointsRule {

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
    @Action
    public void addPoints(@Fact("payAmount") BigDecimal payAmount,@Fact("orderFact") SysOrder orderFact, Facts facts) {
        BigDecimal multiply = payAmount.multiply(new BigDecimal("0.2"))
                .setScale(2, RoundingMode.HALF_UP);
        orderFact.setTotalAmount(multiply);
//        facts.put("points", multiply);
        log.info("满减{}，赠送积分{}", payAmount, multiply);

    }

}