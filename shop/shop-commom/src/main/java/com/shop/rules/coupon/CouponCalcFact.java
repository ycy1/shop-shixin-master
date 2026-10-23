package com.shop.rules.coupon;

import com.shop.entity.ShopActivityCoupon;
import com.shop.entity.ShopActivityCouponUser;
import com.shop.entity.ShopOrderItem;
import com.shop.enums.CouponRejectEnum;
import lombok.Getter;
import lombok.Setter;
import org.jeasy.rules.api.Facts;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 一张券在一次下单里的事实。
 *
 * 构造进来的那几格全是**业务侧备好的输入**：调用方（ShopOrderCouponServiceImpl）读库拿到这一张券和它的模板，
 * 再拿 CouponUtils 那一套口径算好「这张券命中这一单里的哪几行、那些行值多少钱」一起传进来 ——
 * 所以规则一次库都不查、也不另算一份钱，只在现成的输入上判「这一张过不过」。
 *
 * 写出去的只有三格：discount / freeShipping 是认领了这一张的那条券类型规则从 CouponUtils 挂回来的结果，rejectCode 是唯一的裁决。
 *
 * 规则**只写一个拒绝码、不写那句中文**：怎么把「不过」说给买家听是业务的事 —— 那几句话要带券名、金额、日期，
 * 由调用方按这个码拼（见 ShopOrderCouponServiceImpl#rejectText）。
 *
 * 规则也**不抛异常**：引擎的参数是 skipOnFirstFailedRule(false)，一条规则失败并不保证异常会传到调用方，
 * 而"券没核销、钱却抵了"是要退真金白银的，所以判不过就写个码，由调用方拿到后当场抛出去整单回滚。
 *
 * 只塞一个对象进 {@link Facts}（而不是券、行、金额各塞一格）是为了让每条规则的 @Condition 都只看得见
 * 它该看得见的那部分，也免得「哪一格叫什么名字」这种约定散落在几个规则类里。
 *
 * 一次 fire 只算一张券：多张券之间"这一行还剩多少可抵"是跨券的状态，那个不在规则里（见服务层的摊行那一段）。
 */
@Getter
public class CouponCalcFact {

    /** 规则和事实之间唯一的键名 */
    public static final String KEY = "couponCalc";

    /** 这一张用户券：券类型、面额、门槛、最高抵扣、适用范围类型、状态、过期时间都读它上面的快照 */
    private final ShopActivityCouponUser coupon;

    /** 券模板：只有范围名单（scope_value）和适用门店（store_id）两格在模板上，用户券表没存这两格 */
    private final ShopActivityCoupon template;

    /** 这张券命中了哪几行，由调用方拿 CouponUtils#matchLines 筛好传进来（规则不参与筛选，只判筛出来是不是空） */
    private final List<ShopOrderItem> hit;

    /** 命中行的小计合计（原价口径）：调用方拿 CouponUtils#baseOf 算好传进来，满减门槛比它、折扣券拿它当基数 */
    private final BigDecimal base;

    /** 下单人（sys_user.id）：券必须在他名下，别人的券拿 id 打进来就是白捡钱 */
    private final Long holderId;

    /** 这一次算钱的时刻，过期判定用它（下单和试算各算各的 now） */
    private final LocalDateTime now;

    /** 这一张最多抵多少（还没拿"这些行还剩多少可抵"压顶，那一步跨券，只有调用方看得见），只由 {@link #applyDiscount} 写 */
    private BigDecimal discount;

    /** 免邮券置真：它抵的是配送费，那一笔钱要等建配送单那一步才算得出来 */
    @Setter
    private boolean freeShipping;

    /** 被拒掉的哪一档：谁先命中谁说话，后面的规则不再改口径 */
    private CouponRejectEnum rejectCode;

    public CouponCalcFact(ShopActivityCouponUser coupon, ShopActivityCoupon template,
                          List<ShopOrderItem> hit, BigDecimal base, Long holderId, LocalDateTime now) {
        this.coupon = coupon;
        this.template = template;
        this.hit = hit;
        this.base = base;
        this.holderId = holderId;
        this.now = now;
    }

    /** 交给引擎的那一袋事实 */
    public Facts toFacts() {
        Facts facts = new Facts();
        facts.put(KEY, this);
        return facts;
    }

    /** 还没有任何一条规则说过话，后面的规则才接着往下判 */
    public boolean passed() {
        return rejectCode == null;
    }

    /** 命中行不是空的（共用判据先判它，四档规则只在这之上才挂结果） */
    public boolean hasHit() {
        return hit != null && !hit.isEmpty();
    }

    /**
     * 档位规则把折出来的那一个数挂回事实：抵不出正数就顺手拒掉这一张。
     *
     * 三条金额档都走这一刀，同一个判据不抄三遍（免邮档不挂货款，所以不归它）。
     * 为什么宁可拒也不静默按 0 走：抵 0 就是"券没收掉、钱也没给抵"，买家页面上看到的金额和真收的钱对不上。
     */
    public void applyDiscount(BigDecimal discount) {
        this.discount = discount;
        if (discount.signum() <= 0) {
            reject(CouponRejectEnum.NO_DISCOUNT);
        }
    }

    public void reject(CouponRejectEnum code) {
        if (rejectCode == null) {
            rejectCode = code;
        }
    }
}
