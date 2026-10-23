<script setup lang="ts">
import { useI18n } from '@/i18n';

/**
 * 首页和搜索结果页共用这一颗搜索框（两处各写一遍就是第三份样板了，所以抽出来）。
 *
 * 提交刻意用原生的 <form action="/search" method="get">，不写 JS：浏览器自己就拼出 /search?kw=xxx，
 * 回车、点按钮、禁用 JS 三种走法都是同一个结果；而且搜这一趟是换页不是换组件，本来就该让地址栏变。
 * tab 只从搜索页传过来（首页没有 tab），带上它就等于「在我看的那个 tab 里换个词再搜」。
 */
const props = defineProps<{ keyword?: string | undefined; tab?: string }>();

/** 这一颗不是岛：语种由首页/搜索页那块岛 useI18n() 时钉到棵上，这里 inject 拿同一份 */
const { d, href } = useI18n();
</script>

<template>
  <!-- action 也得包 href()：原生表单提交是照着这一格拼 URL 的，写死 /search 的话从 /en 搜一趟就掉回中文那档 -->
  <form :action="href('/search')" method="get" class="flex w-full gap-2" role="search">
    <input v-if="props.tab" type="hidden" name="tab" :value="props.tab" />
    <label class="relative flex-1">
      <span class="sr-only">{{ d.searchBox.keyword }}</span>
      <svg
        class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        aria-hidden="true"
      >
        <circle cx="11" cy="11" r="7" />
        <path stroke-linecap="round" d="m20 20-3.5-3.5" />
      </svg>
      <input
        name="kw"
        type="search"
        :value="props.keyword"
        :placeholder="d.searchBox.placeholder"
        class="w-full rounded-lg border border-gray-300 py-2 pl-9 pr-3 outline-none focus:border-blue-500 dark:border-gray-600 dark:bg-gray-900"
      />
    </label>
    <button
      type="submit"
      class="shrink-0 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
    >
      {{ d.site.search }}
    </button>
  </form>
</template>
