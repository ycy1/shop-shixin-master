package com.shop.dto.product;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 商品 Web 端查询参数（/api/web/shop/product 那两条读接口专用）
 *
 * 不复用 ShopProductQueryDto：那边带着 status（能把下架的单独筛出来）和 currentUserId 两格，
 * 这一条又不看登录态，挂上去等于让调用方自己决定能看见哪个状态的商品。
 * Web 端只看「已上架」，这一档钉死在 ShopProductMapper.xml 的 SQL 里，不在这收口。
 *
 * 分页参数（pageNum/pageSize）由 PageUtil 直接从请求里取，不在此处声明。
 */
@Data
@ApiModel(value = "商品 Web 端查询参数")
public class ShopProductWebQueryDto {

    @ApiModelProperty(value = "商品名称/编码关键字")
    private String keyword;

    @ApiModelProperty(value = "商品分类id，命中这一级连同它整棵下级一起筛")
    private Long categoryId;

    // 和 status 那一档不一样：上架是必须钉死的判据，这一列只是挑一块位置放哪些商品，所以留给调用方筛
    @ApiModelProperty(value = "是否轮播：1只取进首页轮播位的 0只取不进的；不传=都取")
    private Integer carouselEnable;

    // 门店详情页的「本店商品」。等值筛，不含 store_id 为 NULL 的平台货 —— 那一档是「不限定门店」，
    // 不算进任何一家店名下，硬塞进每家店的页面反而会让人以为这家店有卖。
    @ApiModelProperty(value = "所属门店id，只取这一家店的货；不传=不限门店")
    private Long storeId;

    // 品牌详情页的「品牌商品」那一列。和 storeId 一样是等值筛，不看分类、也不看这个品牌的其它属性。
    @ApiModelProperty(value = "品牌id，只取这个品牌的货；不传=不限品牌")
    private Long brandId;

    // 门店详情页商品区那两档 tab（门店商品 / 文创商品）靠这一格切：1 只取文创、0 只取普通商品。
    // 这一列 NOT NULL，所以 0 那一档不需要补 IS NULL 兜历史行。
    @ApiModelProperty(value = "是否文创商品：1只取文创商品 0只取普通商品；不传=两档都取")
    private Integer culturalEnable;

    /**
     * 由服务层按 categoryId 展开的整棵子树（含它自己），SQL 只认这一格。
     *
     * 故意不接收请求参数：这一条是 GET 对象绑定，调用方直接传 categoryIds 就能绕过
     * categoryId 那层校验，所以服务层每趟都重算覆盖 —— 没传 categoryId 就置空，SQL 那段 <if> 不生效。
     */
    @ApiModelProperty(value = "分类id集合（服务端按 categoryId 展开下级后回填，传了也不认）", hidden = true)
    private List<Long> categoryIds;
}
