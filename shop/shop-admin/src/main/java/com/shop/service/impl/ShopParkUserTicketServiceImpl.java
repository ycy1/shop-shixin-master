package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.dto.park.ShopParkTicketIssueDto;
import com.shop.entity.ShopParkUserTicket;
import com.shop.entity.ShopTrendyParkTicket;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopParkUserTicketMapper;
import com.shop.mapper.ShopTrendyParkTicketMapper;
import com.shop.service.ShopParkTicketIssueService;
import com.shop.service.ShopParkUserTicketService;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import com.shop.utils.ParkTicketSlotUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户票种的读 + 状态流转。
 *
 * <b>出票不在这里</b>：那一处收在 shop-commom 的 {@link ShopParkTicketIssueService} —— 票码、票面快照、
 * 状态钉 0、票面二维码都在那边，因为统一下单支付成功那一路将来要落同一形状的一行，抄两份早晚会走岔。
 * 这里的 insert 只把表单那几格递过去，再把落下来的第一行回灌给 controller 当返回值。
 *
 * status 不给表单直接改：0→1 只走「入园核销」，0/1/2→3 只走「退款」，两条都是自定义 UPDATE，
 * 前态压在 WHERE 里当并发那道闸（形状同用户券的核销/反核销）；
 * 2 已过期由 expireTask 那条定时任务落（口径同用户券的过期扫描），这里只读不写。
 */
