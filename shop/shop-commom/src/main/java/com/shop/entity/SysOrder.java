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

    @ApiModelProperty(value = "订单类型：1-普通订单 2-秒杀订单 3-拼团订单 4-虚拟订单 5-续费订单")
    private Integer orderType;

    @ApiModelProperty(value = "用户ID")
    private Long userId;

    @ApiModelProperty(value = "用户昵称")
    private String userName;

    @ApiModelProperty(value = "手机号")
    private String mobile;

    @ApiModelProperty(value = "业务ID（关联具体业务，如商品ID、课程ID等）")
    private String bizId;

    @ApiModelProperty(value = "业务类型：1-商品 2-课程 3-会员 4-充值 等")
    private Integer bizType;

    @ApiModelProperty(value = "订单总金额（原价）")
    private BigDecimal totalAmount;

    @ApiModelProperty(value = "实付金额")
    private BigDecimal payAmount;

    @ApiModelProperty(value = "优惠金额")
    private BigDecimal discountAmount;

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
