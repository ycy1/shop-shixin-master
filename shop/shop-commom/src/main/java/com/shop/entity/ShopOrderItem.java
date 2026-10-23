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
 * 订单明细（一张 sys_order 对应 N 行，一行一个**具体规格**：同一商品买两个规格就是两行）
 *
 * 这一行由小程序侧下单接口写入（ShopOrderCreateServiceImpl），商品名称/规格文本/主图/单价都是**下单时的快照**：
 * 商品以后改名、改价、换主图、改规格都不回刷已下的单，所以本表存着这几列，列表和详情读的都是本表的值。
 * 单价取的是这一行那条规格的 shop_product_sku.product_price，规格文本抄的是它的 spec_text。
 * 金额（price / total_amount）同样是服务端算出来落库的，不参与后续重算。
 *
 * 后台这一页只读：没有新增/修改/删除接口，只有分页和详情。
 * 两个例外由别的链路回写：
 * 1. ship_status —— 下单写 0 待发货，之后由「商品出库」的出库动作把这行的货出满时推成
 *    3 已出库（ShopErpSaleOutboundServiceImpl），不再往 sys_order.status 上写"已出库/已发货"——
 *    一张订单几行商品可能从几个仓分别出、分别发，这一档本来就该按行记。
 *    整单的行都到 3 之后，操作员在「商品出库」列表那颗「生成配送单」的弹框里点「确认」，把这一单的配送单推成 1 待接单。
 *    配送单不由这里建：它在**统一下单**那一刻就跟着这张订单建好了（重量、距离、配送费都在那一次定死，
 *    买家付的钱里就含着它），支付回调再把它换绑到这一行所属的出库单上
 *    （shop-api 的 ShopOrderDeliveryService；一出库单一配送，见 ShopDeliveryService#confirmDelivery）。
 * 2. returned_quantity —— 退款回调成功时把这行这次退掉的件数累加上（ShopRefundCallbackServiceImpl），
 *    同时按累加后的进度把 ship_status 写成退货轨道上的那一档：退满（= quantity）→ 2 已退货，
 *    只退了一部分 → 4 部分退货。所以"这一行还能退几件" = quantity − 本列，由服务层算，不落库。
 *    和 2 一样，4 也是退货轨道上的状态：一旦这行退过货，发货那边的回写（0→3、3→1）就不再推它，
 *    这行的状态从此只反映退到哪一步了。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_order_item")
@ApiModel(value = "订单明细")
public class ShopOrderItem extends BaseEntity {

    /**
     * 发货状态（字典 order_ship_status），号按"先占号"排、展示顺序由字典 sort 管：
     * 发货那条路是 0 待发货 → 3 已出库 → 1 已发货，退货那条路是 4 部分退货 → 2 已退货
     * （4/2 由退款回调按 returned_quantity 的进度写：退了一部分给 4，退满给 2，
     * 半退的行不会退回发货那几档）。
     * 3 是 2026-09-21 把「已出库」从 sys_order.status 搬到明细行时新加的，所以号不连着；
     * 4 是同一轮退货接口新加的那一档。
     */
    public static final int SHIP_STATUS_PENDING = 0;
    public static final int SHIP_STATUS_SHIPPED = 1;
    public static final int SHIP_STATUS_RETURNED = 2;
    public static final int SHIP_STATUS_OUTBOUND = 3;
    public static final int SHIP_STATUS_PARTIAL_RETURNED = 4;

    @ApiModelProperty(value = "订单ID(sys_order.id)")
    private Long orderId;

    // 语义同 shop_consumer_cart.user_id：这一列是"这条明细属于哪个下单消费者"，不是后台创建人
    // （创建人留痕在 create_by）。消费者账号（account_type=3）的列表按这一列=登录 id 收窄。
    @ApiModelProperty(value = "下单消费者ID(sys_user.id)")
    private Long userId;

    @ApiModelProperty(value = "所属门店ID(shop_store.id)，下单时从商品冗余，NULL=非门店商品")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long storeId;

