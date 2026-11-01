<script setup lang="ts">
import type { IShopSigninRow } from '@/api/types/shop'
import { getMyNotificationUnread } from '@/api/notification'
import { getMySignins } from '@/api/profile'
import { LOGIN_PAGE } from '@/router/config'
import { useTokenStore } from '@/store/token'
import { todayText } from '@/utils/profile'

/**
 * 右侧栏：从「我的」那一页里搬出来的五张入口（订单、广场消息、购物车留在「我的」那一页）。
 *
 * 开法两条：任意页面左滑（布局那一层）、点搜索那一排右边那颗（components/AppSearchBar.vue），这里只认 v-model。
 * 未读数和签到状态是现数：只在开抽屉这一趟发两枪（并发），不开就一趟都不发 —— 库在公网。
 */
const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

interface MenuItem {
  key: 'browse' | 'favorite' | 'info' | 'message' | 'signin'
  label: string
  url: string
}

const ITEMS: MenuItem[] = [
  { key: 'info', label: '个人信息', url: '/pages/profile/info' },
  { key: 'favorite', label: '我的收藏', url: '/pages/profile/favorite' },
  { key: 'browse', label: '浏览足迹', url: '/pages/profile/browse' },
  { key: 'message', label: '我的消息', url: '/pages/message/center' },
  { key: 'signin', label: '签到记录', url: '/pages/profile/signin' },
]

const tokenStore = useTokenStore()
const unread = ref(0)
const signedToday = ref(false)

async function refresh() {
  if (!tokenStore.updateNowTime().hasLogin) {
    unread.value = 0
    signedToday.value = false
    return
  }
  const [summary, signins] = await Promise.all([
    getMyNotificationUnread().catch(() => null),
    // 兜底那一份要给成行数组的类型：写成 [] 会被推成 never[]，后面 .some 拿到的元素是 never
    getMySignins().catch((): IShopSigninRow[] => []),
  ])
  // 角标和那颗签到是提示不是数据：捞不到就留旧样子，不该让抽屉变成一屏红框（口径同「我的」那页）
  unread.value = Number(summary?.total) || 0
  signedToday.value = signins.some(one => one.signDate === todayText())
}

watch(() => props.modelValue, (open) => {
  if (open)
    void refresh()
})

function go(one: MenuItem) {
  emit('update:modelValue', false)
  // 这五张读的都是「自己名下」的数据，没登录进去只能拿服务层那句「请先登录」换一屏红框 ⇒ 先收住带去登录页
  if (!tokenStore.updateNowTime().hasLogin) {
    uni.navigateTo({ url: LOGIN_PAGE })
    return
  }
  uni.navigateTo({ url: one.url })
}
</script>

<template>
  <!-- z-index 要盖过自定义 tabbar 那一层（它写的是 1000），不然抽屉底下那几行被 bar 压住 -->
  <wd-popup
    :model-value="props.modelValue"
    position="right"
    :z-index="1200"
    safe-area-inset-bottom
    @update:model-value="emit('update:modelValue', $event)"
  >
    <view class="w-[480rpx] box-border pb-48rpx pt-40rpx">
      <view class="px-32rpx text-[32rpx] font-medium text-[#333]">
        功能菜单
      </view>
      <view class="mt-24rpx px-32rpx">
        <view
          v-for="one in ITEMS"
          :key="one.key"
          hover-class="fx-tap"
          class="fx-press flex items-center justify-between border-b border-gray-50 py-32rpx last:border-b-0"
          @click="go(one)"
        >
          <text class="text-[28rpx] text-[#333]">{{ one.label }}</text>
          <view class="flex items-center">
            <!-- 未读数钉在「我的消息」那一行：红底白字，和「我的」头部那颗铃铛同一款 -->
            <text
              v-if="one.key === 'message' && unread"
              class="mr-16rpx min-w-[32rpx] rounded-full bg-[#d14328] px-10rpx text-center text-[20rpx] leading-32rpx text-white"
            >
              {{ unread > 99 ? '99+' : unread }}
            </text>
            <!-- 签到这一行说的是今天的状态，不在这儿签（那一动作归「我的」头部那颗） -->
            <text v-else-if="one.key === 'signin'" class="mr-16rpx text-[24rpx]" :class="signedToday ? 'text-gray-400' : 'text-[#d14328]'">
              {{ signedToday ? '今日已签' : '今天还没签' }}
            </text>
            <text class="text-[26rpx] text-gray-300">›</text>
          </view>
        </view>
      </view>
    </view>
  </wd-popup>
</template>
