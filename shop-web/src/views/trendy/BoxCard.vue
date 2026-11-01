<script setup lang="ts">
/**
 * 一张盲盒卡片（专区首页那一排、盲盒页那一排都用它）。
 *
 * 卡片不是岛：语种由外面那颗岛钉到棵上，这里 inject 拿同一份（口径同 ProductCard）。
 * 玩法/状态/票种类型那几档的称呼走后端字典（lib/dict.ts），字典没到时先留空，
 * 不自己抄一份中文兜底 —— 那样又会出现在四份 i18n 里各改一半的那种坑。
 */
import { computed } from 'vue';
import { useI18n } from '@/i18n';
import { dictLabel } from '@/lib/dict';
import type { TrendyBox } from '@/lib/trendy';

const props = defineProps<{ box: TrendyBox }>();

const { d, fill, href } = useI18n();
const t = d.trendy.card;

/** 端盒那一档只有卖整盒的盒才有意义（端盒价没配就是不卖整盒） */
const endBoxText = computed(() =>
  props.box.endBoxPrice == null ? null : Number(props.box.endBoxPrice).toFixed(2),
);
const drawText = computed(() =>
  props.box.price == null ? null : Number(props.box.price).toFixed(2),
);

/** 隐藏款概率：盒上勾了「公示概率」才念得出，没勾就整行不挂（合规那一刀按盒走） */
const hiddenText = computed(() => {
  if (props.box.isProbabilityShow !== 1 || props.box.hiddenRate == null) return null;
  return fill(t.probability, { p: `${(Number(props.box.hiddenRate) * 100).toFixed(2)}%` });
});

const remainText = computed(() =>
  props.box.remainCount == null ? t.unlimited : fill(t.remain, { n: props.box.remainCount }),
);

/** 款式预览：图优先，没图退成名字（后台允许款式不配图） */
const styles = computed(() => (props.box.styles ?? []).slice(0, 8));
</script>

<template>
  <!-- 整张卡片是链接：点进这台盒的详情页（整台款式、概率表、玩法说明都在那一页） -->
  <a
    :href="href(`/trendy/box/${box.id}`)"
    class="flex h-full flex-col overflow-hidden rounded-xl border border-gray-200 bg-white transition-shadow hover:shadow-md dark:border-gray-700 dark:bg-gray-800"
  >
    <div class="relative aspect-[4/3] bg-gray-100 dark:bg-gray-700">
      <img v-if="box.coverImage" :src="box.coverImage" :alt="box.boxName" class="h-full w-full object-cover" />
      <span v-else class="flex h-full w-full items-center justify-center text-sm text-gray-400">{{ t.noImage }}</span>
      <span
        class="absolute left-2 top-2 rounded-full bg-black/60 px-2 py-0.5 text-xs text-white"
        v-if="dictLabel('trendy_box_status', box.status)"
      >
        {{ dictLabel('trendy_box_status', box.status) }}
      </span>
    </div>

    <div class="flex flex-1 flex-col gap-2 p-4">
      <div>
        <h3 class="truncate text-sm font-medium">{{ box.boxName }}</h3>
        <p class="mt-1 truncate text-xs text-gray-500 dark:text-gray-400">
          {{ box.trendyName || t.noSeries }}
          <template v-if="dictLabel('trendy_box_type', box.boxType)">
            · {{ dictLabel('trendy_box_type', box.boxType) }}
          </template>
        </p>
      </div>

      <!-- 款式预览：一排小图，一眼看得出这一台里有什么 -->
      <div v-if="styles.length" class="flex gap-1.5 overflow-hidden">
        <span
          v-for="(item, i) in styles"
          :key="i"
          class="relative h-10 w-10 shrink-0 overflow-hidden rounded bg-gray-100 dark:bg-gray-700"
          :title="item.styleName || ''"
        >
          <img v-if="item.styleImage" :src="item.styleImage" :alt="item.styleName || ''" class="h-full w-full object-cover" />
          <span v-else class="flex h-full w-full items-center justify-center text-[10px] text-gray-400">
            {{ (item.styleName || '').slice(0, 2) }}
          </span>
          <span
            v-if="item.isHidden === 1"
            class="absolute bottom-0 left-0 right-0 bg-amber-500/80 text-center text-[10px] text-white"
          >
            {{ t.hidden }}
          </span>
        </span>
      </div>

      <div class="mt-auto flex flex-wrap items-baseline gap-x-3 gap-y-1 pt-1">
        <span v-if="drawText" class="text-base font-semibold text-red-600 dark:text-red-400">
          ¥{{ drawText }}<span class="text-xs font-normal">{{ ` ${t.drawPrice}` }}</span>
        </span>
        <span v-if="endBoxText" class="text-sm text-gray-600 dark:text-gray-300">
          ¥{{ endBoxText }}<span class="text-xs">{{ ` ${t.endBoxPrice}` }}</span>
        </span>
      </div>

      <p class="flex flex-wrap gap-x-3 text-xs text-gray-500 dark:text-gray-400">
        <span>{{ remainText }}</span>
        <span v-if="box.itemCount != null">{{ t.styles }} {{ box.itemCount }} {{ t.stylesUnit }}</span>
        <span v-if="hiddenText">{{ hiddenText }}</span>
      </p>

      <p v-if="box.status === 1" class="text-xs text-gray-400">{{ t.notStartTip }}</p>
    </div>
  </a>
</template>
