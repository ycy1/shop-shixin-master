import request from '@/utils/request'

// 获取消息通知列表
export function getNotificationListApi(params: any) {
  return request({
    url: '/sys/notification/list',
    method: 'get',
    params
  })
}

// 修改消息通知
export function updateNotificationApi(data: any) {
  return request({
    url: '/sys/notification/update',
    method: 'put',
    data
  })
}

// 删除消息通知（逻辑删除）
export function deleteNotificationApi(ids: any) {
  return request({
    url: `/sys/notification/delete/${ids}`,
    method: 'delete'
  })
}

// 查询消息阅读记录（关联表，分页，可按阅读人搜索）
export function getNotificationReceiverListApi(notificationId: number, params: any = {}) {
  return request({
    url: '/sys/notification/receiver/list',
    method: 'get',
    params: { notificationId, ...params }
  })
}

// 阅读记录：标记已读
export function updateNotificationReceiverReadApi(ids: any[]) {
  return request({
    url: '/sys/notification/receiver/read',
    method: 'put',
    data: ids
  })
}

// 阅读记录：删除（该接收人隐藏消息）
export function deleteNotificationReceiverApi(ids: any[]) {
  return request({
    url: '/sys/notification/receiver/delete',
    method: 'put',
    data: ids
  })
}
