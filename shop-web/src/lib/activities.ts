import { fill, type Dict } from '@/i18n';
import { apiGet, apiPost } from './api';
import type { WebPage } from './search';

/**
 * 首页那一块活动轮播（GET /api/web/shop/search/activities）+ 活动列表页那张卡片
 * （GET /api/web/shop/activity/list，两份共用同一条 Java VO，只有图那一格的可空性不一样，见下面 WebActivityRow）。
 *
 * 后端只回「投放中」的那几条，字段也刻意收得很窄：
 * content（后台 WangEditor 存的 HTML）不给 —— 轮播和列表都不渲染富文本，
 * 活动位要的是那张图，把 HTML 递到不要登录的端点上等于给自己留一个 XSS。只有详情那一条带它（见 WebActivityDetail）。
 */
export interface WebActivity {
  id: number;
  /** 活动名称：标题没填时页面拿这一格兜底 */
  activityName: string | null;
  title: string | null;
  /** 活动图片：首页轮播那条接口按「配了图」筛过，所以这一档必不为空（列表那条不挑图，另见 WebActivityRow） */
  image: string;
  /** 字典 link_type：1商品 2分类 4外链 5小程序页面 6无跳转（3 跳另一条活动，已下线） */
  linkType: number | null;
  /** 商品ID / 分类ID / URL / 小程序页面路径，按 linkType 分档，库里存的就是一个字符串 */
  linkValue: string | null;
  /** 投放起止只是卡片上那一行说法，不是判据（后端只认 status=2 投放中）；首页轮播那条接口不选这两格 */
  startTime?: string | null;
  endTime?: string | null;
}

/** 字典 activity_event_type：1 曝光 */
export const ACTIVITY_EVENT_VIEW = 1;

/** 字典 activity_event_type：2 点击 */
export const ACTIVITY_EVENT_CLICK = 2;

export function fetchActivities(): Promise<WebActivity[]> {
  return apiGet<WebActivity[]>('/api/web/shop/search/activities');
}

/** 活动列表页那一屏的行：接口不挑图（文字类活动也要能被看见），所以这一份把 image 收成可空 */
export type WebActivityRow = Omit<WebActivity, 'image'> & { image: string | null };

/** 卡片上那一行大字：标题没填回落活动名称，两格都空着给个兜底名（列表页和搜索页那一 tab 同一个说法） */
export function activityLabel(one: { title: string | null; activityName: string | null; id: number }, d: Dict): string {
  return one.title?.trim() || one.activityName?.trim() || fill(d.activity.fallback, { n: one.id });
}

/**
 * 投放起止只是卡片上那一行说法，不是判据（后端只认 status=2 投放中），所以只到日、不显示秒。
 * 后端回的是 yyyy-MM-dd HH:mm:ss 定长串，截前 10 位就够；两格都可能没填，缺哪半截就说另外半截。
 * 日子本身照原样念（yyyy-MM-dd 四档都看得懂），只有「至/开始/截至」那几个字跟着语种走。
 */
export function activityTimeText(
  one: { startTime?: string | null; endTime?: string | null },
  d: Dict,
): string {
  const from = one.startTime?.slice(0, 10) ?? '';
  const to = one.endTime?.slice(0, 10) ?? '';
  if (from && to) return fill(d.activity.between, { a: from, b: to });
  if (from) return fill(d.activity.from, { a: from });
  if (to) return fill(d.activity.to, { b: to });
  return '';
}

/** 跳转只要这两格，所以 activityTarget 收到这一份窄类型上 —— 列表、轮播、详情三份共用它 */
export interface ActivityLink {
  linkType: number | null;
  linkValue: string | null;
}

/**
 * 活动列表页那一屏 + 搜索结果页那个「活动」tab 共用（只给投放中的，按权重排）；
 * 分页吃 pageNum/pageSize，和商品/门店那几条一套。
 * keyword 是那一 tab 传的关键字（后端按名称或标题命中都行），不传就是不限 —— undefined 不进查询串（axios 把它跳掉）。
 */
export function fetchActivityPage(pageNum: number, pageSize: number, keyword?: string): Promise<WebPage<WebActivityRow>> {
  return apiGet<WebPage<WebActivityRow>>('/api/web/shop/activity/list', { pageNum, pageSize, keyword });
}

/**
 * 活动详情页那一条。全站只有这一条接口带 content（后台 WangEditor 存的 HTML），
 * 渲染前必须过 sanitizeRichText —— 这一格是后台自由文本，直接 v-html 等于把脚本注进不要登录的 C 端页面。
 */
export interface WebActivityDetail {
  id: number;
  activityName: string | null;
  title: string | null;
  /** 详情页这一条也不挑图，没配图的活动就是空 */
  image: string | null;
  videoUrl: string | null;
  content: string | null;
  linkType: number | null;
  linkValue: string | null;
  startTime: string | null;
  endTime: string | null;
}

/** 撤下了、结束了、根本没这个 id —— 后端回同一句「活动不存在或没在投放中」，这一页直接把那句话当提示用 */
export function fetchActivityDetail(id: number): Promise<WebActivityDetail> {
  return apiGet<WebActivityDetail>(`/api/web/shop/activity/${id}`);
}

/**
 * 埋点：一次曝光、一次点击各往 shop_activity_click 里插一行（POST /api/comm/activity/track）。
 *
 * 只传 activityId + eventType 两格，谁触发的、ip、系统全由服务端现取（这一条不鉴权，前端能写的字段就等于谁都能填）。
 * 发出去就不管回包：报数失败不该把活动位变成红框，更不该挡住跳转。
 */
export function trackActivity(activityId: number, eventType: number): void {
  apiPost<void>('/api/comm/activity/track', { activityId, eventType }).catch(() => {});
}

/**
 * 活动那条跳转拼成页面能用的形状，三种结果分开给：
 * - href：能当链接点的（1 商品 → 详情页；4 外链 → 原样，但只放 http(s) 打头的）
 * - categoryId：2 分类。首页的筛选栏就在同一块 island 里，直接把它选上就行，不绕 URL
 *   （绕 URL 就得处理「已经在首页了再点一次」，那条链接 ClientRouter 根本不会重新挂载组件）
 * - 两个都没有：点不动。5 小程序页面前台没有对应页，6 无跳转本来就是不动，
 *   加上 link_value 手填得不成形的（商品/分类那两档不是纯数字、外链那档不是 http(s) 开头）
 *
 * 外链那一道前缀白名单是必须的：这一格是后台手填的自由文本，
 * 拼进 href 就能塞 javascript: 进来。id 只认纯数字同理 —— 拼出来的是路径片段。
 */
export interface ActivityTarget {
  href?: string;
  categoryId?: number;
}

export function activityTarget(activity: ActivityLink): ActivityTarget {
  const raw = activity.linkValue?.trim() ?? '';
  if (!raw) return {};
  switch (activity.linkType) {
    case 1:
      return /^\d+$/.test(raw) ? { href: `/product/${raw}` } : {};
    case 2:
      return /^\d+$/.test(raw) ? { categoryId: Number(raw) } : {};
    case 4:
      return /^https?:\/\//i.test(raw) ? { href: raw } : {};
    default:
      return {};
  }
}
