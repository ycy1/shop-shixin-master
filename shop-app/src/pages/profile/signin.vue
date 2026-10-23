<script lang="ts" setup>
import type { IShopSigninRow } from '@/api/types/shop'
import { getMySignins } from '@/api/profile'
import { signinMakeup, signinToday } from '@/api/activity'
import { LOGIN_PAGE } from '@/router/config'
import { useTokenStore } from '@/store/token'
import { todayText } from '@/utils/profile'

/**
 * 签到记录：一个月历 + 漏签那几天的补签（形状照 shop-web 的 views/user/SigninCalendar.vue，从弹窗搬成一张页）。
 *
 * 读走 App 那条转手（GET /api/app/shop/profile/signin/list?month=yyyy-MM），一次给全当月签过的日子、没签的不给行；
 * 写两条都是两端共用的动作，留 /api/comm：签今天不带参数，补签只认 signDate（能补的永远是自己那本账）。
 *
 * 「只能补最近 7 天」服务端有这一道闸，这里收同一颗数（SELF_MAKEUP_DAYS）只为不把按钮挂在点下去必然弹红的格子上；
 * 今天那一格不给补 —— 那一天还没过完，服务端会说「要签的话请到前台自己签」，所以它走「签今天」那一颗。
 * 补签给的分和正常签到一样多，累计天数也算一天，所以点完就得整月重捞（分数、连续天数都是服务端算的）。
 */

defineOptions({
  name: 'ProfileSignin',
})

definePage({
  style: {
    navigationBarTitleText: '签到记录',
    enablePullDownRefresh: true,
  },
})

/** 和后端 ShopSigninServiceImpl#makeupSelf 钉的同一颗数 */
const SELF_MAKEUP_DAYS = 7

const tokenStore = useTokenStore()
const today = todayText()
const month = ref(today.slice(0, 7))
const rows = ref<IShopSigninRow[]>([])
const loading = ref(false)
const submitting = ref('')
const loadError = ref('')

const records = computed(() => {
  const map: Record<string, IShopSigninRow> = {}
  for (const row of rows.value) {
    if (row.signDate) {
      map[row.signDate] = row
    }
  }
  return map
})

const signedDays = computed(() => Object.keys(records.value).length)
const makeupDays = computed(() => rows.value.filter(row => Number(row.signType) === 2).length)
const monthPoints = computed(() => rows.value.reduce((sum, row) => sum + Number(row.points || 0), 0))
/** 连签几天：服务端从最晚一天往前回数（最多 7 天），同一个数盖在返回的每一行上，取任意一行读它都行 */
const continuousDays = computed(() => Number(rows.value[0]?.currentContinuous ?? 0))
/**
 * 今天签没签：这一句和「正看着哪一个月」不相干 —— 捞回来的是那一个月的行，翻到没有今天的那个月，
 * 读 records 就会跟着清成没签，那颗按钮从「今天已经签过了」退回「签今天」，点下去必被服务端拒。
 * 所以只钉成真、不往回放（口径同 Web 那份日历）。
 */
const signedToday = ref(false)

const weekdayTexts = ['一', '二', '三', '四', '五', '六', '日']

function pad(value: number) {
  return String(value).padStart(2, '0')
}

/** 月初前面补几格空位（周一开头，和后台那页一致）；邻月的日子压根不画 —— 这一页按归属月捞记录 */
const cells = computed(() => {
  const [year, m] = month.value.split('-').map(Number)
  if (!year || !m) {
    return []
  }
  const leading = (new Date(year, m - 1, 1).getDay() + 6) % 7
  const total = new Date(year, m, 0).getDate()
  const list: (string | null)[] = []
  for (let i = 0; i < leading; i += 1) {
    list.push(null)
  }
  for (let d = 1; d <= total; d += 1) {
    list.push(`${year}-${pad(m)}-${pad(d)}`)
  }
  return list
})

/** 补签窗口的最早那一天：'yyyy-MM-dd' 定长串，字典序比就是日期序 */
const earliestMakeup = computed(() => {
  const from = new Date()
  from.setDate(from.getDate() - SELF_MAKEUP_DAYS)
  return `${from.getFullYear()}-${pad(from.getMonth() + 1)}-${pad(from.getDate())}`
})

function canMakeup(day: string) {
  return day < today && day >= earliestMakeup.value && !records.value[day]
}

async function loadMonth() {
  if (!tokenStore.updateNowTime().hasLogin) {
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    rows.value = (await getMySignins(month.value)) ?? []
    if (rows.value.some(one => one.signDate === today)) {
      signedToday.value = true
    }
  }
  catch (err) {
    loadError.value = (err as Error).message
  }
  finally {
    loading.value = false
  }
}

function shiftMonth(step: number) {
  const [year, m] = month.value.split('-').map(Number)
  const next = new Date(year, m - 1 + step, 1)
  month.value = `${next.getFullYear()}-${pad(next.getMonth() + 1)}`
}

function backToCurrent() {
  month.value = today.slice(0, 7)
}

/** 换月就重捞（月份是接口的一格参数，不是在前端那串行里过滤） */
watch(month, () => {
  void loadMonth()
})

