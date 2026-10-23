import type { IPageRes, IShopPlazaMessage, IShopPlazaPublishForm, IShopPlazaUpdateForm } from './types/shop'
import { http } from '@/http/http'

/**
 * 广场（底部那根 bar 那一页 + 每条消息的详情页 + 「我的」页里那一档）用到的接口绑定。
 *
 * 读两条走 /api/app（App 端自己那一份控制器），发布/改自己那条/删自己那条/点赞/记一次分享走 /api/comm —— 这几条是
 * 「App 和 shop-web 同一个动作」，判据只能有一份，分叉了就会出现网页发得出去、App 上被拒。
 * 调 comm 要写全路径：request 的 baseURL 会再叠一层 /api（口径同 api/comment.ts 那三颗）。
 *
 * 列表和详情都来自后端同一份读服务，回的就是 shop_plaza_message 那一行实体：列表那条 SQL 不取 content，
 * 所以正文只有详情页那一趟读得到 —— 「修改」必须先按 id 读一次详情，否则编辑器里是空的。
 * 判据（只看已发布未下架、置顶打头）全压在服务端，前端传什么都到不了查询串；
 * 只有 my/list 那一档整个不拦审核状态，因为要看的正是自己还没过审、被拒掉的那几条。
 */

/** 关联类型（字典 plaza_relation_type）：relationId 指向哪张表由它定，0 就是没挂任何东西 */
export const PLAZA_RELATION_NONE = 0
export const PLAZA_RELATION_PRODUCT = 1
export const PLAZA_RELATION_STORE = 2
export const PLAZA_RELATION_BRAND = 3
export const PLAZA_RELATION_ACTIVITY = 4

/** 关联类型那一排胶囊（发布页的选择器就按这个顺序铺） */
export const PLAZA_RELATION_TEXT: Record<number, string> = {
  [PLAZA_RELATION_NONE]: '无关联',
  [PLAZA_RELATION_PRODUCT]: '商品',
  [PLAZA_RELATION_STORE]: '门店',
  [PLAZA_RELATION_BRAND]: '品牌',
  [PLAZA_RELATION_ACTIVITY]: '活动',
}

/** 审核状态（字典 plaza_audit_status）：0 发布落库那一档，1 才上广场，2 是被判下来的 */
export const PLAZA_AUDIT_PENDING = 0
export const PLAZA_AUDIT_PASSED = 1
export const PLAZA_AUDIT_REJECTED = 2

/** 「我的」页那一档的行徽标：文字跟着字典，底色只有过审那颗是红的 */
export const PLAZA_AUDIT_TEXT: Record<number, string> = {
  [PLAZA_AUDIT_PENDING]: '待审核',
  [PLAZA_AUDIT_PASSED]: '已发布',
  [PLAZA_AUDIT_REJECTED]: '已拒绝',
}

/** 分享渠道（字典 share_channel）：App 里没有微信分享 SDK，复制链接就是 5 这一档 */
export const PLAZA_SHARE_LINK = 5

/** 审核通过后才给分。这个数只在页面上说一句「发完为什么没动静」，判据和实际加多少都在服务端 */
export const PLAZA_PUBLISH_POINTS = 10

/** 正文长度闸：服务端先剥标签再数字数（2000），整串 HTML 另有 20000 的顶，前端提前拦一句免得白跑一趟上传 */
export const PLAZA_MAX_TEXT_LENGTH = 2000
export const PLAZA_MAX_HTML_LENGTH = 20000

/**
 * 广场那一页一次十条往下捞：不要求登录，游客照常能逛，登录时每行多带回 selfLiked。
 * 筛选两格都可空：keyword 命中标题/正文摘要/发布者昵称；relationType 是关联类型，
 * 「无关联」是 0 这一档真值，所以「不限」只能不传这一格（传 0 是点名要没挂东西的那几条）。
 *
 * orderByLike 给 1 是把序换成「点赞最多的排最前」（搜索结果页那一块用），不传就是广场这一页原本的流。
 * 排的是明细现算出来的那个数，库里那列从不回写。
 */
export function getPlazaPage(pageNum = 1, pageSize = 10, query: { keyword?: string, relationType?: number, orderByLike?: number } = {}) {
  return http.get<IPageRes<IShopPlazaMessage>>('/api/app/shop/plaza/list', { pageNum, pageSize, ...query })
}

/**
 * 推荐位那一档：读的还是列表那一条接口，只是多钉一格 isRecommend=1（后台勾了推荐的那几条）。
 * 它是导流不是列表 —— 一次捞一页就够、不跟着往下翻，换筛选也不重捞；不是登录才能看，游客照常捞得到。
 */
export function getRecommendedPlazaPage(pageSize = 8) {
  return http.get<IPageRes<IShopPlazaMessage>>('/api/app/shop/plaza/list', { pageNum: 1, pageSize, isRecommend: 1 })
}

