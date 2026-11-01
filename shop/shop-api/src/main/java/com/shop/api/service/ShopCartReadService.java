package com.shop.api.service;

import com.shop.vo.cart.ShopCartVo;

import java.util.List;

/**
 * 购物车 前台读取接口（商城前台 /api/web/shop/cart/**，购物车那一页整块的数据）
 *
 * 这一张表的读法有两条，分工同收货地址那一份：后台那条 /shop/cart/list 按账号类型收窄
 *（消费者账号只看自己的，平台/门店等其它账号看全部）、走分页，而且压的是后台登录态；
 * 那一条既不在 /api/** 下（前台 dev proxy 和生产 nginx 都只转 /api），也不能拿「谁是管理员」这套身份给前台用，
 * 所以前台自己要的这一条在这另开一份 —— 只认登录 id 名下的那些行，没有可点名的 userId 参数。
 */
public interface ShopCartReadService {

    /**
     * 我这个人名下的购物车，一行是一个规格，一次全给、不分页。
     *
     * 不分页是这一页的用法决定的：数量、勾选、合计都横跨全部行，切成一页一页就没法「全选」也报不出总数。
     * 规格或商品已经被删掉的行不给（那一行联不出单价和名称）；商品下架、规格停售的照给，
     * 由 SQL 现算的 buyable 那一格标成不可购，前端画灰行、不计入选中和合计。
     */
    List<ShopCartVo> myCart();
}
