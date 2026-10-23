package com.shop.api.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.dto.product.ShopProductWebQueryDto;
import com.shop.vo.product.ShopProductWebVo;

/**
 * 商品读 服务接口（Web 前台浏览商品列表 / 商品详情）
 *
 * 为什么写在 shop-api：和下单、接单、报名那几路一样是前台链路，走 Web 那一包
 * （ShopProductWebController，GET /api/web/shop/product/list 与 /{id}）。
 * shop-api 只依赖 shop-commom，摸得到 shop_product 的 mapper，也摸不到后台那份 ShopProductService。
 *
 * 不复用后台的 ShopProductService.selectPage：那边第一句就按账号类型（=门店）把数据范围收窄、
 * 返回的是带着成本价和预警参数的 ShopProductVo，两样都不属于前台。这一条只做一件事：
 * 把「已经上架」的那部分商品原样给出去。
 */
public interface ShopProductReadService {

    /**
     * 分页浏览商品。登录态不参与：游客和消费者看到的是同一份货，也不按门店收窄
     * （哪家店的货能在前台出现，由后台那侧的上架那一档决定，不由看的人决定）。
     *
     * @param query 关键字与分类，都可为空
     */
    IPage<ShopProductWebVo> page(ShopProductWebQueryDto query);

    /**
     * 单个商品详情。查不到（不存在、已删除、已下架）一律报同一句话，
     * 不给调用方用错误信息试探某个 id 到底存不存在。
     */
    ShopProductWebVo detail(Long id);
}
