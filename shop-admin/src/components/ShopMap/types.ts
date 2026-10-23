/** 一次点选的结果：坐标 + 逆地理出来的地址（逆地理失败时地址为空串） */
export interface PickedPoint {
  longitude: number
  latitude: number
  address: string
  /** 逆地理还没回来；父组件据此判断地址稍后还会再更新一次 */
  resolving: boolean
  /** 逆地理失败原因（高德给的 status / info），成功时为空 */
  message?: string
}
