<script setup lang="ts">
/**
 * 公告详情页：/notice/{id}，从消息中心（或页头铃铛）点一条公告消息进来。
 *
 * content 必须过 sanitizeRichText 才上屏：这一格是后台富文本编辑器那份原文，直接 v-html
 * 等于让能登后台的人在所有消费者页面上执行脚本（详见 lib/richtext.ts）。
 * 清洗只在下面 contentHtml 那一处做，模板里那个 v-html 只认它。
 */
import { computed, onMounted, ref } from 'vue';
import { fetchNoticeDetail, type NoticeDetail as Notice } from '@/lib/notice';
import { sanitizeRichText } from '@/lib/richtext';
import { useI18n } from '@/i18n';

/** locale 由页面壳递进来：岛里拿不到 Astro 对象（见 @/i18n 顶上那段） */
const props = defineProps<{ noticeId: number; locale?: string }>();
const { d, href } = useI18n();

const notice = ref<Notice | null>(null);
const loading = ref(true);
const error = ref('');

const contentHtml = computed(() => sanitizeRichText(notice.value?.content ?? ''));

onMounted(async () => {
  try {
    notice.value = await fetchNoticeDetail(props.noticeId);
  } catch (err) {
    error.value = (err as Error).message;
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <p v-if="loading" class="py-10 text-center text-sm text-gray-500 dark:text-gray-400">{{ d.common.loading }}</p>

  <p v-else-if="error" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
    {{ error }}
    <a :href="href('/user?section=messages')" class="ml-2 underline">{{ d.noticeDetail.backToList }}</a>
  </p>

  <article v-else-if="notice">
    <a :href="href('/user?section=messages')" class="text-sm text-blue-600 hover:underline dark:text-blue-400"
      >← {{ d.noticeDetail.listLink }}</a
    >

    <h1 class="mt-2 text-xl font-semibold">{{ notice.title }}</h1>
    <p v-if="notice.createTime" class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ notice.createTime }}</p>

    <!-- 富文本那格：白名单之外的标签已经被拆成纯文字，这里只补排版。
         Tailwind 的选择器进不去 v-html 里的节点，所以用 [&_x] 这种任意变体按标签点名。 -->
    <div
      v-if="contentHtml"
      class="mt-6 rounded-lg border border-gray-200 bg-white p-4 text-sm leading-7 text-gray-700 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300 [&_a]:text-blue-600 [&_a]:underline dark:[&_a]:text-blue-400 [&_h1]:mt-4 [&_h1]:text-lg [&_h1]:font-semibold [&_h2]:mt-4 [&_h2]:text-base [&_h2]:font-semibold [&_h3]:mt-3 [&_h3]:font-semibold [&_img]:my-2 [&_img]:max-w-full [&_li]:ml-6 [&_li]:list-disc [&_ol]:my-2 [&_p]:my-2 [&_pre]:my-2 [&_pre]:overflow-x-auto [&_pre]:rounded [&_pre]:bg-gray-100 [&_pre]:p-2 dark:[&_pre]:bg-gray-700 [&_table]:my-2 [&_td]:border [&_td]:border-gray-200 [&_td]:p-1.5 dark:[&_td]:border-gray-700 [&_th]:border [&_th]:border-gray-200 [&_th]:p-1.5 dark:[&_th]:border-gray-700 [&_ul]:my-2"
      v-html="contentHtml"
    ></div>
    <p v-else class="mt-6 py-6 text-center text-sm text-gray-500 dark:text-gray-400">{{ d.noticeDetail.noContent }}</p>
  </article>
</template>
