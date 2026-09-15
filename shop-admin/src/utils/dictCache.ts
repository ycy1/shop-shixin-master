import { reactive } from 'vue'
import { getDictDataByDictTypesApi, refreshDictCacheApi } from '@/api/system/dict'

/** el-tag 支持的 type */
export type DictTagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

/** 对应后端 sys_dict_data */
export interface DictItem {
  label: string
  value: string
  style?: DictTagType
  sort?: number
  isDefault?: number
  remark?: string
  [key: string]: any
}

/** 供 el-select 使用的选项，value 已按需转为数字 */
export interface DictOption {
  label: string
  value: string | number
  style?: DictTagType
}

interface DictEntry {
  /** 原始字典项 */
  list: DictItem[]
  /** el-select 选项 */
  options: DictOption[]
  /** 后端 isDefault=1 那条的 value */
  defaultValue: string | null
}

/** 接口形状：{ order_type: { defaultValue, list }, ... } */
interface DictApiResult {
  [dictType: string]: { defaultValue?: string | null; list?: DictItem[] } | undefined
}

/** 已加载的字典（reactive，模板可直接响应） */
const dictCache = reactive<Record<string, DictEntry>>({})
/** 加载状态 */
const loadingMap = reactive<Record<string, boolean>>({})
/** 请求中的类型，避免同一字典并发重复请求 */
const pending = new Set<string>()

/**
 * 字典值是字符串，而实体里的枚举字段多为 Integer，
 * el-select 回显用全等匹配，不转数字会导致选中项显示不出 label
 */
const toValue = (value: string): string | number => {
  if (value === '') return value
  const num = Number(value)
  return isNaN(num) ? value : num
}

const buildEntry = (raw?: { defaultValue?: string | null; list?: DictItem[] }): DictEntry => {
  const list = raw?.list ?? []
  return {
    list,
    options: list.map(item => ({ label: item.label, value: toValue(item.value), style: item.style })),
    defaultValue: raw?.defaultValue ?? null
  }
}

/**
 * 确保字典已加载（已加载或请求中的类型会跳过）
 * 请求失败不会写缓存，下次调用会重试
 */
export const ensureDicts = async (types: string[]): Promise<void> => {
  const todo = Array.from(new Set(types)).filter(type => type && !dictCache[type] && !pending.has(type))
  if (!todo.length) return

  todo.forEach(type => {
    pending.add(type)
    loadingMap[type] = true
  })

  try {
    const { data } = await getDictDataByDictTypesApi(todo)
    todo.forEach(type => {
      dictCache[type] = buildEntry((data as DictApiResult)?.[type])
    })
  } catch (error) {
    console.error('加载字典失败：', todo, error)
  } finally {
    todo.forEach(type => {
      pending.delete(type)
      loadingMap[type] = false
    })
  }
}

/** 字典项列表 */
export const dictList = (type: string): DictItem[] => dictCache[type]?.list ?? []

/** el-select 选项 */
export const dictOptions = (type: string): DictOption[] => dictCache[type]?.options ?? []

/** 是否加载中 */
export const dictLoading = (type: string): boolean => !!loadingMap[type]

/** 查找字典项：字典值为字符串，统一 String 比较，避免数字/字符串不一致 */
export const dictItem = (type: string, value: any): DictItem | undefined =>
  dictList(type).find(item => item.value === String(value))

/** 字典标签 */
export const dictLabel = (type: string, value: any, fallback = '-'): string =>
  dictItem(type, value)?.label ?? fallback

/** el-tag 的 type */
export const dictTag = (type: string, value: any, fallback: DictTagType = 'primary'): DictTagType =>
  (dictItem(type, value)?.style || fallback) as DictTagType

/** 该字典的默认值（sys_dict_data 中 isDefault=1 的那条） */
export const dictDefaultValue = (type: string, fallback: any = undefined): any => {
  const value = dictCache[type]?.defaultValue
  return value === null || value === undefined || value === '' ? fallback : toValue(value)
}

/**
 * 刷新字典：清后端 Redis 缓存 + 清本地缓存后重新拉取
 * 需要 sys:dict:update 权限
 */
export const refreshDicts = async (types: string[]): Promise<void> => {
  await refreshDictCacheApi(types)
  types.forEach(type => delete dictCache[type])
  await ensureDicts(types)
}

/** 清空本地缓存（如退出登录时调用），不传类型则全部清空 */
export const clearDictCache = (types?: string[]): void => {
  const keys = types?.length ? types : Object.keys(dictCache)
  keys.forEach(type => delete dictCache[type])
}

/**
 * 组件内使用：传入需要的字典类型，自动加载并返回读取方法
 *
 * const dict = useDict('order_type', 'order_status')
 * dict.options('order_type')     // el-select 选项
 * dict.label('order_status', row.status)
 * dict.tag('order_status', row.status)
 */
export const useDict = (...types: string[]) => {
  ensureDicts(types)

  return {
    list: (type: string) => dictList(type),
    options: (type: string) => dictOptions(type),
    label: (type: string, value: any, fallback = '-') => dictLabel(type, value, fallback),
    tag: (type: string, value: any) => dictTag(type, value),
    defaultValue: (type: string, fallback?: any) => dictDefaultValue(type, fallback),
    loading: (type: string) => dictLoading(type),
    refresh: () => refreshDicts(types),
    clear: () => clearDictCache(types)
  }
}

export default useDict