/** 签今天 / 补签都只干一件事：把这一格补上，然后整月重捞 —— 给多少分、连签几天都由服务端算 */
async function act(kind: 'today' | string) {
  if (submitting.value) {
    return
  }
  submitting.value = kind
  try {
    const row = kind === 'today' ? await signinToday() : await signinMakeup(kind)
    uni.showToast({
      title: kind === 'today'
        ? `签到成功：连续第 ${row?.currentContinuous ?? 0} 天，+${row?.points ?? 0} 分`
        : `已补签 ${kind.slice(5)}，+${row?.points ?? 0} 分`,
      icon: 'none',
    })
    await loadMonth()
  }
  catch {
    // 拒回来的那句（这一天已经签过了、还没过完、请先登录）由 http 层 toast，这里不另写一套
  }
  finally {
    submitting.value = ''
  }
}

function toLogin() {
  uni.navigateTo({ url: LOGIN_PAGE })
}

onShow(() => {
  void loadMonth()
})

onPullDownRefresh(async () => {
  await loadMonth()
  uni.stopPullDownRefresh()
})
</script>

<template>
  <view class="min-h-screen bg-[#f5f6f7] pb-48rpx pt-24rpx">
    <view v-if="!tokenStore.hasLogin" class="px-32rpx py-160rpx text-center">
      <view class="text-[28rpx] text-gray-400">
        登录后才能看自己的签到记录
      </view>
      <wd-button class="mt-32rpx" type="error" @click="toLogin">
        去登录
      </wd-button>
    </view>

    <template v-else>
      <!-- 这一本账上当月的三个数 + 连签：都是服务端算好的，页面只数它给的那几行 -->
      <view class="mx-24rpx mb-24rpx rounded-xl bg-white px-32rpx py-32rpx">
        <view class="flex items-baseline justify-between">
          <text class="text-[30rpx] text-[#333] font-medium">本月签到</text>
          <text class="text-[26rpx] text-[#d14328]">连签 {{ continuousDays }} 天</text>
        </view>
        <view class="mt-16rpx flex text-[24rpx] text-gray-500">
          <text>签了 {{ signedDays }} 天</text>
          <text class="ml-24rpx">补签 {{ makeupDays }} 天</text>
          <text class="ml-24rpx">共 {{ monthPoints }} 分</text>
        </view>
      </view>

      <view v-if="loadError" class="mx-24rpx mb-24rpx rounded-lg bg-red-50 px-24rpx py-16rpx text-[26rpx] text-[#d14328]">
        {{ loadError }}
        <text hover-class="fx-tap" class="fx-press ml-16rpx underline" @click="loadMonth()">重试</text>
      </view>

      <view class="mx-24rpx mb-24rpx rounded-xl bg-white px-32rpx py-32rpx">
        <!-- 翻月：月份是接口的一格参数，往左往右各挪一个月；不在当月时给一颗回到本月 -->
        <view class="flex items-center justify-between">
          <text hover-class="fx-tap" class="fx-press px-16rpx text-[30rpx] text-gray-400" @click="shiftMonth(-1)">‹</text>
          <view class="flex items-center">
            <text class="text-[30rpx] text-[#333] font-medium">{{ month.replace('-', '年') }}月</text>
            <text
              v-if="month !== today.slice(0, 7)"
              hover-class="fx-tap"
              class="fx-press ml-16rpx rounded-full bg-gray-100 px-16rpx py-4rpx text-[22rpx] text-gray-500"
              @click="backToCurrent"
            >
              回到本月
            </text>
          </view>
          <text hover-class="fx-tap" class="fx-press px-16rpx text-[30rpx] text-gray-400" @click="shiftMonth(1)">›</text>
        </view>

        <view class="mt-24rpx flex">
          <text v-for="one in weekdayTexts" :key="one" class="w-1/7 text-center text-[22rpx] text-gray-400">
            {{ one }}
          </text>
        </view>

        <view class="mt-8rpx flex flex-wrap">
          <view v-for="(day, index) in cells" :key="index" class="box-border w-1/7 px-4rpx py-8rpx">
            <!-- 空格（月初前面的补位）占一格不画内容：一格的高度别跟着格子数跳 -->
            <view v-if="!day" class="h-[104rpx]" />
            <view
              v-else
              class="h-[104rpx] flex flex-col items-center justify-center rounded-lg"
              :class="[
                records[day] ? 'bg-[#f0f7f0]' : 'bg-gray-50',
                day === today ? 'border border-[#d14328]' : '',
              ]"
            >
              <text class="text-[24rpx]" :class="day === today ? 'text-[#d14328] font-medium' : 'text-gray-500'">
                {{ Number(day.slice(8)) }}
              </text>
              <text
                v-if="records[day]"
                class="mt-4rpx text-[18rpx]"
                :class="Number(records[day].signType) === 2 ? 'text-amber-600' : 'text-green-600'"
              >
                {{ Number(records[day].signType) === 2 ? '补签' : '已签' }}
              </text>
              <text v-else-if="canMakeup(day)" hover-class="fx-tap" class="fx-press mt-4rpx text-[18rpx] text-[#d14328]" @click="act(day)">
                补签
              </text>
              <text v-if="records[day]" class="text-[18rpx] text-gray-400">
                +{{ records[day].points ?? 0 }} 分
              </text>
            </view>
          </view>
        </view>

        <view class="mt-32rpx text-[22rpx] text-gray-400 leading-[34rpx]">
          点最近 {{ SELF_MAKEUP_DAYS }} 天内没签的过去日子即可补签，补签给的分和正常签到一样；更早的日子就补不了了。
        </view>
      </view>

      <view class="mx-24rpx mb-24rpx">
        <wd-button block type="error" size="large" :loading="submitting === 'today'" :disabled="signedToday || Boolean(submitting)" @click="act('today')">
          {{ signedToday ? '今天已经签过了' : '签今天' }}
        </wd-button>
      </view>
    </template>
  </view>
</template>
