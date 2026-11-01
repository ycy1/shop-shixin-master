package com.shop.dto.park;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDate;

/**
 * 出票入参（主题公园）
 *
 * 只有"给谁、哪张票种、哪天来玩、约哪个时段、票写给谁"这几格是调用方说的；
 * 票码、园区、票名/类型/票价、状态一律由 {@code ShopParkTicketIssueService} 按票种行定下来，
 * 传了也不认 —— 一张票的票面信息只能有一个来源，否则后台代客和支付回调两头出票会出成两种形状。
 */
@Data
@ApiModel(value = "主题公园出票入参")
public class ShopParkTicketIssueDto {

    @ApiModelProperty(value = "持票用户ID（sys_user.id）", required = true)
    private Long userId;

    @ApiModelProperty(value = "票种ID（shop_trendy_park_ticket.id）", required = true)
    private Long ticketId;

    @JsonFormat(pattern = DateUtil.YYYY_MM_DD, timezone = "GMT+8")
    @ApiModelProperty(value = "游玩日期", required = true)
    private LocalDate visitDate;

    @ApiModelProperty(value = "入园时段（这一张票约的那一档，0全天 1上午 2下午 3夜场；必须是所选票种允许的那几档之一）。"
            + "不传就走票种的第一档")
    private Integer timeSlot;

    @ApiModelProperty(value = "出几张，NULL=1 张（支付回调按订单明细的件数传这一格）")
    private Integer quantity;

    @ApiModelProperty(value = "持票人姓名")
    private String holderName;

    @ApiModelProperty(value = "持票人电话")
    private String holderPhone;

    @ApiModelProperty(value = "持票人身份证号")
    private String holderIdCard;

    @ApiModelProperty(value = "备注（会原样记在每一张票上）")
    private String remark;

    @ApiModelProperty(value = "要不要生成票面二维码（qr_img）。默认生成；"
            + "批量补历史数据这种场景传 false，省得往文件存储里灌一堆没人扫的图")
    private Boolean withQrCode = Boolean.TRUE;
}
