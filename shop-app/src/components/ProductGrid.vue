<script lang="ts" setup>
import type { IShopProduct } from '@/api/types/shop'

/**
 * 两列商品网格：首页、搜索结果、门店详情、品牌详情四处画的是同一样东西，所以收成一颗组件。
 *
 * 只负责「画 + 点进详情」，取数、分页、空态提示都留在各页面里 ——
 * 四页的续页节奏和空态措辞不一样（首页是「没有找到相关商品」，门店页是「这家店还没上架商品」）。
 * 加购不在这里：列表这一行拿不到规格那一档（skuId），替他挑一档会把买错的东西塞进车里，
 * 所以进车这个动作留在商品详情页（见 pages/goods/detail.vue 那颗悬浮购物车）。
 */
defineProps<{
  products: IShopProduct[]
}>()

function toDetail(id: number) {
  uni.navigateTo({ url: `/pages/goods/detail?id=${id}` })
}
</script>

<template>
  <view class="flex flex-wrap px-16rpx pt-16rpx">
    <view
      v-for="item in products"
      :key="item.id"
      hover-class="fx-tap"
      class="fx-press box-border w-1/2 px-8rpx py-16rpx"
      @click="toDetail(item.id)"
    >
      <image
        v-if="item.mainImage"
        :src="item.mainImage"
        mode="aspectFill"
        class="aspect-square w-full rounded-lg bg-gray-50"
      />
      <view v-else class="aspect-square w-full rounded-lg bg-gray-100" />
      <view class="mt-16rpx text-[28rpx] text-[#333] leading-[38rpx]">
        {{ item.productName }}
      </view>
      <view v-if="item.subTitle" class="mt-8rpx truncate text-[24rpx] text-gray-400">
        {{ item.subTitle }}
      </view>
      <view class="mt-16rpx flex items-end justify-between">
        <text v-if="item.priceMin != null" class="text-[30rpx] text-[#d14328]">
          ¥{{ item.priceMin }}
          <text class="text-[22rpx] text-gray-400">起</text>
        </text>
        <text v-else class="text-[24rpx] text-gray-400">
          暂无售价
        </text>
        <text v-if="item.brandName" class="max-w-120rpx truncate text-[22rpx] text-gray-400">
          {{ item.brandName }}
        </text>
      </view>
    </view>
  </view>
</template>
