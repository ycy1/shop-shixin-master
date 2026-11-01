<script setup lang="ts">
/**
 * 结算页：购物车里勾着的那几行（或详情页「立即购买」那一条规格）→ 选地址、选券、抵积分 → 提交下单。
 *
 * 三件事决定这一页的写法：
 * 1) 钱不由这一页算。配送费要看收货地就近分仓（有坐标按距离挑仓，没坐标就按那条选的地区匹仓）、
 *    券要按适用范围摊到行、积分折多少钱按后台那档汇率算，三份都在服务端。
 *    所以页面上任何会影响钱的动作都重发一次试算（POST /api/comm/shop/order/preview），
 *    画的是它报回来的数；提交那一颗走同一条 DTO 的 /order/create，两句说的是同一份账。
 * 2) 数据在这里重新捞一遍，不接购物车那一岛的状态（astro 每个岛一份 store，跨岛不共享）。
 *    勾选本来就落库，所以重拉 /cart/list 拿到的就是购物车里勾着的那几行。
 * 3) 商品可用校验在这一页先挡一道：行买不了（商品已下架）或件数超过可用库存，都不给提交，
 *    因为服务端到下单那一刻说的也是同一句话。规格的 sale_enable 不看 —— 那是 ERP 建销售单的开关。
 *
 * 试算失败（券被拒、积分超了、库存不够）就把金额收起来、提交置灰：这一单收多少钱都说不清，就不能提交。
 *
 * 钱这一拍有两颗：「提交订单」只落单（主单 0 待支付），「提交并支付」在落单之后紧接调服务端那条支付回调，
 * 也就是当作这笔已经付过（微信/支付宝网关还没接，pay_type 那一格现在只是把人选的方式记到订单上）。
 * 收货地址在这里就地增改（AddressManager 弹窗，表单和个人中心那份共用），不用切页。
 */
import { computed, onMounted, ref, watch } from 'vue';
import { FwbButton } from 'flowbite-vue';
import AddressManager from '@/components/AddressManager.vue';
import { enumChoices, enumLabel, useI18n } from '@/i18n';
import { fetchProductDetail, parseImageUrls } from '@/lib/products';
import {
  canShipTo,
  couponBenefit,
  couponExpired,
  fetchMyAddresses,
  fetchMyCoupons,
  fetchMyPoints,
  fetchMyWallet,
  type AddressRow,
  type CouponRow,
  type WalletAccount,
} from '@/lib/profile';
import {
  MAX_COUPON_COUNT,
  createOrder,
  payOrder,
  previewOrder,
  type OrderCreated,
  type OrderForm,
  type OrderPreview,
  type PreviewItem,
} from '@/lib/order';
import { useAuthStore } from '@/stores/auth';
import {
  boxPieceCount,
  boxTagOf,
  isWholeBoxLine,
  lineImageOf,
  payPriceOf,
  stockCapOf,
} from '@/lib/cart';
import { useCartStore } from '@/stores/cart';

/**
 * 两种进来法：
 * - 购物车「去结算」：不给参数，这一页自己重拉一次车，取勾着的那几行；
 * - 详情页「立即购买」：带 productId + skuId + qty，只把这一条规格开单，不动车里的东西。
 */
const props = withDefaults(defineProps<{ productId?: number; skuId?: number; qty?: number; locale?: string }>(), {
  productId: 0,
  skuId: 0,
  qty: 1,
});

/** 一行要结算的规格：两种进来法都往这个形状上收敛（字段名沿用购物车那份） */
interface CheckoutLine {
  skuId: number;
  productId: number | null;
  productName: string;
  specText: string | null;
  image: string | null;
  price: number;
  quantity: number;
  /** 可用库存；下单那一刻服务端按它复核，这里只用来先挡一道 */
  availableStock: number;
  /** 1 还能买 0 商品已下架 */
  buyable: number;
  /** 购物车行的 id：只有从车里来的那几行有，下单成功后要把它们从车里删掉 */
  cartId: number | null;
  /** 来源：1 盲盒商品（抽中入车的那一行，单价是盒给的），结算页跟着购物车页标一句 */
  sourceType: number | null;
  /** 来源名称：盲盒那行是哪台盒抽的（普通行、立即购买那一路都是 null） */
  sourceName: string | null;
  /** 来源ID：拿它把试算回来的那些行认回这一条购物车行（整盒的那几行靠它合起来念） */
  sourceId: number;
  /** 一整盒那一行盒内一共几件（其它行是 null，那一格照旧念规格） */
  boxPieces: number | null;
}

const auth = useAuthStore();
const cart = useCartStore();
const i18n = useI18n();
const { d, fill, href } = i18n;

const ready = ref(false);
const signedIn = computed(() => ready.value && auth.isLoggedIn);
const buyNow = computed(() => props.skuId > 0);

const lines = ref<CheckoutLine[]>([]);
const loadError = ref('');

const addresses = ref<AddressRow[]>([]);
const addressId = ref<number | null>(null);

const coupons = ref<CouponRow[]>([]);
const couponIds = ref<number[]>([]);

const pointsBalance = ref(0);
const payPoints = ref(0);

/** 钱包那一行账（null = 还没开账，余额按 0 元算）：只有选余额支付才用得上 */
const wallet = ref<WalletAccount | null>(null);

/** 支付方式候选：档位就是枚举那一份（d.enum.payType），语种按页现取，所以在这儿算、不留模块顶层 */
const payOptions = enumChoices(d.enum.payType);

