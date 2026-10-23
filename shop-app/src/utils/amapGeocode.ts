/**
 * 拿一句文字地址去高德换一个点（GCJ-02，和地图选点、库里那两列同一个坐标系，不用换算）。
 *
 * 只有 H5 走得起：那份 JSAPI 是浏览器端的脚本，小程序和 App 里没有 DOM 加载不起来（分法同
 * components/MapPicker —— 那一端要坐标只能 uni.chooseLocation 点一下）。
 * 所以这一格给的是「有就填、没有就算了」：认不出来回 null，页面那两格照旧空着等他选。
 */

/** 高德按文字地址换回来的坐标 */
export interface IGeoPoint {
  longitude: number
  latitude: number
}

const LOAD_TIMEOUT_MS = 15000

// #ifdef H5
async function geocodeOnWeb(query: string, city?: string): Promise<IGeoPoint | null> {
  const text = query.trim()
  if (!text) {
    return null
  }
  try {
    const loader = (await import('@amap/amap-jsapi-loader')).default
    // 安全密钥要在 loader 之前挂到 window 上，否则 2.0 的 JSAPI 会拒（同 MapPicker 那一段）
    ;(window as any)._AMapSecurityConfig = {
      securityJsCode: import.meta.env.VITE_AMAP_SECURITY_JS_CODE as string,
    }
    const timeout = new Promise((_, reject) => {
      setTimeout(() => reject(new Error(`高德 JSAPI ${LOAD_TIMEOUT_MS / 1000}s 内没回来`)), LOAD_TIMEOUT_MS)
    })
    const AMap = await Promise.race([
      loader.load({
        key: import.meta.env.VITE_AMAP_API_KEY as string,
        version: '2.0',
        plugins: ['AMap.Geocoder'],
      }),
      timeout,
    ]) as any
    // 选到市就把市递给 Geocoder，只写到省或没写就让它按全国找
    const geocoder = new AMap.Geocoder({ city: city || '全国' })
    return await new Promise<IGeoPoint | null>((resolve) => {
      geocoder.getLocation(text, (status: string, result: any) => {
        const geo = status === 'complete' && result?.info === 'OK' ? result.geocodes?.[0] : null
        resolve(geo ? { longitude: geo.location.getLng(), latitude: geo.location.getLat() } : null)
      })
    })
  }
  catch (err) {
    console.warn('按详细地址取坐标失败：', err)
    return null
  }
}
// #endif

export async function geocodeByAddress(query: string, city?: string): Promise<IGeoPoint | null> {
  let point: IGeoPoint | null = null
  // #ifdef H5
  point = await geocodeOnWeb(query, city)
  // #endif
  return point
}
