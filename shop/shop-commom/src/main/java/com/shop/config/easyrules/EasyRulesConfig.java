package com.shop.config.easyrules;

import com.shop.annotation.RuleGroup;
import org.jeasy.rules.annotation.Rule;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngine;
import org.jeasy.rules.api.RulesEngineParameters;
import org.jeasy.rules.core.DefaultRulesEngine;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * @author xxj
 * @title EasyRulesConfig
 * @date 2026/9/28 18:30
 * @description TODO 规则引擎配置类
 */
@Configuration
public class EasyRulesConfig {
    @Bean
    public RulesEngine rulesEngine() {
        RulesEngineParameters params = new RulesEngineParameters()
                .skipOnFirstAppliedRule(false)  // 所有匹配规则都执行
                .skipOnFirstFailedRule(false);  // 某条规则异常不影响后续
        return new DefaultRulesEngine(params);
    }


    @Bean("orderRules")
    public Rules orderRules(ApplicationContext context) {
        return rulesOf(context, RuleGroup.ORDER);
    }

    @Bean("pointsRules")
    public Rules pointsRules(ApplicationContext context) {
        return rulesOf(context, RuleGroup.POINTS);
    }

    /**
     * 下单用券那一组（一条与券类型无关的判据排最前，后面四条按券类型各认领一档，按 @Rule 的 priority 从小到大跑）。
     * 规则类都在 com.shop.rules.coupon，一次 fire 只管一张券。
     * 规则只判"这一张过不过"（往事实上写一个拒绝码），算式和给买家的文案都不在规则里。
     */
    @Bean("couponRules")
    public Rules couponRules(ApplicationContext context) {
        return rulesOf(context, RuleGroup.COUPON);
    }

    /**
     * 把容器里带了 {@link Rule} 注解的 bean 按 {@link RuleGroup} 的 key 收成一组。
     *
     * 注册前显式按 priority 排一遍：getBeansWithAnnotation 给的是容器里的任意顺序，而一组规则之间是有先后的
     *（用券那一组，范围没命中就不该去比门槛，门槛没过就不该再折算金额）。
     */
    private static Rules rulesOf(ApplicationContext context, String group) {
        List<Object> matched = new ArrayList<>();
        Map<String, Object> beans = context.getBeansWithAnnotation(Rule.class);
        for (Object bean : beans.values()) {
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            RuleGroup key = targetClass.getAnnotation(RuleGroup.class);
            if (key != null && key.key().equals(group)) { // 只注册本组的规则
                matched.add(bean);
            }
        }
        matched.sort(Comparator.comparingInt((Object bean) ->
                AopUtils.getTargetClass(bean).getAnnotation(Rule.class).priority()));
        Rules rules = new Rules();
        matched.forEach(rules::register);
        return rules;
    }
}
