package com.shop.vo.cache;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "缓存值修改参数")
public class CacheValueDto {

    @ApiModelProperty(value = "键名")
    private String key;

    @ApiModelProperty(value = "要写回的内容（string 那一格库里存的原文，改坏的 JSON 服务端会拦一下）")
    private String value;
}
