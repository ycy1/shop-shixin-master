<script lang="ts" setup>
import type { IShopAccount, IShopAddressRow, IShopBadgeRow, IShopCouponRow, IShopPointsAccount, IShopPointsLogRow, IShopWalletAccount, IShopWalletLogRow } from '@/api/types/shop'
import {
  deleteMyAddress,
  getMyAccount,
  getMyAddresses,
  getMyBadges,
  getMyCoupons,
  getMyPointsAccount,
  getMyPointsLogs,
  getMyWalletAccount,
  getMyWalletLogs,
} from '@/api/profile'
import ListState from '@/components/ListState.vue'
import WalletChangePopup from '@/components/WalletChangePopup.vue'
import { usePagedList } from '@/composables/usePagedList'
import { LOGIN_PAGE } from '@/router/config'
import { useTokenStore } from '@/store/token'
import {
  BADGE_SOURCE_TEXT,
  BADGE_TYPE_TEXT,
  badgeStatusText,
  badgeValid,
  codeText,
  COUPON_STATUS_TEXT,
  COUPON_TYPE_TEXT,
  couponBenefit,
  couponExpired,
  dayOf,
  GET_WAY_TEXT,
  moneyOf,
  POINTS_BIZ_TEXT,
  pointsOf,
  SEX_TEXT,
  signedMoney,
  signedPoints,
  WALLET_BIZ_TEXT,
  WALLET_STATUS_TEXT,
} from '@/utils/profile'

defineOptions({
  name: 'ProfileInfo',
})

definePage({
  style: {
    navigationBarTitleText: '个人信息',
    enablePullDownRefresh: true,
    onReachBottomDistance: 100,
  },
})

/**
 * 个人信息六档，形状照 shop-web 的「个人信息」那一块（同一排 tab、同样的先后顺序）：
 * 基础信息 / 地址管理 / 优惠券 / 徽章 / 积分 / 钱包。底下这几格都在同一个账号上，只是看的角度不同。
 *
 * 只有点开的那一档发请求（tab 切过去才拉，切走再切回来不重捞 —— 判据是那一块的 page 还是 0）。
 * 积分、钱包两档是「上面一行账 + 下面一张流水」两路一起拉：流水是分页列表，账户行只有一行。
 * 广场消息原来挂在这一排最后面，现在跟「个人信息」持平、单开一张页（/pages/profile/plaza）。
 */
const TABS = [
  { key: 'base', label: '基础信息' },
  { key: 'address', label: '地址管理' },
  { key: 'coupon', label: '优惠券' },
  { key: 'badge', label: '徽章' },
  { key: 'points', label: '积分' },
  { key: 'wallet', label: '钱包' },
] as const
type TabKey = (typeof TABS)[number]['key']

const tokenStore = useTokenStore()
const tab = ref<TabKey>('base')

const account = ref<IShopAccount | null>(null)
const accountLoaded = ref(false)
const accountBusy = ref(false)
const accountError = ref('')
const pointsAccount = ref<IShopPointsAccount | null>(null)
const pointsLoaded = ref(false)
const pointsError = ref('')
const walletAccount = ref<IShopWalletAccount | null>(null)
const walletLoaded = ref(false)
const walletError = ref('')

/**
 * 优惠券那两档筛选：null = 不限。「未使用」的真值恰好是 0，所以哨兵用 null 而不是 0，
 * 不用再像后台那样把候选值整体挪一格。声明在 coupons 上面：下面那条 fetch 是按这一格现取参数的。
 */
const couponStatus = ref<number | null>(null)
const couponType = ref<number | null>(null)
const STATUS_CHIPS = [{ label: '不限', value: null }, ...[0, 1, 2, 3].map(status => ({ label: COUPON_STATUS_TEXT[status], value: status }))]
const TYPE_CHIPS = [{ label: '不限', value: null }, ...[1, 2, 3, 4].map(type => ({ label: COUPON_TYPE_TEXT[type], value: type }))]

const coupons = usePagedList<IShopCouponRow>((pageNum, pageSize) =>
  getMyCoupons(pageNum, pageSize, couponStatus.value ?? undefined, couponType.value ?? undefined))
const badges = usePagedList<IShopBadgeRow>(getMyBadges)
const pointLogs = usePagedList<IShopPointsLogRow>(getMyPointsLogs)
const walletLogs = usePagedList<IShopWalletLogRow>(getMyWalletLogs)

