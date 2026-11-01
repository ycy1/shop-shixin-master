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

    @ApiModelProperty(value = "所属门店id（潮玩的绑定商品面板按门店筛候选用；商品列表那一页不送这一格）")
    private Long storeId;

    /** 绑定面板的主列表用这一格：只列已绑到这条系列上的商品 */
    @ApiModelProperty(value = "潮玩系列id：只看绑到这条系列的商品")
    private Long sectionId;

    /** 绑定面板里「挑新候选」那一层用：只要没绑任何专区（section_id 为空）的商品。和 sectionId 同时传时按 sectionId 为准 */
    @ApiModelProperty(value = "true=只看还没绑任何潮玩系列的商品")
    private Boolean noTrendy;

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
