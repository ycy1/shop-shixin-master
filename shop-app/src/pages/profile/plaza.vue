<script lang="ts" setup>
import type { IShopPlazaMessage } from '@/api/types/shop'
import { deletePlaza, getMyPlazaPage, PLAZA_AUDIT_PASSED, PLAZA_AUDIT_TEXT, PLAZA_RELATION_TEXT, plazaLabel } from '@/api/plaza'
import ListState from '@/components/ListState.vue'
import { usePagedList } from '@/composables/usePagedList'
import { LOGIN_PAGE } from '@/router/config'
import { useTokenStore } from '@/store/token'
import { codeText, dayOf } from '@/utils/profile'

defineOptions({
  name: 'ProfilePlaza',
})

definePage({
  style: {
    navigationBarTitleText: '广场消息',
    enablePullDownRefresh: true,
    onReachBottomDistance: 100,
  },
})

/**
 * 我的广场消息：原来挂在「个人信息」下面最后一档 tab，现在跟「个人信息」持平，单独一张页（形状照收藏/足迹那两张）。
 *
 * 读的是 my/list ⇒ 判据和广场那一页差一刀：待审核的、被拒的都在这一串里（广场上找不到它们），
 * 所以这一页也是「改自己那一条」唯一的入口。改完服务端把审核状态打回 0，回来必须重捞才能看到新徽标。
 *
 * 三样筛选（关键字 + 关联类型 + 审核状态）都是列表接口的查询参数：胶囊一改就回第一页整块重捞，
 * 关键字要按下搜索才算（边打字边捞没法看）。「无关联」「待审核」两档的真值都是 0，所以哨兵用 null 而不是 0。
 */
const tokenStore = useTokenStore()

/** null = 不限；下面两排胶囊的第一颗就是它，往后各档直接取字典那两表 */
const keyword = ref('')
const relationType = ref<number | null>(null)
const auditStatus = ref<number | null>(null)

const RELATION_CHIPS = [
  { label: '全部', value: null },
  ...[0, 1, 2, 3, 4].map(type => ({ label: PLAZA_RELATION_TEXT[type], value: type })),
]
const AUDIT_CHIPS = [
  { label: '全部', value: null },
  ...[0, 1, 2].map(status => ({ label: PLAZA_AUDIT_TEXT[status], value: status })),
]

const posts = usePagedList<IShopPlazaMessage>((pageNum, pageSize) => {
  const query: { keyword?: string, relationType?: number, auditStatus?: number } = {}
  const kw = keyword.value.trim()
  if (kw) {
    query.keyword = kw
  }
  if (relationType.value !== null) {
    query.relationType = relationType.value
  }
  if (auditStatus.value !== null) {
    query.auditStatus = auditStatus.value
  }
  return getMyPlazaPage(pageNum, pageSize, query)
})

watch([relationType, auditStatus], () => posts.reload())

const deleting = ref(false)

function toEditPlaza(row: IShopPlazaMessage) {
  uni.navigateTo({ url: `/pages/plaza/publish?id=${row.id}` })
}

function toPlazaDetail(id: number) {
  uni.navigateTo({ url: `/pages/plaza/detail?id=${id}` })
}

function askDeletePlaza(row: IShopPlazaMessage) {
  if (deleting.value) {
    return
  }
  uni.showModal({
    title: '删除广场消息',
    content: `确定删掉「${plazaLabel(row)}」这一条？删了就找不回来，名下收到的赞和评论也跟着没。`,
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      deleting.value = true
      try {
        await deletePlaza(row.id)
        await posts.reload()
      }
      catch {
        // 「这条消息不存在或已经被删除」由 http 层弹出来，这一列保持原样
      }
      finally {
        deleting.value = false
      }
    },
  })
}

function toLogin() {
  uni.navigateTo({ url: LOGIN_PAGE })
}

onLoad(() => {
  if (tokenStore.updateNowTime().hasLogin) {
    void posts.load()
  }
})

/** 从「修改广场消息」那一页回来重捞：改完审核状态打回待审核，不重拉看到的还是旧徽标 */
onShow(() => {
  if (posts.page) {
    void posts.reload()
  }
})

onPullDownRefresh(async () => {
  await posts.reload()
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  if (posts.hasMore) {
    void posts.load()
  }
})
</script>

