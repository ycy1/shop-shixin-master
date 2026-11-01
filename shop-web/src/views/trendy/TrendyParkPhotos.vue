<script setup lang="ts">
/**
 * 一家园区的全部照片（从详情页顶上那屏九宫格下面的「全部照片」进来）。
 *
 * 20 张一页、用「再看 20 张」往后接，不学首页那种滚动加载（口径同盲盒那一页：一屏一屏点得清楚）。
 * 只出审核通过的那几档、精选在前 —— 判据和次序都在服务端那一趟（和详情页墙上用的是同一条），
 * 这里不重挑一遍，否则两页翻出来的先后会对不上。
 *
 * 点一张开大图（公共的 ImagePreview）：翻的是**已经捞回来的这些**，所以看到第 40 张时左右两颗能一直翻到底。
 *
 * 顶上那颗「上传照片」走的是投稿那一条：传文件 → POST /api/comm/park/photo，来源钉 2 用户上传、
 * 状态钉 0 待审核 —— 投完的图不会立刻出现在这一页（这一页只读审核通过的），弹窗里要说白在等审核。
 * 没登录时那颗按钮照画，只挂 data-open-login 让页头那扇登录窗认领（口径同详情页那颗买票）。
 *
 * URL 上带 ?highlight= 时（从消息中心点那条审核结论进来，链接是服务端审核那一路拼的，见 lib/notification）：
 * 翻到那张所在的那一页、滚过去描一圈边。一屏 20 张，那张可能在第 30 张之后 ⇒ 找不到就自动往后接一页，
 * 接到见底还没有就明说「这张现在不在照片墙上」—— 被拒绝、被隐藏、被删掉的那几张这一页本来就不出，
 * 不写这一句的话人只看到一面墙，不知道自己那张去哪了。
 */
import { computed, nextTick, onMounted, ref } from 'vue';
import ImagePreview from '@/components/ImagePreview.vue';
import ParkPhotoUploadDialog from './ParkPhotoUploadDialog.vue';
import { useI18n } from '@/i18n';
import { useAuthStore } from '@/stores/auth';
import { fetchParkPhotos, type ParkPhoto } from '@/lib/trendy';

const props = defineProps<{ parkId: number; locale?: string; highlightId?: number | undefined }>();

const { d, fill } = useI18n();
const t = d.trendy;

const auth = useAuthStore();
/** 登录态读的是 localStorage，服务端那一趟读不到 ⇒ 挂起来之后再认，不然撞 hydration mismatch */
const ready = ref(false);
const signedIn = computed(() => ready.value && auth.isLoggedIn);
const uploadOpen = ref(false);

function openUpload() {
  // 没登录这一支走不到这里：那颗按钮上挂的是 data-open-login，点击归页头那扇登录窗认领
  if (!signedIn.value) return;
  uploadOpen.value = true;
}

const PAGE_SIZE = 20;

const rows = ref<ParkPhoto[]>([]);
const total = ref(0);
const page = ref(0);
const pages = ref(0);
const loading = ref(true);
const error = ref('');
/** 后面某一页没 Load 出来另说一行：已经画出来的那些图不撤走 */
const moreError = ref('');
const viewer = ref<{ urls: string[]; index: number } | null>(null);

const hasMore = computed(() => page.value < pages.value);
const urls = computed(() => rows.value.map((one) => one.imageUrl).filter((url): url is string => !!url));

/** 正被指着的那张（描边一会儿就散），和「翻到见底也没见着它」那一格提示 */
const litId = ref<number | null>(null);
const targetMissing = ref(false);
/** 已经定位过一次就别再来一遍（人自己点「再看 20 张」时不该再被拽回去） */
const located = ref(false);
let huntToken = 0;

/**
 * 从消息中心带 ?highlight= 进来时找那张。
 *
 * 每捞回一页就在已经到手的那些里找一次；还没有且后面还有页，就自己点一次「再看 20 张」接着找 ——
 * 一张一张让人自己翻不现实，而这条链的起点是「你那张图审核过了」，人就是要看自己那张。
 * 翻到底还没有（被拒绝、被隐藏、被删掉的那几种这一页本来就不出）就在原地说明一句。
 *
 * huntToken 认「最近一次加载」：自动翻页翻到一半人又点了「再看 20 张」时，旧那一趟的结果不能再回来滚画面
 *（口径同结算页那份试算序号）。
 */
async function hunt() {
  const target = props.highlightId;
  if (!target || located.value || loading.value || error.value) return;
  const token = ++huntToken;
  if (rows.value.some((one) => one.id === target)) {
    located.value = true;
    litId.value = target;
    await nextTick();
    if (token === huntToken) {
      document.getElementById(`park-photo-${target}`)?.scrollIntoView({ block: 'center' });
    }
    window.setTimeout(() => {
      litId.value = null;
    }, 3200);
    return;
  }
  if (hasMore.value) {
    await load(false);
    if (token === huntToken) await hunt();
    return;
  }
  if (token === huntToken) targetMissing.value = true;
}

