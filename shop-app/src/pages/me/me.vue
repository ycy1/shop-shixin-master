<script lang="ts" setup>
import type { IShopAccount, IShopSigninRow } from '@/api/types/shop'
import { storeToRefs } from 'pinia'
import { signinToday } from '@/api/activity'
import { getMyNotificationUnread } from '@/api/notification'
import { getMyAccount, getMySignins } from '@/api/profile'
import { LOGIN_PAGE } from '@/router/config'
import { useUserStore } from '@/store'
import { useTokenStore } from '@/store/token'
import { clearCartBadge } from '@/utils/cartBadge'
import AppNavBar from '@/components/AppNavBar.vue'
import AppSearchBar from '@/components/AppSearchBar.vue'
import { ACCOUNT_TYPE_TEXT, todayText } from '@/utils/profile'

defineOptions({
  name: 'Me',
})

definePage({
  style: {
    navigationBarTitleText: '我的',
    // 导航条自绘，同首页（components/AppNavBar.vue）
    navigationStyle: 'custom',
  },
})

/**
 * 「我的」这一页留三张主入口：订单管理、广场消息、我的购物车；其余五张搬进了右侧栏
 * （components/SideMenu.vue，任意页面左滑开、tab 四页点头部导航右侧那颗，详情页没有那颗图标只能左滑）；
 * 账号那一块（头像、登录、铃铛、签到、退出）和四行资料照旧留在这。
 * 形状照 shop-web 的 /user 那一页搬（他点名的范本）。
 * 这一页自己有两处写：头部那颗签到（POST /api/comm/activity/signin，两端共用那一个动作）和它旁边那颗退出；
 * 改资料在「个人信息 → 基础信息」的修改页上，签到记录与补签在「签到记录」那一张页上，
 * 收货地址收在「个人信息 → 地址管理」那一档里（结算页也直接落到那一档），改密码那条 App 页面还没铺。
 */
interface Entry {
  key: string
  label: string
  url: string
  /** tabbar 那一档的页面：navigateTo 跳不过去，得换 switchTab */
  tab?: boolean
}

/** 头部那颗铃铛的落点：和抽屉里「我的消息」是同一张页，这一页清单里不列它（他点名清单只留订单、广场、购物车） */
const MESSAGE: Entry = { key: 'message', label: '我的消息', url: '/pages/message/center' }

const ENTRIES: Entry[] = [
  { key: 'order', label: '订单管理', url: '/pages/order/list' },
  { key: 'plaza', label: '广场消息', url: '/pages/profile/plaza' },
  { key: 'cart', label: '我的购物车', url: '/pages/cart/cart', tab: true },
]

const userStore = useUserStore()
const tokenStore = useTokenStore()
const { userInfo } = storeToRefs(userStore)

const account = ref<IShopAccount | null>(null)

/**
 * 头部那颗铃铛上的未读数：读的是服务端汇总那一格（total），不数本地消息列表 —— 和后台铃铛、Web 页头同一口径。
 * 它是提示不是数据：捞不到就留着上一趟的数，不能因为角标把这一页变成红框。
 */
const unread = ref(0)

async function loadUnread() {
  try {
    unread.value = Number((await getMyNotificationUnread()).total) || 0
  }
  catch {
    // 同上：这一趟不改样子
  }
}

const rows = computed(() => {
  const user = account.value?.sysUser
  if (!user) {
    return []
  }
  return [
    { key: 'mobile', label: '手机号', value: user.mobile ?? '' },
    { key: 'email', label: '邮箱', value: user.email ?? '' },
    { key: 'area', label: '所在地区', value: [user.areaZh, user.address].filter(Boolean).join(' ') },
    { key: 'signature', label: '个性签名', value: user.signature ?? '' },
  ].filter(row => row.value)
})

const accountTypeText = computed(() => {
  const type = account.value?.sysUser?.accountType
  return type == null ? '' : ACCOUNT_TYPE_TEXT[type] ?? ''
})

/**
 * 头部那颗签到：判「今天签过没」只看当月那几天的 signDate（month 留空，服务端就按当月给），
 * 所以这一页只发一趟读，不为签到另开一张日历页。
 */