/** 换筛选 = 回第一页整块重拉（旧那一串上接新条件的结果会混档）；两格一起改也只跑一趟 */
watch([couponStatus, couponType], () => coupons.reload())

const user = computed(() => account.value?.sysUser ?? null)

/**
 * 八排：只留账号自己填得动的那几格（后台「消费者管理 → 个人信息」上的登录时间/创建时间/
 * 账号状态/账号类型/角色这一列 App 上不展示）。空列一律画「—」，后端那份实体是 NON_NULL，
 * 没填的格干脆不在 JSON 里。
 */
const baseRows = computed(() => {
  const one = user.value
  if (!one) {
    return []
  }
  return [
    { key: 'nickname', label: '昵称', value: one.nickname || '—' },
    { key: 'username', label: '账号', value: one.username || '—' },
    { key: 'mobile', label: '手机号', value: one.mobile || '—' },
    { key: 'email', label: '邮箱', value: one.email || '—' },
    { key: 'sex', label: '性别', value: one.sex == null ? '—' : codeText(SEX_TEXT, one.sex) },
    { key: 'area', label: '所在地区', value: one.areaZh || '—' },
    { key: 'detail', label: '详细地址', value: one.address || '—' },
    { key: 'signature', label: '个性签名', value: one.signature || '—' },
  ]
})

const pointCells = computed(() => {
  const one = pointsAccount.value
  if (!one) {
    return []
  }
  return [
    { key: 'balance', label: '可用积分', value: pointsOf(one.balance), strong: true },
    { key: 'total', label: '累计获得', value: pointsOf(one.totalPoints) },
    { key: 'used', label: '累计消耗', value: pointsOf(one.usedPoints) },
    { key: 'frozen', label: '冻结中', value: pointsOf(one.frozenPoints) },
    { key: 'expire', label: '即将过期', value: pointsOf(one.expirePoints) },
    { key: 'created', label: '建账时间', value: dayOf(one.createTime) },
  ]
})

const walletCells = computed(() => {
  const one = walletAccount.value
  if (!one) {
    return []
  }
  return [
    { key: 'balance', label: '可用余额', value: moneyOf(one.balance), strong: true },
    { key: 'frozen', label: '冻结中', value: moneyOf(one.frozenBalance) },
    { key: 'gift', label: '赠送余额', value: moneyOf(one.giftBalance) },
    { key: 'recharge', label: '累计充值', value: moneyOf(one.totalRecharge) },
    { key: 'consume', label: '累计消费', value: moneyOf(one.totalConsume) },
    { key: 'withdraw', label: '累计提现', value: moneyOf(one.totalWithdraw) },
    { key: 'status', label: '账户状态', value: codeText(WALLET_STATUS_TEXT, one.status) || '—' },
    { key: 'created', label: '建账时间', value: dayOf(one.createTime) },
  ]
})

/** 券名没填、徽章名没填时的兜底（后台那两格本来就不是必填） */
function couponNameOf(row: IShopCouponRow) {
  return row.couponName || `券 ${row.id}`
}

function badgeNameOf(row: IShopBadgeRow) {
  return row.badgeName || `徽章 ${row.id}`
}

/** 一张券上那几句：过期那句优先于 status —— status 翻成 2 已过期要等有人去刷，不能只信它 */
function couponStatusText(row: IShopCouponRow) {
  return couponExpired(row) ? '已过期' : codeText(COUPON_STATUS_TEXT, row.status ?? 0)
}

function badgeValidText(row: IShopBadgeRow) {
  return row.endTime ? `${dayOf(row.endTime)} 前有效` : '长期有效'
}

/** 状态徽标的底色：能用的一律红（券未使用 / 徽章有效），其余压灰 */
function couponTone(row: IShopCouponRow) {
  return row.status === 0 && !couponExpired(row)
    ? 'bg-red-50 text-[#d14328]'
    : 'bg-gray-100 text-gray-500'
}

function badgeTone(row: IShopBadgeRow) {
  return badgeValid(row) ? 'bg-red-50 text-[#d14328]' : 'bg-gray-100 text-gray-500'
}

async function loadAccount() {
  // 请求在路上时别再来一趟（下拉刷新和「重试」那颗可能同一时刻都进来）
  if (accountBusy.value) {
    return
  }
  accountBusy.value = true
  accountError.value = ''
  try {
    account.value = await getMyAccount()
    accountLoaded.value = true
  }
  catch (err) {
    accountError.value = (err as Error).message
  }
  finally {
    accountBusy.value = false
  }
}

