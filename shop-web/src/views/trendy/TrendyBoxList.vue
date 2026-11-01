<script setup lang="ts">
/**
 * 潮玩专区「盲盒」那一页：未开始 + 进行中的盒，三档筛选（全部 / 进行中 / 未开始）。
 *
 * 筛选走服务端（status 递给那条读接口），不在前端挑：后端本来就只回这两档，
 * 前端再切一遍会出现「翻页时按筛完的条数算还剩几页」那种对不上的数。
 * 翻页用「再看 N 个」，不学首页的滚动加载（口径同活动列表页：一屏一屏点得清楚）。
 */
import { computed, onMounted, ref } from 'vue';
import BoxCard from './BoxCard.vue';
import { useI18n } from '@/i18n';
import { loadDict } from '@/lib/dict';
import { fetchTrendyBoxes, type TrendyBox } from '@/lib/trendy';

defineProps<{ locale?: string }>();

const { d, fill } = useI18n();
const t = d.trendy;

const PAGE_SIZE = 12;

const rows = ref<TrendyBox[]>([]);
const total = ref(0);
const page = ref(0);
const pages = ref(0);
const loading = ref(true);
const error = ref('');
/** 后面某一页没 Load 出来另说一行：已经画出来的卡片不撤走 */
const moreError = ref('');
/** undefined = 不收窄（全部）；1 未开始；2 进行中 */
const status = ref<number | undefined>(undefined);

const filters = computed(() => [
  { value: undefined as number | undefined, label: t.box.filterAll },
  { value: 2, label: t.box.filterRunning },
  { value: 1, label: t.box.filterNotStart },
]);

const hasMore = computed(() => page.value < pages.value);

async function load(reset: boolean) {
  if (reset) {
    loading.value = true;
    error.value = '';
  }
  moreError.value = '';
  try {
    const data = await fetchTrendyBoxes({
      pageNum: reset ? 1 : page.value + 1,
      pageSize: PAGE_SIZE,
      ...(status.value == null ? {} : { status: status.value }),
    });
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

function pickFilter(value: number | undefined) {
  if (value === status.value) return;
  status.value = value;
  void load(true);
}

onMounted(() => {
  loadDict(['trendy_box_type', 'trendy_box_status']);
  void load(true);
});
</script>

<template>
  <section>
    <header class="mb-4">
      <h1 class="text-xl font-semibold">{{ t.box.title }}</h1>
      <p class="text-sm text-gray-500 dark:text-gray-400">{{ t.box.sub }}</p>
    </header>

    <div class="mb-4 flex flex-wrap gap-2">
      <button
        v-for="item in filters"
        :key="String(item.value)"
        type="button"
        class="rounded-full border px-3 py-1 text-sm transition-colors"
        :class="
          item.value === status
            ? 'border-blue-600 bg-blue-600 text-white dark:border-blue-500 dark:bg-blue-500'
            : 'border-gray-200 text-gray-600 hover:bg-gray-100 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700'
        "
        @click="pickFilter(item.value)"
      >
        {{ item.label }}
      </button>
    </div>

    <p v-if="loading" class="text-sm text-gray-500">{{ d.common.loading }}</p>
    <p v-else-if="error" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
      {{ fill(t.home.loadFailed, { msg: error }) }}
      <button type="button" class="ml-2 underline" @click="load(true)">{{ t.home.retry }}</button>
    </p>
    <p v-else-if="!rows.length" class="text-sm text-gray-500 dark:text-gray-400">{{ t.box.empty }}</p>

    <template v-else>
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        <BoxCard v-for="box in rows" :key="box.id" :box="box" />
      </div>

      <p class="mt-4 text-sm text-gray-500 dark:text-gray-400">{{ fill(t.box.countLine, { n: total }) }}</p>
      <p v-if="moreError" class="mt-2 text-sm text-red-600 dark:text-red-400">
        {{ fill(t.home.loadFailed, { msg: moreError }) }}
      </p>
      <button
        v-if="hasMore"
        type="button"
        class="mt-2 rounded-lg border border-gray-200 px-4 py-2 text-sm hover:bg-gray-100 dark:border-gray-600 dark:hover:bg-gray-700"
        @click="load(false)"
      >
        {{ fill(d.common.more, { n: PAGE_SIZE }) }}
      </button>
    </template>
  </section>
</template>