/** 支付方式（字典 pay_type）里：默认第一档（微信）。这一格现在既跟着 create 落进订单、也跟着回调走 */
const payType = ref<number>(payOptions[0]?.value ?? 1);

/** 收货地址管理弹窗：就地增改，不用把人赶回个人中心 */
const addressManagerOpen = ref(false);

const remark = ref('');

const preview = ref<OrderPreview | null>(null);
const previewing = ref(false);
const previewError = ref('');

const submitting = ref(false);
const submitError = ref('');
const created = ref<OrderCreated | null>(null);
/** 这一单有没有当场付掉（走「提交并支付」那一颗才可能为真） */
const paid = ref(false);
/** 单已经落了、钱那一步没成：这两句得分开说，不然人以为单也没下成 */
const payError = ref('');

/**
 * 试算一次要一两百毫秒，而改一格就发一次：这一格序号认「最后一次按的」，
 * 回来得慢的那一趟直接丢掉，否则金额会停在先发出的那次上（口径同后台登录页那份 qrEpoch）。
 */
let quoteEpoch = 0;
let quoteTimer: ReturnType<typeof setTimeout> | undefined;

onMounted(() => {
  ready.value = true;
  if (signedIn.value) void reloadAll();
});

// 在这一页里登进来（头部那颗登录窗是另一颗岛，它只把登录态事件抛过来）：这几份数据这时候才捞得动
watch(signedIn, (on) => {
  if (on) void reloadAll();
});

/** 五份数据各捞各的：任何一份捞不动都不该让其余几块画不出来 */
async function reloadAll() {
  loadError.value = '';
  try {
    await loadLines();
  } catch (err) {
    loadError.value = (err as Error).message;
  }
  await Promise.all([loadAddresses(), loadCoupons(), loadPoints(), loadWallet()]);
  // 试算不在这里发：上面那几笔赋值已经把 watch 撞响了，400ms 后自己会跑一趟
}

/** 车上勾着的行 / 立即购买那一条，都收敛成同一份 rows */
async function loadLines() {
  if (buyNow.value) {
    lines.value = await loadBuyNowLine();
    return;
  }
  await cart.load();
  if (cart.error) {
    loadError.value = cart.error;
    return;
  }
  lines.value = cart.lines
    .filter((line) => line.selected === 1)
    .map((line) => ({
      skuId: line.skuId,
      productId: line.productId,
      // 端盒那一趟在车里是一整盒一行：念「一整盒 · 盒名」，不念商品名（那一行不挂在某个商品上）
      productName: isWholeBoxLine(line)
        ? (line.sourceName ? `${d.cart.wholeBox} · ${line.sourceName}` : d.cart.wholeBox)
        : (line.productName ?? d.checkout.lineGone),
      specText: line.specText,
      // 整盒那行没有规格，改说这一盒一共几件（盒里有哪几款各几件在购物车那页展开看）
      boxPieces: isWholeBoxLine(line) ? boxPieceCount(line) : null,
      image: lineImageOf(line),
      price: payPriceOf(line),
      quantity: line.quantity,
      availableStock: stockCapOf(line),
      buyable: line.buyable ?? 0,
      cartId: line.id,
      sourceType: line.sourceType ?? 0,
      sourceName: line.sourceName ?? null,
      sourceId: Number(line.sourceId ?? 0),
    }));
}

/**
 * 立即购买没有现成的行：拿 productId 捞一次详情，从里面挑出那一档规格。
 * 详情那条接口只给上架的商品，所以下架/删掉的会直接落成 loadError 那句（后端那句「已下架」比状态码准）。
 */
async function loadBuyNowLine(): Promise<CheckoutLine[]> {
  const detail = await fetchProductDetail(props.productId);
  const sku = (detail.skus ?? []).find((item) => item.id === props.skuId);
  if (!sku) {
    loadError.value = d.checkout.skuGone;
    return [];
  }
  const shots = parseImageUrls(sku.skuImages ?? null);
  return [
    {
      skuId: sku.id,
      productId: detail.id,
      productName: detail.productName,
      specText: sku.specText,
      image: shots[0] || sku.skuImage || detail.mainImage,
      price: Number(sku.productPrice ?? 0),
      quantity: props.qty > 0 ? props.qty : 1,
      availableStock: sku.availableStock ?? 0,
      // 详情页捞得来的必然是上架的（下架的商品在这一步的接口就报错了）
      buyable: 1,
      cartId: null,
      sourceType: 0,
      sourceName: null,
      sourceId: 0,
      boxPieces: null,
    },
  ];
}

async function loadAddresses() {
  try {
    const page = await fetchMyAddresses(1, 50);
    addresses.value = page.records;
    // 弹窗里改完回来，原来选着那条还在就继续用（换到别条上等于替用户改了收货地）
    if (addressId.value != null && addresses.value.some((row) => row.id === addressId.value && canShipTo(row))) {
      return;
    }
    // 默认那条优先，其次第一条能分仓的：既没坐标又没地区的地址服务端也挑不出仓，不给选
    const usable = addresses.value.filter((row) => canShipTo(row));
    const preset = usable.find((row) => row.isDefault === 1) ?? usable[0];
    addressId.value = preset?.id ?? null;
  } catch (err) {
    loadError.value = (err as Error).message;
  }
}

/** 改过地址（补了坐标、换了默认）钱可能跟着变：重拉列表，再按新那份重算一次 */
async function onAddressChanged() {
  await loadAddresses();
  await runPreview();
}