@Service
@RequiredArgsConstructor
public class ShopParkUserTicketServiceImpl extends ServiceImpl<ShopParkUserTicketMapper, ShopParkUserTicket>
        implements ShopParkUserTicketService {

    /** 状态四档（字典 user_ticket_status） */
    private static final int STATUS_UNUSED = 0;
    private static final int STATUS_USED = 1;
    private static final int STATUS_EXPIRED = 2;
    private static final int STATUS_REFUNDED = 3;

    /** 只用来看这张票的票种卖哪几档时段（改时段时要对一下） */
    private final ShopTrendyParkTicketMapper ticketMapper;

    /** 出票那一条唯一落库处（在 shop-commom） */
    private final ShopParkTicketIssueService shopParkTicketIssueService;

    @Override
    public IPage<ShopParkUserTicket> selectPage(ShopParkUserTicket query) {
        if (query == null) {
            query = new ShopParkUserTicket();
        }
        // 消费者账号只看自己名下的票（口径同 ShopOrderItemServiceImpl 的列表）：
        // 一律覆盖 userId，前端传什么都不认 —— 「消费者管理 → 我的订单」那个票种 tab 靠这一道
        if (LoginUserUtils.isConsumer()) {
            query.setUserId(StpUtil.getLoginIdAsLong());
        }
        return baseMapper.selectUserTicketPage(PageUtil.getPage(), query);
    }

    @Override
    public ShopParkUserTicket detail(Long id) {
        ShopParkUserTicket ticket = getById(id);
        if (ticket == null) {
            throw new ServiceException("这张票不存在");
        }
        assertConsumerOwns(ticket.getUserId());
        return ticket;
    }

    @Override
    public void insert(ShopParkUserTicket userTicket) {
        ShopParkTicketIssueDto dto = new ShopParkTicketIssueDto();
        dto.setUserId(userTicket.getUserId());
        dto.setTicketId(userTicket.getTicketId());
        dto.setVisitDate(userTicket.getVisitDate());
        dto.setTimeSlot(userTicket.getTimeSlot());
        dto.setQuantity(userTicket.getQuantity());
        dto.setHolderName(userTicket.getHolderName());
        dto.setHolderPhone(userTicket.getHolderPhone());
        dto.setHolderIdCard(userTicket.getHolderIdCard());
        dto.setRemark(userTicket.getRemark());
        List<ShopParkUserTicket> issued = shopParkTicketIssueService.issue(dto);
        // 一次出几张就落几行，这里把第一行回灌进入参（add 要回一个 id；其余几行列表重查自然看得见）
        ShopParkUserTicket first = issued.get(0);
        userTicket.setId(first.getId());
        userTicket.setTicketCode(first.getTicketCode());
        userTicket.setParkId(first.getParkId());
        userTicket.setTicketName(first.getTicketName());
        userTicket.setTicketType(first.getTicketType());
        userTicket.setPrice(first.getPrice());
        userTicket.setQrImg(first.getQrImg());
        userTicket.setStatus(first.getStatus());
    }

    @Override
    public void update(ShopParkUserTicket userTicket) {
        if (userTicket.getId() == null) {
            throw new ServiceException("票ID不能为空");
        }
        ShopParkUserTicket exists = getById(userTicket.getId());
        if (exists == null) {
            throw new ServiceException("这张票不存在");
        }
        assertConsumerOwns(exists.getUserId());
        // 只还没用过的票改得动：核销过的票改了游玩日期/持票人就把那次入园的记录对不上了，
        // 过期或退过的更是已经结掉的一票 —— 要改就删了重出
        if (exists.getStatus() != null && exists.getStatus() != STATUS_UNUSED) {
            throw new ServiceException("这张票" + statusLabel(exists.getStatus()) + "，不能再修改；需要改动请先删掉再重新出票");
        }
        if (userTicket.getVisitDate() == null) {
            throw new ServiceException("请选择游玩日期");
        }
        // 换时段只能换到这个票种卖的那几档里（卖上午/下午的票种不能把这张票改成夜场）。
        // 档位在不在四档之内单独判一道：票种行后来被人删了就没有候选可对，不能放着一个 7 这种值落进库
        if (userTicket.getTimeSlot() != null && !ParkTicketSlotUtils.isValid(userTicket.getTimeSlot())) {
            throw new ServiceException("入园时段不合法（0全天 1上午 2下午 3夜场）");
        }
        if (userTicket.getTimeSlot() != null && !userTicket.getTimeSlot().equals(exists.getTimeSlot())) {
            ShopTrendyParkTicket ticket = ticketMapper.selectById(exists.getTicketId());
            if (ticket != null && !ParkTicketSlotUtils.contains(ticket.getTimeSlots(), userTicket.getTimeSlot())) {
                throw new ServiceException("票种「" + ticket.getTicketName() + "」不卖这个入园时段，可选的那几档在票种上看一眼");
            }
        }
        // 白名单式整行覆盖：只带这几格进去，票面快照、园区、状态、那两个时间一个都不碰。
        // 三个持票人字段是实体上的 IGNORED，清空写得回库；全局 not_empty 会跳过 null，所以不给赋 null 的那些格保持原值。
        ShopParkUserTicket form = new ShopParkUserTicket();
        form.setId(exists.getId());
        form.setVisitDate(userTicket.getVisitDate());
        form.setTimeSlot(userTicket.getTimeSlot());
        form.setHolderName(userTicket.getHolderName());
        form.setHolderPhone(userTicket.getHolderPhone());
        form.setHolderIdCard(userTicket.getHolderIdCard());
        form.setRemark(userTicket.getRemark());
        // qrImg 也是 IGNORED，但它不是表单里的格（出票那一刻生成的），不带过来这一趟就把票面图抹成 NULL 了
        //（实测：改一次游玩日期，qr_img 就空了）—— 原样带着走，要重出图另说
        form.setQrImg(exists.getQrImg());
        updateById(form);
    }

    @Override
    public void verify(Long id) {
        ShopParkUserTicket ticket = mustGet(id);
        assertConsumerOwns(ticket.getUserId());
        if (ticket.getStatus() != null && ticket.getStatus() != STATUS_UNUSED) {
            throw new ServiceException("这张票" + statusLabel(ticket.getStatus()) + "，不用再核销");
        }
        // 游玩日期还没到不给放人进去（口径同影院票不能提前一天检票）。
        // 过去了的那天不用在这里现判：过期那一档由 expireTask 每晚落，落到 2 的票上面那道状态判据已经拦住了
        if (ticket.getVisitDate() != null && ticket.getVisitDate().isAfter(LocalDate.now())) {
            throw new ServiceException("这张票的游玩日期是 " + ticket.getVisitDate() + "，还没到入园日子");
        }
        int rows = baseMapper.verify(id, LocalDateTime.now());
        if (rows == 0) {
            throw new ServiceException("这张票刚被核销过了，请刷新后看状态");
        }
    }

    @Override
    public void refund(Long id) {
        ShopParkUserTicket ticket = mustGet(id);
        assertConsumerOwns(ticket.getUserId());
        if (ticket.getStatus() != null && ticket.getStatus() == STATUS_REFUNDED) {
            throw new ServiceException("这张票已经退过了");
        }
        // 只改状态、不动钱：这一期没有购票支付链路（没有订单、没有 sys_refund），
        // 真接上统一下单时这一条要改成走退款单，由回调那边盖 refund_time
        int rows = baseMapper.refund(id, LocalDateTime.now());
        if (rows == 0) {
            throw new ServiceException("这张票当前状态不能退款，请刷新后看状态");
        }
    }

    @Override
    public String refreshQrCode(Long id) {
        ShopParkUserTicket ticket = mustGet(id);
        assertConsumerOwns(ticket.getUserId());
        // 出图和落 qr_img 都在 commom 那一处（和出票时的是同一条路，只是这一条不咽错误）：
        // 票码不动，所以扫出来还是同一串，手里已经印出来的那张码不会因此作废
        return shopParkTicketIssueService.regenerateQrCode(id);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        removeByIds(ids);
    }

    private ShopParkUserTicket mustGet(Long id) {
        if (id == null) {
            throw new ServiceException("票ID不能为空");
        }
        ShopParkUserTicket ticket = getById(id);
        if (ticket == null) {
            throw new ServiceException("这张票不存在");
        }
        return ticket;
    }

    /**
     * 报错文案里的状态标签：字典那套读接口在这里够不着（服务层不读 Redis 缓存），
     * 就按字典 user_ticket_status 那四档自己摊一遍
     */
    private String statusLabel(Integer status) {
        if (status == null) {
            return "状态未知";
        }
        switch (status) {
            case STATUS_USED:
                return "已核销";
            case STATUS_EXPIRED:
                return "已过期";
            case STATUS_REFUNDED:
                return "已退款";
            case STATUS_UNUSED:
                return "还没用过";
            default:
                return "状态是 " + status;
        }
    }

    /**
     * 消费者账号的归属校验，口径同列表的数据范围：非消费者直接放过。
     * 列表已经收窄了，这里补的是"绕过列表直接拿别人的票 id 打接口"那条路。
     *
     * @param ownerId 这张票归属的消费者 id（shop_trendy_park_user_ticket.user_id）
     */
    private void assertConsumerOwns(Long ownerId) {
        if (!LoginUserUtils.isConsumer()) {
            return;
        }
        if (ownerId == null || ownerId.longValue() != StpUtil.getLoginIdAsLong()) {
            throw new ServiceException("这张票不属于你，无权操作");
        }
    }
}
