package com.shop.enums;

/**
 * @author xxj
 * @title Test
 * @date 2026/8/29 20:07
 * @description TODO
 */
public enum UserStatusEnum {

    ONE("启用", 1),  TWO("禁用", 0);

    private String lable;

    private Integer value;


    UserStatusEnum(String lable, Integer value) {
        this.lable = lable;
        this.value = value;
    }

    public String getLable() {
        return lable;
    }

    public void setLable(String lable) {
        this.lable = lable;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }
}
