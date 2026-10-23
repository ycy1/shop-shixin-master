<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from '@/i18n';
import type { WebProduct } from '@/lib/products';

const props = defineProps<{ product: WebProduct; active?: boolean }>();

/** 卡片不是岛：语种由外面那颗岛（首页/搜索页/详情页）useI18n() 时钉到棵上，这里 inject 拿同一份 */
const { d, href } = useI18n();

// 单价在规格那一层，列表只汇总出最小/最大值：摆最低价，真有区间才补一个「起」
const priceText = computed(() => Number(props.product.priceMin ?? 0).toFixed(2));
const hasRange = computed(
  () =>
    props.product.priceMin != null &&
    props.product.priceMax != null &&
    Number(props.product.priceMin) !== Number(props.product.priceMax),
);
// 分类和品牌哪格有值才占一行，两个都缺时整行不挂
const metaLine = computed(() =>
  [props.product.categoryName, props.product.brandName].filter(Boolean).join(' · '),
);
</script>

<template>
  <article
    class="flex h-full flex-col rounded-lg border bg-white p-4 transition-all duration-300 dark:bg-gray-800"
    :class="
      active
        ? 'border-blue-300 shadow-lg dark:border-blue-700'
        : 'border-gray-200 shadow-sm dark:border-gray-700'
    "
  >
    <!-- 图和名合成一个链接跳详情。链接接管了卡片剩下的那格 flex-1，图在它里面照样 flex-1，排版不变。
         「加入购物车」挪到详情页了：一件商品下面好几个规格、每个规格各自一个价，列表这一格定不下来要哪一个 -->
    <a :href="href(`/product/${product.id}`)" class="flex min-h-0 flex-1 flex-col">
      <img
        v-if="product.mainImage"
        :src="product.mainImage"
        :alt="product.productName"
        class="min-h-0 w-full flex-1 rounded object-cover"
        loading="lazy"
      />
      <div
        v-else
        class="flex min-h-0 w-full flex-1 items-center justify-center rounded bg-gray-50 text-sm text-gray-400 dark:bg-gray-700"
      >
        {{ d.card.noImage }}
      </div>

      <h3 class="mt-3 truncate text-base font-semibold text-gray-900 dark:text-gray-100" :title="product.productName">
        {{ product.productName }}
      </h3>
      <p v-if="product.subTitle" class="mt-0.5 line-clamp-1 text-xs text-gray-500 dark:text-gray-400" :title="product.subTitle">
        {{ product.subTitle }}
      </p>
    </a>
    <p v-if="metaLine" class="mt-0.5 truncate text-xs text-gray-500 dark:text-gray-400">{{ metaLine }}</p>

    <!-- 这一行不给汇总数：销量记在每一条规格上、库存是「一个规格 × 一个仓」那一格的账，
         卡片这一档没有规格维度，摊成一件商品一个数会骗人。要看就进详情页选那一档规格（价、库存、赠送积分都在那里按规格给） -->

    <!-- 这一排只给一行的高度：图是 flex-1，这一排一换行就把它顶掉、卡片整张撑高，窄格（英文比中文长）尤其明显。
         所以两颗都钉不折行 —— 价那格 min-w-0 加 ellipsis（数字打头，砍掉的是「起 / and up」那一小截），按钮 shrink-0 保住自己的字 -->
    <div class="mt-3 flex items-center justify-between gap-2">
      <span class="min-w-0 truncate text-base font-semibold text-red-600 dark:text-red-400">
        ¥{{ priceText }}{{ hasRange ? ` ${d.card.priceFrom}` : '' }}
      </span>
      <!-- 这一颗是链接不是按钮：加购要先定规格，那一格只有详情页有 -->
      <a
        :href="href(`/product/${product.id}`)"
        class="shrink-0 whitespace-nowrap rounded-lg bg-blue-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-800 dark:bg-blue-600 dark:hover:bg-blue-700"
        @click.stop
      >{{ d.card.pickSpec }}</a>
    </div>
  </article>
</template>
