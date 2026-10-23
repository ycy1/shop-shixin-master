package com.shop.config.mybatisplus;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {
    
    /**
     * 分页插件 + 乐观锁插件
     *
     * 多个内部拦截器的顺序有要求（官方：多租户/动态表名 -> 分页 -> 乐观锁 -> 防止全表更新删除），
     * 所以乐观锁排在分页后面，别调换。
     *
     * 乐观锁只对标了 @Version 的实体生效，目前全项目只有 ShopErpInventory#version
     * （商品库存那一行账会被多个 ERP 操作员同时改）。生效范围是 BaseMapper 的
     * updateById / update(entity, wrapper)：WHERE 会自动带 version = 旧值、SET 里 version 自增，
     * 实体 version 为 null 时不加条件（等同没开）。
     * 自定义 XML 语句（如 ShopErpInventoryMapper#increaseStock）插件不接管，那条自己写的
     * WHERE version = ? 继续留着 —— 它要在 SQL 里做 stock = stock + delta 的增量，不能改成读-改-写。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}