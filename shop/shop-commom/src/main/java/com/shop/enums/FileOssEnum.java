package com.shop.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author: quequnlong
 * @date: 2025/2/21
 * @description:
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public enum FileOssEnum {
    QINIU("qiniu"),

    ALI("ali"),

    TENCENT("tencent"),

    MINIO("minio"),

    LOCAL("local"),

    FASTDFS("fastdfs");

    private String value;

}
