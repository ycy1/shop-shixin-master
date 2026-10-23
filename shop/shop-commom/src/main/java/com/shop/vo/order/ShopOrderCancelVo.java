package com.shop.vo.order;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 取消订单的结果（只处理还没付钱的那一档）
 *
 * 形状照 {@link ShopOrderReceiveVo}：只回"这一单现在是哪一档 + 刚才那一下都动了些什么"，
 * 客户端拿到就能提示"已取消，券、库存、积分和余额都还回去了"，不用再调一次详情。
 * 后面几格（退了几张券、还了几件库存、解冻了几分、退了多少钱）是这一趟真做掉的那几件事的回执，数对不上时先来看这几格。
 */
@Data
@ApiModel(value = "订单取消结果")
public class ShopOrderCancelVo {

    @ApiModelProperty(value = "订单ID(sys_order.id)")
    private Long orderId;

    @ApiModelProperty(value = "订单号")
    private String orderNo;

    @ApiModelProperty(value = "取消后的订单状态：固定 3 已取消（字典 order_status）")
    private Integer status;

    // 表上没有单独的取消时间列（sys_order 只有 pay_time / expire_time 两格时间），
    // 所以这一格是服务端翻状态那一刻取的当前时间，只回给调用方看，不落库
    @ApiModelProperty(value = "取消的时间（服务端取的当前时间；sys_order 没有取消时间列，这一格不落库）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime cancelTime;

    @ApiModelProperty(value = "这一单的明细行数（每一行的库存都按它自己那家仓还回去）")
    private Integer itemCount;

    @ApiModelProperty(value = "归还到库存锁定里的件数（Σ 各行数量；没分过仓的老行不算进来）")
    private Integer releasedQuantity;

    @ApiModelProperty(value = "退回用户手上的优惠券张数（这一单没用过券就是 0）")
    private Integer couponCount;

    @ApiModelProperty(value = "解冻还回可用积分的分值（Σ 各行 used_points；这一单没用过分就是 0）")
    private Integer releasedPoints;

    // 余额付的是整单实付，所以这一格读的不是明细行，而是主单上 pay_type + pay_amount 那两格
    @ApiModelProperty(value = "退回钱包可用余额的钱数（下单选的是余额支付（pay_type=4）就是这一单的实付金额，其余一档是 0）")
    private BigDecimal releasedBalance;
}
