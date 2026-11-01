/**
 * 前台购物车的接口绑定（/api/web/shop/cart/*，五条都要带 token）。
 *
 * 一行的身份是「某个商品的某个规格」，不是商品：同一件商品的两个规格就是两行，各自一个价、
 * 各自一份库存，所以这一路传的都是 skuId（下单要传的也是它）。
 * 名称、单价、规格文本、主图、可用库存都由服务端联出来，前端不拼第二份 ——
 * 每一颗写完了都重拉一次列表，画的就是库里那一份。
 */
import { apiDelete, apiGet, apiPost, apiPut } from './api';

export interface CartLine {
  /** 购物车行的 id（改数量、勾选、删除点的都是它；一行一个规格） */
  id: number;
  skuId: number;
  /** 所属商品（SPU）id：点回 /product/{id} 用的是它，skuId 点不进详情页 */
  productId: number | null;
  productName: string | null;
  productCode: string | null;
  /** 规格文本；默认规格那一条后端给的是空串或 NULL，页面自己补「默认规格」 */
  specText: string | null;
  /** 已经按这一条规格走过：这条规格自己配过图就是它，没配退回商品主图（服务端 COALESCE） */
  mainImage: string | null;
  productPrice: number | null;
  /**
   * 消费单价：这一行按它算钱（抽中入车的那几条挂的是盲盒单抽价，不是这款规格的原价）。
   * 老行和后台代客加购的行可能是 NULL ⇒ 那种就按 productPrice 现价算，页面和合计都读
   * {@link payPriceOf}，别各写各的 ?? 一遍，两边算出两个数就说不清了。
   */
  price: number | null;
  quantity: number;
  /** 1选中 0未选中：合计只算选中的那几行 */
  selected: number | null;
  /** 来源：0 普通商品（自己加购） 1 盲盒商品（抽中入车）—— 同一规格按来源分行，各叠各的数量、各算各的价 */
  sourceType: number | null;
  /** 来源ID：盲盒商品那行是抽中它的那台盒(shop_trendy_box.id)，两台盒抽中同一款是两行两价；普通行为 null（库里记 0） */
  sourceId: number | null;
  /** 来源名称：盲盒行=抽中它的那台盒叫什么（联表带出）；普通行、以及那台盒已被删掉时是 null */
  sourceName: string | null;
  /** 这一条规格的可用库存（各仓 SUM(stock - locked_stock)，整盒那一行是 null=不按仓存封），步进按它封顶 */
  availableStock: number | null;
  /** 1还能买 0商品已下架：画灰行、不给勾、不计入合计（规格的 sale_enable 不参与，那是 ERP 建销售单的开关） */
  buyable: number | null;
  categoryName: string | null;
  brandName: string | null;
  storeName: string | null;
  /** 一整盒（端盒那一趟）里有哪几款、各几件；其它行是空数组 */
  boxItemList: CartBoxItem[];
}

/** 整盒那一行里的一个款式（服务端把库里那串 JSON 拆好的） */
export interface CartBoxItem {
  skuId: number | null;
  styleName: string | null;
  styleImage: string | null;
  quantity: number | null;
}

/** 一次全给、不分页：数量/勾选/合计都横跨全部行，分页就没法「全选」也报不出总数 */
export function fetchMyCart(): Promise<CartLine[]> {
  return apiGet<CartLine[]>('/api/web/shop/cart/list');
}

/**
 * 这一行按多少钱算 —— 车里的 price 优先，没写过（老行、后台代客加购的行）才退回这条规格的现价。
 *
 * 下单那一刻服务端取价的判据和这一句是同一条（见 ShopOrderCreateServiceImpl#buildItem 读购物车那一格），
 * 所以页面上显示的、合计里加的、最后真收的钱是同一个数。抽中入车的那几行在这里就会显出盒的单抽价。
 */
export function payPriceOf(line: CartLine): number {
  return Number(line.price ?? line.productPrice ?? 0);
}

/**
 * 那一枚「盲盒商品」tip 上的字：认得出是哪台盒抽的就报盒名 —— 两台盒抽中同一款是并排两行、
 * 各挂各的单抽价，只写「盲盒商品」看不出这一行归谁（盒后来被删了联不出名字，退回只说这一句）。
 */
export function boxTagOf(
  boxLine: string,
  line: Pick<CartLine, 'sourceType' | 'sourceName'>,
): string {
  return line.sourceType === 1 && line.sourceName ? `${boxLine} · ${line.sourceName}` : boxLine;
}

/**
 * 这一行是不是一整盒（端盒那一趟在车里就是一行）：库里那一行不挂在某条规格上，
 * sku_id 落的是 0 这个哨兵，盒内有哪几款各几件看 boxItemList。
 */
export function isWholeBoxLine(line: Pick<CartLine, 'skuId'>): boolean {
  return Number(line.skuId) === 0;
}

/** 整盒那一行没有商品主图可取（它不挂商品），页面拿盒内第一款那张当封面 */
export function lineImageOf(line: CartLine): string | null {
  return line.mainImage ?? line.boxItemList?.[0]?.styleImage ?? null;
}

/**
 * 步进能加到几件：普通行按这一条规格的可用库存封顶，整盒那一行不封顶（Infinity）——
 * 它买的是「几盒」，某一款的仓存不是这一行的上限，库存要等下单那一刻按盒内每款逐条复核。
 */
export function stockCapOf(line: CartLine): number {
  return isWholeBoxLine(line) ? Number.POSITIVE_INFINITY : (line.availableStock ?? 0);
}

/**
 * 这一行上一共有几件实物：盒内每款的件数 × 这一行是几盒（同一台盒端第二次并成一行，
 * 款式清单是按盒 id 现查的「一盒里有什么」，所以件数要跟着盒数翻）。
 */
export function boxPieceCount(line: CartLine): number {
  const perBox = (line.boxItemList ?? []).reduce((sum, one) => sum + Number(one.quantity ?? 0), 0);
  return perBox * Number(line.quantity ?? 1);
}

/**
 * 加入购物车：只送 规格 + 件数。
 * 车上已经有这一条就在它上面叠件数，有一条删掉的把那一行复活，都没有才建第一条 —— 点第二次不会报错。
 */
export function addMyCart(skuId: number, quantity: number): Promise<null> {
  return apiPost<null>('/api/web/shop/cart/add', { skuId, quantity });
}

/** 局部更新：只认传上来的那两格，另一格不动 */
export function updateMyCart(params: {
  id: number;
  quantity?: number;
  selected?: 0 | 1;
}): Promise<null> {
  return apiPut<null>('/api/web/shop/cart/update', params);
}

/** 全选 / 取消全选：一次一句 UPDATE 按人刷完，不逐行发请求 */
export function selectAllMyCart(selected: 0 | 1): Promise<null> {
  return apiPut<null>(`/api/web/shop/cart/selectAll?selected=${selected}`);
}

/** 删一条也走这一路（数组里就一个 id），ids 是逗号分隔的路径串 */
export function deleteMyCart(ids: number[]): Promise<null> {
  return apiDelete<null>(`/api/web/shop/cart/delete/${ids.join(',')}`);
}
