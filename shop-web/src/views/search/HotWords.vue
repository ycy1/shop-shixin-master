<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useI18n } from '@/i18n';
import { fetchHotWords } from '@/lib/search';

/**
 * 首页搜索框下那一排热搜：一排能点的词，点一下就等于替人搜了这个词
 * （链接就是搜索框那张原生表单会拼出来的形状 /search?kw=xxx，落默认的商品那一块）。
 *
 * 词由服务端 Redis 那边维护（GET /api/web/shop/search/hot），这一份只管摆：前端不排序、不去重、不加权重，
 * 回来的顺序就是被搜次数从多到少。一条都没就整块不出现，也不留一行「暂无热搜」占位。
 */
const words = ref<string[]>([]);

/** 这一颗不是岛：语种由首页那块岛 useI18n() 时钉到棵上，这里 inject 拿同一份 */
const { d, href } = useI18n();

onMounted(() => {
  fetchHotWords()
    .then((list) => {
      words.value = list;
    })
    .catch(() => {
      // 读不出来就把这一排收掉：它是搜索框下面的一句提示，不值得在首页占一条错误位
    });
});
</script>

<template>
  <section
    v-if="words.length"
    class="flex flex-wrap items-center justify-center gap-2 text-sm"
    :aria-label="d.hotWords.ariaLabel"
  >
    <span class="text-gray-500 dark:text-gray-400">{{ d.hotWords.label }}</span>
    <a
      v-for="word in words"
      :key="word"
      :href="href(`/search?kw=${encodeURIComponent(word)}`)"
      class="rounded-full border border-gray-300 px-3 py-1 text-gray-700 transition-colors hover:border-blue-500 hover:text-blue-600 dark:border-gray-600 dark:text-gray-300 dark:hover:border-blue-400 dark:hover:text-blue-400"
    >
      {{ word }}
    </a>
  </section>
</template>
