<script lang="ts" setup>
import type { IShopFavoriteRow } from '@/api/types/shop'
import { getMyFavorites } from '@/api/profile'
import ListState from '@/components/ListState.vue'
import { usePagedList } from '@/composables/usePagedList'
import { LOGIN_PAGE } from '@/router/config'
import { useTokenStore } from '@/store/token'
import { codeText, dayOf, FAVORITE_TARGET_TEXT } from '@/utils/profile'

defineOptions({
  name: 'FavoriteList',
})

definePage({
  style: {
    navigationBarTitleText: '我的收藏',
    enablePullDownRefresh: true,
    onReachBottomDistance: 100,
  },
})

/**
 * 我的收藏：商品/门店/品牌三样同住一张表，一行一个收藏对象（targetId 指向各自那张表，没有外键，
 * 名称是读的时候现联出来的），口径照 shop-web「个人中心 → 我的收藏」那一块。
 *
 * 只读：取消收藏是 /api/comm 那条 toggle 写动作，App 页面铺到那一步再补。
 * 名称为空 = 收藏的那个对象已经被删了（表上没 FK，联不到就回 null），这种行点不动，只说「已下架/已删除」。
 */
const tokenStore = useTokenStore()

/** 0 是「不限」哨兵；真值 1/2/3 正好和 target_type 的三档对上 */
const TYPES = [
  { value: 0, label: '全部' },
  { value: 1, label: '商品' },
  { value: 2, label: '门店' },
  { value: 3, label: '品牌' },
]
const filterType = ref(0)

const favorites = usePagedList<IShopFavoriteRow>(
  (page, size) => getMyFavorites(page, size, filterType.value || undefined),
)

watch(filterType, () => favorites.reload())

function pickType(value: number) {
  filterType.value = value
}

/** 换三类各跳各的详情页，路径和搜索页那一份一致 */
function toDetail(row: IShopFavoriteRow) {
  if (row.targetId == null) {
    return
  }
  const url = {
    1: `/pages/goods/detail?id=${row.targetId}`,
    2: `/pages/store/detail?id=${row.targetId}`,
    3: `/pages/brand/detail?id=${row.targetId}`,
  }[row.targetType ?? 0]
  if (url) {
    uni.navigateTo({ url })
  }
}

function toLogin() {
  uni.navigateTo({ url: LOGIN_PAGE })
}

onLoad(() => {
  if (tokenStore.updateNowTime().hasLogin) {
    void favorites.load()
  }
})

onPullDownRefresh(async () => {
  await favorites.reload()
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  if (favorites.hasMore) {
    void favorites.load()
  }
})
</script>

<template>
  <view class="min-h-screen bg-[#f5f6f7] pb-48rpx">
    <view v-if="!tokenStore.hasLogin" class="px-32rpx py-160rpx text-center">
      <view class="text-[28rpx] text-gray-400">
        登录后才能看自己收藏了什么
      </view>
      <wd-button class="mt-32rpx" type="error" @click="toLogin">
        去登录
      </wd-button>
    </view>

    <template v-else>
      <view class="bg-white px-24rpx pt-16rpx">
        <view class="flex border-b border-gray-100">
          <view
            v-for="one in TYPES"
            :key="one.value"
            hover-class="fx-tap"
            class="fx-press mr-32rpx border-b-2 pb-16rpx text-[28rpx]"
            :class="filterType === one.value ? 'border-[#d14328] text-[#d14328] font-medium' : 'border-transparent text-gray-500'"
            @click="pickType(one.value)"
          >
            {{ one.label }}
          </view>
        </view>
      </view>

      <ListState
        class="mt-24rpx"
        :loading="favorites.loading"
        :error="favorites.error"
        :count="favorites.rows.length"
        :has-more="favorites.hasMore"
        :empty-text="filterType ? `还没有收藏过${codeText(FAVORITE_TARGET_TEXT, filterType)}` : '还没有收藏过东西'"
        @retry="favorites.load()"
      >
        <view
          v-for="row in favorites.rows"
          :key="row.id"
          hover-class="fx-tap"
          class="fx-press mx-24rpx my-24rpx flex rounded-xl bg-white p-32rpx"
          @click="toDetail(row)"
        >
          <text class="mr-24rpx shrink-0 self-start rounded bg-gray-100 px-16rpx py-4rpx text-[22rpx] text-gray-600">
            {{ codeText(FAVORITE_TARGET_TEXT, row.targetType) || '其他' }}
          </text>
          <view class="min-w-0 flex-1">
            <view class="truncate text-[30rpx] text-[#333]">
              {{ row.targetName || '（这个对象已经不在了）' }}
            </view>
            <view class="mt-8rpx text-[24rpx] text-gray-400">
              收藏于 {{ dayOf(row.favoriteTime) }}
            </view>
          </view>
          <text v-if="row.targetName" class="ml-16rpx shrink-0 self-center text-[26rpx] text-gray-300">
            ›
          </text>
        </view>
      </ListState>
    </template>
  </view>
</template>
