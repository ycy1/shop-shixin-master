package com.shop.vo.activity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动详情 Web 端视图（GET /api/web/shop/activity/{id}，前台那一页活动详情）
 *
 * 和列表那份 ShopActivityWebVo 分开钉，就为了这一格 content：
 * 首页轮播和活动列表都不渲染富文本，把后台 WangEditor 出来的 HTML 递到不要登录的端点上是白给一个 XSS 面，
 * 所以只有「人已经点到这一条的详情页上了」这一条端点才带它 —— 前台渲染前还要过一道标签/属性白名单
 * （见 shop-web/src/lib/richtext.ts），两头各挡一遍。
 *
 * 依旧不给的：适用门店（store_id/storeName，那是投放范围不是内容）、三个统计数、launchTime、审计字段。
 */
@Data
@ApiModel(value = "活动详情 Web 端视图")
public class ShopActivityDetailWebVo {

    @ApiModelProperty(value = "活动id（埋点上报用的就是它）")
    private Long id;

    @ApiModelProperty(value = "活动名称（标题没填时页面拿这一格兜底）")
    private String activityName;

    @ApiModelProperty(value = "活动标题")
    private String title;

    @ApiModelProperty(value = "活动图片URL（可能为空，页面自己垫一块无图占位）")
    private String image;

    @ApiModelProperty(value = "视频URL（活动类型是视频的那几条才有）")
    private String videoUrl;

    @ApiModelProperty(value = "活动内容（富文本 HTML，前台过完白名单才 v-html）")
    private String content;

    @ApiModelProperty(value = "跳转类型：1商品 2分类 4外链 5小程序页面 6无跳转（字典 link_type）")
    private Integer linkType;

    @ApiModelProperty(value = "跳转值：商品ID/分类ID/URL/小程序页面路径")
    private String linkValue;

    @ApiModelProperty(value = "投放开始时间（NULL=不限）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "投放结束时间（NULL=不限）")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime endTime;
}
