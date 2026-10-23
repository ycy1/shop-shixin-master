/**
 * 配送单的六个时间节点：新增/修改表单（el-date-picker）和详情弹窗（只读文本）都按这一份顺序和取名，
 * 抄一遍就会在两边对不上。
 */
export const deliveryTimeNodes = [
  { label: '预计送达', prop: 'expectTime' },
  { label: '派单时间', prop: 'assignTime' },
  { label: '接单时间', prop: 'acceptTime' },
  { label: '取货时间', prop: 'pickupTime' },
  { label: '送达时间', prop: 'deliveredTime' },
  { label: '取消时间', prop: 'cancelTime' }
]

/**
 * 配送时长的估算参数，和后端 ShopDeliveryServiceImpl 里那两个常量是同一套口径
 * （取货/交接固定耗时 + 平均速度）。改一头就要改另一头，否则弹窗上预览的分钟数和落库的对不上。
 */
export const DELIVERY_PICKUP_MINUTES = 10
export const DELIVERY_AVG_SPEED_KMH = 15

/** 地球半径（km），和 MySQL ST_Distance_Sphere 用的那个平均值一致 */
const EARTH_RADIUS_KM = 6371.0088

const toPointNumber = (value: any) =>
  value === null || value === undefined || value === '' ? undefined : Number(value)

/**
 * 按「发货点 → 收件点」这两个地点估算配送数据：球面直线距离（km，2 位小数）+ 折算出来的分钟数。
 * 两个点缺任何一边就返回 null，调用方把那两格清空。
 *
 * 只是给表单/弹窗实时预览用：真正存进去的那两个数是后端算的（距离走 ST_Distance_Sphere，
 * 见 ShopDeliveryServiceImpl#fillEstimate），两边差在毫米级，不影响显示的分钟数。
 */
export const estimateByPoints = (senderLng: any, senderLat: any, receiverLng: any, receiverLat: any) => {
  const [sLng, sLat, rLng, rLat] = [
    toPointNumber(senderLng),
    toPointNumber(senderLat),
    toPointNumber(receiverLng),
    toPointNumber(receiverLat)
  ]
  if ([sLng, sLat, rLng, rLat].some((n) => n === undefined || !Number.isFinite(n))) {
    return null
  }
  const rad = (deg: number) => (deg * Math.PI) / 180
  const dLat = rad(rLat! - sLat!)
  const dLng = rad(rLng! - sLng!)
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(sLat!)) * Math.cos(rad(rLat!)) * Math.sin(dLng / 2) ** 2
  const km = Number((2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(h)))).toFixed(2))
  return {
    distance: km,
    estimatedTime: Math.ceil((km * 60) / DELIVERY_AVG_SPEED_KMH) + DELIVERY_PICKUP_MINUTES
  }
}

/**
 * 预计时长的显示口径：库里存的就是分钟数（`estimated_time` 那一列 int），给人看要拆成天/小时/分钟
 * （2026-09-21 他一句"预计时长 展示为天 小时 分钟"）。详情、配送管理表单两处共用这一份。
 * 值为 0 的单位省略（"1小时5分钟"而不是"0天1小时5分钟"），整格是 0 分钟才写"0分钟"；
 * 没算出来（空/非数字）返回空串，由调用方决定显示占位还是破折号 —— 别在这里塞"-"，那样校验不过的格子也会显得有值。
 */
export const formatDuration = (minutes: any): string => {
  if (minutes === null || minutes === undefined || minutes === '') return ''
  const total = Math.floor(Number(minutes))
  if (!Number.isFinite(total) || total < 0) return ''
  const days = Math.floor(total / (24 * 60))
  const hours = Math.floor((total % (24 * 60)) / 60)
  const rest = total % 60
  const parts: string[] = []
  if (days) parts.push(`${days}天`)
  if (hours) parts.push(`${hours}小时`)
  if (rest || !parts.length) parts.push(`${rest}分钟`)
  return parts.join('')
}
