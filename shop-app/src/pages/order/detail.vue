<script lang="ts" setup>
import type { IShopOrderDetail } from '@/api/types/shop'
import { getMyOrderDetail } from '@/api/profile'
import OrderPayPopup from '@/components/OrderPayPopup.vue'
import {
  codeText,
  dayOf,
  moneyOf,
  ORDER_STATUS_TEXT,
  PAY_TYPE_TEXT,
  SHIP_STATUS_TEXT,
} from '@/utils/profile'

defineOptions({
  name: 'OrderDetail',
})

definePage({
  style: {
    navigationBarTitleText: '订单详情',
  },
})

/**
 * 订单详情：主表那一排 + 名下全部商品明细行（后端按 orderId 一次全带，不分页）。
 *
 * 别人的单、被人删过的单、根本没这个 id —— 后端回同一句「订单不存在」，页面只说这一句，
 * 不为它们分档（口径同商品详情、同 shop-web 那份订单详情弹窗）。
 * 金额一律读服务端算好的那几格，页面不自己相加。
 */
const orderId = ref(0)
const detail = ref<IShopOrderDetail | null>(null)
const loadError = ref('')

/** 待支付那一单在本页也能付：弹窗收的是这份详情（它本身就是订单一行加明细） */
const payOpen = ref(false)

/** 0→1 由服务端翻，本地盖不上：付完把这一页整份重捞（状态、支付方式、支付时间那三格都是它改的） */
function onPaid() {
  payOpen.value = false
  void loadDetail()
}

const rows = computed(() => {
  const one = detail.value
  if (!one) {
    return []
  }
  return [
    { key: 'orderNo', label: '订单编号', value: one.orderNo },
    { key: 'status', label: '订单状态', value: codeText(ORDER_STATUS_TEXT, one.status) || '—' },
    { key: 'created', label: '下单时间', value: dayOf(one.createTime) },
    { key: 'payType', label: '支付方式', value: one.payType == null ? '未支付' : codeText(PAY_TYPE_TEXT, one.payType) },
    { key: 'payTime', label: '支付时间', value: dayOf(one.payTime) },
    { key: 'discount', label: '优惠金额', value: moneyOf(one.discountAmount) },
    { key: 'delivery', label: '配送费', value: moneyOf(one.deliveryFee) },
    { key: 'total', label: '订单金额', value: moneyOf(one.totalAmount) },
    { key: 'pay', label: '实付金额', value: moneyOf(one.payAmount) },
    { key: 'store', label: '下单门店', value: one.storeNames || '—' },
    { key: 'remark', label: '备注', value: one.remark || '—' },
  ].filter(row => row.value)
})

async function loadDetail() {
  try {
    detail.value = await getMyOrderDetail(orderId.value)
    uni.setNavigationBarTitle({ title: `订单 ${detail.value.orderNo}` })
  }
  catch (err) {
    // http 层已经 toast 服务端那句话，这里只把这一块换成提示
    loadError.value = (err as Error).message || '订单不存在'
  }
}

/** 明细行点回商品详情页；那条规格被删过就没带出 productId，不给链接 */
function toProduct(productId: number | null) {
  if (productId) {
    uni.navigateTo({ url: `/pages/goods/detail?id=${productId}` })
  }
}

function previewImage(url: string | null) {
  if (url) {
    uni.previewImage({ urls: [url] })
  }
}

onLoad((query) => {
  orderId.value = Number(query?.id) || 0
  if (!orderId.value) {
    loadError.value = '参数不对，没拿到订单 id'
    return
  }
  loadDetail()
})
</script>

