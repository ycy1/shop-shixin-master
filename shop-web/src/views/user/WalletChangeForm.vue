<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from '@/i18n';
import ModalOverlay from '@/components/ModalOverlay.vue';
import {
  rechargeWallet,
  withdrawWallet,
  type WalletAccount,
  type WalletChangeForm as WalletChangePayload,
} from '@/lib/profile';

/**
 * 钱包那一颗弹窗：充值和提现共用这一份（形状照同目录的 AddressForm，只是表单只有两格）
 *
 * 只收两个数：这一笔多少钱、为什么（原样写进流水的说明）。账户上那几个数一概不在表单里 ——
 * 这一本是账，余额只能由充值进、提现出、下单占、退款还这些动作自己加加减减，
 * 页面能直接填余额就等于允许"账上多了 500 块、流水里什么都没有"（同后台商品库存那一页的道理）。
 * 也没有「给谁充」这一格：comm 那两条把 userId 钉成登录 id，动的永远是自己这本账。
 *
 * 提现那一头多一道当场判据（比可用余额多就不给点）：服务端那条 SQL 压着 balance >= 这一笔，
 * 撞上去只会整趟回滚，能在页面上先说清就别等人提交完才报错。
 * 冻结中那一部分（下单没付的单子占着的）本来就不在可用余额里，所以这里读的就是 balance 那一格。
 */

const props = defineProps<{ action: 'recharge' | 'withdraw'; account?: WalletAccount | null }>();

const emit = defineEmits<{ saved: []; close: [] }>();

const { d, fill } = useI18n();

const isRecharge = computed(() => props.action === 'recharge');
const title = computed(() => (isRecharge.value ? d.wallet.rechargeTitle : d.wallet.withdrawTitle));

/** 钱数在这一页各处都是这么说的（两个小数，空着说「—」） */
function moneyOf(value: number | null | undefined) {
  return value == null ? d.common.dash : `¥${value.toFixed(2)}`;
}

/** 这一格 v-model 绑在 type=number 上，Vue 会把填进去的转成 number 写回来，所以两种都收 */
const amount = ref<string | number>('');
const description = ref('');
const submitting = ref(false);
const error = ref('');

/** 账上的可用余额（还没开账就没有这一行，那一档按 0 元说） */
const usable = computed(() => props.account?.balance ?? 0);

const tip = computed(() => (isRecharge.value ? d.wallet.rechargeTip : d.wallet.withdrawTip));

/** 填进去的数（空着、或者压根不是个数都是 null） */
const picked = computed(() => {
  const text = String(amount.value).trim();
  if (!text) return null;
  const value = Number(text);
  return Number.isFinite(value) ? value : null;
});

/** 最多两位小数：库里那一列是 decimal(14,2)，服务端这一条是拒的（不当场四舍五入），所以这里先拦住 */
const tooPrecise = computed(() => {
  const value = picked.value;
  if (value == null) return false;
  return Math.round(value * 100) / 100 !== value;
});

/** 提现那一头的超提：填的比可用余额多，服务端那条 SQL 一定改不动 */
const tooMuch = computed(() => !isRecharge.value && picked.value != null && picked.value > usable.value);

const canSubmit = computed(
  () => !submitting.value && picked.value != null && picked.value > 0 && !tooPrecise.value && !tooMuch.value,
);

async function submit() {
  if (!canSubmit.value) return;
  submitting.value = true;
  error.value = '';
  try {
    const payload: WalletChangePayload = { amount: picked.value as number, description: description.value.trim() };
    await (isRecharge.value ? rechargeWallet(payload) : withdrawWallet(payload));
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
      :aria-label="title"
      class="w-[460px] max-w-full overflow-y-auto rounded-xl bg-white p-6 shadow-xl dark:bg-gray-800"
    >
      <div class="mb-4 flex items-center justify-between">
        <h2 class="text-lg font-medium">{{ title }}</h2>
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
          <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.wallet.amount }} *</span>
          <input
            v-model="amount"
            type="number"
            min="0.01"
            step="0.01"
            :placeholder="d.wallet.amountPlaceholder"
            class="w-full rounded-lg border border-gray-300 px-3 py-2 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
          />
        </label>

        <p v-if="tooPrecise" class="text-xs text-red-500 dark:text-red-400">{{ d.wallet.tooPrecise }}</p>
        <p v-else-if="tooMuch" class="text-xs text-red-500 dark:text-red-400">
          {{ fill(d.wallet.tooMuch, { usable: moneyOf(usable), picked: moneyOf(picked) }) }}
        </p>
        <p v-else class="text-xs text-gray-500 dark:text-gray-400">
          {{ isRecharge ? d.wallet.rechargeHint : fill(d.wallet.withdrawHint, { usable: moneyOf(usable) }) }}
        </p>

        <label class="block text-sm">
          <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.wallet.description }}</span>
          <input
            v-model="description"
            type="text"
            :placeholder="isRecharge ? d.wallet.rechargeDescriptionPlaceholder : d.wallet.withdrawDescriptionPlaceholder"
            class="w-full rounded-lg border border-gray-300 px-3 py-2 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
          />
        </label>

        <p class="text-xs text-gray-500 dark:text-gray-400">{{ tip }}</p>

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
            {{ submitting ? d.wallet.submitting : isRecharge ? d.wallet.confirmRecharge : d.wallet.confirmWithdraw }}
          </button>
        </div>
      </form>
    </div>
  </ModalOverlay>
</template>