async function loadCoupons() {
  try {
    // 只捞未使用那一档（status=0）：其余券提交必被拒，摆出来只是让人多点一次
    const page = await fetchMyCoupons(1, 50, 0);
    // status=0 里还会掺着 expireTime 已经过了的（服务端翻成 2 要等有人刷那一档），
    // 那几张提交一样被拒，所以标出来、禁勾，并且排到可用那批后面（稳定排序，同档内保持原序）
    coupons.value = [...page.records].sort(
      (a, b) => Number(couponExpired(a)) - Number(couponExpired(b)),
    );
  } catch {
    // 券捞不动不拦下单：这一格顶多是「没用上券」，比整页打不开好
    coupons.value = [];
  }
}

async function loadPoints() {
  try {
    const account = await fetchMyPoints();
    // 一分都没获得过就没有这一行账（后端给 null），画成 0 分，输入框本来就填不出数
    pointsBalance.value = Number(account?.balance ?? 0);
  } catch {
    pointsBalance.value = 0;
  }
}

/** 钱包那一行账：捞不动就当 0 元，余额那一档禁选、别让人点了才被服务端拒 */
async function loadWallet() {
  try {
    wallet.value = await fetchMyWallet();
  } catch {
    wallet.value = null;
  }
}

/** 买不了的行：下架的、件数比可用库存多的。有它们在，任何一趟请求之前就先挡下来 */
const blockedLines = computed(() =>
  lines.value.filter((line) => line.buyable !== 1 || line.quantity > line.availableStock),
);

function reasonOf(line: CheckoutLine) {
  if (line.buyable !== 1) return d.checkout.offline;
  if (line.quantity > line.availableStock) return fill(d.checkout.stockLeft, { n: line.availableStock });
  return '';
}

function specLabel(line: CheckoutLine) {
  return line.specText?.trim() || d.checkout.defaultSpec;
}

/** 支付方式（字典 pay_type）里余额那一档：只有它会在下单这一刻动钱包 */
const PAY_TYPE_BALANCE = 4;

/** 钱包里能拿来付的钱：没开过账（后端给 null）就是 0 元 */
const walletBalance = computed(() => Number(wallet.value?.balance ?? 0));

/** 账户被冻住（status=0）连余额支付这一档都不给选 —— 服务端那句判据是同一个 */
const walletUsable = computed(() => wallet.value == null || wallet.value.status !== 0);

/** 这一单要付多少钱（试算还没回来就按 0，别把「还没算出来」说成「钱不够」） */
const payAmount = computed(() => Number(preview.value?.payAmount ?? 0));

/** 余额这一档现在点不点得动：账户被冻、或者不够付这一单实付的全额（余额只整笔付，不支持一部分余额一部分微信） */
const balanceUsable = computed(() => walletUsable.value && walletBalance.value >= payAmount.value);

/** 已经选了余额但它现在付不起了：这一条要挡住提交，不能让人按下才被服务端拒 */
const balanceBlocked = computed(() => payType.value === PAY_TYPE_BALANCE && !balanceUsable.value);

function money(value: number | null | undefined) {
  return Number(value ?? 0).toFixed(2);
}

/** 整单件数：这一页不让改件数（改的回去购物车改），所以只报数 */
const totalQuantity = computed(() => lines.value.reduce((sum, line) => sum + line.quantity, 0));

const orderForm = computed<OrderForm>(() => ({
  addressId: addressId.value,
  couponIds: couponIds.value.length ? [...couponIds.value] : undefined,
  payPoints: payPoints.value > 0 ? Math.floor(payPoints.value) : undefined,
  remark: remark.value.trim() || undefined,
  // 支付方式不在这一份里（下单那一步才带，见 submit）：余额够不够是页面自己判的（见 balanceUsable），
  // 塞进共用的这一份，选余额、钱不够时整个试算会被服务端拒掉，连这一单多少钱都看不见了
  items: lines.value.map((line) => ({
    skuId: line.skuId,
    quantity: line.quantity,
    // 从车里来的那几行把自己那条购物车记录的 id 报上去，单价按那一格算（抽中的那几条是盒的单抽价）
    cartId: line.cartId ?? undefined,
  })),
}));

/**
 * 影响钱的那几格一动就重发试算（地址、券、积分、行本身）。
 * 留 400ms 空档是因为积分那一格是一格一格敲的，每敲一个数字发一次请求没意义。
 */
watch(
  [addressId, couponIds, payPoints, lines],
  () => {
    if (!ready.value || !signedIn.value) return;
    if (quoteTimer) clearTimeout(quoteTimer);
    quoteTimer = setTimeout(runPreview, 400);
  },
  { deep: true },
);

async function runPreview() {
  const epoch = ++quoteEpoch;
  previewError.value = '';
  if (blockedLines.value.length || !addressId.value || !lines.value.length) {
    // 有买不了的行、还没地址就别去问钱：服务端那句说的是同一件事，这里先把它挡住
    preview.value = null;
    previewing.value = false;
    return;
  }
  previewing.value = true;
  try {
    const data = await previewOrder(orderForm.value);
    if (epoch !== quoteEpoch) return;
    preview.value = data;
  } catch (err) {
    if (epoch !== quoteEpoch) return;
    preview.value = null;
    previewError.value = (err as Error).message;
  } finally {
    if (epoch === quoteEpoch) previewing.value = false;
  }
}

