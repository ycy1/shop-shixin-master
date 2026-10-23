<script lang="ts" setup>
import type { IShopBrand, IShopProduct, IShopStore } from '@/api/types/shop'
import CommentSection from '@/components/CommentSection.vue'
import ProductGrid from '@/components/ProductGrid.vue'
import { COMMENT_TYPE_BRAND, getCommentTotal } from '@/api/comment'
import { getProductList } from '@/api/product'
import { getBrandDetail, getBrandStores } from '@/api/search'

defineOptions({
  name: 'BrandDetail',
})

definePage({
  style: {
    navigationBarTitleText: '品牌',
    enablePullDownRefresh: true,
    onReachBottomDistance: 100,
  },
})

const PAGE_SIZE = 10

const brandId = ref(0)
const brand = ref<IShopBrand | null>(null)
const loadError = ref('')

const stores = ref<IShopStore[]>([])
const list = ref<IShopProduct[]>([])
const total = ref(0)
const pageNum = ref(0)
/** 门店和商品分开两条错：门店那一条两档都要看见，商品那一条只在「品牌商品」档露 */
const storesError = ref('')
const productsError = ref('')
let loading = false

/** 底下那一排两档：品牌商品 / 品牌评价。评论不是一种查询条件，所以单开一个开关，切过去也不清商品列表 */
const showComment = ref(false)
/** 「品牌评价」标签上印的条数：还没回来（或捞失败）留 null，那一格整格不出现，别闪一个（0）在那里 */
const commentCount = ref<number | null>(null)

/** 代言人那一圈图：json 列里每项带 name，预览时把名字当标题递过去 */
const spokespersons = computed(() => {
  const raw = brand.value?.brandSpokesperson
  if (!raw) {
    return []
  }
  try {
    const arr: unknown = JSON.parse(raw)
    if (!Array.isArray(arr)) {
      return []
    }
    return arr
      .map(item => ({ url: (item as { url?: unknown })?.url, name: (item as { name?: unknown })?.name }))
      .filter((one): one is { url: string, name: string } => typeof one.url === 'string' && !!one.url)
  }
  catch {
    return []
  }
})

const shots = computed(() => spokespersons.value.map(one => one.url))

async function loadBrand() {
  try {
    brand.value = await getBrandDetail(brandId.value)
    uni.setNavigationBarTitle({ title: brand.value.brandName })
    // 品牌拿到了才去数评论（带着错 id 不捞）；这一发不等它，别把首屏压在多一次往返之后
    loadCommentCount()
  }
  catch {
    // 停用/已删/没这个 id 后端回同一句话，http 层已经 toast
    loadError.value = '品牌不存在或已停用'
  }
}

/** 「品牌评价」标签上那个数单独捞一次（一页一条、只读 total），捞不到就算了，那一档点开照样有自己的取数 */
async function loadCommentCount() {
  try {
    commentCount.value = await getCommentTotal(COMMENT_TYPE_BRAND, brandId.value)
  }
  catch {
    commentCount.value = null
  }
}

/** 名下门店和商品都只吃 brandId，不等品牌头部：谁先回来谁先画 */
async function loadStores() {
  try {
    stores.value = await getBrandStores(brandId.value)
  }
  catch {
    storesError.value = '品牌门店加载失败'
  }
}

async function loadPage(reset = false) {
  if (loading || !brandId.value) {
    return
  }
  loading = true
  const next = reset ? 1 : pageNum.value + 1
  try {
    const page = await getProductList({
      brandId: brandId.value,
      pageNum: next,
      pageSize: PAGE_SIZE,
    })
    list.value = next === 1 ? page.records : [...list.value, ...page.records]
    total.value = Number(page.total ?? 0)
    pageNum.value = next
    productsError.value = ''
  }
  catch {
    productsError.value = '品牌商品加载失败'
  }
  finally {
    loading = false
  }
}

function previewShots(index: number) {
  uni.previewImage({ urls: shots.value, current: index })
}

/** 两档只换下面那块：商品那一档的翻页进度留着，切回来接着走 */
function pickProducts() {
  showComment.value = false
}

function pickComment() {
  showComment.value = true
}

function toStore(id: number) {
  uni.navigateTo({ url: `/pages/store/detail?id=${id}` })
}

onLoad((query) => {
  brandId.value = Number(query?.id) || 0
  if (!brandId.value) {
    loadError.value = '参数不对，没拿到品牌 id'
    return
  }
  loadBrand()
  loadStores()
  loadPage(true)
})

onPullDownRefresh(async () => {
  await Promise.all([loadBrand(), loadStores(), loadPage(true)])
  uni.stopPullDownRefresh()
})

