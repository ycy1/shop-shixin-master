import { fill, type I18n } from '@/i18n';
import { apiDelete, apiGet, apiPost, apiPut, apiUpload } from './api';

/**
 * 个人中心那几块的接口绑定（GET /api/web/shop/profile/*）。
 * 每一条都要求登录态，没登录后端回「请先登录…」那一句，由调用方（那一页）呈现成未登录提示。
 * 分页信封和商品列表同一形状，照 ProductPage 的写法各留一格 records/pages。
 */

interface Paged<T> {
  records: T[];
  total: number;
  pages: number;
  current: number;
}

export interface OrderRow {
  id: number;
  orderNo: string;
  status: number;
  totalAmount: number | null;
  payAmount: number | null;
  deliveryFee: number | null;
  itemCount: number | null;
  totalQuantity: number | null;
  storeNames: string | null;
  createTime: string | null;
}

export interface AddressRow {
  id: number;
  receiverName: string | null;
  receiverPhone: string | null;
  /** 编辑要回填那三级下拉，所以代码串也带回来（列表上画的还是 area_zh） */
  areaCode: string | null;
  areaZh: string | null;
  detail: string | null;
  tag: string | null;
  /** 前台这一版表单没有邮编那一格，但整条覆盖会把它清成空，所以带回来原样送回去 */
  postalCode: string | null;
  longitude: number | null;
  latitude: number | null;
  isDefault: number | null;
}

/**
 * 这一条地址能不能拿去下单分仓（结算页候选、地址弹窗那行提示都用它）：
 * 服务端先按经纬度算距离挑最近的仓，没坐标就退回按省/市/区三段行政区划代码匹同地区的仓
 * （ShopErpInventoryMapper#selectWarehouseIdByArea），所以两份都没有的那一条才真挑不出仓。
 */
export function canShipTo(row: AddressRow): boolean {
  const point = row.longitude != null && row.latitude != null;
  return point || String(row.areaCode ?? '').trim() !== '';
}

export interface FavoriteRow {
  id: number;
  targetType: number | null;
  targetId: number | null;
  targetName: string | null;
  /** 收藏时间：重新收藏同一个对象会刷新这一格（create_time 留第一次建行的痕，列表不读它） */
  favoriteTime: string | null;
  createTime: string | null;
}

export interface BrowseRow {
  id: number;
  productId: number | null;
  productName: string | null;
  mainImage: string | null;
  productPrice: number | null;
  categoryName: string | null;
  /** 最近一次浏览的时间，第一次记录在 createTime 那一格 */
  browseTime: string | null;
}

export type OrderPage = Paged<OrderRow>;
export type AddressPage = Paged<AddressRow>;
export type FavoritePage = Paged<FavoriteRow>;
export type BrowsePage = Paged<BrowseRow>;

/** userId 这一格前端不传：传了也会被服务层覆盖成登录 id */
export function fetchMyOrders(pageNum = 1, pageSize = 5): Promise<OrderPage> {
  return apiGet<OrderPage>('/api/web/shop/profile/order/list', { pageNum, pageSize });
}

export function fetchMyAddresses(pageNum = 1, pageSize = 5): Promise<AddressPage> {
  return apiGet<AddressPage>('/api/web/shop/profile/address/list', { pageNum, pageSize });
}

export function fetchMyFavorites(pageNum = 1, pageSize = 5): Promise<FavoritePage> {
  return apiGet<FavoritePage>('/api/web/shop/profile/favorite/list', { pageNum, pageSize });
}

export function fetchMyBrowses(pageNum = 1, pageSize = 5): Promise<BrowsePage> {
  return apiGet<BrowsePage>('/api/web/shop/profile/browse/list', { pageNum, pageSize });
}

/** shop_consumer_address 的标签（字典 address_tag）：库里存的就是这几个中文串，前台不联字典接口 */
export const ADDRESS_TAGS = ['家', '公司', '学校'];

/** 新增地址的表单形状（字段名和后台那份 ShopAddressSaveDto 一一对应）。
 *  userId 这一格前端不传：这是「往自己名下加」的接口，服务端把它钉成登录 id */
