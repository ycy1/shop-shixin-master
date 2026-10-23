package com.shop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.entity.core.BaseEntity;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户收藏（一个消费者 + 一种类型 + 一个对象 一条，库上是 uk_user_target）
 *
 * target_id 指向哪张表由 target_type 决定（1商品 2门店 3品牌），所以这一列不建外键，
 * 存在性由服务层按类型去对应表里查（见 ShopConsumerFavoriteServiceImpl#validate）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_consumer_favorite")
@ApiModel(value = "用户收藏")
public class ShopConsumerFavorite extends BaseEntity {

    // 和购物车那张表的 user_id 一个含义：是"这条收藏属于哪个消费者"，不是"后台谁录的"（留痕看 create_by）。
    // 所以既不加 NEVER（表单要能改归属），也别照抄门店/创建人那套数据范围，收窄只认消费者账号。
    @ApiModelProperty(value = "收藏的消费者用户ID(sys_user.id)")
    private Long userId;

    @ApiModelProperty(value = "收藏类型：1商品 2门店 3品牌（字典 favorite_target_type）")
    private Integer targetType;

    @ApiModelProperty(value = "收藏对象ID：按 target_type 分别指向 shop_product/shop_store/shop_brand")
    private Long targetId;

    // 一人一对象只留一条（uk_user_target），取消收藏只是软删、再收藏是把那一行复活，create_time 停在第一次那一趟，
    // 所以列表上的「收藏时间」读这一列 —— 口径同浏览足迹的 browse_time。库上 NOT NULL DEFAULT CURRENT_TIMESTAMP，
    // 新收藏那两路（前台 insert、后台代客录入）不用显式给，只有复活那一路手写 NOW()。
    @ApiModelProperty(value = "收藏时间：最近一次收藏上这一对的时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime favoriteTime;
}
