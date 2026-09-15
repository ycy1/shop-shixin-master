package com.shop.dto.user;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 添加部门人员请求
 */
@Data
@ApiModel(value = "添加部门人员请求")
public class AddDeptUserDTO {

    @ApiModelProperty(value = "部门ID")
    private Long deptId;

    @ApiModelProperty(value = "用户ID列表")
    private List<Integer> userIds;
}
