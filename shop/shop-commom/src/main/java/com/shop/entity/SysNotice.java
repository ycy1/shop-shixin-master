package com.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.utils.DateUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.time.LocalDateTime;
import java.io.Serializable;

@Data
@TableName("sys_notice")
@ApiModel(value = "公告对象")
public class SysNotice implements Serializable {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty(value = "主键")
    private Long id;

    @ApiModelProperty(value = "标题")
    private String title;

    @ApiModelProperty(value = "公告内容")
    private String content;

    @ApiModelProperty(value = "是否展示")
    private Integer isShow;

    @ApiModelProperty(value = "显示位置 （top：顶部，right:右侧）")
    private String position;

    @ApiModelProperty(value = "推送对象（JSON：{\"user\":[],\"dept\":[],\"role\":[]}，null 或全空=全员可见）")
    private String noticePush;

    @ApiModelProperty(value = "是否立即发送（新增弹窗“发送”按钮触发，true 则保存后立即推送，不入库）")
    @TableField(exist = false)
    private Boolean send;

    @ApiModelProperty(value = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = DateUtil.YYYY_MM_DD_HH_MM_SS, timezone = "GMT+8")
    private LocalDateTime createTime;
}
