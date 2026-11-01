<script setup lang="ts">
/**
 * 活动详情页：/activity/{id}，活动列表那张卡片点进来的落点。
 *
 * 这一页是这一条活动「跳哪儿」的唯一场合 —— 列表页只负责让人挑一条，
 * 而这里才拿得到 content（后台那格富文本），也能把「这条活动讲什么」摆出来再让人决定走不走。
 *
 * content 必须过 sanitizeRichText 才上屏：这一格是后台自由文本，接口又不要登录，
 * 直接 v-html 等于让能登后台的人在所有消费者页面上执行脚本（详见 lib/richtext.ts）。
 * 数据是 onMounted 捞回来才清洗的，DOMParser 只在浏览器那一趟跑，岛在服务端那一趟碰不到它。
 */
import { computed, onMounted, ref } from 'vue';
import { FwbButton } from 'flowbite-vue';
import {
  ACTIVITY_EVENT_CLICK,
  activityLabel,
  activityTarget,
  fetchActivityDetail,
  trackActivity,
  type WebActivityDetail,
} from '@/lib/activities';
import { sanitizeRichText } from '@/lib/richtext';
import { useI18n } from '@/i18n';

/** locale 由页面壳递进来：岛里拿不到 Astro 对象（见 @/i18n 顶上那段） */
const props = defineProps<{ activityId: number; locale?: string }>();
/** 这一页自己已经有一颗叫 href 的 computed（活动跳转那条链接），所以把 useI18n 那颗别名开，免得撞名 */
const { d, fill, href: localeHref } = useI18n();

const detail = ref<WebActivityDetail | null>(null);
const loading = ref(true);
const error = ref('');

/** 标题没填回落活动名称，两格都空着给个兜底名 —— 和列表卡片同一个说法（lib 那一份） */
const heading = computed(() => (detail.value ? activityLabel(detail.value, d) : ''));

/**
 * 跳转目标复用首页轮播那一份拼法（1 商品 2 分类 4 外链，5/6 拼不出目标就是点不动）。
 * activityTarget 只认 linkType/linkValue 两格，详情这份直接传就行。
 */
const target = computed(() => (detail.value ? activityTarget(detail.value) : {}));
const href = computed(() => target.value.href);
/** 分类那一档在这一页是换页（首页的筛选栏在另一块 island 里），所以走 /?categoryId= */
const categoryHref = computed(() => {
  const categoryId = target.value.categoryId;
  return categoryId == null ? undefined : `/?categoryId=${categoryId}`;
});
/** 站内那两档（商品详情、首页的分类筛选）要带语种前缀；后台手填的外链那一档原样走，前缀不该加在它头上 */
const actionHref = computed(() => {
  const raw = href.value ?? categoryHref.value;
  if (!raw) return undefined;
  return raw.startsWith('/') ? localeHref(raw) : raw;
});
/** 外链那一档要跳出本站，新窗口开；站内两档跟着 ClientRouter 免刷新走 */
const external = computed(() => !!href.value && !href.value.startsWith('/'));

const actionText = computed(() => {
  if (href.value) return external.value ? d.activityDetail.openLink : d.activityDetail.look;
  if (categoryHref.value) return d.activityDetail.viewCategory;
  return '';
});

/** 清洗只在这一处做：模板里那个 v-html 只认 computed，别让别的地方漏着原文 */
const contentHtml = computed(() => sanitizeRichText(detail.value?.content ?? ''));

function timeText(one: WebActivityDetail) {
  const from = one.startTime?.slice(0, 16) ?? '';
  const to = one.endTime?.slice(0, 16) ?? '';
  if (from && to) return fill(d.activity.between, { a: from, b: to });
  if (from) return fill(d.activity.from, { a: from });
  if (to) return fill(d.activity.to, { b: to });
  return '';
}

function onActionClick() {
  // 点这一颗就是「这条活动被人当真点了」，和列表卡片那一次点击记同一档；发出去就不管回包
  const one = detail.value;
  if (one && actionHref.value) trackActivity(one.id, ACTIVITY_EVENT_CLICK);
}

onMounted(async () => {
  try {
    detail.value = await fetchActivityDetail(props.activityId);
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
    <a :href="localeHref('/activity')" class="ml-2 underline">{{ d.activityDetail.backToList }}</a>
  </p>

  <section v-else-if="detail">
    <a :href="localeHref('/activity')" class="text-sm text-blue-600 hover:underline dark:text-blue-400">← {{ d.activityDetail.listLink }}</a>

    <h1 class="mt-2 text-xl font-semibold">{{ heading }}</h1>
    <p v-if="timeText(detail)" class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ timeText(detail) }}</p>

    <!-- 主图和视频各占一整块：配了视频就上下排，不做成二选一（运营常是图先上、视频后补） -->
    <img
      v-if="detail.image"
      :src="detail.image"
      :alt="heading"
      class="mt-4 w-full rounded-lg border border-gray-200 object-cover dark:border-gray-700"
    />
    <video
      v-if="detail.videoUrl"
      :src="detail.videoUrl"
      controls
      preload="metadata"
      class="mt-4 w-full rounded-lg border border-gray-200 bg-black dark:border-gray-700"
    ></video>

    <div v-if="actionHref" class="mt-5">
      <FwbButton
        color="blue"
        :href="actionHref"
        :target="external ? '_blank' : undefined"
        :rel="external ? 'noopener noreferrer nofollow' : undefined"
        @click="onActionClick"
      >
        {{ actionText }}
      </FwbButton>
    </div>

    <!-- 富文本那格：白名单之外的标签已经被拆成纯文字，这里只补排版。
         Tailwind 的选择器进不去 v-html 里的节点，所以用 [&_x] 这种任意变体按标签点名。 -->
    <div
      v-if="contentHtml"
      class="mt-6 rounded-lg border border-gray-200 bg-white p-4 text-sm leading-7 text-gray-700 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300 [&_a]:text-blue-600 [&_a]:underline dark:[&_a]:text-blue-400 [&_h1]:mt-4 [&_h1]:text-lg [&_h1]:font-semibold [&_h2]:mt-4 [&_h2]:text-base [&_h2]:font-semibold [&_h3]:mt-3 [&_h3]:font-semibold [&_img]:my-2 [&_img]:max-w-full [&_li]:ml-6 [&_li]:list-disc [&_ol]:my-2 [&_p]:my-2 [&_pre]:my-2 [&_pre]:overflow-x-auto [&_pre]:rounded [&_pre]:bg-gray-100 [&_pre]:p-2 dark:[&_pre]:bg-gray-700 [&_table]:my-2 [&_td]:border [&_td]:border-gray-200 [&_td]:p-1.5 dark:[&_td]:border-gray-700 [&_th]:border [&_th]:border-gray-200 [&_th]:p-1.5 dark:[&_th]:border-gray-700 [&_ul]:my-2"
      v-html="contentHtml"
    ></div>
    <p v-else class="mt-6 py-6 text-center text-sm text-gray-500 dark:text-gray-400">{{ d.activityDetail.noContent }}</p>
  </section>
</template>
