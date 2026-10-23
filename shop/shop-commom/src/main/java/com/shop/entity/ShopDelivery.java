package com.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.entity.core.BaseEntity;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 配送单表
 *
 * **一槽两用，不换列**（2026-09-21 他改的口径："配送单不要再加 order_id / order_no，
 * 直接使用出库单号和出库id，这时候出库单还没有生成"）：
 * outbound_id / outbound_no 这一对就是唯一的绑定槽 —— 统一下单落主单之后先写**订单**的那两个值
 * （这一单多重、离多远、多少钱全在那一刻定下来，买家付的货款里已经含着那笔配送费），
 * 支付回调建出出库单之后把它们**原地覆盖**成出库单的那两个值（换绑）。
 * 所以这一对列上先后出现过两种 id，光看 id 分不出绑的是哪一头：**判据必须 id + no 两个一起比**
 * （订单号 ORDER_ 打头、出库单号 SALE_OUTBOUND_ 打头，no 对得上的才是绑了的那一条）；库里也就不能留
 * uk_outbound_id（同一个数会先被一张订单占用、再被另一张真正的出库单占用），
 * "一出库单一配送"改由服务层判（ShopDeliveryService#confirmDelivery 和它自己的 validate）。
 *
 * 也不关联门店了（2026-09-21 那轮"送货员和配送单不在关联门店"）：表上的 store_id 已删，
 * 这一单从哪儿发货只看自己那五个 sender_* 快照列 —— 建单时从这一行的发货仓抄下来，支付回调改派过仓会再刷一次，
 * 之后仓库改地址也不影响历史单。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_delivery")
@ApiModel(value = "配送单")
public class ShopDelivery extends BaseEntity {

    @ApiModelProperty(value = "配送单号，唯一；新增时不传则由系统生成（DELIVERY_+时间戳）")
    private String deliveryNo;

    /**
     * 绑定的那一张单，一槽两用：统一下单建出来这一刻是 sys_order.id，支付回调换绑后是
     * shop_erp_sale_outbound.id。表上没有单独的 order_id 列（他这轮的口径），
     * 所以想知道这一条绑的是哪一头、绑的是谁，只能 id + outbound_no 两个一起看。
     */
    @ApiModelProperty(value = "关联单ID：下单时是订单ID(sys_order.id)，支付回调换绑后是出库单ID(shop_erp_sale_outbound.id)")
    private Long outboundId;

    // 这一列不由表单决定：服务层按上面那个 id 取对应的单号回填（换绑时和 id 一起覆盖，永远对得上）
    @ApiModelProperty(value = "关联单号（冗余，服务层回填：未换绑时是订单号，换绑后是出库单号）")
    private String outboundNo;

    // 未派单时就是 NULL，"取消派单/换送货员"里的清空是正常操作，所以显式 IGNORED
    @ApiModelProperty(value = "送货员ID，NULL为未接单")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long riderId;

    @ApiModelProperty(value = "送货员姓名（服务层回填，非本表列）")
    @TableField(exist = false)
    private String riderName;

    @ApiModelProperty(value = "配送方式：1平台配送 2商家自配 3到店自提 4第三方配送（字典 delivery_type）")
    private Integer deliveryType;

