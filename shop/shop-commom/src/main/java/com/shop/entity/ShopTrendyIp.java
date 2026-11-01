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

import java.time.LocalDate;

/**
 * 潮玩IP表
 *
 * 层级在潮玩系列之上：一个 IP（Molly、Dimoo…）下挂若干 shop_trendy，靠 shop_trendy.ip_id 关联，
 * 绑/解都在「潮玩IP」列表那个面板里改，口径同系列绑商品。
 * 这张表和 shop_trendy 一样是平台级字典，刻意不收 user_id、不按账号类型收窄数据范围。
 *
 * 可空列一律标 IGNORED：全局 field-strategy=not_empty 会把 null 一起从 UPDATE 的 SET 里跳过，
 * 不标的话「把英文名清掉」「授权到期日期改掉」这类操作保存不生效（口径同 ShopTrendy）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_trendy_ip")
@ApiModel(value = "潮玩IP")
public class ShopTrendyIp extends BaseEntity {

    @ApiModelProperty(value = "IP名称，唯一")
    private String ipName;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "IP英文名")
    private String ipEnName;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "IP Logo URL")
    private String ipLogo;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "IP封面图 URL")
    private String ipCover;

    /** json 列按项目的约定映射成 String：存的是 [{"title":"","url":""}] 这一段 JSON 文本，页面自己拆成那组图 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "IP轮播图，JSON数组字符串")
    private String ipBanner;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "IP介绍")
    private String ipDesc;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "IP故事背景")
    private String ipStory;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "版权登记号")
    private String copyrightNo;

    /**
     * 版权人/授权人 —— 业务归属，不是数据范围：这张表是平台级字典，查询不按账号类型收窄这一格
     * （商品/门店表上的 user_id 才是收窄用的那一格，别混）。授权到期提醒就发给他。
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "版权人/授权人(sys_user.id)")
    private Long userId;

    @ApiModelProperty(value = "授权类型：1原创 2独家授权 3非独家授权 4联名（字典 license_type）")
    private Integer licenseType;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD, timezone = "GMT+8")
    @ApiModelProperty(value = "授权开始日期")
    private LocalDate licenseStart;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD, timezone = "GMT+8")
    @ApiModelProperty(value = "授权结束日期")
    private LocalDate licenseEnd;

    @ApiModelProperty(value = "是否热门IP：1是 0否")
    private Integer isHot;

    @ApiModelProperty(value = "是否推荐：1是 0否")
    private Integer isRecommend;

    @ApiModelProperty(value = "排序")
    private Integer sort;

    @ApiModelProperty(value = "状态：1启用 0禁用（字典 trendy_ip_status）")
    private Integer status;

    /** 名下绑了多少个系列：不是本表列，服务层按当页 id 一次分组数出来回填（列表那一列和那颗按钮都读它） */
    @ApiModelProperty(value = "绑定的系列数（服务层回填，非本表列）")
    @TableField(exist = false)
    private Integer trendyCount;

    /** 版权人/授权人的名字：也不是本表列，服务层按当页 user_id 一次查回来（列表那一列和表单那格的回显都读它） */
    @ApiModelProperty(value = "版权人/授权人名称（服务层回填，非本表列）")
    @TableField(exist = false)
    private String userName;
}
