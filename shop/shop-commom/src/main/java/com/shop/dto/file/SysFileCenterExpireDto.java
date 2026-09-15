package com.shop.dto.file;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件中心过期时间设置
 */
@Data
@ApiModel(value = "文件中心过期时间设置")
public class SysFileCenterExpireDto {

    @ApiModelProperty(value = "文件ID集合")
    private List<Long> ids;

    @ApiModelProperty(value = "过期时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime expireTime;
}
