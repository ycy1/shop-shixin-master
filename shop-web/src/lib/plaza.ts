import { apiGet, apiPost, apiUpload } from './api';
import type { WebPage } from './search';

/**
 * 广场（前台「广场」那一页 + 每条消息的详情页）用到的接口绑定。
 *
 * 读两条各端一份（Web 这条 /api/web/shop/plaza/...，App 那条 /api/app/...，两边判据同一份 service）；
 * 发布/改自己那条/删自己那条/点赞/记一次分享是「两端同一个动作」，走 /api/comm/shop/plaza/...，调 comm 要写全路径
 *（request 的 baseURL 会再叠一层 /api，口径同 lib/comment 里那三颗）。
 *
 * 广场那一页只回 status=1 且 audit_status=1 的那一些（这一刀压在 SQL 里，前端传什么都到不了查询串），
 * 所以列表里不会出现待审核的东西；两处例外：
 * 详情那一条作者本人读得到自己那一条待审核/已拒绝的，页面按 auditStatus 给「还没上广场」那一行说法；
 * my/list 那一条整个不拦审核状态 —— 个人中心那一档看的正是自己还没过审、被拒掉的那几条。
 */

/** 关联类型（字典 plaza_relation_type）：relationId 指向哪张表由它定，0 就是没挂任何东西 */
export const PLAZA_RELATION_NONE = 0;
export const PLAZA_RELATION_PRODUCT = 1;
export const PLAZA_RELATION_STORE = 2;
export const PLAZA_RELATION_BRAND = 3;
export const PLAZA_RELATION_ACTIVITY = 4;

/** 审核状态（字典 plaza_audit_status）：0 发布落库那一档，1 才上广场，2 是被判下来的 */
export const PLAZA_AUDIT_PENDING = 0;
export const PLAZA_AUDIT_PASSED = 1;
export const PLAZA_AUDIT_REJECTED = 2;

/** 分享渠道（字典 share_channel）：网页端没有分享 SDK，复制链接就是 5 这一档 */
export const PLAZA_SHARE_LINK = 5;

/** 发布通过审核后才给分，这个数只在页面上说一句话说服用户「发完为什么没动静」，判据在服务端 */
export const PLAZA_PUBLISH_POINTS = 10;

/** 列表和详情共用的一行（后端回的就是 ShopPlazaMessage 实体，列表那条 SQL 不取 content 那一列） */
export interface PlazaMessage {
  id: number;
  userId: number | null;
  /** 发布者昵称：联 sys_user 带出，账号删了就是空，页面按 id 兜底 */
  userName: string | null;
  /** 关联类型：0无关联 1商品 2门店 3品牌 4活动 */
  relationType: number | null;
  /** 关联对象ID，按 relationType 指向四张表；名字不冗余，靠 relationName 联出来 */
  relationId: number | null;
  /** 关联对象名称：对象被删了就是空 */
  relationName: string | null;
  /** 标题可空，正文才是必填 */
  title: string | null;
  /** 富文本正文（HTML）：只有详情那一条带，列表里这一格根本不在 JSON 里 */
  content?: string | null;
  /** 服务端从正文剥标签算出来的摘要，列表预览读的就是它 */
  contentText: string | null;
  /** 广场消息图：发布人自己上传的图，一格里存若干个地址用英文逗号隔开（可空）。卡片配图取第一张 */
  plazaImage: string | null;
  isTop: number | null;
  isRecommend: number | null;
  /** 三个计数一律读明细现算，前端只念不回写 */
  likeCount: number | null;
  commentCount: number | null;
  shareCount: number | null;
  auditStatus: number | null;
  /** 判「拒绝」时后台写给发布者看的那一句，前台只有作者本人那一趟读得到 */
  auditRemark: string | null;
  status: number | null;
  /** 审核通过那一刻由服务端盖的时间；没通过就是空 */
  publishTime: string | null;
  createTime?: string | null;
  /** 我赞过没有：登录时列表/详情才带回这一格，未登录是 undefined */
  selfLiked?: boolean | null;
}

