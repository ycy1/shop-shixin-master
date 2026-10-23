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
 * 门店培训（门店管理 -> 门店培训）
 *
 * 编号列 NOT NULL 但不进表单：新增时由系统生成（见 ShopStoreTrainingServiceImpl#insert）。
 * 所属门店必填（2026-09-24 起「总部统一培训」那一档取消了），列本身仍允许 NULL 存历史数据，
 * 必填由服务层 validate 挡；报名那一头因此永远知道自己是替哪家店报的。
 *
 * user_id 是「负责的培训讲师」，不是创建人（留痕那两列在 BaseEntity 的 create_by/update_by），
 * 同样可选可清空，用 IGNORED。
 *
 * sign_count 这一列读的时候不认库里的值：列表和详情那条 SQL 都按参训人员表现算
 * （count(sign_status=1) 覆盖同名属性），所以实体上钉了 NEVER —— UPDATE 永远不带它，
 * 后台改状态/改人员都不会把这列写歪。新增时由服务层 normalize 补 0（列 NOT NULL）。
 *
 * publish_time / finish_time 按 DDL 的注释是"状态第一次流转到 1/4 时服务端补、之后不覆盖"，
 * 表单里没有这两格，服务层 update 时按库里的旧值走（见 fillStatusTime）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_store_training")
@ApiModel(value = "门店培训")
public class ShopStoreTraining extends BaseEntity {

    @ApiModelProperty(value = "培训编号，唯一；新增时不传则由系统生成（TRAIN_+时间戳）")
    private String trainingNo;

    @ApiModelProperty(value = "培训名称")
    private String trainingName;

    @ApiModelProperty(value = "培训内容简介")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String content;

    @ApiModelProperty(value = "培训类型：1入职培训 2产品培训 3技能培训 4管理培训 5营销培训 6其他（字典 training_type）")
    private Integer trainingType;

    @ApiModelProperty(value = "培训方式：1线下 2线上直播 3录播 4混合（字典 train_mode）")
    private Integer trainMode;

    // 所属门店必填（2026-09-24 他把「总部统一培训」那一档撤了）：策略回到默认的 not_empty，
    // 也就是再没有哪条路能把这一格刷成 NULL —— 之前挂 IGNORED 就是为了「改回空 = 总部统一」，那个概念没了
    @ApiModelProperty(value = "所属门店ID（shop_store.id），必填")
    private Long storeId;

    @ApiModelProperty(value = "负责的培训讲师用户ID（sys_user.id，account_type=1 门店账号）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long userId;

    @ApiModelProperty(value = "经度(GCJ-02)，前端地图选点写入")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal longitude;

    @ApiModelProperty(value = "纬度(GCJ-02)，前端地图选点写入")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal latitude;

    @ApiModelProperty(value = "详细地址（街道、门牌号）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String address;

    @ApiModelProperty(value = "线上培训链接")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String trainUrl;

    @ApiModelProperty(value = "培训开始时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime startTime;

    @ApiModelProperty(value = "培训结束时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime endTime;

    @ApiModelProperty(value = "报名开始时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime signStart;

    @ApiModelProperty(value = "报名截止时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime signEnd;

    @ApiModelProperty(value = "最大参训人数，NULL=不限")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer maxCount;

    @ApiModelProperty(value = "已报名人数（读的时候按参训人员表现算覆盖，写的时候不进 SET）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer signCount;

    @ApiModelProperty(value = "状态：0待发布 1报名中 2已截止 3进行中 4已结束 5已取消（字典 training_status）")
    private Integer status;

    @ApiModelProperty(value = "发布时间（状态第一次置为 1 时服务端补，之后不覆盖）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime publishTime;

    @ApiModelProperty(value = "结束时间（状态第一次置为 4 时服务端补，之后不覆盖）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime finishTime;

    @ApiModelProperty(value = "备注")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;

    // 门店名/讲师名一律联表现取，不在这张表里冗余快照（同采购单的主表），
    // 所以这两个字段不是本表列，只在两条读 SQL 的结果里带回来（也供表单回显）
    @ApiModelProperty(value = "所属门店名称（联表带出，非本表列）")
    @TableField(exist = false)
    private String storeName;

    @ApiModelProperty(value = "培训讲师昵称（联表带出，非本表列）")
    @TableField(exist = false)
    private String userName;

    // 这两格是「当前登录账号在这一场的报名/签到状态」：只有列表那条 SQL 联本账号在参训人员表里那一行带得回来
    // （没报过名的人联不到行，两格都是 null）。前台操作列那两颗按钮靠它们判可见，不参与任何写入。
    // 名字不叫 signStatus：本表另有 sign_start / sign_end / sign_count 三格，再挤一个 signStatus 分不清是谁的状态
    @ApiModelProperty(value = "我在这一场的报名状态（联表带出，非本表列；没报过名为 null，字典 training_sign_status）")
    @TableField(exist = false)
    private Integer selfSignStatus;

    @ApiModelProperty(value = "我在这一场的签到状态（联表带出，非本表列；没报过名为 null，字典 training_attend_status）")
    @TableField(exist = false)
    private Integer selfAttendStatus;

    // 查询用的三个临时字段。筛「培训开始时间」区间的两个名字刻意不叫 beginTime/endTime：
    // endTime 在本表是"培训结束时间"那一列，撞了名实体就没法再同时当查询条件用
    @ApiModelProperty(value = "查询：培训编号/名称/地点关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;

    @ApiModelProperty(value = "查询：培训开始时间-区间起（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String startFrom;

    @ApiModelProperty(value = "查询：培训开始时间-区间止（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String startTo;
}
