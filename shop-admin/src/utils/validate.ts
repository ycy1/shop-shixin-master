
import dayjs from 'dayjs'

const validate = {
    /**
   * 判断是否为外部链接
   * @param {string} path
   * @returns {boolean}
   */
  isExternal(path: string): boolean {
    return /^(https?:|mailto:|tel:)/.test(path)
  },

  /**
   * 时间转换为指定格式
   * 注意：不能直接把空值丢给 dayjs —— null/'' 会得到 "Invalid Date"，
   * undefined 会被当成"当前时间"，所以统一在这里拦掉
   * @param {string} time
   * @param {string} format
   * @returns {string}
   */
  formatTime(time: string, format: string = 'YYYY-MM-DD HH:mm:ss'): string {
    if (!time) return '-'
    const day = dayjs(time)
    return day.isValid() ? day.format(format) : '-'
  },
  
  /**
   * 金额转换为指定格式
   * @param {any} value
   * @returns {string}
   */
  formatAmount(value: any): string {
    return value === null || value === undefined || value === '' ? '-' : `¥${Number(value).toFixed(2)}`
  }
  
}
// 导出工具类
export default validate
