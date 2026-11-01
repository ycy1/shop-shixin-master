<script lang="ts" setup>
import type { IShopOrderRow, IShopParkTicketRow } from '@/api/types/shop'
import { getMyOrders, getMyParkTickets } from '@/api/profile'
import ListState from '@/components/ListState.vue'
import OrderPayPopup from '@/components/OrderPayPopup.vue'
import { usePagedList } from '@/composables/usePagedList'
import { LOGIN_PAGE } from '@/router/config'
import { useTokenStore } from '@/store/token'
import {
  codeText,
  dayOf,
  moneyOf,
  ORDER_STATUS_TEXT,
  PARK_TICKET_SLOT_TEXT,
  PARK_TICKET_STATUS_TEXT,
  PARK_TICKET_TYPE_TEXT,
} from '@/utils/profile'

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
 * 两个 tab 一本账：第一本是买货下的单，第二本是发到手上、能进园的公园票。
 * 两本各一份分页（切过去才拉那一本，口径同 Web 个人中心「点开谁拉谁」），
 * 下拉刷新和触底续页都只认现在开着的那一本。
 *
 * 第一本：一行一张订单（不是行级明细），口径照 shop-web「个人中心 → 我的订单」那一块 ——
 * 单号 + 状态 + 下单日子 + 几种/件 + 名下门店 + 实付金额，右边一颗「详情」进下一张页看明细行。
 * 只读之外就一颗「支付」：只挂在 status=0 那一行（口径同 shop-web 个人中心 → 我的订单），
 * 取消、退货那几颗还是写动作，App 页面还没铺，铺到哪条再补哪条。
 *
 * 第二本：一行一张票。票名/类型/票价读的是出票那一刻定格的快照（后台后来改了票种也不串到这张票上），
 * 票面二维码那张图的内容就是这一行的票码 —— 点开放大是对着闸机用的，这一本不给核销/退款那几颗按钮。
 */
const tokenStore = useTokenStore()

type TabKey = 'order' | 'ticket'
const tab = ref<TabKey>('order')
const TABS: { key: TabKey, label: string }[] = [
  { key: 'order', label: '我的订单' },
  { key: 'ticket', label: '我的票种' },
]

const orders = usePagedList<IShopOrderRow>(getMyOrders)
const tickets = usePagedList<IShopParkTicketRow>(getMyParkTickets)

/** 现在开着的那一本：下拉刷新、触底续页、ListState 那四格状态都只认它 */
const active = computed(() => (tab.value === 'order' ? orders : tickets))

/** 点开哪一本才拉哪一本（另一本等切过去）；page===0 就是"一次都没捞过"（空数据也算捞过） */
function pickTab(next: TabKey) {
  if (next === tab.value)
    return
  tab.value = next
  if (active.value.page === 0 && !active.value.loading)
    void active.value.load()
}

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

/** 一张票上的那一行说明：游玩日子 + 约的时段（时段是一张票一个档，不是票种卖的那几档） */
function visitText(row: IShopParkTicketRow) {
  return `${row.visitDate || '—'} · ${codeText(PARK_TICKET_SLOT_TEXT, row.timeSlot) || '不限时段'}`
}

/** 票面图点开：给一整屏看，方便对着闸机扫；出票时那趟出图失败就没有这一颗（票本身照样能用票码核销） */
function previewQr(row: IShopParkTicketRow) {
  if (!row.qrImg)
    return
  uni.previewImage({ urls: [row.qrImg], current: row.qrImg })
}

function toDetail(id: number) {
  uni.navigateTo({ url: `/pages/order/detail?id=${id}` })
}

function toLogin() {
  uni.navigateTo({ url: LOGIN_PAGE })
}

onLoad(() => {
  if (tokenStore.updateNowTime().hasLogin)
    void orders.load()
})

onPullDownRefresh(async () => {
  await active.value.reload()
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  if (active.value.hasMore)
    void active.value.load()
})
</script>

