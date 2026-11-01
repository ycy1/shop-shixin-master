<script setup lang="ts">
/**
 * 潮玩专区「图鉴」那一页：一行一条潮玩系列，报我手上的收集进度和这条系列的集齐奖励。
 *
 * 三件事都在服务端算，页面只念：
 * · 分子分母（凑到几格 / 这套一共几格）—— 和后端领取那一步的判定同一条口径，
 *   页面自己数一遍会出现「这里说还差一格、点领取却领到了」；
 * · 谁排前面 —— 我抽过的系列整排在前，这个排序在 SQL 里做，前端再排一次就把「再看 N 条」的页界打乱；
 * · 奖励那句人话（rewardText）—— 积分念「N 分」，券和实物是回捞的名字，页面不认 id。
 *
 * 领奖励是真写库的那一颗（POST /api/comm/trendy/collection/{id}/claim，一人一条系列只落一行）。
 * 领完不重捞整页：把返回那一句念在这一行下面，并把这一行就地改成「已领取」——
 * 原来那条定时任务撤了，什么时候到账由人点这一下决定。
 */
import { computed, onMounted, ref } from 'vue';
import ModalOverlay from '@/components/ModalOverlay.vue';
import { useI18n } from '@/i18n';
import { dictLabel, loadDict } from '@/lib/dict';
import {
  claimCollectReward,
  fetchTrendyCollection,
  fetchTrendyCollectionItems,
  type CollectionItem,
  type CollectionRow,
} from '@/lib/trendy';
import { useAuthStore } from '@/stores/auth';

defineProps<{ locale?: string }>();

/**
 * 这一岛自己建一次 store：token 落在 api 层那一格模块变量里，谁先建 store 谁把它填上。
 * 不建就指望页头那颗登录岛先水合 —— 刷新时几颗岛同时起，先后没有保证；那一趟请求会不带
 * Authorization，后端就会拿浏览器里别的 cookie 认人（图鉴于是念成别人的收集进度）。
 */
useAuthStore();

const { d, fill } = useI18n();
const t = d.trendy;

const REWARD_DICT = 'trendy_reward_type';
const PAGE_SIZE = 10;

const rows = ref<CollectionRow[]>([]);
const total = ref(0);
const page = ref(0);
const pages = ref(0);
const loading = ref(true);
const error = ref('');
const moreError = ref('');
/** 正在领的那一条系列 id：只有它那颗按钮转灰，别的行不动 */
const claimingId = ref<number | null>(null);
/** 领完那一句念在这一行下面（按 trendyId 存，翻页后不串到别的行上） */
const claimNote = ref<Record<number, string>>({});

/**
 * 底下那颗「看收集明细」开的弹窗：整条系列的规格一格一行，抽到过的那格标日期。
 * 明细按系列 id 存一份（同一条系列连点两次不重捞），点另一条系列就地换内容。
 */
const itemsOpen = ref<CollectionRow | null>(null);
const itemsByTrendy = ref<Record<number, CollectionItem[]>>({});
const itemsLoading = ref(false);
const itemsError = ref('');

function itemsOf(row: CollectionRow): CollectionItem[] | null {
  return itemsByTrendy.value[row.trendyId] ?? null;
}

async function openItems(row: CollectionRow) {
  itemsOpen.value = row;
  itemsError.value = '';
  if (itemsByTrendy.value[row.trendyId]) return;
  itemsLoading.value = true;
  try {
    const list = await fetchTrendyCollectionItems(row.trendyId);
    itemsByTrendy.value = { ...itemsByTrendy.value, [row.trendyId]: list ?? [] };
  } catch (err) {
    itemsError.value = err instanceof Error ? err.message : String(err);
  } finally {
    itemsLoading.value = false;
  }
}

/** 抽到的那一格念日期就够了，时刻没人看（后端给的是 yyyy-MM-dd HH:mm:ss） */
function drawnDate(raw: string | null) {
  return raw ? raw.slice(0, 10) : '';
}

const hasMore = computed(() => page.value < pages.value);

