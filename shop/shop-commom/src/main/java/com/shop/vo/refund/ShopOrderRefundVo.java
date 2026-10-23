package com.shop.vo.refund;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 一张退款单（后台「我的订单 → 退款单」弹窗里的一行，一张订单名下可能有好几张）
 *
 * 只给这一张弹窗看，所以列是挑出来的：币种、第三方退款单号这些不回。
 * 故意**不继承 SysRefund**：那样会把 status 之外的整套列（还有 auditorName 那类 @TableField(exist=false)
 * 的查询字段）一起吐出去；这一路只要下面那一排，行级内容解析成 items 就完了（extra 那一格 @JsonIgnore 挡着）。
 *
 * 一张订单可以同时挂着好几张在途退款单（各退各的商品行、各占各的件数），但历史上被拒绝/失败/取消的那些还留在这张订单名下，
 * 所以这一路返回的是列表 —— 前端按 id 正序列，最新一张在最后。
 */
@Data
@ApiModel(value = "退款单详情（主表字段 + 行级退货商品）")
public class ShopOrderRefundVo {

    @ApiModelProperty(value = "退款ID(sys_refund.id)")
    private Long id;

    @ApiModelProperty(value = "退款单号")
    private String refundNo;

    @ApiModelProperty(value = "退款原因（买家提退货时填的那一句）")
    private String refundReason;

    @ApiModelProperty(value = "退款类型：1-全额退款 2-部分退款（建单时按行判）")
    private Integer refundType;

    @ApiModelProperty(value = "申请退款金额（这次申请退的钱 = 下面 items 各行金额之和）")
    private BigDecimal refundAmount;

    @ApiModelProperty(value = "实际退款金额（审核人可以下调；还没审核时为空）")
    private BigDecimal actualRefundAmount;

    @ApiModelProperty(value = "退款状态（字典 refund_status：0待审核 2审核拒绝 3退款中 4退款成功 5退款失败 6已取消，审核通过就落 3）")
    private Integer status;

    @ApiModelProperty(value = "申请时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime applyTime;

    @ApiModelProperty(value = "审核人昵称（sys_refund.auditor_id 联 sys_user，没审核时为空）")
    private String auditorName;

    @ApiModelProperty(value = "审核时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime auditTime;

    @ApiModelProperty(value = "审核备注（拒绝时这一格就是拒绝原因）")
    private String auditRemark;

    @ApiModelProperty(value = "退款成功时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime refundTime;

    @ApiModelProperty(value = "退款失败原因（status=5 那一档才有）")
    private String failReason;

    @ApiModelProperty(value = "退货商品行数（items 的长度，非 sys_refund 列）")
    private Integer itemCount;

    @ApiModelProperty(value = "这次申请退的件数合计（Σ items.quantity，非 sys_refund 列）")
    private Integer returnQuantity;

    @ApiModelProperty(value = "退了哪些商品行：来自 sys_refund.extra 那份 JSON，商品名称/规格读的时候现联")
    private List<ShopOrderRefundItemVo> items;

    // 只为"同一条 SQL 把行级内容一起捞回来"而存在：服务层解析完 items 就没人再看它，
    // @JsonIgnore 保证这一大坨原始 JSON 不会跟着接口返回（前端拿到的说法就是上面那个 items）
    @JsonIgnore
    @ApiModelProperty(value = "sys_refund.extra 原始 JSON（只在服务层解析用，不返回前端）", hidden = true)
    private String extra;
}
