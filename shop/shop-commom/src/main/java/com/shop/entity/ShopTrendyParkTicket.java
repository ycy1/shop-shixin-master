package com.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 潮玩主题公园票种表
 *
 * 一个票种 = 一种卖法（类型/价格/时段/有效期/限购），归属某一家园区（park_id NOT NULL，没有"全平台通用票"这一档）。
 *
 * time_slot 是 varchar 存的**多选**值（逗号分隔的 ticket_time_slot 档位，如 "1,2"），读的时候按 split 拆开看；
 * 0 全天不和其他三档并列（选全天=上午下午夜场都行，再配一档没有意义），服务层按这条拦。
 * 发到用户手上那一张票的具体时段是单值（tinyint），候选由这一格允许的那几档里出，见 ShopParkUserTicket。
 *
 * park_id / price / ticket_type 都不标 IGNORED：NOT NULL 且必填，服务层 normalize 会兜住默认档，
 * 清空既不是正常操作、也不该让前端传个 null 进来把票价抹掉。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_trendy_park_ticket")
@ApiModel(value = "主题公园票种")
public class ShopTrendyParkTicket extends BaseEntity {

    @ApiModelProperty(value = "票种编号，唯一；新增时不传由服务端生成（TICKET_+时间戳）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String ticketNo;

    @ApiModelProperty(value = "票种名称")
    private String ticketName;

    @ApiModelProperty(value = "票种类型：1日票 2年卡 3套票 4快速通行 5限定款购买资格（字典 ticket_type）")
    private Integer ticketType;

    @ApiModelProperty(value = "所属园区ID(shop_trendy_park.id)")
    private Long parkId;

    @ApiModelProperty(value = "售价")
    private BigDecimal price;

    // 列名是单数的 time_slot（DDL 给的就是这一列），属性按复数叫 timeSlots 才说清它是多选，所以这里显式钉住列名。
    // 自定义 XML 那几条查询也要跟着起别名 `t.time_slot AS timeSlots`，否则按默认的下划线转驼峰读回来是 null
    @TableField("time_slot")
    @ApiModelProperty(value = "入园时段（多选，逗号分隔的 ticket_time_slot 值，如 \"1,2\"；只放这家票种允许进场的时段）")
    private String timeSlots;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "有效天数，NULL=不限（年卡/套票按发售日起算）")
    private Integer validDays;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "每单限购数量，NULL=不限")
    private Integer maxPerOrder;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "票种说明")
    private String description;

    @ApiModelProperty(value = "状态：1上架 0下架")
    private Integer status;

    @ApiModelProperty(value = "排序")
    private Integer sort;

    // 下面两格都不是本表列：园区名按 park_id 查回来，已出票数按当页票种 id 一次分组数完
    //（形状同 ShopTrendyBox.itemCount、ShopTrendyIp.trendyCount）
    @TableField(exist = false)
    @ApiModelProperty(value = "所属园区名称（服务层回填，非本表列）")
    private String parkName;

    @TableField(exist = false)
    @ApiModelProperty(value = "这个票种已经出出去多少张（服务层回填，非本表列；删除票种前先看这一格）")
    private Integer soldCount;

    @ApiModelProperty(value = "查询：票种名称/编号关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;
}