/** 一条消息（带正文）：作者本人读得到自己那条待审核/被拒的，别人点进来会被后端直接报一句话 */
export function getPlazaDetail(id: number) {
  return http.get<IShopPlazaMessage>(`/api/app/shop/plaza/${id}`)
}

/**
 * 我发的那些（「我的」页里「广场消息」那一档）：判据和广场那一页差一刀 —— 这里不拦审核状态，
 * 待审核的和被拒的都要看得见（否则人只能干等一句审核结果，也改不了自己写错的那一条）。
 * 必须登录，归属由服务端按登录账号钉，参数里没有 userId。
 *
 * 三格筛选都能空着（空 = 不传 = 这一格不限）：「无关联」和「待审核」两档的真值都是 0，
 * 所以前端那颗「全部」用的是 null 哨兵、干脆不发这一格，不能拿 0 当「不限」发出去。
 */
export function getMyPlazaPage(
  pageNum = 1,
  pageSize = 10,
  query: { keyword?: string, relationType?: number, auditStatus?: number } = {},
) {
  return http.get<IPageRes<IShopPlazaMessage>>('/api/app/shop/plaza/my/list', { pageNum, pageSize, ...query })
}

/**
 * 发布：落库一律是 0 待审核，审核通过才上广场、才给那 10 分。
 * 正文里那些 <img> 是页面先把图传上去、再把地址拼进标签的，所以这一条只交一串 HTML。
 */
export function publishPlaza(form: IShopPlazaPublishForm) {
  return http.post<IShopPlazaMessage>('/api/comm/shop/plaza/publish', form)
}

/**
 * 改自己发的那一条：正文/标题/关联对象换一遍，服务端把 audit_status 打回 0 重新排队、
 * 审核说明跟着清空；已经给过的那 10 分不会重发（publish_time 留着，后台拿它判「是不是头一次通过」）。
 * 别人的和已经删掉的，后端都回同一句「这条消息不存在或已经被删除」。
 */
export function updatePlaza(form: IShopPlazaUpdateForm) {
  return http.post<void>('/api/comm/shop/plaza/update', form)
}

/** 删自己发的那一条：软删，任意审核状态都删得动；名下收到的赞/评/分享和已经到手的积分都不回收 */
export function deletePlaza(id: number) {
  return http.post<void>(`/api/comm/shop/plaza/delete/${id}`)
}

/** 点赞那颗：一颗管两个方向，回按完之后的状态（true 点上了、false 撤了），不用再回读一遍 */
export function togglePlazaLike(id: number) {
  return http.post<boolean>(`/api/comm/shop/plaza/like/${id}`)
}

/**
 * 记一次分享：只往明细表插一行留痕，不判重复也不回写计数（分享数读的时候现算）。
 * 火忘就行：这一条失败不该把「链接已经复制出去了」那声提示变成报错。
 */
export function trackPlazaShare(messageId: number, shareChannel = PLAZA_SHARE_LINK) {
  return http
    .post<void>('/api/comm/shop/plaza/share', { messageId, shareChannel }, undefined, undefined, { hideErrorToast: true })
    .catch(() => {})
}

/** 卡片上一行没有标题的消息拿正文摘要当标题，两格都空才给兜底名 */
export function plazaLabel(one: IShopPlazaMessage, fallback = '（没有标题）'): string {
  const title = one.title?.trim()
  if (title)
    return title
  const text = one.contentText?.trim().replace(/\s+/g, ' ')
  if (text)
    return text.length > 40 ? `${text.slice(0, 40)}…` : text
  return fallback
}

/** 关联对象那一格的落点：挂到商品/门店/品牌/活动时详情页给一条可点的链接，无关联或对象已被删就没有 */
export function relationUrl(type: number | null | undefined, id: number | null | undefined): string {
  if (!type || !id)
    return ''
  const paths: Record<number, string> = {
    [PLAZA_RELATION_PRODUCT]: `/pages/goods/detail?id=${id}`,
    [PLAZA_RELATION_STORE]: `/pages/store/detail?id=${id}`,
    [PLAZA_RELATION_BRAND]: `/pages/brand/detail?id=${id}`,
    [PLAZA_RELATION_ACTIVITY]: `/pages/activity/detail?id=${id}`,
  }
  return paths[type] ?? ''
}

/**
 * 从正文里把图片地址按出现顺序捞出来：详情页看大图要把整组递给 uni.previewImage（左右滑才翻得动）。
 *
 * 这里不用 String.matchAll：它要靠正则对象的 lastIndex，同一个 source 反复渲染时容易串位，
 * 而 new RegExp 每次新建一条、exec 循环自己走游标，是最不会出错的写法。只收 http 开头的地址（相对路径在这三端都拼不出能预览的 URL）。
 */
