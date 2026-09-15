package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.entity.SysNotifications;
import com.shop.vo.notifications.NotificationsListVo;
import com.shop.vo.notifications.NotificationsReceiverVo;

import java.util.List;

/**
 * 消息通知 服务接口
 *
 * @author: quequnlong
 * @date: 2026/8/21
 * @description:
 */
public interface SysNotificationService extends IService<SysNotifications> {

    /**
     * 分页查询消息通知（管理端，可查看全部消息）
     *
     * @param notifications 查询条件（businessType/businessTypes/isRead）
     * @return 消息通知分页
     */
    IPage<NotificationsListVo> selectPage(SysNotifications notifications);

    /**
     * 批量删除消息通知
     *
     * @param ids 消息id集合
     * @return 是否成功
     */
    boolean deleteBatch(List<Long> ids);

    /**
     * 修改消息通知：更新主表（标题/内容/删除标记/推送对象）
     *
     * @param notifications 消息通知
     * @return 是否成功
     */
    boolean update(SysNotifications notifications);

    /**
     * 分页查询某消息的阅读记录（关联表 sys_notifications_receiver + 阅读人信息，可按阅读人昵称/用户名搜索）
     *
     * @param notificationId 消息id
     * @param keyword        阅读人搜索关键字（可空）
     * @return 阅读记录分页
     */
    IPage<NotificationsReceiverVo> listReceivers(Long notificationId, String keyword);

    /**
     * 按记录 id 批量标记已读
     *
     * @param ids 关联表记录id集合
     * @return 是否成功
     */
    boolean updateReceiversRead(List<Long> ids);

    /**
     * 按记录 id 批量删除（该接收人隐藏消息）
     *
     * @param ids 关联表记录id集合
     * @return 是否成功
     */
    boolean updateReceiversDeleted(List<Long> ids);
}
