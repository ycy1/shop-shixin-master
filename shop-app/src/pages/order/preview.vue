<script lang="ts" setup>
import type { IShopAddressRow, IShopOrderForm, IShopOrderPreview } from '@/api/types/shop'
import { deleteCart, getMyCart } from '@/api/cart'
import { getMyAddresses } from '@/api/profile'
import { createOrder, previewOrder } from '@/api/order'
import { useTokenStore } from '@/store/token'
import { refreshCartBadge } from '@/utils/cartBadge'
import { toLoginPage } from '@/utils/toLoginPage'

defineOptions({
  name: 'OrderPreview',
})

definePage({
  style: {
    navigationBarTitleText: '确认订单',
  },
})

/**
 * 结算页：先挑一条收货地址，再问服务端这一单多少钱。
 *
 * 地址是这一页的第一格，而且是下单的硬前置：分仓（哪家仓出货）、配送费（跨不跨省、几公里）都由服务端按
 * 这一条地址上的坐标算，没挑地址就还没到能算钱的那一步。默认那条排在第一（SQL 钉的排序），进页先选中它。
 * 这一列从「个人信息 → 地址管理」那一档进来、也能从下面那颗「管理地址」过去，回来 onShow 重拉一遍 ——
 * 删掉的正好是选中那条时要换一颗，新加的也可能就是默认那条。
 */

const tokenStore = useTokenStore()

const form = ref<IShopOrderForm>({ items: [] })
const quote = ref<IShopOrderPreview | null>(null)
const submitting = ref(false)
/** 从购物车过来：行不由路径参数给，每次进页重拉一遍库里「勾着且还能买」的那几行 */
const fromCart = ref(false)
const cartIds = ref<number[]>([])

const addresses = ref<IShopAddressRow[]>([])
/** 地址那一列读到过没有：没读到不能把「还没有收货地址」说成事实 */
const addressLoaded = ref(false)
const pickerOpen = ref(false)

const selected = computed(() => addresses.value.find(one => one.id === form.value.addressId) ?? null)

const waitingText = computed(() => {
  if (!addressLoaded.value) {
    return '正在读你的收货地址…'
  }
  if (!selected.value) {
    return '先添加并选中一条收货地址，配送费和发货的仓都按它算'
  }
  if (form.value.items.length) {
    return '试算中…'
  }
  return fromCart.value ? '购物车里没有勾着的商品，回去勾一下' : '参数不对，没拿到规格'
})

async function loadAddresses() {
  try {
    const rows = (await getMyAddresses()).records
    addresses.value = rows
    // 已经选着的那一条还在这一列里就留着（换了地址、重新进来都不该跳回默认那条）；不在了就换第一颗
    if (!rows.some(one => one.id === form.value.addressId)) {
      form.value.addressId = rows[0]?.id
    }
  }
  catch {
    // 读不到就当这一列还是空的：地址那一格会指着「去添加」，不至于白报一次后端那句话
  }
  finally {
    addressLoaded.value = true
  }
}

/** 挑一条就重新试算一遍：换了地址，出货的仓和配送费都可能跟着变 */
function pick(one: IShopAddressRow) {
  pickerOpen.value = false
  if (one.id === form.value.addressId) {
    return
  }
  form.value.addressId = one.id
  quote.value = null
  void loadQuote()
}

function openPicker() {
  if (!addresses.value.length) {
    uni.navigateTo({ url: '/pages/profile/info?tab=address' })
    return
  }
  pickerOpen.value = true
}

/** 弹层里那颗「管理地址」：加一条、改一条、删一条都去「个人信息 → 地址管理」那一档，回来 onShow 会重拉这一列 */
function toAddressPage() {
  pickerOpen.value = false
  uni.navigateTo({ url: '/pages/profile/info?tab=address' })
}

async function loadCartItems() {
  try {
    const rows = await getMyCart()
    const picked = rows.filter(one => one.buyable === 1 && one.selected === 1)
    cartIds.value = picked.map(one => one.id)
    form.value = { ...form.value, items: picked.map(one => ({ skuId: one.skuId, quantity: one.quantity })) }
  }
  catch {
    // 拉不到车就留在「试算中」那一格，http 层已经把后端那句弹出来了
  }
}

