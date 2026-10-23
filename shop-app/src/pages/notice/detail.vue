<script lang="ts" setup>
import type { IShopNoticeDetail } from '@/api/types/shop'
import { getNoticeDetail } from '@/api/notice'

defineOptions({
  name: 'NoticeDetail',
})

definePage({
  style: {
    navigationBarTitleText: '公告详情',
    enablePullDownRefresh: true,
  },
})

/**
 * 公告详情页：从「我的消息」点公告那一条进来（消息列表里那一格在 service 就剥成纯文本了，全文只有这一页有）。
 *
 * 富文本这一格是后台 WangEditor 出来的 HTML，接口又不要登录 —— 只走 <rich-text>：它按自己的标签白名单建树，
 * script 和 onxxx 事件属性都进不去，等于 Web 端那道 sanitizeRichText 的作用（口径同活动详情页）。
 * 它又不吃外部 CSS，图片按原尺寸会把屏幕撑爆，所以只能就地钉一个行内样式。
 */
const noticeId = ref(0)
const notice = ref<IShopNoticeDetail | null>(null)
const loadError = ref('')

const contentNodes = computed(() => {
  const raw = notice.value?.content?.trim() ?? ''
  return raw ? raw.replace(/<img(\s|>)/gi, '<img style="max-width:100%;display:block;"$1') : ''
})

async function loadDetail() {
  try {
    notice.value = await getNoticeDetail(noticeId.value)
    loadError.value = ''
    if (notice.value.title) {
      uni.setNavigationBarTitle({ title: notice.value.title })
    }
  }
  catch {
    // 撤下了、根本没这个 id：后端回同一句话，http 层已经 toast
    loadError.value = '公告不存在或已撤回'
    notice.value = null
  }
}

onLoad((query) => {
  noticeId.value = Number(query?.id) || 0
  if (!noticeId.value) {
    loadError.value = '参数不对，没拿到公告 id'
    return
  }
  loadDetail()
})

onPullDownRefresh(async () => {
  if (noticeId.value) {
    await loadDetail()
  }
  uni.stopPullDownRefresh()
})
</script>

<template>
  <view class="min-h-screen bg-[#f5f6f7] pb-48rpx pt-24rpx">
    <view v-if="loadError" class="py-160rpx text-center text-[26rpx] text-gray-400">
      {{ loadError }}
    </view>

    <template v-else-if="notice">
      <view class="mx-24rpx mb-24rpx rounded-xl bg-white px-32rpx py-32rpx">
        <view class="text-[36rpx] text-[#333] font-medium">
          {{ notice.title || '（没有标题）' }}
        </view>
        <view v-if="notice.createTime" class="mt-16rpx text-[24rpx] text-gray-400">
          {{ notice.createTime }}
        </view>
      </view>

      <view v-if="contentNodes" class="mx-24rpx rounded-xl bg-white p-32rpx">
        <rich-text class="text-[28rpx] text-[#444] leading-56rpx" :nodes="contentNodes" />
      </view>
      <view v-else class="py-128rpx text-center text-[26rpx] text-gray-400">
        这条公告还没写正文
      </view>
    </template>

    <view v-else class="py-128rpx text-center text-[26rpx] text-gray-400">
      加载中…
    </view>
  </view>
</template>
