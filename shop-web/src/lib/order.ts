/**
 * 结算那两条接口：下单试算 + 统一下单（POST /api/comm/shop/order/preview 与 /create）。
 *
 * 两条吃的是同一个请求体，区别只在写不写库：试算不落订单、不锁库存、不核销券、不动积分账户，
 * 纯粹把这一单的钱问一遍。所以页面上「先试算、点提交才下单」这两趟报出来的钱是同一份算法算的，
 * 不会出现第二种说法（见 ShopOrderCreateServiceImpl#quote）。
 *
 * 前端能给的只有「哪条规格买几件、用哪几张券、这一单花几分」：
 * 单价、规格文本、商品名、配送费、每张券抵多少、积分抵多少钱，全由服务端算并在这份返回里报回来，
 * 这里不拼第二份，也算不出第二份（汇率在 sys_config，配送费规则在后台那几张表上）。
 *
 * 两条都要带 token（券的归属和积分余额都按下单人判），路径走 /api/comm 那一串。
 */
import { apiPost } from './api';

/** 一单最多几张券：后端 MAX_COUPON_COUNT 就是 20，超了整单被拒，页面上先挡住 */
export const MAX_COUPON_COUNT = 20;

/** 一行 = 一条规格（SKU）；同一件商品的两个规格就是两行 */
export interface OrderLineForm {
  skuId: number;
  quantity: number;
  /**
   * 这一行是购物车里哪一条(shop_consumer_cart.id)：从车里结算才带，「立即购买」那一条不带。
   * 带了服务端就按那一行自己的单价算钱（抽中入车的那几条挂的是盲盒单抽价），
   * 价格依然由服务端读，不是客户端报数 —— 这里只是一个"按哪一条车里的行算"的指认。
   * 归属、sku 对不上服务端当场拒单（见 ShopOrderCreateServiceImpl#buildItem）。
   */
  cartId?: number | undefined;
  /** 这一行自己花几分；结算页用的是整单那一格（payPoints），逐行这份留着不填 */
  usedPoints?: number;
}

/**
 * 形状照 ShopOrderCreateDto：userId 不传，服务端钉成 token 背后那一个账号。
 * 每一格都写着 `| undefined` —— 这个项目开着 exactOptionalPropertyTypes，
 * 「这一格不带」和「这一格带个 null」是两种类型，而页面那边是按条件拼的（不用券就是 undefined）。
 */
export interface OrderForm {
  /** 收货地址(shop_consumer_address.id)：服务端按它分仓（有坐标算距离、没坐标按那条的地区匹仓），两份都没有的下不成单 */
  addressId?: number | null | undefined;
  /** 不传按 1 普通订单 */
  orderType?: number | undefined;
  remark?: string | null | undefined;
  /** 用户券 id（shop_activity_coupon_user.id），不是模板 id；不用券就不带 */
  couponIds?: number[] | undefined;
  /** 整单要用的积分总数，服务端按各行还剩多少钱可抵摊到行上 */
  payPoints?: number | undefined;
  /**
   * 1微信 2支付宝 3银行卡 4余额（字典 pay_type）：不传就是还没定，支付回调拿实际渠道覆盖它。
   * 只有 4 会在下单这一刻动钱包 —— 这一单的实付整笔从可用余额冻进冻结余额，余额不够服务端当场拒
   */
  payType?: number | undefined;
  items: OrderLineForm[];
}

/** 一张券在这一单里抵了多少。used=false 只可能是免邮券：运费本来是 0，或这一趟已经被另一张免邮券消掉了 */
export interface PreviewCoupon {
  couponId: number;
  couponName: string | null;
  couponType: number | null;
  discountAmount: number | null;
  used: boolean | null;
}

