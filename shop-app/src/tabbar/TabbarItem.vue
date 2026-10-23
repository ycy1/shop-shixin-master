<script setup lang="ts">
import type { CustomTabBarItem } from './types'
import { tabbarStore } from './store'

defineProps<{
  item: CustomTabBarItem
  index: number
  isBulge?: boolean
}>()

function getImageByIndex(index: number, item: CustomTabBarItem) {
  if (!item.iconActive) {
    console.warn('image 模式下，需要配置 iconActive (高亮时的图片），否则无法切换高亮图片')
    return item.icon
  }
  return tabbarStore.curIdx === index ? item.iconActive : item.icon
}
</script>

<template>
  <view class="flex flex-col items-center justify-center">
    <!-- 图标那一颗当角标的定位基准：外面那格比图标宽（px-24rpx + 文字还居中），贴格子上就跑到整列右边去了 -->
    <view class="relative">
      <template v-if="item.iconType === 'uiLib'">
        <!-- TODO: 以下内容请根据选择的UI库自行替换 -->
        <!-- 如：<wd-icon name="home" /> (https://wot-design-uni.cn/component/icon.html) -->
        <!-- 如：<uv-icon name="home" /> (https://www.uvui.cn/components/icon.html) -->
        <!-- 如：<sar-icon name="image" /> (https://sard.wzt.zone/sard-uniapp-docs/components/icon)(sar没有home图标^_^) -->
        <!-- <wd-icon :name="item.icon" size="20" /> -->
      </template>
      <template v-if="item.iconType === 'unocss' || item.iconType === 'iconfont'">
        <view :class="[item.icon, isBulge ? 'text-160rpx' : 'text-40rpx']" />
      </template>
      <template v-if="item.iconType === 'image'">
        <image :src="getImageByIndex(index, item)" mode="scaleToFill" :class="isBulge ? 'h-160rpx w-160rpx' : 'h-48rpx w-48rpx'" />
      </template>
      <!-- 角标：压在图标右上角（整格 100rpx 上下各只余十来 rpx，数字那颗不敢多探出去）。
           尺寸和字号都走 rpx（1rpx = 屏宽/750），跟着屏幕缩，不同机型上贴的那一角不变 -->
      <view v-if="item.badge === 'dot'" class="absolute h-16rpx w-16rpx rounded-full bg-#f56c6c -right-8rpx -top-8rpx" />
      <view
        v-else-if="item.badge"
        class="absolute box-border h-32rpx min-w-32rpx center rounded-full bg-#f56c6c px-8rpx text-center text-2xs text-white -right-12rpx -top-6rpx"
      >
        {{ item.badge > 99 ? '99+' : item.badge }}
      </view>
    </view>
    <view v-if="!isBulge" class="mt-4rpx text-24rpx">
      {{ item.text }}
    </view>
  </view>
</template>
