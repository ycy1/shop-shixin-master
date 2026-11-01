package com.shop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 消息通知那一段异步活儿用的线程池（ NotificationsUtil 里 @Async("notificationExecutor") 认的就是这个 bean 名）。
 *
 * 为什么单开一个而不借用现成的：项目里原先没有 @EnableAsync，公告发送用的那份线程池是 SysNoticeServiceImpl
 * 页内自己 new 的（一个 service 实例一个池，关掉还漏线程）；这一轮把公告并进事件那条链，池就得由容器管。
 * 队列给到 500、超了让提交的那条线程自己跑：宁可慢一点也不能把已经提交成功的通知悄悄丢掉。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notification-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
