<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useAuthStore } from '@/stores/auth';
import ModalOverlay from '@/components/ModalOverlay.vue';
import { fetchMyNotifications, fetchUnreadSummary, messageTarget, readNotification, type NotificationRow } from '@/lib/notification';
import { useI18n } from '@/i18n';

const auth = useAuthStore();
/** 语种由页头那颗链接当 prop 递进来：岛里拿不到 Astro 对象（见 @/i18n 顶上那段） */
defineProps<{ locale?: string }>();
const { d, fill, href } = useI18n();

/**
 * 头像/昵称那一格要等挂到浏览器上才认。
 *
 * 登录态读的是 localStorage（stores/auth 建 store 时就读了），服务端那一趟没有它，
 * 两边渲得不一样就会撞 Hydration mismatch —— 和 CartBadge 同一个坑，同一个修法：
 * 第一趟一律画那颗默认人物头像。
 */
const mounted = ref(false);
const showDialog = ref(false);

const tab = ref<'login' | 'register'>('login');
/** 空串 = 没在跑；这一格同时管三颗按钮的禁用，不铺三个 boolean */
const busy = ref('');
const error = ref('');
const notice = ref('');
const avatarBroken = ref(false);

const loginForm = ref({ username: '', password: '' });
const registerForm = ref({ username: '', email: '', code: '', password: '' });
const countdown = ref(0);
let timer: ReturnType<typeof setInterval> | undefined;

const nickname = computed(() => auth.user?.nickname || auth.user?.username || d.auth.me);
/** 登录态读的是 localStorage，服务端那一趟没有 ⇒ 第一趟两边都按没登录画 */
const signedIn = computed(() => mounted.value && auth.isLoggedIn);
/** 注册时后端给的是 dicebear 那种外链头像，挂了也只是退回默认人物头像，进个人中心照旧 */
const avatar = computed(() => (signedIn.value && !avatarBroken.value ? auth.user?.avatar : null));

function message(err: unknown) {
  return (err as Error).message;
}

function onEsc() {
  if (showDialog.value) closeDialog();
  bellOpen.value = false;
}

function openDialog(next: 'login' | 'register' = 'login') {
  tab.value = next;
  error.value = '';
  notice.value = '';
  showDialog.value = true;
}

function closeDialog() {
  showDialog.value = false;
  error.value = '';
  notice.value = '';
}

async function submitLogin() {
  busy.value = 'login';
  error.value = '';
  try {
    await auth.signIn({ ...loginForm.value });
    loginForm.value = { username: '', password: '' };
    avatarBroken.value = false;
    closeDialog();
  } catch (err) {
    error.value = message(err);
  } finally {
    busy.value = '';
  }
}

function startCountdown() {
  countdown.value = 60;
  timer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0 && timer) clearInterval(timer);
  }, 1000);
}

async function onSendCode() {
  if (!registerForm.value.email) {
    error.value = d.auth.needEmail;
    return;
  }
  busy.value = 'code';
  error.value = '';
  notice.value = '';
  try {
    await auth.emailCode(registerForm.value.email);
    notice.value = d.auth.codeSent;
    startCountdown();
  } catch (err) {
    error.value = message(err);
  } finally {
    busy.value = '';
  }
}

async function onRegister() {
  busy.value = 'register';
  error.value = '';
  try {
    await auth.signUp({ ...registerForm.value });
    registerForm.value = { username: '', email: '', code: '', password: '' };
    openDialog('login');
    notice.value = d.auth.registered;
  } catch (err) {
    error.value = message(err);
  } finally {
    busy.value = '';
  }
}

/**
 * 页头那颗未读红点：捞的是 /api/comm/shop/notification/unread 里的 total（消息中心三档 chip 上的那两个数是同一趟）。
 *
 * 只在登录之后发这一趟 —— 那五条接口全要 token，没登录发了也只是红一次。
 * 不做轮询：进这一页捞一次、登进来捞一次、登出清零，个人中心那一页里读没读由它自己那块管（切回来 pick 会再捞）。
 */
const unread = ref(0);

async function loadUnread() {
  try {
    unread.value = (await fetchUnreadSummary()).total;
  } catch {
    // 红点捞不到就当没有：那颗入口本身照常点得动，不在这儿报错
  }
}

/**
 * 页头那颗铃铛：点开就是最近五条 + 一条直达消息中心的链接，不用先跳一页再挑。
 *
 * 面板里的数每次开面板现捞（连同红点一起），所以站内读过了再点开不会是旧数；
 * 关面板不在这里做超时，按属性认领点击（见 onDocClick 里那一格 data-bell）。
 */