/** 发布那一格表单：只有这五格是前端说了算的（其余由服务端按登录账号和判据钉） */
export interface PlazaPublishForm {
  title?: string;
  content: string;
  /** 广场消息图：发布人自己上传的图，若干个地址按上传顺序用英文逗号拼在这一格（走 uploadPlazaImage 传上来）；不填就是没图。
   *  修改时这一格每次都照表单写 —— 想留住原来那些图，得先把读回来的那一串带上去。 */
  plazaImage?: string;
  /** 关联类型：不填按 0 无关联；填了 1/2/3/4 就必须一并给出 relationId */
  relationType?: number;
  /** 关联对象ID，按 relationType 指向商品/门店/品牌/活动那张表 */
  relationId?: number;
}

/** 修改那份表单：形状同发布，多一个 id（只能改自己的，改完服务端打回待审核） */
export interface PlazaUpdateForm extends PlazaPublishForm {
  id: number;
}

/**
 * 广场那一页一次十条往下捞（分页吃查询串里的 pageNum/pageSize，「一次 10 条」是前端在这两格上给的）。
 * 不要求登录：游客照常能逛，登录时每一行多带回 selfLiked。
 *
 * isRecommend 给 1 就是只要推荐位的那几条（右侧那一块单独走这一格，判据还在 SQL 里那一刀之上再叠一层）；
 * 这一格留空就是全捞，所以左侧主列表不传它。
 *
 * orderByLike 给 1 是把序换成「点赞最多的排最前」（搜索结果页那一块用），不传就是广场那一页原本的流。
 * 排的是明细现算出来的那个数，不是库里那列 —— 库里从不回写。
 */
export function fetchPlazaPage(
  pageNum: number,
  pageSize: number,
  query: { keyword?: string | undefined; relationType?: number; isRecommend?: number; orderByLike?: number } = {},
): Promise<WebPage<PlazaMessage>> {
  return apiGet<WebPage<PlazaMessage>>('/api/web/shop/plaza/list', { pageNum, pageSize, ...query });
}

/** 右侧那一块推荐位：一次拿一页就够（它是导流不是列表，往下翻归主列表），条数由调用方给 */
export function fetchRecommendedPlazaPage(pageSize: number): Promise<WebPage<PlazaMessage>> {
  return fetchPlazaPage(1, pageSize, { isRecommend: 1 });
}

/** 一条消息（带正文）：详情页用；不是自己发的又没上广场的那一条，后端会直接报一句话 */
export function fetchPlaza(id: number): Promise<PlazaMessage> {
  return apiGet<PlazaMessage>(`/api/web/shop/plaza/${id}`);
}

/**
 * 发布：落库一律是 0 待审核。正文里的图片是编辑器先走 /api/file/uploadImage 传上去、再把地址写进 img 标签的，
 * 广场消息图另走一颗 uploadPlazaImage，回来的地址按顺序拼在 form.plazaImage 那一格上（不传就是这条没图）。
 * 返回刚落库的那一行（publishTime 还是空）。
 */
export function publishPlaza(form: PlazaPublishForm): Promise<PlazaMessage> {
  return apiPost<PlazaMessage>('/api/comm/shop/plaza/publish', form);
}

/**
 * 传一张图，回来的就是能拼进 plaza_image 那一列的 URL（同 uploadAvatar 那一颗，只是分目录的 source 换成 plaza）。
 * 多张就按顺序一张一颗，页面自己拼成逗号串。正文里的插图不进这一颗 —— 那条由编辑器的自定义上传负责，
 * 两处都是全站唯一那个收文件的接口。
 */
export function uploadPlazaImage(file: File): Promise<string> {
  return apiUpload<string>('/api/file/uploadImage', file, { source: 'plaza' }, undefined, { timeout: 60000 });
}

