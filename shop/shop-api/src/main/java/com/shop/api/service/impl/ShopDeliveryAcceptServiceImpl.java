package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.shop.api.service.ShopDeliveryAcceptService;
import com.shop.dto.delivery.ShopDeliveryAcceptDto;
import com.shop.dto.delivery.ShopDeliveryActionDto;
import com.shop.dto.user.LoginUserInfo;
import com.shop.entity.ShopErpSaleOutbound;
import com.shop.entity.ShopErpSaleOutboundItem;
import com.shop.entity.ShopOrderItem;
import com.shop.entity.ShopDelivery;
import com.shop.entity.ShopDeliveryRider;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopErpSaleOutboundItemMapper;
import com.shop.mapper.ShopErpSaleOutboundMapper;
import com.shop.mapper.ShopOrderItemMapper;
import com.shop.mapper.ShopDeliveryMapper;
import com.shop.mapper.ShopDeliveryRiderMapper;
import com.shop.utils.DeliveryTimeUtils;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.TransactionUtils;
import com.shop.vo.delivery.ShopDeliveryAcceptVo;
import com.shop.vo.delivery.ShopDeliveryActionVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 配送单接单 服务实现（2026-09-22 这一轮又并进来取货 / 送达 / 取消三颗，它们共用同一套归属判据）
 *
 * 为什么写在 shop-api 而不是后台那个配送单 service 里：接单是"两端同一个动作"——
 * 送货员在自己那一端（列表里那些还没人接的单）点一下，和他在后台「配送管理」里点同一颗按钮，
 * 干的必须是同一件事，所以入口放在 ShopRiderCommonController（/api/comm/shop/delivery/accept），
 * 后台前端也调这一条（那颗按钮只对送货员本人账号显示，非送货员账号要看的是「修改」里的派单）。
 * 取货和送达是同一个理由（送货员跑完一趟要点的就是这两颗，后台那一端只是替他记），所以也在这一份实现里。
 * 和统一下单、支付回调是同一个位置、同一个理由：
 * shop-api 只依赖 shop-commom，摸得到这几张表的 mapper，也摸不到后台的 service。
 *
 * 鉴权和下单那两接口同理：/api/** 不过 Sa-Token 拦截器，所以这里自己取登录态，
 * 取不到就报"请先登录后再接单"。**能接谁的单**判的是账号类型（account_type，口径同 /shop/rider/my）：
 * 送货员账号只可能接成自己的单，其它账号是"替某位送货员确认接单"，必须点名是哪一位。
 * 后三步的归属判据不一样，见 {@link #assertRunningBy}：人已经写在单子上了，参数再点名就是后门。
 *
 * 三处写全在同一个事务里，而且全是条件更新（读到的状态没被别人动过才改得动）：
 * 配送单翻成已接单、订单明细行翻成已发货，要么一起成要么一起回滚 ——
 * 只成前一半就是"没人接这单却已经算发出去了"，只成后一半是"货标发了但单还挂在待接单池里"。
 */
@Service
@RequiredArgsConstructor
public class ShopDeliveryAcceptServiceImpl implements ShopDeliveryAcceptService {

    private final ShopDeliveryMapper shopDeliveryMapper;

    private final ShopDeliveryRiderMapper shopDeliveryRiderMapper;

    /** 接单时顺带推的是"这一单绑的那张出库单"名下的订单明细行，所以按出库单找行 */
    private final ShopErpSaleOutboundItemMapper shopErpSaleOutboundItemMapper;

    /** 先按 id + 号两个值确认那一槽装的真是出库单（见 shiftOrderItemShipStatus），配不上就不动明细 */
    private final ShopErpSaleOutboundMapper shopErpSaleOutboundMapper;

    private final ShopOrderItemMapper shopOrderItemMapper;

    /** 配送状态（字典 delivery_status）：0 待派单 1 待接单 —— 这两档都在"还没人接"的池子里 */
    private static final int STATUS_WAIT_ASSIGN = 0;

    private static final int STATUS_WAIT_ACCEPT = 1;

    /** 配送状态：2 已接单 —— 接单成功后进这一档，后面的取货/送达是另外的接口 */
    private static final int STATUS_ACCEPTED = 2;

    /** 配送状态：3 配送中 —— 取货成功之后（货已经在送货员手上、在路上） */
    private static final int STATUS_DELIVERING = 3;

