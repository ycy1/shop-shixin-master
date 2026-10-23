import type { IShopNoticeDetail } from './types/shop'
import { http } from '@/http/http'

/**
 * 公告详情（App 端）：GET /api/app/shop/notice/{id}，和 Web 前台那条走的是同一个 service 的同一个方法
 *（判据只认 is_show=1，撤下/没这个 id 回同一句话），这一条只搬路径。
 *
 * 只有这一条带 content；消息列表里那一格是发的时候剥成纯文本的正文，看全文只能进这一页。
 * 不要登录 —— 公告本来就是发给大家看的，所以页面自己不用先拦登录态。
 */
export function getNoticeDetail(id: number) {
  return http.get<IShopNoticeDetail>(`/api/app/shop/notice/${id}`)
}
