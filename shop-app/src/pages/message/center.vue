<script lang="ts" setup>
import type { IShopNotificationRow, IShopNotificationUnread } from '@/api/types/shop'
import {
  deleteMyNotification,
  getMyNotifications,
  getMyNotificationUnread,
  readAllMyNotification,
  readMyNotification,
} from '@/api/notification'
import ListState from '@/components/ListState.vue'
import { usePagedList } from '@/composables/usePagedList'
import { LOGIN_PAGE } from '@/router/config'
import { useTokenStore } from '@/store/token'
import { messageTarget } from '@/utils/notification'
import { dayOf } from '@/utils/profile'

defineOptions({
  name: 'MessageCenter',
})

definePage({
  style: {
    navigationBarTitleText: '我的消息',
    enablePullDownRefresh: true,
    onReachBottomDistance: 100,
  },
})

/**
 * 收件箱：三档筛选（全部 / 系统 / 互动，每档头上顶着自己的未读数），一条消息一行，
 * 形状照 shop-web 的「个人中心 → 消息」那一块（他点名的范本）。
 *
 * 未读数读的是服务端那一格，不数当前这一页 —— 页是截过的，未读的可能根本不在这一串里。
 * 「全部已读」一键清所有未读，不跟着筛选档走（筛选只用来挑看哪一串）。
 * 点一行 = 标已读 + 跳到它说的那一页：link 服务端留空，各端按 businessType 自己拼路由（见 utils/notification）。
 * 只想读掉或删掉这一条、不想跳转 → 往左滑行，露出「已读 / 删除」两颗。
 */
const FILTERS = [
  { label: '全部', value: 0, key: 'total' as const },
  { label: '系统消息', value: 1, key: 'systemCount' as const },
  { label: '互动消息', value: 2, key: 'interactCount' as const },
]

const tokenStore = useTokenStore()
const category = ref(0)
const unread = reactive<IShopNotificationUnread>({ total: 0, systemCount: 0, interactCount: 0 })
const notice = ref('')
const busy = ref(false)
/** 每一行左滑停在哪：wd-swipe-action 那一颗要 v-model，按行 id 挂，免得重拉列表时串到别行上 */
const swipe = reactive<Record<number, string>>({})

const messages = usePagedList<IShopNotificationRow>((pageNum, pageSize) =>
  getMyNotifications(pageNum, pageSize, category.value || undefined))

async function loadUnread() {
  try {
    Object.assign(unread, await getMyNotificationUnread())
  }
  catch {
    // 红点捞不到不影响列表本身
  }
}

/** 换档 = 回第一页整块重拉；那一句提示跟着清掉，不然它还挂着上一档的「已全部读过」；滑开的那一行也收回 */
async function refilter() {
  notice.value = ''
  Object.keys(swipe).forEach(id => delete swipe[Number(id)])
  await messages.reload()
}

watch(category, () => void refilter())

/**
 * 点一行 = 标已读 + 跳到它说的那一页（拼法见 utils/notification：公告进详情页、活动进活动详情、
 * 回复与点赞过到「全部评论」那一页并定位到那一条）。
 * 已读没成也照样跳：那条消息本身还是要去看的；读过的行再点也只跳转，不该被「已经读过了」挡死。
 */
async function readOne(row: IShopNotificationRow) {
  if (busy.value) {
    return
  }
  // 这一行还滑开着：这一下只当收回按钮，别顺手把人带去详情页
  if (swipe[row.id] === 'right') {
    return
  }
  const url = messageTarget(row)
  if (row.isRead !== 1) {
    busy.value = true
    try {
      await readMyNotification(row.id)
      row.isRead = 1
      await loadUnread()
    }
    catch {
      // 标已读失败只留红点在，不弹第二句：http 层已经说过那句了
    }
    finally {
      busy.value = false
    }
  }
  if (url) {
    uni.navigateTo({ url })
  }
}

/** 这一档读完没读完都不影响「全部已读」那颗：它清的是所有未读 */
async function readAll() {
  if (busy.value) {
    return
  }
  busy.value = true
  notice.value = ''
  messages.error = ''
  try {
    await readAllMyNotification()
    await refilter()
    await loadUnread()
    notice.value = '所有消息都读过了'
  }
  catch (err) {
    messages.error = `操作没成功：${(err as Error).message}`
  }
  finally {
    busy.value = false
  }
}

/**
 * 左滑出来的「已读」：只把这一条标读过，不跳转 —— 人有时就是想把红点清掉、正文就地看过了。
 * 读过的行再点也只当无事发生，服务端那条 read 本来就是幂等 upsert，不该白跑一趟。
 */
async function markRead(row: IShopNotificationRow) {
  if (busy.value || row.isRead === 1) {
    return
  }
  busy.value = true
  notice.value = ''
  messages.error = ''
  try {
    await readMyNotification(row.id)
    row.isRead = 1
    await loadUnread()
  }
  catch (err) {
    messages.error = `操作没成功：${(err as Error).message}`
  }
  finally {
    busy.value = false
  }
}