const bellOpen = ref(false);
const bellRows = ref<NotificationRow[]>([]);
const bellLoading = ref(false);

async function loadBell() {
  bellLoading.value = true;
  try {
    const [page, summary] = await Promise.all([fetchMyNotifications(1, 5), fetchUnreadSummary()]);
    bellRows.value = page.records;
    unread.value = summary.total;
  } catch {
    // 捞不动就留着上一次的列表：这一格是快捷入口，报错会把人挡在消息中心外面
  } finally {
    bellLoading.value = false;
  }
}

function toggleBell() {
  bellOpen.value = !bellOpen.value;
  if (bellOpen.value) void loadBell();
}

/** 面板里点一条：先标已读（不挡跳转，读失败也照跳），有落地页就过去 */
function openMessage(row: NotificationRow) {
  const target = messageTarget(row);
  if (row.isRead !== 1) {
    readNotification(row.id)
      .then(() => void loadUnread())
      .catch(() => undefined);
    row.isRead = 1;
  }
  bellOpen.value = false;
  if (target) window.location.href = href(target);
}

/**
 * 个人中心那一页在没登录时会给出一颗 `data-open-login` 的按钮。
 * island 之间不共享 store，也不共享组件实例，所以按属性认领这一次点击（头部那颗昼夜按钮同一做法）。
 */
function onDocClick(event: MouseEvent) {
  const target = event.target;
  if (!(target instanceof Element)) return;
  if (target.closest('[data-open-login]')) openDialog();
  // 点铃铛外面就收起来；点在里面（那颗按钮、面板里任意一条）都不算
  if (bellOpen.value && !target.closest('[data-bell]')) bellOpen.value = false;
}

onMounted(() => {
  mounted.value = true;
  document.addEventListener('click', onDocClick);
  /**
   * 页头挂了 transition:persist ⇒ 换页时这颗岛留着原实例，onMounted 不会重跑，
   * 所以人在个人中心把消息读完再点回首页，红点还是旧的。换页后补捞一次（和 Base.astro 里那几条同一手法）。
   */
  window.addEventListener('astro:after-swap', onSwap);
  // 存着的 token 可能早就被后台踢了：捞一次，捞不动就当没登录（见 store 的 restore）
  auth.restore();
});

function onSwap() {
  bellOpen.value = false;
  if (signedIn.value) void loadUnread();
}

/**
 * 登录态一翻就各动一次红点：登进来要看见有几条没读，登出要把上一个人的那个数从画面上抹掉。
 * restore() 回捞成功也会走这一条（save 里 apply 翻的是同一个 signedIn），所以开局那一路不用另发一次。
 */
watch(signedIn, (on) => {
  if (on) void loadUnread();
  else unread.value = 0;
});

onUnmounted(() => {
  document.removeEventListener('click', onDocClick);
  window.removeEventListener('astro:after-swap', onSwap);
  if (timer) clearInterval(timer);
});
</script>

