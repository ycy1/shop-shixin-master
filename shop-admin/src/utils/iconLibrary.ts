import { reactive } from 'vue'
import { optionsIconApi } from '@/api/system/icon'

/** sys_icon 里渲染要用到的那几格 */
export interface IconRecord {
  id: number
  iconName: string
  iconType: number
  iconValue: string
  iconCategory?: number
}

/** 已加载的图标（reactive，模板直接响应；key 是 id 的字符串形式） */
const iconCache = reactive<Record<string, IconRecord>>({})
let request: Promise<void> | null = null
let loaded = false

/**
 * 拉一次全量启用图标，整站共用一份（菜单图标可能出现在侧边栏、面包屑、菜单管理三处，
 * 每处各发一次请求没意义）。数据量是图标库那一小张表，所以一次取全、不按分类取。
 * 失败不置 loaded，下次调用会重试（口径同 utils/dictCache）
 */
export const ensureIcons = async (): Promise<void> => {
  if (request) return request
  request = (async () => {
    try {
      const { data } = await optionsIconApi()
      ;(data || []).forEach((item: IconRecord) => {
        iconCache[String(item.id)] = item
      })
      loaded = true
    } catch (error) {
      // 让下一次调用还能重试出去
      request = null
    }
  })()
  return request
}

/** 按 id 取一条图标；没加载完或那条已禁用/已删除时给 undefined，由调用方决定显示什么 */
export const iconRecord = (id: number | string): IconRecord | undefined => iconCache[String(id)]

/** 全量取回来了没有：取回来 yet 却查不到，才是真的没有（显示占位符），否则只是还没到 */
export const iconsReady = (): boolean => loaded

/** 图标库被改过之后刷一遍：改了 SVG 内容、禁用了某条，侧边栏不该还拿着旧的显示 */
export const reloadIcons = async (): Promise<void> => {
  Object.keys(iconCache).forEach((key) => delete iconCache[key])
  request = null
  loaded = false
  await ensureIcons()
}
