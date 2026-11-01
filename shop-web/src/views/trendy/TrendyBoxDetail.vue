<script setup lang="ts">
/**
 * 一台盲盒的详情页：这台盒摊开给你看 —— 价格、概率、剩余、玩法说明，底下一台真能抽的老虎机。
 *
 * 两处和首页那两台不一样：
 * - 机台走 draw 档（先打 /api/comm/trendy/box/{id}/draw 真抽一把再定格），抽完重捞一次详情，
 *   不然「还剩几次」那一格一直停在抽之前；
 * - 整台盒的款式不在页面上平铺，收在「看整台盒的 N 款」那颗弹窗里 —— 这一页的主角是机台，
 *   款式是给人点开核对概率用的，摊在下面会把机器挤到首屏外。
 *
 * 读的是 /api/web/shop/trendy/box/{id}：那条和列表同源，区别只在款式不截断。
 * 已结束、已取消的盒后端回「盲盒不存在或已结束」，这一页直接把那句当提示用，不再自己判状态。
 * 概率表只在盒上勾了「公示概率」时画 —— 那一格是合规开关，开关在盒身上，不在页面上。
 */
import { computed, onMounted, ref } from 'vue';
import ModalOverlay from '@/components/ModalOverlay.vue';
import SlotMachine from './SlotMachine.vue';
import { useI18n } from '@/i18n';
import { dictLabel, loadDict } from '@/lib/dict';
import { fetchTrendyBoxDetail, type TrendyBox } from '@/lib/trendy';

const props = defineProps<{ boxId: number; locale?: string }>();

const { d, fill } = useI18n();
const t = d.trendy;

const box = ref<TrendyBox | null>(null);
const loading = ref(true);
const error = ref('');
const stylesOpen = ref(false);

const showOdds = computed(() => box.value?.isProbabilityShow === 1);
const drawText = computed(() =>
  box.value?.price == null ? null : Number(box.value.price).toFixed(2),
);
const endBoxText = computed(() =>
  box.value?.endBoxPrice == null ? null : Number(box.value.endBoxPrice).toFixed(2),
);
const timeText = computed(() => {
  const row = box.value;
  if (!row) return '';
  // 后端给的是 yyyy-MM-dd HH:mm:ss，卡片上只念到日
  const day = (v: string | null) => (v ? v.slice(0, 10) : '');
  const from = day(row.startTime);
  const to = day(row.endTime);
  if (!from && !to) return t.detail.noTime;
  return [from, to].filter(Boolean).join(' → ');
});

async function load() {
  loading.value = true;
  error.value = '';
  try {
    box.value = await fetchTrendyBoxDetail(props.boxId);
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err);
  } finally {
    loading.value = false;
  }
}

/**
 * 抽完只重捞数据、不再走那一遍 loading：机器已经报过结果，这里只是把「还剩几次 / 已抽几次」
 * 和款式那几格的余量换成抽后的数。这一趟失败也不叠错误提示 —— 顶多那几格停在抽之前的数。
 */
async function refresh() {
  try {
    box.value = await fetchTrendyBoxDetail(props.boxId);
  } catch {
    // 见上面那句：这一趟只是回读，够不着用户
  }
}

onMounted(() => {
  loadDict(['trendy_box_type', 'trendy_box_status']);
  void load();
});
</script>

