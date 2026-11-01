package com.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 购物车（SKU 维度 + 来源：一个消费者 + 一个规格 + 一个来源（哪一类、哪一台）一条，库上是
 * uk_user_sku_source(user_id, sku_id, source_type, source_id)；同一商品下单买两个规格就是两条，
 * 各自记自己的单价和数量；同一规格如果既自己加购过、又被两台盒抽中过，就是好几行 —— 见下面 sourceType
 * 和 sourceId 两格，一行只有一个价，两种来源挤一行必有一方的价被刷掉。
 * 端盒那一趟是另一种形状：一整盒躺一行、sku_id 填 0 当哨兵，盒里有哪几款按 source_id 现查那台盒的款式清单）
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

    @ApiModelProperty(value = "商品SKU ID(shop_product_sku.id)，无规格的默认SKU 也算一条规格；0=一整盒（端盒那一趟，见 SKU_ID_WHOLE_BOX）")
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

    /**
     * 消费单价：这一行按这个价算钱，下单落 shop_order_item.price 也是它。
     *
     * 为什么单价要落在购物车行上而不是下单时现联 SKU —— 抽中入车的那几件挂的是**盲盒的单抽价**
     *（盒上配的 price），不是这款规格的商品原价，现联取不到那个数。
     * NULL = 这一格没写过，下单回落到这条规格的 product_price（后台代客加购的那些行就是 NULL，
     * 那本来就是"按现价买"，不需要专门补一格）。
     *
     * 不标 IGNORED：前台「改数量/勾选」和后台整行提交都不带这一格，全局 not_empty 正好让它保持原值
     *（口径同 ip_id 那类"只由特定那一路写"的列反过来用）。
     */
    @ApiModelProperty(value = "消费单价（这一行按它算钱），NULL=按这条规格的现价")
    private BigDecimal price;

    @ApiModelProperty(value = "是否选中：1选中 0未选中")
    private Integer selected;

    /** 来源：自己加购的（按这条规格的现价算钱） */
    public static final int SOURCE_PRODUCT = 0;
    /** 来源：盲盒抽中入车的（按盲盒给的价格算钱，见 ShopTrendyBoxDrawServiceImpl#addToCart） */
    public static final int SOURCE_BOX = 1;
    /** 来源ID：这一行不是某个来源带来的（自己加购、后台代客补录）—— 库里写 0，不写空 */
    public static final long SOURCE_ID_NONE = 0L;

    /**
     * 来源类型（字典 cart_source_type）：0 普通商品 1 盲盒商品。
     *
     * 为什么一行要认自己是谁：一个规格一行、一行只有一个单价，抽中入车（按盒价）和详情页手动加购
     * （按原价）挤在同一行上就会互相刷掉对方的价 —— 谁最后进车，整行就按谁的价算钱，先那一批白算了。
     * 现在两种来源各一行、各叠各的数量、各算各的价，唯一键也跟着带上这一格（uk_user_sku_source）。
     */
    @ApiModelProperty(value = "来源：0普通商品（自己加购） 1盲盒商品（抽中入车），字典 cart_source_type")
    private Integer sourceType;

    // 刻意不标 IGNORED：后台「修改」那颗是整行提交、表单里没有这两格，标了就会把盲盒行的来源盒抹成 0
    //（口径同商城照片的 audit_msg、用户票的 qr_img）——抹完这一行就跟另一台盒抽中的那行撞键了。
    /**
     * 来源ID：盲盒商品 = 抽中它的那台盒(shop_trendy_box.id)；普通商品 = 0（{@link #SOURCE_ID_NONE}）。
     *
     * 为什么不是空：这一格进唯一键（uk_user_sku_source 四格 = 人 + 规格 + 来源类型 + 来源ID），
     * 而 MySQL 的唯一索引不约束 NULL —— 留空等于「自己加购」那一半根本不去重，手动加购能插出无数条重复行。
     * 为什么不同款的盒要分行：同一款在不同盒里配的单抽价可以不一样，并成一行就是后一台盒的价刷掉前一台
     *（increaseQuantityBySku 那句 price = IFNULL(#{price}, price)），先抽中的那件白按贵的那笔结。
     */
    @ApiModelProperty(value = "来源ID：盲盒商品=抽中它的那台盒(shop_trendy_box.id)；普通商品（自己加购）为0")
    private Long sourceId;

    /** 整盒行（端盒那一趟）的 sku_id 哨兵：这一行买的是一整盒，不指向某一条规格 */
    public static final Long SKU_ID_WHOLE_BOX = 0L;

    /**
     * 整盒那一行盒内有哪几款、各几件，库里**不存**：按 {@link #sourceId} 现查那台盒的款式清单
     *（shop_trendy_box_item，见 ShopTrendyBoxStyleService）。
     *
     * 口径是「一整盒 = 这台盒当前配置的款式各一件」：盒里的款式后来被运营改过，这一行跟着改；
     * 那台盒被删了就查不出款式，这一行在下单那一刻当场报错，不硬结一份看不见的货。
     * 数量那一格是「几盒」，同一台盒端第二次并到这一行上 +1、单价按最新那次的端盒价覆盖
     *（和商品那一路「后一次加购定这一行的价」同一个口径）。
     */
}
