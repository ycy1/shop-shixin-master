<script lang="ts" setup>
import type { IShopOrderRow } from '@/api/types/shop'
import { getMyOrders } from '@/api/profile'
import ListState from '@/components/ListState.vue'
import OrderPayPopup from '@/components/OrderPayPopup.vue'
import { usePagedList } from '@/composables/usePagedList'
import { LOGIN_PAGE } from '@/router/config'
import { useTokenStore } from '@/store/token'
import { codeText, dayOf, moneyOf, ORDER_STATUS_TEXT } from '@/utils/profile'

defineOptions({
  name: 'OrderList',
})

definePage({
  style: {
    navigationBarTitleText: '我的订单',
    enablePullDownRefresh: true,
    onReachBottomDistance: 100,
  },
})

/**
 * 我的订单：一行一张订单（不是行级明细），口径照 shop-web「个人中心 → 我的订单」那一块 ——
 * 单号 + 状态 + 下单日子 + 几种/件 + 名下门店 + 实付金额，右边一颗「详情」进下一张页看明细行。
 *
 * 只读之外就一颗「支付」：只挂在 status=0 那一行（口径同 shop-web 个人中心 → 我的订单），
 * 取消、退货那几颗还是写动作，App 页面还没铺，铺到哪条再补哪条。
 */
const tokenStore = useTokenStore()
const orders = usePagedList<IShopOrderRow>(getMyOrders)

/** 支付弹窗收的是整行：单号、实付都从行上拿，页面不另捞一次详情 */
const payOpen = ref(false)
const payTarget = ref<IShopOrderRow | null>(null)

function openPay(row: IShopOrderRow) {
  payTarget.value = row
  payOpen.value = true
}

/** 0→1 是服务端翻的（那边按 WHERE status = 0 更新），本地盖不上，只能整块清回首页重拉 */
function onPaid() {
  payOpen.value = false
  payTarget.value = null
  void orders.reload()
}

/** 一行一张单上的「几种 / 件」：行数、件数都是服务端从明细上现汇总的，页面不自己数 */
function kindsPieces(row: IShopOrderRow) {
  return `${row.itemCount ?? 0} 种 / ${row.totalQuantity ?? 0} 件`
}

function toDetail(id: number) {
  uni.navigateTo({ url: `/pages/order/detail?id=${id}` })
}

function toLogin() {
  uni.navigateTo({ url: LOGIN_PAGE })
}

onLoad(() => {
  if (tokenStore.updateNowTime().hasLogin) {
    void orders.load()
  }
})

onPullDownRefresh(async () => {
  await orders.reload()
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  if (orders.hasMore) {
    void orders.load()
  }
})
</script>

<template>
  <view class="min-h-screen bg-[#f5f6f7] pb-48rpx">
    <view v-if="!tokenStore.hasLogin" class="px-32rpx py-160rpx text-center">
      <view class="text-[28rpx] text-gray-400">
        登录后才能看自己名下的订单
      </view>
      <wd-button class="mt-32rpx" type="error" @click="toLogin">
        去登录
      </wd-button>
    </view>

    <ListState
      v-else
      class="pt-24rpx"
      :loading="orders.loading"
      :error="orders.error"
      :count="orders.rows.length"
      :has-more="orders.hasMore"
      empty-text="还没有下过单"
      @retry="orders.load()"
    >
      <view
        v-for="row in orders.rows"
        :key="row.id"
        hover-class="fx-tap"
        class="fx-press mx-24rpx mb-24rpx rounded-xl bg-white p-32rpx"
        @click="toDetail(row.id)"
      >
        <view class="flex items-baseline justify-between">
          <text class="min-w-0 flex-1 truncate text-[24rpx] text-gray-400">
            {{ row.orderNo }}
          </text>
          <text class="ml-16rpx shrink-0 rounded bg-gray-100 px-16rpx py-4rpx text-[22rpx] text-gray-600">
            {{ codeText(ORDER_STATUS_TEXT, row.status) || '—' }}
          </text>
        </view>

        <view class="mt-16rpx flex items-baseline justify-between">
          <text class="min-w-0 flex-1 truncate text-[24rpx] text-gray-400">
            {{ dayOf(row.createTime) }} · {{ kindsPieces(row) }}
            <template v-if="row.storeNames"> · {{ row.storeNames }}</template>
          </text>
          <text class="ml-16rpx shrink-0 text-[30rpx] text-[#d14328] font-medium">
            {{ moneyOf(row.payAmount ?? row.totalAmount) }}
          </text>
        </view>

        <view class="mt-24rpx flex items-center justify-end gap-16rpx border-t border-gray-50 pt-24rpx">
          <text
            v-if="row.status === 0"
            hover-class="fx-tap"
            class="fx-press rounded-full bg-[#d14328] px-32rpx py-12rpx text-[26rpx] text-white"
            @click.stop="openPay(row)"
          >
            支付
          </text>
          <text hover-class="fx-tap" class="fx-press text-[26rpx] text-[#d14328]" @click.stop="toDetail(row.id)">订单详情 ›</text>
        </view>
      </view>
    </ListState>

    <OrderPayPopup v-model="payOpen" :order="payTarget" @paid="onPaid" />
  </view>
</template>
