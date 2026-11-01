<script setup lang="ts">
/**
 * 潮玩专区「公园」那一页：营业中的园区一张卡，整张卡点进那家园区的详情页看在架的票。
 *
 * 列表这一趟只带「几个票种 / 最低价」两格汇总（后端 park/list 就是这两格）——
 * 票的明细挪到了详情页（park/{id}）：这一页原先是点开一张卡就地捞票，现在有了落点，
 * 那张卡不该既当入口又当抽屉，两套展开逻辑也留成一份。
 */
import { onMounted, ref } from 'vue';
import { useI18n } from '@/i18n';
import { fetchTrendyParks, type Park } from '@/lib/trendy';

defineProps<{ locale?: string }>();

const { d, fill, href } = useI18n();
const t = d.trendy;

const parks = ref<Park[]>([]);
const loading = ref(true);
const error = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    parks.value = (await fetchTrendyParks()) ?? [];
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section>
    <header class="mb-4">
      <h1 class="text-xl font-semibold">{{ t.park.title }}</h1>
      <p class="text-sm text-gray-500 dark:text-gray-400">{{ t.park.sub }}</p>
    </header>

    <p v-if="loading" class="text-sm text-gray-500">{{ d.common.loading }}</p>
    <p v-else-if="error" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
      {{ fill(t.home.loadFailed, { msg: error }) }}
      <button type="button" class="ml-2 underline" @click="load">{{ t.home.retry }}</button>
    </p>
    <p v-else-if="!parks.length" class="text-sm text-gray-500 dark:text-gray-400">{{ t.park.empty }}</p>

    <div v-else class="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <a
        v-for="park in parks"
        :key="park.id"
        :href="href(`/trendy/park/${park.id}`)"
        class="flex flex-col gap-3 overflow-hidden rounded-xl border border-gray-200 bg-white p-4 transition-shadow hover:shadow-md dark:border-gray-700 dark:bg-gray-800"
      >
        <!-- 图和下面那几行共用同一圈内边距（图自己再圆一次角），左右沿才对得齐；
             原先图通铺到卡片边、文字往里缩一格，看着像两块东西叠在一起 -->
        <div class="relative aspect-[16/9] overflow-hidden rounded-xl bg-gray-100 dark:bg-gray-700">
          <img v-if="park.coverImage" :src="park.coverImage" :alt="park.parkName" class="h-full w-full object-cover" />
          <span v-else class="flex h-full w-full items-center justify-center text-sm text-gray-400">
            {{ t.parkCard.noImage }}
          </span>
        </div>

        <!-- 文字那一块每一行都固定占位（缺字段也画一行、给个破折号）：
             原先按"有没有值"决定画不画，卡片张数内容长短不一，网格里的下沿就一根根对不齐 -->
        <div class="flex flex-1 flex-col gap-2">
          <div class="flex flex-wrap items-baseline justify-between gap-2">
            <h2 class="truncate text-base font-medium">{{ park.parkName }}</h2>
            <span v-if="park.priceMin != null" class="text-sm font-semibold text-red-600 dark:text-red-400">
              {{ fill(t.parkCard.priceFrom, { p: Number(park.priceMin).toFixed(2) }) }}
            </span>
            <span v-else class="text-xs text-gray-400">{{ t.parkCard.noTicket }}</span>
          </div>

          <dl class="grid grid-cols-1 gap-x-4 gap-y-1 text-sm text-gray-600 dark:text-gray-300 sm:grid-cols-2">
            <div class="flex gap-1">
              <dt class="shrink-0 text-gray-400">{{ t.parkCard.area }}</dt>
              <dd class="min-w-0 truncate">{{ park.areaZh || d.common.dash }}</dd>
            </div>
            <div class="flex gap-1">
              <dt class="shrink-0 text-gray-400">{{ t.parkCard.openTime }}</dt>
              <dd class="truncate">{{ park.openTime || d.common.dash }}</dd>
            </div>
            <div class="flex gap-1 sm:col-span-2">
              <dt class="shrink-0 text-gray-400">{{ t.parkCard.address }}</dt>
              <dd class="min-w-0 truncate">{{ park.address || d.common.dash }}</dd>
            </div>
          </dl>

          <!-- 介绍固定占两行：有短有长都只显两行，缺的那张也占满这两行的高度 -->
          <p class="line-clamp-2 min-h-[2.5rem] text-sm text-gray-500 dark:text-gray-400">
            {{ park.description || d.common.dash }}
          </p>

          <p class="mt-auto pt-1 text-sm text-blue-600 dark:text-blue-400">
            <template v-if="park.ticketCount">{{ fill(t.parkCard.tickets, { n: park.ticketCount }) }}</template>
            <template v-else>{{ t.parkCard.noTicket }}</template>
            <span class="ml-2">{{ t.park.loadTickets }} ›</span>
          </p>
        </div>
      </a>
    </div>
  </section>
</template>
