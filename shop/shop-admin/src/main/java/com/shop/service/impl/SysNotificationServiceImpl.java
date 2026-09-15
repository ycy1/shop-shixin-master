package com.shop.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.SysNotifications;
import com.shop.mapper.SysNotificationsMapper;
import com.shop.service.SysNotificationService;
import com.shop.utils.PageUtil;
import com.shop.vo.notifications.NotificationsListVo;
import com.shop.vo.notifications.NotificationsReceiverVo;
import lombok.RequiredArgsConstructor;
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

    @Override
    public IPage<NotificationsListVo> selectPage(SysNotifications notifications) {
        return baseMapper.selectNotificationAdminPage(PageUtil.getPage(), notifications);
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
        return updateById(notifications);
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
