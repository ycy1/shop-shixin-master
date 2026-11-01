<script setup lang="ts">
/**
 * 首页「全部商品」上面那一块活动轮播（数据来自后台的活动管理）。
 *
 * 轨道、指示点、左右箭头照抄门店那一圈图片（views/store/StoreCarousel.vue），这里只多三件事：
 * 1）自己捞数据 —— 活动位要哪几条是后端那三道判据（首页轮播位 / 投放中 / 配了图），页面不该再挑一遍；
 * 2）每一张是能点的（跳转怎么拼见 lib/activities.ts 的 activityTarget，拼不出目标的那一档渲染成 div，不给一个按不动的链接）；
 * 3）埋点 —— 曝光的口径是「这一张真被看见过」，所以两道闸门都得在场：整块进视口 + 是当前这一张，
 *    并且一条活动在这次访问里只报一回（轮播会转回来，每转一圈就把曝光量抬上去就不是事实了）。
 *    自动播放同样只在这块进视口时走：没人看着的那一段不该偷偷换图、偷偷报数。
 *
 * 加载中整块不画（首页上面已经有一排在转，别再垫一块空轨道），只有取数失败才红一行。
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import DotTrack from '@/components/DotTrack.vue';
import { useI18n } from '@/i18n';
import { ACTIVITY_EVENT_CLICK, ACTIVITY_EVENT_VIEW, activityTarget, fetchActivities, trackActivity, type WebActivity } from '@/lib/activities';

/** 跳转类型是分类时抛给首页筛选栏（那一块就在同一张 island 里，不绕 URL） */
const emit = defineEmits<{ (event: 'selectCategory', categoryId: number): void }>();

/** 这一颗自己不接 locale：首页那块岛（Storefront）已经 useI18n() 往棵上钉过语种，inject 就行 */
const { d, fill, href } = useI18n();

const AUTOPLAY_MS = 4000;

const activities = ref<WebActivity[]>([]);
const error = ref('');
const active = ref(0);
/** 指针压在这一块上就别自己往前走：正要点某一条时被换掉，点下去就成了另一条 */
const hovered = ref(false);
/** 观察器回调之前当「没进视口」：宁可晚一拍开始转，也别在没人看见的时候把曝光报出去 */
const inView = ref(false);

const root = ref<HTMLElement | null>(null);
let timer: ReturnType<typeof setInterval> | undefined;
let observer: IntersectionObserver | undefined;

/** 这一次访问里已经报过曝光的：轮播会转回同一条，报一回就够 */
const viewed = new Set<number>();

interface Slide {
  activity: WebActivity;
  /** 外链和商品详情是链接，其余两档点不动 */
  href: string | null;
  /** 分类那一档：选中它的是首页下面那三格筛选，不是换页 */
  categoryId: number | null;
  /** 图上那一行字：标题没填回落名称，两格都没有就整行不渲染 */
  label: string;
}

/** 活动位这一格是后台手填的自由文本：外链（http(s) 打头）原样递出去，站内那一档才加语种前缀 */
function localizedHref(value: string | null | undefined): string | null {
  if (!value) return null;
  return /^https?:\/\//i.test(value) ? value : href(value);
}

const slides = computed<Slide[]>(() =>
  activities.value.map((activity) => {
    const target = activityTarget(activity);
    return {
      activity,
      href: localizedHref(target.href),
      categoryId: target.categoryId ?? null,
      label: activity.title?.trim() || activity.activityName?.trim() || '',
    };
  }),
);

function go(index: number) {
  active.value = Math.max(0, Math.min(slides.value.length - 1, index));
}

function next() {
  // 到尾一条接着回第一条：这是「一圈展示」，不留一个按不动的尾端
  active.value = active.value >= slides.value.length - 1 ? 0 : active.value + 1;
}

function step(delta: number) {
  const last = slides.value.length - 1;
  const target = active.value + delta;
  go(target < 0 ? last : target > last ? 0 : target);
}

/** 当前这一张进过视口就报一次曝光：报数发出去就不管回包，它不该把活动位变成红框 */
function reportView() {
  if (!inView.value) return;
  const one = slides.value[active.value];
  if (!one || viewed.has(one.activity.id)) return;
  viewed.add(one.activity.id);
  trackActivity(one.activity.id, ACTIVITY_EVENT_VIEW);
}

