import cn.dev33.satoken.dao.SaTokenDaoRedisJackson;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.lianjiatech.retrofit.spring.boot.core.RetrofitScan;
import com.shop.annotation.RuleGroup;
import com.shop.client.HttpApi;
import com.shop.entity.SysOrder;
import org.jeasy.rules.annotation.Rule;
import org.jeasy.rules.api.*;
import org.jeasy.rules.core.DefaultRulesEngine;
import org.jeasy.rules.spel.SpELRule;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.Map;

/**
 * @author xxj
 * @title Test
 * @date 2025/7/12 20:07
 * @description TODO
 */
@SpringBootApplication(scanBasePackages = {"com.shop.client", "easyrules","com.shop.config.easyrules"},
        exclude = {
                DataSourceAutoConfiguration.class,
                MybatisPlusAutoConfiguration.class,
                RedisAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class,
                SaTokenDaoRedisJackson.class
        })
@ComponentScan(
        basePackages = {"com.shop.client", "easyrules","com.shop.config.easyrules"}
)
@RetrofitScan("com.shop.client") // 扫描该包下的 @RetrofitClient 接口
@SpringBootTest(classes = RulesTest.class)
public class RulesTest {



    @Configuration
    static class TestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return Jackson2ObjectMapperBuilder.json()
                    .modules(new JavaTimeModule())
                    .build();
        }

    }

    @Autowired
    private RulesEngine rulesEngine;

    @Autowired
    @Qualifier("orderRules")
    private Rules orderRules;

    @Autowired
    @Qualifier("pointsRules")
    private Rules pointsRules;

    @org.junit.jupiter.api.Test
    public void test() {
        Facts facts = new Facts();
        SysOrder orderFact = new SysOrder();
        orderFact.setOrderNo("20230712-0001");
        orderFact.setDiscountAmount(new BigDecimal("750"));
        BigDecimal discountAmount = orderFact.getDiscountAmount();
        facts.put("orderFact", orderFact);
        facts.put("payAmount", discountAmount);
        facts.put("sale", 0.9);

        rulesEngine.fire(orderRules, facts);
        System.out.println(orderFact.getPayAmount());

        rulesEngine.fire(pointsRules, facts);
        System.out.println(orderFact.getTotalAmount());



    }



}
