import { computed, onScopeDispose, ref } from 'vue';
import { defineStore } from 'pinia';
import { fetchLoginUser, login, logout, register, sendEmailCode, type LoginForm, type LoginUser, type RegisterForm } from '@/lib/auth';
import { setAuthToken } from '@/lib/api';

/**
 * 一份 blob 存两格：token 用来带 Authorization 头，user 用来回显头像/昵称。
 * 分开存会出现「有头像没 token」这种半截登录态，所以同进同出。
 */
const STORAGE_KEY = 'shop-auth';
/** 登录态一变就在 window 上抛这一颗事件，让别的岛各自那份 store 回读 localStorage（见下面 save 那段） */
const AUTH_EVENT = 'shop-auth-changed';
const inBrowser = typeof window !== 'undefined';

interface StoredAuth {
  token: string;
  user: LoginUser;
}

/** 手改过 localStorage、存的不是这一形状，一律当没登录 */
function readStored(): StoredAuth | null {
  if (!inBrowser) return null;
  try {
    const raw: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null');
    if (!raw || typeof raw !== 'object') return null;
    const { token, user } = raw as Partial<StoredAuth>;
    if (typeof token !== 'string' || !token || !user || typeof user !== 'object') return null;
    return { token, user };
  } catch {
    return null;
  }
}

export const useAuthStore = defineStore('auth', () => {
  const stored = readStored();
  const user = ref<LoginUser | null>(stored?.user ?? null);
  const token = ref<string | null>(stored?.token ?? null);
  // 建 store 就交给 api 层一次：请求拦截器只认它自己那一格模块变量，不回头读 store
  setAuthToken(token.value);

  const isLoggedIn = computed(() => !!token.value);

  /** 只把这一份 blob 装进这一份 store（连带把 token 交给 api 层那一格），不写 localStorage、不抛事件 */
  function apply(next: StoredAuth | null) {
    user.value = next?.user ?? null;
    token.value = next?.token ?? null;
    setAuthToken(token.value);
  }

  function save(next: StoredAuth | null) {
    apply(next);
    if (!inBrowser) return;
    try {
      if (next) localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      else localStorage.removeItem(STORAGE_KEY);
    } catch {
      // 存不进去（隐私模式）就退化成「只在这一页有效」的登录态，不影响功能
    }
    // 页头那颗登录窗是一岛，商品详情、购物车是另外几岛 —— astro 每个岛一个 Vue 应用，store 因此各一份。
    // 这一颗事件抛出去，别的岛自己回读一次 localStorage。不做这步的话「在详情页里直接登进来」那一路，
    // 详情页那份 store 还停在没登录，加购和收藏要点第二次（或刷新一次）才认。
    window.dispatchEvent(new Event(AUTH_EVENT));
  }

  if (inBrowser) {
    const onAuthChanged = () => apply(readStored());
    window.addEventListener(AUTH_EVENT, onAuthChanged);
    // 绑在 document/window 上的监听不会随岛销毁：ClientRouter 每导航一次就多留一个握死 store 的监听，
    // 所以挂到 pinia 这份 store 自己的 scope 上收掉
    onScopeDispose(() => window.removeEventListener(AUTH_EVENT, onAuthChanged));
  }

  async function signIn(form: LoginForm) {
    const info = await login(form);
    const { token: issued, ...rest } = info;
    if (!issued) throw new Error('后端没回 token，登录没成');
    save({ token: issued, user: rest });
  }

  async function signUp(form: RegisterForm) {
    return register(form);
  }

  function emailCode(email: string) {
    return sendEmailCode(email);
  }

  /**
   * 带着存好的 token 回捞一次用户信息。
   *
   * 捞不动就当没登录：token 有效期一周，中间被后台踢下线、改过密码、账号被禁用都会捞不到，
   * 这时候留着旧头像只会让人以为还在登录态里。
   */
  async function restore() {
    if (!token.value) return;
    try {
      const info = await fetchLoginUser();
      save({ token: token.value, user: info });
    } catch {
      save(null);
    }
  }

  /** 登出失败也要清本地：token 都留不住的话，留着它只会让后面每颗请求都红一次 */
  async function signOut() {
    try {
      await logout();
    } finally {
      save(null);
    }
  }

  return { user, token, isLoggedIn, signIn, signUp, emailCode, restore, signOut };
});
