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

import java.time.LocalDateTime;

/**
 * 培训参训人员（门店培训列表那一颗「培训人员」弹窗看的就这张表）
 *
 * 姓名/电话是报名那一刻从 sys_user 带的快照（DDL 的注释就是这么写的）：人后来改名或换手机号，
 * 这一条报名记录得留得住当时的样子，所以这两列是普通列，不联表覆盖。
 * 所属门店名称不在本表，读的时候联 shop_store 现取（同培训主表那一侧）。
 *
 * 写入那一头不在后台的 ShopStoreTrainingService 里：报名是"两端同一个动作"（门店在自己那一端报，
 * 后台以后替他登记要落同一个状态），所以走 POST /api/comm/store/training/sign，
 * 实现在 shop-api 的 ShopTrainingSignServiceImpl（shop-api 只依赖本模块，摸得到这张表的 mapper）。
 * 后台这一侧目前只有读的一头：列表页那颗「培训人员」弹窗按培训查列表。
 * 签到同理走 POST /api/comm/store/training/attend/{trainingId}（把这一行的 attend_status 翻成 1）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_store_training_member")
@ApiModel(value = "培训参训人员")
public class ShopStoreTrainingMember extends BaseEntity {

    @ApiModelProperty(value = "培训ID（shop_store_training.id）")
    private Long trainingId;

    @ApiModelProperty(value = "所属门店ID（shop_store.id）")
    private Long storeId;

    @ApiModelProperty(value = "参训员工ID（sys_user.id）")
    private Long userId;

    @ApiModelProperty(value = "参训人姓名（报名时的快照）")
    private String userName;

    @ApiModelProperty(value = "参训人电话（报名时的快照）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String userPhone;

    @ApiModelProperty(value = "报名状态：0未报名 1已报名 2已取消（字典 training_sign_status）")
    private Integer signStatus;

    @ApiModelProperty(value = "报名时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime signTime;

    @ApiModelProperty(value = "出勤状态：0未签到 1已签到（字典 training_attend_status）")
    private Integer attendStatus;

    @ApiModelProperty(value = "签到时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime attendTime;

    @ApiModelProperty(value = "所属门店名称（联表带出，非本表列）")
    @TableField(exist = false)
    private String storeName;
}
