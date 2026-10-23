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
 * 优惠券模板（营销活动 -> 优惠券）
 *
 * 一张券的"面额 + 有效期 + 发行量"定在这里，发到用户手上才变成 shop_activity_coupon_user 那一行（带快照）。
 *
 * 和活动/培训那几页一样不按账号类型收窄（平台看全部）：券是平台发的活动物资，行上那一格「适用门店」
 * 是这张券能给谁用（NULL = 全平台），不是这一行的可见范围，所以它清空是合法操作（专场改回全平台），挂 IGNORED。
 *
 * 数量那三格里只有 total_count 是表单能改的：issued_count 由「发放」原子加，used_count 由下单用券核销原子加
 * （shop-api 的 ShopOrderCouponService#redeem），两格都钉 NEVER —— 请求体里带什么都不进 UPDATE，
 * 免得后台改一次单就把已经发出去/已经用掉的额度改没了。要动这两格只有那两条带校验的原子 UPDATE。
 *
 * scope_value 库里是 json 列，手填一段不是 JSON 的文本进去 MySQL 直接报 3140，所以表单里那一格收的是
 * 逗号分隔的 id（transient 的 scopeIds），由服务层按 scope_type 拼成合法 JSON 再写、读的时候再拆回去。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_activity_coupon")
@ApiModel(value = "优惠券模板")
public class ShopActivityCoupon extends BaseEntity {

    @ApiModelProperty(value = "券码（新增不传则系统生成 COUPON_+时间戳；库里这一列没建唯一键，查重在服务层）")
    private String couponCode;

    @ApiModelProperty(value = "券名称")
    private String couponName;

    @ApiModelProperty(value = "券类型：1满减券 2折扣券 3免邮券 4代金券（字典 coupon_type）")
    private Integer couponType;

    @ApiModelProperty(value = "适用门店ID（shop_store.id），NULL=全平台")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long storeId;

    @ApiModelProperty(value = "负责的运营（sys_user.id），只登记到人、列表不按它收窄")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long userId;

    @ApiModelProperty(value = "面额/折扣值：满减券和代金券是金额，折扣券是折扣率（如 0.9）")
    private BigDecimal faceValue;

    @ApiModelProperty(value = "使用门槛，满多少可用，0=无门槛")
    private BigDecimal thresholdAmount;

    @ApiModelProperty(value = "最高抵扣金额（折扣券用），NULL=不封顶")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal maxDiscount;

    @ApiModelProperty(value = "发行总量，0=不限")
    private Integer totalCount;

    @ApiModelProperty(value = "已发放数量（只有「发放」那一条路会加它，表单改不了）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer issuedCount;

    @ApiModelProperty(value = "已使用数量（只有统一下单核销那一条路会加它，取消未支付的单会原路减回去；表单改不了）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer usedCount;

    @ApiModelProperty(value = "有效期类型：1固定时间段 2领取后N天（字典 valid_type）")
    private Integer validType;

    @ApiModelProperty(value = "有效期开始（valid_type=1 时用）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime validStart;

    @ApiModelProperty(value = "有效期结束（valid_type=1 时用，发放时过期时间就取这一格）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime validEnd;

    @ApiModelProperty(value = "领取后有效天数（valid_type=2 时用）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer validDays;

    @ApiModelProperty(value = "适用范围：1全场 2指定商品 3指定分类 4指定门店（字典 scope_type）")
    private Integer scopeType;

    @ApiModelProperty(value = "范围值（库里是 json 列，如 {\"productIds\":[1,2]}，由服务层拼，表单里不直接填它）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String scopeValue;

    @ApiModelProperty(value = "领取方式：1主动领取 2系统发放 3活动赠送 4新人礼 5积分兑换 6徽章发放（字典 get_way；6 只在用户券那一侧由徽章发放写入）")
    private Integer getWay;

    @ApiModelProperty(value = "状态：1未开始 2发放中 3已结束 4已取消（字典 coupon_status）")
    private Integer status;

    @ApiModelProperty(value = "使用说明")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String description;

    // 表单和列表用的是这一串逗号分隔的 id，不是 json：写的时候服务层按 scope_type 拼成 scope_value，
    // 读的时候再把 json 拆回来（1 全场 / 4 指定门店 这两档这一格是空的）
    @ApiModelProperty(value = "范围值里的 id 串（逗号分隔，如 1,2,3；表单用这格，落库时拼成 scope_value）")
    @TableField(exist = false)
    private String scopeIds;

    // 门店名和运营昵称一律联表现取，不在这张表里冗余快照（同活动、门店培训）
    @ApiModelProperty(value = "适用门店名称（联表带出，非本表列）")
    @TableField(exist = false)
    private String storeName;

    @ApiModelProperty(value = "负责的运营昵称（联表带出，非本表列）")
    @TableField(exist = false)
    private String userName;

    // 查询用的三个临时字段。筛「有效期开始」区间的两个名字刻意不叫 beginTime/endTime：
    // 本表已经有 valid_start/valid_end 两列，撞了名实体就没法同时当查询条件用
    @ApiModelProperty(value = "查询：券名称/券码关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;

    @ApiModelProperty(value = "查询：有效期开始时间-区间起（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String startFrom;

    @ApiModelProperty(value = "查询：有效期开始时间-区间止（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String startTo;
}