async function loadPoints() {
  if (pointsLoaded.value) {
    return
  }
  pointsError.value = ''
  try {
    pointsAccount.value = await getMyPointsAccount()
    pointsLoaded.value = true
  }
  catch (err) {
    // 拉失败不能当成「还没有账户」：下一趟（切回来或下拉刷新）还得再试
    pointsError.value = (err as Error).message
  }
}

async function loadWallet() {
  if (walletLoaded.value) {
    return
  }
  walletError.value = ''
  try {
    walletAccount.value = await getMyWalletAccount()
    walletLoaded.value = true
  }
  catch (err) {
    walletError.value = (err as Error).message
  }
}

/**
 * 充值 / 提现那一颗弹窗：两颗共用一份表单，靠 walletAction 分档（标题、判据、发哪条请求都跟着它走）。
 * 现成那一行账要带进弹窗 —— 提现得当场比一比可用余额；还没开账时带 null，弹窗里按 0 元说。
 * 两颗都不跟着数据走：第一次充值正是把那一行账建出来的动作，提现点开了会说清能提的是 0 元。
 */
const walletFormOpen = ref(false)
const walletAction = ref<'recharge' | 'withdraw'>('recharge')

function openWalletForm(action: 'recharge' | 'withdraw') {
  walletAction.value = action
  walletFormOpen.value = true
}

/**
 * 充 / 提成功：上面那几个数正是这一笔改的，清掉「拉过就不再拉」那道闸门重捞账户行；
 * 流水多出的那一笔排在最前面，所以整块清回首页重拉（不这么办「共 N 笔」还是旧的数）。
 */
async function onWalletChanged() {
  walletFormOpen.value = false
  walletLoaded.value = false
  await loadWallet()
  await walletLogs.reload()
  uni.showToast({ title: walletAction.value === 'recharge' ? '充值成功' : '提现成功', icon: 'success' })
}

/**
 * 地址管理那一档：整列一次读完（默认那条由 SQL 钉在第一位），不做分页控件 —— 这一列没人有五十条地址。
 * 增删改都在「地址编辑」那一页上做，回来靠 onShow 整块重拉：设默认会改到别的行，
 * 就地改一份本地数据会画出两颗默认（口径同 Web 那个 AddressManager，存完也是重拉）。
 */
const addresses = ref<IShopAddressRow[]>([])
const addressLoading = ref(false)
const addressLoaded = ref(false)
const addressError = ref('')
const addressDeleting = ref(false)

async function loadAddresses() {
  addressLoading.value = true
  addressError.value = ''
  try {
    addresses.value = (await getMyAddresses()).records
    addressLoaded.value = true
  }
  catch (err) {
    addressError.value = (err as Error).message
  }
  finally {
    addressLoading.value = false
  }
}

function toEditAddress(id?: number) {
  uni.navigateTo({ url: id ? `/pages/profile/address-edit?id=${id}` : '/pages/profile/address-edit' })
}

/** 库里是逻辑删除；删掉的正好是默认那条不自动补选，所以结算页那边要重新挑一颗 */
function askDeleteAddress(row: IShopAddressRow) {
  if (addressDeleting.value) {
    return
  }
  uni.showModal({
    title: '删除地址',
    content: `删掉「${row.receiverName} ${row.receiverPhone}」这一条？`,
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      addressDeleting.value = true
      try {
        await deleteMyAddress(row.id)
        await loadAddresses()
      }
      catch {
        // 后端那句「收货地址不存在」由 http 层弹出来，这一列保持原样
      }
      finally {
        addressDeleting.value = false
      }
    },
  })
}

/** 点开哪一档拉哪一档；积分、钱包两档是「账户行 + 流水」一起拉 */
function pick(key: TabKey) {
  tab.value = key
  if (key === 'base') {
    if (!accountLoaded.value) {
      void loadAccount()
    }
    return
  }
  if (key === 'address') {
    if (!addressLoaded.value) {
      void loadAddresses()
    }
    return
  }
  if (key === 'coupon') {
    if (!coupons.page) {
      void coupons.load()
    }
    return
  }
  if (key === 'badge') {
    if (!badges.page) {
      void badges.load()
    }
    return
  }
  if (key === 'points') {
    void loadPoints()
    if (!pointLogs.page) {
      void pointLogs.load()
    }
    return
  }
  if (key === 'wallet') {
    void loadWallet()
    if (!walletLogs.page) {
      void walletLogs.load()
    }
    return
  }
}

