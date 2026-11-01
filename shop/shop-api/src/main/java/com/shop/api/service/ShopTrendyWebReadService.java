package com.shop.api.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.vo.trendy.ShopParkPhotoWebVo;
import com.shop.vo.trendy.ShopTrendyBoxWebVo;
import com.shop.vo.trendy.ShopTrendyCollectItemVo;
import com.shop.vo.trendy.ShopTrendyCollectionViewVo;
import com.shop.vo.trendy.ShopTrendyParkWebVo;

import java.util.List;

/**
 * @author xxj
 * @title ShopTrendyWebReadService
 * @description 潮玩专区（web 前台）的三趟读：盲盒列表、园区列表、园区详情。
 * 可见性判据都收在这一层（哪些状态能出现在前台），页面只管传分页和筛选。
 */
public interface ShopTrendyWebReadService {

    /**
     * 盲盒列表：只给「1 未开始 / 2 进行中」两档，已结束和已取消的盒前台不该再点得到。
     *
     * @param status 再收窄一档（1 或 2）；传别的值等于不收窄
     * @param hot    传 1 只看后台勾了「热门」的（首页那两台机台那一趟）；别的值等于不收窄
     */
    IPage<ShopTrendyBoxWebVo> pageBoxes(Integer status, Integer hot);

    /**
     * 一台盒的详情：卡片上只带前若干款（列表那一趟按整页收着），这一条把名下在架款式全给出来。
     * 已结束、已取消或根本没这个 id 都回同一句话 —— 不从错误信息里试哪台盒存在过。
     */
    ShopTrendyBoxWebVo boxDetail(Long id);

    /**
     * 园区列表：只给营业中的，热门的排前面；卡片上那格「N 个票种 / ¥X 起」是现算的。
     *
     * @param hot 传 1 只看勾了「热门」的（首页轮播那一趟）；别的值等于不收窄
     */
    List<ShopTrendyParkWebVo> listParks(Integer hot);

    /**
     * 园区详情：园区 + 名下在架票种 + 顶上那张照片卡片里的几张照片（只给前 6 张，其余走分页那条）。
     * 停业、下架、逻辑删除或根本没这个 id 都回同一句话，不从错误信息里试哪个园区存在过。
     */
    ShopTrendyParkWebVo parkDetail(Long id);

    /**
     * 一家园区名下的全部照片（分页，20 张一页那种）：只给审核通过的，精选在前。
     * 判据和详情那一屏是同一条，两页翻出来的先后次序也对得上。
     *
     * @param id 园区ID；停业/不存在的园区回同一句话（详情页进得来，这一页就进得来）
     */
    IPage<ShopParkPhotoWebVo> pageParkPhotos(Long id);

    /**
     * 图鉴（/trendy/collection）：一行一条潮玩系列，带「这套一共几格 / 我凑到几格 / 集齐给什么 /
     * 那一笔领过没有」，10 条一页，我抽过的系列整排在前。
     *
     * 不登录也回：进度那一格念成「0 / N」、领取那格空着 —— 图鉴本来就是给人逛的。
     * 登录后才有的那两格（凑到的格数、领取记录）由登录态现取，参数不给（能点名别人就等于看别人的图鉴）。
     */
    IPage<ShopTrendyCollectionViewVo> pageCollection();

    /**
     * 图鉴卡片底下那颗「看收集明细」：这一条系列名下的<b>全部</b>规格，一格标出我抽到过没有
     *（抽到过的那格带第一次凑上的日期，NULL 就是还缺它）。
     *
     * 一行一个规格、不是"我抽过的那些记录" —— 差哪几格只有从整套这一侧才看得出来，
     * 反过来列我抽过的，页面还得自己数「3 / 5」里的分母是哪来的（口径同后台集齐进度点开一个人的那一排）。
     * 不分页：一条系列名下撑死几十格，一次全带。
     *
     * 不登录也回：那时每一格都是"还缺它"（认人只认登录态，参数里没有 userId）。
     */
    List<ShopTrendyCollectItemVo> listCollectionItems(Long trendyId);
}