<template>
  <section>
    <p v-if="loading" class="text-sm text-gray-500">{{ d.common.loading }}</p>
    <p v-else-if="error" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
      {{ fill(t.home.loadFailed, { msg: error }) }}
      <button type="button" class="ml-2 underline" @click="load">{{ t.home.retry }}</button>
    </p>

    <template v-else-if="box">
      <div class="flex flex-col gap-6 lg:flex-row">
        <div class="w-full shrink-0 lg:w-1/2">
          <div class="aspect-[4/3] overflow-hidden rounded-2xl bg-gray-100 dark:bg-gray-700">
            <img
              v-if="box.coverImage"
              :src="box.coverImage"
              :alt="box.boxName"
              class="h-full w-full object-cover"
            />
            <span v-else class="flex h-full w-full items-center justify-center text-sm text-gray-400">
              {{ t.card.noImage }}
            </span>
          </div>
        </div>

        <div class="flex min-w-0 flex-1 flex-col gap-3">
          <div class="flex flex-wrap items-baseline gap-x-3 gap-y-2">
            <h1 class="text-xl font-semibold">{{ box.boxName }}</h1>
            <span
              v-if="dictLabel('trendy_box_status', box.status)"
              class="rounded-full bg-blue-50 px-2 py-0.5 text-xs text-blue-700 dark:bg-blue-900/40 dark:text-blue-300"
            >
              {{ dictLabel('trendy_box_status', box.status) }}
            </span>
            <span
              v-if="dictLabel('trendy_box_type', box.boxType)"
              class="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-300"
            >
              {{ dictLabel('trendy_box_type', box.boxType) }}
            </span>
          </div>

          <p class="text-2xl font-semibold text-red-600 dark:text-red-400">
            <template v-if="drawText">¥{{ drawText }}<span class="text-sm font-normal"> {{ t.card.drawPrice }}</span></template>
            <template v-if="endBoxText">
              <span class="mx-2 text-gray-300 dark:text-gray-600">|</span>
              ¥{{ endBoxText }}<span class="text-sm font-normal"> {{ t.card.endBoxPrice }}</span>
            </template>
          </p>

          <dl class="grid grid-cols-1 gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
            <div class="flex gap-2">
              <dt class="shrink-0 text-gray-400">{{ t.detail.seriesTitle }}</dt>
              <dd>{{ box.trendyName || t.card.noSeries }}</dd>
            </div>
            <div class="flex gap-2">
              <dt class="shrink-0 text-gray-400">{{ t.detail.remainTitle }}</dt>
              <dd>{{ box.remainCount == null ? t.card.unlimited : fill(t.card.remain, { n: box.remainCount }) }}</dd>
            </div>
            <div class="flex gap-2">
              <dt class="shrink-0 text-gray-400">{{ t.detail.timeTitle }}</dt>
              <dd>{{ timeText }}</dd>
            </div>
            <div v-if="box.guaranteeCount" class="flex gap-2">
              <dt class="shrink-0 text-gray-400">{{ t.detail.probabilityTitle }}</dt>
              <dd>{{ fill(t.card.guarantee, { n: box.guaranteeCount }) }}</dd>
            </div>
          </dl>

          <p v-if="box.description" class="text-sm text-gray-600 dark:text-gray-300">
            <span class="mr-2 text-gray-400">{{ t.detail.ruleTitle }}</span>{{ box.description }}
          </p>
        </div>
      </div>

      <!-- 一台真能抽的机台（draw 档：先真抽一把再定格），右边那颗开的是整台盒的款式 -->
      <div class="mt-8 flex flex-wrap items-center justify-between gap-3">
        <h2 class="text-lg font-semibold">{{ t.detail.machineTitle }}</h2>
        <button
          type="button"
          class="rounded-lg border border-gray-200 px-3 py-1.5 text-sm hover:bg-gray-100 dark:border-gray-600 dark:hover:bg-gray-700"
          @click="stylesOpen = true"
        >
          {{ fill(t.detail.stylesBtn, { n: box.itemCount ?? 0 }) }}
        </button>
      </div>

      <!-- 机台收一口宽度：首页那台是两列并排（每列约 596px），这一页独占整行会把它拉得比首页宽一大截 -->
      <div class="mt-3 w-full max-w-xl">
        <SlotMachine :box="box" mode="draw" @drawn="refresh" />
      </div>

      <!-- 整台盒的款式收在这一张弹窗里（详情页这一趟不截断，所以数得齐后台配的款式数） -->
      <ModalOverlay v-if="stylesOpen" @close="stylesOpen = false">
        <div
          role="dialog"
          aria-modal="true"
          :aria-label="t.detail.stylesTitle"
          class="max-h-[80vh] w-full max-w-3xl overflow-y-auto rounded-2xl border border-gray-200 bg-white p-5 shadow-xl dark:border-gray-700 dark:bg-gray-800"
        >
          <div class="flex items-baseline justify-between gap-3">
            <h3 class="text-base font-semibold">
              {{ t.detail.stylesTitle }}
              <span class="ml-1 text-sm font-normal text-gray-400">{{ box.itemCount ?? 0 }}</span>
            </h3>
            <button
              type="button"
              class="text-sm text-gray-500 hover:underline dark:text-gray-400"
              @click="stylesOpen = false"
            >
              {{ d.common.close }}
            </button>
          </div>

          <p v-if="!box.styles?.length" class="mt-4 text-sm text-gray-500 dark:text-gray-400">{{ t.slot.empty }}</p>
          <div v-else class="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-3">
            <article
              v-for="(item, i) in box.styles"
              :key="i"
              class="overflow-hidden rounded-xl border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800"
            >
              <div class="relative aspect-square bg-gray-100 dark:bg-gray-700">
                <img
                  v-if="item.styleImage"
                  :src="item.styleImage"
                  :alt="item.styleName || ''"
                  class="h-full w-full object-cover"
                  loading="lazy"
                />
                <span v-else class="flex h-full w-full items-center justify-center text-sm text-gray-400">
                  {{ t.card.noImage }}
                </span>
                <span
                  v-if="item.isHidden === 1"
                  class="absolute left-2 top-2 rounded-full bg-amber-500 px-2 py-0.5 text-xs text-white"
                >
                  {{ t.card.hidden }}
                </span>
              </div>
              <div class="flex flex-col gap-1 p-3">
                <p class="truncate text-sm font-medium">{{ item.styleName || d.common.dash }}</p>
                <p class="text-xs text-gray-500 dark:text-gray-400">
                  <span v-if="showOdds && item.probability != null">
                    {{ fill(t.card.probability, { p: `${(Number(item.probability) * 100).toFixed(2)}%` }) }}
                  </span>
                  <span v-if="item.remainCount != null" class="ml-2">
                    {{ item.remainCount > 0 ? fill(t.detail.styleRemain, { n: item.remainCount }) : t.detail.soldOut }}
                  </span>
                </p>
              </div>
            </article>
          </div>
        </div>
      </ModalOverlay>
    </template>
  </section>
</template>
