/** shop_product.images、shop_brand.brand_spokesperson 这类 MySQL json 列，实体侧按项目约定映射成
 *  String，接口原样给回的是一段 JSON 文本（形如 [{"title":"","url":""}]），编辑器和列表都要靠它互转。
 *  每项除 url 外的键随列而定（商品轮播图是 title、品牌代言人是 name），所以这里只保证 url 存在。 */
export interface ImageJsonItem {
  url: string
  [key: string]: any
}

/** 解析失败（脏数据、NULL、存的不是数组）一律退回空数组：一格坏数据不该把整张表打断 */
export const parseImageJson = (raw?: string | null): ImageJsonItem[] => {
  if (!raw) return []
  try {
    const arr = JSON.parse(raw)
    if (!Array.isArray(arr)) return []
    return arr.filter((item: any) => item?.url) as ImageJsonItem[]
  } catch {
    return []
  }
}

/** el-image 的 preview-src-list 只吃纯地址数组 */
export const toImageUrls = (list: ImageJsonItem[]): string[] => list.map((item) => item.url)

/** 逗号串那一档的多图列（shop_plaza_message.plaza_image）：存的是一串英文逗号分隔的地址，不是 JSON。
 *  脏数据（连续逗号、空段、前后空格）在这里一并吃掉，坏一段不该打断整串 */
export const parseCommaUrls = (raw?: string | null): string[] => {
  if (!raw) return []
  const seen = new Set<string>()
  return raw
    .split(',')
    .map((url) => url.trim())
    .filter((url) => !!url && !seen.has(url) && seen.add(url))
}

/** 回写用：空列表给空串（这一列是可空 varchar，服务端每次都照请求体写，空串就是「这条没配图」） */
export const toCommaUrls = (urls: string[]): string => urls.filter(Boolean).join(',')
