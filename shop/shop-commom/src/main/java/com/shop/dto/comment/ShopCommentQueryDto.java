package com.shop.dto.comment;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author ycy
 * @title ShopCommentQueryDto
 * @description 前台评论区那一页的查询条件（Web 与 App 两端共用）
 *
 * 只有三格，且两格必填：一张表挂三类对象，businessId 单看会撞号（某个商品的 id 恰好等于某家门店的 id），
 * 所以 commentType 必须跟着一起进谓词 —— 少给一条都不查（判据在 ShopCommentReadServiceImpl）。
 * pageNum/pageSize 不在这里，由 PageUtil 直接从请求串里取，和后台列表同一套。
 */
@Data
@ApiModel(value = "评论列表查询条件")
public class ShopCommentQueryDto {

    @ApiModelProperty(value = "评论类型：1商品 2门店 3品牌 4广场（字典 comment_type），决定 businessId 指向哪张表")
    private Integer commentType;

    @ApiModelProperty(value = "评论对象：对应那张表的主键（商品是 SPU 那一层，不分规格）")
    private Long businessId;

    @ApiModelProperty(value = "排序：like 按点赞数排，其它（含不传）按时间新旧排；两种排法都把置顶那几条打头")
    private String sortType;
}
