package com.shop.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.entity.SysNotifications;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.vo.notifications.NotificationsListVo;
import com.shop.vo.notifications.NotificationsReceiverVo;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 消息通知表 Mapper接口
 */
@Mapper
public interface SysNotificationsMapper extends BaseMapper<SysNotifications> {
    /**
     * 分页查询消息通知（当前用户可见的，按 notice_push 判断接收人，按 sys_notifications_receiver 判断已读）
     *
     * @param page          分页参数
     * @param notifications 查询条件
     * @param userId        当前登录用户id
     * @return 消息通知分页
     */
    IPage<NotificationsListVo> selectNotificationsPage(@Param("page") Page<Object> page, @Param("notifications") SysNotifications notifications, @Param("userId") Long userId);

    /**
     * 管理端分页查询消息通知（不带可见性过滤，可查看全部；透出 notice_push 供展示接收对象）
     */
    IPage<NotificationsListVo> selectNotificationAdminPage(@Param("page") Page<Object> page, @Param("notifications") SysNotifications notifications);

    /**
     * 查询消息详情（按当前用户计算已读状态）
     */
    NotificationsListVo selectNotificationsById(@Param("id") Long id, @Param("userId") Long userId);

    @MapKey("type")
    Map<String, Integer> getUnReadNum(@Param("userId") long userId);

    /**
     * 当前用户的未读消息数量（>0 表示有未读）
     */
    long countMyUnread(@Param("userId") long userId);

    /**
     * 标记已读：存在已读记录则更新 is_read=1，否则插入（幂等，upsert）
     *
     * @return 受影响行数（<=0 表示主消息不存在）
     */
    int doRead(@Param("userId") Long userId, @Param("notificationId") Long notificationId);

    /**
     * 全部标记已读：把当前用户可见的未读消息批量插入 receiver 记录
     */
    int allRead(@Param("userId") Long userId);

    /**
     * 接收人删除消息：upsert receiver 记录 is_deleted=1（仅对当前用户隐藏）
     *
     * @return 受影响行数（<=0 表示主消息不存在）
     */
    int deleteByUser(@Param("userId") Long userId, @Param("notificationId") Long notificationId);

    /**
     * 分页查询某消息的阅读记录（关联表 sys_notifications_receiver + 阅读人信息，可按阅读人昵称/用户名搜索）
     *
     * @param page           分页参数
     * @param notificationId 消息id
     * @param keyword        阅读人搜索关键字（昵称/用户名模糊匹配，可空）
     * @return 阅读记录分页
     */
    IPage<NotificationsReceiverVo> selectReceiverList(@Param("page") Page<Object> page,
                                                      @Param("notificationId") Long notificationId,
                                                      @Param("keyword") String keyword);

    /**
     * 按记录 id 批量标记已读（is_read=1、read_time=now、is_deleted=0）
     *
     * @param ids 关联表记录id集合
     * @return 受影响行数
     */
    int updateReceiverReadByIds(@Param("ids") List<Long> ids);

    /**
     * 按记录 id 批量删除（is_deleted=1，该接收人隐藏消息）
     *
     * @param ids 关联表记录id集合
     * @return 受影响行数
     */
    int updateReceiverDeletedByIds(@Param("ids") List<Long> ids);

    /**
     * 重新发送前清空某消息的所有接收记录（新一轮发送从零开始）
     *
     * @param notificationId 消息id
     * @return 受影响行数
     */
    int deleteReceiverByNotificationId(@Param("notificationId") Long notificationId);

}
