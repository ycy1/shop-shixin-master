<script lang="ts" setup>
import type { IShopProductDetail, IShopProductSku } from '@/api/types/shop'
import { addToCart } from '@/api/cart'
import { COMMENT_TYPE_PRODUCT, getCommentTotal } from '@/api/comment'
import CommentSection from '@/components/CommentSection.vue'
import { getProductDetail } from '@/api/product'
import { useTokenStore } from '@/store/token'
import { parseImageUrls } from '@/utils'
import { cartCount, refreshCartBadge } from '@/utils/cartBadge'
import { flyTo } from '@/utils/flyToCart'
import { toLoginPage } from '@/utils/toLoginPage'

defineOptions({
  name: 'GoodsDetail',
})

definePage({
  style: {
    navigationBarTitleText: '商品详情',
  },
})

const tokenStore = useTokenStore()

const detail = ref<IShopProductDetail | null>(null)
const skus = ref<IShopProductSku[]>([])
const activeSku = ref<IShopProductSku | null>(null)
const quantity = ref(1)

/** 主图那一大块：选中规格有自己的封面就排最前，其余按商品配的轮播图；一条没配就回落 mainImage */
const shots = computed(() => {
  const urls = parseImageUrls(detail.value?.images ?? null)
  if (!urls.length && detail.value?.mainImage) {
    urls.push(detail.value.mainImage)
  }
  const skuImage = activeSku.value?.skuImage
  if (skuImage) {
    return [skuImage, ...urls.filter(url => url !== skuImage)]
  }
  return urls
})

/** 「商品详情」那块铺的是选中规格的图文详情，换一档就换一组图 */
const detailImages = computed(() => parseImageUrls(activeSku.value?.detailImages ?? null))

/**
 * 下半区两档：商品详情（描述 + 图文）/ 评论，口径同 shop-web 的商品详情页。
 * 上面的价格、规格、数量不进 tab —— 底部那两颗按钮要一直点得到，挑完规格也得马上看到价钱跟着变。
 */
const TABS = [
  { key: 'detail', label: '商品详情' },
  { key: 'comment', label: '评论' },
] as const
type TabKey = (typeof TABS)[number]['key']
const tab = ref<TabKey>('detail')
/** 评论那一档标签上印的条数：还没回来（或捞失败）留 null，那一格整格不出现，别闪一个（0）在那里 */
const commentCount = ref<number | null>(null)

function specTextOf(sku: IShopProductSku) {
  return sku.specText || '默认规格'
}

function selectSku(sku: IShopProductSku) {
  activeSku.value = sku
  quantity.value = 1
}

async function load(id: number) {
  try {
    const data = await getProductDetail(id)
    detail.value = data
    skus.value = data.skus ?? []
    activeSku.value = skus.value[0] ?? null
    uni.setNavigationBarTitle({ title: data.productName })
    // 商品拿到了才去数评论（带着错 id 不捞）；这一发不等它，别把首屏压在多一次往返之后
    loadCommentCount(id)
  }
  catch {
    // 下架、删掉的行服务端回同一句错误，http 层已经 toast，页面停在「加载中」
  }
}

/** tab 标签上那个数单独捞一次（一页一条、只读 total），捞不到就算了，评论那一档点开照样有自己的取数 */
async function loadCommentCount(id: number) {
  try {
    commentCount.value = await getCommentTotal(COMMENT_TYPE_PRODUCT, id)
  }
  catch {
    commentCount.value = null
  }
}

/** 加购、下单都要登录态（后端服务层第一句就 requireLoginUser），这里先拦一道，免得白报一次错 */
function ensureLogin() {
  if (tokenStore.updateNowTime().hasLogin) {
    return true
  }
  toLoginPage()
  return false
}

const instance = getCurrentInstance()

/**
 * 飞行动画：起点是底部「加入购物车」那一整块的中心，终点是那颗悬浮球的中心。
 * 两个点都现查节点，不按几何推 —— 球是 fixed 在视口上的，而 uni.getSystemInfoSync 给的窗口高
 * 不含 H5 那一条标题栏，按死数算会差出一截，实测落点比球高 36px。
 */
