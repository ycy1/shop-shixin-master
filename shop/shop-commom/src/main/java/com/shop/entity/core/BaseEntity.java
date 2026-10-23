package com.shop.entity.core;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * shop_* 表共用字段：主键、留痕四件套、逻辑删除。
 *
 * createBy 只在新增时写（NEVER 让它永不进 UPDATE 的 SET），updateBy 新增和修改都写，
 * 两者存的都是 sys_user.id，由 MyMetaObjectHandler 按当前登录用户强制覆盖，表单不用传也不该由表单决定。
 *
 * isDeleted 不写 @TableLogic：application.yml 的 logic-delete-field 是按字段名接管的，
 * 只要实体（含继承）有 isDeleted 属性，MP 就会自动加 is_deleted = 0 并把删除改成 UPDATE。
 * 所以所有 shop_* 表都必须有这一列，建表时别漏。
 */
@Data
public class BaseEntity implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "创建人用户ID")
    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private Long createBy;

    @ApiModelProperty(value = "修改人用户ID")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @ApiModelProperty(value = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "修改时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime updateTime;

    @ApiModelProperty(value = "逻辑删除：0未删除 1已删除")
    private Integer isDeleted = 0;
}
