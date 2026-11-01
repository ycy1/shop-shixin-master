package com.shop.service;

import com.shop.dto.park.ShopParkTicketBuyDto;
import com.shop.vo.trendy.ShopParkTicketBuyVo;

/**
 * 主题公园购票（用户自己买票那一路，两端共用的那一条动作）
 *
 * 为什么收在 shop-commom 而不是各端各写一份：和 {@link ShopTrendyBoxDrawService} 同一个理由 ——
 * web、小程序、将来后台代买读的必须是同一套规矩（谁能买、哪天能用、约哪个时段、该付多少）。
 *
 * 这一条只做「说清楚要买哪张票、哪天来」的那一步，落库还是走 {@link ShopParkTicketIssueService}：
 * 票码、票面快照、状态钉 0 未使用、票面二维码 —— 全平台只有那一处在写用户票表，
 * 后台代客出票和这里出来的行必须同形，抄两遍早晚会出岔子。
 *
 * 只报钱不扣钱：返回体里的 amount 是这一单该付多少，扣余额/走支付那条口还没接上（口径同抽盒），
 * 接上时在 {@link #buy} 这份事务里加一步就行。
 */
public interface ShopParkTicketBuyService {

    /**
     * 买票：给自己买，几张、哪天、约哪一档；一次买几张的落几行（一张票一行）。
     *
     * @param dto 票种、游玩日期、时段、张数和持票人三格（没有「给谁」这一格，见 DTO 注释）
     * @return 买到那几张票 + 本单应付合计
     */
    ShopParkTicketBuyVo buy(ShopParkTicketBuyDto dto);
}
