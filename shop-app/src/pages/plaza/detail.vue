<script lang="ts" setup>
import type { IShopPlazaMessage } from '@/api/types/shop'
import { COMMENT_TYPE_PLAZA } from '@/api/comment'
import { getPlazaDetail, PLAZA_AUDIT_PENDING, PLAZA_AUDIT_REJECTED, plazaColumnImages, plazaImages, plazaLabel, plazaSegments, relationUrl, togglePlazaLike, trackPlazaShare } from '@/api/plaza'
import CommentSection from '@/components/CommentSection.vue'
import { useTokenStore } from '@/store/token'
import { toLoginPage } from '@/utils/toLoginPage'

defineOptions({
  name: 'PlazaDetail',
})

definePage({
  style: {
    navigationBarTitleText: '广场消息',
    enablePullDownRefresh: true,
  },
})

/**
 * 一条广场消息：形状照 shop-web 的 /plaza/{id}（他点名的范本）。
 *
 * 只有这一趟读得到 content；作者本人点得进自己那条待审核/被拒的（别人读不到，后端直接报一句话），
 * 所以顶上要给「还没上广场」那一行说法。底下的评论区给完整态（带排序、输入框和翻页），不是预览态 ——
 * 这一页就是这条消息名下列表的落点；没审核通过的这一栏不挂，评论也发不出去。
 *
 * highlight 由消息中心那条点赞/评论点进来时带上（见 utils/notification 里拼的那条路由），
 * 递给 CommentSection 让它滚到、亮一下那一条。
 *
 * 这一页有两组图：正文里内嵌的那些排在原处，plaza_image 那一串整组铺在正文下面。
 * App 发布时这两组本来就是同一批图（页面只有一排图片选择器），所以同一条会看着像重复了一遍 ——
 * 各用各的预览列表，不混在一起翻。
 */
const messageId = ref(0)
const highlightId = ref<number | undefined>()
const detail = ref<IShopPlazaMessage | null>(null)
const loadError = ref('')
const busy = ref(false)
const tokenStore = useTokenStore()

const images = computed(() => plazaImages(detail.value?.content))
/** plaza_image 那一列上的「广场消息图」：跟正文里内嵌的那批各走各的预览列表 */
const columnImages = computed(() => plazaColumnImages(detail.value))
const link = computed(() => (detail.value ? relationUrl(detail.value.relationType, detail.value.relationId) : ''))

/** 标题没填就拿正文摘要当题，两格都空给兜底名（和列表卡片同一个说法） */
const heading = computed(() => (detail.value ? plazaLabel(detail.value) : ''))

const author = computed(() => {
  const one = detail.value
  if (!one) {
    return ''
  }
  const name = one.userName?.trim()
  return name || (one.userId ? `已注销的用户 #${one.userId}` : '—')
})

/** 上了广场的念「发布于」，还在审核/被拒的只有作者本人看得得到，那时念「提交于」 */
const timeText = computed(() => {
  const one = detail.value
  if (!one) {
    return ''
  }
  const time = (one.publishTime || one.createTime || '').slice(0, 16)
  if (!time) {
    return ''
  }
  return one.publishTime ? `发布于 ${time}` : `提交于 ${time}`
})

const auditTip = computed(() => {
  const one = detail.value
  if (!one) {
    return ''
  }
  if (one.auditStatus === PLAZA_AUDIT_PENDING) {
    return '这条还在审核中，通过后才会出现在广场上'
  }
  return one.auditStatus === PLAZA_AUDIT_REJECTED ? '这条没有通过审核' : ''
})

/**
 * 正文按 <img> 拆成「富文本段 + 图段」交替的那一列：图就排在它原来所在的位置，
 * 只是换成 <image> 组件画 —— rich-text 里的图点不动也放不大，非要预览就得从 HTML 里摘出来。
 */
const segments = computed(() => plazaSegments(detail.value?.content))

