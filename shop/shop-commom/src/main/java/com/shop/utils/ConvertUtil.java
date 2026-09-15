package com.shop.utils;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author xxj
 * @title ConvertUtil
 * @date 2026/8/29 19:31
 * @description TODO
 */
public class ConvertUtil {

    /**
     * 字符串转换成Map
     * @param str 1=账号密码,2=手机验证码,3=邮箱验证码
     * @param clazz
     * @return
     */
    public static Map StrToMap(String str, Class clazz) {
        // 输出: {1=账号密码, 2=手机验证码, 3=邮箱验证码}
        return Arrays.stream(str.split(","))
                .map(pair -> pair.split("="))
                .filter(arr -> arr.length == 2)
                .collect(Collectors.toMap(
                        arr -> clazz == Integer.class? Integer.parseInt(arr[0]) :arr[0],   // key
                        arr -> arr[1]    // value
                ));
    }
}
