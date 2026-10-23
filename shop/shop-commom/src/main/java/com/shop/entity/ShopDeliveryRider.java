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
import java.time.LocalDateTime;

/**
 * 送货员表
 *
 * 归属只有一条线：logistics_company_id（可空，没挂靠就是自由身）。
 * 原来还有一列 store_id 挂门店，2026-09-21 那轮"送货员和配送单不在关联门店"把它删了，
 * 所以这个类里不再有门店相关的字段，表名里的 store_ 只是门店侧运营表的前缀惯例。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_delivery_rider")
@ApiModel(value = "送货员")
public class ShopDeliveryRider extends BaseEntity {

    @ApiModelProperty(value = "送货员编码，唯一；新增时不传则由系统生成（RIDER_+时间戳）")
    private String riderCode;

    // 这一列存的是送货员自己的登录账号，不是创建人（创建人记在 create_by）。
    // 可空且"解绑账号"是正常操作，所以显式 IGNORED，否则全局 not_empty 会挡住清空
    @ApiModelProperty(value = "送货员登录账号ID(sys_user.id)，NULL表示未开通账号")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long userId;

    // 昵称不是本表列，服务层按 user_id 查 sys_user 回填，给列表显示和账号选择器回显用
    @ApiModelProperty(value = "送货员账号昵称（服务层回填，非本表列）")
    @TableField(exist = false)
    private String userName;

    @ApiModelProperty(value = "送货员姓名")
    private String riderName;

    @ApiModelProperty(value = "手机号，唯一")
    private String phone;

    @ApiModelProperty(value = "头像URL")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String avatar;

    // 可空（不挂靠物流公司是正常状态），必须显式 IGNORED，否则全局 not_empty 会让"清空物流公司"整列跳过
    @ApiModelProperty(value = "所属物流公司ID，NULL为未挂靠")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long logisticsCompanyId;

    @ApiModelProperty(value = "所属物流公司名称（服务层回填，非本表列）")
    @TableField(exist = false)
    private String logisticsCompanyName;

    // 常驻地点：删掉门店归属之后，"这个人常在哪儿"是唯一能用来就近派单的坐标。
    // 三个都可空且能清掉（不填经纬度的送货员照样能用，只是他那一份配送列表排不出距离），
    // 所以显式 IGNORED —— 全局 not_empty 会把 null 整列跳过，不写这三格就再也清不掉了
    @ApiModelProperty(value = "常驻地点(地址文本，前端选点带回)")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String residentAddress;

    @ApiModelProperty(value = "常驻经度(GCJ-02)")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal residentLng;

    @ApiModelProperty(value = "常驻纬度(GCJ-02)")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal residentLat;

    @ApiModelProperty(value = "身份证号")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String idCard;

    @ApiModelProperty(value = "身份证正面URL")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String idCardFront;

    @ApiModelProperty(value = "身份证反面URL")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String idCardBack;

    @ApiModelProperty(value = "健康证图片URL")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String healthCert;

    @ApiModelProperty(value = "健康证到期日")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime healthCertExpire;

    @ApiModelProperty(value = "交通工具：1电动车 2摩托车 3自行车 4汽车（字典 rider_vehicle_type）")
    private Integer vehicleType;

    @ApiModelProperty(value = "车牌号")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String vehicleNo;

    @ApiModelProperty(value = "服务城市")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String serviceCity;

    @ApiModelProperty(value = "服务区域，逗号分隔")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String serviceArea;

    @ApiModelProperty(value = "最大同时接单数")
    private Integer maxOrderCount;

    @ApiModelProperty(value = "在线状态：1在线 0离线 2忙碌（字典 rider_online_status）")
    private Integer onlineStatus;

    @ApiModelProperty(value = "工作状态：1接单中 0休息（字典 rider_work_status）")
    private Integer workStatus;

    @ApiModelProperty(value = "启用状态：1启用 0禁用（字典 rider_status）")
    private Integer status;

    // 这三列是业务累计出来的统计值，CRUD 表单不编辑。NEVER 让列永不进 UPDATE 的 SET，
    // 请求里带什么都改不动；新增时不传即为 null，走库里的 DEFAULT
    @ApiModelProperty(value = "累计接单数（统计值，表单不编辑）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer totalOrderCount;

    @ApiModelProperty(value = "累计收入（统计值，表单不编辑）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private BigDecimal totalIncome;

    @ApiModelProperty(value = "评分（统计值，表单不编辑）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private BigDecimal rating;

    @ApiModelProperty(value = "查询：送货员姓名/编码/手机号关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;
}
