<script setup lang="ts">
/**
 * 广场列表页：头部导航「广场」那一颗的落点，前台用户发的图文流。
 *
 * 游客照常能逛（读那条接口不判登录），只有右下角那颗发布和卡片上那颗点赞要登录 —— 按钮照画、挂 data-open-login
 * 让页头的登录窗认领，写法同详情页那颗收藏（FavoriteButton.vue）。
 *
 * 一次十条往下捞：这一页是「流」，不像活动页那样给「再看 10 条」那颗（活动总共没几条，得让人数得清；
 * 广场往后翻是无底的，所以照首页商品那一屏挂一颗哨兵，滑到近处自动续页）。
 *
 * 卡片上那两颗（点赞 / 分享）就摆在列表里，不用先进详情再回来点：动作走的是 comm 那两条，
 * 和详情页同一份判据、同一张明细表，所以在哪儿点都算同一次。因为卡片里有按钮，卡片整颗不再是链接了 ——
 * 按钮嵌在 <a> 里不合法，点了也会连带跳转；改成外层 article，标题那一块套 <a>，动作那一排挂在它外面。
 *
 * 筛选那一排（关键字 + 关联类型）走的是列表接口那两条查询参数，换筛选就回第一页整块重捞；
 * 「无关联」是一档真值（relation_type=0），所以 chip 里它和「全部」不是一回事。
 * 那一排平时收起来（只留开关那一行）：它钉在页头下面，铺开要吃掉小半屏，收起时已生效的两格印在开关后面。
 */
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import PlazaPublishDialog from '@/views/plaza/PlazaPublishDialog.vue';
import {
  fetchPlazaPage,
  fetchRecommendedPlazaPage,
  plazaLabel,
  plazaLeadImage,
  togglePlazaLike,
  trackPlazaShare,
  type PlazaMessage,
} from '@/lib/plaza';
import { useAuthStore } from '@/stores/auth';
import { enumFilters, useI18n } from '@/i18n';

/** locale 由页面壳递进来：岛里拿不到 Astro 对象（见 @/i18n 顶上那段） */
defineProps<{ locale?: string }>();
const { d, fill, href } = useI18n();

const PAGE_SIZE = 10;

const rows = ref<PlazaMessage[]>([]);
const page = ref(0);
const pages = ref(0);
const total = ref(0);
const loading = ref(false);
const error = ref('');
/** 续页失败另说一行：已经画出来的卡片不撤走（和活动列表同一处理） */
const moreError = ref('');

const auth = useAuthStore();
/** 登录态读 localStorage，服务端那一趟读不到 ⇒ 第一趟一律当没登录，否则整块文案撞 hydration */
const ready = ref(false);
const signedIn = computed(() => ready.value && auth.isLoggedIn);
const publishing = ref(false);
/** 发完那一句「等审核」的说法：列表里新那条本来就不会出现，不写清的人会以为发失败了 */
const notice = ref('');

/** 输入框里正在打的字和这一串已生效的那一份分开存：回车或点「搜索」才生效，边打字边刷页没法看 */
const keywordInput = ref('');
const keyword = ref('');
/** null 就是不限关联类型（不发这一格），0 是「无关联」那一档真值 */
const relationType = ref<number | null>(null);
const relationChips = computed(() => [
  { value: null as number | null, label: d.common.all },
  ...enumFilters(d.enum.plazaRelation).map((one) => ({ value: one.id as number | null, label: one.label })),
]);

/**
 * 搜索和筛选那两排平时收起。这一排钉在页头下面，铺开要吃掉小半屏，而往下逛的人多数时候不动它；
 * 收起时把已生效的那两格印在开关后面 —— 不然「筛过却看不见筛了什么」，人以为列表就是全量。
 * 展开不自动收：换一次筛选要再点两下比多看一眼列表烦；开关也不带自动聚焦，点了未必是想打字。
 */
const filtersOpen = ref(false);
/** 「无关联」是 0 那一档真值，所以按 null 判「不限」，不能按假值判 */
const relationLabel = computed(
  () => relationChips.value.find((chip) => chip.value !== null && chip.value === relationType.value)?.label || '',
);
const hasFilter = computed(() => keyword.value !== '' || relationType.value !== null);

