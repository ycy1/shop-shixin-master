package com.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.io.Serializable;
import java.util.List;

@Data
@TableName("sys_order")
@ApiModel(value = "订单主表对象")
public class SysOrder implements Serializable {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty(value = "订单ID（主键，自增）")
    private Long id;

    @ApiModelProperty(value = "订单编号（业务唯一，对外展示）")
    private String orderNo;

    @ApiModelProperty(value = "订单类型：1-普通订单 2-秒杀订单 3-拼团订单 4-充值订单（字典 order_type）")
    private Integer orderType;

    @ApiModelProperty(value = "用户ID")
    private Long userId;

    @ApiModelProperty(value = "用户昵称")
    private String userName;

    @ApiModelProperty(value = "手机号")
    private String mobile;

    /**
     * 下单时用户选用的收货地址。故意只存 id、不抄一份地址快照：这一单要从哪家仓发（支付回调就近找仓）、
     * 送给谁（下单建配送单时取收件人和坐标），读的都是这同一行 shop_consumer_address。
     * 统一下单之前落的老订单、以及下单时没选地址（按 sys_user 那份坐标分仓）的单都是 NULL。
     */
    @ApiModelProperty(value = "收货地址ID（shop_consumer_address.id，下单时填入；NULL=这一单没选收货地址）")
    private Long addressId;

    @ApiModelProperty(value = "业务ID（关联具体业务，如商品ID、课程ID等）")
    private String bizId;

    @ApiModelProperty(value = "业务类型：1-商品 2-课程 3-会员 4-充值 5-提现（字典 order_biz_type）")
    private Integer bizType;

    @ApiModelProperty(value = "订单总金额（原价）")
    private BigDecimal totalAmount;

    @ApiModelProperty(value = "实付金额")
    private BigDecimal payAmount;

    @ApiModelProperty(value = "优惠金额")
    private BigDecimal discountAmount;

    /**
     * 下单那一刻按"当时优先级最高的那条启用规则"算出来的配送费，已经含在 total_amount 里
     * （口径见 ShopOrderDeliveryServiceImpl#plan）。落库之后不再随规则变 —— 这一笔是跟货款一起收走的钱，
     * 规则后来改了价也不该回头改已经收过的单。
     *
     * ⚠ 所以这张单的钱现在有两处说法：pay_amount = 货值 − 优惠 + 这一格，
     * 而支付回调建出来的那张出库单主表金额只有货值（配送费不出货、不进成本）。
     */
    @ApiModelProperty(value = "配送费（下单时按规则算出，已含在总金额里）")
    private BigDecimal deliveryFee;

    /** 这一笔配送费的出处，口径同 shop_delivery.fee_rule_id：NULL = 当时没有启用规则、按 0 收的 */
    @ApiModelProperty(value = "算这笔配送费用的是哪条规则(shop_delivery_fee_rule.id)，NULL=没按规则算")
    private Long feeRuleId;

    @ApiModelProperty(value = "币种")
    private String currency;

    @ApiModelProperty(value = "订单状态：0-待支付 1-已支付 2-已完成 3-已取消 4-已退款 5-已关闭")
    private Integer status;

    @ApiModelProperty(value = "支付方式：1-微信 2-支付宝 3-银行卡 4-余额")
    private Integer payType;

    @ApiModelProperty(value = "支付时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime payTime;

    @ApiModelProperty(value = "订单过期时间（超时未支付自动关闭）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime expireTime;

    @ApiModelProperty(value = "订单备注")
    private String remark;

    @ApiModelProperty(value = "扩展字段（JSON，存业务个性化数据）")
    private String extra;

    @ApiModelProperty(value = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime createTime;

    @ApiModelProperty(value = "修改时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime updateTime;

    @ApiModelProperty(value = "逻辑删除：0-未删除 1-已删除")
    private Integer isDeleted = 0;


    @ApiModelProperty(value = "查询")
    @TableField(exist = false)
    private List<Long> ids;

    @ApiModelProperty(value = "查询：支付时间范围-开始")
    @TableField(exist = false)
    private String beginTime;

    @ApiModelProperty(value = "查询：支付时间范围-结束")
    @TableField(exist = false)
    private String endTime;

    @ApiModelProperty(value = "查询：仅查询已付款订单（已支付/已完成）")
    @TableField(exist = false)
    private Boolean paidOnly;

}
