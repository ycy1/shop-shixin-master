import { apiGet, apiPost } from './api';

/** 分页信封，和商品那份同形（后端都是 MyBatis-Plus 的 IPage）；商品那份留在 products.ts 不动 */
export interface WebPage<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export interface WebStore {
  id: number;
  storeName: string;
  /** 字典 store_type：1直营 2加盟 3合作 */
  storeType: number | null;
  logo: string | null;
  /**
   * MySQL json 列，原样给回一段 JSON 文本（[{"title":"","url":""}]），用 parseImageUrls 拆。
   * 只有详情那条填（列表那条的列清单里没有这一格，回的是 null）—— 列表一行挂一串图片地址是白给。
   */
  coverImages: string | null;
  /** 「北京市 市辖区 东城区」这样一串，库里冗余下来的中文，直接印 */
  areaZh: string | null;
  address: string | null;
  contactPhone: string | null;
  businessHours: string | null;
  /** 字典 store_business_status：1营业中 0休息中 2已打烊 */
  businessStatus: number | null;
  isDelivery: number | null;
}

export interface WebBrand {
  id: number;
  brandName: string;
  logo: string | null;
  description: string | null;
  /**
   * MySQL json 列，原样给回一段 JSON 文本（[{"name":"","url":""}]），用 parseSpokespersons 拆。
   * 只有品牌详情那条填（列表那块的列清单里没有这一格，回的是 null）—— 同门店 coverImages 那一条理由。
   */
  brandSpokesperson: string | null;
}

export interface SearchQuery {
  /** 同 ProductQuery.keyword：没填关键字时传的就是 undefined */
  keyword?: string | undefined;
  pageNum?: number;
  pageSize?: number;
}

/** 门店那块只给启用中的店，营业状态不在筛选里（休息中的店也要能搜出来，由卡片标出来） */
export function fetchStores(query: SearchQuery = {}): Promise<WebPage<WebStore>> {
  return apiGet<WebPage<WebStore>>('/api/web/shop/search/stores', { ...query });
}

/** 停用、已删、根本没这个 id —— 后端回同一句「门店不存在或已停用」，这一页直接把那句话当提示用 */
export function fetchStoreDetail(id: number): Promise<WebStore> {
  return apiGet<WebStore>(`/api/web/shop/store/${id}`);
}

export function fetchBrands(query: SearchQuery = {}): Promise<WebPage<WebBrand>> {
  return apiGet<WebPage<WebBrand>>('/api/web/shop/search/brands', { ...query });
}

/** 停用、已删、根本没这个 id —— 后端回同一句「品牌不存在或已停用」，这一页直接把那句话当提示用 */
export function fetchBrandDetail(id: number): Promise<WebBrand> {
  return apiGet<WebBrand>(`/api/web/shop/brand/${id}`);
}

/** 首页筛选栏的分类候选：一条都不分页（这一条是给下拉框摆候选的，分页就把候选切没了）；
 *  服务端已经按「父在前、自己的下级紧跟其后」铺平，level 给页面做缩进 */
export interface WebCategory {
  id: number;
  categoryName: string;
  level: number | null;
}

export function fetchCategories(): Promise<WebCategory[]> {
  return apiGet<WebCategory[]>('/api/web/shop/search/categories');
}

/** 品牌名下的门店：后端按门店表单里「绑定品牌」那一格（shop_store.brand_id）取，不接分页，一整块给回来 */
export function fetchBrandStores(id: number): Promise<WebStore[]> {
  return apiGet<WebStore[]>(`/api/web/shop/brand/${id}/stores`);
}

/** 代言人那一格拆出来的形状：名字可能没填（图上就不挂那一行字） */
export interface WebBrandSpokesperson {
  name: string;
  url: string;
}

/**
 * 脏数据（存的不是数组、项里没 url）一律当没有：一格坏数据不能把整页打断。
 * 和商品那边的 parseImageUrls 同一条口径，只是这一列每项多一个 name，拼图上要说得出是谁。
 */
export function parseSpokespersons(raw: string | null): WebBrandSpokesperson[] {
  if (!raw) return [];
  try {
    const arr: unknown = JSON.parse(raw);
    if (!Array.isArray(arr)) return [];
    return arr
      .map((item) => {
        const one = (item ?? {}) as { name?: unknown; url?: unknown };
        return {
          name: typeof one.name === 'string' ? one.name.trim() : '',
          url: typeof one.url === 'string' ? one.url.trim() : '',
        };
      })
      .filter((one) => one.url);
  } catch {
    return [];
  }
}

/**
 * 首页搜索框下那一排热搜（服务端 Redis 里按被搜次数排的前 10 个词，不分类型）。
 * 一个词都没记过就是空数组 —— 那一排整块不出现，和「接口没通」是两回事，由组件自己说。
 */
export function fetchHotWords(): Promise<string[]> {
  return apiGet<string[]>('/api/web/shop/search/hot');
}

/**
 * 记一次搜索（给热搜加一票）。
 *
 * 只在搜索结果页真正进站那一趟打一次：那一页会按 pageSize=1 给另外三个 tab 各探一次数、
 * 翻页还要再捞，挂在读接口上就成了「搜一次、计好几票」。
 * 发出去就不管回包 —— 这一排词没记上是展示层面的事，不该把搜索结果变成红框（和活动埋点同一条口径）。
 *
 * 关键字拼在查询串上：apiPost 发的是 JSON 体，后端那颗 @RequestParam 收不到体里的字段。
 */
export function recordHotWord(keyword: string): void {
  apiPost<void>(`/api/web/shop/search/hot?keyword=${encodeURIComponent(keyword)}`).catch(() => {});
}

