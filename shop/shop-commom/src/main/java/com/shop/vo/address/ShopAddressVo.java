package com.shop.vo.address;

import com.shop.entity.ShopConsumerAddress;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收货地址列表/详情视图对象
 *
 * 消费者昵称是联表 sys_user 带出的，不是 shop_consumer_address 的列，所以只出现在 VO 里。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "收货地址视图对象")
public class ShopAddressVo extends ShopConsumerAddress {

    @ApiModelProperty(value = "消费者昵称（联表带出）")
    private String nickname;

    @ApiModelProperty(value = "消费者用户名（联表带出）")
    private String username;
}
