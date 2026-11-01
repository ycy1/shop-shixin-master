package com.shop.service;

import java.math.BigDecimal;

/**
 * @author xxj
 * @title ShopCartAddService
 * @description 往购物车加一件规格的这一条写（前台那颗「加入购物车」和抽盒抽中的实物进车，共用这一份判据）
 *
 * 从 shop-api 的购物车写入下沉到这里（口径同出票那一段）：判据只有一份，
 * 不然「什么规格能进车」在两处各写一遍，迟早出现详情页能加、抽中却加不进去（或反过来）。
 * 两份的差别只在单价那一格：详情页那颗按这条规格的现价，抽中按盲盒的单抽价
 *（见 {@link #tryAdd}，以及下单取价那一步 ShopOrderCreateServiceImpl#buildItem）。
 */
public interface ShopCartAddService {

    /**
     * 加购（前台那颗按钮走的这一条）：规格不存在、所属商品没了、商品已下架、商品还没分类，
     * 四种都在当场抛出拒单文案 —— 人是主动要点这一颗的，说不清楚为什么没加进去才叫失败。
     *
     * 单价按这条规格**当下**的 product_price 写进那一行（不是留 NULL）：车里的行自己带价，
     * 结算和订单明细都读那一格，运营中途改了 SKU 价也不影响已经在车里的（那一行是加购那一刻的价）。
     *
     * @param userId   加到谁名下（调用方负责钉成登录 id，这里不查登录态）
     * @param skuId    商品规格(shop_product_sku.id)
     * @param quantity 这次加几件，大于 0
     */
    void add(Long userId, Long skuId, int quantity);

    /**
     * 抽中的实物进车走的这一条：三条写法同上，只是来源钉成「盲盒商品」、单价由调用方给。
     *
     * 判据和上面那一条完全一样，过不了的从「抛」换成「return false」—— 差别只在"谁发起的"：
     * 加购是人自己点的，拒了要当场给说法；抽盒是盒里配什么就抽什么，抽中一件刚好被下架的商品不该
     * 把这一趟抽盒整笔退回（款式库存和盒的可抽次数都已经扣掉了）。
     *
     * 为什么要连来源一起分开成不同行：同一个规格可能既被自己加购过、又被抽中过，而一行只存一个单价 ——
     * 挤在同一行上时后写的那一次会把前一次的价冲掉（手动加购把盒价刷回原价就是这一版修掉的场景），
     * 所以这一条写进去的行带 source_type=1 + source_id=哪一台盒，和手动那一行各算各的价、各叠各的数量。
     * 分盒也是同一道理：两台盒给同一款的单抽价可以不一样，只按 source_type 分行还是会让后抽中那台
     * 把自己的盒价刷到前一台那行上，所以来源ID 也进这一行的身份（唯一键的四格）。
     *
     * @param price    这一件按什么价结（盲盒传盒上给的那个价）；传 null 就按这条规格当下的现价写
     * @param sourceId 抽中它的那台盒(shop_trendy_box.id)，落到车里的 source_id = 这一行的身份；传 null 按 0 记
     * @return 真的进车了回 true；这一条规格挂的东西不成回 false
     */
    boolean tryAdd(Long userId, Long skuId, int quantity, BigDecimal price, Long sourceId);

    /**
     * 端盒那一趟进车走的这一条：一整盒躺成一行，不是盒内每款一行。
     *
     * 和上面两条的分别是三件事：这一行没有"某个规格"（sku_id 落 0 当哨兵，分类那格同样落 0，
     * 品牌和门店给空）；单价是**这一盒的价**（盒上配的端盒价，没配就退回单抽价 × 本趟件数）；
     * 数量那一格是「几盒」。盒内有哪几款、各几件**不在行上存快照** —— 按 source_id 现查那台盒配置的
     * 款式清单，页面上展开看的是它、下单把它拆成每款一条明细的也是它（见 ShopTrendyBoxStyleService）。
     *
     * 同一台盒端第二次并到同一行上数量 +1，单价被这一次的最新端盒价覆盖 —— 和商品那一路
     *「后一次加购定这一行的价」同一个口径（运营中途改了端盒价，先端那一盒也跟着新价结）。
     *
     * @param boxPrice 这一盒多少钱
     * @return 真的进车了回 true；参数不成回 false（抽盒不该因为车里落不下去就把这一趟退回）
     */
    boolean tryAddBox(Long userId, Long boxId, BigDecimal boxPrice);
}
