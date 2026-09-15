package com.shop.utils;

import com.shop.entity.SysNotifications;
import com.shop.mapper.SysNotificationsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 通知工具类
 */
@Slf4j
@Service("notificationsUtil")
@RequiredArgsConstructor
public class NotificationsUtil {

    private final EmailUtil emailUtil;

    private final SysNotificationsMapper baseMapper;

    public void publish(SysNotifications sysNotifications) {
        log.info("发布通知：businessType={}, businessId={}, title={}",
                sysNotifications.getBusinessType(), sysNotifications.getBusinessId(), sysNotifications.getTitle());
        try{
            switch (sysNotifications.getBusinessType()) {
                case "notice":
                    // 公告通知所有人：notice_push 保持 null（或全空数组）即全员可见
                    emailUtil.send("2039916844@qq.com", "公告通知【"+ sysNotifications.getTitle()+"】", sysNotifications.getMessage());
                    log.info("公告发布邮件发送：{}", sysNotifications.getTitle());
                    break;
                default:
                    // 其他类型通知，notice_push 已由调用方显式构造
                    break;
            }
            // 发送批次标识：每次发布生成 32 位 uuid（去掉连字符），重新发送时由管理端生成新值
            if (sysNotifications.getSendCode() == null || sysNotifications.getSendCode().isEmpty()) {
                sysNotifications.setSendCode(UUID.randomUUID().toString().replace("-", ""));
            }
            baseMapper.insert(sysNotifications);
        }catch (Exception e){
            log.error("通知发布失败：businessType={}, businessId={}, title={}",
                    sysNotifications.getBusinessType(), sysNotifications.getBusinessId(), sysNotifications.getTitle());
            log.error("通知发布失败:{}", e.getMessage());
        }
    }

    /**
     * 构造推送对象 notice_push JSON：{"user":[],"dept":[],"role":[]}
     * 三数组全空（或返回 null）视为全员可见（公告等广播场景）
     *
     * @param userId  定向推送的用户id（单接收人场景，如点赞/评论回复）
     * @param deptIds 定向推送的部门id集合
     * @param roleIds 定向推送的角色id集合
     * @return notice_push JSON 字符串
     */
    public static String buildNoticePush(Long userId, List<Long> deptIds, List<Long> roleIds) {
        StringBuilder sb = new StringBuilder("{\"user\":[");
        if (userId != null) {
            sb.append(userId);
        }
        sb.append("],\"dept\":[");
        appendIds(sb, deptIds);
        sb.append("],\"role\":[");
        appendIds(sb, roleIds);
        sb.append("]}");
        return sb.toString();
    }

    /**
     * 构造单用户定向的 notice_push
     */
    public static String buildNoticePush(Long userId) {
        return buildNoticePush(userId, null, null);
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

}
