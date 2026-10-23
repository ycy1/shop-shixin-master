package com.shop.entity;

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
 * 用户优惠券（一张券发到一个人手上一行）
 *
 * 写入有三条路：后台「发放」（POST /activity/coupon/give，插行）、统一下单核销（shop-api 的
 * ShopOrderCouponService#redeem，把用掉的那一行 status 0→1、写 order_id/order_no/use_time），
 * 以及取消未支付的订单时原路退回去（同一个 service 的 #giveBack，status 退回 0、那三格清空），
 * 所以这张表在后台没有新增/修改/删除端点。用户自己领券那条前台链路这一期还没接。
 *
 * coupon_name / coupon_type / face_value / threshold_amount / max_discount / scope_type 这六格是**快照**，
 * 和模板同名但不是联表带出来的：发出去那一刻把面额定格，之后模板改了面额不该回头刷已经到用户手上的券。
 * 要改一张已发出去的券的用法，只能作废它再重新发。
 * 有一格没快照下来：范围里那份具体名单（模板的 scope_value）和适用门店（store_id）——
 * 用券时"抵哪几行"只能回模板上读那两格，所以模板改了名单，已发出去的券会跟着新名单筛（面额和券类型不会）。
 *
 * coupon_code 是本表唯一键（每张用户券一个码）：规则是 CP + 模板ID + _ + 这一张是该模板发出去的第几张，
 * 序号取自模板行的 issued_count（占量那条 UPDATE 是原子的，所以并发发放拿不到同一个序号）。
 *
 * userId 和 create_by 不是一回事：前者是"这张券在谁手上"（消费者），后者是 MyMetaObjectHandler
 * 按当前登录用户强制盖的留痕列 —— 后台代发的这一批里，两者通常不同（操作人是运营，持有人是用户）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_activity_coupon_user")
@ApiModel(value = "用户优惠券")
public class ShopActivityCouponUser extends BaseEntity {

    @ApiModelProperty(value = "券模板ID（shop_activity_coupon.id）")
    private Long couponId;

    @ApiModelProperty(value = "券码（每张用户券一个，本表唯一键）")
    private String couponCode;

    @ApiModelProperty(value = "持券用户ID（sys_user.id）")
    private Long userId;

    @ApiModelProperty(value = "使用的订单ID（下单用券核销时写，没used过是 NULL）")
    private Long orderId;

    @ApiModelProperty(value = "使用的订单号（冗余 sys_order.order_no，同上）")
    private String orderNo;

    @ApiModelProperty(value = "券名称（发放时快照）")
    private String couponName;

    @ApiModelProperty(value = "券类型（发放时快照，字典 coupon_type）")
    private Integer couponType;

    @ApiModelProperty(value = "面额/折扣值（发放时快照）")
    private BigDecimal faceValue;

    @ApiModelProperty(value = "使用门槛（发放时快照，0=无门槛）")
    private BigDecimal thresholdAmount;

    @ApiModelProperty(value = "最高抵扣（发放时快照，NULL=不封顶）")
    private BigDecimal maxDiscount;

    @ApiModelProperty(value = "适用范围（发放时快照，字典 scope_type）")
    private Integer scopeType;

    @ApiModelProperty(value = "状态：0未使用 1已使用 2已过期 3已作废（字典 coupon_user_status；1 由统一下单核销写，"
            + "2/3 这一期没有写入方，所以过期是下单时拿 expire_time 现判的）")
    private Integer status;

    @ApiModelProperty(value = "领取时间（发放时服务端盖）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime receiveTime;

    @ApiModelProperty(value = "使用时间（统一下单核销那一刻盖，一单里用掉的几张共用同一个时间）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime useTime;

    @ApiModelProperty(value = "过期时间（发放时按模板的有效期算出来）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime expireTime;

    @ApiModelProperty(value = "领取方式（字典 get_way；后台「发放」发出去的那一批落 2 系统发放，营销徽章达标兑现连带发的那一批落 6 徽章发放）")
    private Integer getWay;

    @ApiModelProperty(value = "备注")
    private String remark;

    @ApiModelProperty(value = "持券用户昵称（联表带出，非本表列）")
    @TableField(exist = false)
    private String userName;

    @ApiModelProperty(value = "持券用户手机号（联表带出，非本表列）")
    @TableField(exist = false)
    private String mobile;
}