export interface AddressForm {
  receiverName: string;
  receiverPhone: string;
  /** 逗号分隔的行政区划代码：110000,110100,110101（省市区级联选出来的三个 value 拼起来） */
  areaCode: string;
  /** 对应那一串代码的中文，同后台 resolveAreaCode 给的形状：北京市 市辖区 东城区 */
  areaZh: string;
  detail: string;
  tag: string;
  /** 前台表单没这一格：新增时不送（库里留空），修改时把原来那个值原样带回去，免得整条覆盖把它抹了 */
  postalCode?: string | null;
  longitude: number | null;
  latitude: number | null;
  isDefault: number;
}

/**
 * 新增一条收货地址：POST /api/web/shop/profile/address，回落库的那一条。
 *
 * 不走后台那颗 POST /shop/address/add：那一条压 shop:address:add，而且不在 /api/** 下
 *（前台 dev 只代理 /api，生产 nginx 也只反代 /api），前台根本打不过去。
 *「第一条直接当默认」「设默认先清别的默认」两条判据在服务层，前端这一格只是个勾。
 */
export function addMyAddress(form: AddressForm): Promise<AddressRow> {
  return apiPost<AddressRow>('/api/web/shop/profile/address', form);
}

/**
 * 改自己名下的一条：PUT 同一条路径，id 放请求体（形状同后台那份 update，不走路径参数）。
 * 整条覆盖式提交 —— areaCode / areaZh / tag / 经纬度 送空就是清成空，所以编辑那格弹窗要先回填再改。
 * 别人的、已删的、没这个 id 都回同一句「收货地址不存在」，那句话直接当提示用。
 */
export function updateMyAddress(form: AddressForm & { id: number }): Promise<AddressRow> {
  return apiPut<AddressRow>('/api/web/shop/profile/address', form);
}

/**
 * 删自己名下的一条：DELETE /api/web/shop/profile/address/{id}（一条一个 id，不做批量）。
 * 逻辑删除，别人的 / 已经删过的 / 没这个 id 都回同一句「收货地址不存在」，那句话直接当提示用；
 * 删掉的正好是默认那条不补选，所以删完前端要重拉一遍（画不画得出默认是服务端说了算）。
 */
export function deleteMyAddress(id: number): Promise<null> {
  return apiDelete<null>(`/api/web/shop/profile/address/${id}`);
}

/**
 * 删自己名下的一条收藏：DELETE /api/web/shop/profile/favorite/{id}（同样一条一个 id，不做批量）。
 * 逻辑删除；别人的 / 已经删过的 / 没这个 id 都回同一句「收藏记录不存在」，那句话直接当提示用。
 */
export function deleteMyFavorite(id: number): Promise<null> {
  return apiDelete<null>(`/api/web/shop/profile/favorite/${id}`);
}

/**
 * 详情页右侧那颗收藏按钮：一颗粒管两个方向，库里落没落这一条都由它定。
 * 请求体只送 类型 + 对象（收藏行的 id 是列表那一路才有的东西，这一颗拿不到，也不认 userId）。
 * 返回点完之后的状态：true 已收藏、false 已取消 —— 按钮画实心还是空心看这一格，不用再回读一遍。
 */
export function toggleMyFavorite(targetType: number, targetId: number): Promise<boolean> {
  return apiPost<boolean>('/api/web/shop/profile/favorite/toggle', { targetType, targetId });
}

/**
 * 这一个收藏过没：借列表那一条接口，按 类型 + 对象 点、一次只捞一行，有行就是收藏过。
 * 没登录时后端直接回「请先登录…」，所以这一颗只在登录态里点。
 */
export async function fetchFavoriteState(targetType: number, targetId: number): Promise<boolean> {
  const page = await apiGet<FavoritePage>('/api/web/shop/profile/favorite/list', {
    targetType,
    targetId,
    pageNum: 1,
    pageSize: 1,
  });
  return page.records.length > 0;
}

/** 详情弹窗下半那张表：一行一个商品（后端按 orderId 一次全带，不分页） */
export interface OrderItemRow {
  id: number;
  /** 这一行买的是哪条规格；点回详情页要的是下面那个商品 id，两个不是一回事 */
  skuId: number | null;
  /** 由 sku_id 联到所属商品带出来的，那条规格被删过就是 null（详情页链接不出现） */
  productId: number | null;
  productName: string | null;
  productCode: string | null;
  image: string | null;
  specText: string | null;
  price: number | null;
  quantity: number | null;
  totalAmount: number | null;
  returnedQuantity: number | null;
  shipStatus: number | null;
  storeName: string | null;
}

