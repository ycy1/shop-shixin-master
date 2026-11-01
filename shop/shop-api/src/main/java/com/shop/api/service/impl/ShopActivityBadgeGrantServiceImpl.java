package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.api.service.ShopActivityBadgeGrantService;
import com.shop.dto.activity.ShopActivityBadgeGiveDto;
import com.shop.entity.ShopActivityBadge;
import com.shop.entity.ShopActivityBadgeUser;
import com.shop.entity.ShopActivityCoupon;
import com.shop.entity.ShopActivityCouponUser;
import com.shop.entity.ShopActivityPoints;
import com.shop.entity.ShopActivityPointsLog;
import com.shop.entity.SysUser;
import com.shop.enums.NotificationTypeEnum;
import com.shop.event.NotificationEvent;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopActivityBadgeMapper;
import com.shop.mapper.ShopActivityBadgeUserMapper;
import com.shop.mapper.ShopActivityCouponMapper;
import com.shop.mapper.ShopActivityCouponUserMapper;
import com.shop.mapper.ShopActivityPointsLogMapper;
import com.shop.mapper.ShopActivityPointsMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.utils.CouponUtils;
import com.shop.utils.NotificationsUtil;
import com.shop.utils.TransactionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 徽章发放实现（三条路怎么分工见接口 {@link ShopActivityBadgeGrantService}）
 *
 * 达标比的是**账户上的存量**、不是这一次的动作量：门店/消费两类比的是这个人名下所有已支付订单的
 * Σ quantity（买满 10 件是分几次买到的也算），签到那一档比的是该用户名下签到表有几行。
 * 所以自动那两条都不挂在业务动作上 —— 一轮定时任务用一条 SQL 把全平台够线的人和徽章一起比完，
 * 回到这里的每一行就是要发的一枚，不用再数一遍存量、也不再按人回捞候选
 *（原来一次支付回调按这一单铺到的每家店各发两趟 SQL，挂在收钱那条链的关键路径上）。
 *
 * 兑现的顺序是先落徽章行、后发分和券：那一行撞 uk_badge_user 就说明这个人早就拿过了，
 * 直接返回、一分一券都不发 —— 反过来先兑现就会出现"券已经到手上、徽章行插不进去"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopActivityBadgeGrantServiceImpl implements ShopActivityBadgeGrantService {

    private final ShopActivityBadgeMapper badgeMapper;

    private final ShopActivityBadgeUserMapper badgeUserMapper;

    private final ShopActivityCouponMapper couponMapper;

    private final ShopActivityCouponUserMapper couponUserMapper;

    private final ShopActivityPointsMapper pointsMapper;

    private final ShopActivityPointsLogMapper pointsLogMapper;

    private final SysUserMapper sysUserMapper;

    private final ShopPointsSupport shopPointsSupport;

    private final NotificationsUtil notificationsUtil;

    /** 积分流水的业务类型：11 徽章发放（字典 points_biz_type，正数是发分、这一档没有负数那头） */
    private static final int BIZ_TYPE_BADGE = 11;

    /**
     * 用户券的领取方式：6 徽章发放 —— 由徽章发出去的券单独记这一档，
     * 和后台「优惠券」那页那颗「发放」（2 系统发放）分开，也留出 3 活动赠送 那一档给以后真接活动赠送时用；
     * 用户在「我的优惠券」和后台的「发放记录」里都能按这一档筛出「这张券是哪枚徽章换来的」
     */
    private static final int GET_WAY_BADGE = 6;

    /** 券模板状态：3 已结束 —— 这一档起不再发券（4 已取消 那一档读 CouponUtils 里那一个常量） */
    private static final int COUPON_STATUS_FINISHED = 3;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int grantSigninBadges() {
        // 达标线在 SQL 的 HAVING 里就比完了，回到这里的一行就是"这一枚该发给这个人"（一人够线几枚出几行），
        // 所以这一趟不再数天数、也不再按人回捞候选 —— 一轮一次一条读，替代了原来每次签到的那几趟
        List<ShopActivityBadge> hits = badgeMapper.selectSigninBadgeHits();
        return issue(hits, ShopActivityBadgeUser.SOURCE_SYSTEM, "签到徽章");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int grantPurchaseBadges() {
        // 和签到那一条同一个形状：件数（按店的和全平台的）都在一条 SQL 里聚完、比完，
        // 门店和消费两档一起回来，认来源只看 badge_type（发放这一刻两类都落 3 消费达标）
        List<ShopActivityBadge> hits = badgeMapper.selectPurchaseBadgeHits();
        return issue(hits, ShopActivityBadgeUser.SOURCE_CONSUME, "门店/消费徽章");
    }

    /** 自动那两条（签到、门店/消费）跑的是同一批动作：逐行发放、数一下实发几枚 */
    private int issue(List<ShopActivityBadge> hits, int source, String label) {
        if (hits.isEmpty()) {
            return 0;
        }
        int issued = 0;
        for (ShopActivityBadge hit : hits) {
            // 定时任务判的是"到这个人名下的存量够不够线"，没有"就是这一单/这一签"那一条记录可指，
            // 所以来源单据那几格留 NULL（同后台手工下发），要回看去订单明细/签到表按 user_id 数
            if (grant(hit, hit.getHitUserId(), source, null, false)) {
                issued++;
            }
        }
        log.info("{}定时判定：够线待发放 {} 组合，实发 {} 枚", label, hits.size(), issued);
        return issued;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int grantToUsers(ShopActivityBadgeGiveDto form) {
        if (form == null || form.getBadgeId() == null) {
            throw new ServiceException("请指明下发的是哪枚徽章");
        }
        List<Long> userIds = form.getUserIds() == null ? Collections.emptyList()
                : form.getUserIds().stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (userIds.isEmpty()) {
            throw new ServiceException("至少选一个要下发的用户");
        }
        ShopActivityBadge badge = badgeMapper.selectById(form.getBadgeId());
        if (badge == null) {
            throw new ServiceException("徽章不存在");
        }
        if (badge.getStatus() == null || badge.getStatus() != ShopActivityBadge.STATUS_ENABLED) {
            throw new ServiceException("这枚徽章已经禁用，不能下发");
        }
        // 真人再确认一遍：请求体里那串 id 是前端给的，账号没了就等于发给一个不存在的人
        //（只看"人还在"，不看停用与否 —— 徽章到了手上就是资产，口径同优惠券发放）
        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        if (users.isEmpty()) {
            throw new ServiceException("所选用户都不存在");
        }
        if (users.size() < userIds.size()) {
            // sys_user.id 是 Integer，请求体里那串是 Long，比之前先归成同一种类型，否则 contains 永远不成立
            List<Long> selected = users.stream().map(u -> u.getId().longValue()).collect(Collectors.toList());
            List<String> missing = userIds.stream().filter(id -> !selected.contains(id))
                    .map(id -> "#" + id).collect(Collectors.toList());
            throw new ServiceException("这几个用户已经不存在：" + String.join("、", missing));
        }
        // 已经持有这一枚的人先筛掉：下面那一步撞唯一键也会跳过，但那样就报不出"实发几个人"了
        Set<Long> held = badgeUserMapper.selectUserIdsByBadge(badge.getId()).stream()
                .collect(Collectors.toSet());
        int issued = 0;
        for (SysUser user : users) {
            Long userId = user.getId().longValue();
            if (held.contains(userId)) {
                continue;
            }
            // 手工下发这一路兑现是严格的：券发不出去就整批回滚，运营要看得见
            if (grant(badge, userId, ShopActivityBadgeUser.SOURCE_MANUAL, form.getEndTime(), true)) {
                issued++;
            }
        }
        if (issued == 0) {
            throw new ServiceException("这几个用户都已经持有这一枚徽章");
        }
        return issued;
    }

    @Override
    public boolean grant(ShopActivityBadge badge, Long userId, int source,
                         LocalDateTime endTime, boolean strictRewards) {
        TransactionUtils.requireActive("发放徽章");
        ShopActivityBadgeUser record = new ShopActivityBadgeUser();
        record.setBadgeId(badge.getId());
        record.setUserId(userId);
        // 形状那三格是快照，发放这一刻从定义表抄走，之后改名换图都不回头刷已经拿到的人
        record.setBadgeName(badge.getBadgeName());
        record.setBadgeText(badge.getBadgeText());
        record.setBadgeIcon(badge.getBadgeIcon());
        record.setSource(source);
        // source_id / source_no 不填：三条路都判不出"就是哪一张单据给的"了 ——
        // 自动那两条 2026-10-18 起住进定时任务，一轮服务里几十个人几张单，
        // 手工下发本来就没有单据。认来源只看 source 那一格，要回看去订单明细/签到表按 user_id 数
        record.setStartTime(LocalDateTime.now());
        record.setEndTime(endTime);
        record.setStatus(ShopActivityBadgeUser.STATUS_VALID);
        record.setIsShow(ShopActivityBadgeUser.SHOW_YES);
        // 前端在一个人同时挂着几枚时按这个排，抄定义表的 priority，省得再回定义表比一次
        record.setSort(badge.getPriority() == null ? 0 : badge.getPriority());
        try {
            badgeUserMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 同一刻两个动作判到同一枚（两轮任务重叠、或下发和任务撞上）：一人一枚，这一枚已经发过了
            return false;
        }
        redeemPoints(badge, userId, strictRewards);
        redeemCoupons(badge, userId, strictRewards);
        // 拿到手才提醒：撞 uk_badge_user 那一支已经提前 return，不会重复给同一个人发同一枚的提醒。
        // grant 一定在事务里（上面那句 requireActive），所以这条要等这一单真提交成功了才落库
        notificationsUtil.publish(NotificationEvent.builder()
                .type(NotificationTypeEnum.BADGE)
                .businessId(badge.getId())
                .title("你获得了一枚新徽章")
                .message("恭喜！你已获得徽章「" + badge.getBadgeName() + "」，去个人中心看看吧")
                .userId(userId)
                .build());
        return true;
    }

    /**
     * 兑现积分：原子加到账户上（没账的人由这一笔把第一行账带出来），再记一笔「11 徽章发放」的流水。
     *
     * 流水那头的 biz_id / biz_no 留 NULL（同上面那一段：三条路都没有那一张可指的单据），
     * 说清楚"这笔分是因为哪一枚徽章来的"靠的是说明里那个徽章名。
     */
    private void redeemPoints(ShopActivityBadge badge, Long userId, boolean strict) {
        int points = badge.getPoints() == null ? 0 : badge.getPoints();
        if (points <= 0) {
            return;
        }
        try {
            gainPoints(userId, points);
            ShopActivityPoints account = pointsMapper.selectOne(new LambdaQueryWrapper<ShopActivityPoints>()
                    .eq(ShopActivityPoints::getUserId, userId)
                    .last("LIMIT 1"));
            ShopActivityPointsLog row = new ShopActivityPointsLog();
            row.setUserId(userId);
            row.setPoints(points);
            // 这一格记的是"这一笔之后"账上的可用分，读回来、不自己算（口径同积分那两条链）
            row.setBalance(account == null || account.getBalance() == null ? 0 : account.getBalance());
            row.setBizType(BIZ_TYPE_BADGE);
            row.setDescription("获得徽章「" + badge.getBadgeName() + "」奖励 " + points + " 分");
            row.setExpireTime(LocalDateTime.now().plusDays(shopPointsSupport.expireDays()));
            pointsLogMapper.insert(row);
        } catch (RuntimeException e) {
            if (strict) {
                throw e;
            }
            // 自动那一路不能因为一笔奖励发不出去把整条链顶回去（分是白给的，钱已经收了）
            log.warn("徽章 {} 的积分奖励发放失败，userId={}", badge.getId(), userId, e);
        }
    }

    /**
     * 给这一笔分。
     *
     * 增量那条 UPDATE 对没账的人是 0 行改动（不报错），所以拿返回值分清"改到了行"还是"要建第一行账"；
     * 建账撞 uk_user 不报错、改走增量，别把已有那行账覆盖掉（同签到、购物送分那两处的写法）。
     */
    private void gainPoints(Long userId, int points) {
        if (pointsMapper.gainPoints(userId, points) == 0) {
            ShopActivityPoints account = new ShopActivityPoints();
            account.setUserId(userId);
            account.setTotalPoints(points);
            account.setUsedPoints(0);
            account.setBalance(points);
            account.setFrozenPoints(0);
            // expire_points 留 0：那一格是"即将过期积分"，要等过期那一条链（本期没有定时任务）才算
            account.setExpirePoints(0);
            try {
                pointsMapper.insert(account);
            } catch (DuplicateKeyException e) {
                if (pointsMapper.gainPoints(userId, points) == 0) {
                    throw new ServiceException("积分账户写入失败，徽章的积分奖励没发出去");
                }
            }
        }
    }

    /**
     * 兑现券：定义表上那几个券模板 id 逐个发到这个人名下，一张模板一张券、各占一份发行额度。
     *
     * 快照那六格、有效期、券码序号全部按后台「发放」那一套现取（同一个 CouponUtils，两份写入方共用一条口径）。
     */
    private void redeemCoupons(ShopActivityBadge badge, Long userId, boolean strict) {
        List<Long> couponIds = parseIds(badge.getCouponIds());
        for (Long couponId : couponIds) {
            try {
                issueCoupon(badge, couponId, userId);
            } catch (ServiceException e) {
                if (strict) {
                    throw new ServiceException("徽章「" + badge.getBadgeName() + "」的优惠券发不出去："
                            + e.getMessage());
                }
                log.warn("徽章 {} 的券模板 {} 发放失败，userId={}：{}", badge.getId(), couponId, userId,
                        e.getMessage());
            }
        }
    }

    /** 发一张券给一个人（占量 → 回读序号 → 落快照行，三步和后台「发放」完全同形） */
    private void issueCoupon(ShopActivityBadge badge, Long couponId, Long userId) {
        ShopActivityCoupon template = couponMapper.selectById(couponId);
        if (template == null) {
            throw new ServiceException("券模板 " + couponId + " 不存在（可能已经被删除）");
        }
        Integer status = template.getStatus();
        if (status != null && (status == COUPON_STATUS_FINISHED
                || status == CouponUtils.TEMPLATE_STATUS_CANCELED)) {
            throw new ServiceException("券「" + template.getCouponName() + "」已经结束或取消");
        }
        LocalDateTime expireTime = CouponUtils.expireTimeOf(template);
        if (couponMapper.increaseIssuedCount(couponId, 1) == 0) {
            throw new ServiceException("券「" + template.getCouponName() + "」的发行量已经占满");
        }
        // 占完再读一次：这一行此刻被本事务锁着，读回来的 issued_count 就是"这一张之后"的数，并发发不到同一个号
        ShopActivityCoupon after = couponMapper.selectById(couponId);
        int issuedAfter = after == null || after.getIssuedCount() == null ? 1 : after.getIssuedCount();

        ShopActivityCouponUser record = new ShopActivityCouponUser();
        record.setCouponId(couponId);
        record.setCouponCode(CouponUtils.couponCode(couponId, issuedAfter));
        record.setUserId(userId);
        record.setCouponName(template.getCouponName());
        record.setCouponType(template.getCouponType());
        record.setFaceValue(template.getFaceValue());
        record.setThresholdAmount(template.getThresholdAmount());
        record.setMaxDiscount(template.getMaxDiscount());
        record.setScopeType(template.getScopeType());
        record.setStatus(CouponUtils.USER_STATUS_UNUSED);
        record.setReceiveTime(LocalDateTime.now());
        record.setExpireTime(expireTime);
        record.setGetWay(GET_WAY_BADGE);
        record.setRemark("由徽章「" + badge.getBadgeName() + "」发放");
        couponUserMapper.insert(record);
    }

    /**
     * 把 coupon_ids 那一格（逗号分隔的券模板 id）拆成列表。
     *
     * 分隔符认中文逗号、顿号和空白，脏值（非数字、空串）跳过不报错：
     * 这一格是后台表单手填的，一个笔误不该把整枚徽章的发放打出去，顶多是那一张券没发到。
     */
    private List<Long> parseIds(String raw) {
        List<Long> ids = new ArrayList<>();
        if (StringUtils.isBlank(raw)) {
            return ids;
        }
        for (String part : raw.split("[，,、\\s]+")) {
            String value = part.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                Long id = Long.valueOf(value);
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            } catch (NumberFormatException e) {
                log.warn("徽章上的券模板 id {} 不是数字，跳过这一格", value);
            }
        }
        return ids;
    }
}
