package com.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 广场消息点赞（一行 = 这个人赞过这条消息；取消赞是硬删这一行）
 *
 * 和别的 shop_* 表不一样的一处：**不继承 BaseEntity**。你给的 DDL 里这张表就没有
 * update_by / update_time / is_deleted 三列，这是对的（赞是一个动作凭证，不该被改、也不该被删）。
 * 继承了 BaseEntity，MP 会把那三列写进 INSERT 的列清单、还给查询自动补 `is_deleted = 0`，
 * 库里没这几列就直接报 Unknown column —— 写法同积分流水 ShopActivityPointsLog。
 *
 * uk_message_user(message_id, user_id) 是唯一键：一个人对一条消息最多一行，
 * 所以重复点赞靠捕获 DuplicateKeyException 挡（同 sys_comment_like 那一路的写法），不先查后插。
 */
@Data
@TableName("shop_plaza_like")
@ApiModel(value = "广场消息点赞")
public class ShopPlazaLike implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "消息ID（shop_plaza_message.id）")
    private Long messageId;

    @ApiModelProperty(value = "点赞用户ID（sys_user.id）")
    private Long userId;

    @ApiModelProperty(value = "点赞时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime createTime;
}
