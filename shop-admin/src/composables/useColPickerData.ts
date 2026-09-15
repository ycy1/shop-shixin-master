import { useCascaderAreaData } from '@vant/area-data'

export type CascaderOption = {
  text: string
  value: string
  children?: CascaderOption[]
}

/**
 * 使用 '@vant/area-data' 作为数据源
 * - colPickerData: 省市区级联数据（vant 级联格式）
 * - findChildrenByCode: 根据 code 查找子节点（用于级联选择器异步加载）
 * - resolveAreaCode: 将逗号分隔的 areaCode（如 110000,110100,110101）解析为可读的省市区名称
 */
export function useColPickerData() {
  // '@vant/area-data' 数据源
  const colPickerData: CascaderOption[] = useCascaderAreaData()

  // 根据 code 查找子节点，不传 code 则返回所有节点
  function findChildrenByCode(data: CascaderOption[], code?: string): CascaderOption[] | null {
    if (!code) {
      return data
    }
    for (const item of data) {
      if (item.value === code) {
        return item.children || null
      }
      if (item.children) {
        const childrenResult = findChildrenByCode(item.children, code)
        if (childrenResult) {
          return childrenResult
        }
      }
    }
    return null
  }

  // 解析逗号分隔的 areaCode（省,市,区）为可读名称
  function resolveAreaCode(code: string): string {
    if (!code) return ''
    const codes = code.split(',').map(c => c.trim()).filter(Boolean)
    if (codes.length === 0) return ''
    const names: string[] = []
    let current: CascaderOption[] | null = colPickerData
    for (const c of codes) {
      const node = current?.find(item => item.value === c)
      if (node) {
        names.push(node.text)
        current = node.children || null
      }
      else {
        break
      }
    }
    return names.join(' ')
  }

  return { colPickerData, findChildrenByCode, resolveAreaCode }
}
