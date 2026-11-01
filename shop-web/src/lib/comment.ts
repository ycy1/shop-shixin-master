import { apiGet, apiPost, apiUpload } from './api';

/**
 * 评论区那四处详情页（商品/门店/品牌/广场）用到的接口绑定。
 *
 * 读各端一份（Web 这条 /api/web/...，App 那条 /api/app/...），三个动作两端共用 /api/comm/...：
 * 同一个动作在两处各写一遍判据，早晚对不上，所以前端这边也只留一份。
 * 调 comm 要写全路径（request 的 baseURL 会再叠一层 /api，见 lib/profile 里签到那两条同款的写法）。
 */

interface Paged<T> {
  records: T[];
  total: number;
  pages: number;
  current: number;
}

/** 评论类型的五档（字典 comment_type），businessId 指向哪张表由它定 */
export const COMMENT_TYPE_PRODUCT = 1;
export const COMMENT_TYPE_STORE = 2;
export const COMMENT_TYPE_BRAND = 3;
export const COMMENT_TYPE_PLAZA = 4;
/** 主题公园 —— businessId 是 shop_trendy_park.id（收藏那一档公园是 4，两本字典各数各的） */
export const COMMENT_TYPE_PARK = 5;

/** 一条评论最多多少字：和服务层那一道闸同一个数（超了服务端拒，不等它拒，输入框自己先限住） */
export const COMMENT_MAX_LENGTH = 500;

/**
 * 一条评论最多带几张图：也是服务端那个数。
 * 图先走 POST /api/file/uploadImage（source=comment）传上去拿回 URL，再把地址拼进正文；「发表」这一条不收文件。
 */
export const COMMENT_MAX_IMAGES = 9;

/**
 * 图片在正文里的那一串：`[图片](https://…)`。它和 content 一起存、不另建列，
 * 所以这一串既是画法的约定也是服务端的判据（形状、地址、张数都在 ShopCommentWriteServiceImpl 里过），改形状要三端加后端一起改。
 * 每次都新开一个正则实例：带 g 的实例有 lastIndex，模块级共用会被上一次匹配留着。
 */
const imageMark = () => /\[图片\]\(([^\s)]*)\)/g;

/** 拼一串图片语法：上传回来那个 URL 递进来 */
export function commentImageMark(url: string): string {
  return `[图片](${url})`;
}

/** 正文拆出来的一段：要么是给人读的文字，要么是一串图片语法 */
export type CommentSegment = { type: 'text'; text: string } | { type: 'image'; url: string };

/**
 * 把 content 拆成「文字段 + 图片段」交替的一条串：文字段仍按纯文本插值画（绝不 v-html，
 * 评论是访客填的），图片段只把地址交给 img 的 src。
 * 地址不是 http(s) 开头的（比如后台改正文时手工敲进去的）不当图、照原文当字画，脏一串裂也只裂那一格。
 */
export function parseCommentContent(content: string | null | undefined): CommentSegment[] {
  const text = content ?? '';
  if (!text) return [];
  const mark = imageMark();
  const segments: CommentSegment[] = [];
  let cursor = 0;
  let hit = mark.exec(text);
  while (hit) {
    const url = hit[1] ?? '';
    if (!/^https?:\/\//i.test(url)) {
      hit = mark.exec(text);
      continue;
    }
    if (hit.index > cursor) segments.push({ type: 'text', text: text.slice(cursor, hit.index) });
    segments.push({ type: 'image', url });
    cursor = hit.index + (hit[0] ?? '').length;
    hit = mark.exec(text);
  }
  if (cursor < text.length) segments.push({ type: 'text', text: text.slice(cursor) });
  return segments;
}

/** 这一串里图片的地址（按出现顺序）：看大图那一层要整组递给查看器 */
export function commentImageUrls(content: string | null | undefined): string[] {
  return parseCommentContent(content)
    .filter((item): item is { type: 'image'; url: string } => item.type === 'image')
    .map((item) => item.url);
}

/** 摘掉第 nth 张图（连语法整串删掉）：输入框里那颗 × 走这一条，正文剩下的部分不动 */
export function removeCommentImage(content: string, nth: number): string {
  const mark = imageMark();
  let index = 0;
  let hit = mark.exec(content);
  while (hit) {
    if (index === nth) return content.slice(0, hit.index) + content.slice(hit.index + hit[0].length);
    index += 1;
    hit = mark.exec(content);
  }
  return content;
}

/**
 * 字数闸只数「人打的字」：图片那一整串（连语法带地址）先摘掉再量，和服务层同一口径。
 * 存储平台给回的地址七八十个字符，连语法一起算进 500 字的话，两条图就把额度吃光了。
 */
export function commentText(content: string): string {
  return content.replace(imageMark(), '');
}

export function commentTextLength(content: string): number {
  return commentText(content).length;
}

/**
 * 表情面板里的那些 emoji。
 * 表情不另设一列、也不是「[微笑]」那种代码：它就是正文里的普通字符，跟着 content 一起存、一起按纯文本画
 *（这一格是 utf8mb4，4 字节字符存得住；长度前后端都按 UTF-16 单元算，一个 emoji 两边都是 2 字，500 字口径不变）。
 */
