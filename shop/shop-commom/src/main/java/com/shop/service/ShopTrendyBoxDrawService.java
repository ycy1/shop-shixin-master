package com.shop.service;

import com.shop.vo.trendy.ShopTrendyBoxDrawVo;

/**
 * 盲盒抽盒服务（两端共用的那一条动作）
 *
 * 为什么收在 shop-commom 而不是留在 shop-api：这一条和出票是同一个形状 —— 落的是「一个人一次动作」
 * 那一行，将来后台代抽、支付回调成功后自动抽都调的是同一份规矩（选款、扣库存、算钱），
 * 抄两遍早晚会出岔子（口径同 {@link ShopParkTicketIssueService}）。
 *
 * @author xxj
 */
public interface ShopTrendyBoxDrawService {

    /**
     * 抽一趟盒：谁抽的按登录态取，本趟抽几个由盒自己的类型和配置决定，页面不给传次数。
     *
     * 抽中的实物顺手加进那个人的购物车（和详情页那颗「加入购物车」同一份判据与三条写法，
     * 见 {@link ShopCartAddService#tryAdd}）—— 所以购物车和结算那一条链不需要认识盲盒：
     * 抽中的东西落库时就是车里的普通一行，按商品正常单价结算。抽签不发实物，那一种不进车。
     *
     * @param boxId 盲盒ID
     * @return 本趟的结果（抽中的款式快照 + 该付多少钱 + 进车几件 + 盒的剩余情况）
     */
    default ShopTrendyBoxDrawVo draw(Long boxId) {
        return draw(boxId, false);
    }

    /**
     * 同上，多一个「这一趟要一整盒」的口径（页面那颗「购买整盒」）。
     *
     * 整盒 = 现在还有余量的款式各一件（盒类型本身就是端盒的那一档天然如此），按盒上配的端盒价结钱，
     * 进车是**一整盒一行**（不是盒内每款一行，见 {@link ShopCartAddService#tryAddBox}）。
     * 抽签（4）不给整盒买 —— 它本来就不发实物；盒没配端盒价也不给（不然只能退回单抽价 × 件数，
     * 那是拿单抽的价卖整盒，页面上那颗按钮也不会画出来）。单抽、端盒、一番赏这三类都让整盒买。
     * 整盒买和抽一把不一样：它不是"抽"，页面那颗也不该转老虎机（一次买定一整盒）。
     *
     * @param wholeBox 这一趟要不要一整盒
     */
    ShopTrendyBoxDrawVo draw(Long boxId, boolean wholeBox);
}
