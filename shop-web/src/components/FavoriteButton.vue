<script setup lang="ts">
/**
 * 详情页右侧那颗收藏按钮：一颗管两个方向（收藏 / 取消收藏），形状和位置照着布局里那颗「回到顶部」抄 ——
 * 同为 fixed right-4 的圆颗粒，只是抬在它那上面一格，两颗一起贴着右下角。
 *
 * 收藏这一条按 (类型, 对象) 点，不走收藏行的 id：页面上只有「这一个我收藏过没」这一个事实，
 * id 是个人中心列表那一路才拿得到的东西。
 * 没登录时也画同一颗，只是挂上 data-open-login —— 头部那颗登录窗按属性认领点击（island 之间不共享 store），
 * 登完登录态一流转，下面那个 watch 自己把状态回读一次，不用刷新整页。
 * 那一格绑的是 null 而不是 false：data-* 不是布尔属性，Vue 会把 false 原样写成 data-open-login="false"，
 * 而登录窗认的是属性存在与否（closest('[data-open-login]')），留着 "false" 就等于登录后点它还要弹登录窗。
 */
import { computed, onMounted, ref, watch } from 'vue';
import { useI18n } from '@/i18n';
import { useAuthStore } from '@/stores/auth';
import { fetchFavoriteState, toggleMyFavorite } from '@/lib/profile';

const props = defineProps<{
  /** 1商品 2门店 3品牌（字典 favorite_target_type），targetId 指向哪张表由它定 */
  targetType: number;
  targetId: number;
  /** 按钮上不说字，只进 title：给个「收藏「迪奥上海总店」」这种说法；详情没回来时传的就是 undefined */
  name?: string | undefined;
}>();

const { d, fill } = useI18n();

const auth = useAuthStore();

/** 登录态读 localStorage，服务端那一趟读不到 ⇒ 第一趟两边都画「没登录」，否则撞 hydration */
const ready = ref(false);
const signedIn = computed(() => ready.value && auth.isLoggedIn);

const favorited = ref(false);
const loading = ref(false);
const busy = ref(false);
const error = ref('');

const subject = computed(() => (props.name ? fill(d.favorite.quotedName, { name: props.name }) : ''));
const hintText = computed(() => {
  if (!signedIn.value) return fill(d.favorite.loginFirst, { target: subject.value });
  if (loading.value || busy.value) return d.favorite.busy;
  return favorited.value ? fill(d.favorite.remove, { target: subject.value }) : fill(d.favorite.add, { target: subject.value });
});

async function refresh() {
  loading.value = true;
  error.value = '';
  try {
    favorited.value = await fetchFavoriteState(props.targetType, props.targetId);
  } catch (err) {
    error.value = (err as Error).message;
  } finally {
    loading.value = false;
  }
}

async function toggle() {
  // 没登录这一支走不到这里：那颗按钮上的 data-open-login 归头部的登录窗认领
  if (!signedIn.value || busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    favorited.value = await toggleMyFavorite(props.targetType, props.targetId);
  } catch (err) {
    // 后端那句业务提示（「所选商品不存在」之类）比状态码准，原样印在按钮左边
    error.value = (err as Error).message;
  } finally {
    busy.value = false;
  }
}

onMounted(() => {
  ready.value = true;
});

// 登录（含弹框里登进来）就把状态捞一次；登出就退回空心，不必再问后端
watch(signedIn, (on) => {
  if (on) void refresh();
  else favorited.value = false;
});
</script>

<template>
  <div class="fixed bottom-24 right-4 z-40 flex items-center gap-2">
    <p
      v-if="error"
      class="rounded bg-red-50 px-2 py-1 text-xs text-red-600 dark:bg-red-900/40 dark:text-red-300"
    >
      {{ error }}
    </p>
    <button
      type="button"
      :title="hintText"
      :aria-label="hintText"
      :aria-pressed="favorited"
      :data-open-login="signedIn ? null : ''"
      :disabled="busy"
      class="rounded-full border p-3 shadow-md transition-colors disabled:opacity-60"
      :class="
        favorited
          ? 'border-red-200 bg-red-50 text-red-500 hover:bg-red-100 dark:border-red-900/60 dark:bg-red-900/40 dark:text-red-400 dark:hover:bg-red-900/60'
          : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-100 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
      "
      @click="toggle"
    >
      <svg
        class="h-4 w-4"
        viewBox="0 0 24 24"
        :fill="favorited ? 'currentColor' : 'none'"
        stroke="currentColor"
        stroke-width="2"
        aria-hidden="true"
      >
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 1 0-7.8 7.8l1 1L12 21.2l7.8-7.8 1-1a5.5 5.5 0 0 0 0-7.8Z"
        />
      </svg>
    </button>
  </div>
</template>
