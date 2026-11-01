package com.shop.service.impl;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.shop.dto.park.ShopParkTicketBuyDto;
import com.shop.dto.park.ShopParkTicketIssueDto;
import com.shop.entity.ShopParkUserTicket;
import com.shop.exception.ServiceException;
import com.shop.service.ShopParkTicketBuyService;
import com.shop.service.ShopParkTicketIssueService;
import com.shop.vo.trendy.ShopParkTicketBuyVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 购票 = 把「给自己买哪张票、哪天来」这份请求转成出票入参，剩下的都交给出票那一条。
 *
 * 这一层只多加两件事：
 * 1. <b>给谁</b>由登录态定，请求体点不到别人的 id（和抽盒同一道规矩：参数能点名别人就等于替别人买）；
 * 2. 游玩日期不能早于今天 —— 后台代客出票没有这条（补历史票是它的正常用法），给自己买的人不会想约过去的日子，
 *    而且真给了昨天，票落下来就是「一落地等着被定时任务落成已过期」，那不是买票是造脏数据。
 *
 * 票种在不在架、园区开没开门、时段是不是这一档卖的、张数和每单限购，一律是
 * {@link ShopParkTicketIssueService} 里判的，这里不抄第二遍。
 */
@Service
@RequiredArgsConstructor
public class ShopParkTicketBuyServiceImpl implements ShopParkTicketBuyService {

    private final ShopParkTicketIssueService issueService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopParkTicketBuyVo buy(ShopParkTicketBuyDto dto) {
        if (dto == null) {
            throw new ServiceException("购票参数不能为空");
        }
        long userId = currentUserId();
        if (dto.getVisitDate() == null) {
            throw new ServiceException("请选择游玩日期");
        }
        if (dto.getVisitDate().isBefore(LocalDate.now())) {
            throw new ServiceException("游玩日期不能早于今天");
        }

        // 一张票一行的形状、票码规则、票面快照全在出票那一条里定，这里只负责递进去
        ShopParkTicketIssueDto issue = new ShopParkTicketIssueDto();
        issue.setUserId(userId);
        issue.setTicketId(dto.getTicketId());
        issue.setVisitDate(dto.getVisitDate());
        issue.setTimeSlot(dto.getTimeSlot());
        issue.setQuantity(dto.getQuantity());
        issue.setHolderName(dto.getHolderName());
        issue.setHolderPhone(dto.getHolderPhone());
        issue.setHolderIdCard(dto.getHolderIdCard());
        List<ShopParkUserTicket> tickets = issueService.issue(issue);

        return result(tickets);
    }

    /** 谁买的只能由登录态决定：参数能点名别人就等于替别人买 */
    private long currentUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (NotLoginException e) {
            throw new ServiceException("请先登录后再购票");
        }
    }

    /**
     * 一次购票的读数：这一批是同一张票种、同一天出来的，那几格取第一行就够；
     * 应付合计按落下来的票价快照逐张加（不是按票种现价的乘 —— 快照才是这次交易的实际单价）。
     */
    private ShopParkTicketBuyVo result(List<ShopParkUserTicket> tickets) {
        ShopParkUserTicket first = tickets.get(0);
        ShopParkTicketBuyVo vo = new ShopParkTicketBuyVo();
        vo.setParkId(first.getParkId());
        vo.setTicketId(first.getTicketId());
        vo.setTicketName(first.getTicketName());
        vo.setTicketType(first.getTicketType());
        vo.setVisitDate(first.getVisitDate());
        vo.setTimeSlot(first.getTimeSlot());
        vo.setCount(tickets.size());
        BigDecimal amount = BigDecimal.ZERO;
        for (ShopParkUserTicket ticket : tickets) {
            if (ticket.getPrice() != null) {
                amount = amount.add(ticket.getPrice());
            }
        }
        vo.setAmount(amount);
        vo.setTickets(tickets);
        return vo;
    }
}
