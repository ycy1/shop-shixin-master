<script lang="ts" setup>
import type { IShopPlazaMessage } from '@/api/types/shop'
import { getPlazaPage, getRecommendedPlazaPage, PLAZA_RELATION_ACTIVITY, PLAZA_RELATION_BRAND, PLAZA_RELATION_NONE, PLAZA_RELATION_PRODUCT, PLAZA_RELATION_STORE, PLAZA_RELATION_TEXT, plazaLabel, plazaLeadImage, togglePlazaLike, trackPlazaShare } from '@/api/plaza'
import ListState from '@/components/ListState.vue'
import { usePagedList } from '@/composables/usePagedList'
import { useTokenStore } from '@/store/token'
import { toLoginPage } from '@/utils/toLoginPage'

defineOptions({
  name: 'PlazaList',
})

definePage({
  style: {
    navigationBarTitleText: '广场',
    enablePullDownRefresh: true,
    onReachBottomDistance: 100,
  },
})

/**
 * 广场：底部 bar 上那一颗的落点，前台用户发的图文流（形状同 shop-web 的 /plaza 那一页）。
 *
 * 一次十条往下捞 —— 「广场页 10 条请求一次」钉在 usePagedList 的 pageSize 上（它默认就是 10，这里写出来是给后人看），
 * 触底续一页，不像活动页那样给「再看 10 条」那颗：往后翻是无底的。
 * 读接口不要登录，游客照常能逛；只有右下角那颗发布和卡片上那颗点赞要登录 —— 没登录点了先去登录页。
 *
 * 筛选那一排（关键字 + 关联类型）走列表接口的两条查询参数，换筛选就回第一页整块重捞；
 * 「无关联」是一档真值（relation_type=0），所以胶囊里那颗「全部」是「不发这一格」，跟 0 不是一回事。
 *
 * 卡片整块可点，进 /pages/plaza/detail：正文（content）列表那条 SQL 根本没取，只有详情那一趟读得到。
 * 底下那排动作挂在卡片里，所以两颗都要 .stop 一下 —— 不拦住会连着跳到详情页。
 */
const PAGE_SIZE = 10

/** 关联类型那一排胶囊（顺序就是字典 plaza_relation_type 的四档 + 无关联，同发布页那一排） */
const RELATION_TYPES = [
  PLAZA_RELATION_NONE,
  PLAZA_RELATION_PRODUCT,
  PLAZA_RELATION_STORE,
  PLAZA_RELATION_BRAND,
  PLAZA_RELATION_ACTIVITY,
]

const tokenStore = useTokenStore()

/** null 就是不限关联类型（不发这一格），0 是「无关联」那一档真值 */
const keyword = ref('')
const relationType = ref<number | null>(null)

const messages = usePagedList<IShopPlazaMessage>((pageNum, pageSize) => {
  const query: { keyword?: string, relationType?: number } = {}
  const kw = keyword.value.trim()
  if (kw) {
    query.keyword = kw
  }
  if (relationType.value !== null) {
    query.relationType = relationType.value
  }
  return getPlazaPage(pageNum, pageSize, query)
}, PAGE_SIZE)

/** 点赞那一趟：一次只让一条在动（记的是哪一条），同详情页那颗的判据 */
const busyId = ref<number | null>(null)

/**
 * 推荐位那一块：读的还是列表那一条接口，只是多钉一格 isRecommend=1（后台勾了推荐的那几条）。
 * 窄屏没有「右侧」那一格，所以它摆在筛选和主列表中间，只列标题、行与行之间一条虚线隔开。
 * 默认收起（只剩标题那一行）—— 广场是无底的流，导流那一块不该一进来就占着半屏；要点的人自己展开。
 * 跟下面那串不是一份数据：换筛选、往下翻都不重捞它；一条没捞着或那一趟失败就整块不画。
 */
const RECOMMEND_SIZE = 8
const recommends = ref<IShopPlazaMessage[]>([])
const recOpen = ref(false)

async function loadRecommended() {
  try {
    recommends.value = (await getRecommendedPlazaPage(RECOMMEND_SIZE)).records || []
  }
  catch {
    // 导流块：那一趟失败就什么都不画，主列表照旧能用（连一句报错都不给，不值得占一行）
  }
}

/** 发布者那一格：账号注销了联不出昵称，只剩 id */
function authorOf(one: IShopPlazaMessage) {
  const name = one.userName?.trim()
  if (name) {
    return name
  }
  return one.userId ? `已注销的用户 #${one.userId}` : '—'
}

/** 时间只到日，秒在这一页没意义 */
function dateOf(one: IShopPlazaMessage) {
  return (one.publishTime || one.createTime || '').slice(0, 10)
}

function toDetail(one: IShopPlazaMessage) {
  uni.navigateTo({ url: `/pages/plaza/detail?id=${one.id}` })
}

function pickRelation(value: number | null) {
  if (relationType.value === value) {
    return
  }
  relationType.value = value
  void messages.reload()
}

