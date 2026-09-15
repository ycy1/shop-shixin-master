package com.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 部门
 */
@Data
@TableName("sys_dept")
@ApiModel(value = "部门")
public class SysDept implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "父部门ID（0表示顶级部门）")
    private Long parentId;

    @ApiModelProperty(value = "所属部门ID列表（部门树筛选用）")
    @TableField(exist = false)
    private List<Long> parentIds;

    @ApiModelProperty(value = "部门名称/编码关键字（筛选用）")
    @TableField(exist = false)
    private String keyword;

    @ApiModelProperty(value = "部门名称")
    private String name;

    @ApiModelProperty(value = "部门编码（可选）")
    private String code;

    @ApiModelProperty(value = "部门负责人ID（关联用户表）")
    private Integer leaderId;

    @ApiModelProperty(value = "状态：1-启用 0-停用")
    private Integer status;

    @ApiModelProperty(value = "同级排序权重")
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.UPDATE)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<SysDept> children;

    @TableField(exist = false)
    private String leaderName;

    @ApiModelProperty(value = "部门下的人数")
    @TableField(exist = false)
    private Integer userCount;
}
