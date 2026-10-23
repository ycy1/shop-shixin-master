<script lang="ts" setup>
import type { IShopActivityRow, IShopBrand, IShopPlazaMessage, IShopProduct, IShopStore } from '@/api/types/shop'
import { getActivityPage } from '@/api/activity'
import { getBrandList, getStoreList } from '@/api/home'
import { getPlazaPage, plazaLabel, plazaLeadImage } from '@/api/plaza'
import { getProductList } from '@/api/product'
import ProductGrid from '@/components/ProductGrid.vue'
import { activityLabel, activityTimeText } from '@/utils/activity'

defineOptions({
  name: 'SearchPage',
})

definePage({
  style: {
    navigationBarTitleText: '搜索',
    enablePullDownRefresh: true,
    onReachBottomDistance: 100,
  },
})

/** 五块各自的行类型不一样，但状态形状一样：捞到第几页、一共几页几个，各记各的账 */
type TabKey = 'product' | 'store' | 'brand' | 'activity' | 'plaza'

interface Block<T> {
  rows: T[]
  page: number
  pages: number
  total: number
  loading: boolean
  error: string
}

type AnyBlock = Block<unknown>

const PAGE_SIZE = 10
/** 空白串不算关键字：后端那几块筛选用的是 isNotBlank，前端按同一口径决定空结果怎么说 */
const keyword = ref('')
const categoryId = ref(0)

const products = reactive<Block<IShopProduct>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' })
const stores = reactive<Block<IShopStore>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' })
const brands = reactive<Block<IShopBrand>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' })
const activities = reactive<Block<IShopActivityRow>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' })
const plazas = reactive<Block<IShopPlazaMessage>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' })

const kw = () => keyword.value.trim() || undefined

interface TabItem {
  key: TabKey
  label: string
  /** 「没有找到相关门店」那句话里的名词 */
  noun: string
  block: AnyBlock
  fetch: (page: number, size: number) => Promise<{ records: unknown[], total: number, pages: number }>
}

const TABS: TabItem[] = [
  {
    key: 'product',
    label: '商品',
    noun: '商品',
    block: products,
    fetch: (page, size) => getProductList({
      keyword: kw(),
      categoryId: categoryId.value || undefined,
      pageNum: page,
      pageSize: size,
    }),
  },
  { key: 'store', label: '门店', noun: '门店', block: stores, fetch: (page, size) => getStoreList({ keyword: kw(), pageNum: page, pageSize: size }) },
  { key: 'brand', label: '品牌', noun: '品牌', block: brands, fetch: (page, size) => getBrandList({ keyword: kw(), pageNum: page, pageSize: size }) },
  // 读的是活动列表那一条接口，只是多带一个关键字（后端按名称或标题命中都行），判据没有第二套
  { key: 'activity', label: '活动', noun: '活动', block: activities, fetch: (page, size) => getActivityPage({ keyword: kw(), pageNum: page, pageSize: size }) },
  // 读的也是广场那一页的列表接口：keyword 命中标题/正文摘要/发布者昵称，可见性还是服务层钉的已发布 + 审核通过。
  // 唯一多带的一格是 orderByLike —— 搜索这一头按赞最多的排最前，广场本身的流不动
  { key: 'plaza', label: '广场', noun: '广场', block: plazas, fetch: (page, size) => getPlazaPage(page, size, { keyword: kw(), orderByLike: 1 }) },
]

const active = ref<TabKey>('product')
const current = computed(() => TABS.find(item => item.key === active.value)!)

/**
 * 五个 tab 上的命中数一进来就都摆出来（不等到切过去才知道那块空不空）。
 * 后端没有现成的 count 口，所以按 pageSize=1 各发一条、只读信封里那个 total —— 比另开五条接口便宜。
 * null = 还没探到（那一格数字不出现，不写 0，0 会被当成「真的一条都没有」）。
 */
const counts = reactive<Record<TabKey, number | null>>({ product: null, store: null, brand: null, activity: null, plaza: null })

const productRows = computed(() => products.rows)
const storeRows = computed(() => stores.rows)
const brandRows = computed(() => brands.rows)
const activityRows = computed(() => activities.rows)
const plazaRows = computed(() => plazas.rows)