async function toggleLike(one: IShopPlazaMessage) {
  if (!tokenStore.updateNowTime().hasLogin) {
    toLoginPage()
    return
  }
  if (busyId.value !== null) {
    return
  }
  busyId.value = one.id
  try {
    // 那条端点一颗管两个方向，回的就是按完之后的状态，本地把 selfLiked 和计数一起改，不用再捞一趟
    const before = one.selfLiked === true
    const liked = await togglePlazaLike(one.id)
    if (before !== liked) {
      one.selfLiked = liked
      one.likeCount = Math.max(0, (one.likeCount ?? 0) + (liked ? 1 : -1))
    }
  }
  catch {
    // 拒回来那句由 http 层 toast，页面不再补第二句
  }
  finally {
    busyId.value = null
  }
}

/**
 * 分享：没接微信分享 SDK，也没有前台站地址可拼，所以复制的是这条消息的文字卡片（形状同详情页那颗）。
 * 记流水失败不该把「已经复制出去了」那声提示变成报错，trackPlazaShare 里已经把异常吞了。
 */
function share(one: IShopPlazaMessage) {
  const text = [plazaLabel(one), one.contentText?.trim(), '来自「商城广场」'].filter(Boolean).join('\n')
  uni.setClipboardData({
    data: text,
    success: () => {
      uni.showToast({ title: '这条的内容已复制到剪贴板', icon: 'none' })
      if (tokenStore.hasLogin) {
        void trackPlazaShare(one.id)
      }
    },
  })
}

function toPublish() {
  if (!tokenStore.updateNowTime().hasLogin) {
    toLoginPage()
    return
  }
  uni.navigateTo({ url: '/pages/plaza/publish' })
}

onLoad(() => {
  void messages.load()
  void loadRecommended()
})

/**
 * 从发布页回来重捞第一页：刚发那一条还在待审核，本来也不会上这一串，
 * 但别人在这期间发的、以及总数都会挪，回来一趟看一眼不该还是旧的。
 */
onShow(() => {
  if (messages.page > 0) {
    void messages.reload()
  }
})

onPullDownRefresh(async () => {
  await messages.reload()
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  if (messages.hasMore) {
    void messages.load()
  }
})
</script>

