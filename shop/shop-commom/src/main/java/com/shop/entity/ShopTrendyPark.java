package com.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.entity.core.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 潮玩主题公园园区表
 *
 * 一家园区 = 一个线下场子（位置/营业时间/介绍），底下的票种在 shop_trendy_park_ticket，
 * 名下的照片在 shop_trendy_park_photo（原先这一格是 json 数组 images，一张图一行才挂得住审核状态和标签）。
 * 形状照抄 ShopStore：编号服务端生成、地区两列（编码 + 冗余中文）、经纬度配 ShopMap 选点。
 *
 * 可空列一律标 IGNORED：全局 field-strategy=not_empty 会把 null 和空串一起从 UPDATE 的 SET 里跳过，
 * 不标的话「把园区改回不绑IP」「清掉营业时间」这类操作保存不生效（口径同 ShopBrand）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_trendy_park")
@ApiModel(value = "主题公园园区")
public class ShopTrendyPark extends BaseEntity {

    @ApiModelProperty(value = "园区编号，唯一；新增时不传由服务端生成（PARK_+时间戳）")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String parkNo;

    @ApiModelProperty(value = "园区名称")
    private String parkName;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "关联IP ID(shop_trendy_ip.id)，NULL=不归属某个IP")
    private Long ipId;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "地区编码，逗号分隔的行政区划代码：110000,110100,110101")
    private String areaCode;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "地区中文，如 北京市 市辖区 东城区（提交时按编码解析出来冗余下来的）")
    private String areaZh;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "详细地址")
    private String address;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "经度")
    private BigDecimal longitude;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "纬度")
    private BigDecimal latitude;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "营业时间，如 09:00-21:00")
    private String openTime;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "园区封面 URL")
    private String coverImage;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @ApiModelProperty(value = "园区介绍")
    private String description;

    @ApiModelProperty(value = "状态：1营业中 0停业（字典 park_status）")
    private Integer status;

    @ApiModelProperty(value = "是否热门：1是 0否（潮玩专区首页那圈轮播按这一格挑）")
    private Integer isHot;

    // 下面两格都不是本表列，服务层按当页 id 一次查回来，只给列表那几列显示用（形状同 ShopStore.brandName）
    @TableField(exist = false)
    @ApiModelProperty(value = "关联IP名称（服务层回填，非本表列）")
    private String ipName;

    @TableField(exist = false)
    @ApiModelProperty(value = "名下票种数（服务层回填，非本表列；删除园区前先看这一格）")
    private Integer ticketCount;

    @ApiModelProperty(value = "查询：园区名称/编号关键字（只用于查询条件，不是本表列）")
    @TableField(exist = false)
    private String keyword;
}
