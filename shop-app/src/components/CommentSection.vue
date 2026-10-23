<script lang="ts" setup>
import type { ICommentSegment } from '@/api/comment'
import type { IShopCommentRow } from '@/api/types/shop'
import { COMMENT_EMOJIS, COMMENT_MAX_IMAGES, COMMENT_MAX_LENGTH, commentImageMark, commentImageUrls, commentText, deleteComment, getComments, parseCommentContent, publishComment, removeCommentImage, toggleCommentLike } from '@/api/comment'
import { uploadImage } from '@/api/file'
import { useUserStore } from '@/store'
import { useTokenStore } from '@/store/token'
import { toLoginPage } from '@/utils/toLoginPage'

defineOptions({
  name: 'CommentSection',
})

/**
 * 评论区：商品/门店/品牌/广场四处详情页底下那一大块共用这一份，只吃「评的是哪一个」两格，外加详情页用 preview 收成预览态。
 *
 * 取数、发表、回复、点赞、删除都在这块里，页面只管挂上去、给个标题和外层壳（卡片还是通栏由页面定，
 * 所以这一颗自己不带左右边距和背景）。文案先直写中文：App 这一端还没铺多语言。
 * 错误一律靠 http 层 toast 服务端那句话，块里只留「加载失败 + 重试」这种页面自己的状态。
 *
 * 表情就是插进正文的普通字符（不另存一列、也不转成「[微笑]」那种代码，一个 emoji 前后端都算 2 字）；
 * 图先走 uploadImage(source=comment) 传上去拿回地址，再把 `[图片](https://…)` 拼在正文末尾，
 * 和文字存在同一格 content 里（不建图片列，「发表」也只送 content）。
 * 画的时候把正文拆成「文字段 + 图片段」交替铺：文字段仍按纯文本铺（富文本渲染等于把访客敲的标记当代码执行），
 * 图片段只把地址交给 image 的 src。字数那道闸只数摘掉图片串之后的文字，跟服务层同一口径。
 */
const props = defineProps<{
  commentType: number
  businessId: number
  /**
   * 预览态（详情页那一档）：只捞最热的前 5 条，不给排序、不给翻页，
   * 底下换成一颗「查看全部 N 条评论」跳 /pages/comment/list —— 详情页是来买东西的，铺一整页评论会把货挤下去。
   * 点赞和回复在这两档里都给（人看到那一条就想当场回一句，不该先把他赶去另一页）。
   */
  preview?: boolean
  /**
   * 「全部评论」页从消息里带过来的那一条：捞到之后滚到它、高亮两秒。
   * 页是一路往下捞到它为止（回复和它的顶层行同一页），压根没这条（被删了、类型对不上）就什么都不做。
   */
  highlightId?: number
}>()

const PAGE_SIZE = 10
/** 预览态只铺这一页：5 条最热，剩下的交给「全部评论」那一页 */
const PREVIEW_SIZE = 5
const pageSize = () => (props.preview ? PREVIEW_SIZE : PAGE_SIZE)

/** 排序两档：置顶那几条不管哪一档都打头（服务端排的）；预览态没有这一排，钉死在「最热」 */
const SORTS = [
  { key: 'time', label: '最新' },
  { key: 'like', label: '最热' },
] as const
type SortKey = (typeof SORTS)[number]['key']

const tokenStore = useTokenStore()
const userStore = useUserStore()

const rows = ref<IShopCommentRow[]>([])
const loading = ref(true)
const loadError = ref('')
const pageNum = ref(0)
const pages = ref(0)
const total = ref(0)
// 预览态没有排序那一排，钉死在「最热」；完整态默认最新
const sortType = ref<SortKey>(props.preview ? 'like' : 'time')

const draft = ref('')
const posting = ref(false)
const replyTo = ref<IShopCommentRow | null>(null)
/** 正在处理中的那一条（点赞/删除共用一颗，点了就别让它再点一次） */
const busyId = ref<number | null>(null)
const moreLoading = ref(false)
/** 表情面板开合：点一颗就往正文末尾追加，不另存一份 */
const emojiOpen = ref(false)
const uploading = ref(false)

