package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.entity.ShopParkUserTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户票种表 Mapper接口
 *
 * 列表走自定义 XML（要联 sys_user 拿昵称/手机号、联园区名，BaseMapper 的 page() 挂不上 JOIN）；
 * 新增/编辑走 BaseMapper；两道状态流转（核销、退款）是自定义 UPDATE，
 * 前态压在 WHERE 里当并发那道闸（形状同 ShopActivityCouponUserMapper#redeem）。
 */
@Mapper
public interface ShopParkUserTicketMapper extends BaseMapper<ShopParkUserTicket> {

    /**
     * 分页查用户票：keyword 搜票码/票种名/持票人姓名电话，userId、parkId、ticketType、status 精确匹配，
     * beginVisitDate/endVisitDate 卡游玩日期
     */
    IPage<ShopParkUserTicket> selectUserTicketPage(IPage<ShopParkUserTicket> page, @Param("q") ShopParkUserTicket query);

    /**
     * 入园核销：0 未使用 → 1 已核销，同时盖核销时间。
     * status = 0 压在 WHERE 里就是那道闸（同一张票同时被两个闸机点，后到的那条一行都更新不到，
     * 服务层按返回 0 报"刚被核销过了"）。
     *
     * @return 更新到的行数（0 表示这一张已经不是未使用状态了）
     */
    int verify(@Param("id") Long id, @Param("usedTime") LocalDateTime usedTime);

    /**
     * 退款：0 未使用 / 1 已核销 / 2 已过期 → 3 已退款，同时盖退款时间。
     * 3 已退款不再在放行名单里，所以重复点这一颗第二次一行都更新不到。
     * 2 已过期放开：过期票是最该退的一种（判据由 expireTask 那条任务落，见 markExpired）。
     *
     * @return 更新到的行数（0 表示这张票的状态不能退）
     */
    int refund(@Param("id") Long id, @Param("refundTime") LocalDateTime refundTime);

    /**
     * 过期扫描（定时任务用，一天一次）：游玩日期已经过了、还从来没核销过的票 → 2 已过期。
     * status = 0 压在 WHERE 里 ⇒ 已核销/已退过的绝不回头改状态，重复跑只补当天新进过窗口的那几张。
     *
     * @param today 判"过了当天"的基准日（任务里传当天；今天那张还没玩完，不算过期）
     * @return 这次落成已过期的张数
     */
    int markExpired(@Param("today") LocalDate today);
}
