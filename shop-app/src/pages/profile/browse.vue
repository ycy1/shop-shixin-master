<script lang="ts" setup>
import type { IShopBrowseRow } from '@/api/types/shop'
import { getMyBrowses } from '@/api/profile'
import ListState from '@/components/ListState.vue'
import { usePagedList } from '@/composables/usePagedList'
import { LOGIN_PAGE } from '@/router/config'
import { useTokenStore } from '@/store/token'
import { dayOf, moneyOf } from '@/utils/profile'

defineOptions({
  name: 'BrowseList',
})

definePage({
  style: {
    navigationBarTitleText: '浏览足迹',
    enablePullDownRefresh: true,
    onReachBottomDistance: 100,
  },
})

/**
 * 浏览足迹：一行一个商品（同一个商品只看最近那一次，后端是 upsert 所以不会铺重复行），
 * 口径照 shop-web「个人中心 → 浏览足迹」那一块。名称为空 = 这商品已经删了，联不到，点不动。
 *
 * 只读：清空足迹是写动作，App 页面还没铺。
 */
const tokenStore = useTokenStore()
const browses = usePagedList<IShopBrowseRow>(getMyBrowses)

function toDetail(productId: number | null) {
  if (productId == null) {
    return
  }
  uni.navigateTo({ url: `/pages/goods/detail?id=${productId}` })
}

function toLogin() {
  uni.navigateTo({ url: LOGIN_PAGE })
}

onLoad(() => {
  if (tokenStore.updateNowTime().hasLogin) {
    void browses.load()
  }
})

onPullDownRefresh(async () => {
  await browses.reload()
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  if (browses.hasMore) {
    void browses.load()
  }
})
</script>

<template>
  <view class="min-h-screen bg-[#f5f6f7] pb-48rpx">
    <view v-if="!tokenStore.hasLogin" class="px-32rpx py-160rpx text-center">
      <view class="text-[28rpx] text-gray-400">
        登录后才能看自己看过什么
      </view>
      <wd-button class="mt-32rpx" type="error" @click="toLogin">
        去登录
      </wd-button>
    </view>

    <ListState
      v-else
      class="pt-24rpx"
      :loading="browses.loading"
      :error="browses.error"
      :count="browses.rows.length"
      :has-more="browses.hasMore"
      empty-text="还没有浏览记录"
      @retry="browses.load()"
    >
      <view
        v-for="row in browses.rows"
        :key="row.id"
        hover-class="fx-tap"
        class="fx-press mx-24rpx mb-24rpx flex rounded-xl bg-white p-24rpx"
        @click="toDetail(row.productId)"
      >
        <image
          v-if="row.mainImage"
          :src="row.mainImage"
          mode="aspectFill"
          class="h-[128rpx] w-[128rpx] shrink-0 rounded-lg bg-gray-50"
        />
        <view v-else class="h-[128rpx] w-[128rpx] shrink-0 rounded-lg bg-gray-100" />

        <view class="ml-24rpx min-w-0 flex-1">
          <view class="truncate text-[28rpx] text-[#333]">
            {{ row.productName || '（这个商品已经下架了）' }}
          </view>
          <view class="mt-8rpx truncate text-[24rpx] text-gray-400">
            {{ dayOf(row.browseTime) }} 看过
            <template v-if="row.categoryName">
              · {{ row.categoryName }}
            </template>
          </view>
          <view class="mt-16rpx text-[28rpx] text-[#d14328]">
            {{ moneyOf(row.productPrice) }}
          </view>
        </view>
      </view>
    </ListState>
  </view>
</template>
