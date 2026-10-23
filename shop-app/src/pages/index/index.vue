<script lang="ts" setup>
import type { IShopActivity, IShopBrand, IShopCategory, IShopProduct, IShopStore } from '@/api/types/shop'
import { ACTIVITY_EVENT_CLICK, ACTIVITY_EVENT_VIEW, trackActivity } from '@/api/activity'
import { getBrandList, getCategoryList, getHomeActivities, getStoreList } from '@/api/home'
import { getProductList } from '@/api/product'
import ProductGrid from '@/components/ProductGrid.vue'

defineOptions({
  name: 'Home',
})

definePage({
  // 使用 type: "home" 属性设置首页，其他页面不需要设置，默认为page
  type: 'home',
  style: {
    navigationBarTitleText: '商品',
    enablePullDownRefresh: true,
    onReachBottomDistance: 100,
  },
})

const PAGE_SIZE = 10
/** 门店/品牌那一行只摆 4 个当地图用，剩下的去搜索那一档看 */
const SHELF_SIZE = 4

const list = ref<IShopProduct[]>([])
const total = ref(0)
const pageNum = ref(0)
// 下拉刷新和触底可能同一时刻都进来，同一趟只跑一个请求
let loading = false
/** 请求在路上又换了分类：记一笔，等这一趟落下来补跑一次 —— 不然新条件被静默吞掉，列表还是旧那一串 */
let reloadWanted = false

const banners = ref<IShopActivity[]>([])
const stores = ref<IShopStore[]>([])
const brands = ref<IShopBrand[]>([])

/**
 * 「全部商品」那一块的分类筛选：候选一次给全（只含启用中的，已按父子顺序铺平），
 * 选哪一级都只往商品列表递一个 categoryId，「命中本级连同整棵下级」是服务端展开的，页面不自己凑。
 * 0 = 不限（后端那个字段是 isNotBlank/isNotNull 才参与筛，0 传过去等于筛出空集，所以换成不传）。
 */
const categories = ref<IShopCategory[]>([])
const categoryId = ref(0)
const categoryError = ref('')

async function loadCategories() {
  try {
    categories.value = await getCategoryList()
    categoryError.value = ''
  }
  catch (err) {
    // 单独兜一道：候选没读到只让这一行消失，不能把商品列表也拖成错误态
    categoryError.value = (err as Error).message
  }
}

/**
 * 下拉里的项：服务端已按「父在前、下级紧跟」铺平，这里只补一枚「└」表示它挂在前面那一级下面，
 * 头一格是「全部分类」（0 = 不筛，后端那个字段不传就是不参与，所以不能把 0 递过去）。
 */
const categoryColumns = computed(() => [
  { value: 0, label: '全部分类' },
  ...categories.value.map(one => ({
    value: one.id,
    label: (one.level ?? 1) > 1 ? `└ ${one.categoryName}` : one.categoryName,
  })),
])

/** 下拉选完（radio 档点一行即选中并收起）就回第一页整块重捞，续页再接着往下翻 */
watch(categoryId, () => loadPage(true))

async function loadPage(reset = false) {
  if (loading) {
    if (reset) {
      reloadWanted = true
    }
    return
  }
  loading = true
  const next = reset ? 1 : pageNum.value + 1
  try {
    const page = await getProductList({
      categoryId: categoryId.value || undefined,
      pageNum: next,
      pageSize: PAGE_SIZE,
    })
    list.value = next === 1 ? page.records : [...list.value, ...page.records]
    total.value = Number(page.total ?? 0)
    pageNum.value = next
  }
  catch {
    // 失败提示由 http 层统一 toast，这里只保证 onLoad 不抛未捕获异常
  }
  finally {
    loading = false
    if (reloadWanted) {
      reloadWanted = false
      loadPage(true)
    }
  }
}

/** 上面两块各自兜一道：门店接口没通不能把轮播和商品列表一起拖成红框 */
async function loadShelves() {
  try {
    banners.value = await getHomeActivities()
    reportBannerView()
  }
  catch { /* 已由 http 层提示 */ }
  try {
    stores.value = (await getStoreList({ pageSize: SHELF_SIZE })).records
  }
  catch { /* 同上 */ }
  try {
    brands.value = (await getBrandList({ pageSize: SHELF_SIZE })).records
  }
  catch { /* 同上 */ }
}