const signedIn = computed(() => tokenStore.hasLogin)
const myId = computed(() => userStore.userInfo?.userId ?? null)
const hasMore = computed(() => !props.preview && pageNum.value < pages.value)
/** 输入框里已经拼进去的那几串图：待发缩略图、张数、剩余槽位都从正文拆出来读，不另存一份 */
const draftSegments = computed(() => parseCommentContent(draft.value))
const draftImages = computed(() => draftSegments.value.filter(seg => seg.type === 'image'))
/** 字数只数文字：图片那一整串（连语法带地址）摘掉再量，地址七八十个字符，算进去两条图就把额度吃光 */
const textLength = computed(() => commentText(draft.value).length)
// 传图期间不让发表：那一串地址还没拼齐就提交，落库的张数会少于页面上看到的
const canPost = computed(() => signedIn.value && !posting.value && !uploading.value && commentText(draft.value).trim().length > 0 && textLength.value <= COMMENT_MAX_LENGTH)
const remain = computed(() => Math.max(0, COMMENT_MAX_LENGTH - textLength.value))
const leftSlots = computed(() => COMMENT_MAX_IMAGES - draftImages.value.length)

/**
 * 一棵评论树铺成一条串：回复的回复一直往下铺，模板只写一份行形状，缩进靠 indent。
 * indent 到第二格就封顶 —— 屏宽就这么点，层层缩下去会把正文挤成一条竖缝，深到第三层以后只看「回复 @某人」那一句。
 * 正文的拆串结果单独算一份（不摊成 row 的副本）—— 点赞那颗改的是 rows 里那个响应式对象本身，
 * 改成副本的话按完赞数就丢了
 */
const INDENT_CLASS = ['', 'pl-56rpx', 'pl-112rpx']
interface CommentItem {
  key: string
  row: IShopCommentRow
  segments: ICommentSegment[]
  images: string[]
  indent: number
}
const items = computed(() => rows.value.flatMap((row) => {
  const walk = (r: IShopCommentRow, depth: number): CommentItem[] => [
    {
      key: `${depth ? 'c' : 't'}${r.id}`,
      row: r,
      segments: parseCommentContent(r.content),
      images: commentImageUrls(r.content),
      indent: Math.min(depth, INDENT_CLASS.length - 1),
    },
    ...(r.children ?? []).flatMap(child => walk(child, depth + 1)),
  ]
  return walk(row, 0)
}))

/** 列表里的一处位置：一条评论，或者「发表/回复」那一格（整块模板只有一份，位置由这里定） */
type FlowLine = ({ kind: 'comment' } & CommentItem) | { kind: 'composer', key: 'composer', indent: number }
const flow = computed<FlowLine[]>(() => {
  const lines: FlowLine[] = items.value.map(item => ({ kind: 'comment', ...item }))
  // 预览态平时不排输入框那一格（要新发表就去「全部评论」那一页），但点了「回复」就得把它摆出来 —— 详情页也能回一条
  if (props.preview && !replyTo.value) {
    return lines
  }
  const target = replyTo.value
  const at = target ? lines.findIndex(line => line.kind === 'comment' && line.row.id === target.id) : -1
  // 被回复那一条不在当前这一页（换了排序、或那一支刚被删）时退回最前，总比输入框凭空没了强
  const above = at < 0 ? null : items.value[at]
  lines.splice(at + 1, 0, { kind: 'composer', key: 'composer', indent: above ? above.indent : 0 })
  return lines
})

/** 加购、下单、评论都吃登录态（服务层第一句就 requireLoginUser），这里先拦一道，免得白报一次错 */
function ensureLogin() {
  if (tokenStore.updateNowTime().hasLogin) {
    return true
  }
  toLoginPage()
  return false
}

