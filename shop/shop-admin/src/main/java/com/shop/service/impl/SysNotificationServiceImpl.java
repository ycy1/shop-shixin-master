package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.SysNotifications;
import com.shop.exception.ServiceException;
import com.shop.mapper.SysNotificationsMapper;
import com.shop.service.SysNotificationService;
import com.shop.utils.NotificationsUtil;
import com.shop.utils.PageUtil;
import com.shop.vo.notifications.NotificationsListVo;
import com.shop.vo.notifications.NotificationsReceiverVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 消息通知 服务实现类
 *
 * @author: quequnlong
 * @date: 2026/8/21
 * @description:
 */
@Service
@RequiredArgsConstructor
public class SysNotificationServiceImpl extends ServiceImpl<SysNotificationsMapper, SysNotifications> implements SysNotificationService {

    /**
     * 前台认的是 sys_notifications_receiver 里那几行，不再解析 notice_push 那串 JSON（可见性谓词已改），
     * 所以这一页新增/修改完必须把发送范围展开成人 —— 不铺就谁也看不见这条消息。
     */
    private final NotificationsUtil notificationsUtil;

    @Override
    public IPage<NotificationsListVo> selectPage(SysNotifications notifications) {
        return baseMapper.selectNotificationAdminPage(PageUtil.getPage(), notifications);
    }

    @Override
    public boolean insert(SysNotifications notifications) {
        // 这三列在 sys_notifications 里是 NOT NULL，缺了会在 INSERT 才报错，提前拦
        if (StringUtils.isBlank(notifications.getBusinessType())) {
            throw new ServiceException("业务类型不能为空");
        }
        if (StringUtils.isBlank(notifications.getTitle())) {
            throw new ServiceException("标题不能为空");
        }
        if (StringUtils.isBlank(notifications.getMessage())) {
            throw new ServiceException("消息内容不能为空");
        }
        // 发送人取当前登录用户：列表那列"发送人"就是按 from_user_id 联 sys_user.nickname 出来的
        notifications.setFromUserId(StpUtil.getLoginIdAsLong());
        // 新增即发布：生成首个发送批次（32位uuid），app 端按 send_code 判定未读，口径同 NotificationsUtil.publish
        notifications.setSendCode(UUID.randomUUID().toString().replace("-", ""));
        if (notifications.getDelFlag() == null) {
            notifications.setDelFlag(0);
        }
        if (!save(notifications)) {
            return false;
        }
        // 有了 id 才铺得下接收人行；category / is_broadcast 也在这一步按 notice_push 认出来回写
        notificationsUtil.expandReceivers(notifications);
        return true;
    }

    @Override
    public boolean deleteBatch(List<Long> ids) {
        // 逻辑删除：del_flag 置 1（管理端仍可查看，app 端过滤）
        return lambdaUpdate()
                .in(SysNotifications::getId, ids)
                .set(SysNotifications::getDelFlag, 1)
                .update();
    }

    @Override
    public boolean update(SysNotifications notifications) {
        if (Boolean.TRUE.equals(notifications.getSend())) {
            // 重新发送：生成新的发送批次 send_code（32位uuid），并清空旧的接收记录（新一轮发送从零开始）
            notifications.setSendCode(UUID.randomUUID().toString().replace("-", ""));
            if (notifications.getId() != null) {
                baseMapper.deleteReceiverByNotificationId(notifications.getId());
            }
        }
        // 管理端只更新主表常规字段（标题/内容/删除标记/推送对象），不再管理已读状态
        if (!updateById(notifications)) {
            return false;
        }
        // 重新按 notice_push 铺一遍范围。回读库里这一行是必要的：updateById 收的是表单那几列的稀疏实体，
        // notice_push / send_code 未必在里面，而铺人和回写 category 认的正是这两格
        if (notifications.getId() != null) {
            SysNotifications one = getById(notifications.getId());
            if (one != null) {
                notificationsUtil.expandReceivers(one);
            }
        }
        return true;
    }

    @Override
    public IPage<NotificationsReceiverVo> listReceivers(Long notificationId, String keyword) {
        return baseMapper.selectReceiverList(PageUtil.getPage(), notificationId, keyword);
    }

    @Override
    public boolean updateReceiversRead(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        return baseMapper.updateReceiverReadByIds(ids) > 0;
    }

    @Override
    public boolean updateReceiversDeleted(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        return baseMapper.updateReceiverDeletedByIds(ids) > 0;
    }
}
