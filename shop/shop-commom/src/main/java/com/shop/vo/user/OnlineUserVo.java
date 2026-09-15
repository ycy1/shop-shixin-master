package com.shop.vo.user;

import com.shop.entity.SysUser;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;


/**
 * @author: quequnlong
 * @date: 2025/1/3
 * @description:
 */
@Data
public class OnlineUserVo extends SysUser {

    @ApiModelProperty(value = "token")
    private String tokenValue;

}