function toggleCoupon(row: CouponRow) {
  if (couponExpired(row)) return;
  const hit = couponIds.value.indexOf(row.id);
  if (hit >= 0) {
    couponIds.value.splice(hit, 1);
    return;
  }
  // 后端一单只认 20 张，超了整单被拒；勾到第 21 张就地不给按，别等试算回来那一句
  if (couponIds.value.length >= MAX_COUPON_COUNT) return;
  couponIds.value.push(row.id);
}

/** 券在这一单里抵了多少、有没有真消掉，只有服务端那份报得清（免邮券可能一分也没抵着） */
function couponResultText(id: number) {
  const hit = preview.value?.coupons?.find((item) => item.couponId === id);
  if (!hit) return '';
  return hit.used === false ? d.checkout.couponNotUsed : `-¥${money(hit.discountAmount)}`;
}

/**
 * 行上那一句：券摊到行、积分摊到行都是服务端的事，这里只把它报的数念一遍。
 *
 * 对法是按身份（规格 + 来源类型 + 来源ID），不是按位置：一整盒在试算回来的是**好几行**（盒内每款一行），
 * 按位置配就会把下一条购物车行念成这一盒的第二款；整盒那一条把它这几行的数加起来念。
 * 单靠 skuId 也不行：同一个规格可能既自己加购过、又被某台盒抽中过，两行两个价，靠来源那两格才分得开。
 */
/** 哪几条盒行被下拉摊开了：key 用那条购物车行的 id（立即购买那一路没有盒行，所以不会撞位） */
const openedBoxes = ref<Set<string>>(new Set());

function toggleBoxOpen(key: string) {
  const next = new Set(openedBoxes.value);
  if (next.has(key)) {
    next.delete(key);
  } else {
    next.add(key);
  }
  openedBoxes.value = next;
}

function itemsOfLine(line: CheckoutLine): PreviewItem[] {
  const items = preview.value?.items ?? [];
  if (line.boxPieces != null) {
    return items.filter((one) => Number(one.sourceType) === 1 && Number(one.sourceId) === Number(line.sourceId));
  }
  return items.filter((one) => one.skuId === line.skuId
    && Number(one.sourceType ?? 0) === Number(line.sourceType ?? 0)
    && Number(one.sourceId ?? 0) === Number(line.sourceId ?? 0));
}

function lineResultText(index: number) {
  const line = lines.value[index];
  if (!line) return '';
  const hits = itemsOfLine(line);
  if (!hits.length) return '';
  const sum = (pick: (one: PreviewItem) => number | null) =>
    hits.reduce((total, one) => total + Number(pick(one) ?? 0), 0);
  const parts = [fill(d.checkout.lineCoupon, { n: `¥${money(sum((one) => one.discountAmount))}` })];
  if (sum((one) => one.usedPoints) > 0) {
    parts.push(fill(d.checkout.linePoints, { n: `¥${money(sum((one) => one.pointsAmount))}` }));
  }
  parts.push(fill(d.checkout.linePaid, { n: `¥${money(sum((one) => one.paidAmount))}` }));
  return parts.join(' · ');
}

const canSubmit = computed(
  () =>
    signedIn.value &&
    !!addressId.value &&
    lines.value.length > 0 &&
    !blockedLines.value.length &&
    !balanceBlocked.value &&
    !previewing.value &&
    !submitting.value &&
    !previewError.value,
);

const submitHint = computed(() => {
  if (!signedIn.value) return d.checkout.hintNeedLogin;
  if (!lines.value.length) return d.checkout.hintNoLines;
  if (blockedLines.value.length) return d.checkout.hintBlocked;
  if (!addressId.value) return d.checkout.hintNeedAddress;
  if (previewError.value) return d.checkout.hintPreviewFailed;
  // 这两句和余额那一档的置灰是同一个判据：账户冻了、或者钱不够，就在这儿说清差多少
  if (payType.value === PAY_TYPE_BALANCE && !walletUsable.value) {
    return d.checkout.hintWalletFrozen;
  }
  if (balanceBlocked.value) {
    return fill(d.checkout.hintBalanceShort, {
      a: `¥${money(walletBalance.value)}`,
      b: `¥${money(payAmount.value)}`,
    });
  }
  if (previewing.value) return d.checkout.hintPricing;
  return '';
});

async function submit(withPay: boolean) {
  if (!canSubmit.value) return;
  submitting.value = true;
  submitError.value = '';
  try {
    // 支付方式只有下单才带：选的是余额时，服务端在这一刻就把这一单的实付从可用余额冻进冻结余额
    const result = await createOrder({ ...orderForm.value, payType: payType.value });
    created.value = result;
    // 车里的行只有下了单才删：删早了，这一单没成，买的东西就凭空从车里少了
    const cartIds = lines.value.map((line) => line.cartId).filter((id): id is number => id != null);
    if (cartIds.length) {
      await cart.remove(cartIds).catch(() => undefined);
    }
    // 支付那一拍放在删车之后：钱没付成也不该把已经落下的单和已经清掉的车搅在一起说
    if (withPay) await pay(result);
  } catch (err) {
    submitError.value = (err as Error).message;
  } finally {
    submitting.value = false;
  }
}

/**
 * 直接调服务端那条支付回调（POST /api/comm/shop/order/payCallback），微信网关那一路还没接，
 * 所以这一颗点下去就等价于「这笔已经付过了」：主单 0 → 1、还的锁转正、券核销、积分冻结转消耗、
 * 出库单和配送单那几步都是那一段自己走完的（幂等靠它那句 WHERE status = 0）。
 * payAmount 送 create 报回来的那一格，服务端拿它和订单上的实付比，对不上就整笔拒。
 */
