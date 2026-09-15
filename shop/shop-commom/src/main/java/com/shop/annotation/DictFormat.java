package com.shop.annotation;

import com.alibaba.fastjson2.JSONArray;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author xxj
 * @title DictFormat
 * @date 2026/8/28 09:53
 * @description TODO
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DictFormat {
    // 用于指定字典编码，例如 "user_status", "gender"
    String code() default "";

    // 用于指定字典数据，例如 "1=正常,2=禁用"
    String data() default "";

    // 用于指定枚举字典类型
    enum None{}
    Class<? extends Enum<?>> enumType() default None.class;

    // 用于指定数据库实体类类型 (数据量大时慎用，推荐在数据查询时自定义sql)
    Class <?> dbClazz() default Void.class;

}
