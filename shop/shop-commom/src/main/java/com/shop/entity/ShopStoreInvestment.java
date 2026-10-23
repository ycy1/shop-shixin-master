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
 * 门店招商（门店管理 -> 门店招商）
 *
 * 编码列 NOT NULL 但不进表单：新增时由系统生成（见 ShopStoreInvestmentServiceImpl#insert）。
 * 地区按项目统一口径只存两列：area_code 是 "110000,110100,110101" 这种行政区划代码，
 * area_zh 是解析出来的中文名（同 sys_user / shop_store / shop_erp_supplier）。
 *
 * user_id 是「负责的招商专员」，不是创建人（留痕那两列在 BaseEntity 的 create_by/update_by），
 * 所以它是表单里可选可清空的一格，用 IGNORED 而不是 NEVER。
 * sign_time 也是表单字段（添加/修改都要填，见服务层 validate），带 IGNORED 只是为了能在弹窗里改值。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_store_investment")
@ApiModel(value = "门店招商")
public class ShopStoreInvestment extends BaseEntity {

    @ApiModelProperty(value = "招商编号，唯一；新增时不传则由系统生成（INVEST_+时间戳）")
    private String investmentNo;

    @ApiModelProperty(value = "负责的招商专员用户ID（sys_user.id，account_type=0 平台账号）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long userId;

    @ApiModelProperty(value = "申请人姓名")
    private String applicantName;

    @ApiModelProperty(value = "申请人电话")
    private String applicantPhone;

    @ApiModelProperty(value = "意向类型：1加盟 2代理 3合作 4直营（字典 invest_intent_type）")
    private Integer intentType;

    @ApiModelProperty(value = "签约文件url（pdf/docx，前端上传后存返回的地址）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String contractFile;

    @ApiModelProperty(value = "投资预算")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal budgetAmount;

    @ApiModelProperty(value = "是否有行业经验：1有 0无")
    private Integer hasExperience;

    @ApiModelProperty(value = "线索来源：1官网 2电话 3转介绍 4展会 5活动 6其他（字典 invest_source）")
    private Integer source;

    @ApiModelProperty(value = "备注")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;

    @ApiModelProperty(value = "状态：0待审核 1已签约 2已取消 3已失效（字典 invest_status）")
    private Integer status;

    @ApiModelProperty(value = "签约时间（表单必填，可改；服务层校验非空）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime signTime;

    @ApiModelProperty(value = "过期时间（表单必填，可改；服务层校验非空）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime expiryTime;

    @ApiModelProperty(value = "地区编码，逗号分隔的行政区划代码")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String areaCode;

    @ApiModelProperty(value = "地区中文（由 area_code 解析出的冗余快照，列表直接显示这一列）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String areaZh;

    @ApiModelProperty(value = "经度(GCJ-02)，前端地图选点写入")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal longitude;

    @ApiModelProperty(value = "纬度(GCJ-02)，前端地图选点写入")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal latitude;

    @ApiModelProperty(value = "详细地址（街道门牌，不含省市区）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String address;

    // 专员的显示名不是本表列，服务层按 user_id 查 sys_user 回填，
    // 给列表显示和负责专员选择器回显用（同 ShopErpSupplier.userName）
    @ApiModelProperty(value = "负责专员昵称（服务层回填，非本表列）")
    @TableField(exist = false)
    private String userName;

    // 关联记在门店那一侧，所以这一格是服务层反查回填的，本表没有列。
    // 列表页拿它判"这条线索已经建过门店了，还让不让再建"，判据按门店表那格 investment_no 有没有值算
    @ApiModelProperty(value = "已由这条线索建出的门店ID（服务层按 shop_store.investment_no 反查回填，非本表列；NULL=还没建过店）")
    @TableField(exist = false)
    private Long storeId;

    @ApiModelProperty(value = "查询：招商编号/申请人/电话关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;

    @ApiModelProperty(value = "查询：签约时间范围-开始（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String beginTime;

    @ApiModelProperty(value = "查询：签约时间范围-结束（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String endTime;
}