async function pay(result: OrderCreated) {
  payError.value = '';
  try {
    await payOrder({
      orderId: result.orderId,
      payType: payType.value,
      payAmount: result.payAmount ?? undefined,
    });
    paid.value = true;
  } catch (err) {
    payError.value = (err as Error).message;
  }
}

function clampPoints() {
  const next = Math.floor(Number(payPoints.value) || 0);
  payPoints.value = Math.min(Math.max(next, 0), pointsBalance.value);
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-semibold">{{ d.checkout.title }}</h1>
      <a
        :href="buyNow ? href(`/product/${props.productId}`) : href('/cart')"
        class="text-sm text-blue-600 hover:underline dark:text-blue-400"
      >
        {{ buyNow ? d.checkout.backToProduct : d.checkout.backToCart }}
      </a>
    </div>

    <div
      v-if="!signedIn"
      class="rounded-xl border border-gray-200 bg-white p-8 text-center dark:border-gray-700 dark:bg-gray-800"
    >
      <p class="text-gray-600 dark:text-gray-300">{{ d.checkout.signedOut }}</p>
      <button
        type="button"
        data-open-login
        class="mt-4 rounded-lg bg-blue-600 px-5 py-2 text-sm font-medium text-white hover:bg-blue-700"
      >
        {{ d.auth.entry }}
      </button>
    </div>

    <!-- 下成了就换这一屏：订单号和实付是 create 报回来的那一份，不是试算的 -->
    <div
      v-else-if="created"
      class="rounded-xl border border-gray-200 bg-white p-8 text-center dark:border-gray-700 dark:bg-gray-800"
    >
      <p class="text-lg font-semibold text-green-600 dark:text-green-400">
        {{ paid ? d.checkout.placedPaid : d.checkout.placed }}
      </p>
      <p class="mt-2 text-sm text-gray-600 dark:text-gray-300">
        {{ fill(d.checkout.orderNoLine, { no: created.orderNo }) }}
        <span class="font-medium text-red-600 dark:text-red-400">¥{{ money(created.payAmount) }}</span>
      </p>
      <p v-if="paid" class="mt-1 text-xs text-gray-500 dark:text-gray-400">
        {{ fill(d.checkout.paidRecorded, { pay: enumLabel(d.enum.payType, payType, d.common.dash) }) }}
      </p>
      <p v-else-if="payError" class="mt-1 text-xs text-red-600 dark:text-red-400">
        {{ fill(d.checkout.payFailed, { err: payError }) }}
      </p>
      <p v-else class="mt-1 text-xs text-gray-500 dark:text-gray-400">
        {{ d.checkout.awaitingPay }}
      </p>
      <div class="mt-5 flex flex-wrap justify-center gap-3">
        <a :href="href('/user')" class="rounded-lg bg-blue-600 px-5 py-2 text-sm font-medium text-white hover:bg-blue-700">
          {{ d.checkout.viewOrders }}
        </a>
        <a
          :href="href('/')"
          class="rounded-lg border border-gray-200 px-5 py-2 text-sm hover:bg-gray-50 dark:border-gray-700 dark:hover:bg-gray-700"
        >
          {{ d.checkout.keepBrowsing }}
        </a>
      </div>
    </div>

    <p
      v-else-if="loadError"
      class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300"
    >
      {{ loadError }}
    </p>

    <div
      v-else-if="!lines.length"
      class="rounded-xl border border-gray-200 bg-white p-10 text-center dark:border-gray-700 dark:bg-gray-800"
    >
      <p class="text-gray-600 dark:text-gray-300">{{ d.checkout.noLines }}</p>
      <a
        :href="href('/cart')"
        class="mt-4 inline-block rounded-lg bg-blue-600 px-5 py-2 text-sm font-medium text-white hover:bg-blue-700"
      >
        {{ d.checkout.goCart }}
      </a>
    </div>

    <template v-else>
      <!-- 商品清单：行是库里那一行，价是服务端那份快照，这里一个数都不自己拼 -->
      <section class="rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-700 dark:bg-gray-800">
        <h2 class="mb-3 text-base font-semibold">{{ fill(d.checkout.goodsHeading, { a: lines.length, b: totalQuantity }) }}</h2>
        <ul class="divide-y divide-gray-100 dark:divide-gray-700">
          <!-- key 不能用 skuId：同一规格可能并排两行（自己加购的 + 抽中入车的），重复 key 会让 Vue 复用错行 -->
          <li v-for="(line, i) in lines" :key="line.cartId ?? `${line.skuId}-${i}`" class="flex flex-wrap items-center gap-4 py-3">
            <div
              class="h-16 w-16 shrink-0 overflow-hidden rounded-lg border border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-900"
            >
              <img v-if="line.image" :src="line.image" :alt="line.productName" class="h-full w-full object-cover" />
            </div>
            <div class="min-w-0 flex-1">
              <p class="truncate font-medium">
                <a
                  v-if="line.productId"
                  :href="href(`/product/${line.productId}`)"
                  class="hover:text-blue-600 hover:underline dark:hover:text-blue-400"
                >{{ line.productName }}</a>
                <span v-else>{{ line.productName }}</span>
              </p>
              <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
                <span
                  v-if="line.sourceType === 1"
                  class="mr-2 rounded bg-amber-100 px-1.5 py-0.5 text-[11px] text-amber-700 dark:bg-amber-900/40 dark:text-amber-300"
                >{{ boxTagOf(isWholeBoxLine(line) ? d.cart.endBoxLine : d.cart.boxLine, line) }}</span>
                <!-- 一整盒那一行不说规格（它不挂在某条规格上），说这一盒几件、几盒、每盒多少钱 -->
                <template v-if="line.boxPieces != null">
                  {{ fill(d.checkout.boxLine, { price: `¥${money(line.price)}`, qty: line.quantity, n: line.boxPieces }) }}
                </template>
                <template v-else>
                  {{ fill(d.checkout.specLine, { spec: specLabel(line), price: `¥${money(line.price)}`, qty: line.quantity }) }}
                </template>
              </p>
              <p v-if="reasonOf(line)" class="mt-1 text-xs text-red-600 dark:text-red-400">{{ reasonOf(line) }}</p>
              <p v-else-if="lineResultText(i)" class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                {{ lineResultText(i) }}
              </p>
              <!-- 一整盒那一行默认只报「几件 × 几盒」，点这颗才下拉看盒内每款
                   （摊到每款的单价不在这里报 —— 那一笔是账本上拆给退款/出库用的，不是给买家看的） -->
              <button
                v-if="line.boxPieces != null && itemsOfLine(line).length"
                type="button"
                class="mt-2 inline-flex items-center gap-1.5 rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-600 hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
                @click="toggleBoxOpen(String(line.cartId ?? i))"
              ><span
                  class="inline-block text-[10px] transition-transform"
                  :class="openedBoxes.has(String(line.cartId ?? i)) ? 'rotate-90' : ''"
                  aria-hidden="true"
                >▶</span>{{ d.checkout.boxStyles }}</button>
              <ul
                v-if="line.boxPieces != null && openedBoxes.has(String(line.cartId ?? i))"
                class="mt-2 space-y-1 text-xs text-gray-500 dark:text-gray-400"
              >
                <li
                  v-for="one in itemsOfLine(line)"
                  :key="`${one.skuId}-${one.specText ?? ''}`"
                  class="flex items-center gap-2"
                >
                  <span class="min-w-0 flex-1 truncate">
                    {{ one.productName }} · {{ one.specText?.trim() || d.checkout.defaultSpec }}
                  </span>
                  <span class="shrink-0">× {{ one.quantity ?? 0 }}</span>
                </li>
              </ul>
            </div>
            <p class="w-24 shrink-0 text-right text-sm font-medium text-red-600 dark:text-red-400">
              ¥{{ money(line.price * line.quantity) }}
            </p>
          </li>
        </ul>
        <p
          v-if="blockedLines.length"
          class="mt-3 rounded-lg bg-red-50 px-4 py-2 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300"
        >
          {{ fill(d.checkout.blockedLines, { n: blockedLines.length }) }}<a
            v-if="!buyNow"
            :href="href('/cart')"
            class="underline"
          >{{ d.checkout.adjustInCart }}</a>
        </p>
      </section>

      <!-- 收货地址：服务端只认 addressId，分仓先按坐标算距离、没坐标就按那条的地区匹仓，
           所以只有「既没坐标又没地区」的那一条才不给选 -->
      <section class="rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-700 dark:bg-gray-800">
        <div class="mb-3 flex items-center justify-between">
          <h2 class="text-base font-semibold">{{ d.checkout.addressHeading }}</h2>
          <button
            type="button"
            class="text-sm text-blue-600 hover:underline dark:text-blue-400"
            @click="addressManagerOpen = true"
          >
            {{ d.checkout.manageAddresses }}
          </button>
        </div>
        <p v-if="!addresses.length" class="text-sm text-gray-500 dark:text-gray-400">
          {{ d.checkout.noAddressLead }}
          <button
            type="button"
            class="text-blue-600 hover:underline dark:text-blue-400"
            @click="addressManagerOpen = true"
          >
            {{ d.checkout.addAddressFirst }}
          </button>
        </p>
        <div v-else class="grid grid-cols-1 gap-3 md:grid-cols-2">
          <label
            v-for="row in addresses"
            :key="row.id"
            class="flex gap-3 rounded-lg border p-3 text-sm transition-colors"
            :class="
              !canShipTo(row)
                ? 'cursor-not-allowed border-gray-200 text-gray-400 dark:border-gray-700 dark:text-gray-500'
                : row.id === addressId
                  ? 'border-blue-600 bg-blue-50 text-blue-800 dark:border-blue-500 dark:bg-blue-900/40 dark:text-blue-100'
                  : 'cursor-pointer border-gray-200 hover:border-gray-400 dark:border-gray-700 dark:hover:border-gray-500'
            "
          >
            <input
              type="radio"
              name="address"
              class="mt-1 shrink-0"
              :disabled="!canShipTo(row)"
              :checked="row.id === addressId"
              @change="addressId = row.id"
            />
            <span class="min-w-0">
              <span class="font-medium">{{ row.receiverName }}</span>
              <span class="ml-2">{{ row.receiverPhone }}</span>
              <span
                v-if="row.tag"
                class="ml-2 rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-300"
              >{{ row.tag }}</span>
              <span
                v-if="row.isDefault === 1"
                class="ml-2 rounded bg-blue-100 px-1.5 py-0.5 text-xs text-blue-700 dark:bg-blue-900/60 dark:text-blue-200"
              >{{ d.checkout.defaultTag }}</span>
              <span class="mt-1 block break-words text-gray-600 dark:text-gray-300">{{ row.areaZh }} {{ row.detail }}</span>
              <span v-if="!canShipTo(row)" class="mt-1 block text-xs">
                {{ d.checkout.addressNoCoord }}
              </span>
            </span>
          </label>
        </div>
      </section>

      <!-- 就地增改收货地址：新增/修改用的还是个人中心那一份表单，改完重拉列表并重算一次钱 -->
      <AddressManager
        v-if="addressManagerOpen"
        @changed="onAddressChanged"
        @close="addressManagerOpen = false"
      />

      <section class="rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-700 dark:bg-gray-800">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 class="text-base font-semibold">{{ d.enum.coupon }}</h2>
          <span class="text-sm text-gray-500 dark:text-gray-400">
            {{ fill(d.checkout.couponSelected, { a: couponIds.length, b: MAX_COUPON_COUNT }) }}
          </span>
        </div>
        <p v-if="!coupons.length" class="text-sm text-gray-500 dark:text-gray-400">
          {{ d.checkout.noCoupons }}
        </p>
        <div v-else class="max-h-72 space-y-2 overflow-y-auto pr-1">
          <label
            v-for="row in coupons"
            :key="row.id"
            class="flex items-start gap-3 rounded-lg border border-gray-200 p-3 text-sm transition-colors dark:border-gray-700"
            :class="
              couponExpired(row)
                ? 'cursor-not-allowed bg-gray-50 text-gray-400 dark:bg-gray-700/30 dark:text-gray-500'
                : couponIds.includes(row.id)
                  ? 'cursor-pointer bg-blue-50 dark:bg-blue-900/30'
                  : 'cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700/50'
            "
          >
            <input
              type="checkbox"
              class="mt-1 shrink-0"
              :checked="couponIds.includes(row.id)"
              :disabled="
                couponExpired(row) || (!couponIds.includes(row.id) && couponIds.length >= MAX_COUPON_COUNT)
              "
              @change="toggleCoupon(row)"
            />
            <span class="min-w-0 flex-1">
              <span class="font-medium">{{ row.couponName || d.enum.coupon }}</span>
              <span
                class="ml-2 rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-300"
              >{{ enumLabel(d.enum.couponType, row.couponType, d.common.dash) }}</span>
              <span
                v-if="couponExpired(row)"
                class="ml-2 rounded bg-gray-200 px-1.5 py-0.5 text-xs text-gray-600 dark:bg-gray-600 dark:text-gray-200"
              >{{ enumLabel(d.enum.couponUserStatus, 2, d.common.dash) }}</span>
              <span class="mt-1 block text-gray-600 dark:text-gray-300">{{ couponBenefit(i18n, row) }}</span>
              <span class="mt-1 block text-xs text-gray-500 dark:text-gray-400">
                {{ Number(row.thresholdAmount) > 0 ? fill(d.checkout.couponThresholdUsable, { n: money(row.thresholdAmount) }) : d.checkout.couponNoThreshold }}
                {{ couponExpired(row) ? d.checkout.expiredOn : d.checkout.validUntil }}
                {{ (row.expireTime || '').slice(0, 10) || d.common.dash }}
              </span>
            </span>
            <span v-if="couponResultText(row.id)" class="shrink-0 text-right text-xs text-red-600 dark:text-red-400">
              {{ couponResultText(row.id) }}
            </span>
          </label>
        </div>
      </section>

      <section class="rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-700 dark:bg-gray-800">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 class="text-base font-semibold">{{ d.checkout.pointsHeading }}</h2>
          <span class="text-sm text-gray-500 dark:text-gray-400">{{ fill(d.checkout.pointsAvailable, { n: pointsBalance }) }}</span>
        </div>
        <div class="flex flex-wrap items-center gap-3 text-sm">
          <input
            v-model.number="payPoints"
            type="number"
            min="0"
            :max="pointsBalance"
            class="w-32 rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm dark:border-gray-700 dark:bg-gray-900"
            @blur="clampPoints"
          />
          <button
            type="button"
            class="rounded-lg border border-gray-200 px-3 py-1.5 text-xs hover:bg-gray-50 disabled:cursor-not-allowed disabled:text-gray-300 dark:border-gray-700 dark:hover:bg-gray-700 dark:disabled:text-gray-600"
            :disabled="!pointsBalance"
            @click="payPoints = pointsBalance"
          >
            {{ d.common.all }}
          </button>
          <span v-if="Number(preview?.pointsDiscount ?? 0) > 0" class="text-gray-600 dark:text-gray-300">
            {{ fill(d.checkout.pointsCanDeduct, { n: `¥${money(preview?.pointsDiscount)}` }) }}
          </span>
          <span v-else class="text-gray-500 dark:text-gray-400">{{ d.checkout.pointsRateNote }}</span>
        </div>
        <p class="mt-2 text-xs text-gray-500 dark:text-gray-400">
          {{ d.checkout.pointsSpreadNote }}
        </p>
      </section>

      <!-- 支付方式：档位 = 字典 pay_type。这一格现在下单也带（余额要在下单那一刻占住钱），回调再按实际渠道盖一次 -->
      <section class="rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-700 dark:bg-gray-800">
        <h2 class="mb-3 text-base font-semibold">{{ d.checkout.payMethodHeading }}</h2>
        <div class="flex flex-wrap gap-3">
          <label
            v-for="opt in payOptions"
            :key="opt.value"
            class="flex items-center gap-2 rounded-lg border px-4 py-2 text-sm transition-colors"
            :class="[
              opt.value === payType
                ? 'border-blue-600 bg-blue-50 text-blue-800 dark:border-blue-500 dark:bg-blue-900/40 dark:text-blue-100'
                : 'border-gray-200 hover:border-gray-400 dark:border-gray-700 dark:hover:border-gray-500',
              opt.value === PAY_TYPE_BALANCE && !balanceUsable
                ? 'cursor-not-allowed opacity-50 hover:border-gray-200 dark:hover:border-gray-700'
                : 'cursor-pointer',
            ]"
          >
            <input
              v-model="payType"
              type="radio"
              name="payType"
              :value="opt.value"
              class="shrink-0"
              :disabled="opt.value === PAY_TYPE_BALANCE && !balanceUsable"
            />
            {{ opt.label }}
            <span v-if="opt.value === PAY_TYPE_BALANCE" class="text-xs text-gray-500 dark:text-gray-400">
              {{ fill(d.checkout.balanceAvailable, { n: `¥${money(walletBalance)}` }) }}
            </span>
          </label>
        </div>
        <p v-if="wallet == null" class="mt-2 text-xs text-gray-500 dark:text-gray-400">
          {{ d.checkout.walletNoneNote }}
        </p>
        <p v-else-if="!walletUsable" class="mt-2 text-xs text-gray-500 dark:text-gray-400">
          {{ d.checkout.walletFrozenNote }}
        </p>
        <p v-else-if="!balanceUsable" class="mt-2 text-xs text-gray-500 dark:text-gray-400">
          {{ fill(d.checkout.balanceShortNote, { n: `¥${money(payAmount - walletBalance)}` }) }}
        </p>
        <p class="mt-2 text-xs text-gray-500 dark:text-gray-400">
          {{ d.checkout.gatewayNote }}
        </p>
      </section>

      <section class="rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-700 dark:bg-gray-800">
        <h2 class="mb-3 text-base font-semibold">{{ d.checkout.remarkHeading }}</h2>
        <input
          v-model="remark"
          type="text"
          maxlength="200"
          :placeholder="d.checkout.remarkPlaceholder"
          class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-900"
        />
      </section>

      <p
        v-if="previewError"
        class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300"
      >
        {{ previewError }}
      </p>

      <section class="rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-700 dark:bg-gray-800">
        <h2 class="mb-3 text-base font-semibold">{{ d.checkout.amountHeading }}</h2>
        <dl class="space-y-2 text-sm">
          <div class="flex justify-between">
            <dt class="text-gray-600 dark:text-gray-300">{{ d.checkout.goodsAmount }}</dt>
            <dd>{{ preview ? `¥${money(preview.goodsAmount)}` : d.common.dash }}</dd>
          </div>
          <div class="flex justify-between">
            <dt class="text-gray-600 dark:text-gray-300">{{ d.checkout.deliveryFee }}</dt>
            <dd>{{ preview ? `¥${money(preview.deliveryFee)}` : d.common.dash }}</dd>
          </div>
          <div class="flex justify-between">
            <dt class="text-gray-600 dark:text-gray-300">{{ d.enum.coupon }}</dt>
            <dd class="text-red-600 dark:text-red-400">
              {{ preview ? `-¥${money(preview.discountAmount)}` : d.common.dash }}
              <span v-if="Number(preview?.shippingDiscount ?? 0) > 0" class="ml-1 text-xs text-gray-500 dark:text-gray-400">
                {{ fill(d.checkout.includesShipping, { n: `¥${money(preview?.shippingDiscount)}` }) }}
              </span>
            </dd>
          </div>
          <div class="flex justify-between">
            <dt class="text-gray-600 dark:text-gray-300">{{ d.checkout.pointsHeading }}</dt>
            <dd class="text-red-600 dark:text-red-400">
              {{ preview ? `-¥${money(preview.pointsDiscount)}` : d.common.dash }}
              <span v-if="Number(preview?.usedPoints ?? 0) > 0" class="ml-1 text-xs text-gray-500 dark:text-gray-400">
                {{ fill(d.checkout.pointsUsedParen, { n: preview?.usedPoints ?? 0 }) }}
              </span>
            </dd>
          </div>
          <div class="flex items-baseline justify-between border-t border-gray-100 pt-3 dark:border-gray-700">
            <dt>{{ d.checkout.payable }}</dt>
            <dd class="text-xl font-semibold text-red-600 dark:text-red-400">
              {{ preview ? `¥${money(preview.payAmount)}` : previewing ? d.checkout.computing : d.common.dash }}
            </dd>
          </div>
        </dl>

        <div class="mt-4 flex flex-wrap items-center gap-4">
          <span :data-open-login="signedIn ? null : ''" class="inline-flex">
            <FwbButton color="alternative" :disabled="!canSubmit" @click="submit(false)">
              {{ submitting ? d.checkout.submitting : d.checkout.placeOrder }}
            </FwbButton>
          </span>
          <span :data-open-login="signedIn ? null : ''" class="inline-flex">
            <FwbButton color="blue" :disabled="!canSubmit" @click="submit(true)">
              {{ submitting ? d.checkout.processing : fill(d.checkout.placeAndPay, { n: `¥${preview ? money(preview.payAmount) : d.common.dash}` }) }}
            </FwbButton>
          </span>
          <span v-if="submitHint" class="text-sm text-gray-500 dark:text-gray-400">{{ submitHint }}</span>
          <span v-else-if="previewing" class="text-sm text-gray-500 dark:text-gray-400">{{ d.checkout.amountUpdating }}</span>
        </div>
        <p v-if="submitError" class="mt-2 text-sm text-red-600 dark:text-red-400">{{ submitError }}</p>
      </section>
    </template>
  </div>
</template>