async function load(reset: boolean) {
  if (reset) {
    loading.value = true;
    error.value = '';
  }
  moreError.value = '';
  try {
    const data = await fetchParkPhotos(props.parkId, reset ? 1 : page.value + 1, PAGE_SIZE);
    const records = data.records ?? [];
    rows.value = reset ? records : [...rows.value, ...records];
    total.value = data.total ?? 0;
    pages.value = data.pages ?? 0;
    page.value = data.current ?? 1;
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    if (reset) error.value = msg;
    else moreError.value = msg;
  } finally {
    loading.value = false;
  }
}

/** 「再看 20 张」：接完一页，带 highlight 进来的人顺手再找一次（那张可能就在新接的这 20 张里） */
async function loadMore() {
  await load(false);
  await hunt();
}

function open(at: number) {
  const url = rows.value[at]?.imageUrl;
  if (!url) return;
  const index = urls.value.indexOf(url);
  viewer.value = { urls: urls.value, index: index < 0 ? 0 : index };
}

onMounted(() => {
  ready.value = true;
  void load(true).then(hunt);
});
</script>

<template>
  <section>
    <header class="mb-4 flex flex-wrap items-start justify-between gap-3">
      <div>
        <h1 class="text-xl font-semibold">{{ t.photos.title }}</h1>
        <p class="text-sm text-gray-500 dark:text-gray-400">{{ t.photos.sub }}</p>
      </div>
      <div class="flex items-center gap-3">
        <span v-if="!signedIn" class="text-xs text-gray-400">{{ t.upload.loginFirst }}</span>
        <button
          type="button"
          class="rounded-lg bg-blue-600 px-4 py-1.5 text-sm text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600"
          :data-open-login="signedIn ? null : ''"
          @click="openUpload"
        >
          {{ t.upload.button }}
        </button>
      </div>
    </header>

    <p v-if="loading" class="text-sm text-gray-500">{{ d.common.loading }}</p>
    <p v-else-if="error" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
      {{ fill(t.home.loadFailed, { msg: error }) }}
      <button type="button" class="ml-2 underline" @click="load(true)">{{ t.home.retry }}</button>
    </p>
    <p v-else-if="!rows.length" class="text-sm text-gray-500 dark:text-gray-400">{{ t.photos.empty }}</p>

    <template v-else>
      <!-- 从消息中心点进来、翻到见底也没见着那张（被拒绝/被隐藏/被删的就不是这一页的内容） -->
      <p v-if="targetMissing" class="mb-3 rounded-lg bg-amber-50 px-4 py-2 text-sm text-amber-700 dark:bg-amber-900/30 dark:text-amber-300">
        {{ t.photos.missing }}
      </p>

      <div class="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
        <button
          v-for="(one, i) in rows"
          :id="`park-photo-${one.id}`"
          :key="one.id"
          type="button"
          class="group relative aspect-square overflow-hidden rounded-xl bg-gray-100 text-left dark:bg-gray-700"
          :class="litId === one.id ? 'ring-2 ring-amber-400' : ''"
          :aria-label="one.description || t.photos.title"
          @click="open(i)"
        >
          <img
            v-if="one.imageUrl"
            :src="one.imageUrl"
            :alt="one.description || ''"
            class="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
            loading="lazy"
          />
          <span v-else class="flex h-full w-full items-center justify-center text-sm text-gray-400">
            {{ t.parkCard.noImage }}
          </span>
          <span
            v-if="one.description"
            class="absolute bottom-0 left-0 right-0 truncate bg-black/45 px-2 py-1 text-[11px] text-white"
          >
            {{ one.description }}
          </span>
        </button>
      </div>

      <p class="mt-4 text-sm text-gray-500 dark:text-gray-400">{{ fill(t.photos.countLine, { n: total }) }}</p>
      <p v-if="moreError" class="mt-2 text-sm text-red-600 dark:text-red-400">
        {{ fill(t.home.loadFailed, { msg: moreError }) }}
      </p>
      <button
        v-if="hasMore"
        type="button"
        class="mt-2 rounded-lg border border-gray-200 px-4 py-2 text-sm hover:bg-gray-100 dark:border-gray-600 dark:hover:bg-gray-700"
        @click="loadMore"
      >
        {{ fill(t.photos.more, { n: PAGE_SIZE }) }}
      </button>
    </template>

    <ImagePreview
      v-if="viewer"
      :list="viewer.urls"
      :index="viewer.index"
      :alt="t.photos.title"
      @close="viewer = null"
    />

    <ParkPhotoUploadDialog v-if="uploadOpen" :park-id="props.parkId" @close="uploadOpen = false" />
  </section>
</template>