export const COMMENT_EMOJIS = [
  '😀', '😄', '😂', '🥰', '😍', '😘', '😉', '😊',
  '😎', '🤔', '😴', '😢', '😭', '😡', '🥺', '😱',
  '👍', '👎', '👏', '🙏', '💪', '🤝', '✌️', '❤️',
  '🎉', '🔥', '✨', '💯', '🌹', '🍀', '⭐', '🌙',
  '☀️', '🌈', '🍰', '🎁', '🚚', '🏠', '🛍️', '💰',
];

/** 排序两档：不传按时间新在前，'like' 按点赞数；置顶那几条不管哪一档都打头（服务端排的） */
export type CommentSort = 'time' | 'like';

/** sys_comment 的一行 + 联 sys_user 带出的昵称头像；回复挂在被回复那一行下面、层数不限，所以 children 会往深里套 */
export interface CommentRow {
  id: number;
  businessId: number | null;
  commentType: number | null;
  parentId: number | null;
  userId: number | null;
  nickname: string | null;
  avatar: string | null;
  /** 回复某个人时才有（顶层评论这一格是空）；页面上那句「回复 @某人」读它 */
  replyNickname?: string | null;
  content: string;
  likeCount: number | null;
  isStick: number | null;
  /** IP 归属地，只到省市；访客的 IP 本身接口不给 */
  ipSource: string | null;
  createTime: string | null;
  /** 这一行我点过赞没：未登录时后端恒给 false，所以按钮照画，只是挂上登录门槛 */
  isLike: boolean | null;
  /** 这一行名下的直接回复（服务端递归 CTE 一趟捞完整棵回复树按 parentId 归好堆，没有就是空数组） */
  children?: CommentRow[] | null;
}

export type CommentPage = Paged<CommentRow>;

/**
 * 某一对象名下的评论：顶层分页，每行把它名下整棵回复树一并带回（一趟两条 SQL，前端不用逐条再问一次）。
 * commentType + businessId 必须一起给（一列指四张表，id 会撞号），少一条后端直接报。
 */
export function fetchComments(
  commentType: number,
  businessId: number,
  pageNum = 1,
  pageSize = 10,
  sortType?: CommentSort,
): Promise<CommentPage> {
  return apiGet<CommentPage>('/api/web/shop/comment/list', {
    commentType,
    businessId,
    pageNum,
    pageSize,
    sortType: sortType === 'like' ? sortType : undefined,
  });
}

/**
 * 某一对象名下的评论条数：详情页 tab 标签上那一格要印它，而 tab 在评论区之前就得有数，
 * 所以不等评论那一档点开、单独先捞一次。只钉一条（后端那个 total 本来就是同一趟 count 出来的），
 * 排序走默认的时间档，跟预览态那一档的「最热」无关。
 */
export async function fetchCommentTotal(commentType: number, businessId: number): Promise<number> {
  const page = await fetchComments(commentType, businessId, 1, 1);
  return Number(page.total ?? 0);
}

/**
 * 发表 / 回复：回的是刚落库的那一行，但昵称头像不在里面（那两格是列表联 sys_user 现带的），
 * 所以调用方发完要回读一次列表。userId 不在参数里 —— 能发的只有自己。
 */
export function publishComment(form: {
  commentType?: number;
  businessId?: number;
  content: string;
  /** 回复挂在评论树的哪一行下面（回回复的回复也递那一条的 id）；不传就是发顶层。
   *  exactOptionalPropertyTypes 下 undefined 也得算合法值，调用方现成的 `row?.id` 才能直接递进来 */
  replyToId?: number | undefined;
}): Promise<CommentRow> {
  return apiPost<CommentRow>('/api/comm/shop/comment/publish', form);
}

/**
 * 传一张评论配图，回来的 URL 用 `commentImageMark()` 拼成一串 `[图片](https://…)` 进正文，和文字一起存 content。
 *
 * 走的是现成的 POST /api/file/uploadImage（和 uploadAvatar 同一个口，只是 source 换成 comment 分一个新子目录）；
 * 后端只认 http(s) 开头的地址、一条最多 9 张，两道闸在服务层，这里先把选图那一侧限住就行。
 * 图片比一般请求慢，超时跟着头像那条一样放宽到 60 秒。
 */
export function uploadCommentImage(file: File): Promise<string> {
  return apiUpload<string>('/api/file/uploadImage', file, { source: 'comment' }, undefined, { timeout: 60000 });
}

/**
 * 点赞那颗：一颗管两个方向，回按完之后的状态（true 已点上、false 已取消），不用再回读一遍。
 * 必须登录 —— 未登录时按钮挂 data-open-login，点击归头部那颗登录窗认领，走不到这里。
 */
export function toggleCommentLike(id: number): Promise<boolean> {
  return apiPost<boolean>(`/api/comm/shop/comment/like/${id}`);
}

/**
 * 删自己发的那一条：软删，删在哪一行就把它名下整棵子树一起收掉。
 * 不是自己发的、已经删过的、没这个 id 后端回同一句话，那句直接当提示用。
 */
export function deleteComment(id: number): Promise<null> {
  return apiPost<null>(`/api/comm/shop/comment/delete/${id}`);
}
