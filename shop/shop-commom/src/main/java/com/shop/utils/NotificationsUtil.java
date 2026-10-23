package com.shop.utils;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shop.entity.SysNotifications;
import com.shop.enums.NotificationTypeEnum;
import com.shop.event.NotificationEvent;
import com.shop.mapper.SysNotificationsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * 消息通知的门面：挂载点只发一句 publish(事件)，落主表、展开接收人、发邮件都在提交之后的那一段异步里做。
 *
 * 为什么绕这一圈：
 * 1) 落库那一段要写两张表、还要按 user/dept/role 展开收件人，挂在发评论、点确认收货那条链上只会让人白等；
 * 2) 通知必须等业务真的提交成功才发 —— 评论回滚了却给被回复那人推一条提醒，比不发更糟；
 * 3) fallbackExecution = true 不能省：评论发表、订单签收这两条链现在压根没有事务，
 *    只认 AFTER_COMMIT 的话这两类通知一次都发不出去。
 */
@Slf4j
@Service("notificationsUtil")
@RequiredArgsConstructor
public class NotificationsUtil {

    /** sys_notifications.title / link 两格的列宽，超了 MySQL 直接截串报错 */
    private static final int TITLE_MAX = 255;
    private static final int LINK_MAX = 255;

    /** message 是 text（64KB），评论正文最长 500 字，正常到不了这里；防的是后台手工塞进公告的超长内容 */
    private static final int MESSAGE_MAX = 20000;

    private final ApplicationEventPublisher publisher;

    private final SysNotificationsMapper baseMapper;

    private final EmailUtil emailUtil;

    /**
     * 挂载点唯一要写的调用：把事件丢出去就完事，本方法不碰库、不占请求线程。
     */
    public void publish(NotificationEvent event) {
        publisher.publishEvent(event);
    }

    /**
     * 业务提交成功之后由容器回调；@Async 让它跑在 notificationExecutor 上。
     * 这一段吞掉所有异常：通知发不出去是页面上少一条提醒，不该把已经提交成功的业务动作翻成失败。
     */
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotification(NotificationEvent event) {
        try {
            send(event);
        } catch (Exception e) {
            log.error("通知发送失败：businessType={}, businessId={}, title={}",
                    event.getType(), event.getBusinessId(), event.getTitle(), e);
        }
    }

    /**
     * 这一条业务数据的这个档通知发过没有 —— 活动开始、培训开始两条定时扫描靠它去重（表上没有 notified 标志列）。
     */
    public boolean alreadySent(NotificationTypeEnum type, Long businessId) {
        return baseMapper.selectCount(new LambdaQueryWrapper<SysNotifications>()
                .eq(SysNotifications::getBusinessType, type.getCode())
                .eq(SysNotifications::getBusinessId, businessId)
                .eq(SysNotifications::getDelFlag, 0)) > 0;
    }

    /**
     * 后台「消息通知」那页新增/修改完之后补两件事：按 notice_push 认收件范围，回写 category / is_broadcast，
     * 定向的再把接收人铺进关联表 —— 前台现在只认接收人行，这一步不做那条消息就谁也看不见。
     *
     * @param record 已经落库、带 id 与 sendCode 的那一行（noticePush 是表单里那串 JSON）
     */
    public void expandReceivers(SysNotifications record) {
        if (record.getId() == null) {
            return;
        }
        List<List<Long>> push = parseNoticePush(record.getNoticePush());
        List<Long> userIds = push.get(0);
        List<Long> deptIds = push.get(1);
        List<Long> roleIds = push.get(2);
        boolean broadcast = userIds.isEmpty() && deptIds.isEmpty() && roleIds.isEmpty();

        int category = NotificationTypeEnum.of(record.getBusinessType()).getCategory();
        baseMapper.update(null, new LambdaUpdateWrapper<SysNotifications>()
                .eq(SysNotifications::getId, record.getId())
                .set(SysNotifications::getCategory, category)
                .set(SysNotifications::getIsBroadcast, broadcast ? 1 : 0));
        if (!broadcast) {
            baseMapper.fanOutReceivers(record.getId(), record.getSendCode(), userIds, deptIds, roleIds);
        }
    }