async function loadPage(page: number) {
  const data = await getComments(props.commentType, props.businessId, page, pageSize(), sortType.value)
  const records = data.records ?? []
  if (page === 1) {
    rows.value = records
  }
  else {
    // 续页按 id 去过再拼：翻到一半服务端有新增/删除时，同一行会重复出现，:key 撞号画得乱
    const seen = new Set(rows.value.map(one => one.id))
    rows.value = [...rows.value, ...records.filter(one => !seen.has(one.id))]
  }
  pageNum.value = page
  pages.value = Number(data.pages ?? 0)
  total.value = Number(data.total ?? 0)
}

async function loadFirst() {
  if (!props.businessId) {
    return
  }
  loading.value = true
  try {
    await loadPage(1)
    loadError.value = ''
  }
  catch {
    loadError.value = '评论加载失败'
  }
  finally {
    loading.value = false
  }
}

async function loadMore() {
  if (moreLoading.value || !hasMore.value) {
    return
  }
  moreLoading.value = true
  try {
    await loadPage(pageNum.value + 1)
  }
  catch {
    // 续页失败不动已有那一叠，按钮还在，可以再点一次
    uni.showToast({ title: '评论没Load出来，再试一次', icon: 'none' })
  }
  finally {
    moreLoading.value = false
  }
}

/**
 * 高亮定位（只有「全部评论」页带 highlightId 才会走）：从第一页一路往下捞到那一条为止，
 * 再滚到它那一行、亮两秒。回复和它的顶层行是同一趟递归 CTE 捞回来的，所以一定在同一页，往下捞就找得到。
 * 捞完还没找到（那条被删了、或者类型对不上）就什么都不做 —— 这一页本来就正常停在最前面，不该报一句假错。
 */
const instance = getCurrentInstance()
const highlightedId = ref<number | null>(null)
const HIGHLIGHT_MAX_PAGES = 20

function hasRow(id: number) {
  return items.value.some(item => item.row.id === id)
}

/** 滚到那一行：整页评论吃的是页面滚动（不是 scroll-view），只能现查节点位置 —— uni 这边没有 scrollIntoView */
function scrollToRow(id: number) {
  const query = instance ? uni.createSelectorQuery().in(instance) : uni.createSelectorQuery()
  let rect: any = null
  let scrollTop = 0
  query.select(`#comment-${id}`).boundingClientRect((res: any) => {
    rect = res
  })
  query.selectViewport().scrollOffset((res: any) => {
    scrollTop = Number(res?.scrollTop ?? 0)
  })
  query.exec(() => {
    if (!rect) {
      return
    }
    // 把那一行摆到屏幕中间：现在的滚动位置 + 它离视口顶的距离 - 半屏高
    const to = scrollTop + rect.top - uni.getSystemInfoSync().windowHeight / 2
    uni.pageScrollTo({ scrollTop: to > 0 ? to : 0, duration: 300 })
  })
}

async function locateHighlight() {
  const target = props.highlightId
  if (!target) {
    return
  }
  for (let guard = 0; guard < HIGHLIGHT_MAX_PAGES; guard++) {
    if (hasRow(target) || pageNum.value >= pages.value) {
      break
    }
    try {
      await loadPage(pageNum.value + 1)
    }
    catch {
      break
    }
  }
  if (!hasRow(target)) {
    return
  }
  highlightedId.value = target
  await nextTick()
  scrollToRow(target)
  setTimeout(() => {
    if (highlightedId.value === target) {
      highlightedId.value = null
    }
  }, 2000)
}

function pickSort(key: SortKey) {
  if (sortType.value === key || loading.value) {
    return
  }
  sortType.value = key
  // 换档后原来那条「回复 @某人」的指向已经不在这一页里了
  replyTo.value = null
  void loadFirst()
}

/** 一颗 emoji 占 2 字（前后端同一口径），装不下就不插，不等服务端拒 */
function insertEmoji(emoji: string) {
  if (textLength.value + emoji.length > COMMENT_MAX_LENGTH) {
    return
  }
  draft.value += emoji
}