/** 开合之后要把钉住的那两个高度重算一遍：RO 只兜「chip 换行、窗口改尺寸」那类不由我们引起的变化，
 *  这一趟是自己改的高度，等它回调不如自己算一次（面板没在前台时 RO 干脆不送达，实测踩过）。 */
function toggleFilters() {
  filtersOpen.value = !filtersOpen.value;
  void nextTick(syncPinGeom);
}

/** 清除：两格一起回「不限」，再走换筛选那条整块重捞的路 */
function clearFilters() {
  keywordInput.value = '';
  keyword.value = '';
  relationType.value = null;
  void reload();
}

/** 点赞那一趟：一次只让一条在动（记的是哪一条），反馈那一行给整个列表看；分享的收尾只印一句 tip */
const busyId = ref<number | null>(null);
const feedback = ref('');

/**
 * 右侧那一块推荐位：读的还是列表那一条接口，只是多钉一格 isRecommend=1（后台勾了推荐的那几条）。
 * 它是导流不是列表，一次捞一页就够、不跟着往下翻；换筛选也不重捞 —— 那一块摆的从来不是当前条件的结果。
 * 捞失败就整块收掉：底下留一句报错占地方，主列表照旧能用。
 */
const RECOMMEND_SIZE = 8;
const recommended = ref<PlazaMessage[]>([]);
const recFailed = ref(false);

const sentinel = ref<HTMLElement | null>(null);
let observer: IntersectionObserver | undefined;

/**
 * 钉住那两排（筛选排、右侧推荐块）各自要停在哪儿，得先知道页头多高 —— 页头是 sticky 的实心条，
 * 只给一个写死的数会不准：窄屏那条菜单展开会撑成两行，换语种文案变长也会顶高一行。
 * 所以现量，并用同一颗 ResizeObserver 跟着它变（两个高度都在这一次量里取，回调不按来源分叉）。
 * 递出去走的是内联自定义属性：这一份每页都带上，不受换页缺样式那一坑影响。
 */
const filterBar = ref<HTMLElement | null>(null);
const pinTop = ref<string | null>(null);
const filterHeight = ref<string | null>(null);
let pinResize: ResizeObserver | undefined;

function syncPinGeom() {
  const header = document.querySelector('header');
  if (!header) return;
  // 页头实高原样递：这排要贴住它的下沿，多加的那几 px 会变成透气的缝
  pinTop.value = `${Math.round(header.getBoundingClientRect().height)}px`;
  if (filterBar.value) {
    filterHeight.value = `${Math.round(filterBar.value.getBoundingClientRect().height) + 8}px`;
  }
}

/** 两个量齐了才递：只递一半会让推荐块贴到筛选排底下去 */
const pinStyle = computed(() =>
  pinTop.value && filterHeight.value ? { '--plaza-pin-top': pinTop.value, '--plaza-filter-h': filterHeight.value } : undefined,
);

const hasMore = computed(() => page.value < pages.value);

/** 发布者那一格：联不出昵称（账号注销了）只剩 id，给一句能念的说法 */
function author(one: PlazaMessage) {
  const name = one.userName?.trim();
  if (name) return name;
  return one.userId ? fill(d.plazaList.authorUnknown, { n: one.userId }) : d.common.dash;
}

async function load() {
  if (loading.value) return;
  loading.value = true;
  const first = rows.value.length === 0;
  if (first) error.value = '';
  else moreError.value = '';
  try {
    const query: { keyword?: string; relationType?: number } = {};
    if (keyword.value) query.keyword = keyword.value;
    if (relationType.value !== null) query.relationType = relationType.value;
    const data = await fetchPlazaPage(page.value + 1, PAGE_SIZE, query);
    // 两页之间有人新发一条，同一条可能被捞两次 ⇒ 按 id 去重再拼
    const seen = new Set(rows.value.map((one) => one.id));
    rows.value.push(...(data.records || []).filter((one) => !seen.has(one.id)));
    page.value += 1;
    pages.value = data.pages ?? 0;
    total.value = data.total ?? 0;
  } catch (err) {
    const message = (err as Error).message;
    if (first) error.value = message;
    else moreError.value = message;
  } finally {
    loading.value = false;
    // 第一屏回来之前 sentinel 那颗还在 v-else 里，得等 DOM 更新完才拿得到；换内容后它是重建过的节点，每次都要重新挂
    await nextTick();
    armObserver();
  }
}

