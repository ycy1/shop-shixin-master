import { reactive, ref } from 'vue'

/** 一趟飞行动画：from 是「加入购物车」那颗按钮的中心，to 是落点（详情页那颗悬浮购物车）的中心 */
export interface Flight {
  id: number
  x: number
  y: number
  /** 相对起点的位移：横向匀速（linear）、竖向越落越快（ease-in 二次曲线），两条叠出来就是抛物线 */
  dx: number
  dy: number
  /** false = 还画在起点那一帧，true = 位移已经改到终点，transition 正在跑 */
  flying: boolean
}

export const flights = ref<Flight[]>([])

/** 飞行时长：和 FlyToCart.vue 里两条 transition 用的是同一个数 */
export const FLY_MS = 620

let seq = 0

/**
 * 从 from 飞一颗点到 to。两个点都是调用方自己量好的视口坐标（px），这一层不认业务、也不查节点。
 */
export function flyTo(from: { x: number, y: number }, to: { x: number, y: number }) {
  const one = reactive<Flight>({
    id: ++seq,
    x: from.x,
    y: from.y,
    dx: to.x - from.x,
    dy: to.y - from.y,
    flying: false,
  })
  flights.value.push(one)
  // 起点那一帧得先画出来，下一拍才把位移改成终点：同一帧里改，transition 不认（小程序没有 rAF，只能用 setTimeout）
  setTimeout(() => {
    one.flying = true
  }, 30)
  setTimeout(() => {
    flights.value = flights.value.filter(item => item.id !== one.id)
  }, FLY_MS + 120)
}
