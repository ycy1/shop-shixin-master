<script lang="ts" setup>
import type { IShopCartRow } from '@/api/types/shop'
import {
  boxPieceCount,
  deleteCart,
  getMyCart,
  isWholeBoxRow,
  payPriceOf,
  rowImageOf,
  setAllCartSelected,
  stockCapOf,
  updateCart,
} from '@/api/cart'
import { useTokenStore } from '@/store/token'
import { setCartBadge } from '@/utils/cartBadge'
import AppNavBar from '@/components/AppNavBar.vue'
import AppSearchBar from '@/components/AppSearchBar.vue'
import { toLoginPage } from '@/utils/toLoginPage'

defineOptions({
  name: 'Cart',
})

definePage({
  style: {
    navigationBarTitleText: '购物车',
    // 导航条自绘，同首页（components/AppNavBar.vue）
    navigationStyle: 'custom',
  },
})

const tokenStore = useTokenStore()

const rows = ref<IShopCartRow[]>([])
const loading = ref(true)
/**
 * 整页一次只让一颗写按得下去（哪一颗在跑记在这儿，只用来把控件置灰）：
 *  每颗写失败都要重拉一次列表回退，两行同时跑会互相把对方的画面换掉
 */
const busyId = ref<number | null>(null)
const batchBusy = ref(false)
const actionError = ref('')

/** 还能买而且勾着的那几行：合计、全选、「去结算」都只看这一批 */
const picked = computed(() => rows.value.filter(one => one.buyable === 1 && one.selected === 1))
const buyableRows = computed(() => rows.value.filter(one => one.buyable === 1))
const allSelected = computed(() => buyableRows.value.length > 0 && picked.value.length === buyableRows.value.length)
/** 合计只按「勾着还能买」的那几行，单价一律走 payPriceOf（抽中入车的那几条是盒的单抽价） */
const pickedTotal = computed(() =>
  picked.value.reduce((sum, one) => sum + payPriceOf(one) * one.quantity, 0).toFixed(2),
)

/** 规格文本这一格后端可空（无规格的默认 SKU），页面自己补一个说法，口径同详情页 */
function specLabel(one: IShopCartRow) {
  return one.specText?.trim() || '默认规格'
}

/** 这一行念成什么：整盒那一行没有商品名（它不挂在某个商品上），念「一整盒 · 盒名」 */
function titleLabel(one: IShopCartRow) {
  if (isWholeBoxRow(one)) {
    return one.sourceName ? `一整盒 · ${one.sourceName}` : '一整盒'
  }
  return one.productName || '商品已被删掉'
}

/** 点开看盒内有哪几款、各几件：就地展开，不跳页 */
const opened = ref<Set<number>>(new Set())

