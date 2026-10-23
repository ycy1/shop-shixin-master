package com.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * 配送费规则：平台级的计价配置，统一下单建配送单时取"启用里优先级最高"的那一条算配送费（那一笔钱跟货款一起收走）。
 *
 * 计算口径见 ShopDeliveryServiceImpl#fillDeliveryFee —— 只有服务层算得出来，
 * 表单里没有 delivery_fee 这一格，前端改不了价。
 *
 * 这一张表和 shop_delivery_company 一样是"配置表"：user_id 只留痕，不参与数据范围过滤。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("shop_delivery_fee_rule")
@ApiModel(value = "配送费规则")
public class ShopDeliveryFeeRule extends BaseEntity {

    @ApiModelProperty(value = "规则名称")
    private String ruleName;

    @ApiModelProperty(value = "适用配送类型：1平台配送 2自提 3快递发货（同配送单 delivery_type）")
    private Integer deliveryType;

    @ApiModelProperty(value = "起步配送费（元）")
    private BigDecimal baseFee;

    @ApiModelProperty(value = "起步价包含的公里数")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal baseDistance;

    @ApiModelProperty(value = "超出起步里程后每公里加收（元/km）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal perKmFee;

    @ApiModelProperty(value = "免费重量上限（kg）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal weightFree;

    @ApiModelProperty(value = "超重部分每公斤加收（元/kg）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal perKgFee;

    @ApiModelProperty(value = "高峰时段加价（元）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal peakFee;

    /**
     * 两个时刻合起来才是一个区间，所以整对判空：任一为 NULL 就不加这一档价。
     * 存 HH:mm:ss（Jackson 按 @JsonFormat 收发，前端 el-time-picker 的 value-format 也是这一串）。
     */
    @ApiModelProperty(value = "高峰时段开始时刻")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime peakStart;

    @ApiModelProperty(value = "高峰时段结束时刻")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime peakEnd;

    @ApiModelProperty(value = "夜间时段加价（元）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal nightFee;

    @ApiModelProperty(value = "夜间时段开始时刻（可以晚于结束时刻，那就是跨零点，如 21:00~06:00）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime nightStart;

    @ApiModelProperty(value = "夜间时段结束时刻")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime nightEnd;

    /** 系统里没有天气数据来源，这一档本期不参与计算，只是先把格子留着（见 fillDeliveryFee 注释）。 */
    @ApiModelProperty(value = "天气加价（元）；本期建配送单时不参与计算")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal weatherFee;

    @ApiModelProperty(value = "订单实付满此金额免配送费，留空表示不启用这一档")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal freeDeliveryAmount;

    @ApiModelProperty(value = "配送距离在此公里数内免配送费，留空表示不启用这一档")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal freeDeliveryDistance;

    @ApiModelProperty(value = "优先级，数字越大越优先")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer priority;

    @ApiModelProperty(value = "状态：1启用 0禁用")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer status;

    @ApiModelProperty(value = "绑定账号用户ID：只留痕，不参与数据范围过滤")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long userId;

    /** 查询条件：一个关键字模糊匹配规则名称。不建列。 */
    @TableField(exist = false)
    private String keyword;
}
