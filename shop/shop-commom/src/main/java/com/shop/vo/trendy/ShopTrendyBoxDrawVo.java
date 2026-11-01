package com.shop.vo.trendy;

import com.shop.entity.ShopTrendyBoxUser;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一次抽盒的结果
 *
 * 前台抽完直接拿这一份弹结果：本趟抽了几次、这一趟按盒的类型该付多少钱、抽中了哪几个款（快照）。
 * 盒的剩余情况一起带回，页面不用为这几格再发一次详情。
 *
 * @author xxj
 */
@Data
@ApiModel(value = "盲盒抽取结果")
public class ShopTrendyBoxDrawVo {

    @ApiModelProperty(value = "盲盒ID")
    private Long boxId;

    @ApiModelProperty(value = "盲盒编号")
    private String boxNo;

    @ApiModelProperty(value = "盲盒名称")
    private String boxName;

    @ApiModelProperty(value = "盲盒类型：1单抽 2端盒 3一番赏 4抽签（字典 trendy_box_type）")
    private Integer boxType;

    @ApiModelProperty(value = "本趟实际抽中几个（端盒/一番赏可能少于请求的个数：池子抽完了就到头）")
    private Integer drawnCount;

    /**
     * 本趟有几件真的进了购物车（shop_consumer_cart）。
     * 抽签不发实物 = 0；抽中那一款没挂规格、或它所属商品已下架，那一种也不算进来（其余按 SKU 合并件数）。
     * 页面拿它念「已加入购物车 N 件」那一句，别拿 drawnCount 念 —— 那两个数不总是相等。
     */
    @ApiModelProperty(value = "本趟进购物车几件（抽签和挂不上规格的款式不进车）")
    private Integer cartCount;

    @ApiModelProperty(value = "本趟应付金额（一整盒按端盒价，其它按单抽价 × 次数）。只报数，不扣款——支付那条口还没接")
    private BigDecimal cost;

    /**
     * 这一趟是不是一整盒：盒本身就是端盒（box_type=2）、或页面点的是那颗「购买整盒」。
     *
     * 页面按它决定弹窗那一句怎么说，也按它知道「车里刚多出的是整盒那一行，不是盒内每款一行」。
     */
    @ApiModelProperty(value = "这一趟是不是一整盒（盒类型是端盒、或点了「购买整盒」）")
    private Boolean wholeBox;

    @ApiModelProperty(value = "是否公示概率（1是 0否）。为 0 时前台别把概率印出去")
    private Integer isProbabilityShow;

    @ApiModelProperty(value = "盒的总可抽次数，0=不限")
    private Integer totalCount;

    @ApiModelProperty(value = "盒累计已抽次数（本趟已计入）")
    private Integer boxDrawnCount;

    @ApiModelProperty(value = "本趟抽中的记录（款式名/图/是否隐藏都是当时的快照）")
    private List<ShopTrendyBoxUser> records;
}
