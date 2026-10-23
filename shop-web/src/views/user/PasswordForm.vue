<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from '@/i18n';
import ModalOverlay from '@/components/ModalOverlay.vue';
import { changeMyPassword } from '@/lib/profile';

/**
 * 修改密码那一格弹窗：基础信息那一块上那颗「修改密码」点开的就是它。
 *
 * 三格两数是这一颗的形状：旧密码必须由服务端比对（对不上回「旧密码错误」），
 * 确认那一格只是当场拦一下打错的，服务端那一路根本没有这一格。
 * 改的是 token 背后那一个账号 —— 请求体里没有 userId 可点名，改不到别人。
 *
 * 改完不用重新登录：那一条只换库里的密码串，这一次会话的 token 还是同一个，
 * 所以要重新登的是「下一次」，页面上提醒一句就够。
 */

const emit = defineEmits<{ saved: []; close: [] }>();

const { d, fill } = useI18n();

const form = ref({ oldPassword: '', newPassword: '', confirmPassword: '' });
const submitting = ref(false);
const error = ref('');

/** 新密码最短六位：服务层只收「非空」，这一句在前端先说，省得用户设出一个一字的密码 */
const tooShort = computed(() => !!form.value.newPassword && form.value.newPassword.length < 6);
const notSame = computed(
  () => !!form.value.confirmPassword && form.value.newPassword !== form.value.confirmPassword,
);

const canSubmit = computed(
  () =>
    !submitting.value &&
    !!form.value.oldPassword &&
    !!form.value.newPassword &&
    !tooShort.value &&
    !notSame.value &&
    form.value.newPassword === form.value.confirmPassword,
);

async function submit() {
  if (!canSubmit.value) return;
  submitting.value = true;
  error.value = '';
  try {
    await changeMyPassword(form.value.oldPassword, form.value.newPassword);
    emit('saved');
  } catch (err) {
    error.value = (err as Error).message;
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <ModalOverlay @close="emit('close')">
    <div
      role="dialog"
      aria-modal="true"
      :aria-label="d.password.title"
      class="max-h-full w-[440px] max-w-full overflow-y-auto rounded-xl bg-white p-6 shadow-xl dark:bg-gray-800"
    >
      <div class="mb-4 flex items-center justify-between">
        <h2 class="text-lg font-medium">{{ d.password.title }}</h2>
        <button
          type="button"
          :aria-label="d.common.close"
          class="rounded p-1 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
          @click="emit('close')"
        >
          <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path stroke-linecap="round" d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      </div>

      <p v-if="error" class="mb-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
        {{ error }}
      </p>

      <form class="space-y-4" @submit.prevent="submit">
        <label class="block text-sm">
          <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.password.oldPassword }} *</span>
          <input
            v-model="form.oldPassword"
            type="password"
            autocomplete="current-password"
            class="w-full rounded-lg border border-gray-300 px-3 py-2 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
          />
        </label>
        <label class="block text-sm">
          <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ fill(d.password.newPassword, { n: 6 }) }}</span>
          <input
            v-model="form.newPassword"
            type="password"
            autocomplete="new-password"
            class="w-full rounded-lg border border-gray-300 px-3 py-2 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
          />
          <span v-if="tooShort" class="mt-1 block text-xs text-red-600 dark:text-red-400">
            {{ fill(d.password.tooShort, { n: 6 }) }}
          </span>
        </label>
        <label class="block text-sm">
          <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.password.confirmPassword }} *</span>
          <input
            v-model="form.confirmPassword"
            type="password"
            autocomplete="new-password"
            class="w-full rounded-lg border border-gray-300 px-3 py-2 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
          />
          <span v-if="notSame" class="mt-1 block text-xs text-red-600 dark:text-red-400">{{ d.password.notSame }}</span>
        </label>

        <p class="text-xs text-gray-500 dark:text-gray-400">
          {{ d.password.tip }}
        </p>

        <div class="flex justify-end gap-2 pt-1">
          <button
            type="button"
            class="rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
            @click="emit('close')"
          >
            {{ d.common.cancel }}
          </button>
          <button
            type="submit"
            class="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="!canSubmit"
          >
            {{ submitting ? d.password.submitting : d.common.confirm }}
          </button>
        </div>
      </form>
    </div>
  </ModalOverlay>
</template>
