import type { IPageRes, IShopCommentRow, IShopCommentSaveForm } from './types/shop'
import { http } from '@/http/http'

/**
 * 评论区（App 端），商品/门店/品牌/广场四处详情页底下那一大块共用这一份。
 *
 * 读走 /api/app/shop/comment/list —— 控制器转一手到 Web 端同一个 service 的同一个方法，
 * 两端看到的是同一批评论，判据只有一处；三个动作（发表、点赞、删除）是两端共用的**动作**，
 * 走 /api/comm 那一对接口，App 不再转手一条。
 *
 * 读不要求登录（未登录时 isLike 恒 false，点赞那颗照样画，点了先拦一道去登录）；
 * 三条动作要登录 —— /api/** 不过 Sa-Token 拦截器，「必须登录」由服务层第一句 requireLoginUser 当场拒掉，
 * 页面自己先用 token store 拦一道，免得白报一次错。
 */

/** 评论类型四档（字典 comment_type）：businessId 指向哪张表由它定，一列指四张表，单看 id 会撞号 */
export const COMMENT_TYPE_PRODUCT = 1
export const COMMENT_TYPE_STORE = 2
export const COMMENT_TYPE_BRAND = 3
export const COMMENT_TYPE_PLAZA = 4

/** 一条评论最多多少字：和服务层那一道闸同一个数，输入框自己先限住，不等服务端拒 */
export const COMMENT_MAX_LENGTH = 500

/**
 * 一条评论最多带几张图：也是服务端那个数（超了那句「一条评论最多带 9 张图」就在这一档拦在前头）。
 * 图不另建列：传上去拿回地址后拼成一串 `[图片](https://…)` 追加进正文，和文字一起存 content。
 */
export const COMMENT_MAX_IMAGES = 9

/**
 * 图片在正文里的那一串。形状、地址、张数三道闸都在服务端（ShopCommentWriteServiceImpl），
 * 这里只负责拼和拆；改形状要三端加后端一起改。
 * 每次都新开一个正则实例：带 g 的实例有 lastIndex，模块级共用会被上一次匹配留着。
 */
const imageMark = () => /\[图片\]\(([^\s)]*)\)/g

/** 上传回来的地址拼成一串进正文 */
export function commentImageMark(url: string) {
  return `[图片](${url})`
}

/**
 * 正文拆出来的一段：type 为 text 时读 text（照纯文本铺，绝不 rich-text/v-html —— 评论是访客填的），
 * 为 image 时读 url（只把地址交给 image 的 src）。
 * 两个字段都留着、不用联合类型，模板里 v-for 那一段就不必做类型收窄。
 */
export interface ICommentSegment {
  type: 'text' | 'image'
  text: string
  url: string
}

/**
 * 把 content 拆成「文字段 + 图片段」交替的一条串。
 * 地址不是 http(s) 开头的（比如后台改正文时手工敲进去的）不当图、照原文当字画，脏一串只裂那一格。
 */
export function parseCommentContent(content?: string | null): ICommentSegment[] {
  const text = content ?? ''
  if (!text)
    return []
  const mark = imageMark()
  const out: ICommentSegment[] = []
  let cursor = 0
  let hit = mark.exec(text)
  while (hit) {
    const url = hit[1] ?? ''
    if (!/^https?:\/\//i.test(url)) {
      hit = mark.exec(text)
      continue
    }
    if (hit.index > cursor)
      out.push({ type: 'text', text: text.slice(cursor, hit.index), url: '' })
    out.push({ type: 'image', text: '', url })
    cursor = hit.index + hit[0].length
    hit = mark.exec(text)
  }
  if (cursor < text.length)
    out.push({ type: 'text', text: text.slice(cursor), url: '' })
  return out
}

/** 正文里那串摘掉图片之后的文字：字数那道闸只数这一份，和服务层同一口径（图片地址七八十个字符，算进去两条图就吃光额度） */
export function commentText(content: string): string {
  return content.replace(imageMark(), '')
}