/** 剩下那点摘不走的图（非 http 的地址）还在 rich-text 里：它不吃外部 CSS，不钉一行内样式会把屏幕撑爆 */
function nodesOf(html: string) {
  return html.replace(/<img(\s|>)/gi, '<img style="max-width:100%;display:block;"$1')
}

function previewImage(index: number) {
  uni.previewImage({ urls: images.value, current: index })
}

/** 正文下面那一组的预览：递给查看器的是 plaza_image 那一串，不带正文里那些 */
function previewColumnImage(index: number) {
  uni.previewImage({ urls: columnImages.value, current: index })
}

/** 广场那张是 tabbar 页，回它只能 switchTab（navigateTo/redirectTo 到 tab 页会被拒） */
function backToList() {
  uni.switchTab({ url: '/pages/plaza/plaza' })
}

function toRelation() {
  if (link.value) {
    uni.navigateTo({ url: link.value })
  }
}

async function toggleLike() {
  const one = detail.value
  if (!one || busy.value) {
    return
  }
  if (!tokenStore.updateNowTime().hasLogin) {
    toLoginPage()
    return
  }
  busy.value = true
  try {
    // 那条端点一颗管两个方向，回的就是按完之后的状态，不用再回读一遍
    const liked = await togglePlazaLike(one.id)
    one.selfLiked = liked
    one.likeCount = Math.max(0, (one.likeCount ?? 0) + (liked ? 1 : -1))
  }
  catch {
    // 拒回来那句由 http 层 toast，页面不再补第二句
  }
  finally {
    busy.value = false
  }
}

/**
 * 分享：App 这一端没接微信分享 SDK，也没有前台站地址可拼，所以复制的是这条消息的文字卡片
 *（跟活动页外链复制同一个退一步的做法），复制成功再往流水里记一行 5 复制链接那一档。
 * 记流水失败不该把「已经复制出去了」那声提示变成报错，trackPlazaShare 里已经把异常吞了。
 */