/**
 * 我发的那些（个人中心「广场消息」那一档）：判据和广场那一页差一刀 —— 这里不拦审核状态，
 * 待审核的和被拒的都要看得见（否则人只能干等一句审核结果，也改不了自己写错的那一条）。
 * 必须登录，归属由服务端按登录账号钉，参数里没有 userId。
 * 筛选三格都可空：keyword 命中标题和正文摘要，relationType / auditStatus 是两档枚举
 *（「无关联」是 0 这一档真值，所以「不限」只能不传 —— 传 0 是点名要没挂东西的那几条）。
 */
export function fetchMyPlazaPage(
  pageNum: number,
  pageSize: number,
  query: { keyword?: string; relationType?: number; auditStatus?: number } = {},
): Promise<WebPage<PlazaMessage>> {
  return apiGet<WebPage<PlazaMessage>>('/api/web/shop/plaza/my/list', { pageNum, pageSize, ...query });
}

/**
 * 改自己发的那一条：正文/标题/广场消息图/关联对象换一遍，服务端把 audit_status 打回 0 重新排队，
 * 已经给过的那 10 分不会重发（publish_time 留着，后台拿它判「是不是头一次通过」）。
 */
export function updatePlaza(form: PlazaUpdateForm): Promise<null> {
  return apiPost<null>('/api/comm/shop/plaza/update', form);
}

/** 删自己发的那一条：软删，任意审核状态都删得动；名下收到的赞/评/分享和已经到手的积分都不回收 */
export function deletePlaza(id: number): Promise<null> {
  return apiPost<null>(`/api/comm/shop/plaza/delete/${id}`);
}

/** 点赞那颗：一颗管两个方向，回按完之后的状态（true 已点上、false 已取消），不用再回读一遍 */
export function togglePlazaLike(id: number): Promise<boolean> {
  return apiPost<boolean>(`/api/comm/shop/plaza/like/${id}`);
}

/**
 * 记一次分享：只往明细表插一行留痕，不判重复也不回写计数（广场上的分享数读的时候现算）。
 * 火忘的调用就行：这一条失败不影响链接已经复制出去了。
 */
export function trackPlazaShare(id: number, shareChannel = PLAZA_SHARE_LINK): Promise<null> {
  return apiPost<null>('/api/comm/shop/plaza/share', { messageId: id, shareChannel });
}

/** 关联对象那一格的落点：挂商品/门店/品牌/活动时详情页要给一条链接，无关联或对象已删就没有 */
export function relationHref(type: number | null | undefined, id: number | null | undefined): string {
  if (!type || !id) return '';
  const paths = {
    1: `/product/${id}`,
    2: `/store/${id}`,
    3: `/brand/${id}`,
    4: `/activity/${id}`,
  } as Record<number, string>;
  return paths[type] || '';
}

/**
 * plaza_image 那一格拆回地址数组：列里存的是「若干张图用英文逗号拼起来的一串」，服务端落库前已经把空白和空段剥掉了，
 * 这里再 trim 一遍只是防历史数据里那种「一个地址一格」的老写法带空格。
 * 卡片配图取 [0]（他定的口径：多图只显示第一张），详情页正文下方那组读全部。
 */
export function plazaImages(one: { plazaImage?: string | null } | null | undefined): string[] {
  if (!one?.plazaImage) return [];
  return one.plazaImage
    .split(',')
    .map((url) => url.trim())
    .filter(Boolean);
}

/** 卡片配图那一格：一串里取第一张，没图给空串（调用方 v-if 判空、铺首字占位那颗） */
export function plazaLeadImage(one: { plazaImage?: string | null } | null | undefined): string {
  return plazaImages(one)[0] || '';
}

/** 列表卡片上没有标题的那些消息：正文摘要拿掉换行当标题用，两格都空才给兜底名 */
export function plazaLabel(one: PlazaMessage, fallback: string): string {
  const title = one.title?.trim();
  if (title) return title;
  const text = one.contentText?.trim().replace(/\s+/g, ' ');
  if (text) return text.length > 40 ? `${text.slice(0, 40)}…` : text;
  return fallback;
}
