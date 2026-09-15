package com.shop.quartz;

import com.shop.utils.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("task")
@RequiredArgsConstructor
public class TaskQuartz {

    private final RedisUtil redisUtil;

    public void neatMultipleParams(String s, Boolean b, Long l, Double d, Integer i) {
    }

    public void neatParams(String params) {
        System.out.println("执行有参方法：" + params);
    }

    public void neatNoParams() {
        System.out.println("执行无参方法");
    }

}
