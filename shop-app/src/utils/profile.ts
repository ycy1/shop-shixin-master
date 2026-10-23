import type { IShopBadgeRow, IShopCouponRow } from '@/api/types/shop'

/**
 * 个人中心那几块共用的中文称呼和格式化，口径照 shop-web 的 i18n 枚举档 + lib/profile.ts。
 *
 * App 这边不做多语言（那四档字典在 shop-web 里），所以码值→中文先写死在工程这一份里：
 * 后台改字典不会同步到这儿，但前台要认的档就那么几个，比对不上一律回兜底句而不是空白。
 */

/** sys_user.account_type：0 平台 1 门店 2 送货员 3 消费者 4 供应商 5 ERP 操作员 */
export const ACCOUNT_TYPE_TEXT: Record<number, string> = {
  0: '平台用户',
  1: '门店',
  2: '送货员',
  3: '消费者',
  4: '供应商',
  5: 'ERP 操作员',
}

/** sys_order.status：0 待支付 1 已支付 2 已完成 3 已取消 4 已退款 5 已关闭 */
export const ORDER_STATUS_TEXT: Record<number, string> = {
  0: '待支付',
  1: '已支付',
  2: '已完成',
  3: '已取消',
  4: '已退款',
  5: '已关闭',
}

/** shop_order_item.ship_status：0 待发货 1 已发货 2 已退货 3 已出库 4 部分退货 */
export const SHIP_STATUS_TEXT: Record<number, string> = {
  0: '待发货',
  1: '已发货',
  2: '已退货',
  3: '已出库',
  4: '部分退货',
}

/** sys_order.pay_type：1 微信 2 支付宝 3 银行卡 4 余额（没付过的单这一格是 null） */
export const PAY_TYPE_TEXT: Record<number, string> = {
  1: '微信支付',
  2: '支付宝',
  3: '银行卡',
  4: '余额支付',
}

/** shop_activity_coupon_user.status：0 未使用 1 已使用 2 已过期 3 已作废 */
export const COUPON_STATUS_TEXT: Record<number, string> = {
  0: '未使用',
  1: '已使用',
  2: '已过期',
  3: '已作废',
}

/** shop_activity_coupon.coupon_type：1 满减 2 折扣 3 免邮 4 代金 */
export const COUPON_TYPE_TEXT: Record<number, string> = {
  1: '满减券',
  2: '折扣券',
  3: '免邮券',
  4: '代金券',
}

/** shop_activity_coupon_user.get_way：1 主动领取 … 6 徽章发放 */
export const GET_WAY_TEXT: Record<number, string> = {
  1: '主动领取',
  2: '系统发放',
  3: '活动赠送',
  4: '新人礼',
  5: '积分兑换',
  6: '徽章发放',
}

/** shop_activity_badge_user.status：0 失效 1 有效（两档；「2 已过期」2026-10-17 整条下线，过期由 badgeValid 拿 endTime 现比，不占这一格）。0 由后台「获取记录」那颗状态开关写 */
export const BADGE_STATUS_TEXT: Record<number, string> = {
  0: '失效',
  1: '有效',
}

/** shop_activity_badge_user.source：1 系统发放 … 5 门店发放 */
export const BADGE_SOURCE_TEXT: Record<number, string> = {
  1: '系统发放',
  2: '活动获得',
  3: '消费达标',
  4: '手动发放',
  5: '门店发放',
}

/** shop_activity_badge.badge_type：1 门店 2 活动 3 签到 4 消费 */
export const BADGE_TYPE_TEXT: Record<number, string> = {
  1: '门店',
  2: '活动',
  3: '签到',
  4: '消费',
}

/** shop_activity_points_log.biz_type：1 签到 … 10 管理员调整 */
export const POINTS_BIZ_TEXT: Record<number, string> = {
  1: '签到',
  2: '购物',
  3: '评价',
  4: '分享',
  5: '邀请',
  6: '注册',
  7: '兑换',
  8: '抵扣',
  9: '过期',
  10: '管理员调整',
  11: '徽章发放',
}

/** shop_consumer_wallet_log.biz_type：1 充值 2 消费 3 退款 … 8 管理员调整 */
export const WALLET_BIZ_TEXT: Record<number, string> = {
  1: '充值',
  2: '消费',
  3: '退款',
  4: '提现',
  5: '奖励',
  6: '冻结',
  7: '解冻',
  8: '管理员调整',
}

/** shop_consumer_wallet.status：0 冻结 1 正常 */
export const WALLET_STATUS_TEXT: Record<number, string> = {
  0: '冻结',
  1: '正常',
}