function askDelete(row: IShopNotificationRow) {
  if (busy.value) {
    return
  }
  uni.showModal({
    title: '删除消息',
    content: '删掉这一条？删了只是对你隐藏，别的收件人照样能看到。',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      busy.value = true
      notice.value = ''
      messages.error = ''
      try {
        await deleteMyNotification(row.id)
        await refilter()
        await loadUnread()
        notice.value = '已删除'
      }
      catch (err) {
        messages.error = (err as Error).message
      }
      finally {
        busy.value = false
      }
    },
  })
}

function toLogin() {
  uni.navigateTo({ url: LOGIN_PAGE })
}

onLoad(() => {
  if (tokenStore.updateNowTime().hasLogin) {
    void messages.load()
    void loadUnread()
  }
})

onPullDownRefresh(async () => {
  if (tokenStore.updateNowTime().hasLogin) {
    await Promise.all([messages.reload(), loadUnread()])
  }
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  if (messages.hasMore) {
    void messages.load()
  }
})
</script>

<template>
  <view class="min-h-screen bg-[#f5f6f7] pb-48rpx">
    <view v-if="!tokenStore.hasLogin" class="px-32rpx py-160rpx text-center">
      <view class="text-[28rpx] text-gray-400">
        登录后才能看到发给自己的消息
      </view>
      <wd-button class="mt-32rpx" type="error" @click="toLogin">
        去登录
      </wd-button>
    </view>

    <template v-else>
      <!-- 三档筛选摆在列表上面（它们换的就是底下这一串）；每档顶自己的未读数，「全部已读」挂在这一排右边 -->
      <view class="mx-24rpx my-24rpx flex items-center">
        <view
          v-for="one in FILTERS"
          :key="one.value"
          hover-class="fx-tap"
          class="fx-press mr-16rpx shrink-0 rounded-full px-24rpx py-8rpx text-[24rpx]"
          :class="category === one.value ? 'bg-[#d14328] text-white' : 'bg-white text-gray-600'"
          @click="category = one.value"
        >
          {{ one.label }}
          <text v-if="unread[one.key]" class="ml-4rpx text-[22rpx]">
            {{ unread[one.key] }}
          </text>
        </view>
        <text
          v-if="unread.total"
          hover-class="fx-tap"
          class="fx-press ml-auto shrink-0 text-[26rpx] text-[#d14328]"
          @click="readAll"
        >
          全部已读
        </text>
      </view>

      <!-- 「全部已读」「已删除」这两句只能在这儿说：动作做完列表就重拉了，没别的地方留提示 -->
      <view v-if="notice" class="mx-24rpx mb-16rpx text-right text-[24rpx] text-green-600">
        {{ notice }}
      </view>

      <ListState
        class="pt-8rpx"
        :loading="messages.loading"
        :error="messages.error"
        :count="messages.rows.length"
        :has-more="messages.hasMore"
        :empty-text="category ? '这一档没有消息，换个筛选看看' : '还没有收到消息'"
        @retry="messages.load()"
      >
        <wd-swipe-action
          v-for="row in messages.rows"
          :key="row.id"
          v-model="swipe[row.id]"
          class="mx-24rpx mb-24rpx overflow-hidden rounded-xl"
        >
          <view hover-class="fx-tap" class="fx-press flex rounded-xl bg-white p-24rpx" @click="readOne(row)">
            <!-- 未读那一个点：读过之后整行留下的位置不变，串不会跳 -->
            <view class="w-24rpx shrink-0 pt-14rpx">
              <view v-if="row.isRead !== 1" class="h-12rpx w-12rpx rounded-full bg-[#d14328]" />
            </view>

            <view class="min-w-0 flex-1">
              <view class="flex items-center">
                <text class="mr-12rpx shrink-0 rounded bg-gray-100 px-12rpx py-2rpx text-[22rpx] text-gray-600">
                  {{ row.category === 2 ? '互动' : '系统' }}
                </text>
                <text class="min-w-0 flex-1 truncate text-[28rpx] text-[#333] font-medium">
                  {{ row.title || '（没有标题）' }}
                </text>
              </view>
              <view class="mt-8rpx text-[26rpx] text-gray-600 leading-[36rpx]">
                {{ row.message || '—' }}
              </view>
              <view class="mt-8rpx truncate text-[22rpx] text-gray-400">
                {{ dayOf(row.createTime) }}
                <template v-if="row.fromNickname">
                  · {{ row.fromNickname }}
                </template>
              </view>
            </view>
          </view>

          <!-- 左滑两颗：已读只清红点、删除走原来的确认框，两颗都不跳转 -->
          <template #right>
            <view class="h-full flex">
              <view
                hover-class="fx-tap"
                class="fx-press w-120rpx flex items-center justify-center bg-[#f5a623] text-[26rpx] text-white"
                @click="markRead(row)"
              >
                已读
              </view>
              <view
                hover-class="fx-tap"
                class="fx-press w-120rpx flex items-center justify-center bg-[#d14328] text-[26rpx] text-white"
                @click="askDelete(row)"
              >
                删除
              </view>
            </view>
          </template>
        </wd-swipe-action>
      </ListState>
    </template>
  </view>
</template>
