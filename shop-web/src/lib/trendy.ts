/**
 * 潮玩专区（shop-web 左侧那条 首页/盲盒/公园）读的三趟接口。
 *
 * 都在 /api/web 下，不要登录态：逛专区不需要账号，抽盒那颗动作才要（走 lib/box 之外的
 * POST /api/comm/trendy/box/{id}/draw，这一期前台还没接）。
 * 可见性判据（哪些状态的盒/园区能出现在前台）在后端服务层，这里不重复一遍。
 */
import { apiGet, apiPost, apiUpload } from './api';

/** 一台盒卡片上的一枚款式（后端按排序最多带 12 枚） */
export interface BoxStyle {
  boxId: number;
  styleName: string | null;
  styleImage: string | null;
  /** 1 隐藏款 0 普通款 */
  isHidden: number | null;
  /** 概率是小数（0.0139 = 1.39%）；盒上没勾「公示概率」时页面不渲染这一格 */
  probability: number | null;
  remainCount: number | null;
}

export interface TrendyBox {
  id: number;
  boxName: string;
  trendyId: number | null;
  trendyName: string | null;
  coverImage: string | null;
  /** 1单抽 2端盒 3一番赏 4抽签（字典 trendy_box_type） */
  boxType: number | null;
  price: number | null;
  endBoxPrice: number | null;
  drawCount: number | null;
  hiddenRate: number | null;
  guaranteeCount: number | null;
  /** 1 公示概率 0 不公示（合规那一刀按盒走） */
  isProbabilityShow: number | null;
  /** 0 = 不限总次数 */
  totalCount: number | null;
  drawnCount: number | null;
  /** 不限次数时为 null（页面就不画「还剩几」那一格） */
  remainCount: number | null;
  startTime: string | null;
  endTime: string | null;
  /** 1 未开始 2 进行中 */
  status: number | null;
  /** 后台勾的「热门」：首页那两台机台优先摆勾了的 */
  isHot: number | null;
  description: string | null;
  itemCount: number | null;
  styles: BoxStyle[] | null;
}

