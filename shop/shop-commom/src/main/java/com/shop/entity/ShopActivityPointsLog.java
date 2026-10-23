package com.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户积分流水（积分管理列表那行账户点开的「积分流水」弹窗）：一次积分变动一行，只插不改不删
 *
 * 这张表是 shop_activity_points 那本账的流水页：账上只有当前数（balance 那一格），
 * 谁在什么时候因为哪张单把积分从多少改成多少、那一笔什么时候过期，全在这一张表里。
 * 所以只有查询接口 —— 手工插一条"某人多了 100 分"的记录，账和流水就对不上了
 *（写法同库存流水 ShopErpInventoryLog，只是这张表连"谁动的"都还不一定有写入方）。
 *
 * 和别的 shop_* 表不一样的一处：**不继承 BaseEntity**。你给的 DDL 里这张表就没有
 * update_by / update_time / is_deleted 三列，这是对的（流水是凭证，不该被改、也不该被删）。
 * 继承了 BaseEntity，MP 会把那三列写进 INSERT 的列清单、还会给查询自动补 `is_deleted = 0`，
 * 库里没这几列就直接报 Unknown column —— 所以这里只声明本表真有的列，
 * 自定义 SQL 里也就不需要手写逻辑删除那一刀。
 *
 * points 是带符号的（获取为正、消耗为负），balance 记的是**这一笔之后**账户上的可用积分：
 * 上一行的 balance 接下一行的 points 就是这条账的来龙去脉，所以这两个数必须由写流水的那一步一起给。
 */
@Data
@TableName("shop_activity_points_log")
@ApiModel(value = "用户积分流水")
public class ShopActivityPointsLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "用户ID（sys_user.id）")
    private Long userId;

    @ApiModelProperty(value = "变动积分，正为获取，负为消耗")
    private Integer points;

    @ApiModelProperty(value = "变动后余额（写流水的那一步和账户表同一个事务里算出来）")
    private Integer balance;

    @ApiModelProperty(value = "业务类型：1签到 2购物 3评价 4分享 5邀请 6注册 7兑换 8抵扣 9过期 10管理员调整 11徽章发放（字典 points_biz_type）")
    private Integer bizType;

    @ApiModelProperty(value = "关联业务ID（订单ID、评价ID等，按 bizType 指向不同的表，所以库上没有外键）")
    private Long bizId;

    @ApiModelProperty(value = "关联业务单号（冗余，同出库单/配送单那一路的口径）")
    private String bizNo;

    @ApiModelProperty(value = "说明（这一笔为什么得/扣这些分，给人看的）")
    private String description;

    /** 只有获取那一头的行有值：expire_day 从 create_time 那天往后数；消耗那几行留 NULL */
    @ApiModelProperty(value = "过期时间（获取时记，过期时用；消耗/抵扣那几行为 NULL）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime expireTime;

    @ApiModelProperty(value = "创建人用户ID（留痕）")
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @ApiModelProperty(value = "创建时间（就是这一笔变动发生的时间）")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime createTime;

    // 不是本表列：XML 联 sys_user 带出来，弹窗顶上那行标题要说"这是谁的流水"（同库存流水的昵称）
    @ApiModelProperty(value = "用户昵称（联表回填，非本表列）")
    @TableField(exist = false)
    private String userName;
}
