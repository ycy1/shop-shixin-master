package com.shop.vo.cache;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "缓存键的内容")
public class CacheValueVo {

    @ApiModelProperty(value = "键名")
    private String key;

    @ApiModelProperty(value = "类型")
    private String type;

    @ApiModelProperty(value = "过期时间（秒，-1 永不过期）")
    private Long ttl;

    @ApiModelProperty(value = "这一格能不能改（只有 string 给改，其余类型只能整体删）")
    private Boolean editable;

    @ApiModelProperty(value = "内容：string 是库里那串原文，其它类型按成员/字段一行一条铺开")
    private String value;
}
