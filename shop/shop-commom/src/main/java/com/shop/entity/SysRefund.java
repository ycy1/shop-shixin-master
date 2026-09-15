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
@TableName("sys_refund")
@ApiModel(value = "退款表对象")
public class SysRefund implements Serializable {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty(value = "退款ID（主键，自增）")
    private Long id;

    @ApiModelProperty(value = "退款单号（业务唯一，对外展示）")
    private String refundNo;

    @ApiModelProperty(value = "订单ID（关联sys_order.id）")
    private Long orderId;

    @ApiModelProperty(value = "订单编号（冗余，便于查询）")
    private String orderNo;

    @ApiModelProperty(value = "用户ID（冗余，便于按用户查询）")
    private Long userId;

    @ApiModelProperty(value = "用户昵称")
    private String userName;

    @ApiModelProperty(value = "手机号")
    private String mobile;

    @ApiModelProperty(value = "退款类型：1-全额退款 2-部分退款")
    private Integer refundType;

    @ApiModelProperty(value = "退款原因（用户填写）")
    private String refundReason;

    @ApiModelProperty(value = "申请退款金额")
    private BigDecimal refundAmount;

    @ApiModelProperty(value = "实际退款金额（审核后确定）")
    private BigDecimal actualRefundAmount;

    @ApiModelProperty(value = "币种")
    private String currency;

    @ApiModelProperty(value = "退款状态：0-待审核 1-审核通过 2-审核拒绝 3-退款中 4-退款成功 5-退款失败 6-已取消")
    private Integer status;

    @ApiModelProperty(value = "退款渠道：1-原路退回 2-余额 3-人工转账")
    private Integer refundChannel;

    @ApiModelProperty(value = "第三方退款单号（微信/支付宝返回）")
    private String thirdRefundNo;

    @ApiModelProperty(value = "申请时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime applyTime;

    @ApiModelProperty(value = "审核时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime auditTime;

    @ApiModelProperty(value = "审核人ID")
    private Long auditorId;

    @ApiModelProperty(value = "审核备注")
    private String auditRemark;

    @ApiModelProperty(value = "退款成功时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS)
    private LocalDateTime refundTime;

    @ApiModelProperty(value = "退款失败原因")
    private String failReason;

    @ApiModelProperty(value = "备注")
    private String remark;

    @ApiModelProperty(value = "扩展字段（JSON，存退款个性化数据）")
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

    @ApiModelProperty(value = "查询：退款时间范围-开始")
    @TableField(exist = false)
    private String beginTime;

    @ApiModelProperty(value = "查询：退款时间范围-结束")
    @TableField(exist = false)
    private String endTime;

    @ApiModelProperty(value = "审核人昵称")
    @TableField(exist = false)
    private String auditorName;
}