function toggleBoxOpen(id: number) {
  const next = new Set(opened.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  opened.value = next
}

function lineTotal(one: IShopCartRow) {
  return (payPriceOf(one) * one.quantity).toFixed(2)
}

/**
 * 步进封顶：普通行封到这一条规格的可用库存（库里没有那一行的库存行时后端给 0，这时候不给往上加，
 * 下单那一刻也会拦）；整盒那一行不封顶 —— 它按「几盒」加减，某一款的仓存不是这一行的上限。
 */
function stockOf(one: IShopCartRow) {
  return stockCapOf(one)
}

/**
 * 已经翻给用户、库里还是旧值的那几行（id → 要写成的值）。任何一趟重拉都要把它们贴回去：
 * 改数量那一跑末尾也会 load()，不贴的话刚勾上的圈会被库里那份冲回原样。
 * 去结算前也要等这批写落干净 —— 结算页是重拉一趟车、取库里 selected=1 的那几行。
 */
const pendingSelected = new Map<number, 0 | 1>()
const pendingTasks: Promise<void>[] = []

async function load() {
  loading.value = true
  try {
    rows.value = await getMyCart()
    rows.value.forEach((one) => {
      const want = pendingSelected.get(one.id)
      if (want != null) {
        one.selected = want
      }
    })
    setCartBadge(rows.value)
    actionError.value = ''
  }
  catch {
    // 拉不到就留着上一趟的画面，红一行提示；http 层已经 toast 过后端那句
    actionError.value = '购物车加载失败'
  }
  finally {
    loading.value = false
  }
}

/** 单颗写失败就地印后端那句（「数量要大于 0」这种），失败后重拉一遍把那一行退回库里那一份 */
async function runOnRow(one: IShopCartRow, write: () => Promise<void>) {
  if (busyId.value !== null || batchBusy.value) {
    return
  }
  busyId.value = one.id
  actionError.value = ''
  try {
    await write()
    await load()
  }
  catch {
    await load()
  }
  finally {
    busyId.value = null
  }
}

/** 把「已经翻给用户、库里还是旧值」这一笔挂进 pending（见上面那段），写失败就重拉退回库里那份 */
function scheduleSelected(one: IShopCartRow, next: 0 | 1) {
  pendingSelected.set(one.id, next)
  const task = updateCart({ id: one.id, selected: next }).catch(async () => {
    await load()
  })
  pendingTasks.push(task)
  void task.finally(() => {
    if (pendingSelected.get(one.id) === next) {
      pendingSelected.delete(one.id)
    }
    const at = pendingTasks.indexOf(task)
    if (at >= 0) {
      pendingTasks.splice(at, 1)
    }
  })
}

function stepQuantity(one: IShopCartRow, delta: number) {
  const next = one.quantity + delta
  if (next < 1 || next > stockOf(one)) {
    return
  }
  runOnRow(one, () => updateCart({ id: one.id, quantity: next }))
}

/** 当场翻，不等回包 */
function toggleSelected(one: IShopCartRow) {
  if (one.buyable !== 1 || batchBusy.value) {
    return
  }
  const next: 0 | 1 = one.selected === 1 ? 0 : 1
  one.selected = next
  scheduleSelected(one, next)
}

async function toggleAll() {
  if (batchBusy.value || !buyableRows.value.length) {
    return
  }
  const next: 0 | 1 = allSelected.value ? 0 : 1
  buyableRows.value.forEach((one) => {
    one.selected = next
    pendingSelected.set(one.id, next)
  })
  batchBusy.value = true
  actionError.value = ''
  try {
    await setAllCartSelected(next)
    // 库里整一片已经是 next 了，这些「待贴」不再需要：留着会把以后重拉回来的正确值盖掉
    pendingSelected.clear()
  }
  catch {
    // 服务端没刷成，先把本地刚翻的那批从 pending 里摘掉，免得重拉时被旧口径的贴回去
    buyableRows.value.forEach(one => pendingSelected.delete(one.id))
    await load()
  }
  finally {
    batchBusy.value = false
  }
}

function askDelete(one: IShopCartRow) {
  uni.showModal({
    title: '删除',
    content: isWholeBoxRow(one)
      ? `把「${titleLabel(one)}」这一整盒从购物车里删掉？`
      : `把「${titleLabel(one)} · ${specLabel(one)}」从购物车里删掉？`,
    success: (res) => {
      if (res.confirm) {
        removeRows([one.id])
      }
    },
  })
}

function askDeletePicked() {
  const ids = picked.value.map(one => one.id)
  if (!ids.length) {
    return
  }
  uni.showModal({
    title: '删除',
    content: `删掉勾着的 ${ids.length} 条？`,
    success: (res) => {
      if (res.confirm) {
        removeRows(ids)
      }
    },
  })
}

async function removeRows(ids: number[]) {
  batchBusy.value = true
  actionError.value = ''
  try {
    await deleteCart(ids)
  }
  catch { /* http 层已提示 */ }
  finally {
    // 删失败可能是库里那几条已经不是这个状态了（别的端删过、后台代客清了），回库里那一份重画
    await load()
    batchBusy.value = false
  }
}

/** 去结算：把勾着的那几行交给结算页，那一页自己重拉一遍（口径同 shop-web 的购物车 → checkout） */
async function toCheckout() {
  if (!picked.value.length || batchBusy.value) {
    return
  }
  // 借用 batchBusy：这一档里「全选」那颗本来也要占住整页，正好把 flush 这段也算进去（那颗按钮本来就 disabled）
  batchBusy.value = true
  try {
    await Promise.all(pendingTasks.slice())
  }
  finally {
    batchBusy.value = false
  }
  uni.navigateTo({ url: '/pages/order/preview?from=cart' })
}

function toDetail(productId: number | null) {
  if (productId) {
    uni.navigateTo({ url: `/pages/goods/detail?id=${productId}` })
  }
}

function toHome() {
  uni.switchTab({ url: '/pages/index/index' })
}

// 加购、改数量都在别的页面上也可能发生，每次回到这一页都重拉一遍库里那一份
onShow(() => {
  if (!tokenStore.updateNowTime().hasLogin) {
    loading.value = false
    return
  }
  load()
})
</script>

<template>
  <view class="bg-[#f5f6f7] pb-[200rpx]">
    <AppNavBar title="购物车" />

    <!-- 搜索入口 + 抽屉那颗：和首页同一排公共件（components/AppSearchBar.vue），四张 tab 页都有 -->
    <AppSearchBar />

    <view v-if="!tokenStore.updateNowTime().hasLogin" class="mx-24rpx mb-24rpx mt-24rpx rounded-xl bg-white px-32rpx py-80rpx text-center">
      <view class="text-[28rpx] text-gray-500">
        登录后才能看自己车里的东西
      </view>
      <wd-button class="mt-32rpx" type="error" @click="toLoginPage()">
        去登录
      </wd-button>
    </view>

    <template v-else>
      <view v-if="!rows.length && !loading" class="mx-24rpx mb-24rpx mt-24rpx rounded-xl bg-white px-32rpx py-96rpx text-center">
        <view class="text-[28rpx] text-gray-500">
          购物车还是空的
        </view>
        <wd-button class="mt-32rpx" type="error" plain @click="toHome">
          去逛商品
        </wd-button>
      </view>

      <template v-else>
        <view v-if="actionError" class="mx-24rpx mb-24rpx mt-24rpx rounded-lg bg-red-50 px-32rpx py-16rpx text-[26rpx] text-[#d14328]">
          {{ actionError }}
        </view>

        <!-- 改这一整列的东西（全选、删除）的按钮开一行放在列表上面，不塞进下面那条合计里 -->
        <!-- mt 那一格是给自绘导航栏让位的：顶上两块（报错条 / 这一排）谁先画出来都得离标题留一截，两处都给上 -->
        <view class="mx-24rpx mb-24rpx mt-24rpx flex items-center gap-24rpx rounded-xl bg-white px-24rpx py-24rpx">
          <view hover-class="fx-tap" class="fx-press flex items-center" @click="toggleAll">
            <view class="cart-check" :class="allSelected ? 'cart-check-on' : ''">
              ✓
            </view>
            <text class="ml-16rpx text-[26rpx] text-[#666]">全选</text>
          </view>
          <text class="flex-1 text-[26rpx] text-gray-400">已选 {{ picked.length }} 件</text>
          <text
            hover-class="fx-tap"
            class="fx-press text-[26rpx]"
            :class="picked.length && !batchBusy ? 'text-[#666]' : 'text-gray-300'"
            @click="askDeletePicked"
          >
            删除选中
          </text>
        </view>

        <view class="mx-24rpx mb-24rpx rounded-xl bg-white px-24rpx py-8rpx">
          <view
            v-for="one in rows"
            :key="one.id"
            class="flex items-center border-b border-gray-50 py-24rpx last:border-b-0"
            :class="one.buyable === 1 ? '' : 'opacity-60'"
          >
            <view hover-class="fx-tap" class="fx-press flex items-center px-8rpx" @click="toggleSelected(one)">
              <view
                class="cart-check"
                :class="one.selected === 1 && one.buyable === 1 ? 'cart-check-on' : (one.buyable === 1 ? '' : 'cart-check-void')"
              >
                ✓
              </view>
            </view>

            <image
              v-if="rowImageOf(one)"
              hover-class="fx-tap"
              :src="rowImageOf(one) || ''"
              mode="aspectFill"
              class="fx-press ml-16rpx h-[140rpx] w-[140rpx] shrink-0 rounded-lg bg-gray-50"
              @click="toDetail(one.productId)"
            />
            <view v-else class="ml-16rpx h-[140rpx] w-[140rpx] shrink-0 rounded-lg bg-gray-100" />

            <view hover-class="fx-tap" class="fx-press ml-24rpx min-w-0 flex-1" @click="toDetail(one.productId)">
              <view class="truncate text-[28rpx] text-[#333]">
                {{ titleLabel(one) }}
              </view>
              <view class="mt-8rpx truncate text-[24rpx] text-gray-400">
                <!-- 抽中入车的这一行和同规格自己加购的那一行并排躺着，不标出来看着像重复加了两次；
                     两台盒抽中同一款也是并排两行（各挂各的单抽价），所以顺带报是哪台盒；
                     端盒那一趟是一整盒一行，说端盒、不说规格（它不挂在某条规格上） -->
                <text v-if="one.sourceType === 1" class="mr-8rpx rounded bg-[#fdf6ec] px-8rpx text-[20rpx] text-[#e6a23c]">{{ isWholeBoxRow(one) ? '端盒整盒' : '盲盒商品' }}{{ one.sourceName ? ` · ${one.sourceName}` : '' }}</text>
                <view
                  v-if="isWholeBoxRow(one)"
                  class="inline-flex items-center gap-8rpx rounded bg-gray-100 px-12rpx py-4rpx text-[22rpx] text-gray-500"
                  @click.stop="toggleBoxOpen(one.id)"
                >
                  <text
                    class="inline-block text-[18rpx] transition-transform"
                    :class="opened.has(one.id) ? 'rotate-90' : ''"
                  >▶</text>
                  <text>盒内共 {{ boxPieceCount(one) }} 件</text>
                </view>
                <text v-else>规格 {{ specLabel(one) }}</text>
              </view>
              <!-- 展开看盒内有哪几款、各几件：只给说法，钱是这一盒自己的单价，不往每款上摊给买家看 -->
              <view v-if="isWholeBoxRow(one) && opened.has(one.id)" class="mt-8rpx space-y-8rpx">
                <view
                  v-for="(item, i) in one.boxItemList"
                  :key="`${one.id}-${i}`"
                  class="flex items-center gap-12rpx text-[22rpx] text-gray-500"
                >
                  <image
                    v-if="item.styleImage"
                    :src="item.styleImage"
                    mode="aspectFill"
                    class="h-40rpx w-40rpx shrink-0 rounded"
                  />
                  <text class="min-w-0 flex-1 truncate">{{ item.styleName || '这款已经没了' }}</text>
                  <!-- 款式清单是「一盒里有什么」，这一行是几盒就翻几倍（同一台盒端第二次并成一行） -->
                  <text class="shrink-0">× {{ Number(item.quantity ?? 1) * one.quantity }}</text>
                </view>
              </view>
              <!-- 买不了只有「商品已下架」这一句：规格的 sale_enable 是 ERP 建销售单那边的开关，不管前台 -->
              <view v-if="one.buyable !== 1" class="mt-8rpx text-[22rpx] text-[#e6a23c]">
                商品已下架
              </view>
              <view class="mt-16rpx flex items-end justify-between">
                <text class="text-[28rpx] text-[#333]">¥{{ payPriceOf(one).toFixed(2) }}</text>
                <!-- 步进器这一整块要 .stop：它就长在「点这一行进商品详情」那一颗的点击区里 -->
                <view hover-class="fx-tap" class="cart-stepper fx-press flex items-center overflow-hidden border border-gray-200 rounded-lg" @click.stop>
                  <text
                    hover-class="fx-tap"
                    class="fx-press w-[56rpx] py-8rpx text-center text-[28rpx] text-gray-500"
                    :class="one.quantity <= 1 || busyId !== null ? 'text-gray-300' : ''"
                    @click="stepQuantity(one, -1)"
                  >
                    −
                  </text>
                  <text class="cart-step-num w-[72rpx] border-x border-gray-200 py-8rpx text-center text-[26rpx]">{{ one.quantity }}</text>
                  <text
                    hover-class="fx-tap"
                    class="fx-press w-[56rpx] py-8rpx text-center text-[28rpx] text-gray-500"
                    :class="one.buyable !== 1 || one.quantity >= stockOf(one) || busyId !== null ? 'text-gray-300' : ''"
                    @click="stepQuantity(one, 1)"
                  >
                    +
                  </text>
                </view>
              </view>
            </view>

            <view class="ml-16rpx w-[150rpx] shrink-0 text-right">
              <view class="text-[28rpx] text-[#d14328] font-medium">
                ¥{{ lineTotal(one) }}
              </view>
              <view hover-class="fx-tap" class="fx-press mt-24rpx text-[26rpx] text-gray-400" @click="askDelete(one)">
                删除
              </view>
            </view>
          </view>
        </view>
      </template>

      <!-- 底部这一条：合计只是货值，配送费、券、积分那一摊在结算页问服务端才准 -->
      <view
        v-if="rows.length"
        class="cart-bar fixed bottom-100rpx left-0 right-0 z-10 flex items-center justify-between border-t border-gray-100 bg-white px-32rpx py-24rpx"
      >
        <view>
          <view class="text-[30rpx] text-[#d14328]">
            合计 ¥{{ pickedTotal }}
          </view>
          <view class="mt-8rpx text-[22rpx] text-gray-400">
            另计配送费，券和积分在结算页挑
          </view>
        </view>
        <wd-button type="error" :disabled="!picked.length || batchBusy" @click="toCheckout">
          去结算
        </wd-button>
      </view>
    </template>

    <!-- 页面尾巴把「安全区」那一段也垫出来：上面那条合计栏被 .cart-bar 往上顶了同样一截，
         不跟着让的话最后一张卡片的「删除」就压在栏底下（小程序上这段是 34px，H5 桌面是 0） -->
    <view class="pb-safe" />
  </view>
</template>

<style lang="scss" scoped>
/*
 * 那颗圆形勾选框。之前是 `border border-gray-300` 那一套：uno.config.ts 里把 Uno 的 preflight
 * （给所有元素补 border-style: solid 的那条兜底）整段注释掉了，而 `border` 工具类只给宽度不给样式，
 * 于是 border-style 还是默认的 none —— 圈根本画不出来，加上未选中时那个 ✓ 是透明的，整颗就消失了。
 * 这里自己写死 solid 描边：未选中一圈灰、选中红底白勾、下架那行一格灰底。
 */
.cart-check {
  box-sizing: border-box;
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 40rpx;
  height: 40rpx;
  font-size: 26rpx;
  line-height: 1;
  color: transparent;
  background-color: #fff;
  border: 3rpx solid #c0c4cc;
  border-radius: 999rpx;
}

.cart-check-on {
  color: #fff;
  background-color: #d14328;
  border-color: #d14328;
}

.cart-check-void {
  background-color: #f2f3f5;
  border-color: #e4e7ed;
}

/*
 * 这条合计栏原本只有 `bottom-100rpx`，是按 H5 桌面那档量的：那里安全区是 0，tabbar 正好 100rpx。
 * 小程序（刘海屏那档）上 tabbar 是「100rpx + 安全区」—— 它自己那层带了 pb-safe（见 src/tabbar/index.vue），
 * 于是整条 bar 比 100rpx 高出 34px，把「去结算」压掉半截。
 * 用 margin-bottom 而不是把 bottom 改成 calc(100rpx + env(...))：fixed 元素的 bottom 定的是外边距盒的下沿，
 * 加一段下外边距就等于整条往上顶，bottom 那个 rpx 值不用动（rpx 写进 calc 里，H5 那侧的换算不认）。
 */
.cart-bar {
  margin-bottom: env(safe-area-inset-bottom);
  border-top-style: solid;
}

/*
 * 步进器那圈描边 + 中间数量格左右两条分隔线：坑同 .cart-check ——
 * uno.config.ts 里 preflight 整段注释掉了，`border` / `border-x` 只给宽度不给 border-style，
 * 默认 none，线画不出来。宽度、颜色还是模板上那两个工具类给的，这里只补 style。
 */
.cart-stepper {
  border-style: solid;
}

.cart-step-num {
  border-left-style: solid;
  border-right-style: solid;
}
</style>