const signins = ref<IShopSigninRow[]>([])
const signing = ref(false)
const signedToday = computed(() => signins.value.some(one => one.signDate === todayText()))
/** 连签天数是服务端盖在每一行上的同一个数（从今天往前逐日回数，断了一天就是 0），页面不自己排 */
const continuousDays = computed(() => Number(signins.value[0]?.currentContinuous ?? 0))

async function loadSignins() {
  try {
    signins.value = await getMySignins()
  }
  catch {
    // 读不到就留在「没签过」的样子：那一颗还是点得，重复签到服务端会拒第二遍
  }
}

async function handleSignin() {
  if (signing.value) {
    return
  }
  // 签过了这一颗不再弹「今天已经签过了」，直接带去记录页：那里能翻月看历史、也能补最近 7 天漏的那几天
  if (signedToday.value) {
    uni.navigateTo({ url: '/pages/profile/signin' })
    return
  }
  signing.value = true
  try {
    const row = await signinToday()
    uni.showToast({ title: `签到成功，+${Number(row.points ?? 0)} 积分`, icon: 'none' })
    await loadSignins()
  }
  catch {
    // 拒签那句（今天已经签过了、账号被禁用等）由 http 层弹，页面这一趟不改样子
  }
  finally {
    signing.value = false
  }
}

async function loadAccount() {
  try {
    account.value = await getMyAccount()
  }
  catch {
    // 没登录或后端拒了都只留登录态那一块，账号明细那块自己不出现（http 层已经 toast）
  }
}

// 哪一档都带去同一张登录页：账号密码哪一档都输得成，微信那颗只在小程序和 App 画（H5 没网页授权凭据），这一页不替用户选
async function handleLogin() {
  uni.navigateTo({ url: LOGIN_PAGE })
}

const guest = computed(() => !tokenStore.hasLogin)

/** 没登录时头像和那行「未登录」都是进登录页的入口；登录后这两块不当按钮用（点了没反应，别误导） */
function handleLoginByHeader() {
  if (guest.value) {
    handleLogin()
  }
}

function handleLogout() {
  uni.showModal({
    title: '提示',
    content: '确定要退出登录吗？',
    success: (res) => {
      if (!res.confirm) {
        return
      }
      tokenStore.logout()
      account.value = null
      signins.value = []
      unread.value = 0
      clearCartBadge()
      uni.showToast({ title: '已退出登录', icon: 'success' })
    },
  })
}

/**
 * 三个入口都要登录：这几张页读的全是「自己名下」的数据，没登录进去只能拿服务层那句「请先登录」
 * 换一屏红框。所以先在这一颗收住、直接带去登录页，登录后回来再点（口径同购物车那一路）。
 */
function toEntry(one: Entry) {
  // tabbar 那一档 navigateTo 跳不过去（会静默失败），得走 switchTab
  if (one.tab) {
    uni.switchTab({ url: one.url })
    return
  }
  if (!tokenStore.updateNowTime().hasLogin) {
    uni.navigateTo({ url: LOGIN_PAGE })
    return
  }
  uni.navigateTo({ url: one.url })
}

onShow(() => {
  if (tokenStore.updateNowTime().hasLogin) {
    loadAccount()
    loadSignins()
    loadUnread()
  }
  else {
    signins.value = []
    // 铃铛跟着不画了，数也一起收掉：不然下次换个账号进来还挂着别人那趟的数
    unread.value = 0
  }
})
</script>