/** 详情弹窗上半那块：列表那一行的格之外多出来的几格都在这（同一份 orderColumns，只是多带明细） */
export interface OrderDetail extends OrderRow {
  orderType: number | null;
  discountAmount: number | null;
  payType: number | null;
  payTime: string | null;
  remark: string | null;
  deliveryNo: string | null;
  items: OrderItemRow[] | null;
}

/** 不是自己名下、已删、根本没这个 id —— 后端回同一句「订单不存在」，那句话直接当提示用 */
export function fetchMyOrderDetail(id: number): Promise<OrderDetail> {
  return apiGet<OrderDetail>(`/api/web/shop/profile/order/${id}`);
}

/** 一行 = 某一天签过；没签的日子接口不给行，日历那一格由前端画成空的 */
export interface SigninRow {
  id: number;
  /** 后端钉了 @JsonFormat("yyyy-MM-dd")，所以这一格就是日历上那个串的本身，不用再切 */
  signDate: string;
  signType: number | null;
  /**
   * 最近连续几天：这张表上不存这一列（原来那列 continuous_days 已删），
   * 服务端从这个人最晚的那一天往前逐日回数（最多 7 天）现算，同一个数盖在返回的每一行上。
   */
  currentContinuous: number | null;
  points: number | null;
}

/**
 * 名下某个月签过的那几天。month 传 yyyy-MM，留空后端按当月 ——
 * 这一条只给登录者自己的，参数里也没有 user_id 那一格。
 */
export function fetchMySignins(month: string): Promise<SigninRow[]> {
  return apiGet<SigninRow[]>('/api/web/shop/profile/signin/list', { month });
}

/**
 * 「个人信息 → 基础信息」那一格：sys_user 上自己那一行 + 名下角色名（形状同后台 /sys/user/profile 那份 SysUserProfileVo）。
 *
 * password 服务端读出来就清了，所以这里根本没有那一格。
 * SysUser 上钉的是 NON_NULL —— 空的列直接不出现在 JSON 里，所以这一堆字段全按「可能没有」收。
 * areaCode / 经纬度 三列页面上不展示，但弹窗要把它们回填再送回去，所以这儿得有。
 */
export interface AccountInfo {
  id: number;
  username?: string | null;
  nickname?: string | null;
  avatar?: string | null;
  sex?: number | null;
  mobile?: string | null;
  email?: string | null;
  /** 逗号分隔的行政区划代码，弹窗按它拆回三级下拉 */
  areaCode?: string | null;
  areaZh?: string | null;
  address?: string | null;
  longitude?: number | null;
  latitude?: number | null;
  signature?: string | null;
  status?: number | null;
  accountType?: number | null;
  createTime?: string | null;
  lastLoginTime?: string | null;
}

export interface AccountProfile {
  sysUser?: AccountInfo | null;
  roles?: string[] | null;
}

/** 一次读一行，不分页；没有 id 参数，读的就是 token 背后那一个账号 */
export function fetchMyAccount(): Promise<AccountProfile> {
  return apiGet<AccountProfile>('/api/web/shop/profile/account');
}

/**
 * 改自己的密码：旧密码要填，服务端比对的是库里的 BCrypt，对不上回「旧密码错误」。
 * 改完不用重新登录 —— 这一条只换库里的密码串，token 还是那一个。
 */
export function changeMyPassword(oldPassword: string, newPassword: string): Promise<null> {
  return apiPut<null>('/api/web/shop/profile/password', { oldPassword, newPassword });
}

/**
 * 基本信息那几格（服务端只认这一串，形状同后台 /sys/user/myProfile 那份表单）。
 * 账号 / 账号类型 / 状态 / 角色 / 密码都不在里面 —— 传了也不认，id 由服务端钉成登录 id。
 *
 * 空着送上去等于「不改」而不是「清空」—— 这张表全局是 not_empty，这几格改得了清不掉。
 */
export interface AccountForm {
  nickname: string;
  mobile?: string | null;
  email?: string | null;
  sex?: number | null;
  /** 图片 URL，由 uploadAvatar 传上去拿回来；这一条不收文件 */
  avatar?: string | null;
  /** 逗号分隔的行政区划代码，和 areaZh 一起由三级下拉拼出来 */
  areaCode?: string | null;
  areaZh?: string | null;
  /** 详细地址（街道、门牌号），地图选点会带过来 */
  address?: string | null;
  longitude?: number | null;
  latitude?: number | null;
  signature?: string | null;
}

