package com.shop.api.service;

/**
 * 用户收藏 写入接口（商城前台 /api/web/shop/profile/**，个人中心「我的收藏」块上那颗「删除」
 * + 商品/门店详情页右侧那颗收藏按钮）
 *
 * 分工同收货地址那一份：这一张表的写入方原本只有后台（/shop/favorite/**，压 shop:favorite:* 权限、
 * 能在表单里挑消费者），那几条既不在 /api/** 下（前台 dev proxy 和生产 nginx 都只转 /api），
 * 又要求后台登录态，前台够不着，所以前台自己要的那一颗在这另开一份，判据抄后台那一份。
 */
public interface ShopFavoriteWriteService {

    /**
     * 删自己名下的一条收藏（列表每行那颗「删除」）。
     *
     * 库里是逻辑删除：行还在，只是 is_deleted=1，前台没有回收站，对用户就是没了。
     * 别人的 / 已删的 / 没这个 id 一律回同一句「收藏记录不存在」—— 分档等于让人拿 id 试出这一条挂在谁名下
     * （口径同订单详情、收货地址）。
     */
    void delete(Long id);

    /**
     * 收藏 / 取消收藏自己名下的一个对象（详情页右侧那颗按钮，按 类型+对象 点，页面拿不到收藏行的 id）。
     *
     * @param targetType 1商品 2门店 3品牌，其它值直接报错
     * @param targetId   按 targetType 指向对应那张表的主键，那一行不在就报错
     * @return 点完这一颗之后的状态：true 已收藏、false 已取消 —— 按钮画实心还是空心就看这个，不用再回读一遍
     */
    boolean toggle(Integer targetType, Long targetId);
}