export function plazaImages(content: string | null | undefined): string[] {
  if (!content)
    return []
  const urls: string[] = []
  const re = /<img[^>]+?src\s*=\s*["']?([^"'>\s]+)/gi
  let hit = re.exec(content)
  while (hit) {
    if (/^https?:\/\//i.test(hit[1]))
      urls.push(hit[1])
    hit = re.exec(content)
  }
  return urls
}

/** 卡片配图那一格：plaza_image 那一串里取第一张，一张没传就是空串（列表两处拿它判空、铺首字占位） */
export function plazaLeadImage(one: { plazaImage?: string | null } | null | undefined): string {
  return plazaColumnImages(one)[0] || ''
}

/**
 * plaza_image 那一格拆回地址数组：列里存的是「几张图用英文逗号拼起来的一串」，落库前服务端已剥掉空白和空段，
 * 这里再 trim 一遍只是防老数据里「一格一个空格」的写法。
 * 名字让开 plazaImages —— 那一颗摘的是正文里的插图，两处各是一组（详情页两组都能放大，各翻各的）。
 */
export function plazaColumnImages(one: { plazaImage?: string | null } | null | undefined): string[] {
  if (!one?.plazaImage)
    return []
  return one.plazaImage
    .split(',')
    .map(url => url.trim())
    .filter(Boolean)
}

/** 详情页正文拆出来的一段：type=html 交给 rich-text 画，type=image 换成 <image> 组件（点得动、能放大）；index 是这张图在整组里的名次 */
export interface IPlazaSegment {
  type: 'html' | 'image'
  html: string
  url: string
  index: number
}

/**
 * 正文拆成「一段富文本 + 一张图」交替的流：详情页要的是图就在原来的位置、点它放大，
 * 而 rich-text 里的图在小程序/App 上点不动也放不大 —— 所以把 http 那张图从 HTML 里摘出来换成 <image> 组件自己接点击。
 *
 * 只摘 http(s) 的地址（判据和 plazaImages 一字不差，两边数出来的是同一组、同一序，预览递给 previewImage 的整组才对得上）；
 * 相对路径那种留在 HTML 里照旧由 rich-text 画，只是点不动 —— 反正这三端也拼不出能预览的 URL。
 */
export function plazaSegments(content: string | null | undefined): IPlazaSegment[] {
  if (!content)
    return []
  // 先按整个 <img> 标签切，再从那一段里取 src：一条正则同时吃掉「标签」和「属性值」会被 eslint 判成能多项式回溯的形状
  const tag = /<img[^>]*>/gi
  const src = /src\s*=\s*["']?([^"'>\s]+)/i
  const out: IPlazaSegment[] = []
  let cursor = 0
  let index = 0
  let hit = tag.exec(content)
  while (hit) {
    const url = src.exec(hit[0])?.[1] ?? ''
    if (!/^https?:\/\//i.test(url)) {
      hit = tag.exec(content)
      continue
    }
    const before = content.slice(cursor, hit.index)
    if (before.replace(/<[^>]*>/g, '').trim() || /<img/i.test(before))
      out.push({ type: 'html', html: before, url: '', index: -1 })
    out.push({ type: 'image', html: '', url, index })
    index += 1
    cursor = hit.index + hit[0].length
    hit = tag.exec(content)
  }
  const tail = content.slice(cursor)
  if (tail.replace(/<[^>]*>/g, '').trim() || /<img/i.test(tail))
    out.push({ type: 'html', html: tail, url: '', index: -1 })
  return out
}

/**
 * 正文里除了 img 还剩多少字：发布页拿它拦长度，判据和这里算法一致（剥标签、压空白），
 * 服务端剥得比我宽也没关系 —— 前端只是提前提示，真正拒下来的那句由后端给。
 */
export function plazaTextLength(content: string): number {
  return content.replace(/<[^>]*>/g, '').replace(/&nbsp;/gi, ' ').replace(/\s+/g, '').length
}

/**
 * 把正文那串 HTML 拆回「文字 + 图片地址」两格：修改时要把已有的那条铺进这一页的输入框（App 上没有所见即所得的编辑器）。
 *
 * 先按 plazaImages 捞图（用的是同一个函数，所以拆出来的顺序和拼回去的一模一样），再把块级收尾标签换成换行、
 * 剥掉其余标签、把实体还原，最后丢掉空行。Web 端编辑器写出来的 <strong>/<ul>/<h2> 会在这一步丢掉样式、但字不会丢 ——
 * 保存后正文统一变成这一页拼的那套 <p>，读侧（<rich-text> / v-html / 后台那个编辑器）照样吃得住。
 */
export function plazaParseContent(content: string | null | undefined): { text: string, images: string[] } {
  const images = plazaImages(content)
  if (!content) {
    return { text: '', images }
  }
  const text = content
    .replace(/<img[^>]*>/gi, '')
    .replace(/<\/(p|div|li|h[1-6])>/gi, '\n')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<[^>]*>/g, '')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, '\'')
    .replace(/&amp;/g, '&')
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(line => line.length > 0)
    .join('\n')
  return { text, images }
}