/** shop_consumer_favorite.target_type：1 商品 2 门店 3 品牌 */
export const FAVORITE_TARGET_TEXT: Record<number, string> = {
  1: '商品',
  2: '门店',
  3: '品牌',
}

/** sys_user.sex：0 保密 1 男 2 女（这三档没有字典） */
export const SEX_TEXT: Record<number, string> = {
  0: '保密',
  1: '男',
  2: '女',
}

/** 认不出的码值（后台新加档而前台没跟上）统一说这一句，不留空白 */
export function codeText(map: Record<number, string>, code: number | null | undefined) {
  if (code == null) {
    return ''
  }
  return map[code] ?? `未知（${code}）`
}

/** 后端给的是 'yyyy-MM-dd HH:mm:ss' 定长串，页面上大多只要「哪天」；空格子画破折号 */
export function dayOf(value: string | null | undefined) {
  return value ? value.slice(0, 10) : '—'
}

/** 今天这一天，'yyyy-MM-dd' —— 签到记录里的 signDate 就是这个格式，拿来直接比 */
export function todayText() {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

export function moneyOf(value: number | null | undefined) {
  return value == null ? '—' : `¥${Number(value).toFixed(2)}`
}

/** 流水里的变动金额：库里带符号存（收入为正、支出为负），补正号让进出一眼分开 */
export function signedMoney(value: number | null | undefined) {
  const amount = Number(value ?? 0)
  const text = `¥${Math.abs(amount).toFixed(2)}`
  if (amount > 0) {
    return `+${text}`
  }
  return amount < 0 ? `-${text}` : text
}

export function pointsOf(value: number | null | undefined) {
  return String(Number(value ?? 0))
}

/** 积分流水里的变动数：同样带符号存（获取正、消耗负） */
export function signedPoints(value: number | null | undefined) {
  const count = Number(value ?? 0)
  return count > 0 ? `+${count}` : String(count)
}

/** 两位小数以内别拖一串 0：20.50 写成 20.5（同后台那一格） */
function numText(value: number | null | undefined) {
  const n = Number(value)
  return Number.isFinite(n) ? String(Number(n.toFixed(2))) : ''
}

/**
 * 券面那一句优惠说成人话：满 100 减 20 / 无门槛减 5 / 9折 / 免配送费，有上限就补一句。
 * 折扣那一档库里存的是折扣率（0.9），中文说「9 折」所以 ×10 —— 换算只在这一颗里做。
 */
export function couponBenefit(row: IShopCouponRow): string {
  const cap = numText(row.maxDiscount)
  const withCap = (text: string) => (cap && Number(cap) > 0 ? `${text}（最高抵 ${cap} 元）` : text)
  if (row.couponType === 3) {
    return withCap('免配送费')
  }
  const face = numText(row.faceValue)
  if (!face || Number(face) <= 0) {
    return '—'
  }
  if (row.couponType === 2) {
    return withCap(`${numText(Number(row.faceValue) * 10)}折`)
  }
  const threshold = Number(row.thresholdAmount) || 0
  return withCap(threshold > 0 ? `满 ${numText(row.thresholdAmount)} 减 ${face}` : `无门槛减 ${face}`)
}

/** 时间串换成 ISO 那副写法才不会被某些内核判成非法日期；读不出数就当没过期（提交时服务端还会拦一次） */
function isPast(value: string | null | undefined) {
  if (!value) {
    return false
  }
  const time = new Date(value.replace(' ', 'T')).getTime()
  return Number.isFinite(time) && time < Date.now()
}

/**
 * 这张券过期了没：status 翻成 2 已过期要等有人去刷，名下一张张券里会掺着
 *「status 还是 0 未使用、expireTime 却已经过了」的，所以前端不能只信 status。
 */
export function couponExpired(row: IShopCouponRow) {
  return row.status === 0 && isPast(row.expireTime)
}

/** 这一枚徽章过期了没，同一张券一个道理；endTime 留空是永久有效 */
export function badgeExpired(row: IShopBadgeRow) {
  return isPast(row.endTime)
}

/** 现在还算不算数：status 是 1 有效、endTime 又没过去 */
export function badgeValid(row: IShopBadgeRow) {
  return (row.status ?? 1) === 1 && !badgeExpired(row)
}

export function badgeStatusText(row: IShopBadgeRow) {
  return badgeValid(row) ? '有效' : badgeExpired(row) ? '已过期' : codeText(BADGE_STATUS_TEXT, row.status)
}
