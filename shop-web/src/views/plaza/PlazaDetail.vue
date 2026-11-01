<script setup lang="ts">
/**
 * 一条广场消息：/plaza/{id}，广场列表那张卡片点进来的落点，也是消息中心那条「有人评论了你」的跳转落点。
 *
 * 正文是访客写的富文本，必须过 sanitizeRichText 才上屏（口径同活动详情那一格，见 lib/richtext）：
 * 这一条详情接口连游客都发得出去，直接 v-html 等于让任何能发布的人在所有消费者页面上执行脚本。
 * 清洗只在捞回来之后跑，DOMParser 只在浏览器那一趟用，服务端那一趟碰不到它。
 *
 * 正文里的图就留在原处（夹在段落之间），点哪张从哪张起看大图，左右翻由 ImagePreview 自己接。
 * 正文之外还有一组广场消息图（plaza_image 那一串，发布时手工挑的那几张）：整组铺在正文下面，同样点一张看大图。
 * 两处各是一组，所以从正文点进去只在那几张插图里翻，从下面这组点进去只在这几张里翻。
 *
 * 待审核/被拒的那两条只有作者本人读得到（后端那一刀压在 detail 里），所以这两档要给一句说法，
 * 否则人点自己的链接看到的是「这条不存在」会以为被删了。评论区只在已发布那一档铺 ——
 * 还没上广场的消息不该收评论（后端那条链也是这么判的）。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue';
import CommentSection from '@/components/CommentSection.vue';
import ImagePreview from '@/components/ImagePreview.vue';
import {
  PLAZA_AUDIT_PENDING,
  PLAZA_AUDIT_REJECTED,
  fetchPlaza,
  plazaImages,
  relationHref,
  togglePlazaLike,
  trackPlazaShare,
  type PlazaMessage,
} from '@/lib/plaza';
import { sanitizeRichText } from '@/lib/richtext';
import { useAuthStore } from '@/stores/auth';
import { useI18n } from '@/i18n';

/** highlightId 由页面壳从 URL 的 highlight 递进来；没带时壳给的是 undefined，所以这一格要写明可空 */
const props = defineProps<{ messageId: number; highlightId?: number | undefined; locale?: string }>();
/** 这一页自己有一颗叫 href 的 computed（关联对象那条链接），所以把 useI18n 那颗别名开，免得撞名 */
const { d, fill, href: localeHref } = useI18n();

const detail = ref<PlazaMessage | null>(null);
const loading = ref(true);
const error = ref('');
const likeError = ref('');
/** 一次只让一颗在动，免得连点把同一条赞两次 */
const busy = ref(false);
/** 分享那颗的收尾：只印一句话，两秒半自己走 */
const tip = ref('');
let tipTimer: ReturnType<typeof setTimeout> | undefined;

function showTip(text: string) {
  tip.value = text;
  clearTimeout(tipTimer);
  tipTimer = setTimeout(() => {
    tip.value = '';
  }, 2500);
}
/** 看大图那一层：整条消息的图 + 从第几张开始 */
const viewer = ref<{ urls: string[]; index: number } | null>(null);

const auth = useAuthStore();
const ready = ref(false);
const signedIn = computed(() => ready.value && auth.isLoggedIn);

const heading = computed(() => detail.value?.title?.trim() || detail.value?.contentText?.trim() || d.plazaList.noTitle);

/** 白名单之后的正文；模板里那个 v-html 只认这一颗 computed */
const contentHtml = computed(() => sanitizeRichText(detail.value?.content ?? ''));

