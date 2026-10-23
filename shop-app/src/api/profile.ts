import type {
  IPageRes,
  IShopAccount,
  IShopAccountForm,
  IShopAddressForm,
  IShopAddressRow,
  IShopBadgeRow,
  IShopBrowseRow,
  IShopCouponRow,
  IShopFavoriteRow,
  IShopOrderDetail,
  IShopOrderRow,
  IShopPointsAccount,
  IShopPointsLogRow,
  IShopSigninRow,
  IShopWalletAccount,
  IShopWalletChangeForm,
  IShopWalletLogRow,
} from './types/shop'
import { http } from '@/http/http'
import { uploadImage } from './file'

/**
 * 个人中心（App 端）。
 *
 * 路径统一在 /api/app 下，控制器转一手到 Web 端同一个 service 的同一个方法：
 * 两端看到的是同一批数据，判据只有一处。每一条都要求登录态 —— /api/** 不过 Sa-Token 拦截器，
 * 「必须登录」由服务层第一句 requireLoginUser 当场拒掉，没登录回业务错、http 层 toast；
 * 页面自己先用 token store 拦一道，免得白报一次。
 *
 * 只有读的那几条不传 userId：传了也会被服务层覆盖成登录 id（能点名别人就等于看别人的单、券、账本）。
 * 分页吃查询串里的 pageNum/pageSize，信封和商品列表同一形状。
 * 写口子有「改基础信息」这一条和地址那三条增删改（改密码、收藏 toggle 那几条 App 页面还没铺）；
 * 文件末尾的充值 / 提现不在这条口径里 —— 那是两端共用的**动作**，走 /api/comm，后台那一份压的同一对接口。
 */

/** 读账号那一行 + 名下角色，一次一行所以不分页 */
export function getMyAccount() {
  return http.get<IShopAccount>('/api/app/shop/profile/account')
}

/**
 * 修改自己的基本信息：服务端只搬表单里那几格，id 钉成登录 id。
 * 全局 field-strategy=not_empty，所以空着送上去等于「不改」而不是「清空」—— 这几格改得了、清不掉。
 */
export function updateMyAccount(form: IShopAccountForm) {
  return http.put<void>('/api/app/shop/profile/account', form)
}

/**
 * 我的收货地址：「地址管理」那一页和结算页挑地址都读这一条。
 *
 * 默认那条排在第一（SQL 钉的 ORDER BY is_default DESC, id DESC），所以结算页取第一颗就是默认地址。
 * 结算页只要挑一颗，一次拉 50 条够了，不做分页控件。
 * silent 那档给「进页面顺手看一眼有没有地址」用：读不到就别把后端那句「请先登录」弹出来。
 */
export function getMyAddresses(pageNum = 1, pageSize = 50, silent = false) {
  return http.get<IPageRes<IShopAddressRow>>('/api/app/shop/profile/address/list', { pageNum, pageSize }, undefined, { hideErrorToast: silent })
}

/** 按 id 捞自己名下的那一条：App 端没有 byId 这条口（后台那一条压着 shop:address:query 权限），就从这一列里找 */
export async function getMyAddress(id: number) {
  const page = await getMyAddresses(1, 50)
  return page.records.find(one => one.id === id) ?? null
}

/**
 * 新增一条收货地址：userId 不送，服务层第一句钉成登录 id。
 * 「这个账号的第一条地址直接当默认」也在服务层，所以表单上那格 isDefault 照实送就行。
 * 存完要整块重拉这一列 —— 设默认会改到别的行，就地改一份本地数据会画出两颗默认。
 */
export function addMyAddress(form: IShopAddressForm) {
  return http.post<IShopAddressRow>('/api/app/shop/profile/address', form)
}

/** 修改一条：整条覆盖式提交，area_code / area_zh / tag / 邮编 / 经纬度 不传就是清成空（实体上钉的 IGNORED） */
export function updateMyAddress(form: IShopAddressForm) {
  return http.put<IShopAddressRow>('/api/app/shop/profile/address', form)
}

/** 删一条（逻辑删除）；删掉的正好是默认那条不自动补选，所以调用方要重新挑一颗 */
export function deleteMyAddress(id: number) {
  return http.delete<void>(`/api/app/shop/profile/address/${id}`)
}

/**
 * 传一张头像，回来的就是能落到 avatar 那一格的 URL。
 *
 * 上传本体收在 src/api/file.ts 的 uploadImage（评论配图也走那一条，只是 source 换一格）：
 * multipart、基准地址、token 头、拆信封那一套只在一处，两处各写一遍早晚对不上。
 */
