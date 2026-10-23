package com.shop.vo.brand;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 品牌 Web 端视图（/api/web/shop/search/brands 专用）
 *
 * 同 ShopStoreWebVo：不直出实体。后台那份额外带着联系方式（招商用的电话/微信）、所属分类 id、
 * 排序值和创建人 id，都不属于给消费者看的范围。
 *
 * brandSpokesperson 只在品牌详情那条填（列表那条的列清单里没有这一格，回来就是 null），
 * 同 ShopStoreWebVo.coverImages 那一条理由。
 */
@Data
@ApiModel(value = "品牌 Web 端视图")
public class ShopBrandWebVo {

    @ApiModelProperty(value = "品牌id")
    private Long id;

    @ApiModelProperty(value = "品牌名称")
    private String brandName;

    @ApiModelProperty(value = "品牌LOGO URL")
    private String logo;

    @ApiModelProperty(value = "品牌介绍")
    private String description;

    @ApiModelProperty(value = "代言人图片，MySQL json 列原样给回（[{\"name\":\"\",\"url\":\"\"}]），只有详情那条填")
    private String brandSpokesperson;
}
