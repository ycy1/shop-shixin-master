package com.shop.dto.product;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 商品分页查询参数
 *
 * 分页参数（pageNum/pageSize）由 PageUtil 直接从请求里取，不在此处声明。
 */
@Data
@ApiModel(value = "商品查询参数")
public class ShopProductQueryDto {

    @ApiModelProperty(value = "商品名称/编码关键字")
    private String keyword;

    @ApiModelProperty(value = "商品分类id")
    private Long categoryId;

    @ApiModelProperty(value = "品牌id")
    private Long brandId;

    /** 按 id 批量取：给「手上只有一串 id、要把名字补回来」的回显用（优惠券的范围值就是这样存的） */
    @ApiModelProperty(value = "商品id集合（回显用，与其它筛选项并列）")
    private List<Long> ids;

    @ApiModelProperty(value = "状态：1上架 0下架（shop_product 自己的列，SPU 级）")
    private Integer status;

    @ApiModelProperty(value = "创建时间范围-开始")
    private String beginTime;

    @ApiModelProperty(value = "创建时间范围-结束")
    private String endTime;

    // 数据范围：这个字段会随查询串绑进来，所以服务层一律覆盖（非 admin 填自己的ID、admin 置空），
    // 不能靠它为空来判断身份
    @ApiModelProperty(hidden = true)
    private Long currentUserId;
}
