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
 * 盲盒表
 *
 * 一张盲盒 = 一套玩法（类型/价格/概率/数量/时间窗），底下的款式在 shop_trendy_box_item。
 * 可空列一律标 IGNORED：全局 field-strategy=not_empty 会把 null 一起从 UPDATE 的 SET 里跳过，
 * 不标的话「把门店改回全平台」「把保底次数清掉」这类操作保存不生效（口径同 ShopBrand）。
 *
 * drawn_count 不标 IGNORED —— 它是抽奖那侧维护的计数，表单不给改也改不掉。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_trendy_box")
@ApiModel(value = "盲盒")
public class ShopTrendyBox extends BaseEntity {

    @ApiModelProperty(value = "盲盒编号，唯一；新增时不传由服务端生成（TBOX_+时间戳）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String boxNo;

    @ApiModelProperty(value = "盲盒名称")
    private String boxName;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "潮玩ID(shop_trendy.id)，NULL=不归属某个系列")
    private Long trendyId;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "所属门店ID，NULL=全平台")
    private Long storeId;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "负责的运营(sys_user.id)")
    private Long userId;

    @ApiModelProperty(value = "盲盒类型：1单抽 2端盒 3一番赏 4抽签（字典 trendy_box_type）")
    private Integer boxType;

    @ApiModelProperty(value = "单抽价格")
    private BigDecimal price;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "端盒价格（整盒买）")
    private BigDecimal endBoxPrice;

    @ApiModelProperty(value = "每次抽取数量，通常1")
    private Integer drawCount;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "最大抽取次数，NULL=不限")
    private Integer maxDrawCount;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "隐藏款概率（小数，0.0139=1.39%）")
    private BigDecimal hiddenRate;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "保底次数，抽N次必出隐藏")
    private Integer guaranteeCount;

    @ApiModelProperty(value = "是否公示概率：1是 0否（合规要求）")
    private Integer isProbabilityShow;

    @ApiModelProperty(value = "总可抽次数，0=不限")
    private Integer totalCount;

    @ApiModelProperty(value = "已抽取次数（抽奖那侧维护，表单不给改）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer drawnCount;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "盲盒封面 URL")
    private String coverImage;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "玩法/规则说明")
    private String description;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @ApiModelProperty(value = "开始时间")
    private LocalDateTime startTime;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    @ApiModelProperty(value = "结束时间")
    private LocalDateTime endTime;

    @ApiModelProperty(value = "状态：1未开始 2进行中 3已结束 4已取消（字典 trendy_box_status）")
    private Integer status;

    @ApiModelProperty(value = "是否热门：1是 0否（潮玩专区首页那台机器按这一格挑）")
    private Integer isHot;

    @ApiModelProperty(value = "排序")
    private Integer sort;

    // 下面三格都不是本表列，服务层按当页 id 一次查回来，只给列表那几列显示用（形状同 ShopStore.brandName）
    @TableField(exist = false)
    @ApiModelProperty(value = "潮玩系列名称（服务层回填，非本表列）")
    private String trendyName;

    @TableField(exist = false)
    @ApiModelProperty(value = "所属门店名称（服务层回填，非本表列；这一格为空就是全平台）")
    private String storeName;

    @TableField(exist = false)
    @ApiModelProperty(value = "负责运营名称（服务层回填，非本表列，表单那格 UserSelect 的回显读它）")
    private String userName;

    @TableField(exist = false)
    @ApiModelProperty(value = "名下款式数（服务层回填，非本表列）")
    private Integer itemCount;
}