<template>
  <view class="bg-[#f5f6f7] pb-192rpx">
    <view class="mx-24rpx mt-24rpx">
      <view class="text-[26rpx] text-gray-500">
        大家发的图文动态，往下滑接着翻
      </view>
    </view>

    <!-- 筛选那一排：关键字要按下搜索才生效（边打字边捞没法看），关联类型点胶囊就换档整块重捞 -->
    <view class="mx-24rpx mt-16rpx rounded-xl bg-white px-16rpx py-16rpx">
      <wd-search
        v-model="keyword"
        placeholder="搜标题、正文或发布者"
        hide-cancel
        @search="messages.reload()"
        @clear="messages.reload()"
      />
      <view class="flex flex-wrap gap-16rpx px-16rpx pb-8rpx">
        <view
          hover-class="fx-tap"
          class="fx-press rounded-full px-24rpx py-8rpx text-[24rpx]"
          :class="relationType === null ? 'bg-[#d14328] text-white' : 'bg-gray-100 text-gray-600'"
          @click="pickRelation(null)"
        >
          全部
        </view>
        <view
          v-for="type in RELATION_TYPES"
          :key="type"
          hover-class="fx-tap"
          class="fx-press rounded-full px-24rpx py-8rpx text-[24rpx]"
          :class="relationType === type ? 'bg-[#d14328] text-white' : 'bg-gray-100 text-gray-600'"
          @click="pickRelation(type)"
        >
          {{ PLAZA_RELATION_TEXT[type] }}
        </view>
      </view>
    </view>

    <!-- 推荐位那一块：摆在筛选和主列表中间，默认收起只剩标题那一行，点了往下铺成一列、只印标题。
         行与行之间一条虚线：UnoCSS 的 preflight 被注释掉了，border-* 那几颗类画不出线，所以这条线只能写行内样式；
         而 uni-h5 又不换算行内样式里的 rpx（实测 1rpx 出来的边框宽度是 0），所以这一格给的是 px。
         这一块的行点进去就是详情页，跟下面那串列表各走各的 —— 换筛选不动它。 -->
    <view v-if="recommends.length" class="mx-24rpx mt-24rpx rounded-xl bg-white px-24rpx py-20rpx">
      <view hover-class="fx-tap" class="fx-press flex items-center justify-between" @click="recOpen = !recOpen">
        <view class="text-[28rpx] text-[#333] font-medium">
          推荐
        </view>
        <view class="text-[24rpx] text-gray-400">
          {{ recOpen ? '收起 ▴' : `${recommends.length} 条 ▾` }}
        </view>
      </view>
      <view v-show="recOpen">
        <view
          v-for="(one, i) in recommends"
          :key="one.id"
          hover-class="fx-tap"
          class="fx-press py-16rpx text-[26rpx] text-[#333]"
          :style="i ? 'border-top: 1px dashed #e5e5e5' : ''"
          @click="toDetail(one)"
        >
          <view class="truncate">
            {{ plazaLabel(one) }}
          </view>
        </view>
      </view>
    </view>

    <ListState
      class="pt-8rpx"
      :loading="messages.loading"
      :error="messages.error"
      :count="messages.rows.length"
      :has-more="messages.hasMore"
      empty-text="广场上还没有一条消息"
      @retry="messages.load()"
    >
      <view
        v-for="one in messages.rows"
        :key="one.id"
        hover-class="fx-tap"
        class="fx-press mx-24rpx mb-24rpx rounded-xl bg-white p-24rpx"
        @click="toDetail(one)"
      >
        <view class="flex items-center text-[22rpx] text-gray-400">
          <text class="mr-12rpx truncate text-[26rpx] text-[#666] font-medium">
            {{ authorOf(one) }}
          </text>
          <text v-if="dateOf(one)" class="mr-12rpx shrink-0">
            {{ dateOf(one) }}
          </text>
          <text v-if="one.isTop === 1" class="mr-8rpx shrink-0 rounded bg-red-50 px-10rpx py-2rpx text-[20rpx] text-[#d14328]">
            置顶
          </text>
          <text v-if="one.isRecommend === 1" class="shrink-0 rounded bg-amber-50 px-10rpx py-2rpx text-[20rpx] text-amber-600">
            推荐
          </text>
        </view>

        <!-- 配图 + 标题/摘要：配图是发布人自己上传的那一组里的第一张（一张没传就在同一格铺标题首字，右边文字才不会跟着忽左忽右）；
             整组铺在详情页正文下方，卡片这一格只取第一张 -->
        <view class="mt-16rpx flex">
          <image
            v-if="plazaLeadImage(one)"
            :src="plazaLeadImage(one)"
            mode="aspectFill"
            class="h-[160rpx] w-[160rpx] shrink-0 rounded-lg bg-gray-100"
          />
          <view
            v-else
            class="h-[160rpx] w-[160rpx] shrink-0 rounded-lg bg-gray-100 text-center text-[48rpx] text-gray-400 leading-[160rpx]"
          >
            {{ plazaLabel(one).charAt(0) }}
          </view>
          <view class="ml-24rpx min-w-0 flex-1">
            <view class="truncate text-[30rpx] text-[#333] font-medium">
              {{ plazaLabel(one) }}
            </view>
            <!-- 没写标题的那些，摘要已经顶到上面那行当了题，这里再铺一遍就是重复 -->
            <view v-if="one.title && one.contentText" class="mt-8rpx text-[26rpx] text-gray-600 leading-[36rpx]">
              {{ one.contentText }}
            </view>
          </view>
        </view>

        <!-- 动作那一排：点赞和分享在列表里就能办（走的动作跟详情页同一份判据，在哪儿点都算同一次）。
             两颗都挂 .stop —— 卡片整块是可点的，不拦住点一下按钮还会顺带跳进详情页。
             描边一律不用（这个工程的 UnoCSS 注释掉了 preflight，border 工具类画不出线），所以选中态靠底色。 -->
        <view class="mt-16rpx flex items-center text-[22rpx] text-gray-400">
          <view
            hover-class="fx-tap"
            class="fx-press mr-16rpx shrink-0 rounded-full px-24rpx py-8rpx text-[24rpx]"
            :class="one.selfLiked ? 'bg-red-50 text-[#d14328]' : 'bg-gray-100 text-gray-600'"
            @click.stop="toggleLike(one)"
          >
            {{ one.selfLiked ? '已赞' : '点赞' }} · {{ one.likeCount ?? 0 }}
          </view>
          <view
            hover-class="fx-tap"
            class="fx-press mr-24rpx shrink-0 rounded-full bg-gray-100 px-24rpx py-8rpx text-[24rpx] text-gray-600"
            @click.stop="share(one)"
          >
            分享 · {{ one.shareCount ?? 0 }}
          </view>
          <text>{{ one.commentCount ?? 0 }} 评论</text>
          <text class="ml-auto text-[#d14328]">看全文和评论</text>
        </view>
      </view>
    </ListState>

    <!--
      右下角那颗悬浮发布：这页在自定义 tabbar 上面，bar 自己那层是「100rpx + 安全区」（见 src/tabbar/index.vue），
      所以 bottom 先让出那一截，安全区那段再用 margin-bottom 往上顶 ——
      把 env() 写进 bottom 的 calc 里，rpx 那一段在 H5 侧的换算不认（同购物车合计栏的处理）。
    -->
    <view hover-class="fx-tap" class="plaza-fab fx-press" @click="toPublish">
      <text class="text-[48rpx] text-white font-light leading-none">
        ＋
      </text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.plaza-fab {
  position: fixed;
  right: 32rpx;
  bottom: 160rpx;
  z-index: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background-color: #d14328;
  box-shadow: 0 6rpx 20rpx rgb(0 0 0 / 20%);
  margin-bottom: env(safe-area-inset-bottom);
}
</style>
