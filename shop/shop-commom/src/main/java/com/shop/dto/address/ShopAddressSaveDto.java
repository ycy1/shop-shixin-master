package com.shop.dto.address;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 收货地址新增/修改参数
 *
 * area_zh 由前端按选中的行政区划代码解析后一起传（同门店、用户管理），
 * 消费者昵称这些回显字段不在这里声明，客户端传了也会被忽略。
 */
@Data
@ApiModel(value = "收货地址新增/修改参数")
public class ShopAddressSaveDto {

    @ApiModelProperty(value = "地址id（修改时传）")
    private Long id;

    @ApiModelProperty(value = "消费者用户ID")
    private Long userId;

    @ApiModelProperty(value = "收货人姓名")
    private String receiverName;

    @ApiModelProperty(value = "收货人电话")
    private String receiverPhone;

    @ApiModelProperty(value = "地区编码，逗号分隔的行政区划代码")
    private String areaCode;

    @ApiModelProperty(value = "地区中文")
    private String areaZh;

    @ApiModelProperty(value = "详细地址")
    private String detail;

    @ApiModelProperty(value = "邮编")
    private String postalCode;

    @ApiModelProperty(value = "经度(GCJ-02)")
    private BigDecimal longitude;

    @ApiModelProperty(value = "纬度(GCJ-02)")
    private BigDecimal latitude;

    @ApiModelProperty(value = "标签：家/公司/学校")
    private String tag;

    @ApiModelProperty(value = "是否默认：1是 0否")
    private Integer isDefault;
}
