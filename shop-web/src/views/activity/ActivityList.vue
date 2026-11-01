<script setup lang="ts">
/**
 * 活动列表页：头部导航「活动」那一颗的落点（数据来自后台的活动管理，只回投放中那几条）。
 *
 * 卡片整颗是链接，点进 /activity/{id} 那张详情页，这一页不做跳转分流 ——
 * 一件活动要跳商品、分类还是外链，是详情页自己的事（那一页才拿得到 content，也才讲得清这条活动是什么）。
 * 翻页照搜索页那套「再看 N 个」，不学首页的滚动加载：活动本来就少，得让人看见一共几条。
 *
 * 埋点沿用首页轮播那两条口径（一次访问里同一条只报一回曝光）：
 * 这里的「被看见」是一张卡片进过视口，所以观察器挂在每张卡片上，而不是整块列表上 ——
 * 整块进视口时下面那几排其实还在屏幕外，报出去就成了虚数。
 */
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import { FwbButton } from 'flowbite-vue';
import {
  ACTIVITY_EVENT_CLICK,
  ACTIVITY_EVENT_VIEW,
  activityLabel,
  activityTimeText,
  fetchActivityPage,
  trackActivity,
  type WebActivityRow,
} from '@/lib/activities';
import { useI18n } from '@/i18n';

/** locale 由页面壳递进来：岛里拿不到 Astro 对象（见 @/i18n 顶上那段） */
defineProps<{ locale?: string }>();
const { d, fill, href } = useI18n();

const PAGE_SIZE = 12;

const rows = ref<WebActivityRow[]>([]);
const page = ref(0);
const pages = ref(0);
const total = ref(0);
const loading = ref(false);
const error = ref('');
/** 后面某一页没 Load 出来另说一行：已经画出来的卡片不撤走（和搜索页同一处理） */
const moreError = ref('');

/** 这一次访问里已经报过曝光的：往下续页时同一张卡片还在 DOM 里，别再报第二遍 */
const viewed = new Set<number>();

const grid = ref<HTMLElement | null>(null);
let observer: IntersectionObserver | undefined;

const hasMore = computed(() => page.value < pages.value);

function onCardClick(one: WebActivityRow) {
  // 先发埋点再让链接走：这一条是火忘的（不 await），点下去照样进详情页
  trackActivity(one.id, ACTIVITY_EVENT_CLICK);
}

function observeCards() {
  const box = grid.value;
  if (!box || !observer) return;
  box.querySelectorAll('[data-activity-id]').forEach((el) => observer?.observe(el));
}

async function load() {
  loading.value = true;
  // 第 1 页失败顶掉整块，续页失败只补下面那一行，两条各用一个变量
  const first = rows.value.length === 0;
  if (first) error.value = '';
  else moreError.value = '';
  try {
    const data = await fetchActivityPage(page.value + 1, PAGE_SIZE);
    // 两页之间有人上下架，同一条可能被捞两次 ⇒ 按 id 去重再拼
    const seen = new Set(rows.value.map((one) => one.id));
    rows.value.push(...data.records.filter((one) => !seen.has(one.id)));
    page.value += 1;
    pages.value = data.pages ?? 0;
    total.value = data.total ?? 0;
    await nextTick();
    observeCards();
  } catch (err) {
    const message = (err as Error).message;
    if (first) error.value = message;
    else moreError.value = message;
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        const id = Number(entry.target.getAttribute('data-activity-id'));
        observer?.unobserve(entry.target);
        if (!Number.isInteger(id) || viewed.has(id)) continue;
        viewed.add(id);
        trackActivity(id, ACTIVITY_EVENT_VIEW);
      }
    },
    // 和首页轮播同一口径：提前 100px 就算被看见了，别等卡片正好扫进那一屏
    { rootMargin: '100px' },
  );
  void load();
});

onUnmounted(() => {
  observer?.disconnect();
});
</script>

<template>
  <section>
    <div class="mb-6">
      <h1 class="text-xl font-semibold">{{ d.site.activity }}</h1>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ d.activityList.subtitle }}</p>
    </div>

    <p v-if="loading && !rows.length" class="py-10 text-center text-sm text-gray-500 dark:text-gray-400">
      {{ d.common.loading }}
    </p>

    <p v-else-if="error && !rows.length" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
      {{ error }}
      <button type="button" class="ml-2 underline" @click="load">{{ d.detailCommon.retry }}</button>
    </p>

    <p v-else-if="!rows.length" class="py-10 text-center text-sm text-gray-500 dark:text-gray-400">
      {{ d.activityList.empty }}
    </p>

    <div v-else ref="grid" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <a
        v-for="one in rows"
        :key="one.id"
        :href="href(`/activity/${one.id}`)"
        :data-activity-id="one.id"
        class="group overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm transition-shadow hover:shadow-md dark:border-gray-700 dark:bg-gray-800"
        @click="onCardClick(one)"
      >
        <!-- 这一条接口不挑图（文字类活动也得上架得被看见），没配图就给一块纯色顶着，别让卡片塌成一行 -->
        <img
          v-if="one.image"
          :src="one.image"
          :alt="activityLabel(one, d)"
          class="aspect-[16/9] w-full object-cover"
          loading="lazy"
        />
        <div v-else class="flex aspect-[16/9] w-full items-center justify-center bg-gray-100 dark:bg-gray-700">
          <span class="px-4 text-center text-sm text-gray-400">{{ activityLabel(one, d) }}</span>
        </div>

        <div class="p-4">
          <h2 class="truncate text-base font-semibold text-gray-900 group-hover:underline dark:text-gray-100" :title="activityLabel(one, d)">
            {{ activityLabel(one, d) }}
          </h2>
          <p v-if="activityTimeText(one, d)" class="mt-1 text-xs text-gray-500 dark:text-gray-400">{{ activityTimeText(one, d) }}</p>
        </div>
      </a>
    </div>

    <div v-if="rows.length" class="mt-6 flex flex-col items-center gap-2">
      <p v-if="moreError" class="text-sm text-red-600 dark:text-red-400">
        {{ fill(d.activityList.moreFailed, { msg: moreError }) }}
        <button type="button" class="ml-2 underline" @click="load">{{ d.detailCommon.retry }}</button>
      </p>
      <span class="text-sm text-gray-500 dark:text-gray-400">{{ fill(d.common.total, { n: total, unit: d.activityList.countUnit }) }}</span>
      <FwbButton v-if="hasMore && !moreError" color="blue" size="sm" :disabled="loading" @click="load">
        {{ loading ? d.common.loading : fill(d.common.more, { n: PAGE_SIZE }) }}
      </FwbButton>
    </div>
  </section>
</template>