/** 停在「品牌评价」那一档时上滑不能接着翻商品页 */
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

    <template v-else-if="brand">
      <view class="mx-24rpx mb-24rpx rounded-xl bg-white px-32rpx py-32rpx">
        <view class="flex items-start">
          <image
            v-if="brand.logo"
            :src="brand.logo"
            mode="aspectFill"
            class="h-[120rpx] w-[120rpx] shrink-0 rounded-lg bg-gray-50"
          />
          <view
            v-else
            class="h-[120rpx] w-[120rpx] shrink-0 rounded-lg bg-gray-100 text-center text-[48rpx] text-gray-400 leading-[120rpx]"
          >
            {{ brand.brandName.charAt(0) }}
          </view>
          <view class="ml-24rpx min-w-0 flex-1">
            <view class="text-[34rpx] text-[#333] font-medium">
              {{ brand.brandName }}
            </view>
            <view v-if="brand.description" class="mt-16rpx text-[26rpx] text-[#666] leading-[36rpx]">
              {{ brand.description }}
            </view>
          </view>
        </view>
      </view>

      <view v-if="spokespersons.length" class="mx-24rpx mb-24rpx rounded-xl bg-white px-32rpx py-24rpx">
        <view class="text-[28rpx] text-[#333] font-medium">
          品牌代言
        </view>
        <scroll-view scroll-x class="mt-16rpx whitespace-nowrap">
          <view
            v-for="(one, index) in spokespersons"
            :key="one.url"
            hover-class="fx-tap"
            class="fx-press mr-24rpx inline-block w-[160rpx] select-none text-center"
            @click="previewShots(index)"
          >
            <image :src="one.url" mode="aspectFill" class="h-[200rpx] w-[160rpx] rounded-lg bg-gray-50" />
            <view class="mt-8rpx truncate text-[24rpx] text-[#666]">
              {{ one.name }}
            </view>
          </view>
        </scroll-view>
      </view>

      <view v-if="stores.length" class="mx-24rpx mb-24rpx rounded-xl bg-white px-32rpx py-24rpx">
        <view class="text-[28rpx] text-[#333] font-medium">
          品牌门店
        </view>
        <view
          v-for="one in stores"
          :key="one.id"
          hover-class="fx-tap"
          class="fx-press mt-24rpx flex items-center"
          @click="toStore(one.id)"
        >
          <image
            v-if="one.logo"
            :src="one.logo"
            mode="aspectFill"
            class="h-[88rpx] w-[88rpx] shrink-0 rounded-lg bg-gray-50"
          />
          <view
            v-else
            class="h-[88rpx] w-[88rpx] shrink-0 rounded-lg bg-gray-100 text-center text-[36rpx] text-gray-400 leading-[88rpx]"
          >
            {{ one.storeName.charAt(0) }}
          </view>
          <view class="ml-24rpx min-w-0 flex-1">
            <view class="truncate text-[28rpx] text-[#333]">
              {{ one.storeName }}
            </view>
            <view class="mt-8rpx truncate text-[24rpx] text-gray-400">
              {{ [one.areaZh, one.address].filter(Boolean).join(' ') || '没填地址' }}
            </view>
          </view>
          <text class="text-[26rpx] text-gray-300">›</text>
        </view>
      </view>
    </template>

    <view v-if="brand" class="mt-24rpx flex items-center gap-48rpx border-b border-gray-100 bg-white px-32rpx">
      <view
        hover-class="fx-tap"
        class="fx-press border-b-2 py-24rpx text-[28rpx] -mb-px"
        :class="!showComment ? 'border-[#d14328] text-[#d14328] font-medium' : 'border-transparent text-gray-500'"
        @click="pickProducts"
      >
        品牌商品
      </view>
      <view
        hover-class="fx-tap"
        class="fx-press border-b-2 py-24rpx text-[28rpx] -mb-px"
        :class="showComment ? 'border-[#d14328] text-[#d14328] font-medium' : 'border-transparent text-gray-500'"
        @click="pickComment"
      >
        品牌评价<text v-if="commentCount !== null" class="ml-8rpx text-[24rpx] text-gray-400">（{{ commentCount }}）</text>
      </view>
    </view>

    <view v-if="storesError" class="mx-24rpx mb-24rpx rounded-lg bg-red-50 px-32rpx py-24rpx text-[26rpx] text-[#d14328]">
      {{ storesError }}
    </view>
    <view v-if="!showComment && productsError" class="mx-24rpx mb-24rpx rounded-lg bg-red-50 px-32rpx py-24rpx text-[26rpx] text-[#d14328]">
      {{ productsError }}
    </view>

    <ProductGrid v-if="brand && !showComment" :products="list" />

    <view v-if="brand && !showComment && !list.length" class="py-128rpx text-center text-[26rpx] text-gray-400">
      这个品牌还没有上架的商品
    </view>
    <view v-else-if="brand && !showComment && list.length && list.length >= total" class="py-32rpx text-center text-[24rpx] text-gray-300">
      没有更多了
    </view>

    <!-- 评论区：品牌不存在/已停用那一趟整块都红了，这一页不会带着错 id 去捞评论 -->
    <view v-if="brand && showComment" class="mx-24rpx mb-24rpx mt-24rpx rounded-xl bg-white px-32rpx py-24rpx">
      <CommentSection preview :comment-type="COMMENT_TYPE_BRAND" :business-id="brandId" />
    </view>
  </view>
</template>
