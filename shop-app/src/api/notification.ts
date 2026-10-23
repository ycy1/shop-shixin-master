import type { IPageRes, IShopNotificationRow, IShopNotificationUnread } from './types/shop'
import { http } from '@/http/http'

/**
 * 我的消息（App 端）。
 *
 * 走 /api/comm 而不是 /api/app：这一份是三端共用的收件箱 —— 后台铃铛、shop-web 消息中心、这里读的都是同五条端点，
 * 「哪些消息对我可见 / 我读过没」那套判据只能有一份，分叉了就会出现后台看着已读、App 上还挂着未读。
 * 路径上那层 /api 是前缀本身（和下单、评论、充值那几条同一个写法）。
 *
 * 五条都要登录态：/api/** 不过 Sa-Token 拦截器，「必须登录」由服务层第一句 requireLoginUser 当场拒掉，
 * 没登录回业务错、http 层 toast；页面自己先用 token store 拦一道，免得白报一次。
 * 每一条都不收 userId —— 传了也只认登录 id，能点名别人就等于看别人的收件箱。
 */

/** 某一档名下的分页：category 不给就是两档都要（1 系统 / 2 互动） */
export function getMyNotifications(pageNum = 1, pageSize = 10, category?: number) {
  return http.get<IPageRes<IShopNotificationRow>>('/api/comm/shop/notification/list', { pageNum, pageSize, category })
}

/** 未读汇总：红点读 total，tab 上那两个数读另外两格 */
export function getMyNotificationUnread() {
  return http.get<IShopNotificationUnread>('/api/comm/shop/notification/unread')
}

/** 读一条：幂等 upsert，重复点只是把已读时间往后挪 */
export function readMyNotification(id: number) {
  return http.post<void>(`/api/comm/shop/notification/read/${id}`)
}

/** 全部已读：category 给就只清那一档；这里不传，一次清掉两档的所有未读 */
export function readAllMyNotification(category?: number) {
  return http.post<void>('/api/comm/shop/notification/readAll', undefined, category ? { category } : undefined)
}

/** 删一条：只是对自己隐藏（receiver 那一行翻 is_deleted），别人的、后台真删都动不到 */
export function deleteMyNotification(id: number) {
  return http.post<void>(`/api/comm/shop/notification/delete/${id}`)
}
