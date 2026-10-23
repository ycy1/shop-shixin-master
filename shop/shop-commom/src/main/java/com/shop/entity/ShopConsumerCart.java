package com.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 购物车（SKU 维度：一个消费者 + 一个规格 一条，库上是 uk_user_sku；
 * 同一商品下单买两个规格就是两条，各自记自己的单价和数量）
 *
 * category_id / brand_id / store_id 是加购时从商品上冗余的快照，由服务层按 sku_id 回填，
 * 表单里不给（见 ShopConsumerCartServiceImpl#validate）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_consumer_cart")
@ApiModel(value = "购物车")
public class ShopConsumerCart extends BaseEntity {

    // 这一列和其它 shop_* 表的 user_id 含义不同：别处是"创建人"，这里是购物车归属的消费者，
    // 后台创建人留痕看 create_by。消费者账号（account_type=3）的列表按这一列=登录 id 收窄，
    // 详情/加/改/删再校验一次归属；门店、送货员账号不收窄（购物车跟他们无关，收窄了就是空列表）。
    // 不加 NEVER：表单要能改归属，锁在这里锁不动被校验拦住的路。
    @ApiModelProperty(value = "消费者用户ID(sys_user.id)")
    private Long userId;

    @ApiModelProperty(value = "商品SKU ID(shop_product_sku.id)；购物车记的是某个规格，不是整件商品")
    private Long skuId;

    @ApiModelProperty(value = "商品分类ID(shop_category.id)，加购时从商品冗余")
    private Long categoryId;

    // 可空且会被服务层重新回填：全局 field-strategy=not_empty 会跳过 null，
    // 换到一个没有品牌的商品时这一列清不掉，所以显式 IGNORED
    @ApiModelProperty(value = "品牌ID(shop_brand.id)，加购时从商品冗余")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long brandId;

    // 同上：商品不限定门店时要能把这一列写回 NULL
    @ApiModelProperty(value = "门店ID(shop_store.id)，NULL=不限定门店")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long storeId;

    @ApiModelProperty(value = "数量")
    private Integer quantity;

    @ApiModelProperty(value = "是否选中：1选中 0未选中")
    private Integer selected;
}
