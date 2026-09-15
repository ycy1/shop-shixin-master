package com.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_file_center")
@ApiModel(value = "文件中心记录表")
public class SysFileCenter implements Serializable {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @ApiModelProperty(value = "关联文件id")
    private String fileId;

    @ApiModelProperty(value = "发起上传/下载的用户ID")
    private Long userId;

    @ApiModelProperty(value = "业务类型")
    private String businessType;

    @ApiModelProperty(value = "最终生成的文件名称（含后缀）")
    private String fileName;

    @ApiModelProperty(value = "本地相对路径 或 OSS的Key")
    private String fileUrl;

    @ApiModelProperty(value = "文件大小（单位：Byte）")
    private Long fileSize;

    @ApiModelProperty(value = "文件来源: 1-用户上传, 2-部门, 3-角色, 4-回收站")
    private Integer fileSource;

    @ApiModelProperty(value = "删除前的来源(用于还原): 1-用户上传, 2-部门, 3-角色")
    private Integer originalSource;

    @ApiModelProperty(value = "文件状态: 1-处理中, 2-已完成, 3-失败, 4-已过期/已清理")
    private Integer fileStatus;

    @ApiModelProperty(value = "失败时的错误信息（用户可读）")
    private String failReason;

    @ApiModelProperty(value = "创建时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "最近更新时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime updateTime;

    @ApiModelProperty(value = "文件过期时间")
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime expireTime;

    @ApiModelProperty(value = "该文件被下载的次数")
    private Integer downloadCount;
}
