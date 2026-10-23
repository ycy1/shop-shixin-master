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
 * 浏览足迹（一个消费者 + 一件商品 一条，库上是 uk_user_product）
 *
 * 重复浏览不新增行：服务层按 (user_id, product_id) 把 browse_time 刷成最近一次（见
 * ShopConsumerBrowseServiceImpl#insert），所以这一列是"最近浏览时间"而不是"入库时间"，
 * BaseEntity 的 create_time 才是第一次记上这条足迹的时间。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_consumer_browse")
@ApiModel(value = "浏览足迹")
public class ShopConsumerBrowse extends BaseEntity {

    // 和购物车、收藏那两张表的 user_id 一个含义：是"这条足迹属于哪个消费者"，不是"后台谁录的"（留痕看 create_by）。
    // 所以既不加 NEVER（表单要能改归属），也别照抄门店/创建人那套数据范围，收窄只认消费者账号。
    @ApiModelProperty(value = "浏览的消费者用户ID(sys_user.id)")
    private Long userId;

    // 不建外键（本项目 shop_* 表统一不建），存在性由服务层查 shop_product 校验；商品名也不冗余，列表实时联表带出
    @ApiModelProperty(value = "商品ID(shop_product.id)")
    private Long productId;

    @ApiModelProperty(value = "最近一次浏览时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime browseTime;
}
