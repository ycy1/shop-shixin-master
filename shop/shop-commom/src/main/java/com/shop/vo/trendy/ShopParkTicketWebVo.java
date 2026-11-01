package com.shop.vo.trendy;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 主题公园票种 Web 端视图（园区详情页上那一排可买的票）
 *
 * 只给上架的票种（status=1）：下架的还在架上卖是后台的事，前台不该看见。
 * 票种编号、排序、创建人这些不外给。
 */
@Data
@ApiModel(value = "主题公园票种 Web 端视图")
public class ShopParkTicketWebVo {

    @ApiModelProperty(value = "票种id")
    private Long id;

    @ApiModelProperty(value = "所属园区id")
    private Long parkId;

    @ApiModelProperty(value = "票种名称")
    private String ticketName;

    @ApiModelProperty(value = "票种类型：1日票 2年卡 3套票 4快速通行 5限定款购买资格（字典 ticket_type）")
    private Integer ticketType;

    @ApiModelProperty(value = "售价")
    private BigDecimal price;

    /** 一串逗号分隔的字典值（如 "1,2"），不是单个数：一个票种可以放开多个入园时段，页面按档位拆开展示 */
    @ApiModelProperty(value = "入园时段（多选，逗号分隔的 ticket_time_slot 值）")
    private String timeSlots;

    @ApiModelProperty(value = "有效天数，NULL=不限")
    private Integer validDays;

    @ApiModelProperty(value = "每单限购数量，NULL=不限")
    private Integer maxPerOrder;

    @ApiModelProperty(value = "票种说明")
    private String description;
}
