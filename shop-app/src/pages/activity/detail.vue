<script lang="ts" setup>
import type { IShopActivityDetail } from '@/api/types/shop'
import { ACTIVITY_EVENT_CLICK, ACTIVITY_EVENT_VIEW, getActivityDetail, trackActivity } from '@/api/activity'
import { activityLabel, activityTarget, activityTimeText, openExternal } from '@/utils/activity'

defineOptions({
  name: 'ActivityDetail',
})

definePage({
  style: {
    navigationBarTitleText: '活动详情',
    enablePullDownRefresh: true,
  },
})

const activityId = ref(0)
const detail = ref<IShopActivityDetail | null>(null)
const loadError = ref('')

/** 标题没填回落活动名称，两格都空着给个兜底名 —— 和搜索页那一 tab 同一个说法 */
const heading = computed(() => (detail.value ? activityLabel(detail.value) : ''))
const timeText = computed(() => (detail.value ? activityTimeText(detail.value) : ''))

/**
 * 跳转目标只有这一页摆得清（1 商品 2 分类 4 外链，5/6 拼不出目标就是点不动），
 * 三档分开回来：站内 url 直接 navigateTo，分类带去搜索页捞商品，外链走 openExternal（小程序里只能复制）。
 */
const target = computed(() => (detail.value ? activityTarget(detail.value) : {}))
const actionText = computed(() => {
  if (target.value.url)
    return '去看看'
  if (target.value.categoryId)
    return '看这个分类的商品'
  if (target.value.external)
    return '打开链接'
  return ''
})

/**
 * 富文本这一格是后台自由文本，接口又不要登录 —— 只走 <rich-text>：它按自己的标签白名单建树，
 * script 和 onxxx 事件属性都进不去，等于 Web 端那道 sanitizeRichText 的作用。
 * 它又不吃外部 CSS，图片按原尺寸会把屏幕撑爆，所以只能就地钉一个行内样式。
 */
const contentNodes = computed(() => {
  const raw = detail.value?.content?.trim() ?? ''
  return raw ? raw.replace(/<img(\s|>)/gi, '<img style="max-width:100%;display:block;"$1') : ''
})

/** 主图点开是系统预览器；模板里不能直接写 uni.xxx（小程序那一档解析不到全局），所以收进函数 */
function previewMain() {
  const url = detail.value?.image
  if (url) {
    uni.previewImage({ urls: [url] })
  }
}

function onAction() {
  const one = detail.value
  if (!one) {
    return
  }
  // 点这一颗就是「这条活动被人当真点了」，和轮播上那一次点击记同一档；发出去就不管回包
  trackActivity(one.id, ACTIVITY_EVENT_CLICK)
  const shot = target.value
  if (shot.url) {
    uni.navigateTo({ url: shot.url })
    return
  }
  if (shot.categoryId) {
    uni.navigateTo({ url: `/pages/search/search?tab=product&categoryId=${shot.categoryId}` })
    return
  }
  if (shot.external) {
    openExternal(shot.external)
  }
}

async function loadDetail() {
  try {
    detail.value = await getActivityDetail(activityId.value)
    // 这一页只装得下这一条活动，捞回来就算一次曝光（报数失败不该把页面变成红框，trackActivity 里已经吞掉）
    trackActivity(detail.value.id, ACTIVITY_EVENT_VIEW)
    uni.setNavigationBarTitle({ title: activityLabel(detail.value) })
  }
  catch {
    // 撤下了、结束了、根本没这个 id：后端回同一句话，http 层已经 toast
    loadError.value = '活动不存在或没在投放中'
    detail.value = null
  }
}

onLoad((query) => {
  activityId.value = Number(query?.id) || 0
  if (!activityId.value) {
    loadError.value = '参数不对，没拿到活动 id'
    return
  }
  loadDetail()
})

onPullDownRefresh(async () => {
  await loadDetail()
  uni.stopPullDownRefresh()
})
</script>

<template>
  <view class="bg-[#f5f6f7] pb-192rpx pt-24rpx">
    <view v-if="loadError" class="py-160rpx text-center text-[26rpx] text-gray-400">
      {{ loadError }}
    </view>

    <template v-else-if="detail">
      <view class="mx-24rpx mb-24rpx rounded-xl bg-white px-32rpx py-32rpx">
        <view class="text-[36rpx] text-[#333] font-medium">
          {{ heading }}
        </view>
        <view v-if="timeText" class="mt-16rpx text-[24rpx] text-gray-400">
          {{ timeText }}
        </view>
      </view>

      <!-- 主图和视频各占一整块：配了视频就上下排，不做成二选一（运营常是图先上、视频后补） -->
      <image
        v-if="detail.image"
        hover-class="fx-tap"
        :src="detail.image"
        mode="widthFix"
        class="fx-press mt-24rpx w-full bg-white"
        @click="previewMain"
      />
      <video
        v-if="detail.videoUrl"
        :src="detail.videoUrl"
        class="mt-24rpx h-[420rpx] w-full bg-black"
      />

      <view v-if="actionText" class="mx-24rpx mb-24rpx">
        <wd-button block type="error" @click="onAction">
          {{ actionText }}
        </wd-button>
      </view>

      <view v-if="contentNodes" class="mx-24rpx mb-24rpx rounded-xl bg-white p-32rpx">
        <rich-text class="text-[28rpx] text-[#444] leading-56rpx" :nodes="contentNodes" />
      </view>
      <view v-else class="py-128rpx text-center text-[26rpx] text-gray-400">
        这条活动没有正文
      </view>
    </template>

    <view v-else class="py-128rpx text-center text-[26rpx] text-gray-400">
      加载中…
    </view>
  </view>
</template>