    private void send(NotificationEvent event) {
        NotificationTypeEnum type = event.getType();
        List<Long> userIds = clean(event.getUserIds());
        List<Long> deptIds = clean(event.getDeptIds());
        List<Long> roleIds = clean(event.getRoleIds());
        // 三档全空 = 全员广播：不铺接收人，前台靠主表 is_broadcast 认（否则每次发公告要先写几万行）
        boolean broadcast = userIds.isEmpty() && deptIds.isEmpty() && roleIds.isEmpty();

        SysNotifications record = SysNotifications.builder()
                .businessType(type.getCode())
                .category(type.getCategory())
                .title(cut(event.getTitle(), TITLE_MAX))
                .message(cut(event.getMessage(), MESSAGE_MAX))
                .link(cut(event.getLink(), LINK_MAX))
                .businessId(event.getBusinessId())
                .fromUserId(event.getFromUserId())
                .noticePush(broadcast ? null : buildNoticePush(userIds, deptIds, roleIds))
                .isBroadcast(broadcast ? 1 : 0)
                // 发送批次：32 位 uuid，后台「重新发送」换新值并把上一批接收记录清掉
                .sendCode(UUID.randomUUID().toString().replace("-", ""))
                .delFlag(0)
                .build();
        baseMapper.insert(record);

        if (!broadcast) {
            baseMapper.fanOutReceivers(record.getId(), record.getSendCode(), userIds, deptIds, roleIds);
        }

        // 公告额外抄送一封邮件（老行为，收件人就是原先钉死在那一句里的那个邮箱）
        if (type == NotificationTypeEnum.NOTICE) {
            try {
                emailUtil.send("2039916844@qq.com", record.getTitle(), record.getMessage());
            } catch (Exception e) {
                log.error("公告邮件发送失败：{}", record.getTitle(), e);
            }
        }
        log.info("通知已发送：businessType={}, businessId={}, broadcast={}, title={}",
                type.getCode(), event.getBusinessId(), broadcast, record.getTitle());
    }

    /**
     * 拼 notice_push JSON：{"user":[],"dept":[],"role":[]}。
     * 这一格现在只给后台回显发送范围用，前台可见性已经改看接收人表，读侧不再解析它。
     */
    public static String buildNoticePush(List<Long> userIds, List<Long> deptIds, List<Long> roleIds) {
        StringBuilder sb = new StringBuilder("{\"user\":[");
        appendIds(sb, userIds);
        sb.append("],\"dept\":[");
        appendIds(sb, deptIds);
        sb.append("],\"role\":[");
        appendIds(sb, roleIds);
        sb.append("]}");
        return sb.toString();
    }

    private static void appendIds(StringBuilder sb, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ids.get(i));
        }
    }

    /**
     * 读后台表单那串 JSON，拆成 user/dept/role 三档 id。
     * 解析不动就当「定向但收件人为空」返回（宁可乐死一条消息，也不能把本该定向的内容当成全员广播铺出去）。
     *
     * @return 定长三项的 List，依次是 userIds、deptIds、roleIds
     */
    public static List<List<Long>> parseNoticePush(String noticePush) {
        List<Long> userIds = new ArrayList<>();
        List<Long> deptIds = new ArrayList<>();
        List<Long> roleIds = new ArrayList<>();
        if (noticePush != null && !noticePush.trim().isEmpty()) {
            try {
                JSONObject json = JSONUtil.parseObj(noticePush);
                fill(json.getJSONArray("user"), userIds);
                fill(json.getJSONArray("dept"), deptIds);
                fill(json.getJSONArray("role"), roleIds);
            } catch (Exception e) {
                log.warn("notice_push 解析失败，这一条按无收件人处理：{}", noticePush);
            }
        }
        List<List<Long>> result = new ArrayList<>(3);
        result.add(userIds);
        result.add(deptIds);
        result.add(roleIds);
        return result;
    }

    private static void fill(JSONArray array, List<Long> into) {
        if (array == null) {
            return;
        }
        for (Object item : array) {
            if (item != null) {
                String text = item.toString().trim();
                if (!text.isEmpty()) {
                    try {
                        into.add(Long.valueOf(text));
                    } catch (NumberFormatException ignore) {
                        // 后台那一格是手工可编的，脏值跳过
                    }
                }
            }
        }
    }

    /** 去掉 null 与重复：一个人既在被点名的用户里、又在被点名的部门里，不该收两条同样的提醒 */
    private static List<Long> clean(List<Long> ids) {
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null) {
                    distinct.add(id);
                }
            }
        }
        return new ArrayList<>(distinct);
    }

    private static String cut(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

}
