<script lang="ts" setup>
import type { IShopProduct, IShopStore } from '@/api/types/shop'
import CommentSection from '@/components/CommentSection.vue'
import ProductGrid from '@/components/ProductGrid.vue'
import { COMMENT_TYPE_STORE, getCommentTotal } from '@/api/comment'
import { getProductList } from '@/api/product'
import { getStoreDetail } from '@/api/search'
import { parseImageUrls } from '@/utils'

defineOptions({
  name: 'StoreDetail',
})

definePage({
  style: {
    navigationBarTitleText: '门店',
    enablePullDownRefresh: true,
    onReachBottomDistance: 100,
  },
})

const PAGE_SIZE = 10

/** 门店分类、营业状态两档中文：和 shop-web 的 d.enum.storeType / d.enum.businessStatus 同一档，App 这边先写死在页面上 */
const STORE_TYPE_TEXT: Record<number, string> = { 1: '直营', 2: '加盟', 3: '合作' }
const BUSINESS_STATUS_TEXT: Record<number, string> = { 1: '营业中', 0: '休息中', 2: '已打烊' }

/** 商品区两档：门店商品（cultural_enable=0）/ 文创商品（=1），判据和 shop-web 门店页一致，切档重拉第一页 */
const PRODUCT_TABS = [
  { key: 'store', label: '门店商品', culturalEnable: 0 },
  { key: 'cultural', label: '文创商品', culturalEnable: 1 },
] as const
type ProductTabKey = (typeof PRODUCT_TABS)[number]['key']

const storeId = ref(0)
const store = ref<IShopStore | null>(null)
const loadError = ref('')

const tab = ref<ProductTabKey>('store')
/** 底下那一排第三档「门店评价」：评论不是一种 cultural_enable，所以单开一个开关，切过去也不清商品的列表 */
const showComment = ref(false)
/** 「门店评价」标签上印的条数：还没回来（或捞失败）留 null，那一格整格不出现，别闪一个（0）在那里 */
const commentCount = ref<number | null>(null)
const list = ref<IShopProduct[]>([])
const total = ref(0)
const pageNum = ref(0)
const productsError = ref('')
let loading = false

const shots = computed(() => parseImageUrls(store.value?.coverImages ?? null))

const storeTypeText = computed(() => {
  const type = store.value?.storeType
  return type == null ? '' : STORE_TYPE_TEXT[type] ?? ''
})
const businessStatusText = computed(() => {
  const status = store.value?.businessStatus
  return status == null ? '' : BUSINESS_STATUS_TEXT[status] ?? ''
})

/** 地址/电话/营业时间：空的整行不出现，别留一行只有标签没有值（同 shop-web 门店页那条） */
const metaRows = computed(() => {
  const one = store.value
  if (!one) {
    return []
  }
  return [
    { key: 'address', label: '地址', value: [one.areaZh, one.address].filter(Boolean).join(' ') },
    { key: 'phone', label: '电话', value: one.contactPhone ?? '' },
    { key: 'hours', label: '营业时间', value: one.businessHours ?? '' },
  ].filter(row => row.value)
})

const emptyText = computed(() =>
  tab.value === 'cultural' ? '这家店还没有文创商品' : '这家店还没有上架的商品',
)

async function loadStore() {
  try {
    store.value = await getStoreDetail(storeId.value)
    uni.setNavigationBarTitle({ title: store.value.storeName })
    // 门店拿到了才去数评论（带着错 id 不捞）；这一发不等它，别把首屏压在多一次往返之后
    loadCommentCount()
  }
  catch {
    // 停用/已删/没这个 id 后端回同一句话，http 层已经 toast，这里只把那一块换成提示
    loadError.value = '门店不存在或已停用'
  }
}

/** 「门店评价」标签上那个数单独捞一次（一页一条、只读 total），捞不到就算了，那一档点开照样有自己的取数 */
async function loadCommentCount() {
  try {
    commentCount.value = await getCommentTotal(COMMENT_TYPE_STORE, storeId.value)
  }
  catch {
    commentCount.value = null
  }
}

async function loadPage(reset = false) {
  if (loading || !storeId.value) {
    return
  }
  loading = true
  const next = reset ? 1 : pageNum.value + 1
  try {
    const page = await getProductList({
      storeId: storeId.value,
      culturalEnable: tab.value === 'cultural' ? 1 : 0,
      pageNum: next,
      pageSize: PAGE_SIZE,
    })
    list.value = next === 1 ? page.records : [...list.value, ...page.records]
    total.value = Number(page.total ?? 0)
    pageNum.value = next
    productsError.value = ''
  }
  catch {
    // 商品那一块单独一条错：接口没通不能把已经拿到的门店头部整页撤走
    productsError.value = '本店商品加载失败'
  }
  finally {
    loading = false
  }
}

function pickTab(key: ProductTabKey) {
  showComment.value = false
  if (tab.value === key) {
    return
  }
  tab.value = key
  list.value = []
  total.value = 0
  pageNum.value = 0
  loadPage(true)
}

/** 评论那一档只换下面那块：商品的翻页进度留着，切回来接着走 */
function pickComment() {
  showComment.value = true
}

/** 门店图那一圈：点开是系统图片预览器，左右能翻 */
function previewShots() {
  if (shots.value.length) {
    uni.previewImage({ urls: shots.value })
  }
}