function share() {
  const one = detail.value
  if (!one) {
    return
  }
  const text = [heading.value, one.contentText?.trim(), '来自「商城广场」'].filter(Boolean).join('\n')
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

async function loadDetail() {
  try {
    detail.value = await getPlazaDetail(messageId.value)
    uni.setNavigationBarTitle({ title: plazaLabel(detail.value) })
  }
  catch {
    // 不是自己发的又没上广场的那一条、根本没这个 id：后端回一句话，http 层已经 toast
    loadError.value = '这条消息看不到了，也许还没通过审核'
    detail.value = null
  }
}

onLoad((query) => {
  messageId.value = Number(query?.id) || 0
  const highlight = Number(query?.highlight)
  highlightId.value = Number.isInteger(highlight) && highlight > 0 ? highlight : undefined
  if (!messageId.value) {
    loadError.value = '参数不对，没拿到消息 id'
    return
  }
  loadDetail()
})

onPullDownRefresh(async () => {
  if (messageId.value) {
    await loadDetail()
  }
  uni.stopPullDownRefresh()
})
</script>

<template>
  <view class="bg-[#f5f6f7] pb-48rpx pt-24rpx">
    <view v-if="loadError" class="py-160rpx text-center text-[26rpx] text-gray-400">
      {{ loadError }}
      <view hover-class="fx-tap" class="fx-press mt-24rpx text-[26rpx] text-[#d14328]" @click="backToList">
        回广场
      </view>
    </view>

    <template v-else-if="detail">
      <view v-if="auditTip" class="mx-24rpx mb-24rpx rounded-xl bg-amber-50 px-24rpx py-20rpx text-[26rpx] text-amber-800 leading-40rpx">
        {{ auditTip }}
        <view v-if="detail.auditRemark" class="mt-8rpx">
          审核说明：{{ detail.auditRemark }}
        </view>
      </view>

      <view class="mx-24rpx mb-24rpx rounded-xl bg-white px-32rpx py-32rpx">
        <view class="text-[36rpx] text-[#333] font-medium">
          {{ heading }}
        </view>
        <view class="mt-16rpx flex flex-wrap items-center text-[24rpx] text-gray-400">
          <text class="mr-16rpx text-[26rpx] text-[#666]">{{ author }}</text>
          <text v-if="timeText">{{ timeText }}</text>
          <text v-if="detail.isTop === 1" class="ml-16rpx rounded bg-red-50 px-10rpx py-2rpx text-[20rpx] text-[#d14328]">
            置顶
          </text>
          <text v-if="detail.isRecommend === 1" class="ml-8rpx rounded bg-amber-50 px-10rpx py-2rpx text-[20rpx] text-amber-600">
            推荐
          </text>
        </view>
        <!-- 关联对象：这一期发布页没有挂东西的入口，所以只有后台录入的那几条会有 -->
        <view
          v-if="link"
          hover-class="fx-tap"
          class="fx-press mt-16rpx truncate text-[26rpx] text-[#d14328]"
          @click="toRelation"
        >
          相关：{{ detail.relationName || '—' }}
        </view>
      </view>

      <!-- 正文按图切段排在原处：文字那几段走 rich-text，图那几段走 <image>（点一张放大，整组左右滑） -->
      <view v-if="segments.length" class="mx-24rpx mb-24rpx rounded-xl bg-white p-32rpx">
        <template v-for="(one, index) in segments" :key="index">
          <image
            v-if="one.type === 'image'"
            hover-class="fx-tap"
            :src="one.url"
            mode="widthFix"
            class="fx-press my-16rpx w-full rounded-lg bg-gray-100"
            @click="previewImage(one.index)"
          />
          <rich-text v-else class="text-[28rpx] text-[#444] leading-56rpx" :nodes="nodesOf(one.html)" />
        </template>
      </view>
      <view v-else class="mx-24rpx mb-24rpx py-128rpx text-center text-[26rpx] text-gray-400">
        这条没有正文
      </view>

      <!-- 正文之外那一组「广场消息图」：plaza_image 那一串整组铺在正文下面，点一张放大、这一组自己左右滑 -->
      <view v-if="columnImages.length" class="mx-24rpx mb-24rpx rounded-xl bg-white p-32rpx">
        <view class="text-[28rpx] text-[#333] font-medium">
          配图
        </view>
        <view class="mt-16rpx flex flex-wrap">
          <image
            v-for="(url, index) in columnImages"
            :key="url"
            hover-class="fx-tap"
            :src="url"
            mode="aspectFill"
            class="fx-press mb-16rpx mr-16rpx h-160rpx w-160rpx rounded-lg bg-gray-100"
            @click="previewColumnImage(index)"
          />
        </view>
      </view>

      <view class="mx-24rpx mb-24rpx flex items-center">
        <view
          hover-class="fx-tap"
          class="fx-press mr-24rpx rounded-full px-32rpx py-12rpx text-[26rpx]"
          :class="detail.selfLiked ? 'bg-[#d14328] text-white' : 'bg-white text-gray-600'"
          @click="toggleLike"
        >
          {{ detail.selfLiked ? '已赞' : '点赞' }} · {{ detail.likeCount ?? 0 }}
        </view>
        <view hover-class="fx-tap" class="fx-press rounded-full bg-white px-32rpx py-12rpx text-[26rpx] text-gray-600" @click="share">
          分享 · {{ detail.shareCount ?? 0 }}
        </view>
      </view>

      <!-- 已发布的那一条才挂评论区；「共 N 条」那一行 CommentSection 自己会铺，这里不再补标题 -->
      <view v-if="detail.auditStatus === 1" class="mx-24rpx rounded-xl bg-white p-32rpx">
        <CommentSection :comment-type="COMMENT_TYPE_PLAZA" :business-id="detail.id" :highlight-id="highlightId" />
      </view>
    </template>

    <view v-else class="py-128rpx text-center text-[26rpx] text-gray-400">
      加载中…
    </view>
  </view>
</template>