export function uploadAvatar(filePath: string) {
  return uploadImage(filePath, 'avatar')
}

/** 名下某个月签过的那几天（month 传 yyyy-MM，留空按当月）；签到按钮用它判今天签没签、连签几天 */
export function getMySignins(month?: string) {
  return http.get<IShopSigninRow[]>('/api/app/shop/profile/signin/list', { month })
}

/** 我的订单：一行一张订单，不含明细行 */
export function getMyOrders(pageNum: number, pageSize: number) {
  return http.get<IPageRes<IShopOrderRow>>('/api/app/shop/profile/order/list', { pageNum, pageSize })
}

/** 订单详情：主表 + 名下全部明细行；别人的、已删的、没这个 id 一律回「订单不存在」 */
export function getMyOrderDetail(id: number) {
  return http.get<IShopOrderDetail>(`/api/app/shop/profile/order/${id}`)
}

/** 我的收藏：商品/门店/品牌共用一张表，targetType 传 1/2/3 就是只筛那一类 */
export function getMyFavorites(pageNum: number, pageSize: number, targetType?: number) {
  return http.get<IPageRes<IShopFavoriteRow>>('/api/app/shop/profile/favorite/list', { pageNum, pageSize, targetType })
}

/** 浏览足迹：一条一个商品，按最近浏览时间倒序 */
export function getMyBrowses(pageNum: number, pageSize: number) {
  return http.get<IPageRes<IShopBrowseRow>>('/api/app/shop/profile/browse/list', { pageNum, pageSize })
}

/** 我的优惠券：status（0未使用 1已使用 2已过期 3已作废）和 couponType 两档都可选筛 */
export function getMyCoupons(pageNum: number, pageSize: number, status?: number, couponType?: number) {
  return http.get<IPageRes<IShopCouponRow>>('/api/app/shop/profile/coupon/list', { pageNum, pageSize, status, couponType })
}

/** 我的徽章：source（获得方式）和 status 两档都可选筛，最新拿到的一枚在前 */
export function getMyBadges(pageNum: number, pageSize: number, source?: number, status?: number) {
  return http.get<IPageRes<IShopBadgeRow>>('/api/app/shop/profile/badge/list', { pageNum, pageSize, source, status })
}

/** 积分账户那一行；一分都没获得过时回 null（那不是错，页面说成「还没有积分账户」） */
export function getMyPointsAccount() {
  return http.get<IShopPointsAccount | null>('/api/app/shop/profile/points')
}

/** 积分流水：bizType 可选筛（1签到 … 10管理员调整），最新一笔在前 */
export function getMyPointsLogs(pageNum: number, pageSize: number, bizType?: number) {
  return http.get<IPageRes<IShopPointsLogRow>>('/api/app/shop/profile/points/log/list', { pageNum, pageSize, bizType })
}

/** 钱包账户那一行；一分钱都没进过账时回 null，理由同上 */
export function getMyWalletAccount() {
  return http.get<IShopWalletAccount | null>('/api/app/shop/profile/wallet')
}

/** 钱包流水：bizType 可选筛（1充值 2消费 3退款 …），最新一笔在前 */
export function getMyWalletLogs(pageNum: number, pageSize: number, bizType?: number) {
  return http.get<IPageRes<IShopWalletLogRow>>('/api/app/shop/profile/wallet/log/list', { pageNum, pageSize, bizType })
}

/**
 * 余额充值：可用余额和累计充值一起加这一笔，同一事务里补一行「1 充值」流水。
 *
 * 路径：POST /api/comm/shop/wallet/recharge（comm 要写双前缀）。充值和提现是两端共用的**动作**
 * ——后台那一页、Web 前台那一页压的是同一对接口，判据只在服务层那一份，所以不在 app 包再转手一条。
 * 入参只有金额和说明两格，没有 userId：服务层第一句把它钉成登录 id（能点名别人就等于花别人的钱）。
 * 第一次充值会把那一行账户建出来，所以「还没开账」的人点充值是对的、点提现会被说清是 0 元。
 * 回的是改完之后那一行账，页面拿它直接盖掉旧的那一份，不必再捞一次。
 */
export function rechargeWallet(form: IShopWalletChangeForm) {
  return http.post<IShopWalletAccount>('/api/comm/shop/wallet/recharge', form)
}

/** 余额提现：可用余额减、累计提现加同一笔，补一行「4 提现」流水；冻结中那一部分（下单没付占的）提不走，账户被冻住也提不走 */
export function withdrawWallet(form: IShopWalletChangeForm) {
  return http.post<IShopWalletAccount>('/api/comm/shop/wallet/withdraw', form)
}
