<script setup lang="ts">
import { useDroplet } from '@/composables/useDroplet'

/**
 * 等分 tab 排 + 水滴胶囊（方案 A · 胶囊拉伸）。
 *
 * 格子一律 flex-1 ⇒ 胶囊位置能按「第几格 / 共几格」的百分比给，不用量盒子：量盒子在小程序里每次切换要多一趟
 * 异步查询，标签上那个评论数一变（(0)→(3)）还得重量。代价是标签居中、不再左堆 —— 选等分这一条时就认了这个形状。
 *
 * 认 key 不认下标：各页手里攥的是自己的 key（'products' / 'comment' / 详情那几档），下标只在组件内部换算一次。
 * 原来那根落在选中项上的 border-b-2 不画了 —— 胶囊就是那根线，会拉长会收住。
 */
const props = defineProps<{
  items: { key: string, label: string, count?: number | null }[]
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [key: string]
}>()

const index = computed(() => {
  const at = props.items.findIndex(one => one.key === props.modelValue)
  return at < 0 ? 0 : at
})

const { style: pillStyle } = useDroplet(
  index,
  computed(() => props.items.length),
)
</script>

<template>
  <view class="relative flex border-b border-gray-100">
    <view class="drop-pill drop-pill--tab" :style="pillStyle">
      <view class="drop-pill__face" />
    </view>
    <view
      v-for="one in props.items"
      :key="one.key"
      hover-class="fx-tap"
      class="fx-press flex flex-1 items-center justify-center whitespace-nowrap py-24rpx text-[28rpx]"
      :class="one.key === props.modelValue ? 'text-[#d14328] font-medium' : 'text-gray-500'"
      @click="emit('update:modelValue', one.key)"
    >
      <!-- 数字用半角括号、只留 4rpx 间距：全角（18）那一串在搜索页五档等分下会把格子撑到两行 -->
      <text>{{ one.label }}</text>
      <text v-if="one.count !== null && one.count !== undefined" class="text-[24rpx] text-gray-400">({{ one.count }})</text>
    </view>
  </view>
</template>