function progress(row: CollectionRow) {
  return fill(t.collection.progress, { got: row.collectedSku, total: row.totalSku });
}

/** 凑满那一格：分母为 0（系列还没绑商品）不算凑满，别给一颗点了必失败的标记 */
function isFull(row: CollectionRow) {
  return row.totalSku > 0 && row.collectedSku >= row.totalSku;
}

function rewardTag(row: CollectionRow) {
  return row.rewardType == null ? null : dictLabel(REWARD_DICT, row.rewardType);
}

async function load(reset: boolean) {
  if (reset) {
    loading.value = true;
    error.value = '';
  }
  moreError.value = '';
  try {
    const data = await fetchTrendyCollection(reset ? 1 : page.value + 1, PAGE_SIZE);
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

async function claim(row: CollectionRow) {
  if (claimingId.value != null) return;
  claimingId.value = row.trendyId;
  try {
    const record = await claimCollectReward(row.trendyId);
    // 就地落成「已领取」：记录行是后端刚插的那一行，页面不重捞一遍整页
    row.recordId = row.recordId ?? -1;
    row.canClaim = false;
    claimNote.value = { ...claimNote.value, [row.trendyId]: record.rewardDesc || t.collection.claimed };
  } catch (err) {
    claimNote.value = {
      ...claimNote.value,
      [row.trendyId]: fill(t.collection.claimFailed, { msg: err instanceof Error ? err.message : String(err) }),
    };
  } finally {
    claimingId.value = null;
  }
}

/**
 * 从「集齐了某条系列」那条消息点进来时带着 ?trendyId=（lib/notification 的 messageTarget 拼的）：
 * 滚到那张卡、亮一圈再撤掉。列表按收集进度排、10 条一页，目标不一定在第一页 —— 往后翻到它在为止。
 *
 * 翻页那一趟失败不重试也不死循环（行数没变就停）：这一页本来还能手动往下看，别把人卡在加载里。
 */
const FOCUS_MS = 4000;
const focusedId = ref(0);

function focusCard(trendyId: number) {
  focusedId.value = trendyId;
  // 等一拍再滚：卡片刚挂上、封面图还没撑开时 scrollIntoView 会少滚一截，落在页头底下
  window.setTimeout(() => {
    document.getElementById(`trendy-${trendyId}`)?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }, 60);
  window.setTimeout(() => {
    if (focusedId.value === trendyId) focusedId.value = 0;
  }, FOCUS_MS);
}

async function focusFromQuery() {
  const raw = new URLSearchParams(window.location.search).get('trendyId');
  const target = raw ? Number(raw) : 0;
  if (!Number.isInteger(target) || target <= 0) return;
  // 上限 9 趟（90 条）：这一页真长到那么长的时候，人自己往下翻比一口气拉完更快
  for (let guard = 0; guard < 9 && hasMore.value; guard += 1) {
    if (rows.value.some((row) => row.trendyId === target)) break;
    const before = rows.value.length;
    await load(false);
    if (rows.value.length === before) break;
  }
  if (rows.value.some((row) => row.trendyId === target)) focusCard(target);
}

onMounted(async () => {
  loadDict([REWARD_DICT]);
  await load(true);
  await focusFromQuery();
});
</script>

<template>
  <section>
    <header class="mb-4">
      <h1 class="text-xl font-semibold">{{ t.collection.title }}</h1>
      <p class="text-sm text-gray-500 dark:text-gray-400">{{ t.collection.sub }}</p>
    </header>

    <p v-if="loading" class="text-sm text-gray-500">{{ t.collection.loading }}</p>
    <p v-else-if="error" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
      {{ fill(t.collection.loadFailed, { msg: error }) }}
      <button type="button" class="ml-2 underline" @click="load(true)">{{ t.collection.retry }}</button>
    </p>
    <p v-else-if="!rows.length" class="text-sm text-gray-500 dark:text-gray-400">{{ t.collection.empty }}</p>

    <template v-else>
      <ul class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <li
          v-for="row in rows"
          :key="row.trendyId"
          :id="`trendy-${row.trendyId}`"
          class="flex flex-col rounded-xl border border-gray-200 bg-white p-3 transition-shadow dark:border-gray-700 dark:bg-gray-800"
          :class="focusedId === row.trendyId ? 'ring-2 ring-emerald-400 dark:ring-emerald-500' : ''"
        >
          <div class="flex gap-3">
            <span class="h-16 w-16 shrink-0 overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-700">
              <img v-if="row.coverImage" :src="row.coverImage" :alt="row.trendyName" class="h-full w-full object-cover" />
            </span>
            <div class="min-w-0 flex-1">
              <p class="flex items-center gap-1.5">
                <span class="truncate font-medium">{{ row.trendyName }}</span>
                <span
                  v-if="isFull(row)"
                  class="shrink-0 rounded bg-emerald-100 px-1.5 py-0.5 text-[11px] text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300"
                >{{ t.collection.full }}</span>
              </p>
              <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ progress(row) }}</p>
              <p v-if="row.totalSku === 0" class="mt-1 text-xs text-gray-400">{{ t.collection.noSku }}</p>
            </div>
          </div>

          <!-- 进度那条：只有分母大于 0 才画（一套没绑商品的系列，画一根空槽是在编一个数） -->
          <div v-if="row.totalSku > 0" class="mt-3 h-1.5 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
            <div
              class="h-full rounded-full bg-emerald-500 transition-all"
              :style="{ width: `${Math.min(100, Math.round((row.collectedSku / row.totalSku) * 100))}%` }"
            ></div>
          </div>

          <div class="mt-3 flex items-center justify-between gap-2">
            <p class="min-w-0 text-sm text-gray-600 dark:text-gray-300">
              <span class="text-gray-400">{{ t.collection.rewardLabel }}</span>
              <span v-if="rewardTag(row)" class="ml-1 rounded bg-gray-100 px-1.5 py-0.5 text-[11px] text-gray-600 dark:bg-gray-700 dark:text-gray-300">{{ rewardTag(row) }}</span>
              <span v-if="row.rewardText" class="ml-1 truncate">{{ row.rewardText }}</span>
              <span v-else class="ml-1 text-gray-400">{{ t.collection.rewardNone }}</span>
            </p>
            <button
              v-if="row.canClaim"
              type="button"
              class="shrink-0 rounded-lg bg-blue-600 px-3 py-1.5 text-sm text-white transition-colors hover:bg-blue-700 disabled:opacity-60 dark:bg-blue-500 dark:hover:bg-blue-600"
              :disabled="claimingId === row.trendyId"
              @click="claim(row)"
            >
              {{ claimingId === row.trendyId ? t.collection.claiming : t.collection.claim }}
            </button>
            <span v-else-if="row.recordId" class="shrink-0 text-sm text-gray-400">{{ t.collection.claimed }}</span>
          </div>

          <p v-if="row.recordId && row.rewardDesc" class="mt-2 text-xs text-gray-500 dark:text-gray-400">
            {{ row.rewardDesc }}
          </p>
          <p v-if="claimNote[row.trendyId]" class="mt-2 text-xs text-emerald-700 dark:text-emerald-300">
            {{ claimNote[row.trendyId] }}
          </p>

          <!-- 卡片底下这一颗：整条系列的每一格都列出来，抽到过的那格标日期、没抽到的说「还缺这一格」。
               只列抽过的那几行数不出「还差哪几格」，所以这一屏打头的是整套、不是我的记录。
               外面那层 mt-auto 把它压到卡片底：上面那几行（已领取/兑现说明/还缺提示）有无不一，
               不压的话同一排的几颗会一高一低 -->
          <div class="mt-auto pt-3">
            <button
              type="button"
              class="w-full rounded-lg border border-gray-200 px-3 py-1.5 text-sm text-gray-600 transition-colors hover:bg-gray-100 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
              @click="openItems(row)"
            >
              {{ t.collection.openItems }}
            </button>
          </div>
        </li>
      </ul>

      <p class="mt-4 text-sm text-gray-500 dark:text-gray-400">{{ fill(t.collection.countLine, { n: total }) }}</p>
      <p v-if="moreError" class="mt-2 text-sm text-red-600 dark:text-red-400">
        {{ fill(t.collection.loadFailed, { msg: moreError }) }}
      </p>
      <button
        v-if="hasMore"
        type="button"
        class="mt-2 rounded-lg border border-gray-200 px-4 py-2 text-sm hover:bg-gray-100 dark:border-gray-600 dark:hover:bg-gray-700"
        @click="load(false)"
      >
        {{ fill(t.collection.more, { n: PAGE_SIZE }) }}
      </button>
    </template>

    <!-- 收集明细弹窗：一格一行铺一排小卡，抽到的那格打标 + 日期，没抽到的那格压灰说「还缺这一格」 -->
    <ModalOverlay v-if="itemsOpen" @close="itemsOpen = null">
      <div
        role="dialog"
        aria-modal="true"
        :aria-label="t.collection.itemsTitle"
        class="w-full max-w-lg rounded-2xl border border-gray-200 bg-white p-5 shadow-xl dark:border-gray-700 dark:bg-gray-800"
      >
        <h3 class="text-center text-base font-semibold">{{ t.collection.itemsTitle }}</h3>
        <p class="mt-1 text-center text-xs text-gray-500 dark:text-gray-400">
          {{ fill(t.collection.itemsSub, { name: itemsOpen.trendyName, total: itemsOpen.totalSku, got: itemsOpen.collectedSku }) }}
        </p>

        <p v-if="itemsLoading" class="mt-4 text-sm text-gray-500">{{ t.collection.itemsLoading }}</p>
        <p v-else-if="itemsError" class="mt-4 text-sm text-red-600 dark:text-red-400">
          {{ fill(t.collection.itemsFailed, { msg: itemsError }) }}
        </p>
        <p v-else-if="!itemsOf(itemsOpen)?.length" class="mt-4 text-sm text-gray-500 dark:text-gray-400">
          {{ t.collection.noSku }}
        </p>

        <div v-else class="mt-4 grid max-h-[60vh] grid-cols-3 gap-3 overflow-y-auto">
          <div v-for="one in itemsOf(itemsOpen)" :key="one.skuId" class="text-center">
            <div
              class="relative aspect-square overflow-hidden rounded-xl bg-gray-100 dark:bg-gray-700"
              :class="one.drawnTime ? '' : 'opacity-45'"
            >
              <img v-if="one.skuImage" :src="one.skuImage" :alt="one.productName" class="h-full w-full object-cover" />
              <span v-else class="flex h-full w-full items-center justify-center px-1 text-[11px] text-gray-400">
                {{ t.card.noImage }}
              </span>
              <span
                v-if="one.drawnTime"
                class="absolute left-1 top-1 rounded-full bg-emerald-500 px-2 py-0.5 text-[10px] text-white"
              >
                {{ t.collection.got }}
              </span>
            </div>
            <p class="mt-1 truncate text-xs">{{ one.productName }}</p>
            <p v-if="one.specText" class="truncate text-[11px] text-gray-500 dark:text-gray-400">{{ one.specText }}</p>
            <p class="text-[11px]" :class="one.drawnTime ? 'text-gray-400' : 'text-gray-400 dark:text-gray-500'">
              {{ one.drawnTime ? fill(t.collection.gotAt, { date: drawnDate(one.drawnTime) }) : t.collection.miss }}
            </p>
          </div>
        </div>

        <div class="mt-4 text-center">
          <button
            type="button"
            class="rounded-lg border border-gray-200 px-4 py-2 text-sm hover:bg-gray-100 dark:border-gray-600 dark:hover:bg-gray-700"
            @click="itemsOpen = null"
          >
            {{ d.common.close }}
          </button>
        </div>
      </div>
    </ModalOverlay>
  </section>
</template>
