package com.shop.vo.trendy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.entity.ShopParkUserTicket;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 一次购票的结果
 *
 * 买完直接拿这一份弹提示：买到几张、这一单该付多少钱、票码和票面图在哪。
 * 形状照一次抽盒那份（{@link ShopTrendyBoxDrawVo}）：都是「一次动作落到名下的几行 + 该付多少」。
 */
@Data
@ApiModel(value = "主题公园购票结果")
public class ShopParkTicketBuyVo {

    @ApiModelProperty(value = "园区ID")
    private Long parkId;

    @ApiModelProperty(value = "票种ID")
    private Long ticketId;

    @ApiModelProperty(value = "票种名称（出票那一刻的快照）")
    private String ticketName;

    @ApiModelProperty(value = "票种类型：1日票 2年卡 3套票 4快速通行 5限定款购买资格（字典 ticket_type）")
    private Integer ticketType;

    @JsonFormat(pattern = DateUtil.YYYY_MM_DD, timezone = "GMT+8")
    @ApiModelProperty(value = "游玩日期")
    private LocalDate visitDate;

    @ApiModelProperty(value = "入园时段（这一批票约的那一档，字典 ticket_time_slot）")
    private Integer timeSlot;

    @ApiModelProperty(value = "买到几张")
    private Integer count;

    @ApiModelProperty(value = "本单应付合计（票价快照 × 张数）。只报数，不扣款 —— 支付那条口还没接")
    private BigDecimal amount;

    @ApiModelProperty(value = "落到名下的那几张票（含票码和票面二维码）")
    private List<ShopParkUserTicket> tickets;
}
