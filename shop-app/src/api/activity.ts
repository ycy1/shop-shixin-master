import type { IPageRes, IShopActivityDetail, IShopActivityRow, IShopSigninRow } from './types/shop'
import { http } from '@/http/http'

/** 字典 activity_event_type：1 曝光 */
export const ACTIVITY_EVENT_VIEW = 1

/** 字典 activity_event_type：2 点击 */
export const ACTIVITY_EVENT_CLICK = 2

/**
 * 活动（App 端读接口）。判据钉在 ShopSearchReadService 里，和 Web 前台那两条同源 ——
 * 只给投放中的（status=2），按权重排，keyword 命中名称或标题都行；这一条不挑图，文字类活动也要搜得到。
 */
export function getActivityPage(query: { keyword?: string, pageNum?: number, pageSize?: number }) {
  return http.get<IPageRes<IShopActivityRow>>('/api/app/shop/activity/list', query)
}

/** 活动详情：全站只有这一条带 content（后台富文本），撤下/结束/没这个 id 回同一句话 */
export function getActivityDetail(id: number) {
  return http.get<IShopActivityDetail>(`/api/app/shop/activity/${id}`)
}

/**
 * 埋点：曝光和点击共用这一条，只传 id + 档位，谁触发的、ip、系统全由服务端现取（这条不鉴权，前端能写的字段等于谁都能填）。
 * 它是两端共用的动作，所以留 /api/comm，不在 app 包再开一条。
 * 发出去就不管回包：报数失败不该把活动位变成红框，更不该挡住跳转。
 */
export function trackActivity(activityId: number, eventType: number) {
  return http
    .post<void>('/api/comm/activity/track', { activityId, eventType }, undefined, undefined, { hideErrorToast: true })
    .catch(() => {})
}

/**
 * 签今天：落一行 + 给积分 + 记一笔流水，三件在一个事务里，判据全在服务端。
 * 这是两端共用的**动作**（后台日历上那颗补签压的是另一条真权限），所以留 /api/comm，不在 app 包再开一条。
 * 参数一个都没有 —— 能签的只有自己；回的就是刚落下来的那一天（类型 / 这一天给的分 / 连签几天）。
 * 今天已经签过会被拒，拒的那句话由 http 层 toast，页面不用另写一套。
 */
export function signinToday() {
  return http.post<IShopSigninRow>('/api/comm/activity/signin')
}

/**
 * 本人补签：把漏签的那一天补上。同样是两端共用的**动作**，留 /api/comm。
 * 参数只认 signDate（userId 传了也不认，服务层钉登录 id），判据全在服务层：
 * 只能补今天之前、且最近 7 天之内的日子（那颗天数和日历上钉的是同一颗数），那一天还没过完就归「签今天」那条管；
 * 已经签过的日子再点会被拒（「这一天已经签过了，不用补」）。补签给的和正常签到一样多，累计天数也算一天。
 * 拒回来的那句由 http 层 toast，页面只把它再留一份在格子上。
 */
export function signinMakeup(signDate: string) {
  return http.post<IShopSigninRow>('/api/comm/activity/signin/makeup', { signDate })
}
