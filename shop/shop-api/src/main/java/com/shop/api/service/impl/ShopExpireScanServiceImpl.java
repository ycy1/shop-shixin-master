package com.shop.api.service.impl;

import com.shop.api.service.ShopExpireScanService;
import com.shop.mapper.ShopActivityCouponUserMapper;
import com.shop.mapper.ShopParkUserTicketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 两条过期扫描都只是"一条条件 UPDATE + 记一行日志"，判据全在 SQL 的 WHERE 里
 *（形状同 mapper 上那两个方法的注释）：
 * status = 0 压在条件里，所以已经用掉/核销/作废/退过的行绝不回头被改成过期，任务重复跑是幂等的。
 *
 * 为什么不捞出来逐行改：这两张表都是全平台量级，公网 MySQL 一发一个来回；
 * 而且这里不需要知道"是哪几张"，只要把该过的那批一次推过去。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopExpireScanServiceImpl implements ShopExpireScanService {

    private final ShopParkUserTicketMapper parkUserTicketMapper;

    private final ShopActivityCouponUserMapper couponUserMapper;

    @Override
    public int expireParkTickets() {
        // 基准日是"今天"：今天那张还没玩完，得等明天这一轮才落成过期
        int rows = parkUserTicketMapper.markExpired(LocalDate.now());
        log.info("主题公园票过期扫描：{} 张 0 未使用 落成 2 已过期（游玩日期早于 {}）", rows, LocalDate.now());
        return rows;
    }

    @Override
    public int expireUserCoupons() {
        int rows = couponUserMapper.markExpired(LocalDateTime.now());
        log.info("用户优惠券过期扫描：{} 张 0 未使用 落成 2 已过期（expire_time 早于 {}）", rows, LocalDateTime.now());
        return rows;
    }
}
