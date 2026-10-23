<script lang="ts" setup>
import type { IShopPlazaPublishForm, IShopPlazaUpdateForm } from '@/api/types/shop'
import { getActivityPage } from '@/api/activity'
import { uploadImage } from '@/api/file'
import { getBrandList, getStoreList } from '@/api/home'
import {
  getPlazaDetail,
  PLAZA_MAX_HTML_LENGTH,
  PLAZA_MAX_TEXT_LENGTH,
  PLAZA_PUBLISH_POINTS,
  PLAZA_RELATION_ACTIVITY,
  PLAZA_RELATION_BRAND,
  PLAZA_RELATION_NONE,
  PLAZA_RELATION_PRODUCT,
  PLAZA_RELATION_STORE,
  PLAZA_RELATION_TEXT,
  plazaParseContent,
  plazaTextLength,
  publishPlaza,
  updatePlaza,
} from '@/api/plaza'
import { getProductList } from '@/api/product'
import { useTokenStore } from '@/store/token'
import { activityLabel } from '@/utils/activity'
import { toLoginPage } from '@/utils/toLoginPage'

defineOptions({
  name: 'PlazaPublish',
})

definePage({
  style: {
    navigationBarTitleText: '发布广场消息',
  },
})

/**
 * 发布 / 修改自己发的那一条，同一页两档：带 ?id= 进来就是修改（顶上标题和底下那颗按钮跟着换字），
 * 形状照 shop-web 那个 WangEditor 弹窗（它编辑态就是这么复用同一颗弹窗的）。
 *
 * 四格能改：标题、正文、图片、关联对象。置顶/推荐/上下架都归后台录入，前端给不了（给了服务层也不认）；
 * 审核状态更是恒由服务端打回 0 重新排队 —— 已经给过的那 10 分不会重发，靠的是 publish_time 没被清掉。
 *
 * 「富文本」在 App 这一端落到「文字 + 若干张图」：小程序里没有能用的所见即所得编辑器（要引第三方 editor 组件、
 * 还要处理它在自己那套 web-view / renderjs 里的兼容），所以正文由页面自己拼成 HTML 交给后端 ——
 * 每一行文字包一个 <p>，图各占一段 <p><img src=… /></p>，落库的和 Web 端编辑器出来的是同一种形状，
 * 读侧（<rich-text> / v-html / 后台那个编辑器）都能原样吃下。
 * 修改时反过来用 plazaParseContent 拆回两格；摘要由服务端剥标签算。
 * 这一排图除了拼进正文，还整串落 plaza_image（广场消息图那一列）：列表卡片配图读它的第一张、详情页在正文下方整组铺开。
 * App 上只有这一排图（正文里内嵌的就是它们），所以两处看到的是同一组；网页端那一边可以单独挑配图，用 App 改一条会把它
 * 单独加的配图覆盖成这一排 —— 要留住那些得去网页端改。
 */

/** 候选那一行：四路接口各自的字段先在这一格拍平，列表和挑中那一下就不用再按类型分叉 */
interface RelationOption {
  id: number
  name: string
  image: string | null
}

/** 张数上限：这一排图既拼进正文、整串又落 plaza_image，服务端那一列也卡 9 张（PlazaContentUtils），两边同一个数 */
const MAX_IMAGES = 9

/** 关联类型那一排胶囊（顺序就是字典 plaza_relation_type 的四档 + 无关联） */
const RELATION_TYPES = [
  PLAZA_RELATION_NONE,
  PLAZA_RELATION_PRODUCT,
  PLAZA_RELATION_STORE,
  PLAZA_RELATION_BRAND,
  PLAZA_RELATION_ACTIVITY,
]

const tokenStore = useTokenStore()

const editingId = ref<number | null>(null)
const title = ref('')
const text = ref('')
const images = ref<string[]>([])
const uploading = ref(false)
const loadingDetail = ref(false)
const posting = ref(false)
const error = ref('')

/** 关联对象：类型 + 挑中的那一个 + 按名称搜出来的候选（候选只在搜的时候才捞，一次十条） */
const relationType = ref<number>(PLAZA_RELATION_NONE)
const relationId = ref<number | null>(null)
const relationName = ref('')
const keyword = ref('')
const candidates = ref<RelationOption[]>([])
const searching = ref(false)
/** 搜过一次的记号：刚点开这页就甩一句「没搜到」会莫名其妙 */
const searched = ref(false)
const relationError = ref('')