export interface BoxPage {
  records: TrendyBox[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

/** 园区名下一张在架的票 */
export interface ParkTicket {
  id: number;
  parkId: number;
  ticketName: string;
  /** 1日票 2年卡 3套票 4快速通行 5限定款购买资格（字典 ticket_type） */
  ticketType: number | null;
  price: number | null;
  /** 一串逗号分隔的 ticket_time_slot 值（"1,2"），不是单个数 */
  timeSlots: string | null;
  validDays: number | null;
  maxPerOrder: number | null;
  description: string | null;
}

/** 园区名下一张审核通过的照片（后台那张表上来的，一张一行） */
export interface ParkPhoto {
  id: number;
  imageUrl: string | null;
  /** 图注一句话；没填就是 null，页面不画那一行 */
  description: string | null;
}

export interface Park {
  id: number;
  parkName: string;
  coverImage: string | null;
  /** 名下审核通过的照片，精选在前（只有园区详情那一条会填；原先这是园区上的一个 json 列） */
  photos?: ParkPhoto[] | null;
  areaZh: string | null;
  address: string | null;
  longitude: number | null;
  latitude: number | null;
  openTime: string | null;
  description: string | null;
  /** 后台勾的「热门」：首页那圈轮播优先摆勾了的 */
  isHot: number | null;
  ticketCount: number | null;
  /** 名下在架票种的最低价；一个都没上架就是 null */
  priceMin: number | null;
  /** 只有园区详情那一条会填 */
  tickets?: ParkTicket[] | null;
}

/** 盲盒列表：`hot: 1` 只查勾了「热门」的（首页机台那一趟）；`status` 再收窄一档（1 未开始 / 2 进行中） */
export interface PhotoPage {
  records: ParkPhoto[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export function fetchTrendyBoxes(query: { status?: number; hot?: number; pageNum?: number; pageSize?: number } = {}): Promise<BoxPage> {
  return apiGet<BoxPage>('/api/web/shop/trendy/box/list', { ...query });
}

/**
 * 一台盒的详情：和列表那条同一份形状，区别只在 styles —— 列表按预览截断（一台最多带 12 款），
 * 这一条把名下在架款式全给出来（详情页那张概率表要数得齐）。
 */
export function fetchTrendyBoxDetail(id: number): Promise<TrendyBox> {
  return apiGet<TrendyBox>(`/api/web/shop/trendy/box/${id}`);
}

/**
 * 园区列表不分页（轮播位和卡片是同一趟数据）。
 * `hot: 1` = 只要后台勾了「热门」的那几家（首页轮播那一趟）；不传就是营业中的全给。
 */
export function fetchTrendyParks(hot?: number): Promise<Park[]> {
  return apiGet<Park[]>('/api/web/shop/trendy/park/list', { hot });
}

/** 园区详情：停业、下架、没这个 id 后端回同一句话，页面直接把那句话当提示用 */
export function fetchTrendyParkDetail(id: number): Promise<Park> {
  return apiGet<Park>(`/api/web/shop/trendy/park/${id}`);
}

/** 一家园区的全部照片（分页那一页用）：只给审核通过的，精选在前，次序和详情顶上那一屏对得上 */
export function fetchParkPhotos(parkId: number, pageNum = 1, pageSize = 20): Promise<PhotoPage> {
  return apiGet<PhotoPage>(`/api/web/shop/trendy/park/${parkId}/photo/list`, { pageNum, pageSize });
}

/** 一次抽盒落下来的一条记录（款式那三格是抽中那一刻的快照，后来改名字换图不回刷） */
export interface DrawRecord {
  boxId: number;
  itemId: number | null;
  styleName: string | null;
  styleImage: string | null;
  /** 1 隐藏款 0 普通款 */
  isHidden: number | null;
  createTime: string | null;
}

/** 抽盒那条动作的返回（POST /api/comm/trendy/box/{boxId}/draw，要登录态） */
export interface DrawResult {
  boxId: number;
  boxName: string;
  /** 本趟实际抽中几个（端盒可能少于配的个数：池子抽完了就到头） */
  drawnCount: number;
  /**
   * 本趟有几件真的进了购物车（抽中的款挂到了具体规格才会进；抽签那一档不发实物，恒为 0）。
   * 念「已加入购物车 N 件」用这一格，别拿 drawnCount 顶 —— 两个数不总是相等。
   */
  cartCount: number | null;
  /** 本趟应付金额。服务端只报数不扣款，页面得把这句说白 */
  cost: number | null;
  /** 这一趟是不是一整盒（盒本身是端盒、或点的是那颗「购买整盒」）：弹窗那一句按它换说法 */
  wholeBox: boolean | null;
  /** 0 不公示：那这一趟别把概率印出去 */
  isProbabilityShow: number | null;
  /** 盒的总可抽次数，0=不限 */
  totalCount: number | null;
  /** 盒累计已抽次数（本趟已计入） */
  boxDrawnCount: number | null;
  records: DrawRecord[] | null;
}

/** 购票那条动作的入参（POST /api/comm/park/ticket/buy，要登录态）；没有「给谁」这一格，登录态说了算 */
export interface BuyForm {
  ticketId: number;
  /** yyyy-MM-dd */
  visitDate: string;
  /** 这一张票约的那一档（0全天 1上午 2下午 3夜场）；票种只卖「全天」时不传也行 */
  timeSlot?: number;
  quantity?: number;
  /** 这两格是选填：没填就整个不递（exactOptionalPropertyTypes 下显式 undefined 不算「没给」） */
  holderName?: string;
  holderPhone?: string;
}

/** 一张买到手的票（票面那几格是出票那一刻的快照） */
export interface BoughtTicket {
  id: number;
  ticketCode: string;
  ticketName: string;
  price: number | null;
  visitDate: string;
  timeSlot: number | null;
  /** 票面二维码；出图那一步失败只记日志不回滚出票，所以这一格可能是空的 */
  qrImg: string | null;
  /** 0 未使用 1 已核销 2 已过期 3 已退款 */
  status: number | null;
}

export interface BuyResult {
  parkId: number;
  ticketId: number;
  ticketName: string;
  ticketType: number | null;
  visitDate: string;
  timeSlot: number | null;
  count: number;
  /** 本单应付合计。同抽盒一样：只报数，不扣款 */
  amount: number | null;
  tickets: BoughtTicket[] | null;
}

/** 抽一趟盒（真记一条抽盒记录、真扣款式库存）。没登录后端回「请先登录后再抽盒」 */
/**
 * 抽一趟盒。wholeBox=true 是那颗「购买整盒」：一趟拿一整盒（还有余量的款式各一件）、按端盒价结、
 * 进车是一整盒一行。不传就是按盒的类型抽一趟。
 */
export function drawTrendyBox(boxId: number, wholeBox = false): Promise<DrawResult> {
  return apiPost<DrawResult>(`/api/comm/trendy/box/${boxId}/draw${wholeBox ? '?wholeBox=true' : ''}`);
}

/** 买票：一次一张或几张，一张票落一行 */
export function buyParkTicket(form: BuyForm): Promise<BuyResult> {
  return apiPost<BuyResult>('/api/comm/park/ticket/buy', form);
}

/** 投稿一条园区照片的入参；图要先走 uploadParkPhotoFile 拿到 URL，这一条不收文件 */
export interface ParkPhotoSubmitForm {
  parkId: number;
  imageUrl: string;
  /** 图注一句话，选填（服务端限 64 字，input 上也钉了 maxlength） */
  description?: string;
}

/** 落库那一行里页面要念的几格（来源/状态都是服务端钉的，这里只回读） */
export interface ParkPhotoSubmission {
  id: number;
  parkId: number;
  imageUrl: string;
  description: string | null;
  /** 2 用户上传（投稿这一条只会是它） */
  sourceType: number | null;
  /** 0 待审核 —— 投完就是这一档，运营点了通过才会上墙 */
  status: number | null;
}

/**
 * 传文件拿 URL：全站那一条收文件的接口（头像、广场动态、评论图都走它），
 * 压缩成 webp、挑存储平台都在服务端，source=park 只是给存储上的路径分个档。
 * 60s 超时同头像那颗（公网存储，默认 15s 不够）。
 */
export function uploadParkPhotoFile(file: File): Promise<string> {
  return apiUpload<string>('/api/file/uploadImage', file, { source: 'park' }, undefined, { timeout: 60000 });
}

/** 投稿一张照片到某家园区名下（要登录态，来源钉 2、状态钉 0 待审核） */
export function submitParkPhoto(form: ParkPhotoSubmitForm): Promise<ParkPhotoSubmission> {
  return apiPost<ParkPhotoSubmission>('/api/comm/park/photo', form);
}

/** 逗号那串拆成码值数组（脏数据、空串一律当没有，不让一格坏数据把整张卡片打断） */
export function parseSlotCodes(raw: string | null): number[] {
  if (!raw) return [];
  return raw
    .split(',')
    .map((part) => Number(part.trim()))
    .filter((code) => Number.isInteger(code) && code >= 0);
}

/**
 * 图鉴（/trendy/collection）的一行：一条潮玩系列 + 我在这条系列上的收集进度 + 集齐给什么。
 *
 * 一行是系列、不是款式：这一页要看的是「整套集到哪了、还差哪几条系列」。
 * collectedSku / recordId 没登录都是 0 / null（后端那条读不要登录态，进度就念「0 / N」）。
 * rewardText 是后端按 rewardType 现拼的那句人话（积分念「N 分」，券和实物回捞名字）；
 * rewardType 留着是给页面挑那枚小标签（积分/优惠券/实物），文案一律念 rewardText。
 */
export interface CollectionRow {
  trendyId: number;
  trendyName: string;
  coverImage: string | null;
  description: string | null;
  totalSku: number;
  collectedSku: number;
  rewardType: number | null;
  rewardValue: string | null;
  rewardText: string | null;
  /** 这一笔领过没有（后端落的那一行） */
  recordId: number | null;
  collectTime: string | null;
  /** 领到的是什么 / 当时为什么没发出去，后端兑现那一刻定格的说明 */
  rewardDesc: string | null;
  /** 凑满了、奖励配齐了、而且还没领过 —— 按钮只按这一格出现 */
  canClaim: boolean;
}

export interface CollectionPage {
  records: CollectionRow[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

/** 图鉴那一页：10 条一页，我抽过的系列排最前（排序在后端那条 SQL 里做，页面不重排） */
export function fetchTrendyCollection(pageNum = 1, pageSize = 10): Promise<CollectionPage> {
  return apiGet<CollectionPage>('/api/web/shop/trendy/collection/list', { pageNum, pageSize });
}

/**
 * 领取这一条系列的集齐奖励（谁领的按登录态取，参数只有系列 id）。
 * 后端回的那一行里 rewardDesc 就是「这一笔兑现成了什么」，页面直接念那一句。
 * 没登录 / 没集齐 / 已经领过都是 HTTP 200 + body 里一句错误，由 apiPost 抛出来给页面当提示用。
 */
export function claimCollectReward(trendyId: number): Promise<{ rewardDesc: string | null }> {
  return apiPost(`/api/comm/trendy/collection/${trendyId}/claim`);
}

/**
 * 一条系列的一格收集明细（图鉴卡片底下那颗按钮开的弹窗）：
 * 一行是<b>整套里的一个规格</b>，不是「我抽过的那些记录」—— 差哪几格只有从这一侧才数得出来。
 * drawnTime 为 null 就是还没抽到过；同一格抽几次也只占一行（取第一次凑上的那一天）。
 */
export interface CollectionItem {
  skuId: number;
  productId: number;
  productName: string;
  specText: string | null;
  skuImage: string | null;
  drawnTime: string | null;
}

/** 收集明细：一次全带（一条系列名下撑死几十格，不翻页）；没登录后端回每一格都没抽到 */
export function fetchTrendyCollectionItems(trendyId: number): Promise<CollectionItem[]> {
  return apiGet<CollectionItem[]>(`/api/web/shop/trendy/collection/${trendyId}/items`);
}