/** 从清洗过的正文里把图地址挨个摘出来（服务端只给一串 HTML，没有单独的图片列） */
const images = computed(() => {
  const out: string[] = [];
  for (const match of contentHtml.value.matchAll(/<img[^>]+?src\s*=\s*["']?([^"'\s>]+)/gi)) {
    const url = match[1];
    if (url) out.push(url);
  }
  return out;
});

/**
 * 正文是 v-html 出来的，图嵌在段落里、拿不到 Vue 的事件绑定，所以在容器上接一次冒泡：
 * 点到的不是 IMG 就当没发生，是 IMG 就按 src 在这条消息的那组图里找名次，整组递给 ImagePreview（左右翻照旧）。
 */
function openImage(event: MouseEvent) {
  const node = event.target as HTMLElement | null;
  if (!node || node.tagName !== 'IMG') return;
  const src = node.getAttribute('src');
  const index = src ? images.value.indexOf(src) : -1;
  if (index < 0) return;
  viewer.value = { urls: images.value, index };
}

/** 正文之外那一组广场消息图（plaza_image 那一串）：整组铺在正文下面 */
const postImages = computed(() => plazaImages(detail.value));

/** 这一组的图点哪张从哪张起看大图：整组递给 ImagePreview，左右翻在这几张之间走 */
function openPostImage(index: number) {
  viewer.value = { urls: postImages.value, index };
}

/** 审核没过/还没过的说法：这两档只有作者本人点得进来，所以两句都是写给发布者看的 */
const auditTip = computed(() => {
  const one = detail.value;
  if (!one) return '';
  if (one.auditStatus === PLAZA_AUDIT_PENDING) return d.plazaDetail.pending;
  if (one.auditStatus === PLAZA_AUDIT_REJECTED) return d.plazaDetail.rejected;
  return '';
});

const link = computed(() => (detail.value ? relationHref(detail.value.relationType, detail.value.relationId) : ''));

function author(one: PlazaMessage) {
  const name = one.userName?.trim();
  if (name) return name;
  return one.userId ? fill(d.plazaDetail.authorUnknown, { n: one.userId }) : d.common.dash;
}

/** 时间只到日：这一页不是流水单，秒没有意义；没上广场的那一条读提交时间 */
function timeText(one: PlazaMessage) {
  if (one.publishTime) return fill(d.plazaDetail.publishedAt, { time: one.publishTime.slice(0, 16) });
  const submitted = one.createTime;
  return submitted ? fill(d.plazaDetail.submittedAt, { time: submitted.slice(0, 16) }) : '';
}

async function toggleLike() {
  const one = detail.value;
  // 没登录这一支走不到这里：那颗按钮上的 data-open-login 归头部的登录窗认领
  if (!one || !signedIn.value || busy.value) return;
  busy.value = true;
  likeError.value = '';
  try {
    const liked = await togglePlazaLike(one.id);
    const before = one.selfLiked === true;
    if (before !== liked) {
      one.selfLiked = liked;
      one.likeCount = Math.max((one.likeCount ?? 0) + (liked ? 1 : -1), 0);
    }
  } catch (err) {
    likeError.value = (err as Error).message;
  } finally {
    busy.value = false;
  }
}

/**
 * 本期不接分享 SDK：网页端就是把这一条的链接复制走，顺带记一行留痕。
 *
 * 收尾只印一句话（tip），两秒半自己走，口径同广场列表那张卡片。
 * 留痕要等它回来才加那一格数：分享数读的是明细表，插失败（没登录、网络断了）就不能把数报高了。
 * 游客这一趟只复制链接：shop_plaza_share 的 user_id 是必填，服务端没得记，所以数也不动，那句提示改说这件事。
 */
async function share() {
  const url = window.location.href;
  try {
    await navigator.clipboard.writeText(url);
  } catch {
    showTip(d.plazaDetail.copyFailed);
    return;
  }
  if (!signedIn.value) {
    showTip(d.plazaDetail.shareGuestTip);
    return;
  }
  try {
    await trackPlazaShare(detail.value?.id ?? props.messageId);
    if (detail.value) detail.value.shareCount = (detail.value.shareCount ?? 0) + 1;
  } catch {
    // 留痕没记上不影响链接已经复制出去，照样说「已复制」，只是那一格数不动
  }
  showTip(d.plazaDetail.copied);
}

onMounted(async () => {
  ready.value = true;
  try {
    detail.value = await fetchPlaza(props.messageId);
  } catch (err) {
    error.value = (err as Error).message;
  } finally {
    loading.value = false;
  }
});

onUnmounted(() => {
  clearTimeout(tipTimer);
});
</script>

<template>
  <p v-if="loading" class="py-10 text-center text-sm text-gray-500 dark:text-gray-400">{{ d.common.loading }}</p>

  <p v-else-if="error" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
    {{ error }}
    <a :href="localeHref('/plaza')" class="ml-2 underline">{{ d.plazaDetail.backToList }}</a>
  </p>

  <section v-else-if="detail">
    <a :href="localeHref('/plaza')" class="text-sm text-blue-600 hover:underline dark:text-blue-400">← {{ d.plazaDetail.listLink }}</a>

    <div
      v-if="auditTip"
      class="mt-3 rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:bg-amber-900/40 dark:text-amber-200"
    >
      <p>{{ auditTip }}</p>
      <p v-if="detail.auditRemark" class="mt-1">{{ fill(d.plazaDetail.auditRemark, { msg: detail.auditRemark }) }}</p>
    </div>

    <h1 class="mt-3 text-xl font-semibold">{{ heading }}</h1>
    <div class="mt-1 flex flex-wrap items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
      <span class="font-medium text-gray-700 dark:text-gray-300">{{ author(detail) }}</span>
      <span v-if="timeText(detail)">{{ timeText(detail) }}</span>
      <span v-if="detail.isTop === 1" class="rounded bg-red-50 px-1.5 py-0.5 text-xs text-red-600 dark:bg-red-900/40 dark:text-red-300">
        {{ d.plazaList.pinned }}
      </span>
      <span v-if="detail.isRecommend === 1" class="rounded bg-amber-50 px-1.5 py-0.5 text-xs text-amber-600 dark:bg-amber-900/40 dark:text-amber-300">
        {{ d.plazaList.recommended }}
      </span>
      <a v-if="link" :href="localeHref(link)" class="text-blue-600 hover:underline dark:text-blue-400">
        {{ fill(d.plazaDetail.relation, { name: detail.relationName || d.common.dash }) }}
      </a>
    </div>

    <!-- 正文里的图不另起一排：跟着段落排在原处，点哪张从哪张起看大图（容器接一次冒泡，见 openImage） -->
    <div
      v-if="contentHtml"
      class="plaza-content mt-5 rounded-lg border border-gray-200 bg-white p-4 text-sm leading-7 text-gray-700 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300 [&_a]:text-blue-600 [&_a]:underline dark:[&_a]:text-blue-400 [&_h1]:mt-4 [&_h1]:text-lg [&_h1]:font-semibold [&_h2]:mt-4 [&_h2]:text-base [&_h2]:font-semibold [&_h3]:mt-3 [&_h3]:font-semibold [&_img]:my-2 [&_img]:max-w-full [&_li]:ml-6 [&_li]:list-disc [&_ol]:my-2 [&_p]:my-2 [&_pre]:my-2 [&_pre]:overflow-x-auto [&_pre]:rounded [&_pre]:bg-gray-100 [&_pre]:p-2 dark:[&_pre]:bg-gray-700 [&_table]:my-2 [&_td]:border [&_td]:border-gray-200 [&_td]:p-1.5 dark:[&_td]:border-gray-700 [&_th]:border [&_th]:border-gray-200 [&_th]:p-1.5 dark:[&_th]:border-gray-700 [&_ul]:my-2"
      @click="openImage"
      v-html="contentHtml"
    ></div>
    <p v-else class="mt-5 py-6 text-center text-sm text-gray-500 dark:text-gray-400">{{ d.plazaDetail.noContent }}</p>

    <!-- 广场消息图（正文之外的那一组）：整组铺在正文下面，点一张看大图。
         方框尺寸和 cursor 写在 global.css 的 .plaza-images（这一颗是 island 里新添的几何，写成工具类换页那一趟会掉）。 -->
    <div v-if="postImages.length" class="mt-4">
      <p class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ d.plazaDetail.images }}</p>
      <div class="plaza-images mt-2">
        <img
          v-for="(url, index) in postImages"
          :key="url"
          :src="url"
          :alt="`${heading} ${index + 1}`"
          loading="lazy"
          @click="openPostImage(index)"
        />
      </div>
    </div>

    <div class="mt-4 flex items-center gap-3">
      <button
        type="button"
        :data-open-login="signedIn ? null : ''"
        :disabled="busy"
        class="rounded-full border px-4 py-2 text-sm transition-colors disabled:opacity-60"
        :class="
          detail.selfLiked
            ? 'border-red-200 bg-red-50 text-red-500 dark:border-red-900/60 dark:bg-red-900/40 dark:text-red-300'
            : 'border-gray-300 bg-white text-gray-600 hover:bg-gray-100 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
        "
        @click="toggleLike"
      >
        {{ (detail.selfLiked ? d.plazaDetail.liked : d.plazaDetail.like) + ' · ' + (detail.likeCount ?? 0) }}
      </button>
      <button
        type="button"
        class="rounded-full border border-gray-300 bg-white px-4 py-2 text-sm text-gray-600 hover:bg-gray-100 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
        @click="share"
      >
        {{ d.plazaDetail.share + ' · ' + (detail.shareCount ?? 0) }}
      </button>
      <p v-if="likeError" class="text-sm text-red-600 dark:text-red-400">{{ likeError }}</p>
    </div>

    <ImagePreview v-if="viewer" :list="viewer.urls" :index="viewer.index" @close="viewer = null" />

    <!-- 分享那颗的收尾只印一句话：这一页正文长，垫在按钮下面那一行容易滚出视线，所以钉在视口顶部中间。
         形状同广场列表那张卡片，全在 global.css 的 .plaza-tip（含暗色那格底色） -->
    <p v-if="tip" class="plaza-tip">{{ tip }}</p>

    <!-- 已发布的那一条才挂评论区：这一页就是这条消息名下列表的落点，所以给完整态（带排序、输入框和翻页），不是预览态。
         「共 N 条」那一行 CommentSection 自己会铺，这里不再补标题 -->
    <div v-if="detail.auditStatus === 1" class="mt-8 border-t border-gray-200 pt-6 dark:border-gray-700">
      <CommentSection :comment-type="4" :business-id="detail.id" :highlight-id="highlightId" />
    </div>
  </section>
</template>
