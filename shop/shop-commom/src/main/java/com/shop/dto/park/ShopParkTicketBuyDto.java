package com.shop.dto.park;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDate;

/**
 * 购票入参（主题公园，用户自己买那一路）
 *
 * 和 {@link ShopParkTicketIssueDto} 的区别就一格：**给谁**不在这里。
 * 谁能买到票由登录态决定（见 ShopParkTicketBuyService），请求体里点别人的 id 就等于替别人买票，
 * 所以这一份 DTO 干脆没有 userId 这一格，后台代客出票才用得上那份。
 *
 * 票面那几格（票名/类型/票价/园区）也一样不认 —— 源头只有票种那一行，两处各填一份迟早对不上。
 */
@Data
@ApiModel(value = "主题公园购票入参")
public class ShopParkTicketBuyDto {

    @ApiModelProperty(value = "票种ID(shop_trendy_park_ticket.id)", required = true)
    private Long ticketId;

    @JsonFormat(pattern = DateUtil.YYYY_MM_DD, timezone = "GMT+8")
    @ApiModelProperty(value = "游玩日期（不能早于今天）", required = true)
    private LocalDate visitDate;

    @ApiModelProperty(value = "入园时段（这一张票约的那一档，0全天 1上午 2下午 3夜场；必须是所选票种卖的那几档之一）。"
            + "不传就走票种的第一档")
    private Integer timeSlot;

    @ApiModelProperty(value = "买几张，不传就是 1 张（超票种自己的每单限购会被拦）")
    private Integer quantity;

    @ApiModelProperty(value = "持票人姓名")
    private String holderName;

    @ApiModelProperty(value = "持票人电话")
    private String holderPhone;

    @ApiModelProperty(value = "持票人身份证号")
    private String holderIdCard;
}
