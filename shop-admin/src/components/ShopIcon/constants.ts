/** sys_icon.icon_type 的取值，列表的标签和表单的内容控件都按它切换 */
export const ICON_TYPES = [
  { value: 1, label: 'SVG' },
  { value: 2, label: '图片URL' },
  { value: 3, label: '字体类名' }
]

/**
 * sys_icon.icon_category 的取值：图标选择器就按这三档分 tab，
 * 所以这里加一档就要动选择器，两边别各写一份
 */
export const ICON_CATEGORIES = [
  { value: 1, label: '系统图标' },
  { value: 2, label: '商品分类' },
  { value: 3, label: '其他' }
]

/** 没传分类时的默认档，和建表的 DEFAULT 一致 */
export const ICON_CATEGORY_DEFAULT = 2