    /** 配送状态：4 已送达 —— 送到收件人手里，这一单到此为止，只能查不能再改 */
    private static final int STATUS_DELIVERED = 4;

    /** 配送状态：5 已取消 —— 人工取消这一路（另一路是订单被取消带下来的，见 ShopOrderDeliveryServiceImpl#cancelByOrder） */
    private static final int STATUS_CANCELLED = 5;

    /** 派单方式（字典 delivery_assign_type）：2 抢单 —— 送货员从没派出去的那一批里自己揽一单 */
    private static final int ASSIGN_TYPE_GRAB = 2;

    /** 送货员启用状态（字典 rider_status）：1 启用 —— 停用的档案不能再接新单（后台那个送货员下拉同一口径） */
    private static final int RIDER_STATUS_ENABLED = 1;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopDeliveryAcceptVo accept(ShopDeliveryAcceptDto form) {
        // 配送单和订单明细行得一起改，事务压根没开就别往下写：只改一半的账对不上
        TransactionUtils.requireActive("接单");
        if (form == null || form.getDeliveryId() == null) {
            throw new ServiceException("请选择要接的配送单");
        }
        ShopDeliveryRider rider = resolveRider(form);
        ShopDelivery delivery = shopDeliveryMapper.selectById(form.getDeliveryId());
        if (delivery == null) {
            throw new ServiceException("配送单不存在");
        }
        assertAcceptable(delivery, rider);

        LocalDateTime now = LocalDateTime.now();
        // 距离用的是建单时按「发货点 → 收件点」算好的那一格，这里不补算：两个点凑不齐的单本来就没有距离，
        // 硬估一个只会更误导人。估不出时长就这两格一起留空（前端按空显示"未定"）
        Integer minutes = DeliveryTimeUtils.estimateMinutes(delivery.getDistance(), rider.getVehicleType());
        // 原来就填过预计送达时间（手工建单那一格）的不覆盖，只在空着的时候按"接单时间 + 预计时长"补一个
        LocalDateTime expect = delivery.getExpectTime() != null ? delivery.getExpectTime()
                : (minutes == null ? null : now.plusMinutes(minutes));
        // 从"没派给任何人"的那一批里被送货员自己揽走的，才叫抢单；替他确认的那一路不动派单方式
        boolean selfGrab = delivery.getRiderId() == null && LoginUserUtils.isRider();

        int updated = shopDeliveryMapper.update(null, new LambdaUpdateWrapper<ShopDelivery>()
                .set(ShopDelivery::getRiderId, rider.getId())
                .set(ShopDelivery::getStatus, STATUS_ACCEPTED)
                .set(ShopDelivery::getAcceptTime, now)
                .set(selfGrab, ShopDelivery::getAssignType, ASSIGN_TYPE_GRAB)
                .set(minutes != null, ShopDelivery::getEstimatedTime, minutes)
                .set(expect != null && delivery.getExpectTime() == null, ShopDelivery::getExpectTime, expect)
                .eq(ShopDelivery::getId, delivery.getId())
                .in(ShopDelivery::getStatus, Arrays.asList(STATUS_WAIT_ASSIGN, STATUS_WAIT_ACCEPT))
                // 归属也进 WHERE：读到"没人接"和写之间要是被别人抢先了，这一句就不成立
                .and(w -> w.isNull(ShopDelivery::getRiderId)
                        .or().eq(ShopDelivery::getRiderId, rider.getId())));
        if (updated == 0) {
            throw new ServiceException("这一单刚刚被人接走了（或状态已经变了），请刷新配送列表");
        }

        int shipped = shiftOrderItemShipStatus(delivery.getOutboundId(), delivery.getOutboundNo(),
                ShopOrderItem.SHIP_STATUS_OUTBOUND, ShopOrderItem.SHIP_STATUS_SHIPPED);

        ShopDeliveryAcceptVo vo = new ShopDeliveryAcceptVo();
        vo.setDeliveryId(delivery.getId());
        vo.setDeliveryNo(delivery.getDeliveryNo());
        vo.setStatus(STATUS_ACCEPTED);
        vo.setRiderId(rider.getId());
        vo.setRiderName(rider.getRiderName());
        vo.setAcceptTime(now);
        vo.setEstimatedTime(minutes);
        vo.setExpectTime(expect);
        vo.setShippedItemCount(shipped);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopDeliveryActionVo pickup(ShopDeliveryActionDto form) {
        // 只翻这一单自己的两格：明细那几行在接单那一刻就已经是「已发货」了（货交到人手上就算发出去），
        // 取货这个动作标的只是"已经在路上"，不该再动订单
        return advance(form, STATUS_ACCEPTED, STATUS_DELIVERING, "取货", "已接单",
                ShopDelivery::getPickupTime);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopDeliveryActionVo delivered(ShopDeliveryActionDto form) {
        // 不回写 sys_order 的收货状态：那一路是买家"确认收货"（或超时自动收货）推的，
        // 送货员点送达只说明他送到了，钱和订单终局不归这里抢
        return advance(form, STATUS_DELIVERING, STATUS_DELIVERED, "送达", "配送中",
                ShopDelivery::getDeliveredTime);
    }

    /**
     * 取货和送达是同一条流水线上相邻的两格，校验和写法一模一样，只差"从哪一档来、往哪一档去、
     * 时间记在哪一列"，所以收成这一段（cancel 不同：它能从四档进来、还要退明细行，单开一个方法）。
     *
     * 状态机严格一格一跳，不跨档也不倒退：没取货就点送达会被拒 —— 那是那颗按钮点错了，
     * 报错比默默替他补一档更值钱（补了档，pickup_time 那一格就成了编出来的数）。
     *
     * @param fromLabel 挡住的时候要说清"这一单得先是哪一档"，字典 delivery_status 的中文名
     */
    private ShopDeliveryActionVo advance(ShopDeliveryActionDto form, int from, int to, String action,
                                         String fromLabel, SFunction<ShopDelivery, LocalDateTime> timeColumn) {
        ShopDelivery delivery = loadForAction(form, action);
        ShopDeliveryRider rider = assertRunningBy(delivery, action, true);
        Integer status = delivery.getStatus();
        if (status == null || status != from) {
            throw new ServiceException("只有「" + fromLabel + "」的配送单能" + action + "，这一单不是这一档（可能已经"
                    + action + "过了，或者还没走到这一步）");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = shopDeliveryMapper.update(null, new LambdaUpdateWrapper<ShopDelivery>()
                .set(ShopDelivery::getStatus, to)
                .set(timeColumn, now)
                .eq(ShopDelivery::getId, delivery.getId())
                .eq(ShopDelivery::getStatus, from)
                // 归属也进 WHERE：读到这一下之间要是后台在「配送管理」里换了人，这一句就不成立，
                // 让换过人之后那次点击失败，别把时间记到一个已经不跑这一单的人头上
                .eq(ShopDelivery::getRiderId, rider.getId()));
        if (updated == 0) {
            throw new ServiceException("这一单的状态刚刚被人改过（或换了送货员），请刷新配送列表");
        }
        return actionVo(delivery, rider, to, now, null, null);
    }

    /**
     * 人工取消：0 待派单 / 1 待接单 / 2 已接单 / 3 配送中 都能取消，4 已送达 和 5 已取消 不行。
     *
     * 已经送到的那一档要退走退货（见 /api/comm/shop/order/refund 那一路），把配送单改成"取消"
     * 只会让货在两张单上互相打脸；再取消一次也只是重复点击。
     *
     * 取消要顺带把这一单名下「1 已发货」的订单明细行退回「3 已出库」—— 接单那一步的反操作：
     * 货没走成、还在送货员手上或者已经退回仓库，但确实离开了货架，所以退到 3 而不是 0 待发货
     * （0 那一档是"没出满的部分"，这里出过满，库存也已经扣过了，回退库存是出库单那一路的活，不在这里）。
     *
     * 和订单被取消带下来的那一路（ShopOrderDeliveryServiceImpl#cancelByOrder）刻意两处不同：
     * ① 那一路只收 0/1（订单还没发货，路上的单不归它管），这一路连 2/3 一起收；
     * ② 那一路会把 assign_type / assign_time 清成 NULL（单子压根没发出去，留着会让人以为派给过谁），
     *    这一路**什么都不清** —— rider_id、那两格时间、accept_time 全留着。这一单本来确实派过人、
     *    甚至货已经在路上，取消了也要看得见"谁接的、什么时候接的"，事后追查和结算都要用那几个数。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopDeliveryActionVo cancel(ShopDeliveryActionDto form) {
        // 配送单和订单明细行得一起改，事务压根没开就别往下写：只改一半就是"单取消了、货还算发出去了"
        TransactionUtils.requireActive("取消");
        ShopDelivery delivery = loadForAction(form, "取消");
        List<Integer> cancellable = Arrays.asList(STATUS_WAIT_ASSIGN, STATUS_WAIT_ACCEPT,
                STATUS_ACCEPTED, STATUS_DELIVERING);
        Integer status = delivery.getStatus();
        if (status == null || !cancellable.contains(status)) {
            throw new ServiceException(status != null && status == STATUS_DELIVERED
                    ? "这一单已经送达，要退货物请到订单那边发起退货，配送单取消不了"
                    : "这一单已经取消过了");
        }
        // needRider=false：还没派出去的那两条（0/1）也该能取消，那种单本来就没有 rider_id
        ShopDeliveryRider rider = assertRunningBy(delivery, "取消", false);
        LocalDateTime now = LocalDateTime.now();
        String reason = StringUtils.defaultIfBlank(form.getCancelReason(), "配送单人工取消");

        LambdaUpdateWrapper<ShopDelivery> update = new LambdaUpdateWrapper<ShopDelivery>()
                .set(ShopDelivery::getStatus, STATUS_CANCELLED)
                .set(ShopDelivery::getCancelTime, now)
                .set(ShopDelivery::getCancelReason, reason)
                .eq(ShopDelivery::getId, delivery.getId())
                .in(ShopDelivery::getStatus, cancellable);
        if (rider != null) {
            update.eq(ShopDelivery::getRiderId, rider.getId());
        }
        if (shopDeliveryMapper.update(null, update) == 0) {
            throw new ServiceException("这一单的状态刚刚被人改过（或换了送货员），请刷新配送列表");
        }

        Integer reverted = shiftOrderItemShipStatus(delivery.getOutboundId(), delivery.getOutboundNo(),
                ShopOrderItem.SHIP_STATUS_SHIPPED, ShopOrderItem.SHIP_STATUS_OUTBOUND);
        return actionVo(delivery, rider, STATUS_CANCELLED, now, reason, reverted);
    }

    /** 三颗接口共用的那一句：参数点的这一单存在吗（存在才谈得上是谁的、走到哪一步了） */
    private ShopDelivery loadForAction(ShopDeliveryActionDto form, String action) {
        if (form == null || form.getDeliveryId() == null) {
            throw new ServiceException("请选择要" + action + "的配送单");
        }
        ShopDelivery delivery = shopDeliveryMapper.selectById(form.getDeliveryId());
        if (delivery == null) {
            throw new ServiceException("配送单不存在");
        }
        return delivery;
    }

    /**
     * 后三步"由谁来做"不由参数决定：这一单派给谁就是谁在跑，所以入参里根本没有 riderId
     * （接单那一步要收，是因为那会儿单上还没有人、必须点名由谁接；这里再收一次就是后门）。
     * 送货员账号（account_type=2）只能动 rider_id 是自己那一份档案的单 —— 别人那些在他列表里
     * 本来就被数据范围挡掉了，这一句挡的是直接调接口的那一路。
     * 后台账号是替单上那位送货员记这一步，不动 rider_id。
     *
     * 这里**不查**送货员的启用状态（口径和 {@link #assertRiderUsable} 不同）：停用只是不再往里派新活，
     * 手上那一单还在路上，取货和送达必须收得尾，否则那张单会永远卡在 3 配送中。
     *
     * @param needRider 取货/送达必须已经有人接才谈得上；取消不是，所以那一路传 false
     * @return 单上那一位送货员的档案；这一单还没派人且 needRider=false 时返回 null
     */
    private ShopDeliveryRider assertRunningBy(ShopDelivery delivery, String action, boolean needRider) {
        LoginUserInfo login = LoginUserUtils.requireLoginUser("后再操作配送单");
        if (delivery.getRiderId() == null) {
            if (needRider) {
                throw new ServiceException("这一单还没人接单，先接单才能" + action);
            }
            if (LoginUserUtils.isRider()) {
                // 抢单池里还没人接的单：送货员账号没有"替别人取消"这一说，那是后台「配送管理」的活
                throw new ServiceException("这一单还没派给任何人，要取消请到「配送管理」里操作");
            }
            return null;
        }
        ShopDeliveryRider rider = shopDeliveryRiderMapper.selectById(delivery.getRiderId());
        if (rider == null) {
            throw new ServiceException("这一单挂着的送货员档案已经不存在了，请到「配送管理」里核实后再" + action);
        }
        if (LoginUserUtils.isRider() && !rider.getId().equals(myRider(login.getId()).getId())) {
            throw new ServiceException("这一单不在你的名下，不能" + action);
        }
        return rider;
    }

    /**
     * 送货员账号自己那一份档案：按 user_id=登录 id 命中，历史脏数据重复时取第一条
     * （口径同 /shop/rider/my 和 {@link #resolveRider} 里那一段）。
     * 和那边的区别是不做启用校验 —— 原因见 {@link #assertRunningBy}。
     */
    private ShopDeliveryRider myRider(Long loginId) {
        List<ShopDeliveryRider> mine = shopDeliveryRiderMapper.selectList(new LambdaQueryWrapper<ShopDeliveryRider>()
                .eq(ShopDeliveryRider::getUserId, loginId));
        if (mine.isEmpty()) {
            throw new ServiceException("当前账号还没有绑定送货员档案，请到「送货员管理」里把账号绑上");
        }
        return mine.get(0);
    }

    /** 三颗接口共用的返回：这一单现在到第几步了、是谁的、以及这一步刚写进去的那一格时间 */
    private ShopDeliveryActionVo actionVo(ShopDelivery delivery, ShopDeliveryRider rider, int status,
                                          LocalDateTime actionTime, String cancelReason, Integer reverted) {
        ShopDeliveryActionVo vo = new ShopDeliveryActionVo();
        vo.setDeliveryId(delivery.getId());
        vo.setDeliveryNo(delivery.getDeliveryNo());
        vo.setStatus(status);
        vo.setRiderId(rider == null ? delivery.getRiderId() : rider.getId());
        vo.setRiderName(rider == null ? null : rider.getRiderName());
        vo.setActionTime(actionTime);
        vo.setCancelReason(cancelReason);
        vo.setRevertedItemCount(reverted);
        return vo;
    }

    /**
     * 定位"由哪位送货员接这一单"：
     * 送货员账号（account_type=2）一律是他自己那一份档案，按 user_id=登录 id 命中，
     * 历史脏数据重复时取第一条（口径同 /shop/rider/my 和送货员那份配送列表的 myRider），
     * 参数里传别人的 riderId 也不认 —— 这个接口唯一能把某一单挂到人名下的方式就是"本人来接"或"后台点名"。
     * 其它账号那一档必须点名，不点就拒：省得在后台误点一下把单塞给一个不知道是谁的人。
     */
    private ShopDeliveryRider resolveRider(ShopDeliveryAcceptDto form) {
        LoginUserInfo login = LoginUserUtils.requireLoginUser("再接单");
        if (LoginUserUtils.isRider()) {
            return assertRiderUsable(myRider(login.getId()));
        }
        if (form.getRiderId() == null) {
            throw new ServiceException("请指定接单的送货员（送货员本人接单请用他自己的账号）");
        }
        ShopDeliveryRider rider = shopDeliveryRiderMapper.selectById(form.getRiderId());
        if (rider == null) {
            throw new ServiceException("所选送货员不存在");
        }
        return assertRiderUsable(rider);
    }

    /** 停用的送货员不该再接新单（档案还在、还能看历史单，只是不再往里派活） */
    private ShopDeliveryRider assertRiderUsable(ShopDeliveryRider rider) {
        if (rider.getStatus() != null && rider.getStatus() != RIDER_STATUS_ENABLED) {
            throw new ServiceException("送货员「" + rider.getRiderName() + "」已停用，不能接单");
        }
        return rider;
    }

    /**
     * 接单前的两道闸，全在服务层判（前端那颗按钮的显示条件是同一口径，但按钮挡不住直接调接口的人）：
     * ① 只有 0 待派单 / 1 待接单 能接，2 已接单及以后是重复点击；
     * ② 已经派给过别人的单不由这个接口翻案 —— 换人是「配送管理」里修改那一格的活，
     *    在这儿悄悄改走会绕过那边的校验和日志。
     * 同一句判据在下面 UPDATE 的 WHERE 里又写了一遍：这里先报错是为了让人看得懂是哪一条，
     * 那里带条件才是真的挡并发。
     */
    private void assertAcceptable(ShopDelivery delivery, ShopDeliveryRider rider) {
        Integer status = delivery.getStatus();
        if (status == null || (status != STATUS_WAIT_ASSIGN && status != STATUS_WAIT_ACCEPT)) {
            throw new ServiceException("只有「待派单 / 待接单」的配送单能接单，这一单已经走到下一步了");
        }
        if (delivery.getRiderId() != null && !delivery.getRiderId().equals(rider.getId())) {
            throw new ServiceException("这一单已经派给了别的送货员，要换人请到「配送管理」里修改那一条");
        }
    }

    /**
     * 把这一单名下的订单明细行从 from 那一档推到 to 那一档，返回**这次真的改了几行**。
     * 接单用它推 3 已出库 → 1 已发货（{@link #accept}），取消用它反着推回去（{@link #cancel}），
     * 所以两个方向是同一句 UPDATE 加两个参数，不再抄一份。
     *
     * 为什么接单那一步要推：出库动作只把出满的那几行标到 3 已出库（货离开了货架），
     * 而"这批货交到人手上、开始往买家那儿送"是接单这一刻才发生的事；
     * 下单建出来的那条配送单只是张待派单的单据，还没人拿走货，那时候标"已发货"太早。
     *
     * 传进来的是配送单那一槽的两个值，**必须两个一起配**才知道它是不是真出库单：还没换绑的那一条里装的是
     * 订单 id + 订单号，只按 id 去查出库明细就会错拿另一张同号出库单的行来标（口径同 findByBinding）。
     * 配不上就当这次没有可推的行，返回 0 —— 单子照旧操作成功，别让钱的账卡住派送。
     *
     * WHERE 带上原状态（from）：还停在 0 待发货 的行是这一单里没出满的部分，不该算发出去；
     * 已经是 2 已退货 / 4 部分退货 的行也不往回拽（同一张订单行可能被几张出库单分批出，
     * 早出满的那几行先标，后出满的下次接单再标）。读到写之间被别的操作改过就以那边为准，
     * 返回值才是"这次真的改了几行"。
     */
    private int shiftOrderItemShipStatus(Long boundId, String boundNo, int from, int to) {
        if (boundId == null || StringUtils.isBlank(boundNo)) {
            return 0;
        }
        ShopErpSaleOutbound outbound = shopErpSaleOutboundMapper.selectOne(
                new LambdaQueryWrapper<ShopErpSaleOutbound>()
                        .eq(ShopErpSaleOutbound::getId, boundId)
                        .eq(ShopErpSaleOutbound::getOutboundNo, boundNo)
                        .last("LIMIT 1"));
        if (outbound == null) {
            return 0;
        }
        // isNotNull 必须留在 SQL 里，不能靠取值前那道判空：这里只 SELECT 了 order_item_id 一列，
        // 而 MyBatis 对「整行全是 NULL」的结果行默认不建对象（returnInstanceForEmptyRow=false）、
        // 直接往 List 里塞一个 null ⇒ 来源 3 采购退货 那一路（order_item_id 整列为 NULL）会让下面取值得不到对象就 NPE。
        // 挡在 SQL 上捞回来的行更少，语义也一样：这一列本来为 NULL 的行就不算订单明细。
        List<ShopErpSaleOutboundItem> lines = shopErpSaleOutboundItemMapper.selectList(
                new LambdaQueryWrapper<ShopErpSaleOutboundItem>()
                        .select(ShopErpSaleOutboundItem::getOrderItemId)
                        .eq(ShopErpSaleOutboundItem::getOutboundId, outbound.getId())
                        .isNotNull(ShopErpSaleOutboundItem::getOrderItemId));
        Set<Long> orderItemIds = new HashSet<>();
        for (ShopErpSaleOutboundItem line : lines) {
            orderItemIds.add(line.getOrderItemId());
        }
        if (orderItemIds.isEmpty()) {
            return 0;
        }
        return shopOrderItemMapper.update(null, new LambdaUpdateWrapper<ShopOrderItem>()
                .set(ShopOrderItem::getShipStatus, to)
                .in(ShopOrderItem::getId, orderItemIds)
                .eq(ShopOrderItem::getShipStatus, from));
    }
}
