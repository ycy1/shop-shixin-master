import { COMMENT_TYPE_PLAZA } from '@/api/comment'
import type { IShopNotificationRow } from '@/api/types/shop'

/**
 * 这条消息点进去该去哪 —— 拼的是 App 自己的路由，交给 uni.navigateTo。
 *
 * 服务端 link 那一格九档全留空（发送那一刻不知道该端的路由长什么样），所以跳转由各端按 businessType 自己拼；
 * 但 link 非空时优先认它：那是后台手工填的目标，这里拼的那份只是兜底。
 * 回空串 = 这一档 App 上没有对应页面（培训在门店页、签收在订单里、徽章和后台通知都没有独立详情页），
 * 调用方就把这一条只标已读、别把人带去一个空页。
 */
export function messageTarget(row: IShopNotificationRow): string {
  if (row.link) {
    return row.link
  }
  const id = row.businessId
  if (!id) {
    return ''
  }
  switch (row.businessType) {
    // 公告正文是富文本，得进详情页按 HTML 渲染（消息列表里那一格只剩纯文本）
    case 'notice':
      return `/pages/notice/detail?id=${id}`
    case 'activity':
      return `/pages/activity/detail?id=${id}`
    // 审核结论的 businessId 就是那条广场消息自己，直接进它的详情；
    // 待审核和已拒绝的那一条只有作者本人读得到，正好就是收到这条消息的人
    case 'plaza':
      return `/pages/plaza/detail?id=${id}`
    // 回复与点赞的 businessId 是那条评论的 id，挂在哪个商品/门店上要读列表另外带出的两列；
    // highlight 让「全部评论」页滚到那一条并高亮，不这么给的话人只能自己在几百条里找自己被赞的那条
    // 广场那一档例外：comment/list 那一页只有评论没有正文，看广场消息的回复要去它自己那条底下（评论区挂在详情页里）
    case 'comment':
    case 'like':
      if (!row.commentType || !row.commentBusinessId) {
        return ''
      }
      return row.commentType === COMMENT_TYPE_PLAZA
        ? `/pages/plaza/detail?id=${row.commentBusinessId}&highlight=${id}`
        : `/pages/comment/list?type=${row.commentType}&id=${row.commentBusinessId}&highlight=${id}`
    default:
      return ''
  }
}