/** 当前这一张轮播进过页面就报一次曝光：报数发出去就不管回包；同一个 id 来回翻、下拉刷新都不再补报 */
const viewed = new Set<number>()
const bannerIdx = ref(0)

function reportBannerView() {
  const one = banners.value[bannerIdx.value]
  if (!one || viewed.has(one.id)) {
    return
  }
  viewed.add(one.id)
  trackActivity(one.id, ACTIVITY_EVENT_VIEW)
}

function onBannerChange(e: { detail: { current: number } }) {
  bannerIdx.value = e.detail.current
  reportBannerView()
}

/** 点轮播这一张 = 这条活动被人当真点了，先记一笔再进详情页（跳哪儿在详情页上才摆得清） */
function toActivity(id: number) {
  trackActivity(id, ACTIVITY_EVENT_CLICK)
  uni.navigateTo({ url: `/pages/activity/detail?id=${id}` })
}

/** 搜索框是入口不是输入框：点一下就跳搜索页，词在那里打（首页不再就地筛商品） */
function toSearch(tab?: string) {
  uni.navigateTo({ url: `/pages/search/search${tab ? `?tab=${tab}` : ''}` })
}

/** 门店 / 品牌自己的详情页 */
function toStore(id: number) {
  uni.navigateTo({ url: `/pages/store/detail?id=${id}` })
}

function toBrand(id: number) {
  uni.navigateTo({ url: `/pages/brand/detail?id=${id}` })
}

onLoad(() => {
  loadShelves()
  loadCategories()
  loadPage(true)
})

onPullDownRefresh(async () => {
  await Promise.all([loadShelves(), loadCategories()])
  await loadPage(true)
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  if (list.value.length < total.value) {
    loadPage()
  }
})
</script>

