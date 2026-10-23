package com.shop.annotation;

import io.swagger.annotations.ApiModelProperty;

import java.lang.annotation.*;

/**
 * @author: quequnlong
 * @date: 2024/12/28
 * @description: 规则分组
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RuleGroup {
    String ORDER = "order";
    String RISK = "risk";
    String POINTS = "points";
    /** 下单用券那一组：一条与券类型无关的判据 + 四档券类型规则，全在 com.shop.rules.coupon（规则只写拒绝码，不算钱也不写文案） */
    String COUPON = "coupon";


    @ApiModelProperty(value = "规则key")
    String key(); // 必填

//    @ApiModelProperty(value = "执行顺序")
//    int order() default 1;
}