/** 一行规格的试算结果：单价和小计是服务端按 SKU 表算的，行优惠是券摊下来的 */
export interface PreviewItem {
  skuId: number;
  productName: string | null;
  specText: string | null;
  price: number | null;
  quantity: number | null;
  totalAmount: number | null;
  discountAmount: number | null;
  usedPoints: number | null;
  pointsAmount: number | null;
  paidAmount: number | null;
  /**
   * 来源两格（同 shop_order_item / 购物车那两格）：一整盒在试算里是盒内每款一行，
   * 结算页要把属于同一条购物车行的那几行合起来念，认的就是这两格（只按 skuId 配会把两行念成一行）。
   */
  sourceType: number | null;
  sourceId: number | null;
}

/** 金额之间的账是平的：total=goods+delivery、discount=货值券+免邮券、pay=total−discount−积分 */
export interface OrderPreview {
  itemCount: number | null;
  goodsAmount: number | null;
  deliveryFee: number | null;
  discountAmount: number | null;
  goodsDiscount: number | null;
  shippingDiscount: number | null;
  usedPoints: number | null;
  pointsDiscount: number | null;
  totalAmount: number | null;
  payAmount: number | null;
  coupons: PreviewCoupon[] | null;
  items: PreviewItem[] | null;
}

/** 下单的结果：订单号和金额够了，支付那一步按 orderNo 再查 */
export interface OrderCreated {
  orderId: number;
  orderNo: string;
  /** 下单这一刻固定 0 待支付 */
  status: number | null;
  totalAmount: number | null;
  discountAmount: number | null;
  deliveryFee: number | null;
  usedPoints: number | null;
  pointsAmount: number | null;
  payAmount: number | null;
  itemCount: number | null;
}

/**
 * 试算：页面上任何会影响钱的动作（换地址、勾券、改积分）都重发一次这一颗。
 * 会被拒的那几种情况（库存不够、券不是这个人的/过期/够不上门槛/范围内没这一单的商品、积分超了）
 * 一样按同样的话抛回来，所以这一颗也是结算页那排错误提示的来源。
 */
export function previewOrder(form: OrderForm): Promise<OrderPreview> {
  return apiPost<OrderPreview>('/api/comm/shop/order/preview', form);
}

/** 统一下单：落 sys_order + 明细，锁库存、核销券、冻结积分；主单落 0 待支付 */
export function createOrder(form: OrderForm): Promise<OrderCreated> {
  return apiPost<OrderCreated>('/api/comm/shop/order/create', form);
}

/**
 * 支付：形状照 ShopOrderPaidDto。payAmount 传了就和库里 sys_order.pay_amount 比，对不上整单拒，
 * 所以这一格一律拿 create 报回来的那份，不用页面上算出来的数。
 */
export interface PayForm {
  orderId?: number | undefined;
  orderNo?: string | undefined;
  /** 1微信 2支付宝 3银行卡 4余额（字典 pay_type）：不传就不写 sys_order.pay_type 那一列 */
  payType?: number | undefined;
  payAmount?: number | undefined;
}

/** 支付结果：status 是翻完之后的主单状态，outboundCreated=false 表示这一单早前已经建过出库单（重复回调） */
export interface OrderPaid {
  orderId: number;
  orderNo: string;
  status: number | null;
  outboundId: number | null;
  outboundNo: string | null;
  outboundCreated: boolean | null;
}

/**
 * 支付结果通知（POST /api/comm/shop/order/payCallback）：主单 0 待支付 → 1 已支付并补 pay_time，
 * 顺带建那张 outbound_source=1 的商品出库单、把下单时那条配送单换绑过去、冻结的积分转成已用并按行送分。
 * 只记账不动库存 —— 下单锁住的那份可用量要等后台点「出库」才真减。
 *
 * 幂等靠服务端那句 `WHERE status = 0`：已经是已支付/已完成的单再调一次不会重翻状态、也不会建第二张出库单。
 * 已取消(3)/已退款(4)/已关闭(5) 的单它当场拒（不会因为收到一条「支付成功」就复活）。
 *
 * 微信那一路还没接（这一期没有真实网关、也没有第三方交易号那一列），所以这里就是「付好了，来记账」。
 */
export function payOrder(form: PayForm): Promise<OrderPaid> {
  return apiPost<OrderPaid>('/api/comm/shop/order/payCallback', form);
}
