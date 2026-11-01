package com.shop.vo.trendy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 盲盒 Web 端视图（潮玩专区那一页的卡片）
 *
 * 不直接吐 ShopTrendyBox：那张表上还带着负责的运营（user_id）、所属门店、端盒价配置这些后台才看的格，
 * 前台读接口没有登录态，一股脑给出去就是泄露（口径同 ShopProductWebVo：要加字段先问一句这一格能不能给外人看）。
 */
@Data
@ApiModel(value = "盲盒 Web 端视图")
public class ShopTrendyBoxWebVo {

    @ApiModelProperty(value = "盲盒id")
    private Long id;

    @ApiModelProperty(value = "盲盒名称")
    private String boxName;

    @ApiModelProperty(value = "潮玩系列id，NULL=不归属某个系列")
    private Long trendyId;

    @ApiModelProperty(value = "潮玩系列名称（服务层回填）")
    private String trendyName;

    @ApiModelProperty(value = "盲盒封面 URL")
    private String coverImage;

    @ApiModelProperty(value = "盲盒类型：1单抽 2端盒 3一番赏 4抽签（字典 trendy_box_type）")
    private Integer boxType;

    @ApiModelProperty(value = "单抽价格")
    private BigDecimal price;

    @ApiModelProperty(value = "端盒价格（整盒买）")
    private BigDecimal endBoxPrice;

    @ApiModelProperty(value = "每次抽取数量")
    private Integer drawCount;

    @ApiModelProperty(value = "隐藏款概率（小数，0.0139=1.39%）")
    private BigDecimal hiddenRate;

    @ApiModelProperty(value = "保底次数")
    private Integer guaranteeCount;

    @ApiModelProperty(value = "是否公示概率：1是 0否")
    private Integer isProbabilityShow;

    @ApiModelProperty(value = "总可抽次数，0=不限")
    private Integer totalCount;

    @ApiModelProperty(value = "已抽取次数")
    private Integer drawnCount;

    @ApiModelProperty(value = "还剩多少次可抽；总次数不限（totalCount=0）时为 null")
    private Integer remainCount;

    @ApiModelProperty(value = "开始时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "结束时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime endTime;

    @ApiModelProperty(value = "状态：1未开始 2进行中（3已结束 4已取消 根本不会被查出来）")
    private Integer status;

    @ApiModelProperty(value = "是否热门：1是 0否（首页那两台机台优先摆热门的）")
    private Integer isHot;

    @ApiModelProperty(value = "玩法/规则说明")
    private String description;

    @ApiModelProperty(value = "名下在架款式数（服务层回填）")
    private Integer itemCount;

    @ApiModelProperty(value = "款式预览（服务层回填，按排序取前若干条，卡片上那一排小图）")
    private List<ShopTrendyBoxItemWebVo> styles;
}
