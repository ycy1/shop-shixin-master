package com.shop.dto.address;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 收货地址分页查询参数
 *
 * 分页参数（pageNum/pageSize）由 PageUtil 直接从请求里取，不在此处声明。
 */
@Data
@ApiModel(value = "收货地址查询参数")
public class ShopAddressQueryDto {

    @ApiModelProperty(value = "收货人姓名/电话关键字")
    private String keyword;

    @ApiModelProperty(value = "消费者用户ID（消费者账号登录时后端会覆盖为本人 id）")
    private Long userId;

    @ApiModelProperty(value = "标签（字典 address_tag 的值）")
    private String tag;

    @ApiModelProperty(value = "是否默认：1是 0否")
    private Integer isDefault;

    @ApiModelProperty(value = "创建时间范围-开始")
    private String beginTime;

    @ApiModelProperty(value = "创建时间范围-结束")
    private String endTime;
}