async function loadQuote() {
  try {
    quote.value = await previewOrder(form.value)
  }
  catch {
    // 试算被拒（库存不足、券不合规等）由 http 层 toast，页面留在「试算中」这一格
  }
}

async function handleSubmit() {
  if (submitting.value || !selected.value) {
    return
  }
  submitting.value = true
  try {
    const res = await createOrder(form.value)
    // 下过单的那几行留在车里只会让人再下一遍；删失败不影响这一单已经落库，回去还能在车里手动删
    if (fromCart.value && cartIds.value.length) {
      await deleteCart(cartIds.value).catch(() => undefined)
      void refreshCartBadge()
    }
    uni.showModal({
      title: '下单成功',
      content: `订单号 ${res.orderNo}（待支付）`,
      showCancel: false,
      success: () => uni.navigateBack(),
    })
  }
  catch {
    // 同上，拒单原因以服务端那句话为准
  }
  finally {
    submitting.value = false
  }
}

onLoad((query) => {
  if (query?.from === 'cart') {
    fromCart.value = true
    return
  }
  const skuId = Number(query?.skuId)
  const quantity = Number(query?.quantity) || 1
  if (!skuId) {
    uni.showToast({ title: '参数不对', icon: 'none' })
    return
  }
  // 分仓、配送费、券抵扣都由服务端按选中的收货地址算，页面只给「哪个规格几件」+ 那一颗地址 id
  form.value = { ...form.value, items: [{ skuId, quantity }] }
})

// 每次进到这一页都重新问一遍服务端：从登录页回来要能算出钱，价格和券也可能在别处变了
onShow(async () => {
  if (!tokenStore.updateNowTime().hasLogin) {
    toLoginPage()
    return
  }
  await loadAddresses()
  if (fromCart.value) {
    await loadCartItems()
  }
  if (!form.value.items.length || !selected.value) {
    quote.value = null
    return
  }
  await loadQuote()
})
</script>