const BUSINESS_STATUS_TEXT: Record<number, string> = { 1: '营业中', 0: '休息中', 2: '已打烊' }

/** 字典列都可能回 null（老数据没填），取不到说法就当那一格没有 */
function businessStatusText(status: number | null) {
  return status == null ? '' : BUSINESS_STATUS_TEXT[status] ?? ''
}

/** 发布者那一格：账号注销了联不出昵称，只剩 id（口径同广场那一页的卡片） */
function plazaAuthor(one: IShopPlazaMessage) {
  const name = one.userName?.trim()
  if (name) {
    return name
  }
  return one.userId ? `已注销的用户 #${one.userId}` : '—'
}

/** 时间只到日（这一页只做导流，秒在这里没意义） */
function plazaDate(one: IShopPlazaMessage) {
  return (one.publishTime || one.createTime || '').slice(0, 10)
}

async function load(item: TabItem) {
  const block = item.block
  block.loading = true
  block.error = ''
  try {
    const data = await item.fetch(item.block.page + 1, PAGE_SIZE)
    // 两页之间有人上下架，同一条可能被捞两次 ⇒ 按 id 去重再拼
    const seen = new Set(block.rows.map(row => (row as { id: number }).id))
    block.rows.push(...data.records.filter(row => !seen.has((row as { id: number }).id)))
    block.page += 1
    block.pages = data.pages ?? 0
    block.total = data.total ?? 0
    // 这一块捞过了，tab 上那个数就以它自己的 total 为准（那条 pageSize=1 的探数可能比它早回、也可能压根没发）
    counts[item.key] = block.total
  }
  catch {
    block.error = '加载失败'
  }
  finally {
    block.loading = false
  }
}

/** 只探还没捞过的那几块：已经捞过的块上面那条已经把真数写进去了，再探一遍就是多一趟后端 */
function readCount(item: TabItem) {
  return item
    .fetch(1, 1)
    .then((data) => {
      if (item.block.page === 0) {
        counts[item.key] = data.total ?? 0
      }
    })
    .catch(() => {
      // 数没探着不拦页面：那一格本来就空着，等他真切过去那一块自己会报错
    })
}

/** 五块一起回到第一页：换了词（或带了分类）就是另一次搜索，旧那几页不作数 */
function reloadAll() {
  TABS.forEach((item) => {
    item.block.rows = []
    item.block.page = 0
    item.block.pages = 0
    item.block.total = 0
    item.block.error = ''
    counts[item.key] = null
  })
  load(current.value)
  TABS.filter(item => item.key !== active.value).forEach(readCount)
}

function pickTab(key: TabKey) {
  if (active.value === key) {
    return
  }
  active.value = key
  const item = TABS.find(tab => tab.key === key)!
  // page === 0 才是「这一块还没捞过」；空结果也算捞过，否则每次切回来都重发一遍
  if (item.block.page === 0 && !item.block.loading) {
    load(item)
  }
}

function toDetail(key: TabKey, id: number) {
  const url = {
    product: `/pages/goods/detail?id=${id}`,
    store: `/pages/store/detail?id=${id}`,
    brand: `/pages/brand/detail?id=${id}`,
    activity: `/pages/activity/detail?id=${id}`,
    plaza: `/pages/plaza/detail?id=${id}`,
  }[key]
  uni.navigateTo({ url })
}

onLoad((query) => {
  // uni 把 onLoad 的 query 解好码了，这里再 decodeURIComponent 等于把「100%」这种词解崩
  keyword.value = query?.keyword ?? ''
  categoryId.value = Number(query?.categoryId) || 0
  if (query?.tab && TABS.some(item => item.key === query.tab)) {
    active.value = query.tab as TabKey
  }
  reloadAll()
})

onPullDownRefresh(async () => {
  reloadAll()
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  const block = current.value.block
  if (!block.loading && block.rows.length && block.rows.length < block.total) {
    load(current.value)
  }
})
</script>

