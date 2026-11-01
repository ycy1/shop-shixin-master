package com.shop.service;

import com.shop.vo.cart.ShopCartBoxItemVo;

import java.util.List;

/**
 * 一整盒里有哪些款式 —— 按盒 id 现查那台盒配的款式清单（不在购物车行上存快照）
 *
 * 读的两处口径必须同一个：前台购物车页展开看的那一叠、统一下单把一整盒拆成每款一条明细的那一份。
 * 收在一份里就是为了这两处不会有一天一个说「盒里有 6 款」、另一个说「拆出 5 行」。
 */
public interface ShopTrendyBoxStyleService {

    /**
     * 这一盒 = 它当前配置的款式各一件（shop_trendy_box_item 里 status=1 的那些，按 sort、id 排）。
     *
     * 盒里的款式后来被运营改过，车上那一行跟着改 —— 选的是「盒是配置出来的」这个口径：
     * 那一行记的是「一台盒」，盒现在装了什么就是什么。那台盒被删了、或一款都没配，回来是空列表，
     * 由调用方决定怎么回（下单那一路当场报错，不给硬结一份看不见的货）。
     *
     * @param boxId 那台盒(shop_trendy_box.id)；传 null 回空列表
     */
    List<ShopCartBoxItemVo> stylesOf(Long boxId);
}
