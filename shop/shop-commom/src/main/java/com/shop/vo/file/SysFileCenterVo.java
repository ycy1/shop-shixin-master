package com.shop.vo.file;

import com.shop.entity.SysFileCenter;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "文件中心记录VO")
public class SysFileCenterVo extends SysFileCenter {

    @ApiModelProperty(value = "所属用户昵称")
    private String ownerNickname;

    @ApiModelProperty(value = "所属用户账号")
    private String ownerUsername;
}