/** 改自己那几格基本信息：改完只有这一块要回刷，顶部那颗头像/昵称是登录时的会话快照，重登才换 */
export function updateMyAccount(form: AccountForm): Promise<null> {
  return apiPut<null>('/api/web/shop/profile/account', form);
}

/**
 * 传一张头像，回来的就是能落到 avatar 那一列的 URL。
 *
 * 走的是现成的 POST /api/file/uploadImage（shop-file 那份 FileApiController）—— 收文件的判据、
 * 压缩、存储平台都在那边，这一条不另开一份。它虽然钉了 @SaCheckLogin，但 SaTokenConfigure 把 /api/**
 * 整体排在拦截器之外，所以那道闸在这一路上其实没生效；能落到自己账号上的那一步靠的是下面那条 PUT
 *（服务层第一句 requireLoginUser），传完不点「确定」什么都不会变。
 * source 那一格是后端拿来分目录的（一天一个目录 + 一个来源子目录），后台上传组件传的也是 avatar。
 * 图片比一般请求慢，超时单独放宽到 60 秒。
 */
export function uploadAvatar(file: File): Promise<string> {
  return apiUpload<string>('/api/file/uploadImage', file, { source: 'avatar' }, undefined, { timeout: 60000 });
}

/**
 * 签今天 / 补签过去某一天：两颗都走 /api/comm 那两条（后台日历上补签压的是另一条真权限）。
 * 请求体里没有 user_id 可点名，能签的只有自己；返回的就是刚落下来的那一行（含这一天的分和连续天数）。
 */
export function signinToday(): Promise<SigninRow> {
  return apiPost<SigninRow>('/api/comm/activity/signin');
}

export function signinMakeup(signDate: string): Promise<SigninRow> {
  return apiPost<SigninRow>('/api/comm/activity/signin/makeup', { signDate });
}

/**
 * 「个人信息」那一块下面另两格（优惠券 / 积分）读的那三张表。
 * 都没有 userId 这一格可传：服务层第一句把它覆盖成登录 id，能点名别人就等于看别人的券包和账本。
 */

/** shop_activity_coupon_user 的一行：面额那几格是发放时定格的快照，后台改了模板不会串到已发出去的券上 */
export interface CouponRow {
  id: number;
  couponName: string | null;
  couponCode: string | null;
  couponType: number | null;
  /** 满减券/代金券是金额，折扣券是折扣率（0.9 = 九折） */
  faceValue: number | null;
  /** 使用门槛，0 = 无门槛 */
  thresholdAmount: number | null;
  /** 最高抵扣：折扣券用，null = 不封顶 */
  maxDiscount: number | null;
  status: number | null;
  getWay: number | null;
  receiveTime: string | null;
  useTime: string | null;
  expireTime: string | null;
  orderNo: string | null;
}

export type CouponPage = Paged<CouponRow>;

/** 名下一张张券：状态和券类型都由调用方筛（不传就是不限），按最新发的排 */
export function fetchMyCoupons(pageNum = 1, pageSize = 5, status?: number, couponType?: number): Promise<CouponPage> {
  return apiGet<CouponPage>('/api/web/shop/profile/coupon/list', { pageNum, pageSize, status, couponType });
}

/** 一位小数以下的数别拖一串 0：20.50 写成 20.5（同后台那一格） */
function numText(value: number | null | undefined): string {
  return value == null || !Number.isFinite(Number(value)) ? '' : String(Number(Number(value).toFixed(2)));
}

/**
 * 优惠那一格说成人话，按语种那一档：满 100 减 20 / 9折 / 免配送费，有上限就补一句。
 * 算式和中文句照旧，只有折扣那一档两边说法给的数不一样 —— 中文「9 折」拿面额×10，
 * 其余几档（英/日/韩）都说「砍掉百分之几」，拿 (1−面额)×100，
 * 所以按语种换算只在这一颗里分一次，页面两头都只调它。
 */
export function couponBenefit(i18n: I18n, row: CouponRow): string {
  const { d, locale } = i18n;
  const cap = numText(row.maxDiscount);
  const withCap = (text: string) => (cap && Number(cap) > 0 ? fill(d.benefit.cap, { n: cap }) : text);
  if (row.couponType === 3) return withCap(d.benefit.freeShipping);
  const face = numText(row.faceValue);
  if (!face || Number(face) <= 0) return d.common.dash;
  if (row.couponType === 2) {
    const rate = Number(row.faceValue);
    return withCap(fill(d.benefit.discount, { n: numText(locale === 'zh' ? rate * 10 : (1 - rate) * 100) }));
  }
  const threshold = Number(row.thresholdAmount) || 0;
  return withCap(
    threshold > 0
      ? fill(d.benefit.threshold, { a: numText(row.thresholdAmount), b: face })
      : fill(d.benefit.noThreshold, { n: face }),
  );
}

