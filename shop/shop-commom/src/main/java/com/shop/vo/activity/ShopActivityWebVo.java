package com.shop.vo.activity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动 Web 端视图（首页那一块活动轮播 + /api/web/shop/activity/list 那一页卡片共用）
 *
 * 不直出实体：后台那一份带 content（WangEditor 存的 HTML，只有活动详情页那条才给，见 ShopActivityDetailWebVo）、
 * videoUrl、适用门店、三个联表现算的统计数、还有审计字段 ——
 * 这两条端点都不要登录态，能不给的就别给。
 *
 * linkValue 原样给回（存的是商品id/分类id/URL/小程序页面路径，按 linkType 分档），
 * 拼不拼得出一个真链接由页面判：这一层不去查商品还上架没有、分类还启用没有，
 * 点进去由各自那条详情页自己的「不存在或已下架」兜住（同商品卡片挂链接那条口径）。
 */
@Data
@ApiModel(value = "活动 Web 端视图")
public class ShopActivityWebVo {

    @ApiModelProperty(value = "活动id（埋点上报用的就是它）")
    private Long id;

    @ApiModelProperty(value = "活动名称（标题没填时页面拿这一格兜底）")
    private String activityName;

    @ApiModelProperty(value = "活动标题")
    private String title;

    @ApiModelProperty(value = "活动图片URL（首页轮播那条只回传了图的，所以那一档这里必不为空；活动列表那条不挑图，可能为空）")
    private String image;

    @ApiModelProperty(value = "跳转类型：1商品 2分类 4外链 5小程序页面 6无跳转（字典 link_type）")
    private Integer linkType;

    @ApiModelProperty(value = "跳转值：商品ID/分类ID/URL/小程序页面路径")
    private String linkValue;

    // 投放起止这两格只是列表卡片上那一行「10-01 ~ 10-31」的说法：
    // 它们不是判据（全仓没有拿它们和当前时间比过，「正在投」只认 status=2），所以列表那条查询才敢不带时间条件
    @ApiModelProperty(value = "投放开始时间（NULL=不限）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "投放结束时间（NULL=不限）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime endTime;
}