/** 换筛选 / 发完一条：回第一页整块重捞（往下翻的那几页接在旧那一串后面会串味，口径同消息中心换档） */
async function reload() {
  rows.value = [];
  page.value = 0;
  pages.value = 0;
  total.value = 0;
  moreError.value = '';
  feedback.value = '';
  await load();
}

function applyKeyword() {
  keyword.value = keywordInput.value.trim();
  void reload();
}

function pickRelation(value: number | null) {
  if (relationType.value === value) return;
  relationType.value = value;
  void reload();
}

/**
 * 卡片上那颗点赞：走 comm 那一条 toggle，回按完之后的状态，本地把 selfLiked 和计数一起改（不用再捞一趟）。
 * 没登录这一支走不到这里：那颗按钮上的 data-open-login 归页头的登录窗认领。
 */
async function toggleLike(one: PlazaMessage) {
  if (!signedIn.value || busyId.value !== null) return;
  busyId.value = one.id;
  feedback.value = '';
  try {
    const before = one.selfLiked === true;
    const liked = await togglePlazaLike(one.id);
    if (before !== liked) {
      one.selfLiked = liked;
      one.likeCount = Math.max((one.likeCount ?? 0) + (liked ? 1 : -1), 0);
    }
  } catch (err) {
    feedback.value = (err as Error).message;
  } finally {
    busyId.value = null;
  }
}

/**
 * 本期不接分享 SDK：网页端就是把这一条的链接复制走，顺带记一行留痕。
 *
 * 收尾只印一句话（tip），两秒半自己走。
 * 留痕要等它回来才加那一格数：分享数读的是明细表，插失败（没登录、网络断了）就不能把数报高了。
 * 游客这一趟只复制链接，shop_plaza_share 的 user_id 是必填、服务端没得记，数不动 ⇒ 那句提示改说这件事，
 * 否则人点了半天看见数没变，只会当成 bug。
 */
const tip = ref('');
let tipTimer: ReturnType<typeof setTimeout> | undefined;

function showTip(text: string) {
  tip.value = text;
  clearTimeout(tipTimer);
  tipTimer = setTimeout(() => {
    tip.value = '';
  }, 2500);
}

async function share(one: PlazaMessage) {
  // 语种前缀要带上：复制出去的链接得落在人当前在读的那一版页面上
  const url = `${window.location.origin}${href(`/plaza/${one.id}`)}`;
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
    await trackPlazaShare(one.id);
    one.shareCount = (one.shareCount ?? 0) + 1;
  } catch {
    // 留痕没记上不影响链接已经复制出去，照样说「已复制」，只是卡片那一格数不动
  }
  showTip(d.plazaDetail.copied);
}

function armObserver() {
  if (!observer || !sentinel.value) return;
  observer.disconnect();
  observer.observe(sentinel.value);
}

/** 右侧那一块：只捞推荐位那一档，一次一页；捞失败就整块收掉（导流块，不值得占一行报错） */
async function loadRecommended() {
  try {
    recommended.value = (await fetchRecommendedPlazaPage(RECOMMEND_SIZE)).records || [];
  } catch {
    recFailed.value = true;
  }
}

/** 发布那颗悬浮按钮：没登录这一支什么都不做，点击由页头的登录窗认领（同 FavoriteButton） */
function openPublish() {
  if (!signedIn.value) return;
  publishing.value = true;
}

/** 发布成功：整块重捞第一页（新那条还在待审核，本来也不该出现，但总数和后面那几页会跟着挪） */
async function onPublished() {
  publishing.value = false;
  notice.value = d.plazaPublish.success;
  await reload();
}

onMounted(() => {
  ready.value = true;
  observer = new IntersectionObserver(
    (entries) => {
      if (!entries.some((entry) => entry.isIntersecting)) return;
      if (loading.value || error.value || moreError.value || !hasMore.value) return;
      void load();
    },
    // 和首页同一口径：提前 200px 就开取下一页，省得人滑到底干等
    { rootMargin: '200px' },
  );
  void load();
  void loadRecommended();
  syncPinGeom();
  const header = document.querySelector('header');
  if (header && filterBar.value) {
    pinResize = new ResizeObserver(() => syncPinGeom());
    pinResize.observe(header);
    pinResize.observe(filterBar.value);
  }
});