const editing = computed(() => editingId.value != null)
const relationText = computed(() => PLAZA_RELATION_TEXT[relationType.value] ?? '')
const textLength = computed(() => plazaTextLength(text.value))
const over = computed(() => textLength.value > PLAZA_MAX_TEXT_LENGTH)
const remain = computed(() => Math.max(0, PLAZA_MAX_TEXT_LENGTH - textLength.value))
const canPost = computed(() =>
  !posting.value
  && !uploading.value
  && !loadingDetail.value
  && (text.value.trim().length > 0 || images.value.length > 0)
  && !over.value)

/** 尖括号和 & 要转义：正文是访客敲的，原样拼进 HTML 等于把他的话当标签执行 */
function escapeHtml(raw: string) {
  return raw.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/** 一行一个 <p>，空行丢掉；图各占一段排在文字后面 */
function composeHtml() {
  const lines = text.value
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(line => line.length > 0)
    .map(line => `<p>${escapeHtml(line)}</p>`)
  const pics = images.value.map(url => `<p><img src="${url}" /></p>`)
  return [...lines, ...pics].join('')
}

function chooseImages() {
  const left = MAX_IMAGES - images.value.length
  if (uploading.value || left <= 0) {
    return
  }
  uni.chooseImage({
    count: left,
    sizeType: ['compressed'],
    success: async (res) => {
      const paths = (res.tempFilePaths as string[]) ?? []
      uploading.value = true
      error.value = ''
      try {
        for (const path of paths.slice(0, left)) {
          const url = await uploadImage(path, 'plaza')
          // 同一张图选两次会拿到同一个地址，已经在里面就跳过
          if (!images.value.includes(url)) {
            images.value.push(url)
          }
        }
      }
      catch (err) {
        // uni.uploadFile 走不到 http 层，那句失败只能在这儿自己说
        error.value = (err as Error).message || '图片上传失败，再试一次'
      }
      finally {
        uploading.value = false
      }
    },
  })
}

function removeImage(index: number) {
  images.value.splice(index, 1)
}

function previewImage(index: number) {
  uni.previewImage({ urls: images.value, current: index })
}

/** 清空已挑的那个对象（换类型和「取消关联」共用这一颗） */
function resetRelation() {
  relationId.value = null
  relationName.value = ''
  candidates.value = []
  searched.value = false
  relationError.value = ''
}

function pickRelationType(type: number) {
  if (type === relationType.value) {
    return
  }
  // 四张表的 id 会撞号：换类型必须把已经挑的那个清空，否则「商品 8」会变成「品牌 8」
  relationType.value = type
  resetRelation()
}

/** 按名称搜候选：四档各走一条前台读接口，判据（只给在架的门店/品牌、投放中的活动）都跟着那一页的读接口走 */
async function searchRelation() {
  if (searching.value || relationType.value === PLAZA_RELATION_NONE) {
    return
  }
  searching.value = true
  relationError.value = ''
  const kw = keyword.value.trim() || undefined
  try {
    if (relationType.value === PLAZA_RELATION_PRODUCT) {
      const res = await getProductList({ keyword: kw, pageNum: 1, pageSize: 10 })
      candidates.value = (res.records ?? []).map(one => ({ id: one.id, name: one.productName, image: one.mainImage }))
    }
    else if (relationType.value === PLAZA_RELATION_STORE) {
      const res = await getStoreList({ keyword: kw, pageNum: 1, pageSize: 10 })
      candidates.value = (res.records ?? []).map(one => ({ id: one.id, name: one.storeName, image: one.logo }))
    }
    else if (relationType.value === PLAZA_RELATION_BRAND) {
      const res = await getBrandList({ keyword: kw, pageNum: 1, pageSize: 10 })
      candidates.value = (res.records ?? []).map(one => ({ id: one.id, name: one.brandName, image: one.logo }))
    }
    else {
      const res = await getActivityPage({ keyword: kw, pageNum: 1, pageSize: 10 })
      candidates.value = (res.records ?? []).map(one => ({ id: one.id, name: activityLabel(one), image: one.image }))
    }
    searched.value = true
  }
  catch (err) {
    relationError.value = (err as Error).message
  }
  finally {
    searching.value = false
  }
}

function chooseRelation(one: RelationOption) {
  relationId.value = one.id
  relationName.value = one.name
  candidates.value = []
}

/**
 * 修改那一档：把这条读回来铺进三个输入格。
 * 必须走详情那一条 —— 列表 SQL 根本不取 content，铺不出正文；作者本人读得到自己待审核/被拒的那条，不会撞墙。
 */
async function loadDetail(id: number) {
  loadingDetail.value = true
  error.value = ''
  try {
    const one = await getPlazaDetail(id)
    title.value = one.title ?? ''
    const parsed = plazaParseContent(one.content)
    text.value = parsed.text
    images.value = parsed.images
    relationType.value = one.relationType ?? PLAZA_RELATION_NONE
    relationId.value = one.relationId
    // 关联的对象被删了就没名字，给个带 id 的兜底，至少看得清这一格还挂着东西、可以取消重挑
    relationName.value = one.relationName ?? (one.relationId ? `对象 ${one.relationId}` : '')
    uni.setNavigationBarTitle({ title: '修改广场消息' })
  }
  catch (err) {
    error.value = (err as Error).message
  }
  finally {
    loadingDetail.value = false
  }
}

async function submit() {
  if (!canPost.value) {
    return
  }
  if (relationType.value !== PLAZA_RELATION_NONE && relationId.value == null) {
    error.value = '选了关联类型，还要挑要关联的那一个'
    return
  }
  const html = composeHtml()
  // 剥完标签没字、又一张图都没有 ⇒ 后端那句「消息内容不能为空」说的就是这种，先在这儿拦一句
  if (!html) {
    error.value = '正文不能是空的'
    return
  }
  if (html.length > PLAZA_MAX_HTML_LENGTH) {
    error.value = '正文太长了，删掉一些再发'
    return
  }
  posting.value = true
  error.value = ''
  try {
    // 没填标题就不给这一格（不给空串），免得库里留一条空标题的行
    const form: IShopPlazaPublishForm = { content: html }
    const name = title.value.trim()
    if (name) {
      form.title = name
    }
    // 这一格服务端每次都照表单写，不传就是清空：App 上这一排图同时就是广场消息图（正文里内嵌的是同一组）
    if (images.value.length) {
      form.plazaImage = images.value.join(',')
    }
    if (relationType.value !== PLAZA_RELATION_NONE && relationId.value != null) {
      form.relationType = relationType.value
      form.relationId = relationId.value
    }
    if (editingId.value != null) {
      const patch: IShopPlazaUpdateForm = { ...form, id: editingId.value }
      await updatePlaza(patch)
      uni.showToast({ title: '已保存，重新等审核', icon: 'none', duration: 2000 })
    }
    else {
      await publishPlaza(form)
      uni.showToast({ title: '已提交，审核通过就会出现在广场上', icon: 'none', duration: 2000 })
    }
    uni.navigateBack()
  }
  catch (err) {
    error.value = (err as Error).message
  }
  finally {
    posting.value = false
  }
}

onLoad((query) => {
  // 列表页那颗悬浮按钮已经拦过一道；这一页还能被「我的 → 广场消息」那一档打开，所以自己也要认一次登录
  if (!tokenStore.updateNowTime().hasLogin) {
    toLoginPage()
    return
  }
  const id = Number(query?.id)
  if (Number.isFinite(id) && id > 0) {
    editingId.value = id
    void loadDetail(id)
  }
})
</script>

<template>
  <view class="bg-[#f5f6f7] pb-48rpx pt-24rpx">
    <view class="mx-24rpx rounded-xl bg-white px-32rpx py-24rpx">
      <wd-input v-model="title" label="标题" placeholder="可空，一句话给这条起个名" :maxlength="100" clearable no-border />

      <view class="mt-24rpx">
        <view class="mb-12rpx flex items-center justify-between">
          <text class="text-[28rpx] text-[#333] font-medium">
            正文
          </text>
          <text class="text-[22rpx]" :class="over ? 'text-[#d14328]' : 'text-gray-400'">
            还能写 {{ remain }} 字
          </text>
        </view>
        <view class="rounded-lg bg-gray-50 px-24rpx py-16rpx">
          <wd-textarea
            v-model="text"
            placeholder="说说你的想法…一行一段，图片在下面的那颗加"
            :no-border="true"
            :auto-height="true"
          />
        </view>
      </view>

      <view class="mt-24rpx flex items-center">
        <view
          hover-class="fx-tap"
          class="fx-press mr-24rpx rounded-full bg-gray-100 px-24rpx py-8rpx text-[24rpx]"
          :class="images.length >= MAX_IMAGES || uploading ? 'text-gray-300' : 'text-gray-600'"
          @click="chooseImages"
        >
          🖼 图片{{ images.length ? ` ${images.length}/${MAX_IMAGES}` : '' }}
        </view>
        <text v-if="uploading" class="text-[22rpx] text-gray-400">
          上传中…
        </text>
      </view>

      <view v-if="images.length" class="mt-16rpx flex flex-wrap">
        <view v-for="(url, index) in images" :key="url" class="relative mb-16rpx mr-16rpx">
          <image :src="url" mode="aspectFill" class="h-120rpx w-120rpx rounded-lg bg-gray-100" @click="previewImage(index)" />
          <text
            hover-class="fx-tap"
            class="fx-press absolute right--8rpx top--8rpx h-40rpx w-40rpx rounded-full bg-[#333] text-center text-[24rpx] text-white leading-40rpx"
            @click.stop="removeImage(index)"
          >
            ×
          </text>
        </view>
      </view>

      <view v-if="error" class="mt-16rpx text-[24rpx] text-[#d14328]">
        {{ error }}
      </view>
    </view>

    <!-- 关联对象：先挑类型，再按名称搜那一个；挂上之后详情页能顺着这一格跳过去 -->
    <view class="mx-24rpx mt-24rpx rounded-xl bg-white px-32rpx py-24rpx">
      <view class="mb-12rpx text-[28rpx] text-[#333] font-medium">
        关联对象
      </view>
      <view class="flex flex-wrap gap-16rpx">
        <view
          v-for="type in RELATION_TYPES"
          :key="type"
          hover-class="fx-tap"
          class="fx-press rounded-full px-24rpx py-8rpx text-[24rpx]"
          :class="relationType === type ? 'bg-[#d14328] text-white' : 'bg-gray-100 text-gray-600'"
          @click="pickRelationType(type)"
        >
          {{ PLAZA_RELATION_TEXT[type] }}
        </view>
      </view>

      <view v-if="relationId != null" class="mt-16rpx flex items-center">
        <text class="min-w-0 flex-1 truncate text-[26rpx] text-[#333]">
          已关联：{{ relationName }}
        </text>
        <text hover-class="fx-tap" class="fx-press shrink-0 text-[26rpx] text-gray-500" @click="resetRelation">
          取消关联
        </text>
      </view>

      <template v-else-if="relationType !== PLAZA_RELATION_NONE">
        <wd-search v-model="keyword" :placeholder="`输入${relationText}名称搜索`" hide-cancel @search="searchRelation" @clear="candidates = []" />

        <view v-if="searching" class="py-16rpx text-[24rpx] text-gray-400">
          搜索中…
        </view>
        <view v-else>
          <view
            v-for="one in candidates"
            :key="one.id"
            hover-class="fx-tap"
            class="fx-press flex items-center border-b border-gray-50 py-16rpx last:border-b-0"
            @click="chooseRelation(one)"
          >
            <image v-if="one.image" :src="one.image" mode="aspectFill" class="mr-16rpx h-64rpx w-64rpx shrink-0 rounded bg-gray-100" />
            <text class="min-w-0 flex-1 truncate text-[26rpx] text-[#333]">
              {{ one.name }}
            </text>
          </view>
          <view v-if="relationError" class="py-16rpx text-[24rpx] text-[#d14328]">
            {{ relationError }}
          </view>
          <view v-else-if="searched && !candidates.length" class="py-16rpx text-[24rpx] text-gray-400">
            没搜到，换个词试试
          </view>
        </view>
      </template>
    </view>

    <view class="mx-24rpx mt-24rpx">
      <wd-button block type="error" :disabled="!canPost" :loading="posting" @click="submit">
        {{ editing ? '保存' : '发布' }}
      </wd-button>
    </view>

    <!-- 发完不会立刻出现在广场上，这一句必须说清，否则人以为发失败了；改完那句说的是「重新排队、分不重发」 -->
    <view class="mx-24rpx mt-24rpx rounded-xl bg-amber-50 px-24rpx py-20rpx text-[24rpx] text-amber-800 leading-40rpx">
      <template v-if="editing">
        改完会重新回到审核队列，通过之后才再上广场；积分只在头一次通过时给，改正文不会重复发。
      </template>
      <template v-else>
        发布后要等后台审核，通过的那一刻才会上广场，并给你 {{ PLAZA_PUBLISH_POINTS }} 积分。
      </template>
    </view>
  </view>
</template>