    // 下单那一刻先定一次：按收货地址的经纬度（ShopOrderCreateDto.addressId，没传才用 sys_user 上那份）
    // 在 shop_erp_inventory 里找有可用库存且最近的启用仓库，选中就把那一行的 locked_stock 加上本行数量
    //（ShopOrderCreateServiceImpl#create）。
    // 支付回调生成出库单时还会按这一行的**门店**再改派一次（门店仓优先；就近那一档的起点是这一单的收货地址
    // sys_order.address_id，用不上才退回门店坐标，见 ShopOrderPaidServiceImpl#routeWarehouse），
    // 改派成功就是"新仓锁上 + 旧仓还掉 + 这一列跟着改"三件一起做，
    // 所以这一列和库存锁定始终是同一件事的两半，要么一起成功要么整单回滚
    @ApiModelProperty(value = "发货仓库ID(shop_erp_warehouse.id)，下单就近分仓、生成出库单时按门店改派，NULL=历史数据未分仓")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long warehouseId;

    @ApiModelProperty(value = "订单号（冗余 sys_order.order_no）")
    private String orderNo;

    @ApiModelProperty(value = "商品SKU ID(shop_product_sku.id)；下单选的就是某个具体规格，整件商品的事在 shop_product")
    private Long skuId;

    @ApiModelProperty(value = "商品名称（下单时快照）")
    private String productName;

    @ApiModelProperty(value = "规格文本（下单时快照），NULL=无规格")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String specText;

    @ApiModelProperty(value = "商品主图（下单时快照）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String image;

    @ApiModelProperty(value = "成交单价")
    private BigDecimal price;

    @ApiModelProperty(value = "购买数量")
    private Integer quantity;

    // 不是表单列：只有退款回调会加它（建单/审核都不动，所以"审核拒绝的单可退数没被吃掉"这条成立）。
    // 「可退数」= quantity − 本列，服务层每次现算，不落库
    @ApiModelProperty(value = "已退货数量（累计，退款成功回调写入）；可退数 = quantity − 本列")
    private Integer returnedQuantity;

    @ApiModelProperty(value = "小计（原价口径）= price × quantity")
    private BigDecimal totalAmount;

    // 不是入参：2026-09-29 起请求体里没有"这一行优惠多少"那一格了，这一列由统一下单按这一单用的优惠券摊出来
    //（ShopOrderCouponService#apply：券的适用范围命中哪些行就摊在哪些行上，一行最多摊到小计）。
    // 免邮券不在这儿——它抵的是配送费，只记在 sys_order.discount_amount 上
    @ApiModelProperty(value = "这一行的优惠金额（服务端按优惠券摊出来，行实付 = 小计 − 本列 − 积分抵的钱）")
    private BigDecimal discountAmount;

    // 请求体里买家自己选"这一行花几分"（ShopOrderCreateDto.Item#usedPoints），但能抵多少钱由服务端按
    // sys_config 的汇率折、折不过这一行券后剩下的钱，客户端传了金额也不认（口径同单价和 discount_amount）。
    // 支付回调把这笔分结转成真扣走，退款再按退掉的件数比例还回来
    @ApiModelProperty(value = "这一行使用的积分（分数取自请求体、服务端只做校验和折算；行实付 = 小计 − 优惠 − 本列抵的钱）")
    private Integer usedPoints;

    // 也不是入参：下单那一刻收不到"这件送几分"，那是支付回调送分时才算出来的（口径同 returned_quantity）。
    // 落这一格是因为它是**当时**的档位：商品后来改了 gain_points，退款要收回当初送的分就只能读这一行快照
    @ApiModelProperty(value = "这一行赠送的积分（支付回调送分时按 shop_product_sku.gain_points × 件数落库的快照，"
            + "退款收回送分读这一格；0=没送。老数据（这一列加上之前已支付的单）也是 0，按 0 收、不追缴）")
    private Integer gainPoints;

    @ApiModelProperty(value = "发货状态：0待发货 3已出库 1已发货 4部分退货 2已退货（字典 order_ship_status；「已出库」由商品出库的出库动作在这一行的货出满时回写，4/2 由退款回调按已退数量回写）")
    private Integer shipStatus;
}