<template>
  <!-- 顶上这格间距从页面根上搬到第一张卡：根上的 pt 会把自绘的导航栏一起顶下来，标题就和别的页差一截 -->
  <view class="min-h-screen bg-[#f5f6f7] pb-192rpx">
    <AppNavBar title="我的" />

    <!-- 搜索入口 + 抽屉那颗：和首页同一排公共件（components/AppSearchBar.vue），四张 tab 页都有 -->
    <AppSearchBar />

    <view class="mx-24rpx mb-24rpx mt-24rpx rounded-xl bg-white px-32rpx py-40rpx">
      <view class="flex items-center">
        <!-- 没登录时这两块（头像 + 那行名字）跟着「登录」那颗一起是入口，点了都去登录页 -->
        <image
          :src="userInfo.avatar || '/static/images/default-avatar.png'"
          mode="aspectFill"
          :hover-class="guest ? 'fx-tap' : ''"
          :class="guest ? 'fx-press' : ''"
          class="h-[120rpx] w-[120rpx] shrink-0 rounded-full bg-gray-100"
          @click="handleLoginByHeader"
        />
        <view class="ml-24rpx min-w-0 flex-1" :hover-class="guest ? 'fx-tap' : ''" @click="handleLoginByHeader">
          <view class="truncate text-[34rpx] text-[#333] font-medium" :class="guest ? 'fx-press' : ''">
            {{ userInfo.nickname || userInfo.username || '未登录' }}
          </view>
          <view v-if="accountTypeText" class="mt-8rpx flex flex-wrap items-center gap-16rpx">
            <text class="rounded bg-blue-50 px-16rpx py-4rpx text-[22rpx] text-blue-700">
              {{ accountTypeText }}
            </text>
          </view>
        </view>
        <wd-button v-if="!tokenStore.hasLogin" type="error" size="small" @click="handleLogin">
          登录
        </wd-button>
        <template v-else>
          <!-- 铃铛快捷口：点了直接进「我的消息」，未读数挂在右上角（口径同 Web 页头那颗，读的是服务端汇总那一格） -->
          <view
            hover-class="fx-tap"
            class="fx-press relative ml-16rpx shrink-0 rounded-full bg-gray-100 px-20rpx py-12rpx"
            @click="toEntry(MESSAGE)"
          >
            <text class="text-[28rpx] leading-40rpx">
              🔔
            </text>
            <view
              v-if="unread"
              class="absolute right--8rpx top--8rpx min-w-[28rpx] rounded-full bg-[#d14328] px-8rpx text-center text-[18rpx] text-white leading-28rpx"
            >
              {{ unread > 99 ? '99+' : unread }}
            </view>
          </view>
          <!-- 签到就一个动作：点一下落当天那一行；签过了这一颗改成说出连签几天，再点进「签到记录」那一页翻历史和补签 -->
          <view
            hover-class="fx-tap"
            class="fx-press ml-16rpx shrink-0 rounded-full px-24rpx py-12rpx text-[24rpx]"
            :class="signedToday ? 'bg-gray-100 text-gray-400' : 'bg-[#fdece8] text-[#d14328] font-medium'"
            @click="handleSignin"
          >
            {{ signedToday ? `连签${continuousDays}天` : '签到' }}
          </view>
          <!-- 退出登录和签到并排在头部（原来挂在最下面那张卡里，翻到底才看得见） -->
          <view hover-class="fx-tap" class="fx-press ml-16rpx shrink-0 rounded-full bg-gray-100 px-24rpx py-12rpx text-[24rpx] text-gray-500" @click="handleLogout">
            退出
          </view>
        </template>
      </view>
    </view>

    <view v-if="account?.sysUser" class="mx-24rpx mb-24rpx rounded-xl bg-white px-32rpx py-16rpx">
      <view v-for="row in rows" :key="row.key" class="flex justify-between border-b border-gray-50 py-24rpx text-[28rpx] last:border-b-0">
        <text class="text-gray-400">{{ row.label }}</text>
        <text class="ml-32rpx min-w-0 flex-1 truncate text-right text-[#333]">{{ row.value }}</text>
      </view>
    </view>

    <view class="mx-24rpx mb-24rpx rounded-xl bg-white px-32rpx">
      <view
        v-for="one in ENTRIES"
        :key="one.key"
        hover-class="fx-tap"
        class="fx-press flex items-center justify-between border-b border-gray-50 py-32rpx last:border-b-0"
        @click="toEntry(one)"
      >
        <text class="text-[28rpx] text-[#333]">{{ one.label }}</text>
        <text class="text-[26rpx] text-gray-300">›</text>
      </view>
    </view>
  </view>
</template>
