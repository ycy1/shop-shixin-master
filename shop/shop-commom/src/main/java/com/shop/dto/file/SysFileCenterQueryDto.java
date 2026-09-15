package com.shop.dto.file;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 文件中心分页查询参数
 */
@Data
@ApiModel(value = "文件中心查询参数")
public class SysFileCenterQueryDto {

    @ApiModelProperty(value = "Tab来源: 1-我的文件, 2-部门文件, 3-角色文件, 4-回收站")
    private Integer source;

    @ApiModelProperty(value = "当前登录用户ID（服务端填充）")
    private Long userId;

    @ApiModelProperty(value = "是否管理员（服务端填充）")
    private Boolean admin;

    @ApiModelProperty(value = "文件名称（模糊）")
    private String fileName;

    @ApiModelProperty(value = "业务类型")
    private String businessType;

    @ApiModelProperty(value = "文件状态")
    private Integer fileStatus;

    @ApiModelProperty(value = "上传时间范围-开始")
    private String startTime;

    @ApiModelProperty(value = "上传时间范围-结束")
    private String endTime;
}
