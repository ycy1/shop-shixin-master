package com.shop.rules.coupon;

import com.shop.annotation.RuleGroup;
import com.shop.entity.ShopActivityCoupon;
import com.shop.entity.ShopActivityCouponUser;
import com.shop.enums.CouponRejectEnum;
import com.shop.utils.CouponUtils;
import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Fact;
import org.jeasy.rules.annotation.Rule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 与券类型无关的那几道：这四条档位规则都判不了它们（它们跟"这一档怎么折"没关系），所以合成一条排在最前面先过完。
 *
 * 八道是一条 else-if 从上到下短路，顺序就是原来"准入 → 范围 → 门槛"那三条规则的顺序，谁先命中谁说话：
 * 后面那道即使也不合格也不会覆盖前一个码，买家只看第一条理由。
 *
 * - 归属：别人的券拿 id 打进这一路就是白捡一张券，必须拦住（口径同收货地址那一道）
 * - status：只认 0 未使用，1 已使用 / 2 已过期 / 3 已作废 都拒
 * - expire_time：这一期没有定时任务把过期的券推成 2 已过期，所以过期只能拿这一格现判
 * - 模板：只拦 4 已取消；3 已结束不拦（那只是"不再发了"，发到手上的券按它自己的有效期走完）。
 *   模板那两道必须排在范围之前 —— 命中行是拿模板上的 scope_value 筛出来的，模板读不到就别往下走
 * - 适用范围：scope_type 落在 1/2/3/4 之外就拒。库里那一列是 tinyint + 字典，走到这一档说明是别处塞进来的脏值，
 *   认不出范围就别抵钱，抵错了是要退真金白银的
 * - 命中行：为空也拒。名单读不出来（那格 NULL、或者 json 拆不出这一档）也算没命中 ——
 *   一张本该只管几件商品的券，绝不能在名单缺失时退化成全场券，那是白送钱
 *   （四档各自怎么筛、指定分类为什么连下级一起算，口径在 {@link CouponUtils#matchLines}，调用方已经跑完传进事实）
 * - 使用门槛：拿传进来的 base 比 threshold_amount，空或 0 = 无门槛直接放过。比的是**这张券命中那些行**的合计
 *   而不是整单货值（我替他拍的）：券有适用范围，"满 100 减 30"的指定分类券不该靠别的分类的行来凑数
 *
 * 这里只出码，那几句中文在调用方按码拼（口径见 ShopOrderCouponServiceImpl#rejectText）。
 */
@Component
@Rule(name = "与券类型无关的判据", description = "归属 / status=0 / 未过期 / 模板在且未取消 + 适用范围命中 + 够得上门槛", priority = 10)
@RuleGroup(key = RuleGroup.COUPON)
public class CouponAccessRule {

    @Condition
    public boolean notJudgedYet(@Fact(CouponCalcFact.KEY) CouponCalcFact calc) {
        return calc.passed();
    }

    @Action
    public void rejectInaccessible(@Fact(CouponCalcFact.KEY) CouponCalcFact calc) {
        ShopActivityCouponUser coupon = calc.getCoupon();
        Long ownerId = coupon.getUserId();
        Integer status = coupon.getStatus();
        LocalDateTime expireTime = coupon.getExpireTime();
        ShopActivityCoupon template = calc.getTemplate();
        BigDecimal threshold = CouponUtils.money(coupon.getThresholdAmount());
        if (ownerId == null || !ownerId.equals(calc.getHolderId())) {
            calc.reject(CouponRejectEnum.NOT_OWNER);
        } else if (status == null || status != CouponUtils.USER_STATUS_UNUSED) {
            calc.reject(CouponRejectEnum.NOT_UNUSED);
        } else if (expireTime != null && expireTime.isBefore(calc.getNow())) {
            calc.reject(CouponRejectEnum.EXPIRED);
        } else if (template == null) {
            calc.reject(CouponRejectEnum.TEMPLATE_MISSING);
        } else if (template.getStatus() != null && template.getStatus() == CouponUtils.TEMPLATE_STATUS_CANCELED) {
            calc.reject(CouponRejectEnum.TEMPLATE_CANCELED);
        } else if (!CouponUtils.isKnownScope(coupon.getScopeType())) {
            calc.reject(CouponRejectEnum.SCOPE_UNKNOWN);
        } else if (!calc.hasHit()) {
            calc.reject(CouponRejectEnum.SCOPE_NO_HIT);
        } else if (threshold.signum() > 0 && calc.getBase().compareTo(threshold) < 0) {
            calc.reject(CouponRejectEnum.THRESHOLD_NOT_REACHED);
        }
    }
}
