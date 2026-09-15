package com.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("ai_config_source")
@ApiModel(value = "知识源配置对象")
public class ConfigSource implements Serializable {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty(value = "配置ID")
    private Long id;

    @ApiModelProperty(value = "知识源类型: database-数据库, document-文档")
    private String sourceType;

    @ApiModelProperty(value = "知识源名称")
    private String sourceName;

    @ApiModelProperty(value = "知识源描述")
    private String sourceDescription;

    @ApiModelProperty(value = "数据库类型: mysql, postgresql, oracle")
    private String dbType;

    @ApiModelProperty(value = "JDBC连接URL")
    private String jdbcUrl;

    @ApiModelProperty(value = "数据库用户名")
    private String dbUsername;

    @ApiModelProperty(value = "数据库密码（AES加密）")
    private String dbPassword;

    @ApiModelProperty(value = "文档存储类型: local-本地, url-链接")
    private String docStorageType;

    @ApiModelProperty(value = "文档路径（本地路径或URL）")
    private String docPath;

    @ApiModelProperty(value = "文档分块大小（字符数）")
    private Integer chunkSize;

    @ApiModelProperty(value = "是否启用: 0-禁用, 1-启用")
    private Integer enabled;

    @ApiModelProperty(value = "创建人")
    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新人")
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updatedTime;

    @ApiModelProperty(value = "逻辑删除: 0-未删除, 1-已删除")
    private Integer deleted;
}