onUnmounted(() => {
  observer?.disconnect();
  pinResize?.disconnect();
  clearTimeout(tipTimer);
});
</script>

<template>
  <section :style="pinStyle">
    <div class="mb-6">
      <h1 class="text-xl font-semibold">{{ d.site.plaza }}</h1>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ d.plazaList.subtitle }}</p>
    </div>

    <div v-if="notice" class="mb-4 flex items-start justify-between gap-3 rounded-lg bg-green-50 px-4 py-3 text-sm text-green-700 dark:bg-green-900/40 dark:text-green-300">
      <span>{{ notice }}</span>
      <button type="button" class="shrink-0 underline" @click="notice = ''">{{ d.common.close }}</button>
    </div>

    <!-- 搜索和筛选那两排平时收起，只留开关那一行：这一排钉在页头下面（几何在 global.css 的 .plaza-pin），
         铺开要吃掉小半屏。收起时把已生效的两格印在开关后面，否则人以为列表就是全量。
         关键字要回车或点「搜索」才生效（边打字边捞没法看）；关联类型是 chip，点一下换档整块重捞；
         「无关联」是一档真值，跟「全部」不是一回事。 -->
    <div ref="filterBar" class="plaza-pin space-y-2">
      <div class="flex flex-wrap items-center gap-2">
        <button
          type="button"
          aria-controls="plaza-filters"
          :aria-expanded="filtersOpen ? 'true' : 'false'"
          class="flex items-center gap-1.5 rounded-full border border-gray-300 px-3 py-1 text-sm text-gray-600 transition-colors hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
          @click="toggleFilters"
        >
          {{ d.plazaList.filterToggle }}
          <!-- 箭头的角度走内联样式：这颗是开合状态本身，工具类在换页那一趟容易掉（global.css 顶上那段说了） -->
          <svg
            class="h-3.5 w-3.5"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            aria-hidden="true"
            :style="{ transform: filtersOpen ? 'rotate(180deg)' : 'none' }"
          >
            <path stroke-linecap="round" stroke-linejoin="round" d="m19 9-7 7-7-7" />
          </svg>
        </button>
        <template v-if="!filtersOpen">
          <span v-if="keyword" class="rounded-full bg-blue-50 px-2.5 py-1 text-xs text-blue-700 dark:bg-blue-900/40 dark:text-blue-300">
            {{ fill(d.plazaList.filterKeyword, { kw: keyword }) }}
          </span>
          <span v-if="relationLabel" class="rounded-full bg-blue-50 px-2.5 py-1 text-xs text-blue-700 dark:bg-blue-900/40 dark:text-blue-300">
            {{ relationLabel }}
          </span>
          <button
            v-if="hasFilter"
            type="button"
            :disabled="loading"
            class="text-xs text-gray-500 underline disabled:opacity-60 dark:text-gray-400"
            @click="clearFilters"
          >
            {{ d.plazaList.filterClear }}
          </button>
        </template>
      </div>

      <div v-if="filtersOpen" id="plaza-filters" class="space-y-2">
        <div class="flex gap-2">
          <input
            v-model="keywordInput"
            type="search"
            :placeholder="d.plazaList.keywordPlaceholder"
            class="min-w-0 flex-1 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500 dark:border-gray-600 dark:bg-gray-800"
            @keyup.enter="applyKeyword"
          />
          <button
            type="button"
            :disabled="loading"
            class="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-blue-700 disabled:opacity-60"
            @click="applyKeyword"
          >
            {{ d.common.search }}
          </button>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <button
            v-for="chip in relationChips"
            :key="chip.value === null ? 'all' : chip.value"
            type="button"
            :disabled="loading"
            class="rounded-full border px-3 py-1 text-sm transition-colors disabled:opacity-60"
            :class="
              relationType === chip.value
                ? 'border-blue-600 bg-blue-50 font-medium text-blue-700 dark:border-blue-400 dark:bg-blue-900/40 dark:text-blue-300'
                : 'border-gray-300 text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700'
            "
            @click="pickRelation(chip.value)"
          >
            {{ chip.label }}
          </button>
        </div>
      </div>
      <p v-if="feedback" class="text-sm text-gray-600 dark:text-gray-400">{{ feedback }}</p>
    </div>

    <div class="plaza-cols">
      <!-- 右侧那一块推荐位（窄屏排在筛选和主列表中间）：只摆后台勾了推荐的那几条，跟左边那串不是一份数据，
           所以换筛选、往下翻都跟它无关；一条都没捞着或那一趟失败就不画这一格，免得留个空框。
           并排还是上下叠、断点、宽窄轨道这些尺寸全在 global.css 的 .plaza-cols / .plaza-rec（为什么不能写工具类，那一段说了）。 -->
      <aside v-if="!recFailed && recommended.length" class="plaza-rec">
        <div class="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
          <h2 class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ d.plazaList.recommendTitle }}</h2>
          <ul class="mt-2 space-y-3">
            <li v-for="one in recommended" :key="one.id">
              <a :href="href(`/plaza/${one.id}`)" class="group block">
                <span class="block truncate text-sm font-medium text-gray-800 group-hover:text-blue-600 dark:text-gray-200 dark:group-hover:text-blue-400" :title="plazaLabel(one, d.plazaList.noTitle)">
                  {{ plazaLabel(one, d.plazaList.noTitle) }}
                </span>
                <span class="mt-0.5 block truncate text-xs text-gray-500 dark:text-gray-400">
                  {{ author(one) }} · {{ (one.publishTime || one.createTime || '').slice(0, 10) }}
                </span>
              </a>
            </li>
          </ul>
        </div>
      </aside>

      <div class="min-w-0 flex-1 md:order-1">
        <p v-if="loading && !rows.length" class="py-10 text-center text-sm text-gray-500 dark:text-gray-400">
          {{ d.common.loading }}
        </p>

        <p v-else-if="error && !rows.length" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
          {{ error }}
          <button type="button" class="ml-2 underline" @click="load">{{ d.detailCommon.retry }}</button>
        </p>

        <p v-else-if="!rows.length" class="py-10 text-center text-sm text-gray-500 dark:text-gray-400">
          {{ d.plazaList.empty }}
        </p>

        <div v-else class="space-y-4">
          <!-- 卡片里有按钮，所以整颗不能是 <a>（按钮嵌在链接里不合法，点按钮还会连带跳转）：
               外层 article，能点进详情的部分（作者/标题/摘要）套在内层 <a> 里，动作那一排挂在它外面。 -->
          <article
            v-for="one in rows"
            :key="one.id"
            class="rounded-lg border border-gray-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md dark:border-gray-700 dark:bg-gray-800"
          >
            <a :href="href(`/plaza/${one.id}`)" class="flex gap-3">
              <!-- 配图：发布人自己上传的那一组里挑第一张（一张没传就没这一格，服务端不会从正文抓图来补），
                   没图也照样铺一方框的首字占位 —— 不然有图的那张和没图的那张右边文字起笔不齐。
                   方框尺寸写在 global.css 的 .plaza-cover（不能在这儿新添工具类，理由那一段说了）。 -->
              <img v-if="plazaLeadImage(one)" :src="plazaLeadImage(one)" alt="" loading="lazy" class="plaza-cover" />
              <span v-else class="plaza-cover">{{ plazaLabel(one, d.plazaList.noTitle).charAt(0) }}</span>

              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
                  <span class="font-medium text-gray-700 dark:text-gray-300">{{ author(one) }}</span>
                  <span v-if="one.publishTime">{{ one.publishTime.slice(0, 16) }}</span>
                  <span v-if="one.isTop === 1" class="rounded bg-red-50 px-1.5 py-0.5 text-red-600 dark:bg-red-900/40 dark:text-red-300">
                    {{ d.plazaList.pinned }}
                  </span>
                  <span
                    v-if="one.isRecommend === 1"
                    class="rounded bg-amber-50 px-1.5 py-0.5 text-amber-600 dark:bg-amber-900/40 dark:text-amber-300"
                  >
                    {{ d.plazaList.recommended }}
                  </span>
                </div>

                <div class="mt-2">
                  <h2 class="truncate text-base font-semibold text-gray-900 dark:text-gray-100" :title="plazaLabel(one, d.plazaList.noTitle)">
                    {{ plazaLabel(one, d.plazaList.noTitle) }}
                  </h2>
                  <!-- 没写标题的那些，摘要已经顶到上面那行当了题，这里再铺一遍就是重复 -->
                  <p v-if="one.title && one.contentText" class="mt-1 line-clamp-2 text-sm text-gray-600 dark:text-gray-400">
                    {{ one.contentText }}
                  </p>
                </div>
              </div>
            </a>

            <!-- 点赞和分享在列表里就能办：走的动作跟详情页同一份判据（comm 那两条），在哪儿点都算同一次。
                 没登录时点赞那颗照画，点击由页头的登录窗认领（data-open-login，写法同 FavoriteButton）。 -->
            <div class="mt-3 flex items-center gap-2 border-t border-gray-100 pt-3 text-xs dark:border-gray-700">
              <button
                type="button"
                :data-open-login="signedIn ? null : ''"
                :disabled="busyId !== null"
                class="rounded-full border px-3 py-1 transition-colors disabled:opacity-60"
                :class="
                  one.selfLiked
                    ? 'border-red-200 bg-red-50 text-red-500 dark:border-red-800 dark:bg-red-900/40 dark:text-red-300'
                    : 'border-gray-300 text-gray-600 hover:bg-gray-100 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700'
                "
                @click="toggleLike(one)"
              >
                {{ (one.selfLiked ? d.plazaDetail.liked : d.plazaDetail.like) + ' · ' + (one.likeCount ?? 0) }}
              </button>
              <button
                type="button"
                class="rounded-full border border-gray-300 px-3 py-1 text-gray-600 transition-colors hover:bg-gray-100 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
                @click="share(one)"
              >
                {{ d.plazaDetail.share + ' · ' + (one.shareCount ?? 0) }}
              </button>
              <span class="text-gray-500 dark:text-gray-400">{{ fill(d.plazaList.comments, { n: one.commentCount ?? 0 }) }}</span>
              <a :href="href(`/plaza/${one.id}`)" class="ml-auto text-blue-600 hover:underline dark:text-blue-400">{{ d.plazaList.openDetail }}</a>
            </div>
          </article>

          <!-- IntersectionObserver 盯的就是这一格：它进视口就续下一页 -->
          <div ref="sentinel" class="h-px"></div>
        </div>

        <div v-if="rows.length" class="mt-6 flex flex-col items-center gap-2">
          <p v-if="moreError" class="text-sm text-red-600 dark:text-red-400">
            {{ fill(d.plazaList.moreFailed, { msg: moreError }) }}
            <button type="button" class="ml-2 underline" @click="load">{{ d.detailCommon.retry }}</button>
          </p>
          <p v-else-if="loading" class="text-sm text-gray-500 dark:text-gray-400">{{ d.common.loading }}</p>
          <span class="text-sm text-gray-500 dark:text-gray-400">{{ fill(d.common.total, { n: total, unit: d.plazaList.countUnit }) }}</span>
        </div>
      </div>
    </div>

    <!-- 右下角那颗悬浮发布：抬在布局里那颗「回到顶部」（fixed bottom-6 right-4）上面留一格，两颗不贴脸 -->
    <div class="fixed bottom-20 right-4 z-40">
      <button
        type="button"
        :title="d.plazaPublish.fab"
        :aria-label="d.plazaPublish.fab"
        :data-open-login="signedIn ? null : ''"
        class="rounded-full bg-blue-600 p-4 text-white shadow-md transition-colors hover:bg-blue-700"
        @click="openPublish"
      >
        <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m-8-8h16" />
        </svg>
      </button>
    </div>

    <PlazaPublishDialog v-if="publishing" @close="publishing = false" @published="onPublished" />

    <!-- 分享那颗的收尾只印一句话：点的是哪一条卡片，抬头就能看见，不用再看整列表的反馈行。
         形状全在 global.css 的 .plaza-tip（原因记在那一段）：钉顶部中间而不是右下，是因为右下角有悬浮发布那颗按钮。 -->
    <p v-if="tip" class="plaza-tip">{{ tip }}</p>
  </section>
</template>