/**
 * 选图：一次最多选「还剩几个坑」那么多张，选完立刻逐张传（串行，别一次掐着八九个请求打文件服务）。
 * 传回来的地址当场拼成一串 `[图片](url)` 追加进正文 —— 正文就是待发的那叠图，不再另存一串 URL。
 */
function chooseImages() {
  if (uploading.value || leftSlots.value <= 0) {
    return
  }
  uni.chooseImage({
    count: leftSlots.value,
    sizeType: ['compressed'],
    success: async (res) => {
      const paths = (res.tempFilePaths as string[]) ?? []
      uploading.value = true
      try {
        for (const path of paths.slice(0, leftSlots.value)) {
          const url = await uploadImage(path, 'comment')
          const mark = commentImageMark(url)
          // 同一张图选两次会拿到同一个地址，正文里已经有这一串就跳过（否则画出两张一模一样的）
          if (!draft.value.includes(mark)) {
            draft.value += mark
          }
        }
      }
      catch {
        // 上传失败那句由 uploadImage 抛，这里只提示一次：http 层那条 toast 管不到 uni.uploadFile
        uni.showToast({ title: '图片上传失败，再试一次', icon: 'none' })
      }
      finally {
        uploading.value = false
      }
    },
  })
}

/** 撤掉待发的那张图：按正文里第几张来数，摘掉的是连语法的一整串，剩下的文字不动 */
function removePicked(index: number) {
  draft.value = removeCommentImage(draft.value, index)
}

/** 看大图：一组 URL 从第几张起看，左右滑由 uni 那一层自带（H5/App/小程序三端都有这条 API） */
function previewImages(urls: string[], index: number) {
  uni.previewImage({ urls, current: index })
}

async function submit() {
  if (!ensureLogin() || !canPost.value) {
    return
  }
  posting.value = true
  try {
    await publishComment({
      commentType: props.commentType,
      businessId: props.businessId,
      content: draft.value.trim(),
      replyToId: replyTo.value?.id,
    })
    draft.value = ''
    emojiOpen.value = false
    replyTo.value = null
    // 发完切回时间档：新评论在「最热」那一档的第一页很可能排不到，切回去才看得见自己刚发的那条。
    // 预览态不切 —— 它头顶那句写的就是「热门评论」，不能因为回了一条就偷偷换成别的口径
    if (!props.preview) {
      sortType.value = 'time'
    }
    await loadFirst()
    uni.showToast({ title: '发表成功', icon: 'success' })
  }
  catch {
    // 内容空、超 500 字、图片地址不合法、对象已停用等由服务端回话，http 层已 toast
  }
  finally {
    posting.value = false
  }
}

async function handleLike(row: IShopCommentRow) {
  if (!ensureLogin() || busyId.value === row.id) {
    return
  }
  busyId.value = row.id
  try {
    const on = await toggleCommentLike(row.id)
    // 接口只回「按完之后是哪一面」，数不在回包里，跟着方向本地 ±1
    const delta = on === row.isLike ? 0 : on ? 1 : -1
    row.isLike = on
    row.likeCount = Math.max(0, (row.likeCount ?? 0) + delta)
  }
  catch {
    // 评论已删、重复点赞等由服务端回话
  }
  finally {
    busyId.value = null
  }
}

/** 点「回复」：输入框那一格跟着搬到这条的下面、和它同级缩进（位置由 flow 定）—— 框子钉在列表顶上的话，滚到深处点回复就看不见在哪儿敲 */
function pickReply(row: IShopCommentRow) {
  if (!ensureLogin()) {
    return
  }
  replyTo.value = row
}

/** 只有作者本人删得到（归属那道闸在服务层，这一格只决定画不画按钮） */
function canDelete(row: IShopCommentRow) {
  return signedIn.value && row.userId != null && row.userId === myId.value
}

function handleDelete(row: IShopCommentRow) {
  uni.showModal({
    title: '删除评论',
    content: '删掉后这条评论和它名下的回复都不再显示，确定要删吗？',
    confirmText: '删除',
    success: (res) => {
      if (res.confirm) {
        void doDelete(row)
      }
    },
  })
}

