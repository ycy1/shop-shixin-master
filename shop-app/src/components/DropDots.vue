<script setup lang="ts">
import { useDroplet } from '@/composables/useDroplet'

/**
 * 轮播底下那一排 dot：一条半透明的圆角底槽 + 一颗在里面跑的水珠（和 web 的 components/DotTrack.vue 同一档）。
 *
 * 为什么不用 swiper 自带的 indicator-dots：那是组件内部画的，样式和动效都插不进去
 * —— Web 那一头的 dot 本来就是自己画的（ActivityCarousel / StoreCarousel），App 换成这一份两端才对得上。
 * 有了底槽，「挤长」才看得出来：之前那颗白点直接摊在照片上变长变短，两头没参照，感觉就是跳一下。
 * 一格固定 40rpx 宽 ⇒ 胶囊位置按「第几个 / 共几个」的百分比给，不用量盒子；槽的内边距正好兜住珠子撑长那 8rpx。
 */
const props = withDefaults(defineProps<{
  count: number
  current: number
  /** light = 铺在图上（白点 + 白槽）；dark = 铺在白底上（灰点 + 浅灰槽，水珠用品牌红） */
  tone?: 'dark' | 'light'
}>(), {
  tone: 'light',
})

// dot 走 bead：滑过去的同时被挤长、落位再收圆（拉长扫过来那一档留给 tab 那排）
const { style: pillStyle } = useDroplet(
  toRef(() => props.current),
  toRef(() => props.count),
  'bead',
)
</script>

<template>
  <view v-if="props.count > 1" class="dots flex items-center justify-center">
    <view class="dots__track" :class="props.tone === 'light' ? 'dots--light' : 'dots--dark'">
      <view class="relative flex">
        <view class="drop-pill drop-pill--dot" :style="pillStyle">
          <view class="drop-pill__face" />
        </view>
        <view v-for="(_, i) in props.count" :key="i" class="dots__cell">
          <view class="dots__dot" :class="{ 'dots__dot--covered': i === props.current }" />
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 半透明圆角底槽：珠子撑长时溢出那 4px 正好由这条内边距兜住 */
.dots__track {
  display: inline-flex;
  padding: 8rpx;
  border-radius: 999px;
  /* 色由下面 tone 那两档在同一元素上声明的 --dt-bg 给（槽自己既是 tone 那层，不能写成后代选择器） */
  background: var(--dt-bg, transparent);
}

.dots__cell {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40rpx;
  height: 16rpx;
}

.dots__dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 999px;
}

/* 当前那一格自己不再画点：它那一颗由水珠担当，留着会和浮在上面的珠子叠成一坨 */
.dots__dot--covered {
  background: transparent !important;
}

/* 铺在照片上：槽和点用半透明白，水珠用实心白 */
.dots--light {
  --dt-bg: rgba(255, 255, 255, 0.25);
  --dp-bg: #fff;
}

.dots--light .dots__dot {
  background: rgba(255, 255, 255, 0.55);
}

/* 铺在白底上：槽和点用浅灰，水珠用品牌红（和这一页其余高亮同一个色） */
.dots--dark {
  --dt-bg: rgba(0, 0, 0, 0.08);
  --dp-bg: #d14328;
}

.dots--dark .dots__dot {
  background: rgba(0, 0, 0, 0.14);
}
</style>
