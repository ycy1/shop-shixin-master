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
 * 活动（营销活动 -> 活动管理）
 *
 * 这张表没有编号列（活动就靠 activity_name 认），所以主键之外没有唯一键，名称也故意不查重。
 *
 * 「适用门店」是这条活动投放给谁看：NULL = 全平台，填了就是那一家店的专场，
 * 所以这一格挂 IGNORED —— 清空它是合法操作（把专场改成全平台），和门店培训那个必填的所属门店不是一回事。
 *
 * 统计那三格（曝光量/点击量/点击率）库里根本没有列：它们只是读的时候由活动那条 SQL 按
 * shop_activity_click 现算带出来的三个别名（那一张表一行一次事件，event_type 分 1 曝光 2 点击），
 * click_rate 再由这两个数除出来，口径同门店培训的 sign_count（不存计数，所以后台改一次单不会把数写歪）。
 *
 * launch_time 按 DDL 的注释是"状态第一次流转到 2 投放中时服务端补、之后不覆盖"，
 * 表单里没有这一格，服务层 update 时按库里的旧值走（见 fillLaunchTime）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_activity")
@ApiModel(value = "活动")
public class ShopActivity extends BaseEntity {

    @ApiModelProperty(value = "活动名称")
    private String activityName;

    @ApiModelProperty(value = "活动位类型：1首页轮播 2首页Banner 3分类页 4详情页 5弹窗 6其他（字典 position_type）")
    private Integer positionType;

    @ApiModelProperty(value = "活动类型：1图片 2视频 3文字 4轮播 5弹窗（字典 activity_type）")
    private Integer activityType;

    @ApiModelProperty(value = "适用门店ID（shop_store.id），NULL=全平台")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long storeId;

    @ApiModelProperty(value = "活动标题")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String title;

    @ApiModelProperty(value = "活动图片（上传后的地址）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String image;

    @ApiModelProperty(value = "视频URL")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String videoUrl;

    @ApiModelProperty(value = "活动内容（富文本，后台表单用 WangEditor 编，库里存的是编辑器出来的 HTML）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String content;

    @ApiModelProperty(value = "跳转类型：1商品 2分类 4外链 5小程序页面 6无跳转（字典 link_type；3 那一档是「跳本表另一条活动」，已下线）")
    private Integer linkType;

    @ApiModelProperty(value = "跳转值：商品ID/分类ID/URL/小程序页面路径（1、2 两档存 id，名称读的时候联表现取）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String linkValue;

    @ApiModelProperty(value = "投放开始时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime startTime;

    @ApiModelProperty(value = "投放结束时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime endTime;

    @ApiModelProperty(value = "权重，数字越大越靠前")
    private Integer weight;

    @ApiModelProperty(value = "曝光量（按 shop_activity_click 的 1曝光 行数现算带出，库里没有这一列）")
    @TableField(exist = false)
    private Integer viewCount;

    @ApiModelProperty(value = "点击量（按 shop_activity_click 的 2点击 行数现算带出，库里没有这一列）")
    @TableField(exist = false)
    private Integer clickCount;

    @ApiModelProperty(value = "点击率（小数，0.25=25%；由那两个数除出来，曝光为 0 时是 NULL；库里没有这一列）")
    @TableField(exist = false)
    private BigDecimal clickRate;

    @ApiModelProperty(value = "状态：1未开始 2投放中 3已结束 4已取消（字典 activity_status）")
    private Integer status;

    @ApiModelProperty(value = "投放时间（状态第一次置为 2 时服务端补，之后不覆盖）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime launchTime;

    @ApiModelProperty(value = "备注")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;

    // 门店名一律联表现取，不在这张表里冗余快照（同门店培训）：
    // 全平台的那一条联不上门店，这一格就是 NULL，前端显示成「全平台」
    @ApiModelProperty(value = "适用门店名称（联表带出，非本表列）")
    @TableField(exist = false)
    private String storeName;

    // 跳转目标的名字也联表现取（库里那一格只存了个 id，商品/分类改了名要跟着变，冗余一份迟早对不上）：
    // 只有 1商品 / 2分类 两档有值，外链和小程序页面本来就是手填的内容、无跳转那一档就是 NULL，前端按 id 兜底显示
    @ApiModelProperty(value = "跳转目标名称（跳转类型是商品/分类时联表带出，非本表列）")
    @TableField(exist = false)
    private String linkName;

    // 查询用的三个临时字段。筛「投放开始时间」区间的两个名字刻意不叫 beginTime/endTime：
    // endTime 在本表是"投放结束时间"那一列，撞了名实体就没法再同时当查询条件用
    @ApiModelProperty(value = "查询：活动名称/标题关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;

    @ApiModelProperty(value = "查询：投放开始时间-区间起（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String startFrom;

    @ApiModelProperty(value = "查询：投放开始时间-区间止（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String startTo;
}