async function doDelete(row: IShopCommentRow) {
  if (busyId.value === row.id) {
    return
  }
  busyId.value = row.id
  try {
    await deleteComment(row.id)
    if (replyTo.value?.id === row.id) {
      replyTo.value = null
    }
    await loadFirst()
    uni.showToast({ title: '已删除', icon: 'success' })
  }
  catch {
    // 不是自己发的、已经删过的后端回同一句话，http 层已 toast
  }
  finally {
    busyId.value = null
  }
}

/**
 * 跳「全部评论」那一页：commentType + businessId 一起递过去（一列指四张表，少了类型会撞号）。
 * 预览态底下那颗按钮走这一条，页面本身挂的是同一份组件、只是不给 preview。
 */
function viewAll() {
  uni.navigateTo({
    url: `/pages/comment/list?type=${props.commentType}&id=${props.businessId}`,
  })
}

onMounted(() => {
  // 第一页捞完再去对 highlight：那一行很可能不在第一页（消息里点进来时人是去找某一条，不是看最热的）
  void loadFirst().then(locateHighlight)
})

// 四处详情页都是先取头部、id 晚一点才给到，id 一到就得自己捞一趟
watch(() => props.businessId, (id) => {
  if (id) {
    void loadFirst()
  }
})

// 登录回来要重捞一趟：点赞那面空心/实心的状态、删除那颗按钮都跟着登录态走
watch(signedIn, () => {
  void loadFirst()
})
</script>