<template>
  <view class="min-h-screen bg-[#f5f6f7] pb-48rpx">
    <!-- 两本账那一排 tab 在登录闸门上面也要在：不这么做，没登录时这一页只剩一句提示，看着像页面坏了 -->
    <view class="flex border-b border-gray-100 bg-white">
      <view
        v-for="item in TABS"
        :key="item.key"
        hover-class="fx-tap"
        class="fx-press flex-1 py-24rpx text-center text-[28rpx]"
        :class="tab === item.key ? 'text-[#d14328] font-medium' : 'text-gray-500'"
        @click="pickTab(item.key)"
      >
        {{ item.label }}
        <view
          class="mx-auto mt-8rpx h-4rpx w-48rpx rounded-full"
          :class="tab === item.key ? 'bg-[#d14328]' : 'bg-transparent'"
        />
      </view>
    </view>

    <view v-if="!tokenStore.hasLogin" class="px-32rpx py-160rpx text-center">
      <view class="text-[28rpx] text-gray-400">
        登录后才能看自己名下的订单和票
      </view>
      <wd-button class="mt-32rpx" type="error" @click="toLogin">
        去登录
      </wd-button>
    </view>

    <!-- 第一本：我的订单 -->
    <ListState
      v-else-if="tab === 'order'"
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

    <!-- 第二本：我的票种。票码 + 状态一行，票种/园区一行，游玩日子+时段和票价一行，底下那张票面图点开能扫 -->
    <ListState
      v-else
      class="pt-24rpx"
      :loading="tickets.loading"
      :error="tickets.error"
      :count="tickets.rows.length"
      :has-more="tickets.hasMore"
      empty-text="还没有拿到过票"
      @retry="tickets.load()"
    >
      <view
        v-for="row in tickets.rows"
        :key="row.id"
        class="mx-24rpx mb-24rpx rounded-xl bg-white p-32rpx"
      >
        <view class="flex items-baseline justify-between">
          <text class="min-w-0 flex-1 truncate text-[24rpx] text-gray-400">
            {{ row.ticketCode }}
          </text>
          <text class="ml-16rpx shrink-0 rounded bg-gray-100 px-16rpx py-4rpx text-[22rpx] text-gray-600">
            {{ codeText(PARK_TICKET_STATUS_TEXT, row.status) || '—' }}
          </text>
        </view>

        <view class="mt-16rpx flex items-baseline justify-between">
          <text class="min-w-0 flex-1 truncate text-[28rpx] font-medium">
            {{ row.ticketName || '—' }}
            <text v-if="row.ticketType" class="ml-8rpx text-[22rpx] text-gray-400">
              {{ codeText(PARK_TICKET_TYPE_TEXT, row.ticketType) }}
            </text>
          </text>
          <text class="ml-16rpx shrink-0 text-[30rpx] text-[#d14328] font-medium">
            {{ moneyOf(row.price) }}
          </text>
        </view>

        <view class="mt-8rpx text-[24rpx] text-gray-400">
          {{ row.parkName || '园区已下架' }} · {{ visitText(row) }}
        </view>

        <!-- 票面图：出票那一刻生成的那一张，内容就是上面这串票码；没图就只留这一串码 -->
        <view class="mt-24rpx flex items-center justify-between border-t border-gray-50 pt-24rpx">
          <text class="min-w-0 flex-1 truncate text-[24rpx] text-gray-400">
            {{ row.holderName ? `持票人 ${row.holderName}` : '没填持票人' }}
          </text>
          <image
            v-if="row.qrImg"
            :src="row.qrImg"
            mode="aspectFit"
            hover-class="fx-tap"
            class="fx-press h-96rpx w-96rpx shrink-0 rounded bg-white"
            @click="previewQr(row)"
          />
          <text v-else class="shrink-0 text-[24rpx] text-gray-300">
            票面图没生成
          </text>
        </view>
      </view>
    </ListState>

    <OrderPayPopup v-model="payOpen" :order="payTarget" @paid="onPaid" />
  </view>
</template>