<template>
  <view class="bg-[#f5f6f7] pb-192rpx">
    <view class="sticky top-0 z-10 bg-white">
      <wd-search
        v-model="keyword"
        placeholder="搜索商品 / 门店 / 品牌 / 活动 / 广场"
        hide-cancel
        @search="reloadAll"
        @cancel="reloadAll"
        @clear="reloadAll"
      />
    </view>

    <view class="flex items-center gap-48rpx border-b border-gray-100 bg-white px-32rpx">
      <view
        v-for="item in TABS"
        :key="item.key"
        hover-class="fx-tap"
        class="fx-press border-b-2 py-24rpx text-[28rpx] -mb-px"
        :class="active === item.key ? 'border-[#d14328] text-[#d14328] font-medium' : 'border-transparent text-gray-500'"
        @click="pickTab(item.key)"
      >
        {{ item.label }}
        <text v-if="counts[item.key] != null" class="ml-8rpx text-[22rpx] text-gray-400">
          {{ counts[item.key] }}
        </text>
      </view>
    </view>

    <view v-if="categoryId && active === 'product'" class="bg-white px-32rpx py-16rpx text-[24rpx] text-gray-400">
      已按活动所选的分类筛过
    </view>

    <view v-if="current.block.loading && !current.block.rows.length" class="py-128rpx text-center text-[26rpx] text-gray-400">
      加载中…
    </view>

    <view v-else-if="current.block.error && !current.block.rows.length" class="mx-32rpx mt-32rpx rounded-lg bg-red-50 px-32rpx py-24rpx text-[26rpx] text-[#d14328]">
      {{ current.block.error }}
      <text hover-class="fx-tap" class="fx-press ml-16rpx underline" @click="load(current)">重试</text>
    </view>

    <view v-else-if="!current.block.rows.length" class="py-128rpx text-center text-[26rpx] text-gray-400">
      {{ kw() ? `没有找到相关${current.noun}` : `还没有${current.noun}` }}
    </view>

    <!-- 商品：和首页同一份两列卡片 -->
    <ProductGrid v-else-if="active === 'product'" :products="productRows" />

    <!-- 门店 -->
    <view v-else-if="active === 'store'" class="px-24rpx pt-24rpx">
      <view
        v-for="row in storeRows"
        :key="row.id"
        hover-class="fx-tap"
        class="fx-press mb-24rpx flex rounded-xl bg-white p-24rpx"
        @click="toDetail('store', row.id)"
      >
        <image
          v-if="row.logo"
          :src="row.logo"
          mode="aspectFill"
          class="h-[112rpx] w-[112rpx] shrink-0 rounded-lg bg-gray-50"
        />
        <view
          v-else
          class="h-[112rpx] w-[112rpx] shrink-0 rounded-lg bg-gray-100 text-center text-[40rpx] text-gray-400 leading-[112rpx]"
        >
          {{ row.storeName.charAt(0) }}
        </view>
        <view class="ml-24rpx min-w-0 flex-1">
          <view class="flex items-start justify-between">
            <text class="truncate text-[30rpx] text-[#333] font-medium">
              {{ row.storeName }}
            </text>
            <text
              v-if="businessStatusText(row.businessStatus)"
              class="ml-16rpx shrink-0 rounded px-16rpx py-4rpx text-[22rpx]"
              :class="row.businessStatus === 1 ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-500'"
            >
              {{ businessStatusText(row.businessStatus) }}
            </text>
          </view>
          <view class="mt-8rpx text-[24rpx] text-gray-400">
            {{ [row.areaZh, row.address].filter(Boolean).join(' ') || '没有填地址' }}
          </view>
          <view class="mt-8rpx text-[24rpx] text-gray-400">
            {{ [row.contactPhone, row.businessHours].filter(Boolean).join('  ') }}
          </view>
        </view>
      </view>
    </view>

    <!-- 品牌 -->
    <view v-else-if="active === 'brand'" class="px-24rpx pt-24rpx">
      <view
        v-for="row in brandRows"
        :key="row.id"
        hover-class="fx-tap"
        class="fx-press mb-24rpx flex rounded-xl bg-white p-24rpx"
        @click="toDetail('brand', row.id)"
      >
        <image
          v-if="row.logo"
          :src="row.logo"
          mode="aspectFill"
          class="h-[112rpx] w-[112rpx] shrink-0 rounded-lg bg-gray-50"
        />
        <view
          v-else
          class="h-[112rpx] w-[112rpx] shrink-0 rounded-lg bg-gray-100 text-center text-[40rpx] text-gray-400 leading-[112rpx]"
        >
          {{ row.brandName.charAt(0) }}
        </view>
        <view class="ml-24rpx min-w-0 flex-1">
          <view class="truncate text-[30rpx] text-[#333] font-medium">
            {{ row.brandName }}
          </view>
          <view class="mt-8rpx text-[24rpx] text-gray-400">
            {{ row.description || '这个品牌还没有简介' }}
          </view>
        </view>
      </view>
    </view>

    <!-- 活动：形状照门店/品牌那两块，点进去是活动详情页，不是这条活动自己的跳转目标 -->
    <view v-else-if="active === 'activity'" class="px-24rpx pt-24rpx">
      <view
        v-for="row in activityRows"
        :key="row.id"
        hover-class="fx-tap"
        class="fx-press mb-24rpx flex rounded-xl bg-white p-24rpx"
        @click="toDetail('activity', row.id)"
      >
        <image
          v-if="row.image"
          :src="row.image"
          mode="aspectFill"
          class="h-[112rpx] w-[112rpx] shrink-0 rounded-lg bg-gray-50"
        />
        <view
          v-else
          class="h-[112rpx] w-[112rpx] shrink-0 rounded-lg bg-gray-100 text-center text-[40rpx] text-gray-400 leading-[112rpx]"
        >
          {{ activityLabel(row).charAt(0) }}
        </view>
        <view class="ml-24rpx min-w-0 flex-1">
          <view class="truncate text-[30rpx] text-[#333] font-medium">
            {{ activityLabel(row) }}
          </view>
          <view class="mt-8rpx text-[24rpx] text-gray-400">
            {{ activityTimeText(row) || '正在投放' }}
          </view>
        </view>
      </view>
    </view>

    <!-- 广场：形状照门店/品牌/活动那三块（封面 + 标题 + 两行次要信息），点进去是消息详情页。
         封面是发布人自己上传的那一张（没上传就在同一格铺标题首字，右边文字才不会跟着忽左忽右）。
         底下那行是点赞量与分享量 —— 只念数，不给按钮（整块是条跳转，两颗按钮还要各拦一次冒泡，搜索结果页不值得）。 -->
    <view v-else class="px-24rpx pt-24rpx">
      <view
        v-for="row in plazaRows"
        :key="row.id"
        hover-class="fx-tap"
        class="fx-press mb-24rpx flex rounded-xl bg-white p-24rpx"
        @click="toDetail('plaza', row.id)"
      >
        <image
          v-if="plazaLeadImage(row)"
          :src="plazaLeadImage(row)"
          mode="aspectFill"
          class="h-[112rpx] w-[112rpx] shrink-0 rounded-lg bg-gray-50"
        />
        <view
          v-else
          class="h-[112rpx] w-[112rpx] shrink-0 rounded-lg bg-gray-100 text-center text-[40rpx] text-gray-400 leading-[112rpx]"
        >
          {{ plazaLabel(row).charAt(0) }}
        </view>
        <view class="ml-24rpx min-w-0 flex-1">
          <view class="truncate text-[30rpx] text-[#333] font-medium">
            {{ plazaLabel(row) }}
          </view>
          <view class="mt-8rpx truncate text-[24rpx] text-gray-400">
            {{ plazaAuthor(row) }}<text v-if="plazaDate(row)"> · {{ plazaDate(row) }}</text>
          </view>
          <view class="mt-8rpx text-[24rpx] text-gray-400">
            点赞 · {{ row.likeCount ?? 0 }}
            <text class="ml-24rpx">分享 · {{ row.shareCount ?? 0 }}</text>
          </view>
        </view>
      </view>
    </view>

    <view v-if="current.block.rows.length" class="py-32rpx text-center text-[24rpx] text-gray-300">
      <text v-if="current.block.error" class="text-[#d14328]">
        下一页没加载成功
      </text>
      <text v-else-if="current.block.rows.length >= current.block.total">
        没有更多了
      </text>
      <text v-else>
        上滑看更多
      </text>
    </view>
  </view>
</template>
