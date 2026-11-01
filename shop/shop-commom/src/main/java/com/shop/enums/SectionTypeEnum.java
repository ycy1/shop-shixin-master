package com.shop.enums;

/**
 * 专区类型（shop_product / shop_store / shop_brand 的 section_type 那一格）。
 *
 * 专区的语义：一个可空 id（section_id）指向某个专区的对象，类型决定它指向哪里。
 * 现在只有一档：1 潮玩 —— section_id 是 shop_trendy.id（潮玩系列）。
 * 以后加别的专区时，这里补档、读取那侧按类型分叉即可。
 *
 * 表单里没有这一格：保存时按「有没有选专区」自动写（选了 = 1，清空 = 0），
 * 所以运营只需要选专区，不会出现「选了系列、类型没跟上」的半截数据。
 */
public enum SectionTypeEnum {

    /** 不属于任何专区 */
    NONE(0),

    /** 潮玩专区：section_id 指向 shop_trendy.id */
    TRENDY(1);

    private final int code;

    SectionTypeEnum(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