<template>
  <view>
    <view class="flex items-center justify-between">
      <!-- 预览态不给排序（那 5 条钉死在「最热」），左边这一句只说明它们是怎么挑出来的 -->
      <text v-if="preview" class="text-[26rpx] text-gray-900 font-medium">
        热门评论
      </text>
      <view v-else class="flex gap-48rpx">
        <view
          v-for="one in SORTS"
          :key="one.key"
          hover-class="fx-tap"
          class="fx-press text-[26rpx]"
          :class="sortType === one.key ? 'text-[#d14328] font-medium' : 'text-gray-500'"
          @click="pickSort(one.key)"
        >
          {{ one.label }}
        </view>
      </view>
      <text class="text-[24rpx] text-gray-400">
        共 {{ total }} 条
      </text>
    </view>

    <!-- 一条串铺到底：「发表/回复」那一格也在里面，位置跟着 flow 走（没在回复时排最前，回复时紧跟被回复那一条） -->
    <view v-for="line in flow" :key="line.key">
      <!-- 发表这一格：没登录时不能只留个框让人白敲，直接把整块换成一句「登录后发表评论」 -->
      <view
        v-if="line.kind === 'composer' && signedIn"
        class="mt-24rpx"
        :class="line.indent ? INDENT_CLASS[line.indent] : ''"
      >
        <view
          v-if="replyTo"
          class="mb-12rpx flex items-center justify-between rounded-lg bg-gray-50 px-24rpx py-12rpx text-[24rpx] text-gray-500"
        >
          <text class="min-w-0 flex-1 truncate">回复 @{{ replyTo.nickname || '匿名用户' }}</text>
          <text hover-class="fx-tap" class="fx-press ml-16rpx shrink-0 text-[#d14328]" @click="replyTo = null">
            取消
          </text>
        </view>
        <!-- 这一格不收 maxlength：正文里现在连着 [图片](url) 那一整串，地址七八十个字符，
             按原始长度限死的话选两张图就打不了字了；字数那道闸只数摘掉图片串之后的文字（右边那句），超了发不出去 -->
        <view class="comment-box px-24rpx py-16rpx">
          <wd-textarea
            v-model="draft"
            placeholder="说说你的想法…"
            :no-border="true"
            :auto-height="true"
          />
        </view>

        <!-- 输入框底下这一排：左边表情和选图两颗，右边剩字数 + 发表 -->
        <view class="mt-12rpx flex items-center justify-between">
          <view class="flex items-center gap-24rpx text-[24rpx] text-gray-500">
            <text
              hover-class="fx-tap"
              class="fx-press"
              :class="emojiOpen ? 'text-[#d14328]' : ''"
              @click="emojiOpen = !emojiOpen"
            >
              🙂 表情
            </text>
            <text
              hover-class="fx-tap"
              class="fx-press"
              :class="leftSlots <= 0 || uploading ? 'text-gray-300' : ''"
              @click="chooseImages"
            >
              🖼 图片{{ draftImages.length ? ` ${draftImages.length}/${COMMENT_MAX_IMAGES}` : '' }}
            </text>
            <text v-if="uploading" class="text-[22rpx] text-gray-400">
              上传中…
            </text>
          </view>
          <view class="flex items-center gap-16rpx">
            <text class="text-[22rpx] text-gray-400">
              还能写 {{ remain }} 字
            </text>
            <wd-button size="small" :disabled="!canPost" :loading="posting" @click="submit">
              发表
            </wd-button>
          </view>
        </view>

        <!-- 表情面板：插进正文的是普通字符，所以这一格只往 draft 后面追加 -->
        <view v-if="emojiOpen" class="comment-box mt-12rpx flex flex-wrap px-16rpx py-12rpx">
          <text
            v-for="one in COMMENT_EMOJIS"
            :key="one"
            hover-class="fx-tap"
            class="fx-press w-72rpx py-8rpx text-center text-[40rpx]"
            @click="insertEmoji(one)"
          >
            {{ one }}
          </text>
        </view>

        <!-- 已经传好、拼进正文的那几张：点右上角那颗摘掉对应的整串语法（正文剩下的文字不动） -->
        <view v-if="draftImages.length" class="mt-12rpx flex flex-wrap gap-16rpx">
          <view v-for="(one, index) in draftImages" :key="one.url" class="relative">
            <image
              :src="one.url"
              mode="aspectFill"
              class="h-120rpx w-120rpx rounded-lg bg-gray-100"
              @click="previewImages(draftImages.map(seg => seg.url), index)"
            />
            <text
              hover-class="fx-tap"
              class="fx-press absolute right--8rpx top--8rpx h-40rpx w-40rpx rounded-full bg-[#333] text-center text-[24rpx] text-white leading-40rpx"
              @click.stop="removePicked(index)"
            >
              ×
            </text>
          </view>
        </view>
      </view>
      <view
        v-else-if="line.kind === 'composer'"
        hover-class="fx-tap"
        class="fx-press comment-box comment-box--dashed mt-24rpx px-24rpx py-24rpx text-center text-[26rpx] text-gray-400"
        @click="toLoginPage()"
      >
        登录后发表评论
      </view>

      <!-- 评论行：一条挨一条往下排，回复的回复紧跟它的父行；它名下要回复时输入框就插在这行下面 -->
      <view
        v-else
        :id="`comment-${line.row.id}`"
        class="mt-32rpx flex rounded-lg"
        :class="[line.indent ? INDENT_CLASS[line.indent] : '', highlightedId === line.row.id ? 'bg-[#fdf3e6]' : '']"
      >
        <image
          v-if="line.row.avatar"
          :src="line.row.avatar"
          mode="aspectFill"
          class="h-64rpx w-64rpx shrink-0 rounded-full bg-gray-100"
        />
        <view
          v-else
          class="h-64rpx w-64rpx shrink-0 rounded-full bg-gray-100 text-center text-[28rpx] text-gray-400 leading-64rpx"
        >
          {{ (line.row.nickname || '匿').charAt(0) }}
        </view>

        <view class="ml-20rpx min-w-0 flex-1">
          <view class="flex items-center gap-12rpx">
            <text class="truncate text-[26rpx] text-[#333]">
              {{ line.row.nickname || '匿名用户' }}
            </text>
            <text v-if="line.row.isStick === 1" class="shrink-0 rounded bg-[#fff4f2] px-12rpx py-2rpx text-[20rpx] text-[#d14328]">
              置顶
            </text>
          </view>

          <view v-if="line.row.replyNickname" class="mt-4rpx text-[22rpx] text-gray-400">
            回复 @{{ line.row.replyNickname }}
          </view>

          <!-- 正文按「文字段 + 图片段」交替铺出来：文字段仍是纯文本（服务端存的就是用户敲的字，
               富文本渲染等于把评论里的标记当代码执行，emoji 也在这一格里），图片段只把 [图片](url) 那个地址交给 image 的 src。
               点哪张就从哪张起看大图，左右滑由 uni.previewImage 自己给 -->
          <view class="mt-8rpx flex flex-wrap items-start gap-12rpx text-[28rpx] text-[#333] leading-[40rpx]">
            <template v-for="(seg, index) in line.segments" :key="index">
              <text v-if="seg.type === 'text'" class="max-w-full min-w-0 break-all">{{ seg.text }}</text>
              <image
                v-else
                :src="seg.url"
                mode="aspectFill"
                class="h-160rpx w-160rpx shrink-0 rounded-lg bg-gray-100"
                @click="previewImages(line.images, line.images.indexOf(seg.url))"
              />
            </template>
          </view>

          <view class="mt-8rpx flex flex-wrap items-center gap-x-24rpx text-[22rpx] text-gray-400">
            <text>{{ (line.row.createTime || '').slice(0, 16) }}</text>
            <text v-if="line.row.ipSource">IP属地：{{ line.row.ipSource }}</text>
          </view>

          <view class="mt-12rpx flex items-center gap-40rpx text-[24rpx] text-gray-500">
            <view
              hover-class="fx-tap"
              class="fx-press flex items-center gap-8rpx"
              :class="line.row.isLike ? 'text-[#d14328]' : ''"
              @click="handleLike(line.row)"
            >
              <text>{{ line.row.isLike ? '已赞' : '点赞' }}</text>
              <text v-if="line.row.likeCount">{{ line.row.likeCount }}</text>
            </view>
            <text hover-class="fx-tap" class="fx-press" @click="pickReply(line.row)">
              回复
            </text>
            <text
              v-if="canDelete(line.row)"
              hover-class="fx-tap"
              class="fx-press"
              @click="handleDelete(line.row)"
            >
              删除
            </text>
          </view>
        </view>
      </view>
    </view>

    <view v-if="loading" class="py-64rpx text-center text-[26rpx] text-gray-400">
      加载中…
    </view>

    <view v-else-if="loadError" class="py-64rpx text-center text-[26rpx] text-gray-400">
      {{ loadError }}
      <text hover-class="fx-tap" class="fx-press ml-16rpx text-[#d14328]" @click="loadFirst">
        重试
      </text>
    </view>

    <view v-else-if="!items.length" class="py-64rpx text-center text-[26rpx] text-gray-400">
      还没有评论，来说两句吧
    </view>

    <view v-if="!loading && hasMore" class="mt-40rpx flex justify-center">
      <wd-button size="small" plain :loading="moreLoading" @click="loadMore">
        再看 {{ PAGE_SIZE }} 条
      </wd-button>
    </view>

    <!-- 预览态底下这一颗换成「查看全部」：整页评论（带排序、输入框、翻页）在 /pages/comment/list 那一页 -->
    <view v-if="preview && !loading && !loadError && items.length" class="mt-40rpx flex justify-center">
      <wd-button size="small" plain @click="viewAll">
        查看全部 {{ total }} 条评论
      </wd-button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/*
 * 这个工程的 UnoCSS 把 preflight 整块注释掉了 ⇒ `border` 工具类只出宽度、border-style 还是 none，
 * 线一点画不出来（同购物车那颗单选圈那一回）。要看得见的描边只能自己写全，别指望工具类补齐。
 */
.comment-box {
  border: 3rpx solid #e5e7eb;
  border-radius: 12rpx;
}

.comment-box--dashed {
  border-style: dashed;
}
</style>
