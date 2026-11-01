package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.api.service.ShopNoticeScanService;
import com.shop.entity.ShopActivity;
import com.shop.entity.ShopStoreTraining;
import com.shop.entity.ShopStoreTrainingMember;
import com.shop.entity.ShopTrendyIp;
import com.shop.entity.SysNotifications;
import com.shop.enums.NotificationTypeEnum;
import com.shop.event.NotificationEvent;
import com.shop.mapper.ShopActivityMapper;
import com.shop.mapper.ShopStoreTrainingMapper;
import com.shop.mapper.ShopStoreTrainingMemberMapper;
import com.shop.mapper.ShopTrendyIpMapper;
import com.shop.mapper.SysNotificationsMapper;
import com.shop.utils.NotificationsUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 到点提醒的实现（三条各扫各的，形状同徽章那两条自动发放）
 *
 * 活动、培训那两条只发一次靠 NotificationsUtil.alreadySent(type, businessId)：这两张表上都没有"提醒发过没有"那一列，
 * 而 sys_notifications 里存着 business_type + business_id，按这两格回查一次就够了（走的是那次新加的
 * idx_type_business 索引）。不加列的代价是每轮多一趟 count，换来的是不用回头改两张业务表。
 *
 * 只回看一天：任务一天跑一次，往前收一天正好接上；不收这个口的话，这一版上线的第一轮会把库里
 * 所有历史「投放中」「报名中」的活动和培训各广播一条出去 —— 那些活动早就开始过了。
 * 上线当天请手动「执行一次」把那批到点的补齐，之后按 cron 走。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopNoticeScanServiceImpl implements ShopNoticeScanService {

    /** 活动状态（字典 activity_status）：2 投放中 —— 只有正在投放的活动谈得上"开始了" */
    private static final int ACTIVITY_STATUS_RUNNING = 2;

    /** 培训状态（字典 training_status）：1 报名中 2 已截止 3 进行中 —— 这三种都还没开完，到点都该提醒 */
    private static final List<Integer> TRAINING_STATUS_OPEN = Arrays.asList(1, 2, 3);

    /** 参训人员的报名状态（字典 training_sign_status）：1 已报名 —— 退掉的（0/2）不提醒 */
    private static final int SIGN_STATUS_SIGNED = 1;

    /** 回看窗口：一天 */
    private static final int LOOK_BACK_DAYS = 1;

    /** 潮玩IP的状态（字典 trendy_ip_status）：1 启用 —— 已经停用的IP不必再催续期 */
    private static final int IP_STATUS_ENABLED = 1;

    /** 授权到期提醒的提前量：一个月。按 plusMonths 走，不折算成 30 天 —— 说「提前一个月」就照字面算 */
    private static final int LICENSE_AHEAD_MONTHS = 1;

    /** 提醒窗口往回收一天：任务停跑一轮，下一轮还能把那条补上；重叠那天靠下面的到期日判据挡回去 */
    private static final int LICENSE_WINDOW_DAYS = 1;

    private final ShopActivityMapper shopActivityMapper;

    private final ShopStoreTrainingMapper shopStoreTrainingMapper;

    private final ShopStoreTrainingMemberMapper shopStoreTrainingMemberMapper;

    private final ShopTrendyIpMapper shopTrendyIpMapper;

    /** 授权提醒的「这个到期日发过没有」要按到期日判，NotificationsUtil 那个 alreadySent 只到 businessId，所以这里自己回查一次 */
    private final SysNotificationsMapper sysNotificationsMapper;

    private final NotificationsUtil notificationsUtil;

    @Override
    public void scanActivityStart() {
        LocalDateTime now = LocalDateTime.now();
        List<ShopActivity> activities = shopActivityMapper.selectList(new LambdaQueryWrapper<ShopActivity>()
                // 取 activityName（「活动名称」那格，必填）；title 是「显示在活动上的文案」，可以留空，拼进正文就是「null」
                .select(ShopActivity::getId, ShopActivity::getActivityName)
                .eq(ShopActivity::getStatus, ACTIVITY_STATUS_RUNNING)
                // start_time 进 SELECT 清单又同时是判据，这一格非空才能避开"整行是 null"那个坑
                .isNotNull(ShopActivity::getStartTime)
                .le(ShopActivity::getStartTime, now)
                .ge(ShopActivity::getStartTime, now.minusDays(LOOK_BACK_DAYS)));
        for (ShopActivity activity : activities) {
            if (notificationsUtil.alreadySent(NotificationTypeEnum.ACTIVITY, activity.getId())) {
                continue;
            }
            // 三档收件人一个都不填 = 全员广播（主表 is_broadcast=1，不铺接收人行）
            notificationsUtil.publish(NotificationEvent.builder()
                    .type(NotificationTypeEnum.ACTIVITY)
                    .businessId(activity.getId())
                    .title("活动已开始")
                    .message("「" + activity.getActivityName() + "」已经开始，点击查看详情")
                    .build());
        }
    }

    @Override
    public void scanTrainingStart() {
        LocalDateTime now = LocalDateTime.now();
        List<ShopStoreTraining> trainings = shopStoreTrainingMapper.selectList(
                new LambdaQueryWrapper<ShopStoreTraining>()
                        .select(ShopStoreTraining::getId, ShopStoreTraining::getTrainingName,
                                ShopStoreTraining::getUserId)
                        .in(ShopStoreTraining::getStatus, TRAINING_STATUS_OPEN)
                        .isNotNull(ShopStoreTraining::getStartTime)
                        .le(ShopStoreTraining::getStartTime, now)
                        .ge(ShopStoreTraining::getStartTime, now.minusDays(LOOK_BACK_DAYS)));
        for (ShopStoreTraining training : trainings) {
            if (notificationsUtil.alreadySent(NotificationTypeEnum.TRAINING, training.getId())) {
                continue;
            }
            List<Long> userIds = signUserIds(training.getId());
            // 讲师不一定是报名成员（他自己不签自己那场），单独补进收件人
            Long lecturerId = training.getUserId();
            if (lecturerId != null && !userIds.contains(lecturerId)) {
                userIds.add(lecturerId);
            }
            // 一个人都没报（也没讲师）就别发：三档全空在 NotificationsUtil 里等于全员广播，
            // 培训这种定向提醒发成全平台可见就错了
            if (userIds.isEmpty()) {
                log.info("培训「{}」还没人报名，跳过开始提醒", training.getTrainingName());
                continue;
            }
            notificationsUtil.publish(NotificationEvent.builder()
                    .type(NotificationTypeEnum.TRAINING)
                    .businessId(training.getId())
                    .title("你报名的培训开始了")
                    .message("培训「" + training.getTrainingName() + "」已经开始，请按时参加")
                    .userIds(userIds)
                    .build());
        }
    }

    /**
     * 一轮扫临到期的IP授权：到期日落在「今天+1个月」往前收一天这个窗口里的，给版权人/授权人发一条定向提醒。
     *
     * 判据为什么按到期日去重（而不是像活动/培训那样按 businessId 一次终身）：授权是会续的 ——
     * 续完 license_end 变成新日期，下一年这一轮还得再提醒一次；按 businessId 去重就等于「一个IP一辈子只提醒一次」。
     * 键落在 link 那一格：收件箱 VO 刻意不返回 link（各端跳转认的是 businessType + businessId），
     * 所以这一格空着也是空着，这里把它当判据键用，值本身仍是一个能认出目标页的地址。
     */
    @Override
    public void scanLicenseExpire() {
        LocalDate today = LocalDate.now();
        LocalDate target = today.plusMonths(LICENSE_AHEAD_MONTHS);
        List<ShopTrendyIp> ips = shopTrendyIpMapper.selectList(new LambdaQueryWrapper<ShopTrendyIp>()
                // 拼正文要用的几格都进 SELECT：ip_name / license_end / user_id 里 license_end 和 user_id 同时是判据，
                // 非空筛过才不会有「整行被映射成 null」那个坑
                .select(ShopTrendyIp::getId, ShopTrendyIp::getIpName, ShopTrendyIp::getLicenseEnd,
                        ShopTrendyIp::getUserId)
                .eq(ShopTrendyIp::getStatus, IP_STATUS_ENABLED)
                .isNotNull(ShopTrendyIp::getUserId)
                .isNotNull(ShopTrendyIp::getLicenseEnd)
                .ge(ShopTrendyIp::getLicenseEnd, target.minusDays(LICENSE_WINDOW_DAYS))
                .le(ShopTrendyIp::getLicenseEnd, target));
        int sent = 0;
        for (ShopTrendyIp ip : ips) {
            String link = "/trendy/ip?ipId=" + ip.getId() + "&licenseEnd=" + ip.getLicenseEnd();
            if (alreadySentOn(NotificationTypeEnum.LICENSE, ip.getId(), link)) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, ip.getLicenseEnd());
            notificationsUtil.publish(NotificationEvent.builder()
                    .type(NotificationTypeEnum.LICENSE)
                    .businessId(ip.getId())
                    .link(link)
                    .title("IP授权即将到期")
                    .message("IP「" + ip.getIpName() + "」的授权将于 " + ip.getLicenseEnd() + " 到期（还剩 " + days
                            + " 天），请及时联系版权方续期")
                    // 只发给版权人/授权人本人：三档收件人全空在 NotificationsUtil 里等于全员广播，续期是定向的事
                    .userIds(Collections.singletonList(ip.getUserId()))
                    .build());
            sent++;
        }
        log.info("潮玩IP授权到期提醒：{} ~ {} 之间到期的启用IP {} 条，本轮发出 {} 条",
                target.minusDays(LICENSE_WINDOW_DAYS), target, ips.size(), sent);
    }

    /** 这一条授权的这个到期日提醒过没有（按 businessType + businessId + link 三格回查，判据同 NotificationsUtil.alreadySent，只是多带一格 link） */
    private boolean alreadySentOn(NotificationTypeEnum type, Long businessId, String link) {
        return sysNotificationsMapper.selectCount(new LambdaQueryWrapper<SysNotifications>()
                .eq(SysNotifications::getBusinessType, type.getCode())
                .eq(SysNotifications::getBusinessId, businessId)
                .eq(SysNotifications::getLink, link)
                .eq(SysNotifications::getDelFlag, 0)) > 0;
    }

    /**
     * 这一场培训报名成功的人（一条 SQL 捞 userId 那一格）。
     * isNotNull 那句是必需的：只 select 一列时那一列为空的行会整条变成 null 元素，后面取 id 就抛 NPE。
     */
    private List<Long> signUserIds(Long trainingId) {
        List<ShopStoreTrainingMember> members = shopStoreTrainingMemberMapper.selectList(
                new LambdaQueryWrapper<ShopStoreTrainingMember>()
                        .select(ShopStoreTrainingMember::getUserId)
                        .eq(ShopStoreTrainingMember::getTrainingId, trainingId)
                        .eq(ShopStoreTrainingMember::getSignStatus, SIGN_STATUS_SIGNED)
                        .isNotNull(ShopStoreTrainingMember::getUserId));
        Set<Long> distinct = new LinkedHashSet<>();
        for (ShopStoreTrainingMember member : members) {
            distinct.add(member.getUserId());
        }
        return new ArrayList<>(distinct);
    }
}
