package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.ShopParkUserTicket;

import java.util.List;

/**
 * 用户票种 服务类
 *
 * 这张表是"发到某人手上的一张票"的凭证，读的那几格面额信息读本行快照、不回票种表刷（口径同用户优惠券）。
 * 这一期只有后台代客出票这一条写入路（没有下单支付链路），所以新增/修改/删除都开在后台，
 * 状态流转按两颗按钮各一条 SQL 走（核销、退款），不让表单直接改 status；
 * 出票那一步在 shop-commom 的 ShopParkTicketIssueService（票码/快照/票面二维码都在那边）。
 */
public interface ShopParkUserTicketService extends IService<ShopParkUserTicket> {

    /**
     * 分页查询用户票
     *
     * @param query keyword 模糊匹配票码/票种名/持票人姓名电话，userId、parkId、ticketType、status 精确匹配，
     *              beginVisitDate/endVisitDate 卡游玩日期；消费者账号会被强制收窄成本人
     */
    IPage<ShopParkUserTicket> selectPage(ShopParkUserTicket query);

    /**
     * 按 id 查询一张票（消费者账号只能查自己名下的）
     */
    ShopParkUserTicket detail(Long id);

    /**
     * 后台代客出票：票码由服务端生成，园区和票面那几格按 ticketId 从票种行上带成快照，状态钉 0 未使用
     */
    void insert(ShopParkUserTicket userTicket);

    /**
     * 修改：只放开游玩日期、时段、持票人三格和备注；状态为 0 未使用的票才给改
     */
    void update(ShopParkUserTicket userTicket);

    /**
     * 入园核销：0 未使用 → 1 已核销，同时盖核销时间
     */
    void verify(Long id);

    /**
     * 退款：0 未使用 / 1 已核销 / 2 已过期 → 3 已退款，同时盖退款时间（只改状态，这一期没有支付链路可退钱）
     */
    void refund(Long id);

    /**
     * 重出这一张票的票面二维码（票码不变，只把 qr_img 那一格重新生成一次）
     *
     * 出票那一步的出图是"失败只记日志、不回滚"，所以列表上 qr_img 有可能是空 —— 这颗按钮就是给补那一张图用的；
     * 图已经有的时候点它是换一张新的（内容还是同一个票码，扫出来一模一样）。
     *
     * @return 新的票面图 URL
     */
    String refreshQrCode(Long id);

    /**
     * 批量删除（逻辑删除）
     */
    void deleteByIds(List<Long> ids);
}