<template>
  <view class="min-h-full bg-white px-32rpx pb-192rpx pt-32rpx">
    <!-- 收货地址：下单的硬前置，点这一格换一条，没有就跳去「地址管理」加一条 -->
    <view hover-class="fx-tap" class="fx-press border border-gray-100 rounded-xl px-32rpx py-24rpx" @click="openPicker">
      <template v-if="selected">
        <view class="flex items-center">
          <text class="min-w-0 flex-1 truncate text-[30rpx] text-[#333] font-medium">
            {{ selected.receiverName }}
          </text>
          <text class="ml-16rpx shrink-0 text-[26rpx] text-gray-500">
            {{ selected.receiverPhone }}
          </text>
        </view>
        <view class="mt-8rpx text-[26rpx] text-gray-600 leading-[36rpx]">
          <text v-if="selected.areaZh">{{ selected.areaZh }} </text>{{ selected.detail }}
        </view>
        <view class="mt-8rpx flex items-center">
          <text
            v-if="selected.isDefault === 1"
            class="mr-16rpx shrink-0 rounded bg-[#fdece8] px-12rpx py-4rpx text-[22rpx] text-[#d14328]"
          >
            默认
          </text>
          <text
            v-if="selected.tag"
            class="mr-16rpx shrink-0 rounded bg-gray-100 px-12rpx py-4rpx text-[22rpx] text-gray-600"
          >
            {{ selected.tag }}
          </text>
          <text class="ml-auto shrink-0 text-[26rpx] text-[#d14328]">
            更换 ›
          </text>
        </view>
      </template>
      <view v-else class="flex items-center justify-between">
        <text class="min-w-0 flex-1 truncate text-[28rpx]" :class="addressLoaded ? 'text-gray-400' : 'text-gray-300'">
          {{ addressLoaded ? '还没有收货地址，先去添加一条' : '正在读你的收货地址…' }}
        </text>
        <text v-if="addressLoaded" class="ml-16rpx shrink-0 text-[26rpx] text-[#d14328]">
          去添加 ›
        </text>
      </view>
    </view>

    <template v-if="quote">
      <view class="mt-40rpx">
        <view class="text-[28rpx] text-gray-600">
          商品
        </view>
        <view
          v-for="line in quote.items"
          :key="line.skuId"
          class="mt-24rpx flex items-start justify-between border-b border-gray-50 pb-24rpx"
        >
          <view class="flex-1">
            <view class="text-[28rpx]">
              {{ line.productName }}
            </view>
            <view class="mt-8rpx text-[24rpx] text-gray-400">
              {{ line.specText || '默认规格' }} × {{ line.quantity }}
            </view>
          </view>
          <view class="text-right">
            <view class="text-[28rpx]">
              ¥{{ line.totalAmount }}
            </view>
            <view class="mt-8rpx text-[22rpx] text-gray-400">
              单价 ¥{{ line.price }}
            </view>
          </view>
        </view>
      </view>

      <view class="mt-40rpx border-t border-gray-100 pt-32rpx">
        <view class="flex justify-between py-8rpx text-[26rpx] text-gray-600">
          <text>货值合计</text>
          <text>¥{{ quote.goodsAmount }}</text>
        </view>
        <view class="flex justify-between py-8rpx text-[26rpx] text-gray-600">
          <text>配送费</text>
          <text>¥{{ quote.deliveryFee }}</text>
        </view>
        <view v-if="Number(quote.discountAmount) > 0" class="flex justify-between py-8rpx text-[26rpx] text-[#d14328]">
          <text>优惠券抵扣</text>
          <text>-¥{{ quote.discountAmount }}</text>
        </view>
        <view v-if="Number(quote.pointsDiscount) > 0" class="flex justify-between py-8rpx text-[26rpx] text-[#d14328]">
          <text>积分抵扣（{{ quote.usedPoints }} 分）</text>
          <text>-¥{{ quote.pointsDiscount }}</text>
        </view>
        <view class="flex justify-between border-t border-gray-100 py-16rpx text-[26rpx] text-gray-600">
          <text>应付合计</text>
          <text>¥{{ quote.totalAmount }}</text>
        </view>
      </view>

      <view class="fx-bar fixed bottom-0 left-0 right-0 z-10 border-t border-gray-100">
        <view class="flex items-center justify-between px-32rpx py-24rpx">
          <view class="text-[30rpx] text-[#d14328]">
            实付 ¥{{ quote.payAmount }}
          </view>
          <wd-button type="error" :loading="submitting" @click="handleSubmit">
            提交订单
          </wd-button>
        </view>
      </view>

      <!-- 栏子（.fx-bar）比原来高出安全区那一截，页面尾巴也跟着垫同样一截 -->
      <view class="pb-safe" />
    </template>

    <view v-else class="py-128rpx text-center text-[26rpx] text-gray-400">
      {{ waitingText }}
    </view>

    <!-- 挑地址：从下面推出来的一列（形状同支付那两颗弹窗），选中那颗打个勾，底下留一颗去「地址管理」 -->
    <wd-popup v-model="pickerOpen" position="bottom" safe-area-inset-bottom closable>
      <view class="px-32rpx pb-32rpx pt-8rpx">
        <view class="pb-24rpx pt-16rpx text-[32rpx] text-[#333] font-medium">
          选择收货地址
        </view>
        <view
          v-for="one in addresses"
          :key="one.id"
          hover-class="fx-tap"
          class="fx-press border-b border-gray-50 py-24rpx last:border-b-0"
          @click="pick(one)"
        >
          <view class="flex items-center">
            <text class="min-w-0 flex-1 truncate text-[28rpx] text-[#333]">
              {{ one.receiverName }} {{ one.receiverPhone }}
            </text>
            <text v-if="one.id === form.addressId" class="shrink-0 text-[26rpx] text-[#d14328]">
              ✓
            </text>
          </view>
          <view class="mt-8rpx text-[24rpx] text-gray-500 leading-[34rpx]">
            <text v-if="one.areaZh">{{ one.areaZh }} </text>{{ one.detail }}
          </view>
        </view>

        <view class="mt-24rpx">
          <wd-button plain block type="error" @click="toAddressPage">
            管理地址
          </wd-button>
        </view>
      </view>
    </wd-popup>
  </view>
</template>
