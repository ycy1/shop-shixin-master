package com.shop.api.service;

import com.shop.dto.cart.ShopCartSaveDto;

import java.util.List;

/**
 * 购物车 前台写入接口（商城前台 /api/web/shop/cart/**：详情页那颗「加入购物车」、
 * 购物车那一页上的数量步进 / 勾选 / 全选 / 删除）
 *
 * 四条写都没有可点名的 userId：服务层第一句 requireLoginUser，能改的只有登录者自己那几条
 *（判据抄后台 ShopConsumerCartServiceImpl，差别是把「只有消费者账号才逐条比 user_id」改成无条件比 ——
 * 前台这一路根本没有后台身份）。
 *
 * 四条都不挂 @Transactional：每条最多一句写，没有要一起成才算数的第二句
 *（口径同收藏那一份；真正要一个事务的叠库存/锁仓在下单那一路，不在这）。
 */
public interface ShopCartWriteService {

    /**
     * 加入购物车：在 (登录者, 这个规格, 来源=普通商品/无来源) 这一组坐标上叠数量，车上没有这一条就建一条。
     *
     * 三条路按「先叠、再复活、最后插」的顺序走，全程只可能写一条 ——
     * uk_user_sku_source(user_id, sku_id, source_type, source_id) 不含 is_deleted，删掉的那一行还占着这个键，
     * 直接插「删掉后又加回同一个规格」必撞键，所以要把那一行复活而不是建第二条。
     * 只认 form 的 skuId + quantity 两格（这是「往自己名下加」的一颗，userId 和 id 都不认），
     * 分类/品牌/门店三格由服务层按这条规格所属的商品现带（快照，口径同后台那一份 validate）。
     * 数量不做库存上限：可用库存只在下单那一刻才真的拦（口径同后台那份 update 不过库存闸）。
     */
    void add(ShopCartSaveDto form);

    /**
     * 改自己名下的某一行：只认 quantity 和 selected 两格，都是局部更新（不传那格就不动）。
     *
     * 这一颗不认 skuId：一行买的是哪个规格是这一行的身份，改规格等于换一行，
     * 页面上的画法就是「删掉这条 + 重新加一条」，不在这条接口上开挪动的口子
     *（后台那一份能改 skuId，那是代客操作）。
     */
    void update(ShopCartSaveDto form);

    /**
     * 全选 / 取消全选：按人一次把名下没删的行刷成同一个 selected，只刷还能买的那几行
     *（商品下架、规格停售的那些页面上勾是禁用着的，不动它们）。
     *
     * 一次一句 UPDATE，不像前端逐行 PUT 一遍那样点一次发 N 颗请求。
     */
    void selectAll(Integer selected);

    /**
     * 删自己名下的几条（一行那颗「删除」传一个 id，批量删除传一串）。
     *
     * 库里是逻辑删除：行还在，只是 is_deleted=1，前台没有回收站，对用户就是没了；
     * 这一行占着的 uk_user_sku_source 由上面 add 的「复活」那条路接手，所以删了还能再加回来。
     * 别人的 / 已删的 / 没这个 id —— 只要有一条不全是自己的就整批不删，回同一句「购物车记录不存在」，
     * 不做部分成功（分档等于让人拿 id 试出哪一条挂在谁名下，口径同订单详情、收藏）。
     */
    void delete(List<Long> ids);
}
