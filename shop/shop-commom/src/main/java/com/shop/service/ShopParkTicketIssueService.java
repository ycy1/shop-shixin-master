package com.shop.service;

import com.shop.dto.park.ShopParkTicketIssueDto;
import com.shop.entity.ShopParkUserTicket;

import java.util.List;

/**
 * 主题公园出票（生成用户票种数据）—— 一张票发到某人手上一行，全平台只有这一处在写这张表
 *
 * 为什么单独收在 shop-commom 而不是各端各写一份：出票这一步将来有两个入口 ——
 * 后台「用户票种」的代客出票，和统一下单支付成功那一刻按件数出票。这两头落的必须是同一形状的行
 *（票码规则、票面快照从哪来、状态钉在哪一档、二维码要不要生成），抄两遍早晚会出岔子，
 * 口径同 ShopOrderCouponService 那份"核销/退券只有一处"。
 *
 * 一张（或一批）票落下来的东西：
 * 1. 本表一行：票码 TK_+时间戳、park_id / ticket_name / ticket_type / price 四格按票种行定格的快照、状态钉 0 未使用；
 * 2. 票面二维码 qr_img：内容就是这一行的票码（闸机/后台扫到这一串就能定位到这张票），
 *    生成+上传失败只记日志、不回滚出票 —— 图没了还能按票码手输核销，钱和票不能没。
 */
public interface ShopParkTicketIssueService {

    /**
     * 出票
     *
     * @param dto 给谁、哪张票种、哪天来玩、约哪个时段、几张（见 DTO 注释：票面那几格不在调用方的话事范围里）
     * @return 落下来的那几张票（含 id 和票码；quantity 传几就回来几条，顺序和出票顺序一致）
     */
    List<ShopParkUserTicket> issue(ShopParkTicketIssueDto dto);

    /**
     * 重出这一张票的票面二维码：还是按这一行现存的票码生成（<b>不换票码</b>，换了手里那张已印出来的码就对不上了），
     * 生成 + 上传 + 覆盖 qr_img 一整趟重走，把新的 URL 回给调用方。
     *
     * 出票那一步的出图是"失败只记日志、不回滚"（一张卖了钱的票不能因为文件存储抖一下就没了），
     * 这一条相反：它是人在按钮上点的，成了给 URL、败了直接报错，别让人点了没反应。
     * 典型用法就是出票时那一趟没成（列表上 qr_img 是 '-'）之后手动补一张。
     *
     * @param id 用户票ID（shop_trendy_park_user_ticket.id）
     * @return 新的票面图 URL
     */
    String regenerateQrCode(Long id);
}