/**
 * 这张券过期了没。服务端把过期翻成 status=2 要等有人去刷那一档，名下一张张券里会掺着
 * 「status 还是 0 未使用、expireTime 却已经过了」的（下单时按 expireTime 拒），所以前台不能只信 status。
 * 时间串是 'yyyy-MM-dd HH:mm:ss'，换成 ISO 那副写法才不会被 Safari 判成非法日期；读不出数就当没过期，
 * 反正真过期那张提交时服务端还是会拦。
 */
export function couponExpired(row: CouponRow): boolean {
  if (!row.expireTime) return false;
  const time = new Date(row.expireTime.replace(' ', 'T')).getTime();
  return Number.isFinite(time) && time < Date.now();
}

/**
 * 「徽章」那一格读的那一张表：一行一枚（形状和上面优惠券那块对齐 —— 也是张记录表、也分页、
 * 同样没有 userId 这一格可传：服务层第一句把它覆盖成登录 id，能点名别人就等于看别人拿了哪些徽章）。
 * 名称/文字/图标那三格是发放那一刻定格的快照，后台后来改了徽章不会串到已经拿到手的这一枚上。
 */

/** shop_activity_badge_user 的一行：一枚一行，拿到那一刻叫什么、长什么样都看这一行的快照三格 */
export interface BadgeRow {
  id: number;
  badgeId: number;
  badgeName: string | null;
  /** 徽章那几个字（没配图时用；图优先） */
  badgeText: string | null;
  badgeIcon: string | null;
  source: number | null;
  /** 来源单据：消费落订单 id、签到落那条签到记录的 id，后台手工发的留空 */
  sourceId: number | null;
  /** 拿到那一刻（服务端盖的） */
  startTime: string | null;
  /** 有效期到，null = 永久 */
  endTime: string | null;
  status: number | null;
  /** 是否展示：1 挂在账号卡下面那排挂件上、0 收起来（这一格由本人改，就是每行那颗「展示 / 收起」） */
  isShow?: number | null;
  /** 徽章类型（联定义表带出，非本表列） */
  badgeType: number | null;
}

export type BadgePage = Paged<BadgeRow>;

/** 名下一枚枚徽章：获得方式和状态都由调用方筛（不传就是不限），最新拿到的一枚在前 */
export function fetchMyBadges(pageNum = 1, pageSize = 5, source?: number, status?: number): Promise<BadgePage> {
  return apiGet<BadgePage>('/api/web/shop/profile/badge/list', { pageNum, pageSize, source, status });
}

/**
 * 收起 / 展示名下一枚徽章：只动 is_show 那一格，服务端不重读这一行回来，
 * 所以调用方改成功就把本地那一格就地翻过来（挂件那一排跟着它走）。
 * 归属在服务层无条件比登录 id，所以传别人的行 id 回的是同一句「徽章不存在」。
 */
export function setBadgeShow(id: number, isShow: number): Promise<null> {
  return apiPut<null>('/api/web/shop/profile/badge/show', { id, isShow });
}

/**
 * 这一枚过期了没。和上面那张券同一个道理：status 翻成 2 已过期那一档要等有人去刷，
 * 名下一枚枚里会掺着「status 还是 1 有效、endTime 却已经过了」的，所以前台不能只信 status。
 * endTime 留空是永久有效（后台下发弹窗里那一格留空发出去的就是永久），读不出数也当没过期。
 */
export function badgeExpired(row: BadgeRow): boolean {
  if (!row.endTime) return false;
  const time = new Date(row.endTime.replace(' ', 'T')).getTime();
  return Number.isFinite(time) && time < Date.now();
}

/** shop_activity_points 那一行：五个数是存的、读时不做现算（恒等式由写入方在同一个事务里守住） */
export interface PointsAccount {
  id: number;
  balance: number | null;
  totalPoints: number | null;
  usedPoints: number | null;
  frozenPoints: number | null;
  expirePoints: number | null;
  createTime: string | null;
}