    @ApiModelProperty(value = "派单方式：1系统派单 2抢单 3手动指派（字典 delivery_assign_type）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer assignType;

    @ApiModelProperty(value = "收件人姓名")
    private String receiverName;

    @ApiModelProperty(value = "收件人电话")
    private String receiverPhone;

    @ApiModelProperty(value = "收件地址")
    private String receiverAddress;

    @ApiModelProperty(value = "收件经度(GCJ-02)")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal receiverLng;

    @ApiModelProperty(value = "收件纬度(GCJ-02)")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal receiverLat;

    @ApiModelProperty(value = "发货人")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String senderName;

    @ApiModelProperty(value = "发货人电话")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String senderPhone;

    @ApiModelProperty(value = "发货地址")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String senderAddress;

    @ApiModelProperty(value = "发货经度")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal senderLng;

    @ApiModelProperty(value = "发货纬度")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal senderLat;

    // 这两格不由人填：服务层按「发货点 → 收件点」这两个地点算（distance 是 ST_Distance_Sphere 的球面
    // 直线距离，时长 = 固定取货 + 距离按平均速度折算）。两个点齐才算，缺任何一边这两格置空
    @ApiModelProperty(value = "配送距离(km，服务端按两个地点算，球面直线距离保留 2 位)")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal distance;

    @ApiModelProperty(value = "预计配送时长(分钟，服务端按距离折算)")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer estimatedTime;

    @ApiModelProperty(value = "配送费")
    private BigDecimal deliveryFee;

    // 这一笔配送费是哪条规则算出来的，服务端在算钱那一刻写死（见 ShopDeliveryServiceImpl#fillDeliveryFee）。
    // 表单里没有这一格、回传一律不认：人工把配送费改了，这笔钱就不再是规则的产物，id 清成 NULL。
    // 显式 IGNORED 才清得掉（全局 field-strategy=not_empty 会把 null 跳过）
    @ApiModelProperty(value = "计价规则ID(shop_delivery_fee_rule.id)，NULL 表示这笔配送费不是规则算的")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long feeRuleId;

    @ApiModelProperty(value = "计价规则名称（服务层按 feeRuleId 回填，非本表列）")
    @TableField(exist = false)
    private String feeRuleName;

    @ApiModelProperty(value = "小费")
    private BigDecimal tipFee;

    // 统一下单时按 Σ(shop_product_sku.weight × 购买数量) 汇总出来的（见 ShopOrderDeliveryServiceImpl#plan），
    // 表单上这一格仍可人工改（改的是这一单的备注性数据，不会回头改已经收过的配送费）
    @ApiModelProperty(value = "重量(kg)，下单时按商品重量 × 数量汇总")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal weight;

    // 表单里没填时服务层按"当前时间 + 预计时长"补一个（算出来的那两个地点同上）；填了就用填的
    @ApiModelProperty(value = "预计送达时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime expectTime;

    @ApiModelProperty(value = "派单时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime assignTime;

    @ApiModelProperty(value = "接单时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime acceptTime;

    @ApiModelProperty(value = "取货时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime pickupTime;

    @ApiModelProperty(value = "送达时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime deliveredTime;

    @ApiModelProperty(value = "取消时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime cancelTime;

    @ApiModelProperty(value = "状态：0待派单 1待接单 2已接单 3配送中 4已送达 5已取消 6异常（字典 delivery_status）")
    private Integer status;

    @ApiModelProperty(value = "取消原因")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String cancelReason;

    @ApiModelProperty(value = "异常原因")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String exceptionReason;

    // 下面几个只当查询条件用：outboundId / status 本身是本表列，直接复用
    @ApiModelProperty(value = "查询：配送单号/出库单号（未换绑时那一格里是订单号）/收件人/手机号关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;

    // 数据范围（账号类型=送货员）：服务层按登录账号查出自己的送货员档案 id 塞进来，前端传什么都不认。
    // 命中条件是"派给我的 + 还没人接的那一批"（口径见 ShopDeliveryMapper.xml 里那一段），
    // 不复用 riderId：那是搜索栏「送货员」那一格的等值条件，只会看派给自己的，拼不出未接单那一半
    @ApiModelProperty(value = "查询：送货员账号的数据范围（自己那一份送货员档案ID），只用于查询条件，不是本表列")
    @TableField(exist = false)
    private Long scopeRiderId;

    // 当前登录送货员的常驻地点，只给上面那一批按「离我最近」排序用（同样由服务层覆盖，不认前端传的）。
    // 档案上没填经纬度时服务层不传这两个值，ORDER BY 退回默认的 id 倒序
    @ApiModelProperty(value = "查询：排序用-当前送货员常驻经度（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private BigDecimal residentLng;

    @ApiModelProperty(value = "查询：排序用-当前送货员常驻纬度（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private BigDecimal residentLat;

    // 时间范围前端拆成两组字符串传（同商品列表）：接单时间卡 accept_time，派单时间卡 assign_time
    @ApiModelProperty(value = "查询：接单时间范围-开始（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String acceptBeginTime;

    @ApiModelProperty(value = "查询：接单时间范围-结束（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String acceptEndTime;

    @ApiModelProperty(value = "查询：派单时间范围-开始（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String assignBeginTime;

    @ApiModelProperty(value = "查询：派单时间范围-结束（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String assignEndTime;
}