/** 这一串里图片的地址（按出现顺序）：看大图那一层要把整组递给 uni.previewImage */
export function commentImageUrls(content?: string | null): string[] {
  return parseCommentContent(content).filter(seg => seg.type === 'image').map(seg => seg.url)
}

/** 摘掉第 nth 张图（连语法整串删掉）：输入框里那颗 × 走这一条，正文剩下的部分不动 */
export function removeCommentImage(content: string, nth: number): string {
  const mark = imageMark()
  let index = 0
  let hit = mark.exec(content)
  while (hit) {
    if (index === nth)
      return content.slice(0, hit.index) + content.slice(hit.index + hit[0].length)
    index += 1
    hit = mark.exec(content)
  }
  return content
}

/**
 * 表情面板里那些 emoji。
 * 不另设一列、也不是「[微笑]」那种代码：它就是正文里的普通字符，跟着 content 一起存、一起按纯文本画
 *（这一格是 utf8mb4，4 字节字符存得住；长度前后端都按 UTF-16 单元算，一个 emoji 两边都是 2 字，500 字口径不变）。
 * 这份表和 shop-web 的 lib/comment.ts 各一份：两端各画各的面板，词表撞不上不要紧，判据（长度、张数）都在服务端。
 */
export const COMMENT_EMOJIS = [
  '😀',
  '😄',
  '😂',
  '🥰',
  '😍',
  '😘',
  '😉',
  '😊',
  '😎',
  '🤔',
  '😴',
  '😢',
  '😭',
  '😡',
  '🥺',
  '😱',
  '👍',
  '👎',
  '👏',
  '🙏',
  '💪',
  '🤝',
  '✌️',
  '❤️',
  '🎉',
  '🔥',
  '✨',
  '💯',
  '🌹',
  '🍀',
  '⭐',
  '🌙',
  '☀️',
  '🌈',
  '🍰',
  '🎁',
  '🚚',
  '🏠',
  '🛍️',
  '💰',
]

/**
 * 某一对象名下的评论：顶层分页，每行把它名下整棵回复树一并带回（一趟两条 SQL，前端不用逐条再问一次）。
 * commentType + businessId 必须一起给，少一条后端直接报；置顶那几条不管哪一档都打头（服务端排的）。
 */
export function getComments(
  commentType: number,
  businessId: number,
  pageNum = 1,
  pageSize = 10,
  sortType: 'time' | 'like' = 'time',
) {
  // sortType 只在「最热」那一档才送：全局 field-strategy 不送等于取默认，时间档不用拼一个 'time' 过去
  const query: Record<string, any> = { commentType, businessId, pageNum, pageSize }
  if (sortType === 'like') {
    query.sortType = 'like'
  }
  return http.get<IPageRes<IShopCommentRow>>('/api/app/shop/comment/list', query)
}

/**
 * 某一对象名下的评论条数：详情页 tab 标签上要印这个数，而 tab 在评论区之前就得有数，
 * 所以不等评论那一档点开、单独先捞一次。只钉一条（那个 total 本来就是同一趟 count 出来的）。
 */
export async function getCommentTotal(commentType: number, businessId: number) {
  const page = await getComments(commentType, businessId, 1, 1)
  return Number(page.total ?? 0)
}

/**
 * 发表 / 回复：回的是刚落库的那一行，但昵称头像不在里面（那两格是列表联 sys_user 现带的），
 * 所以调用方发完要回读一次列表。userId 不在参数里 —— 能发的只有自己。
 */
export function publishComment(form: IShopCommentSaveForm) {
  return http.post<{ id: number }>('/api/comm/shop/comment/publish', form)
}

/** 点赞那颗：一颗管两个方向，回按完之后的状态（true 已点上、false 已取消），不用再回读一遍 */
export function toggleCommentLike(id: number) {
  return http.post<boolean>(`/api/comm/shop/comment/like/${id}`)
}

/** 删自己发的那一条：软删，删在哪一行就把它名下整棵子树一起收掉；不是自己发的后端回「评论不存在」 */
export function deleteComment(id: number) {
  return http.post<void>(`/api/comm/shop/comment/delete/${id}`)
}
