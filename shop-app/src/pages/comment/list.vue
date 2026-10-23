<script lang="ts" setup>
import CommentSection from '@/components/CommentSection.vue'

defineOptions({
  name: 'CommentList',
})

definePage({
  style: {
    navigationBarTitleText: '全部评论',
    onReachBottomDistance: 100,
  },
})

/**
 * 详情页底下那颗「查看全部 N 条评论」跳到这里：同一份 CommentSection，只是不给 preview，
 * 于是排序、输入框、翻页都回来了。这一页只做一件事 —— 把 URL 上那几个参数收下来交给组件。
 *
 * highlight 是消息里点进来的那一条（回复/点赞的 businessId 指的是评论本身）：组件会往下捞到它并高亮一下。
 * 从详情页「查看全部」那颗进来的没有这一格，正常停在最前面。
 */
const commentType = ref(0)
const businessId = ref(0)
const highlightId = ref<number | undefined>()

onLoad((query) => {
  const type = Number(query?.type)
  const id = Number(query?.id)
  // commentType + businessId 必须一起给（business_id 一列指三张表，少了类型会撞号），少一条就不捞
  if (!type || !id) {
    uni.showToast({ title: '评论参数不对', icon: 'none' })
    return
  }
  commentType.value = type
  businessId.value = id
  const highlight = Number(query?.highlight)
  highlightId.value = highlight > 0 ? highlight : undefined
})
</script>

<template>
  <view v-if="businessId" class="min-h-screen bg-white px-32rpx py-32rpx">
    <CommentSection :comment-type="commentType" :business-id="businessId" :highlight-id="highlightId" />
  </view>
  <view v-else class="py-240rpx text-center text-[26rpx] text-gray-400">
    加载中…
  </view>
</template>
