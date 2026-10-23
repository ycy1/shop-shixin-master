<script lang="ts" setup>
/**
 * 一块列表的四种状态 + 尾巴那一行，抽出来一份。
 *
 * 个人信息里四个 tab、我的订单、收藏、足迹画的是同一件事：加载中 / 错在哪（给一颗重试）/ 空态 /
 * 有行就画行、末尾说清还能不能往下翻。判据口径同搜索页那一块（空数据也算加载过，重试点了才重捞）。
 * 行本身由调用方通过默认插槽给进来 —— 每一块的行形状各不相同，只有外面这层壳是一样的。
 */
defineOptions({
  name: 'ListState',
})

const props = defineProps<{
  loading: boolean
  error: string
  /** 已经捞到几行了：有行时错误/空态都不出现（半途失败留着旧那一串，只说末尾那句） */
  count: number
  /** 还能不能往下翻（已捞到的 < 服务端报的总页数那一档，由调用方按同一口径算好递进来） */
  hasMore: boolean
  emptyText: string
}>()

const emit = defineEmits<{
  (e: 'retry'): void
}>()

function retry() {
  emit('retry')
}

const moreText = computed(() => (props.hasMore ? '上滑看更多' : '没有更多了'))
</script>

<template>
  <!-- 单根：四种状态本来是并列的，但 Vue 对多根组件不透传 class，
       调用方写的 `class="mt-24rpx"`（卡片和上一块之间那道间距）会静默丢掉，所以外面包一层 view -->
  <view>
    <view v-if="loading && !count" class="py-128rpx text-center text-[26rpx] text-gray-400">
      加载中…
    </view>

    <view v-else-if="error && !count" class="mx-32rpx rounded-lg bg-red-50 px-32rpx py-24rpx text-[26rpx] text-[#d14328]">
      {{ error }}
      <text hover-class="fx-tap" class="fx-press ml-16rpx underline" @click="retry">重试</text>
    </view>

    <view v-else-if="!count" class="py-128rpx text-center text-[26rpx] text-gray-400">
      {{ emptyText }}
    </view>

    <template v-else>
      <slot />
      <view class="py-32rpx text-center text-[24rpx] text-gray-300">
        {{ moreText }}
      </view>
    </template>
  </view>
</template>
