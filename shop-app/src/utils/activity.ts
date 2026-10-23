/** 活动那三块（首页轮播、搜索页「活动」tab、活动详情页）共用的说法，口径照 shop-web/src/lib/activities.ts */

/** 标题没填回落活动名称，两格都空着给个兜底名 */
export function activityLabel(one: { title: string | null, activityName: string | null, id: number }) {
  return one.title?.trim() || one.activityName?.trim() || `活动 ${one.id}`
}

/**
 * 投放起止只是卡片上那一行说法，不是判据（后端只认 status=2 投放中），所以只到日、不显示秒：
 * 后端回的是 yyyy-MM-dd HH:mm:ss 定长串，截前 10 位就够；两格都可能没填，缺哪半截就说另外半截。
 */
export function activityTimeText(one: { startTime?: string | null, endTime?: string | null }) {
  const from = one.startTime?.slice(0, 10) ?? ''
  const to = one.endTime?.slice(0, 10) ?? ''
  if (from && to)
    return `${from} 至 ${to}`
  if (from)
    return `${from} 开始`
  if (to)
    return `截至 ${to}`
  return ''
}

/**
 * 活动跳转拼成 uni 能用的形状，三种结果分开给（判据和 Web 那份一致：id 只认纯数字、外链只认 http(s) 打头 ——
 * 这两格是后台手填的自由文本，拼进路径就能塞 javascript: 进来）：
 * - url：站内那一条，1 商品 → 商品详情页
 * - categoryId：2 分类 → 带去搜索页捞这一分类的商品
 * - external：4 外链，H5 直接开，小程序里开不了只能复制
 * 5 小程序页面（App 端没有那套 appid 映射）和 6 无跳转本来就是点不动，回个空对象。
 */
export interface ActivityTarget {
  url?: string
  categoryId?: number
  external?: string
}

export function activityTarget(one: { linkType: number | null, linkValue: string | null }): ActivityTarget {
  const raw = one.linkValue?.trim() ?? ''
  if (!raw) {
    return {}
  }
  switch (one.linkType) {
    case 1:
      return /^\d+$/.test(raw) ? { url: `/pages/goods/detail?id=${raw}` } : {}
    case 2:
      return /^\d+$/.test(raw) ? { categoryId: Number(raw) } : {}
    case 4:
      return /^https?:\/\//i.test(raw) ? { external: raw } : {}
    default:
      return {}
  }
}

/** 外链那一档在小程序里没法直接开（web-view 要配业务域名），退一步复制给用户 */
export function openExternal(url: string) {
  // #ifdef H5
  window.open(url, '_blank', 'noopener,noreferrer')
  // #endif
  // #ifndef H5
  uni.setClipboardData({
    data: url,
    success: () => uni.showToast({ title: '链接已复制，到浏览器打开', icon: 'none' }),
  })
  // #endif
}