<template>
  <view class="bg-[#f5f6f7] pb-192rpx">
    <view class="home-head z-10">
      <!-- disabled 是为了让点击冒泡出来（wd-search 只在 disabled 时抛 click），这一格只是个入口 -->
      <wd-search
        disabled
        placeholder="搜索商品 / 门店 / 品牌 / 活动"
        hide-cancel
        @click="toSearch()"
      />
    </view>

    <!-- 轮播位：图由服务端挑好了给（首页位 + 投放中 + 有图），点一下进这条活动的详情页 -->
    <swiper
      v-if="banners.length"
      class="h-[320rpx] w-full"
      circular
      autoplay
      :interval="4000"
      indicator-dots
      indicator-color="rgba(255, 255, 255, 0.45)"
      indicator-active-color="#ffffff"
      @change="onBannerChange"
    >
      <swiper-item v-for="one in banners" :key="one.id" class="fx-press" hover-class="fx-tap" @click="toActivity(one.id)">
        <image :src="one.image" mode="aspectFill" class="h-full w-full" />
      </swiper-item>
    </swiper>

    <!-- 门店 + 品牌同一块：各只摆 4 个当地图用，点进去是各自的详情页，「全部」跳搜索页那一 tab -->
    <view v-if="stores.length || brands.length" class="mx-24rpx my-24rpx rounded-xl bg-white px-24rpx py-24rpx">
      <view class="text-[30rpx] text-[#333] font-medium">
        门店 · 品牌
      </view>

      <view v-if="stores.length" class="mt-24rpx">
        <view class="flex items-center justify-between">
          <view class="text-[24rpx] text-gray-400">
            门店
          </view>
          <view hover-class="fx-tap" class="fx-press text-[24rpx] text-gray-400" @click="toSearch('store')">
            全部 ›
          </view>
        </view>
        <scroll-view scroll-x class="mt-16rpx whitespace-nowrap">
          <view
            v-for="one in stores"
            :key="one.id"
            hover-class="fx-tap"
            class="fx-press mr-24rpx inline-block w-[150rpx] select-none text-center"
            @click="toStore(one.id)"
          >
            <image
              v-if="one.logo"
              :src="one.logo"
              mode="aspectFill"
              class="h-[96rpx] w-[96rpx] rounded-full bg-gray-50"
            />
            <view v-else class="h-[96rpx] w-[96rpx] rounded-full bg-gray-100" />
            <view
              class="mt-8rpx truncate text-[24rpx] text-[#666]"
            >
              {{ one.storeName }}
            </view>
          </view>
        </scroll-view>
      </view>

      <view v-if="brands.length" class="mt-24rpx">
        <view class="flex items-center justify-between">
          <view class="text-[24rpx] text-gray-400">
            品牌
          </view>
          <view hover-class="fx-tap" class="fx-press text-[24rpx] text-gray-400" @click="toSearch('brand')">
            全部 ›
          </view>
        </view>
        <scroll-view scroll-x class="mt-16rpx whitespace-nowrap">
          <view
            v-for="one in brands"
            :key="one.id"
            hover-class="fx-tap"
            class="fx-press mr-24rpx inline-block w-[150rpx] select-none text-center"
            @click="toBrand(one.id)"
          >
            <image
              v-if="one.logo"
              :src="one.logo"
              mode="aspectFill"
              class="h-[96rpx] w-[96rpx] rounded-lg bg-gray-50"
            />
            <view v-else class="h-[96rpx] w-[96rpx] rounded-lg bg-gray-100" />
            <view
              class="mt-8rpx truncate text-[24rpx] text-[#666]"
            >
              {{ one.brandName }}
            </view>
          </view>
        </scroll-view>
      </view>
    </view>

    <view class="mx-24rpx mt-32rpx text-[30rpx] text-[#333] font-medium">
      全部商品
    </view>

    <!-- 分类筛选：下拉 + 输入匹配（wd-select-picker 的 radio 档，本地过滤，点一行即选中并收起）；
         show-confirm 默认是「要按确认」，这里关掉，否则选一次要点两下 -->
    <view v-if="categories.length" class="mx-24rpx my-16rpx rounded-xl bg-white">
      <wd-select-picker
        v-model="categoryId"
        type="radio"
        filterable
        label="分类"
        placeholder="全部分类"
        :columns="categoryColumns"
        :show-confirm="false"
        filter-placeholder="输入分类名筛选"
      />
    </view>

    <view v-else-if="categoryError" class="mx-24rpx my-16rpx rounded-lg bg-red-50 px-32rpx py-16rpx text-[24rpx] text-[#d14328]">
      {{ categoryError }}
      <text hover-class="fx-tap" class="fx-press ml-16rpx underline" @click="loadCategories">重试</text>
    </view>

    <ProductGrid :products="list" />

    <view v-if="!list.length" class="py-160rpx text-center text-[26rpx] text-gray-400">
      {{ categoryId ? '这个分类下还没有商品' : '没有找到相关商品' }}
    </view>
    <view v-else-if="list.length >= total" class="py-32rpx text-center text-[24rpx] text-gray-300">
      没有更多了
    </view>
  </view>
</template>

<!--
  这一页的样式块刻意没写 scoped：下面第二条要伸手进 wd-search 这个自定义组件里面，
  小程序里 scoped 选择器跨组件边界不一定命中（它得靠页面 wxss + 组件自己的 styleIsolation: shared）。
  两条都以只有本页有的 .home-head 开头，H5 上也不会串到别处。
-->
<style lang="scss">
.home-head {
  position: sticky;
  /* H5 顶上那条 44px 标题栏是盖在内容之上的，粘到 top:0 会钻进它底下（实测滚起来后整条 y=0）；
     --window-top 就是那条的高度，小程序/App 上这个变量不存在 ⇒ 回退成 0 */
  top: var(--window-top, 0);
  /* 毛玻璃：和底部那条 tabbar 同一套做法（见 src/tabbar/index.vue）。老安卓 webview 不认 backdrop-filter，
     那一头就只剩 75% 的白底，不看糊也照样读得清 */
  background-color: rgba(255, 255, 255, 0.75);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  backdrop-filter: saturate(180%) blur(20px);
}

/* wd-search 那层底色是写死的 #fff，它没留 CSS 变量，不摘掉就把上面这层毛玻璃整个盖住了。
   里面那颗灰底输入条（.wd-search__block）留着，不然这一格就不像个搜索框了 */
.home-head .wd-search {
  background: transparent;
}
</style>