function toLogin() {
  uni.navigateTo({ url: LOGIN_PAGE })
}

/** 基础信息那张表在单独一页上（一屏表单塞不进这一页的 tab 里），保存回来靠下面那个 onShow 重拉 */
function toEdit() {
  uni.navigateTo({ url: '/pages/profile/edit' })
}

/** 头像点开是原图（这一格只是缩略圆，图本身可能比它大得多） */
function previewAvatar() {
  const url = user.value?.avatar
  if (url) {
    uni.previewImage({ urls: [url] })
  }
}

/** 徽章同理：有图那一档点开看原图，没配图的（早期发出去的老徽章）只能读那一行文字 */
function previewBadge(row: IShopBadgeRow) {
  if (row.badgeIcon) {
    uni.previewImage({ urls: [row.badgeIcon] })
  }
}

/** 结算页那两颗「去添加地址 / 管理地址」带 ?tab=address 进来，落点就是地址管理那一档 */
onLoad((query) => {
  if (tokenStore.updateNowTime().hasLogin) {
    const key = query?.tab
    pick(TABS.some(one => one.key === key) ? key as TabKey : 'base')
  }
})

/** 从「修改资料」「地址编辑」回来要重捞：不重拉的话，改完看到的还是旧的那几格（第一次进这一页由上面 onLoad 拉） */
onShow(() => {
  if (!tokenStore.updateNowTime().hasLogin) {
    return
  }
  if (accountLoaded.value) {
    void loadAccount()
  }
  if (addressLoaded.value) {
    void loadAddresses()
  }
})

onPullDownRefresh(async () => {
  if (tab.value === 'base') {
    await loadAccount()
  }
  else if (tab.value === 'address') {
    await loadAddresses()
  }
  else if (tab.value === 'coupon') {
    await coupons.reload()
  }
  else if (tab.value === 'badge') {
    await badges.reload()
  }
  else if (tab.value === 'points') {
    await pointLogs.reload()
  }
  else if (tab.value === 'wallet') {
    await walletLogs.reload()
  }
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  if (!tokenStore.updateNowTime().hasLogin) {
    return
  }
  if (tab.value === 'coupon' && coupons.hasMore) {
    void coupons.load()
  }
  else if (tab.value === 'badge' && badges.hasMore) {
    void badges.load()
  }
  else if (tab.value === 'points' && pointLogs.hasMore) {
    void pointLogs.load()
  }
  else if (tab.value === 'wallet' && walletLogs.hasMore) {
    void walletLogs.load()
  }
})
</script>