<template>
  <view class="min-h-screen bg-[#f5f6f7] pb-48rpx">
    <view v-if="!tokenStore.hasLogin" class="px-32rpx py-160rpx text-center">
      <view class="text-[28rpx] text-gray-400">
        登录后才能看自己发过什么
      </view>
      <wd-button class="mt-32rpx" type="error" @click="toLogin">
        去登录
      </wd-button>
    </view>

    <template v-else>
      <!-- 筛选那一块：关键字一格 + 两排胶囊（描边一律不用，这个工程的 UnoCSS 注释掉了 preflight，选中态靠底色） -->
      <view class="mx-24rpx mt-24rpx rounded-xl bg-white px-16rpx py-16rpx">
        <wd-search
          v-model="keyword"
          placeholder="搜标题、正文或发布者"
          hide-cancel
          @search="posts.reload()"
          @clear="posts.reload()"
        />
        <scroll-view scroll-x class="overflow-hidden whitespace-nowrap">
          <view class="inline-flex px-8rpx pb-8rpx">
            <!-- 这一格必须是 view 不能是 text：uni-text 是 white-space: pre-line，模板里那两下换行会当真换行渲染 -->
            <view
              v-for="one in RELATION_CHIPS"
              :key="one.label"
              hover-class="fx-tap"
              class="fx-press mr-16rpx shrink-0 rounded-full px-24rpx py-8rpx text-[24rpx]"
              :class="relationType === one.value ? 'bg-[#d14328] text-white' : 'bg-gray-100 text-gray-600'"
              @click="relationType = one.value"
            >
              {{ one.label }}
            </view>
          </view>
        </scroll-view>
        <scroll-view scroll-x class="mt-8rpx overflow-hidden whitespace-nowrap">
          <view class="inline-flex px-8rpx pb-8rpx">
            <view
              v-for="one in AUDIT_CHIPS"
              :key="one.label"
              hover-class="fx-tap"
              class="fx-press mr-16rpx shrink-0 rounded-full px-24rpx py-8rpx text-[24rpx]"
              :class="auditStatus === one.value ? 'bg-[#d14328] text-white' : 'bg-gray-100 text-gray-600'"
              @click="auditStatus = one.value"
            >
              {{ one.label }}
            </view>
          </view>
        </scroll-view>
      </view>

      <ListState
        class="pt-24rpx"
        :loading="posts.loading"
        :error="posts.error"
        :count="posts.rows.length"
        :has-more="posts.hasMore"
        :empty-text="keyword || relationType !== null || auditStatus !== null ? '这个条件下没有广场消息' : '还没在广场上发过东西'"
        @retry="posts.load()"
      >
        <view
          v-for="row in posts.rows"
          :key="row.id"
          hover-class="fx-tap"
          class="fx-press mx-24rpx mb-24rpx rounded-xl bg-white px-32rpx py-24rpx"
          @click="toPlazaDetail(row.id)"
        >
          <view class="flex items-start">
            <text class="min-w-0 flex-1 truncate text-[30rpx] text-[#333] font-medium">
              {{ plazaLabel(row) }}
            </text>
            <text
              class="ml-16rpx shrink-0 rounded px-16rpx py-4rpx text-[22rpx]"
              :class="row.auditStatus === PLAZA_AUDIT_PASSED ? 'bg-red-50 text-[#d14328]' : 'bg-gray-100 text-gray-500'"
            >
              {{ codeText(PLAZA_AUDIT_TEXT, row.auditStatus ?? 0) }}
            </text>
          </view>

          <view class="mt-8rpx truncate text-[26rpx] text-gray-600">
            {{ row.contentText?.trim() || '这条没有正文' }}
          </view>

          <view class="mt-16rpx text-[22rpx] text-gray-400">
            {{ dayOf(row.createTime) }}
            <template v-if="row.relationName">
              · 相关：{{ row.relationName }}
            </template>
            <template v-if="row.auditRemark">
              · 审核说明：{{ row.auditRemark }}
            </template>
          </view>

          <view class="mt-16rpx flex items-center justify-end">
            <text hover-class="fx-tap" class="fx-press shrink-0 text-[26rpx] text-[#333]" @click.stop="toEditPlaza(row)">
              修改
            </text>
            <text
              hover-class="fx-tap"
              class="fx-press ml-32rpx shrink-0 text-[26rpx]"
              :class="deleting ? 'text-gray-300' : 'text-[#d14328]'"
              @click.stop="askDeletePlaza(row)"
            >
              删除
            </text>
          </view>
        </view>
      </ListState>
    </template>
  </view>
</template>
