package com.shop.utils;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shop.entity.SysNotifications;
import com.shop.entity.SysUser;
import com.shop.enums.NotificationTypeEnum;
import com.shop.event.NotificationEvent;
import com.shop.exception.ServiceException;
import com.shop.mapper.SysNotificationsMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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

    /** 同一条通知最多自动重试几次（含第一次）：满了就停下、通知管理员，等后台手动重试 */
    public static final int MAX_RETRY = 3;

    /** 给管理员的那条提醒的前缀：也靠它防「提醒自己失败又发提醒」的递归 */
    private static final String ALERT_TITLE_PREFIX = "通知发送失败";

    /** sys_user.account_type：0 平台账号（发给管理员的就是这几个） */
    private static final int ACCOUNT_TYPE_PLATFORM = 0;

    private final ApplicationEventPublisher publisher;

    private final SysNotificationsMapper baseMapper;

    private final SysUserMapper sysUserMapper;

    private final EmailUtil emailUtil;

    /** 进程外的那一段（shop-mq 的 RabbitMQ 实现）；没带 MQ 模块时拿不到，退回本地投递 */
    private final ObjectProvider<NotificationDispatcher> dispatchers;

    /**
     * 挂载点唯一要写的调用：把事件丢出去就完事，本方法不碰库、不占请求线程。
     *
     * 顺手给事件钉一个 sendCode：它是这一条通知在库里的身份 —— 重试、幂等地重投都靠它找回同一行，
     * 不能每次消费都重新生成（那样重试会插出第二行）。
     */
    public void publish(NotificationEvent event) {
        if (event.getSendCode() == null || event.getSendCode().trim().isEmpty()) {
            event.setSendCode(UUID.randomUUID().toString().replace("-", ""));
        }
        publisher.publishEvent(event);
    }

    /**
     * 业务提交成功之后由容器回调；@Async 让它跑在 notificationExecutor 上
     *（投递到 broker 也是网络调用，别占请求线程 —— 连不上时更别让下单那条链陪它等）。
     *
     * 正常路径：交给 NotificationDispatcher（shop-mq 那一条到 RabbitMQ），由消费者落库 + 铺接收人 + 发邮件。
     * 没有注册实现（这个工程没带 MQ 模块），或者 broker 不在、投递抛错时，退回原来那条「本地直接投递」——
     * 通知宁可慢一点，也不能因为在途失败就悄悄没了。
     */
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotification(NotificationEvent event) {
        NotificationDispatcher dispatcher = dispatchers.getIfAvailable();
        if (dispatcher != null) {
            try {
                dispatcher.dispatch(event);
                return;
            } catch (Exception e) {
                log.warn("通知投递到 MQ 失败，改走本地投递：businessType={}, businessId={}, title={}",
                        event.getType(), event.getBusinessId(), event.getTitle(), e);
            }
        }
        deliver(event);
    }

    /**
     * 真正落库的那一端：MQ 消费者（shop-mq 的 NotificationMqListener）反序列化完之后调这里；
     * 没有 MQ 或投递失败时，onNotification 也直接调这里。
     *
     * 返回**这一条累计失败了几次**：0 = 这趟成了。
     * 失败时把 fail_count +1（它同时就是"还没发出去"的标记：>0 前台不露、后台可重试）；
     * 到 MAX_RETRY 次就地停手，发一条系统消息通知平台账号 —— 不再往外抛异常，
     * 由调用方（消费者）根据这个数决定 ack 还是 nack。
     */
    public int deliver(NotificationEvent event) {
        try {
            send(event);
            return 0;
        } catch (Exception e) {
            log.error("通知发送失败：businessType={}, businessId={}, title={}",
                    event.getType(), event.getBusinessId(), event.getTitle(), e);
            int fails;
            try {
                fails = markFailed(event);
            } catch (Exception markError) {
                // 连「记失败」这一步都写不进去（库不可用）：按第一次算，让消息按原路重试
                log.error("记录通知失败次数也失败了：sendCode={}", event.getSendCode(), markError);
                return 1;
            }
            if (fails >= MAX_RETRY) {
                notifyAdmins(event, fails);
            }
            return fails;
        }
    }

    /**
     * 人工重试（后台「消息通知」页那颗「重试」）：把失败计数归零，按库里那一行重建事件再投一次。
     *
     * 走同一条投递路（有 MQ 就进队列、没有就本地发），所以重试失败也照样会累计次数、到点再提醒。
     * 收件人从 notice_push 还原（定向的那批 id 当初就存在这一格里）；整行内容不改，重试发的是原来那条。
     */
    public void retryById(Long id) {
        SysNotifications row = id == null ? null : baseMapper.selectById(id);
        if (row == null) {
            throw new ServiceException("这条通知不存在");
        }
        if (row.getFailCount() == null || row.getFailCount() <= 0) {
            throw new ServiceException("这条通知已经发送过了，不用重试");
        }
        baseMapper.update(null, new LambdaUpdateWrapper<SysNotifications>()
                .eq(SysNotifications::getId, row.getId())
                .set(SysNotifications::getFailCount, 0));
        NotificationEvent event = toEvent(row);
        NotificationDispatcher dispatcher = dispatchers.getIfAvailable();
        if (dispatcher != null) {
            try {
                dispatcher.dispatch(event);
                return;
            } catch (Exception e) {
                log.warn("重试通知投递到 MQ 失败，改走本地投递：sendCode={}", row.getSendCode(), e);
            }
        }
        deliver(event);
    }

    /** 把库里那一行还原成事件（重试用）：收件人从 notice_push 拆回来，三格全空 = 全员广播 */
    private NotificationEvent toEvent(SysNotifications row) {
        List<List<Long>> push = parseNoticePush(row.getNoticePush());
        return NotificationEvent.builder()
                .type(NotificationTypeEnum.of(row.getBusinessType()))
                .businessId(row.getBusinessId())
                .title(row.getTitle())
                .message(row.getMessage())
                .link(row.getLink())
                .fromUserId(row.getFromUserId())
                .sendCode(row.getSendCode())
                .userIds(push.get(0))
                .deptIds(push.get(1))
                .roleIds(push.get(2))
                .build();
    }

    /**
     * 失败记一笔：有那一行就 fail_count +1（>0 就等于"还没发出去"）；
     * 连行都还没建出来（第一次就崩在插库之前）就按这一份内容先占个位，计数从 1 开始。
     *
     * @return 这一条累计失败次数
     */
    private int markFailed(NotificationEvent event) {
        SysNotifications row = findBySendCode(event.getSendCode());
        if (row == null) {
            SysNotifications failed = buildRecord(event).failCount(1).build();
            baseMapper.insert(failed);
            return 1;
        }
        baseMapper.update(null, new LambdaUpdateWrapper<SysNotifications>()
                .eq(SysNotifications::getId, row.getId())
                .setSql("fail_count = fail_count + 1"));
        SysNotifications after = baseMapper.selectById(row.getId());
        return after == null || after.getFailCount() == null ? 1 : after.getFailCount();
    }

    /**
     * 自动重试到头了：给平台账号（account_type=0）发一条系统消息，说清是哪条、失败了几次、去哪儿重试。
     *
     * 防递归：提醒本身也是通知，它再失败三次就会再触发一次提醒 —— 靠标题前缀把这一支挡住，只记日志。
     */
    private void notifyAdmins(NotificationEvent failed, int fails) {
        if (failed.getType() == NotificationTypeEnum.SYSTEM
                && failed.getTitle() != null && failed.getTitle().startsWith(ALERT_TITLE_PREFIX)) {
            log.error("通知失败提醒本身也发不出去，不再递归提醒：sendCode={}", failed.getSendCode());
            return;
        }
        List<SysUser> admins = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getAccountType, ACCOUNT_TYPE_PLATFORM)
                .eq(SysUser::getStatus, 1));
        List<Long> adminIds = new ArrayList<>();
        for (SysUser admin : admins) {
            if (admin.getId() != null) {
                // sys_user.id 是 int，事件里的收件人是 Long（口径同各挂载点传进来的 userIds）
                adminIds.add(admin.getId().longValue());
            }
        }
        if (adminIds.isEmpty()) {
            log.warn("通知失败但找不到平台账号可提醒：sendCode={}", failed.getSendCode());
            return;
        }
        publish(NotificationEvent.builder()
                .type(NotificationTypeEnum.SYSTEM)
                .businessId(failed.getBusinessId())
                .title(ALERT_TITLE_PREFIX + "：" + failed.getTitle())
                .message("通知「" + failed.getTitle() + "」连续 " + fails + " 次发送失败（类型 "
                        + failed.getType().getCode() + "、业务 id " + failed.getBusinessId()
                        + "），已停止自动重试。请到「系统监控 → 消息通知」按失败次数筛出来点「重试」。")
                .userIds(adminIds)
                .build());
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

    /**
     * 落库那一趟（幂等）：按事件里的 sendCode 找那一行，没有就插、有就复用。
     *
     * 复用那一支是重试走的：先把上一趟铺出去的接收人行清掉再重新铺
     *（否则重试那次会在旧行上再铺一遍，同一批人撞唯一键被忽略、但上一趟没铺完的那部分就对不齐）；
     * 全员广播不铺接收人，靠主表 is_broadcast 认。
     * 最后把 fail_count 归零 —— 0 就是"已发送"：前台/App 那几条读按 fail_count=0 过滤，
     * 后台那一页则反过来，fail_count>0 的才给「重试」按钮。
     */
    private void send(NotificationEvent event) {
        NotificationTypeEnum type = event.getType();
        List<Long> userIds = clean(event.getUserIds());
        List<Long> deptIds = clean(event.getDeptIds());
        List<Long> roleIds = clean(event.getRoleIds());
        // 三档全空 = 全员广播：不铺接收人，前台靠主表 is_broadcast 认（否则每次发公告要先写几万行）
        boolean broadcast = userIds.isEmpty() && deptIds.isEmpty() && roleIds.isEmpty();

        SysNotifications record = findBySendCode(event.getSendCode());
        if (record == null) {
            record = buildRecord(event).build();
            baseMapper.insert(record);
        } else {
            baseMapper.deleteReceiverByNotificationId(record.getId());
        }

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
        baseMapper.update(null, new LambdaUpdateWrapper<SysNotifications>()
                .eq(SysNotifications::getId, record.getId())
                .set(SysNotifications::getFailCount, 0));
        log.info("通知已发送：businessType={}, businessId={}, broadcast={}, title={}",
                type.getCode(), event.getBusinessId(), broadcast, record.getTitle());
    }

    /** 事件 → 待插入的那一行（failCount 由调用方补：正常发送 0，先失败 1） */
    private SysNotifications.SysNotificationsBuilder buildRecord(NotificationEvent event) {
        NotificationTypeEnum type = event.getType();
        List<Long> userIds = clean(event.getUserIds());
        List<Long> deptIds = clean(event.getDeptIds());
        List<Long> roleIds = clean(event.getRoleIds());
        boolean broadcast = userIds.isEmpty() && deptIds.isEmpty() && roleIds.isEmpty();
        return SysNotifications.builder()
                .businessType(type.getCode())
                .category(type.getCategory())
                .title(cut(event.getTitle(), TITLE_MAX))
                .message(cut(event.getMessage(), MESSAGE_MAX))
                .link(cut(event.getLink(), LINK_MAX))
                .businessId(event.getBusinessId())
                .fromUserId(event.getFromUserId())
                .noticePush(broadcast ? null : buildNoticePush(userIds, deptIds, roleIds))
                .isBroadcast(broadcast ? 1 : 0)
                .sendCode(event.getSendCode())
                .delFlag(0)
                .failCount(0);
    }

    /** 按这一条通知的身份（sendCode）找库里那一行：重试、幂等重投都认它 */
    private SysNotifications findBySendCode(String sendCode) {
        if (sendCode == null || sendCode.trim().isEmpty()) {
            return null;
        }
        return baseMapper.selectOne(new LambdaQueryWrapper<SysNotifications>()
                .eq(SysNotifications::getSendCode, sendCode)
                .last("LIMIT 1"));
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