<template>
  <div style="position: relative" class="flex items-center gap-3" @keydown.esc.window="onEsc">
    <!-- 没登录（含服务端那一趟）就是这颗默认人物头像，点开登录窗 -->
    <button
      v-if="!signedIn"
      type="button"
      :aria-label="d.auth.entry"
      class="rounded-full border border-gray-200 p-1.5 text-gray-600 transition-colors hover:bg-gray-100 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
      @click="openDialog()"
    >
      <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
        <circle cx="12" cy="8" r="4" />
        <path stroke-linecap="round" d="M4.5 20a7.5 7.5 0 0 1 15 0" />
      </svg>
    </button>

    <!-- 登录后头像连昵称一起就是入口：点进 /user 那一页，退出登录挪到页里了 -->
    <a
      v-else
      :href="href('/user')"
      :aria-label="fill(d.auth.signedInAria, { name: nickname })"
      class="flex items-center gap-2 rounded-full border border-gray-200 py-1 pl-1 pr-3 text-sm hover:bg-gray-100 dark:border-gray-600 dark:hover:bg-gray-700"
    >
      <span class="flex h-7 w-7 shrink-0 items-center justify-center overflow-hidden rounded-full text-gray-500 dark:text-gray-300">
        <img v-if="avatar" :src="avatar" :alt="nickname" class="h-full w-full object-cover" @error="avatarBroken = true" />
        <svg v-else class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
          <circle cx="12" cy="8" r="4" />
          <path stroke-linecap="round" d="M4.5 20a7.5 7.5 0 0 1 15 0" />
        </svg>
      </span>
      <span class="max-w-24 truncate">{{ nickname }}</span>
    </a>

    <!--
      铃铛快捷口：只在登录后画（消息全要 token，没登录点开也只能看个报错）。
      排在头像入口后面 —— DOM 顺序就是视觉顺序，不再靠 order 换位；和头像之间那点边距由根节点的 gap-3 给。
      面板挂在根节点上、按它的右边缘对齐；这一格得是 relative，角标才钉在按钮角上（同下面那条注释，写行内样式）。
    -->
    <button
      v-if="signedIn"
      type="button"
      data-bell
      :aria-label="d.site.messages"
      :aria-expanded="bellOpen"
      style="position: relative"
      class="rounded-full border border-gray-200 p-1.5 text-gray-600 transition-colors hover:bg-gray-100 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
      @click="toggleBell"
    >
      <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
        <path stroke-linecap="round" stroke-linejoin="round" d="M6 10a6 6 0 0 1 12 0c0 3.2 1.2 4.6 2 5.5H4c.8-.9 2-2.3 2-5.5Z" />
        <path stroke-linecap="round" d="M10 19a2 2 0 0 0 4 0" />
      </svg>
      <!-- 未读数从昵称后面那格搬到这里：快捷口就是看消息的，数落在这颗上才对得上它的用途 -->
      <!-- 定位那几条写行内样式而不是 -top-0.5 这类工具类：页头常驻，换页送来的那份页内 Tailwind 按新页模块图重扫，
           只被这棵岛用到的类不一定在（掉了 ⇒ top 回落 static ⇒ 角标跑到右下角，面板的 w-80 同一趟）。行内样式跟着元素走。 -->
      <span
        v-if="unread"
        style="position: absolute; top: -2px; right: -2px"
        class="rounded-full bg-red-500 px-1.5 text-xs leading-5 text-white"
        :aria-label="fill(d.auth.unreadAria, { n: unread })"
        >{{ unread > 99 ? '99+' : unread }}</span
      >
    </button>

    <!-- 这一颗也带 data-bell：点面板里任意一条都算「点在铃铛上」，不该被 onDocClick 当外面收掉；
         尺寸定位同上面那颗角标一样钉在行内样式里，换页的 CSS 动不了它 ⇒ 每页点开都是同一个宽、同一个位置。 -->
    <div
      v-if="bellOpen"
      data-bell
      style="
        position: absolute;
        top: 100%;
        right: 0;
        margin-top: 0.5rem;
        width: 20rem;
        max-width: calc(100vw - 2rem);
        z-index: 50;
      "
      class="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-lg dark:border-gray-600 dark:bg-gray-800"
    >
      <ul class="max-h-80 divide-y divide-gray-100 overflow-y-auto dark:divide-gray-700">
        <li v-if="bellLoading && !bellRows.length" class="px-4 py-6 text-center text-sm text-gray-500 dark:text-gray-400">
          {{ d.common.loading }}
        </li>
        <li v-else-if="!bellRows.length" class="px-4 py-6 text-center text-sm text-gray-500 dark:text-gray-400">
          {{ d.site.messagesEmpty }}
        </li>
        <li v-for="row in bellRows" v-else :key="row.id">
          <button
            type="button"
            class="block w-full px-4 py-3 text-left transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
            @click="openMessage(row)"
          >
            <span class="flex items-start gap-2">
              <span
                v-if="row.isRead !== 1"
                class="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-red-500"
                aria-hidden="true"
              ></span>
              <span class="min-w-0 flex-1">
                <span class="block truncate text-sm font-medium">{{ row.title || d.site.messages }}</span>
                <span class="mt-0.5 block truncate text-xs text-gray-500 dark:text-gray-400">{{ row.message }}</span>
              </span>
            </span>
          </button>
        </li>
      </ul>
      <a
        :href="href('/user?section=messages')"
        class="block border-t border-gray-200 px-4 py-2.5 text-center text-sm text-blue-600 hover:bg-gray-50 dark:border-gray-600 dark:text-blue-400 dark:hover:bg-gray-700"
        >{{ d.site.messagesAll }}</a
      >
    </div>

    <ModalOverlay v-if="showDialog" @close="closeDialog">
      <div
        role="dialog"
        aria-modal="true"
        :aria-label="d.auth.dialog"
        class="w-[380px] max-w-full rounded-xl bg-white p-6 shadow-xl dark:bg-gray-800"
      >
        <div class="mb-5 flex items-center justify-between">
          <div class="flex gap-1 rounded-lg bg-gray-100 p-1 dark:bg-gray-900">
            <button
              type="button"
              class="rounded-md px-3 py-1 text-sm"
              :class="tab === 'login' ? 'bg-white font-medium shadow dark:bg-gray-700' : 'text-gray-500 dark:text-gray-400'"
              @click="tab = 'login'"
            >
              {{ d.auth.login }}
            </button>
            <button
              type="button"
              class="rounded-md px-3 py-1 text-sm"
              :class="tab === 'register' ? 'bg-white font-medium shadow dark:bg-gray-700' : 'text-gray-500 dark:text-gray-400'"
              @click="tab = 'register'"
            >
              {{ d.auth.register }}
            </button>
          </div>
          <button
            type="button"
            :aria-label="d.common.close"
            class="rounded p-1 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
            @click="closeDialog"
          >
            <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path stroke-linecap="round" d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>

        <p v-if="notice" class="mb-4 rounded-lg bg-blue-50 px-3 py-2 text-sm text-blue-700 dark:bg-blue-900/40 dark:text-blue-300">
          {{ notice }}
        </p>

        <form v-if="tab === 'login'" class="space-y-4" @submit.prevent="submitLogin">
          <label class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.auth.username }}</span>
            <input
              v-model.trim="loginForm.username"
              name="username"
              autocomplete="username"
              class="w-full rounded-lg border border-gray-300 px-3 py-2 outline-none focus:border-blue-500 dark:border-gray-600 dark:bg-gray-900"
            />
          </label>
          <label class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.auth.password }}</span>
            <input
              v-model="loginForm.password"
              type="password"
              name="password"
              autocomplete="current-password"
              class="w-full rounded-lg border border-gray-300 px-3 py-2 outline-none focus:border-blue-500 dark:border-gray-600 dark:bg-gray-900"
            />
          </label>

          <p v-if="error" class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
            {{ error }}
          </p>

          <button
            type="submit"
            class="w-full rounded-lg bg-blue-600 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="busy === 'login' || !loginForm.username || !loginForm.password"
          >
            {{ busy === 'login' ? d.auth.loggingIn : d.auth.login }}
          </button>
        </form>

        <form v-else class="space-y-4" @submit.prevent="onRegister">
          <label class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.auth.username }}</span>
            <input
              v-model.trim="registerForm.username"
              name="reg-username"
              autocomplete="username"
              class="w-full rounded-lg border border-gray-300 px-3 py-2 outline-none focus:border-blue-500 dark:border-gray-600 dark:bg-gray-900"
            />
          </label>
          <label class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.auth.email }}</span>
            <div class="flex gap-2">
              <input
                v-model.trim="registerForm.email"
                type="email"
                name="email"
                autocomplete="email"
                class="min-w-0 flex-1 rounded-lg border border-gray-300 px-3 py-2 outline-none focus:border-blue-500 dark:border-gray-600 dark:bg-gray-900"
              />
              <button
                type="button"
                class="shrink-0 rounded-lg border border-blue-600 px-3 text-sm text-blue-600 hover:bg-blue-50 disabled:cursor-not-allowed disabled:border-gray-300 disabled:text-gray-400 dark:border-blue-400 dark:text-blue-400 dark:hover:bg-blue-900/40 dark:disabled:border-gray-600"
                :disabled="busy === 'code' || countdown > 0"
                @click="onSendCode"
              >
                {{ countdown > 0 ? fill(d.auth.countdown, { n: countdown }) : d.auth.sendCode }}
              </button>
            </div>
          </label>
          <label class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.auth.code }}</span>
            <input
              v-model.trim="registerForm.code"
              name="code"
              autocomplete="one-time-code"
              class="w-full rounded-lg border border-gray-300 px-3 py-2 outline-none focus:border-blue-500 dark:border-gray-600 dark:bg-gray-900"
            />
          </label>
          <label class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.auth.password }}</span>
            <input
              v-model="registerForm.password"
              type="password"
              name="reg-password"
              autocomplete="new-password"
              class="w-full rounded-lg border border-gray-300 px-3 py-2 outline-none focus:border-blue-500 dark:border-gray-600 dark:bg-gray-900"
            />
          </label>

          <p v-if="error" class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
            {{ error }}
          </p>

          <button
            type="submit"
            class="w-full rounded-lg bg-blue-600 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="busy === 'register' || !registerForm.email || !registerForm.code || !registerForm.username || !registerForm.password"
          >
            {{ busy === 'register' ? d.auth.submitting : d.auth.register }}
          </button>
        </form>
      </div>
    </ModalOverlay>
  </div>
</template>