function flyToBall() {
  const query = instance ? uni.createSelectorQuery().in(instance) : uni.createSelectorQuery()
  query.select('#add-cart-entry').boundingClientRect()
  query.select('#cart-ball').boundingClientRect()
  query.exec((rects: any[]) => {
    const [from, to] = rects ?? []
    if (!from || !to) {
      return
    }
    flyTo(
      { x: from.left + from.width / 2, y: from.top + from.height / 2 },
      { x: to.left + to.width / 2, y: to.top + to.height / 2 },
    )
  })
}

async function handleAddCart() {
  if (!ensureLogin() || !activeSku.value) {
    return
  }
  try {
    await addToCart({ skuId: activeSku.value.id, quantity: quantity.value })
    flyToBall()
    uni.showToast({ title: '已加入购物车', icon: 'success' })
    // 角标跟着这一趟的改动重算（悬浮球和底栏那一格读的是同一个数）
    void refreshCartBadge()
  }
  catch {
    // 库存不足、商品下架等由服务端回话，http 层已 toast
  }
}

function toCart() {
  uni.switchTab({ url: '/pages/cart/cart' })
}

function handleBuy() {
  if (!ensureLogin() || !activeSku.value) {
    return
  }
  uni.navigateTo({
    url: `/pages/order/preview?skuId=${activeSku.value.id}&quantity=${quantity.value}`,
  })
}

onLoad((query) => {
  const id = Number(query?.id)
  if (!id) {
    uni.showToast({ title: '商品不存在', icon: 'none' })
    return
  }
  load(id)
})

// 悬浮球那个数是运行时攒出来的：直接进这一页（分享、搜索点进来）时车里明明有东西，得自己捞一趟
onShow(() => {
  void refreshCartBadge()
})
</script>

