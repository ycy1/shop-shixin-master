import { apiGet } from './api';

// 只声明这一页真正渲染的格；接口回的对象还有 productDesc/images/productCode 等，用到再加
export interface WebProduct {
  id: number;
  productName: string;
  mainImage: string | null;
  /** 单价在规格那一层（一件商品好几个价），列表只汇总出这一档：卡片上摆「¥最低价 起」 */
  priceMin: number | null;
  priceMax: number | null;
  // 卡片背面那几格
  subTitle: string | null;
  categoryName: string | null;
  brandName: string | null;
  // 销量和可用库存都不在商品这一层给：销量记在每一条规格上，库存是「一个规格 × 一个仓」那一格的账，
  // 摊成商品一个数会写着「有货」而缺货的正是人选中的那一档。读 WebProductSku 里的 salesCount/availableStock
}

export interface ProductPage {
  records: WebProduct[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

/** 商品规格那一行：库里 shop_product.product_specs 存的是一串规格 id，服务端详情那条现查成这三格给回来 */
export interface WebProductSpec {
  id: number;
  specName: string | null;
  specValue: string | null;
  specUnit: string | null;
}

/** 一件商品名下的一个具体规格：详情页那一排按钮各对应一条，加购/下单传的就是它的 id */
export interface WebProductSku {
  id: number;
  /** 按钮上显示的字；默认规格那一格是空的，页面自己补「默认规格」 */
  specText: string | null;
  /** 这一档规格的封面 = skuImages 组首那张；规格一张图都没配时为 null，页面沿用商品的图 */
  skuImage: string | null;
  /** MySQL json 列，接口原样给回一段 JSON 文本，用 parseImageUrls 拆（和商品的 images 同一口径） */
  skuImages: string | null;
  /** 这一档规格的图文详情（详情页「商品详情」那一块从上往下铺的就是它），同样是 json 列原样给回的 JSON 文本 */
  detailImages: string | null;
  productPrice: number;
  availableStock: number | null;
  /** 这一档规格卖了多少件（详情页那句「已售」读它，换一档规格就换这个数） */
  salesCount: number | null;
  gainPoints: number | null;
  /** ERP 那边「这一条规格能不能建销售单」的开关（0 = 不开销售单），和前台能不能买、商品上没上架是两码事，前台只读来展示 */
  saleEnable: number | null;
}

/** 详情页多出来的那几格。列表 SQL 也带着它们（后端不为两种视图分列），只是卡片不渲染；specs/skus 例外 —— 只有详情会填 */
export interface WebProductDetail extends WebProduct {
  productCode: string | null;
  productDesc: string | null;
  /** MySQL json 列，接口原样给回一段 JSON 文本，用 parseImageUrls 拆 */
  images: string | null;
  /** 没挂规格、或挂的规格在后台全被禁用/删掉了，服务端给空数组 */
  specs: WebProductSpec[] | null;
  /** 一件商品至少有一条默认规格，正常不会空；真为空就是后台数据没建起来，详情页这时候不给加购 */
  skus: WebProductSku[] | null;
  brandLogo: string | null;
  /** 品牌详情页的入口（那一行「品牌：xxx」挂的就是它）；没挂品牌的商品这里是 null，链接整条不出现 */
  brandId: number | null;
  storeName: string | null;
}

/** 轮播图脏数据（存的不是数组、项里没 url）一律当没有图，不让一格坏数据把详情页打断 */
export function parseImageUrls(raw: string | null): string[] {
  if (!raw) return [];
  try {
    const arr: unknown = JSON.parse(raw);
    if (!Array.isArray(arr)) return [];
    return arr
      .map((item) => (item as { url?: unknown })?.url)
      .filter((url): url is string => typeof url === 'string' && url.length > 0);
  } catch {
    return [];
  }
}

export interface ProductQuery {
  /** 搜索页那一趟传的可能是 undefined（没填关键字＝搜全量），exactOptionalPropertyTypes 下要明说这一格允许给 undefined */
  keyword?: string | undefined;
  // 只传选中的那一级，整棵下级由服务端按 parent_id 展开，前端不用自己拼子树
  categoryId?: number;
  // 首页轮播位传 1（只要运营勾过「首页轮播」的）；不传就是全量货架
  carouselEnable?: number;
  // 门店详情页的「本店商品」：只认这一家店的货（store_id 为 NULL 的平台货不算进任何一家店名下）
  storeId?: number;
  // 品牌详情页的「品牌商品」：只认这个品牌的货
  brandId?: number;
  // 门店详情页商品区那两档 tab：0 门店商品、1 文创商品（文创必须挂门店，所以总是和 storeId 一起传）
  culturalEnable?: number;
  pageNum?: number;
  pageSize?: number;
}

export function fetchProducts(query: ProductQuery = {}): Promise<ProductPage> {
  // 空值不用自己挑：axios 会把 undefined 的参数丢掉
  return apiGet<ProductPage>('/api/web/shop/product/list', { ...query });
}

/** 下架、已删、根本没这个 id —— 后端回同一句「商品不存在或已下架」，这一页直接把那句话当提示用 */
export function fetchProductDetail(id: number): Promise<WebProductDetail> {
  return apiGet<WebProductDetail>(`/api/web/shop/product/${id}`);
}
