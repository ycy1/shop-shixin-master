package com.shop.api.service;

import com.shop.dto.delivery.ShopDeliveryAcceptDto;
import com.shop.dto.delivery.ShopDeliveryActionDto;
import com.shop.vo.delivery.ShopDeliveryAcceptVo;
import com.shop.vo.delivery.ShopDeliveryActionVo;

/**
 * 配送单接单 服务接口（统一入口：送货员端和后台都调这一个，2026-09-21 他要"ShopCommonController 中编写统一接单接口"）
 *
 * 接单之后的取货 / 送达 / 取消三颗也走这一份接口（2026-09-22 那一轮搬进来）：三颗都是"两端同一个动作"，
 * 送货员在自己那一端点，和后台「配送管理」里点同一颗按钮，干的必须是同一件事。
 */
public interface ShopDeliveryAcceptService {

    /**
     * 把一张配送单从「0 待派单 / 1 待接单」推到「2 已接单」，顺带做三件事：
     * ① rider_id 落到接单人头上、accept_time 记当前时间；
     * ② 预计配送时长按**这位送货员的交通工具**折算（Distance 已有值才估得出，口径见 DeliveryTimeUtils），
     *    并推出预计送达时间 —— 统一下单建配送单那一路故意不算这两格，就是因为那时还不知道会是谁送；
     * ③ 这一单绑的那张出库单里、已经出满（ship_status=3 已出库）的那几行订单明细标成 1 已发货 ——
     *    货交到送货员手上了，还没出满的行不动。
     *
     * 幂等/并发：三处写全是条件更新（"还是待接单、而且没派给别人"才改得动），
     * 两个人同时点同一单只有一个成功，另一个拿到的是"这一单刚刚被人接走了"。
     *
     * @param form 接单参数（deliveryId 必填；后台账号还要给 riderId，送货员账号按自己的档案走）
     * @return 接单后的关键几格（状态、接单人、那两个时间、被标成已发货的行数）
     */
    ShopDeliveryAcceptVo accept(ShopDeliveryAcceptDto form);

    /**
     * 取货：「2 已接单」推到「3 配送中」，记 pickup_time。
     *
     * 只改这一单自己的两格，不动订单明细 —— 明细那几行在接单时就已经是 1 已发货 了（货交到送货员手上那一刻
     * 就算发出去，见 {@link #accept}），取货这个动作只是把"在路上"这件事标出来。
     * 送货员账号只能动派给自己那一条（rider_id 判据见实现里的 assertRunningBy）。
     */
    ShopDeliveryActionVo pickup(ShopDeliveryActionDto form);

    /**
     * 送达：「3 配送中」推到「4 已送达」，记 delivered_time。
     *
     * 必须从 3 过来：没取货就直接点送达，说明那一颗按钮点错了，让它报错比默默补一档更值钱。
     * 这一颗只写配送单，不回写订单主单状态（sys_order 的收货状态由买家确认收货那条路推，两边别互相抢）。
     */
    ShopDeliveryActionVo delivered(ShopDeliveryActionDto form);

    /**
     * 人工取消：「0 待派单 / 1 待接单 / 2 已接单 / 3 配送中」推到「5 已取消」，记 cancel_time + cancel_reason，
     * 并把这一单名下已经是 1 已发货 的订单明细行退回 3 已出库（接单那一步的反操作：货没走成，还留在货架上）。
     *
     * 4 已送达 和 5 已取消 不能取消 —— 已经送到的要走退货那一路，不是取消这一单。
     *
     * 和支付/取消订单带下来的那一路（ShopOrderDeliveryService#cancelByOrder）刻意不同：
     * 那一路只收 0/1，而且会把 assign_type / assign_time 清成 NULL（单子压根没发出去，留着会让人以为派给过谁）；
     * 这一路是人在按钮前点"这一单不送了"，2/3 那两条本来就确实派过人、甚至货已经在路上，
     * rider_id 和那两格时间一律留着不改 —— 取消掉的单也要看得见"是谁接的、什么时候接的"，
     * 事后追查和结算都要用。取消原因由点按钮的人填，不填服务端兜底一句。
     */
    ShopDeliveryActionVo cancel(ShopDeliveryActionDto form);
}