<template>
  <view v-if="detail" class="bg-white pb-192rpx">
    <swiper v-if="shots.length" class="aspect-square w-full" :indicator-dots="shots.length > 1">
      <swiper-item v-for="(url, index) in shots" :key="index">
        <image :src="url" mode="aspectFill" class="h-full w-full" />
      </swiper-item>
    </swiper>
    <view v-else class="aspect-square w-full bg-gray-100" />

    <view class="px-32rpx pt-32rpx">
      <view class="flex items-end">
        <text v-if="activeSku" class="text-[44rpx] text-[#d14328]">
          ¥{{ activeSku.productPrice }}
        </text>
        <text v-else-if="detail.priceMin != null" class="text-[44rpx] text-[#d14328]">
          ¥{{ detail.priceMin }}起
        </text>
      </view>
      <view class="mt-16rpx text-[32rpx] leading-[48rpx]">
        {{ detail.productName }}
      </view>
      <view v-if="detail.subTitle" class="mt-8rpx text-[26rpx] text-gray-500">
        {{ detail.subTitle }}
      </view>
      <view class="mt-16rpx flex flex-wrap gap-x-32rpx text-[24rpx] text-gray-400">
        <text v-if="activeSku">已售 {{ activeSku.salesCount ?? 0 }} 件</text>
        <text v-if="detail.brandName">品牌 {{ detail.brandName }}</text>
        <text v-if="detail.categoryName">分类 {{ detail.categoryName }}</text>
      </view>
    </view>

    <view v-if="skus.length" class="mt-32rpx border-t border-gray-100 px-32rpx py-32rpx">
      <view class="text-[28rpx] text-gray-600">
        规格
      </view>
      <view class="mt-24rpx flex flex-wrap gap-24rpx">
        <view
          v-for="sku in skus"
          :key="sku.id"
          hover-class="fx-tap"
          class="fx-press border rounded-full px-40rpx py-16rpx text-[26rpx]"
          :class="activeSku?.id === sku.id
            ? 'border-[#d14328] bg-[#fff4f2] text-[#d14328]'
            : 'border-gray-200 text-gray-600'"
          @click="selectSku(sku)"
        >
          {{ specTextOf(sku) }}
        </view>
      </view>
      <view class="mt-32rpx flex items-center justify-between">
        <text class="text-[26rpx] text-gray-500">
          可用库存 {{ activeSku?.availableStock ?? 0 }}
        </text>
        <view class="flex items-center">
          <text class="mr-24rpx text-[26rpx] text-gray-500">
            数量
          </text>
          <wd-input-number v-model="quantity" :min="1" :max="activeSku?.availableStock || 1" />
        </view>
      </view>
    </view>

    <!-- tab 条形状照门店详情页那两档（红下划线落在选中那一颗上）；这一页用 border-t 和上面的规格区隔开，
         免得和那颗自己的 border-b-2 叠成两条线 -->
    <view class="mt-32rpx flex items-center gap-48rpx border-t border-gray-100 px-32rpx">
      <view
        v-for="one in TABS"
        :key="one.key"
        hover-class="fx-tap"
        class="fx-press border-b-2 py-24rpx text-[28rpx] -mb-px"
        :class="tab === one.key ? 'border-[#d14328] text-[#d14328] font-medium' : 'border-transparent text-gray-500'"
        @click="tab = one.key"
      >
        {{ one.label }}<text v-if="one.key === 'comment' && commentCount !== null" class="ml-8rpx text-[24rpx] text-gray-400">（{{ commentCount }}）</text>
      </view>
    </view>

    <template v-if="tab === 'detail'">
      <view v-if="detail.productDesc" class="px-32rpx py-32rpx">
        <view class="text-[28rpx] text-gray-600">
          商品描述
        </view>
        <view class="mt-16rpx text-[26rpx] text-gray-700 leading-[40rpx]">
          {{ detail.productDesc }}
        </view>
      </view>

      <view v-if="detailImages.length" class="px-32rpx py-32rpx" :class="detail.productDesc ? 'border-t border-gray-100' : ''">
        <view class="text-[28rpx] text-gray-600">
          图文详情
        </view>
        <image
          v-for="(url, index) in detailImages"
          :key="index"
          :src="url"
          mode="widthFix"
          class="mt-16rpx w-full"
        />
      </view>

      <!-- 两节都空着才出这一句，别留一个只有 tab 没有内容的空格 -->
      <view v-if="!detail.productDesc && !detailImages.length" class="py-128rpx text-center text-[26rpx] text-gray-400">
        暂无商品信息
      </view>
    </template>

    <!-- 评论区公共组件：只吃「评的是哪一个」两格，取数/发表/点赞/删除都在它自己那一块里 -->
    <view v-else class="px-32rpx py-32rpx">
      <CommentSection preview :comment-type="COMMENT_TYPE_PRODUCT" :business-id="detail.id" />
    </view>

    <view class="fx-bar fixed bottom-0 left-0 right-0 z-10 border-t border-gray-100">
      <view class="flex items-center gap-24rpx px-32rpx py-24rpx">
        <!-- 包这一格是为了给飞行动画量个起点：wd-button 自己是组件，查它的节点不如查外面这格 -->
        <view id="add-cart-entry" class="flex-1">
          <wd-button plain type="primary" block @click="handleAddCart">
            加入购物车
          </wd-button>
        </view>
        <wd-button type="error" class="flex-1" :disabled="!skus.length" block @click="handleBuy">
          立即购买
        </wd-button>
      </view>
    </view>

    <!-- 悬浮购物车：这一页没有底栏，它是进车的入口，也是上面那颗加购的落点（角标和底栏那一格读同一个数） -->
    <view
      id="cart-ball"
      hover-class="fx-tap"
      class="cart-ball fx-press fixed bottom-260rpx right-24rpx z-20 h-96rpx w-96rpx center rounded-full bg-white"
      @click="toCart"
    >
      <view class="i-carbon-shopping-cart text-48rpx text-#d14328" />
      <view
        v-if="cartCount"
        class="absolute box-border h-32rpx min-w-32rpx center rounded-full bg-#f56c6c px-8rpx text-center text-2xs text-white -right-12rpx -top-6rpx"
      >
        {{ cartCount > 99 ? '99+' : cartCount }}
      </view>
    </view>

    <!-- 栏子（.fx-bar）比原来高出安全区那一截，页面尾巴也跟着垫同样一截，不然最后一块内容压在栏底下 -->
    <view class="pb-safe" />
  </view>

  <view v-else class="py-240rpx text-center text-[26rpx] text-gray-400">
    加载中…
  </view>
</template>

<style lang="scss" scoped>
/* 悬浮球要浮在图上，只靠圆角不够，得有一圈投影 */
.cart-ball {
  box-shadow: 0 6rpx 24rpx rgb(0 0 0 / 16%);
}
</style>