<template>
  <view class="min-h-screen bg-[#f5f6f7] pb-48rpx">
    <view v-if="!tokenStore.hasLogin" class="px-32rpx py-160rpx text-center">
      <view class="text-[28rpx] text-gray-400">
        登录后才能看自己的资料、券、徽章和账本
      </view>
      <wd-button class="mt-32rpx" type="error" @click="toLogin">
        去登录
      </wd-button>
    </view>

    <template v-else>
      <!-- 七档 tab：形状同门店详情页那两档（红下划线在选中那一颗上）。七颗已经排不进一屏，
           所以套一层横着滚的 scroll-view（口径同优惠券上面那两排筛选：内部 inline-flex，边距留在外面那层） -->
      <scroll-view scroll-x class="overflow-hidden whitespace-nowrap border-b border-gray-100 bg-white">
        <view class="inline-flex px-32rpx">
          <view
            v-for="one in TABS"
            :key="one.key"
            hover-class="fx-tap"
            class="fx-press mr-32rpx border-b-2 py-24rpx text-[28rpx] -mb-px last:mr-0"
            :class="tab === one.key ? 'border-[#d14328] text-[#d14328] font-medium' : 'border-transparent text-gray-500'"
            @click="pick(one.key)"
          >
            {{ one.label }}
          </view>
        </view>
      </scroll-view>

      <!-- 基础信息：头像 + 八排（一次一行，所以不分页） -->
      <view v-if="tab === 'base'" class="mt-24rpx">
        <view v-if="accountError" class="mx-32rpx rounded-lg bg-red-50 px-32rpx py-24rpx text-[26rpx] text-[#d14328]">
          {{ accountError }}
          <text hover-class="fx-tap" class="fx-press ml-16rpx underline" @click="loadAccount">重试</text>
        </view>

        <template v-else-if="user">
          <!-- 这一块自己的动作挂在它上面那一排（形状同 Web 那份基础信息的 toolbar，那颗「修改」）；
               回填要读这一块捞回来的那一行，没读到点开就是张空表，所以这一颗跟着数据走 -->
          <view class="mx-24rpx my-16rpx flex justify-end">
            <text hover-class="fx-tap" class="fx-press rounded-full bg-white px-32rpx py-12rpx text-[26rpx] text-[#d14328]" @click="toEdit">
              修改
            </text>
          </view>

          <view class="mx-24rpx my-16rpx flex rounded-xl bg-white p-32rpx">
            <image
              v-if="user.avatar"
              hover-class="fx-tap"
              :src="user.avatar"
              mode="aspectFill"
              class="fx-press h-[120rpx] w-[120rpx] shrink-0 rounded-full bg-gray-100"
              @click="previewAvatar"
            />
            <view v-else class="h-[120rpx] w-[120rpx] shrink-0 rounded-full bg-gray-100" />
            <view class="ml-24rpx min-w-0 flex-1 self-center">
              <view class="truncate text-[32rpx] text-[#333] font-medium">
                {{ user.nickname || user.username || '未命名账号' }}
              </view>
            </view>
          </view>

          <view class="mx-24rpx my-24rpx rounded-xl bg-white px-32rpx">
            <view
              v-for="row in baseRows"
              :key="row.key"
              class="flex justify-between border-b border-gray-50 py-24rpx text-[28rpx] last:border-b-0"
            >
              <text class="shrink-0 text-gray-400">{{ row.label }}</text>
              <text class="ml-32rpx min-w-0 flex-1 truncate text-right text-[#333]">{{ row.value }}</text>
            </view>
          </view>
        </template>

        <view v-else class="py-128rpx text-center text-[26rpx] text-gray-400">
          {{ accountLoaded ? '没读到账号信息' : '加载中…' }}
        </view>
      </view>

      <!-- 地址管理：一颗地址一张卡（默认那条排第一），新增/改/删都从这一格进编辑那一页 -->
      <view v-else-if="tab === 'address'" class="mt-24rpx">
        <view v-if="addressError" class="mx-32rpx rounded-lg bg-red-50 px-32rpx py-24rpx text-[26rpx] text-[#d14328]">
          {{ addressError }}
          <text hover-class="fx-tap" class="fx-press ml-16rpx underline" @click="loadAddresses">重试</text>
        </view>

        <template v-else>
          <view class="mx-24rpx my-16rpx flex justify-end">
            <text hover-class="fx-tap" class="fx-press rounded-full bg-white px-32rpx py-12rpx text-[26rpx] text-[#d14328]" @click="toEditAddress()">
              新增地址
            </text>
          </view>

          <view v-if="addressLoading && !addresses.length" class="py-128rpx text-center text-[26rpx] text-gray-400">
            加载中…
          </view>

          <template v-else>
            <view
              v-for="row in addresses"
              :key="row.id"
              hover-class="fx-tap"
              class="fx-press mx-24rpx mb-24rpx rounded-xl bg-white px-32rpx py-24rpx"
              @click="toEditAddress(row.id)"
            >
              <view class="flex items-center">
                <text class="min-w-0 flex-1 truncate text-[30rpx] text-[#333] font-medium">
                  {{ row.receiverName }}
                </text>
                <text class="ml-16rpx shrink-0 text-[26rpx] text-gray-500">
                  {{ row.receiverPhone }}
                </text>
              </view>

              <view class="mt-8rpx text-[26rpx] text-gray-600 leading-[36rpx]">
                <text v-if="row.areaZh">{{ row.areaZh }} </text>{{ row.detail }}
              </view>

              <view class="mt-16rpx flex items-center">
                <text
                  v-if="row.isDefault === 1"
                  class="mr-16rpx shrink-0 rounded bg-[#fdece8] px-12rpx py-4rpx text-[22rpx] text-[#d14328]"
                >
                  默认
                </text>
                <text
                  v-if="row.tag"
                  class="mr-16rpx shrink-0 rounded bg-gray-100 px-12rpx py-4rpx text-[22rpx] text-gray-600"
                >
                  {{ row.tag }}
                </text>
                <text v-if="row.longitude == null || row.latitude == null" class="min-w-0 flex-1 truncate text-[22rpx] text-gray-400">
                  {{ String(row.areaCode || '').trim() ? '没填坐标，下单按这一条选的地区匹仓库' : '没填坐标也没选地区，这一条下不了单' }}
                </text>
                <view v-else class="flex-1" />
                <text hover-class="fx-tap" class="fx-press shrink-0 text-[26rpx] text-[#333]" @click.stop="toEditAddress(row.id)">
                  修改
                </text>
                <text hover-class="fx-tap" class="fx-press ml-32rpx shrink-0 text-[26rpx] text-[#d14328]" @click.stop="askDeleteAddress(row)">
                  删除
                </text>
              </view>
            </view>

            <view v-if="!addresses.length" class="mx-24rpx mb-24rpx rounded-xl bg-white px-32rpx py-96rpx text-center">
              <view class="text-[28rpx] text-gray-500">
                还没有收货地址
              </view>
              <view class="mt-8rpx text-[24rpx] text-gray-400">
                下单前要挑一条，配送费和就近发货的仓都按地址算
              </view>
            </view>
          </template>
        </template>
      </view>

      <!-- 优惠券：两档筛选摆在券上面（它们换的就是底下这一串），一张券一行 -->
      <view v-else-if="tab === 'coupon'" class="pb-16rpx">
        <!-- 两档筛选各占一横排：scroll-view 自带 width:100%，边距套在它们外面这一层才撑不出屏幕 -->
        <view class="mx-24rpx my-24rpx">
          <scroll-view scroll-x class="overflow-hidden whitespace-nowrap rounded-xl bg-white">
            <view class="inline-flex px-24rpx py-16rpx">
              <!-- 这一格必须是 view 不能是 text：uni-app 的 uni-text 是 white-space: pre-line，
                   模板里那两下换行会被当真换行渲染，胶囊就变成两行高 -->
              <view
                v-for="one in STATUS_CHIPS"
                :key="one.label"
                hover-class="fx-tap"
                class="fx-press mr-16rpx shrink-0 rounded-full px-24rpx py-8rpx text-[24rpx]"
                :class="couponStatus === one.value ? 'bg-[#d14328] text-white' : 'bg-gray-100 text-gray-600'"
                @click="couponStatus = one.value"
              >
                {{ one.label }}
              </view>
            </view>
          </scroll-view>
          <scroll-view scroll-x class="mt-16rpx overflow-hidden whitespace-nowrap rounded-xl bg-white">
            <view class="inline-flex px-24rpx py-16rpx">
              <view
                v-for="one in TYPE_CHIPS"
                :key="one.label"
                hover-class="fx-tap"
                class="fx-press mr-16rpx shrink-0 rounded-full px-24rpx py-8rpx text-[24rpx]"
                :class="couponType === one.value ? 'bg-[#d14328] text-white' : 'bg-gray-100 text-gray-600'"
                @click="couponType = one.value"
              >
                {{ one.label }}
              </view>
            </view>
          </scroll-view>
        </view>

        <ListState
          class="mt-24rpx"
          :loading="coupons.loading"
          :error="coupons.error"
          :count="coupons.rows.length"
          :has-more="coupons.hasMore"
          empty-text="手上还没有优惠券"
          @retry="coupons.load()"
        >
          <view
            v-for="row in coupons.rows"
            :key="row.id"
            class="mx-24rpx my-24rpx rounded-xl bg-white p-32rpx"
          >
            <view class="flex items-start justify-between">
              <text class="min-w-0 flex-1 truncate text-[30rpx] text-[#333] font-medium">
                {{ couponNameOf(row) }}
              </text>
              <text class="ml-16rpx shrink-0 rounded px-16rpx py-4rpx text-[22rpx]" :class="couponTone(row)">
                {{ couponStatusText(row) }}
              </text>
            </view>
            <view class="mt-16rpx flex items-baseline justify-between">
              <text class="text-[30rpx] text-[#d14328]">
                {{ couponBenefit(row) }}
              </text>
              <text v-if="row.expireTime" class="text-[22rpx] text-gray-400">
                {{ dayOf(row.expireTime) }} 前有效
              </text>
            </view>
            <view class="mt-16rpx text-[22rpx] text-gray-400">
              {{ codeText(COUPON_TYPE_TEXT, row.couponType) || '优惠券' }} · {{ dayOf(row.receiveTime) }} 到手
              <template v-if="row.getWay != null">
                · {{ codeText(GET_WAY_TEXT, row.getWay) }}
              </template>
              <template v-if="row.useTime">
                · {{ dayOf(row.useTime) }} 用过
              </template>
              <template v-if="row.orderNo">
                · {{ row.orderNo }}
              </template>
            </view>
          </view>
        </ListState>
      </view>

      <!-- 徽章：一枚一行（形状照上面优惠券那一格）；名称/文字/图标读的都是发放那一刻的快照 -->
      <view v-else-if="tab === 'badge'">
        <ListState
          class="mt-24rpx"
          :loading="badges.loading"
          :error="badges.error"
          :count="badges.rows.length"
          :has-more="badges.hasMore"
          empty-text="还没有拿到徽章"
          @retry="badges.load()"
        >
          <view
            v-for="row in badges.rows"
            :key="row.id"
            class="mx-24rpx my-24rpx flex rounded-xl bg-white p-32rpx"
          >
            <image
              v-if="row.badgeIcon"
              hover-class="fx-tap"
              :src="row.badgeIcon"
              mode="aspectFill"
              class="fx-press h-[88rpx] w-[88rpx] shrink-0 rounded-full bg-gray-50"
              @click="previewBadge(row)"
            />
            <view
              v-else
              class="h-[88rpx] w-[88rpx] shrink-0 break-all rounded-full bg-gray-100 text-center text-[20rpx] text-gray-500 leading-[24rpx]"
            >
              {{ row.badgeText || badgeNameOf(row) }}
            </view>
            <view class="ml-24rpx min-w-0 flex-1 self-center">
              <view class="flex items-start justify-between">
                <text class="min-w-0 flex-1 truncate text-[30rpx] text-[#333] font-medium">
                  {{ badgeNameOf(row) }}
                </text>
                <text class="ml-16rpx shrink-0 rounded px-16rpx py-4rpx text-[22rpx]" :class="badgeTone(row)">
                  {{ badgeStatusText(row) }}
                </text>
              </view>
              <view class="mt-16rpx text-[22rpx] text-gray-400">
                {{ codeText(BADGE_TYPE_TEXT, row.badgeType) || '徽章' }}
                <template v-if="row.source != null">
                  · {{ codeText(BADGE_SOURCE_TEXT, row.source) }}
                </template>
                · {{ dayOf(row.startTime) }} 拿到 · {{ badgeValidText(row) }}
              </view>
            </view>
          </view>
        </ListState>
      </view>

      <!-- 积分：上面一块是那本账的五个数（库里存的，不做现算），下面一张流水表 -->
      <view v-else-if="tab === 'points'">
        <view v-if="pointsAccount" class="mx-24rpx my-24rpx rounded-xl bg-white px-32rpx py-24rpx">
          <view class="grid grid-cols-2">
            <view v-for="cell in pointCells" :key="cell.key" class="py-16rpx">
              <view class="text-[22rpx] text-gray-400">
                {{ cell.label }}
              </view>
              <view class="mt-8rpx text-[28rpx] text-[#333]" :class="cell.strong ? 'text-[#d14328] font-medium' : ''">
                {{ cell.value }}
              </view>
            </view>
          </view>
        </view>
        <!-- 账户行是由第一次积分变动带出来的，一条都没获得过就没有这一行，不是数据丢了；拉失败另说一句 -->
        <view v-else class="mx-24rpx my-24rpx rounded-xl bg-white px-32rpx py-48rpx text-center text-[26rpx] text-gray-400">
          <template v-if="pointsError">
            {{ pointsError }}
            <text hover-class="fx-tap" class="fx-press ml-16rpx underline" @click="loadPoints">重试</text>
          </template>
          <template v-else>
            {{ pointsLoaded ? '还没有积分账户' : '加载中…' }}
          </template>
        </view>

        <ListState
          class="mt-24rpx"
          :loading="pointLogs.loading"
          :error="pointLogs.error"
          :count="pointLogs.rows.length"
          :has-more="pointLogs.hasMore"
          empty-text="还没有积分变动"
          @retry="pointLogs.load()"
        >
          <view class="mx-24rpx my-24rpx rounded-xl bg-white px-32rpx">
            <view
              v-for="row in pointLogs.rows"
              :key="row.id"
              class="flex items-center border-b border-gray-50 py-24rpx text-[26rpx] last:border-b-0"
            >
              <view class="min-w-0 flex-1">
                <view class="truncate text-[#333]">
                  {{ codeText(POINTS_BIZ_TEXT, row.bizType ?? 0) || '积分变动' }}
                </view>
                <view class="mt-8rpx truncate text-[22rpx] text-gray-400">
                  {{ dayOf(row.createTime) }}
                  <template v-if="row.description">
                    · {{ row.description }}
                  </template>
                  <template v-if="row.bizNo">
                    · {{ row.bizNo }}
                  </template>
                </view>
              </view>
              <text class="ml-16rpx shrink-0 text-[28rpx]" :class="(row.points ?? 0) < 0 ? 'text-gray-500' : 'text-[#d14328]'">
                {{ signedPoints(row.points) }}
              </text>
              <text class="ml-24rpx w-[110rpx] shrink-0 text-right text-[22rpx] text-gray-400">
                余 {{ pointsOf(row.balance) }}
              </text>
            </view>
          </view>
        </ListState>
      </view>

      <!-- 钱包：形状同上面积分那一格（账户几排 + 流水表），只是数的是钱 -->
      <view v-else-if="tab === 'wallet'">
        <!-- 钱进钱出的两颗挂在那本账上面（口径同 Web 端个人信息 → 钱包那一格 toolbar） -->
        <view class="mx-24rpx my-16rpx flex justify-end gap-16rpx">
          <text hover-class="fx-tap" class="fx-press rounded-full bg-white px-32rpx py-12rpx text-[26rpx] text-gray-600" @click="openWalletForm('withdraw')">
            提现
          </text>
          <text hover-class="fx-tap" class="fx-press rounded-full bg-[#d14328] px-32rpx py-12rpx text-[26rpx] text-white" @click="openWalletForm('recharge')">
            充值
          </text>
        </view>

        <view v-if="walletAccount" class="mx-24rpx my-24rpx rounded-xl bg-white px-32rpx py-24rpx">
          <view class="grid grid-cols-2">
            <view v-for="cell in walletCells" :key="cell.key" class="py-16rpx">
              <view class="text-[22rpx] text-gray-400">
                {{ cell.label }}
              </view>
              <view class="mt-8rpx text-[28rpx] text-[#333]" :class="cell.strong ? 'text-[#d14328] font-medium' : ''">
                {{ cell.value }}
              </view>
            </view>
          </view>
        </view>
        <view v-else class="mx-24rpx my-24rpx rounded-xl bg-white px-32rpx py-48rpx text-center text-[26rpx] text-gray-400">
          <template v-if="walletError">
            {{ walletError }}
            <text hover-class="fx-tap" class="fx-press ml-16rpx underline" @click="loadWallet">重试</text>
          </template>
          <template v-else>
            {{ walletLoaded ? '还没有钱包账户' : '加载中…' }}
          </template>
        </view>

        <ListState
          class="mt-24rpx"
          :loading="walletLogs.loading"
          :error="walletLogs.error"
          :count="walletLogs.rows.length"
          :has-more="walletLogs.hasMore"
          empty-text="还没有余额变动"
          @retry="walletLogs.load()"
        >
          <view class="mx-24rpx my-24rpx rounded-xl bg-white px-32rpx">
            <view
              v-for="row in walletLogs.rows"
              :key="row.id"
              class="flex items-center border-b border-gray-50 py-24rpx text-[26rpx] last:border-b-0"
            >
              <view class="min-w-0 flex-1">
                <view class="truncate text-[#333]">
                  {{ codeText(WALLET_BIZ_TEXT, row.bizType ?? 0) || '余额变动' }}
                </view>
                <view class="mt-8rpx truncate text-[22rpx] text-gray-400">
                  {{ dayOf(row.createTime) }}
                  <template v-if="row.description">
                    · {{ row.description }}
                  </template>
                  <template v-if="row.bizNo">
                    · {{ row.bizNo }}
                  </template>
                </view>
              </view>
              <text class="ml-16rpx shrink-0 text-[28rpx]" :class="(row.amount ?? 0) < 0 ? 'text-gray-500' : 'text-[#d14328]'">
                {{ signedMoney(row.amount) }}
              </text>
              <text class="ml-24rpx w-[140rpx] shrink-0 text-right text-[22rpx] text-gray-400">
                余 {{ moneyOf(row.balance) }}
              </text>
            </view>
          </view>
        </ListState>
      </view>

      <!-- 充值 / 提现那两颗推出来的弹窗：只在钱包那一档被点开，挂在这里保证它在 tab 之外、不受切档影响 -->
      <WalletChangePopup
        v-model="walletFormOpen"
        :action="walletAction"
        :account="walletAccount"
        @saved="onWalletChanged"
      />
    </template>
  </view>
</template>
