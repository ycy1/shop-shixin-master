package com.shop.vo.user;

import com.shop.entity.SysUser;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
@ApiModel(value = "用户分页视图对象")
public class SysUserVo extends SysUser {

    @ApiModelProperty(value = "角色 code 集合")
    private List<String> roles;

    /** 冗余字段   **/
    @ApiModelProperty(value = "权限列表")
    private List<String> permissions;

    @ApiModelProperty(value = "部门ID集合")
    private List<Long> deptIds;

    @ApiModelProperty(value = "部门名称集合")
    private List<String> deptNames;

}
