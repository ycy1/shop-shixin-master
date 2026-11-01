import { COMMENT_TYPE_PARK, COMMENT_TYPE_PLAZA } from './comment';
import { apiGet, apiPost } from './api';

/**
 * 我的消息接口绑定（GET/POST /api/comm/shop/notification/*）。
 *
 * 走 /api/comm 而不是 /api/web/shop/profile/*：这一份是三端共用的收件箱（后台铃铛、App 消息页读的都是同五条），
 * 可见性判据只能有一份；路径上那层 /api 是前缀本身，和下单、评论那几条同一个写法。
 * 五条都要登录态（消息是私密的），没登录服务端回「请先登录后后再看消息」，由调用方呈现成未登录提示。
 */

interface Paged<T> {
  records: T[];
  total: number;
  pages: number;
  current: number;
}

export interface NotificationRow {
  id: number;
  /** 1 系统消息（公告 / 活动 / 培训 / 签收 / 徽章）、2 互动消息（回复 / 评价 / 点赞） */
  category: number | null;
  /** 业务档（notification_business_type）：notice / activity / training / receive / badge / comment / like / system */
  businessType: string | null;
  /** 指向那张业务单的 id：公告是 sys_notice.id、活动是 shop_activity.id、互动消息是那条评论的 id */
  businessId: number | null;
  /** 服务端 link 那一格：大多数档位留空，园区照片审核那一档带的是 web 那一页的路径（见下面 messageTarget） */
  link: string | null;
  /** 互动消息（comment/like）才有：源评论的类型 1商品 2门店 3品牌 4广场（拼跳转目标要用） */
  commentType: number | null;
  /** 互动消息才有：那条评论挂在哪个商品/门店/品牌/广场消息上 */
  commentBusinessId: number | null;
  title: string | null;
  message: string | null;
  fromNickname: string | null;
  /** 这一页里算好的已读：0 未读 1 已读（读的是当前登录人自己那一行 receiver） */
  isRead: number | null;
  createTime: string | null;
}

export interface UnreadSummary {
  total: number;
  systemCount: number;
  interactCount: number;
}

export type NotificationPage = Paged<NotificationRow>;

/** category 不传就是两档一起（0 在这里当「不限」用，由调用方剥掉） */
export function fetchMyNotifications(pageNum = 1, pageSize = 10, category?: number): Promise<NotificationPage> {
  return apiGet<NotificationPage>('/api/comm/shop/notification/list', {
    pageNum,
    pageSize,
    ...(category ? { category } : {}),
  });
}

/** 红点只看 total，两档各看自己那一格 —— 一条 SQL 的三列，一趟往返拿全 */
export function fetchUnreadSummary(): Promise<UnreadSummary> {
  return apiGet<UnreadSummary>('/api/comm/shop/notification/unread');
}

/** 点开一条就算已读（只对本人那一行生效，重复点也只会刷 read_time） */
export function readNotification(id: number): Promise<void> {
  return apiPost<void>(`/api/comm/shop/notification/read/${id}`);
}

/** 全部已读：category 给了只清那一档，不给两档一起 */
export function readAllNotifications(category?: number): Promise<void> {
  return apiPost<void>(`/api/comm/shop/notification/readAll${category ? `?category=${category}` : ''}`);
}

/** 删掉自己名下的一条（只是对自己隐藏，消息本身还在、别人照样看得见） */
export function deleteNotification(id: number): Promise<void> {
  return apiPost<void>(`/api/comm/shop/notification/delete/${id}`);
}

/**
 * 全部清空：服务端一条 UPDATE 铺完名下可见的那些，和 delete 一样只对自己隐藏。
 *
 * 不在前端按 id 循环调 deleteNotification：库在公网，一趟一个来回，几十条就是几十趟；
 * 而且循环只清得到面板捞到的那几页，「全部」二字对不上。
 */
export function deleteAllNotifications(category?: number): Promise<void> {
  return apiPost<void>(`/api/comm/shop/notification/deleteAll${category ? `?category=${category}` : ''}`);
}

/**
 * 这条消息点进去该去哪 —— 返回不带语言前缀的路径，调用方再过一次 href()。
 *
 * link 那一格大多数档位发送时留空（那一刻不知道该跳去哪个端的路由），由各端自己按 businessType 拼；
 * 但 link 非空时优先认它 —— 现在是三种来源：后台手工填的目标、园区照片审核那一档、
 * 潮玩集齐那一档（后两档是服务端拼的 web 路径，因为照片属于哪家园区、集齐的是哪条系列只有那一行数据知道，
 * App 没有这两页，它在 shop-app 的 utils/notification 里先把这两档排掉、只标已读）。
 * 回 null = 这一档前台没有对应页面（培训在门店页、签收在订单里、徽章和后台通知都没有独立详情页），
 * 调用方就把这一条只标已读、别把人带去一个 404。
 */
export function messageTarget(row: NotificationRow): string | null {
  if (row.link) return row.link;
  const id = row.businessId;
  if (!id) return null;
  switch (row.businessType) {
    // 公告正文是富文本，详情页要按 HTML 渲染（/notice/{id}）
    case 'notice':
      return `/notice/${id}`;
    case 'activity':
      return `/activity/${id}`;
    // 审核结论的 businessId 就是那条广场消息自己，直接进它的详情；
    // 待审核和已拒绝的那一条只有作者本人读得到（判据在 ShopPlazaReadService#detail），正好就是收到消息的这个人
    case 'plaza':
      return `/plaza/${id}`;
    // 集齐那一档的落地页由服务端拼好放在 link 里（/trendy/collection?trendyId=X）；
    // 这一句只捞发送那一版还没写 link 的旧消息，businessId 就是系列 id
    case 'collect':
      return `/trendy/collection?trendyId=${id}`;
    // 回复与点赞的 businessId 是那条评论的 id，挂在哪个商品/门店上要读列表另外带出的两列；
    // highlight 让「全部评论」页滚到那一条并高亮，不这么给的话人只能自己在几百条里找自己被赞的那条
    // 广场那一档例外：/comments 那一页只有评论没有正文，看广场消息的回复要去它自己那条底下（评论区挂在详情页里）
    // 公园那一档也例外：它的评论区就在园区详情页的第二个 tab 上，直接落过去比落「全部评论」那一页顺手
    case 'comment':
    case 'like':
      if (!row.commentType || !row.commentBusinessId) return null;
      if (row.commentType === COMMENT_TYPE_PLAZA) return `/plaza/${row.commentBusinessId}?highlight=${id}`;
      if (row.commentType === COMMENT_TYPE_PARK) {
        return `/trendy/park/${row.commentBusinessId}?tab=comment&highlight=${id}`;
      }
      return `/comments?type=${row.commentType}&id=${row.commentBusinessId}&highlight=${id}`;
    default:
      return null;
  }
}
