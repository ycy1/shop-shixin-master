package com.shop.vo.refund;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退货建单结果（点「退货」提交后返回的那一份）
 *
 * 前端拿 refundId / refundNo 去提示"退款单已提交，等审核"，并刷新列表 ——
 * 列表上那两格判据（已退件数、在途退款单条数）会跟着变，「退货」按钮就该消失了。
 * 金额一律是服务端算出来的那一份，不是表单里填的那个。
 */
@Data
@ApiModel(value = "退货建单结果")
public class ShopRefundCreateVo {

    @ApiModelProperty(value = "退款单ID(sys_refund.id)")
    private Long refundId;

    @ApiModelProperty(value = "退款单号（系统生成，REFUND_+时间戳+3位随机）")
    private String refundNo;

    @ApiModelProperty(value = "所属订单ID / 订单号")
    private Long orderId;

    private String orderNo;

    @ApiModelProperty(value = "退款状态：建单恒为 0 待审核（字典 refund_status），等后台退款管理页审核")
    private Integer status;

    @ApiModelProperty(value = "退款类型：1全额退款 2部分退款（服务端按这一单的行判：这次退完所有行都退满=1）")
    private Integer refundType;

    @ApiModelProperty(value = "申请退款金额（服务端按订单明细逐行算出来相加）")
    private BigDecimal refundAmount;

    @ApiModelProperty(value = "这次退的商品行数 / 件数合计")
    private Integer itemCount;

    private Integer returnQuantity;

    @ApiModelProperty(value = "落库的退货地址ID（没传时用这一单当初的收货地址补的）")
    private Long addressId;

    @ApiModelProperty(value = "申请时间（服务端取当前时间）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime applyTime;
}
