package com.shop.vo.trendy;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 主题公园园区 Web 端视图（潮玩专区「公园」那一页的卡片 + 详情）
 *
 * 只给营业中的园区（判据在服务层，不在这里）：停业的园区名字还在、票却买不到，
 * 前台摆出来就是让人白跑一趟，所以停业的那几行根本不会被查出来。
 * 园区编号、关联IP、创建人这些后台字段不外给（口径同商品那份 WebVo）。
 */
@Data
@ApiModel(value = "主题公园园区 Web 端视图")
public class ShopTrendyParkWebVo {

    @ApiModelProperty(value = "园区id")
    private Long id;

    @ApiModelProperty(value = "园区名称")
    private String parkName;

    @ApiModelProperty(value = "封面 URL（首页轮播位和卡片都用这一张）")
    private String coverImage;

    @ApiModelProperty(value = "照片卡片上那几格（服务层回填，只给审核通过的、精选在前，最多 6 张；列表那一条不填，详情才填）")
    private List<ShopParkPhotoWebVo> photos;

    @ApiModelProperty(value = "地区中文，如 上海市 上海市 黄浦区")
    private String areaZh;

    @ApiModelProperty(value = "详细地址")
    private String address;

    @ApiModelProperty(value = "经度")
    private BigDecimal longitude;

    @ApiModelProperty(value = "纬度")
    private BigDecimal latitude;

    @ApiModelProperty(value = "营业时间，如 09:30-21:30")
    private String openTime;

    @ApiModelProperty(value = "园区介绍")
    private String description;

    @ApiModelProperty(value = "状态：1营业中（能被查到的只有 1）")
    private Integer status;

    @ApiModelProperty(value = "是否热门：1是 0否（首页那圈轮播优先摆热门的）")
    private Integer isHot;

    @ApiModelProperty(value = "名下在架票种数（服务层回填）")
    private Integer ticketCount;

    @ApiModelProperty(value = "名下在架票种的最低价（服务层回填；一个票种都没上架就是 null）")
    private BigDecimal priceMin;

    /** 只有园区详情那一条会填（列表不为它多跑一趟查询），页面渲染的是这一份而不是 ticketCount */
    @ApiModelProperty(value = "名下在架票种（详情用，按排序）")
    private List<ShopParkTicketWebVo> tickets;
}
