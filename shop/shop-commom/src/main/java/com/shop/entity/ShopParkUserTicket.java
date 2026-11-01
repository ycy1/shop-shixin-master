package com.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.entity.core.BaseEntity;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户票种关联表（一张票发到一个人手上一行）
 *
 * ticket_code 是本表唯一键（每张票一个码），规则和其它编号一样：TK_ + 时间戳 + 3位随机数，由服务端生成、页面不给填。
 *
 * qr_img / ticket_name / ticket_type / price 这四格是**出票那一刻从票种行上定格的快照**，
 * 和票种同名但不是联表带出来的：票种后来改名改价，不该回头刷已经发到用户手上的票（口径同 ShopActivityCouponUser）。
 * park_id 也是快照 —— 它由服务层按 ticket_id 从票种行上带出来，请求体里传什么都不认。
 *
 * status 的四档各有写它的动作：0 是出票那一刻的默认档，1 由「入园核销」写（同时盖 used_time），
 * 3 由「退款」写（同时盖 refund_time），2 已过期 由定时任务 expireTask.expireParkTickets 每晚扫一次
 *（游玩日期过了当天、还从来没核销过的票）—— 落到 2 的票还能退，判据都在 shop-server 那份 XML 的 WHERE 里。
 *
 * userId 和 create_by 不是一回事：前者是这张票在谁手上（消费者），后者是 MyMetaObjectHandler
 * 按当前登录用户强制盖的留痕列 —— 后台代客出票的这一批里，两者通常不同（操作人是运营，持票人是用户）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_trendy_park_user_ticket")
@ApiModel(value = "用户票种")
public class ShopParkUserTicket extends BaseEntity {

    @ApiModelProperty(value = "票码/入园码，本表唯一键；新增时不传由服务端生成（TK_+时间戳）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String ticketCode;

    @ApiModelProperty(value = "持票用户ID（sys_user.id）")
    private Long userId;

    @ApiModelProperty(value = "票种ID（shop_trendy_park_ticket.id）")
    private Long ticketId;

    @ApiModelProperty(value = "园区ID（按 ticket_id 从票种行上带出来的快照，请求体里传什么都不认）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long parkId;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "票面二维码（出票时快照，可空）")
    private String qrImg;

    @ApiModelProperty(value = "票种名称（出票时快照）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String ticketName;

    @ApiModelProperty(value = "票种类型（出票时快照，字典 ticket_type）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer ticketType;

    @ApiModelProperty(value = "票价（出票时快照）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private BigDecimal price;

    @ApiModelProperty(value = "游玩日期")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD, timezone = "GMT+8")
    private LocalDate visitDate;

    @ApiModelProperty(value = "入园时段：0全天 1上午 2下午 3夜场（出票时从票种带上，改票时跟着游玩日期一起改得动）")
    private Integer timeSlot;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "持票人姓名")
    private String holderName;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "持票人电话")
    private String holderPhone;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "持票人身份证号")
    private String holderIdCard;

    @ApiModelProperty(value = "状态：0未使用 1已核销 2已过期 3已退款（字典 user_ticket_status；新增时服务端钉 0，"
            + "1 由「入园核销」写，2 由定时任务 expireTask.expireParkTickets 一天扫一次，3 由「退款」写）")
    private Integer status;

    @TableField(updateStrategy = FieldStrategy.NEVER)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @ApiModelProperty(value = "核销时间（「入园核销」那一刻盖，表单不给改）")
    private LocalDateTime usedTime;

    @TableField(updateStrategy = FieldStrategy.NEVER)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @ApiModelProperty(value = "退款时间（「退款」那一刻盖，表单不给改）")
    private LocalDateTime refundTime;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "备注")
    private String remark;

    // 下面三格是列表那两列要显示的用户和园区，本表没有存名字：
    // userName/mobile 在 XML 里联 sys_user 带出，parkName 联 shop_trendy_park 带出（口径同 ShopActivityCouponUser）
    @TableField(exist = false)
    @ApiModelProperty(value = "持票用户昵称（联表带出，非本表列）")
    private String userName;

    @TableField(exist = false)
    @ApiModelProperty(value = "持票用户手机号（联表带出，非本表列）")
    private String mobile;

    @TableField(exist = false)
    @ApiModelProperty(value = "园区名称（联表带出，非本表列）")
    private String parkName;

    @ApiModelProperty(value = "出票张数（只给「代客出票」那一路用，不是本表列；不传就是 1 张，一张票落一行）")
    @TableField(exist = false)
    private Integer quantity;

    @ApiModelProperty(value = "查询：票码/票种名/持票人姓名电话关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;

    @ApiModelProperty(value = "查询：游玩日期起（含），格式 yyyy-MM-dd（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String beginVisitDate;

    @ApiModelProperty(value = "查询：游玩日期止（含），格式 yyyy-MM-dd（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String endVisitDate;
}
