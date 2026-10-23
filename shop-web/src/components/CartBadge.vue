<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useI18n } from '@/i18n';
import { useAuthStore } from '@/stores/auth';
import { useCartStore } from '@/stores/cart';

const auth = useAuthStore();
const cart = useCartStore();
const { d, href } = useI18n();

/**
 * 这一格要等挂到浏览器上才认。
 *
 * 份数/金额读的是库里那一份（要带 token 才捞得动），登录态读的是 localStorage，
 * 服务端那一趟两样都没有 ⇒ 第一趟只画那颗图（静态的，两边一模一样），数字等捞回来了再补。
 */
const mounted = ref(false);
const signedIn = computed(() => mounted.value && auth.isLoggedIn);

/** 那两个数说的是「还要买多少」，所以只算还能买的那几行（下架的不算份数也不算钱） */
const shown = computed(() => signedIn.value && !cart.loading);

onMounted(() => {
  mounted.value = true;
});

/**
 * 登录态一翻就各捞一次：登进来要看见车里有几件，登出要把上一个人的那几件从画面上抹掉
 *（stores/auth 在登录/登出时往 window 上抛事件，所以页头那颗登录窗里登进来，这一岛也跟得上）。
 */
watch(signedIn, (on) => {
  if (on) void cart.load();
  else cart.lines = [];
});
</script>

<template>
  <!-- 这一颗只有图和数：页头那条已经有「购物车」三个字了（布局里那颗链接），再说一遍就是同一屏里两个说法。
       图点得动、和页头那颗去的是同一页。 -->
  <a
    :href="href('/cart')"
    :title="d.cartBadge.title"
    class="flex min-h-6 items-center gap-2 text-sm text-gray-700 transition-colors hover:text-blue-600 dark:text-gray-200 dark:hover:text-blue-400"
  >
    <svg
      class="h-5 w-5"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      aria-hidden="true"
    >
      <path
        stroke-linecap="round"
        stroke-linejoin="round"
        d="M3 4h2l2.4 11.2a1.5 1.5 0 0 0 1.5 1.2h8.5a1.5 1.5 0 0 0 1.5-1.2L21 8H6"
      />
      <circle cx="10" cy="20" r="1.4" />
      <circle cx="18" cy="20" r="1.4" />
    </svg>
    <template v-if="shown">
      <span class="inline-flex items-center rounded-full bg-blue-600 px-2.5 py-0.5 text-xs font-medium text-white">
        {{ cart.count }}
      </span>
      <span>¥{{ cart.total.toFixed(2) }}</span>
    </template>
  </a>
</template>