<template>
  <view class="min-h-screen bg-[#f5f6f7] pb-48rpx pt-24rpx">
    <view v-if="loadError" class="py-160rpx text-center text-[26rpx] text-gray-400">
      {{ loadError }}
    </view>

    <template v-else-if="detail">
      <!-- 上面这块按「这一单买了什么」来排：一行一件商品，图 + 名 + 规格 + 单价×数量 + 行状态 -->
      <view class="mx-24rpx mb-24rpx rounded-xl bg-white px-32rpx py-16rpx">
        <view
          v-for="item in detail.items ?? []"
          :key="item.id"
          hover-class="fx-tap"
          class="fx-press flex border-b border-gray-50 py-24rpx last:border-b-0"
          @click="toProduct(item.productId)"
        >
          <image
            v-if="item.image"
            hover-class="fx-tap"
            :src="item.image"
            mode="aspectFill"
            class="fx-press h-[112rpx] w-[112rpx] shrink-0 rounded-lg bg-gray-50"
            @click.stop="previewImage(item.image)"
          />
          <view
            v-else
            class="h-[112rpx] w-[112rpx] shrink-0 rounded-lg bg-gray-100"
          />
          <view class="ml-24rpx min-w-0 flex-1">
            <view class="flex items-start justify-between">
              <text class="min-w-0 flex-1 truncate text-[28rpx] text-[#333]">
                {{ item.productName || '商品已下架或删除' }}
              </text>
              <text class="ml-16rpx shrink-0 text-[24rpx] text-gray-400">
                ×{{ item.quantity ?? 0 }}
              </text>
            </view>
            <view class="mt-8rpx flex items-baseline justify-between">
              <text class="truncate text-[24rpx] text-gray-400">
                {{ item.specText || '默认规格' }}
                <template v-if="item.storeName"> · {{ item.storeName }}</template>
              </text>
              <text class="ml-16rpx shrink-0 text-[24rpx] text-gray-500">
                {{ moneyOf(item.totalAmount) }}
              </text>
            </view>
            <view class="mt-8rpx flex items-baseline justify-between">
              <text class="text-[24rpx] text-gray-400">
                {{ moneyOf(item.price) }} / 件
              </text>
              <text
                v-if="item.shipStatus != null"
                class="rounded bg-gray-100 px-16rpx py-4rpx text-[22rpx] text-gray-600"
              >
                {{ codeText(SHIP_STATUS_TEXT, item.shipStatus) }}
              </text>
            </view>
          </view>
        </view>
        <view v-if="!detail.items?.length" class="py-48rpx text-center text-[26rpx] text-gray-400">
          这一单没有商品明细行
        </view>
      </view>

      <!-- 底下这一排是主单那几格账，金额都由服务端算好（货值 + 配送费 = 订单金额，再减券和积分 = 实付） -->
      <view class="mx-24rpx mb-24rpx rounded-xl bg-white px-32rpx py-16rpx">
        <view
          v-for="row in rows"
          :key="row.key"
          class="flex justify-between border-b border-gray-50 py-24rpx text-[28rpx] last:border-b-0"
        >
          <text class="shrink-0 text-gray-400">{{ row.label }}</text>
          <text class="ml-32rpx min-w-0 flex-1 truncate text-right text-[#333]">{{ row.value }}</text>
        </view>
      </view>

      <view class="mx-24rpx mb-24rpx flex items-center rounded-xl bg-white px-32rpx py-32rpx">
        <text class="min-w-0 flex-1 text-[26rpx] text-gray-400">
          {{ detail.itemCount ?? 0 }} 种 / {{ detail.totalQuantity ?? 0 }} 件，实付
        </text>
        <text class="text-[32rpx] text-[#d14328] font-medium">
          {{ moneyOf(detail.payAmount ?? detail.totalAmount) }}
        </text>
        <!-- 只有待支付那一档挂这颗（判据同列表那行、同 shop-web 的订单详情弹窗） -->
        <text
          v-if="detail.status === 0"
          hover-class="fx-tap"
          class="fx-press ml-24rpx shrink-0 rounded-full bg-[#d14328] px-32rpx py-12rpx text-[26rpx] text-white"
          @click="payOpen = true"
        >
          支付
        </text>
      </view>

      <OrderPayPopup v-model="payOpen" :order="detail" @paid="onPaid" />
    </template>

    <view v-else class="py-128rpx text-center text-[26rpx] text-gray-400">
      加载中…
    </view>
  </view>
</template>
