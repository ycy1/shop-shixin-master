package com.shop.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.config.interceptor.ApiAccessLogInterceptor;
import com.shop.controller.BaseAppController;
import com.shop.controller.BaseMpController;
import com.shop.entity.SysFileOss;
import com.shop.enums.FileOssEnum;
import com.shop.mapper.SysFileOssMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author blue
 * @date 2022/3/10
 * @apiNote
 */
@Configuration
@EnableWebMvc
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final SysFileOssMapper sysFileOssMapper;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");

        SysFileOss sysFileOss = sysFileOssMapper.selectOne(new LambdaQueryWrapper<SysFileOss>()
                .eq(SysFileOss::getPlatform, FileOssEnum.LOCAL.getValue()));

        if (sysFileOss != null) {
            //本地存储升级版
            registry.addResourceHandler(sysFileOss.getPathPatterns())
                    .addResourceLocations("file:" + sysFileOss.getStoragePath());
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApiAccessLogInterceptor());
    }

    /**
     * 注册跨域信息
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*") // 允许所有跨域地址
                .allowedHeaders("*")
                .allowedMethods("*")
                .maxAge(3600);
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // 为所有继承BaseAppController的类添加前缀
        configurer.addPathPrefix("/api/app", BaseAppController.class::isAssignableFrom);
        configurer.addPathPrefix("/wx/mp", BaseMpController.class::isAssignableFrom);
    }

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    /**
     * 配置消息转换器，确保字符串响应使用UTF-8编码
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 确保 StringHttpMessageConverter 使用 UTF-8
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof StringHttpMessageConverter) {
                ((StringHttpMessageConverter) converter).setDefaultCharset(StandardCharsets.UTF_8);
            }
        }
    }

    /**
     * 配置异步支持，为响应式流提供专用的线程池
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(getAsyncExecutor());
        configurer.setDefaultTimeout(30000); // 设置超时时间为30秒
    }

    /**
     * 创建异步任务执行器线程池
     */
    @Bean
    public ThreadPoolTaskExecutor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // 核心线程数
        executor.setMaxPoolSize(20); // 最大线程数
        executor.setQueueCapacity(100); // 队列容量
        executor.setThreadNamePrefix("async-stream-"); // 线程名前缀
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // 拒绝策略
        executor.setWaitForTasksToCompleteOnShutdown(true); // 关闭时等待任务完成
        executor.setAwaitTerminationSeconds(60); // 等待终止的最大时间
        executor.initialize();
        return executor;
    }

}