onLoad((query) => {
  storeId.value = Number(query?.id) || 0
  if (!storeId.value) {
    loadError.value = '参数不对，没拿到门店 id'
    return
  }
  loadStore()
  loadPage(true)
})

onPullDownRefresh(async () => {
  await Promise.all([loadStore(), loadPage(true)])
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  if (!showComment.value && list.value.length < total.value) {
    loadPage()
  }
})
</script>

<template>
  <view class="bg-[#f5f6f7] pb-192rpx pt-24rpx">
    <view v-if="loadError" class="py-160rpx text-center text-[26rpx] text-gray-400">
      {{ loadError }}
    </view>

    <template v-else-if="store">
      <view class="mx-24rpx mb-24rpx rounded-xl bg-white px-32rpx py-32rpx">
        <view class="flex items-start">
          <image
            v-if="store.logo"
            :src="store.logo"
            mode="aspectFill"
            class="h-[120rpx] w-[120rpx] shrink-0 rounded-lg bg-gray-50"
          />
          <view
            v-else
            class="h-[120rpx] w-[120rpx] shrink-0 rounded-lg bg-gray-100 text-center text-[48rpx] text-gray-400 leading-[120rpx]"
          >
            {{ store.storeName.charAt(0) }}
          </view>

          <view class="ml-24rpx min-w-0 flex-1">
            <view class="text-[34rpx] text-[#333] font-medium">
              {{ store.storeName }}
            </view>
            <view class="mt-16rpx flex flex-wrap items-center gap-16rpx">
              <text v-if="storeTypeText" class="rounded bg-blue-50 px-16rpx py-4rpx text-[22rpx] text-blue-700">
                {{ storeTypeText }}
              </text>
              <text
                v-if="businessStatusText"
                class="rounded px-16rpx py-4rpx text-[22rpx]"
                :class="store.businessStatus === 1 ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-500'"
              >
                {{ businessStatusText }}
              </text>
              <text v-if="store.isDelivery === 1" class="rounded bg-gray-100 px-16rpx py-4rpx text-[22rpx] text-gray-600">
                支持配送
              </text>
            </view>
          </view>
        </view>

        <view v-if="metaRows.length" class="mt-24rpx">
          <view v-for="row in metaRows" :key="row.key" class="mt-8rpx text-[26rpx] text-[#666]">
            <text class="text-gray-400">{{ row.label }}</text>{{ row.value }}
          </view>
        </view>
      </view>

      <!-- 招牌图这一横排套一层壳才敢加边距：swiper 自己带 width:100%，在它身上直接写 mx-24rpx 会撑出屏幕外 -->
      <view v-if="shots.length" class="mx-24rpx mb-24rpx h-[360rpx] overflow-hidden rounded-xl">
        <swiper
          class="h-full w-full"
          circular
          autoplay
          :interval="4000"
          indicator-dots
          indicator-color="rgba(255, 255, 255, 0.45)"
          indicator-active-color="#ffffff"
        >
          <swiper-item v-for="url in shots" :key="url" class="fx-press" hover-class="fx-tap" @click="previewShots">
            <image :src="url" mode="aspectFill" class="h-full w-full" />
          </swiper-item>
        </swiper>
      </view>
    </template>

    <view v-if="store" class="mt-24rpx flex items-center gap-48rpx border-b border-gray-100 bg-white px-32rpx">
      <view
        v-for="one in PRODUCT_TABS"
        :key="one.key"
        hover-class="fx-tap"
        class="fx-press border-b-2 py-24rpx text-[28rpx] -mb-px"
        :class="!showComment && tab === one.key ? 'border-[#d14328] text-[#d14328] font-medium' : 'border-transparent text-gray-500'"
        @click="pickTab(one.key)"
      >
        {{ one.label }}
      </view>
      <view
        hover-class="fx-tap"
        class="fx-press border-b-2 py-24rpx text-[28rpx] -mb-px"
        :class="showComment ? 'border-[#d14328] text-[#d14328] font-medium' : 'border-transparent text-gray-500'"
        @click="pickComment"
      >
        门店评价<text v-if="commentCount !== null" class="ml-8rpx text-[24rpx] text-gray-400">（{{ commentCount }}）</text>
      </view>
    </view>

    <view v-if="!showComment && productsError" class="mx-24rpx mb-24rpx rounded-lg bg-red-50 px-32rpx py-24rpx text-[26rpx] text-[#d14328]">
      {{ productsError }}
    </view>

    <ProductGrid v-else-if="store && !showComment" :products="list" />

    <view v-if="store && !showComment && !list.length && !productsError" class="py-128rpx text-center text-[26rpx] text-gray-400">
      {{ emptyText }}
    </view>
    <view v-else-if="store && !showComment && list.length && list.length >= total" class="py-32rpx text-center text-[24rpx] text-gray-300">
      没有更多了
    </view>

    <!-- 评论区：门店不存在/已停用那一趟整块都红了，这一页不会带着错 id 去捞评论 -->
    <view v-if="store && showComment" class="mx-24rpx mb-24rpx mt-24rpx rounded-xl bg-white px-32rpx py-24rpx">
      <CommentSection preview :comment-type="COMMENT_TYPE_STORE" :business-id="storeId" />
    </view>
  </view>
</template>