function startTimer() {
  stopTimer();
  if (slides.value.length < 2) return;
  timer = setInterval(() => {
    if (!hovered.value && inView.value) next();
  }, AUTOPLAY_MS);
}

function stopTimer() {
  if (timer) {
    clearInterval(timer);
    timer = undefined;
  }
}

function onSlideClick(one: Slide) {
  trackActivity(one.activity.id, ACTIVITY_EVENT_CLICK);
  if (one.categoryId !== null) emit('selectCategory', one.categoryId);
}

async function load() {
  try {
    activities.value = await fetchActivities();
  } catch (err) {
    error.value = (err as Error).message;
    return;
  }
  startTimer();
  reportView();
}

watch([active, inView], reportView);

// 这一块是取到数据才出现的，root 那格晚于 onMounted 才有值，所以观察器跟着它补挂
watch(root, (el) => {
  if (!observer) return;
  observer.disconnect();
  if (el) observer.observe(el);
});

onMounted(() => {
  observer = new IntersectionObserver(
    (entries) => {
      inView.value = entries.some((entry) => entry.isIntersecting);
    },
    // 提前 100px 就算进来了：曝光别等人正好扫到那一屏才报，转圈也别到跟前才开始
    { rootMargin: '100px' },
  );
  if (root.value) observer.observe(root.value);
  void load();
});

onUnmounted(() => {
  stopTimer();
  observer?.disconnect();
});
</script>

<template>
  <p v-if="error" class="mb-10 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
    {{ fill(d.carousel.loadFailed, { msg: error }) }}
  </p>

  <section
    v-else-if="activities.length"
    ref="root"
    class="relative mb-10 overflow-hidden rounded-lg border border-gray-200 bg-gray-100 dark:border-gray-700 dark:bg-gray-800"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false"
  >
    <!-- 整宽平铺：轨道按 100% 一格平移，所以每格钉 w-full shrink-0，高度由这一格自己的 aspect 定 -->
    <div
      class="flex aspect-[16/9] transition-transform duration-500 ease-out sm:aspect-[21/9]"
      :style="{ transform: `translateX(-${active * 100}%)` }"
    >
      <component
        v-for="(one, index) in slides"
        :is="one.href ? 'a' : one.categoryId !== null ? 'button' : 'div'"
        :key="one.activity.id"
        :href="one.href ?? undefined"
        :type="one.href ? undefined : 'button'"
        :aria-label="one.label || undefined"
        :tabindex="index === active ? undefined : -1"
        class="relative block h-full w-full shrink-0 grow-0 basis-full"
        @click="onSlideClick(one)"
      >
        <img
          :src="one.activity.image"
          :alt="one.label"
          class="h-full w-full object-cover"
          loading="lazy"
          :aria-hidden="index !== active"
        />
        <!-- pointer-events-none：整格就是链接，那一行字别把点击截走 -->
        <p
          v-if="one.label"
          class="pointer-events-none absolute inset-x-0 bottom-0 truncate bg-gradient-to-t from-black/60 to-transparent px-4 pb-9 pt-8 text-sm text-white sm:text-base"
        >
          {{ one.label }}
        </p>
      </component>
    </div>

    <!-- 指示点：一条半透明圆角底槽 + 一颗在里面跑的水珠（components/DotTrack.vue） -->
    <div class="absolute bottom-3 left-1/2 -translate-x-1/2">
      <DotTrack :count="slides.length" :current="active" :label="d.carousel.slideAria" @select="go" />
    </div>

    <button
      v-if="slides.length > 1"
      type="button"
      :aria-label="d.carousel.prev"
      class="absolute left-2 top-1/2 -translate-y-1/2 rounded-full bg-black/30 p-2 text-white hover:bg-black/50"
      @click="step(-1)"
    >
      <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
        <path stroke-linecap="round" stroke-linejoin="round" d="m15 6-6 6 6 6" />
      </svg>
    </button>
    <button
      v-if="slides.length > 1"
      type="button"
      :aria-label="d.carousel.next"
      class="absolute right-2 top-1/2 -translate-y-1/2 rounded-full bg-black/30 p-2 text-white hover:bg-black/50"
      @click="step(1)"
    >
      <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
        <path stroke-linecap="round" stroke-linejoin="round" d="m9 6 6 6-6 6" />
      </svg>
    </button>
  </section>
</template>
