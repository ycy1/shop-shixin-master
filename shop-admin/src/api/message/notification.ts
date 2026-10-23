import request from '@/utils/request'

// 获取消息通知列表
export function getNotificationListApi(params: any) {
  return request({
    url: '/sys/notification/list',
    method: 'get',
    params
  })
}

// 新增消息通知（管理端手工发消息，后端写发送人并生成 send_code）
export function addNotificationApi(data: any) {
  return request({
    url: '/sys/notification/add',
    method: 'post',
    data
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

/**
 * 下面五条是**当前登录人自己的收件箱**（导航栏铃铛用的那五条），和上面那组不是一回事：
 * 上面是后台代客发消息、看阅读数（/sys/notification/**，按菜单权限走），这里是「我名下有哪几条、读过没有」。
 * 走 /api/comm 是因为前台两端读的是同一份判据（ShopNotificationCommonController），路径多出来的那层 /api
 * 是前缀本身，道理和「确认收货」那条一样，见 ../consumer/orderItem.ts 的注释。
 */

// 我的消息列表（未读打头；category 1 系统 / 2 互动，不传两档一起）
export function getMyNotificationListApi(params: { category?: number; pageNum?: number; pageSize?: number } = {}) {
  return request({
    url: '/api/comm/shop/notification/list',
    method: 'get',
    params
  })
}

// 我的未读汇总：{ total, systemCount, interactCount }，铃铛上那颗红点只看 total
export function getMyNotificationUnreadApi() {
  return request({
    url: '/api/comm/shop/notification/unread',
    method: 'get'
  })
}

// 点开一条就算已读（只对本人那行生效，幂等）
export function readMyNotificationApi(id: number) {
  return request({
    url: `/api/comm/shop/notification/read/${id}`,
    method: 'post'
  })
}

// 全部已读：category 传了就只清那一档，不传两档一起（铃铛那颗按钮不分类别）
export function readAllMyNotificationApi(category?: number) {
  return request({
    url: '/api/comm/shop/notification/readAll',
    method: 'post',
    params: category ? { category } : {}
  })
}

// 删掉自己名下的一条（只是对自己隐藏，别的人才看不见）
export function deleteMyNotificationApi(id: number) {
  return request({
    url: `/api/comm/shop/notification/delete/${id}`,
    method: 'post'
  })
}