/**
 * 积分账户那一行：一分都没获得过就没有这一行账（账是由第一次积分变动带出来的），
 * 那时后端回 null —— 这一句要画成「还没有积分账户」，不能报成错误。
 */
export function fetchMyPoints(): Promise<PointsAccount | null> {
  return apiGet<PointsAccount | null>('/api/web/shop/profile/points');
}

/** shop_activity_points_log 的一行：points 带符号（获取为正、消耗为负），balance 是那一笔之后的余额 */
export interface PointLogRow {
  id: number;
  points: number | null;
  balance: number | null;
  bizType: number | null;
  bizNo: string | null;
  description: string | null;
  expireTime: string | null;
  createTime: string | null;
}

export type PointLogPage = Paged<PointLogRow>;

export function fetchMyPointLogs(pageNum = 1, pageSize = 5, bizType?: number): Promise<PointLogPage> {
  return apiGet<PointLogPage>('/api/web/shop/profile/points/log/list', { pageNum, pageSize, bizType });
}

/**
 * 「钱包」那一格读的两张表：账户一行、流水一张（形状和上面积分那一对完全对齐）。
 * 同样没有 userId 这一格可传：服务层第一句把它钉成登录 id，能点名别人就等于看别人的账本。
 */

/** shop_consumer_wallet 那一行：几个数都是存的、读时不做现算（恒等式由写入方在同一个事务里守住） */
export interface WalletAccount {
  id: number;
  /** 可用余额：结算页「余额支付」能用的就是这一格 */
  balance: number | null;
  /** 冻结中：下单选余额支付时占住的，支付成功才真扣走、取消订单退回来 */
  frozenBalance: number | null;
  giftBalance: number | null;
  totalRecharge: number | null;
  totalConsume: number | null;
  totalWithdraw: number | null;
  status: number | null;
  createTime: string | null;
}

/**
 * 钱包账户那一行：一分钱都没进过账就没有这一行（账是由第一次余额变动带出来的），
 * 那时后端回 null —— 这一句要画成「还没有钱包账户」，不能报成错误。
 */
export function fetchMyWallet(): Promise<WalletAccount | null> {
  return apiGet<WalletAccount | null>('/api/web/shop/profile/wallet');
}

/** shop_consumer_wallet_log 的一行：amount 带符号（收入为正、支出为负），balance 是这一笔之后的可用余额 */
export interface WalletLogRow {
  id: number;
  amount: number | null;
  balance: number | null;
  bizType: number | null;
  bizId: number | null;
  bizNo: string | null;
  description: string | null;
  createTime: string | null;
}

export type WalletLogPage = Paged<WalletLogRow>;

export function fetchMyWalletLogs(pageNum = 1, pageSize = 5, bizType?: number): Promise<WalletLogPage> {
  return apiGet<WalletLogPage>('/api/web/shop/profile/wallet/log/list', { pageNum, pageSize, bizType });
}

/**
 * 充值 / 提现的入参（两颗都走 /api/comm/shop/wallet/recharge、/withdraw，两端共用）。
 *
 * 只有金额和说明两格：账上那几个数不在参数里 —— 这一本是账，余额只能由充值进、提现出、
 * 下单占、退款还这些动作自己加加减减，表单能填余额就等于允许"账上多了 500 块、流水里什么都没有"。
 * 也没有 userId：服务层第一句把它钉成登录 id，能点名别人就等于花别人的钱。
 */
export interface WalletChangeForm {
  /** 元，大于 0、最多两位小数（库里那一列是 decimal(14,2)，多于两位服务端是拒的，不是四舍五入） */
  amount: number;
  /** 选填，原样写进流水的说明；留空由服务端按动作补一句默认的 */
  description?: string;
}

/**
 * 充值 / 提现：回的是改完之后的那一行账户，所以点成功不必再捞一次 /profile/wallet
 *（但流水那张表要重拉，多出的是刚记下的那一笔）。
 * 第一次充值会把那一行账户建出来 —— 这一本没有"新增账户"的入口，账行由第一次真收钱的动作带出来。
 */
export function rechargeWallet(form: WalletChangeForm): Promise<WalletAccount> {
  return apiPost<WalletAccount>('/api/comm/shop/wallet/recharge', form);
}

export function withdrawWallet(form: WalletChangeForm): Promise<WalletAccount> {
  return apiPost<WalletAccount>('/api/comm/shop/wallet/withdraw', form);
}
