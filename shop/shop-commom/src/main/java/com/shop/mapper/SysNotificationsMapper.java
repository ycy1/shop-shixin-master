package com.shop.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.entity.SysNotifications;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.vo.notifications.NotificationsListVo;
import com.shop.vo.notifications.NotificationsReceiverVo;
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
     * 分页查询消息通知（当前用户可见的：全员广播的 + 接收人展开到自己名下且自己没删的；已读看 sys_notifications_receiver.is_read）
     *
     * @param page          分页参数
     * @param notifications 查询条件（category、businessType、businessTypes）
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

    /**
     * 当前用户的未读汇总：一趟捞回「总数 + 系统消息 + 互动消息」三格（前台消息中心两档 tab 和头上那颗红点共用这一趟）
     *
     * @return 一行三列：total、system_num、interact_num
     */
    Map<String, Object> countUnreadSummary(@Param("userId") Long userId);

    /**
     * 标记已读：存在接收人记录则更新 is_read=1，否则插入（幂等，upsert）
     *
     * @return 受影响行数（<=0 表示主消息不存在）
     */
    int doRead(@Param("userId") Long userId, @Param("notificationId") Long notificationId);

    /**
     * 全部标记已读：把当前用户可见的未读消息批量补上已读的接收人记录
     *
     * @param category 只清这一档（1 系统 / 2 互动），null 表示两档一起
     */
    int allRead(@Param("userId") Long userId, @Param("category") Integer category);

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

    /**
     * 发送时展开接收人：把 userIds / deptIds（sys_dept_user）/ roleIds（sys_user_role）三档命中的启用账号一趟铺成
     * sys_notifications_receiver 的未读记录（insert ignore + uk_message_user 天然去重，同一个人既在部门又在角色也只落一行）。
     * 三格全空不能调这一条 —— 那样等于铺全表，全员广播走主表 is_broadcast，不落接收人。
     *
     * @return 铺出来的接收人行数（撞唯一键被忽略的不计）
     */
    int fanOutReceivers(@Param("notificationId") Long notificationId,
                        @Param("sendCode") String sendCode,
                        @Param("userIds") List<Long> userIds,
                        @Param("deptIds") List<Long> deptIds,
                        @Param("roleIds") List<Long> roleIds);

}
