<script setup lang="ts">
/**
 * 订单支付弹窗：待支付那一单，从「我的订单」列表或详情弹窗里点开来付（结算页那颗「提交并支付」不走这一颗，
 * 它手里就有 create 报回来的实付，直接接着调回调）。
 *
 * 付这一笔调的是服务端那条支付回调（POST /api/comm/shop/order/payCallback）：主单 0 → 1、还的锁转正、
 * 券核销、积分冻结转消耗、出库单和配送单那几步都在那一段里走完。微信/支付宝的网关还没接，所以点「确认支付」
 * 现在等价于把这一单记成已支付；payType 只是落到 sys_order.pay_type 那一列上，不传服务端就不写。
 *
 * 金额送订单上报回来的那一格实付：服务端拿它和自己算的那一笔比（两位小数），对不上整笔拒 —— 这里不重算。
 * 重复点不会付两次：那边靠 WHERE status = 0 更新，第二趟翻不动就按「状态不允许」拒回来。
 */
import { ref } from 'vue';
import ModalOverlay from '@/components/ModalOverlay.vue';
import { enumChoices, useI18n } from '@/i18n';
import { payOrder } from '@/lib/order';

const props = defineProps<{ orderId: number; orderNo: string; payAmount: number | null }>();

const emit = defineEmits<{ paid: []; close: [] }>();

/** 语种由挂它的那颗岛（个人中心 / 订单详情）沿棵传下来，这一颗自己不用收 locale */
const { d } = useI18n();

/** 支付方式候选：档位就是枚举那一份（d.enum.payType），语种按页现取，所以在这儿算、不留模块顶层 */
const payOptions = enumChoices(d.enum.payType);

/**
 * 余额那一档在这一颗里点不动：余额是在**下单**那一刻占住钱的（pay_type=4 落到订单上、实付从可用余额冻进冻结余额），
 * 而这一颗面对的是一张已经落下、当初没选余额的单 —— 钱包上没冻过这一笔，服务端那句结转支付也就无从结转，
 * 放过去只会把订单记成「余额已付」而钱一分没减。那边 settle 同样会当场拒。要拿余额付得先取消这一单重新下。
 */
const PAY_TYPE_BALANCE = 4;

const payType = ref<number>(payOptions[0]?.value ?? 1);
const busy = ref(false);
const error = ref('');

async function confirm() {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    await payOrder({
      orderId: props.orderId,
      payType: payType.value,
      payAmount: props.payAmount ?? undefined,
    });
    emit('paid');
  } catch (err) {
    error.value = (err as Error).message;
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <ModalOverlay :aria-label="d.pay.dialogAria" overlay-class="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 p-4">
    <div
      class="w-[420px] max-w-full rounded-xl bg-white p-5 shadow-xl dark:bg-gray-800"
    >
      <div class="mb-3 flex items-center justify-between">
        <h2 class="text-lg font-medium">{{ d.pay.title }}</h2>
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

      <p class="text-sm font-mono text-gray-500 dark:text-gray-400">{{ props.orderNo }}</p>
      <p class="mt-2 text-2xl font-semibold text-red-600 dark:text-red-400">
        ¥{{ Number(props.payAmount ?? 0).toFixed(2) }}
      </p>

      <div class="mt-4 space-y-2">
        <label
          v-for="opt in payOptions"
          :key="opt.value"
          class="flex items-center gap-3 rounded-lg border px-3 py-2 text-sm transition-colors"
          :class="[
            opt.value === payType
              ? 'border-blue-600 bg-blue-50 text-blue-800 dark:border-blue-500 dark:bg-blue-900/40 dark:text-blue-100'
              : 'border-gray-200 hover:border-gray-400 dark:border-gray-700 dark:hover:border-gray-500',
            opt.value === PAY_TYPE_BALANCE
              ? 'cursor-not-allowed opacity-50 hover:border-gray-200 dark:hover:border-gray-700'
              : 'cursor-pointer',
          ]"
        >
          <input
            v-model="payType"
            type="radio"
            name="orderPayType"
            :value="opt.value"
            class="shrink-0"
            :disabled="opt.value === PAY_TYPE_BALANCE"
          />
          {{ opt.label }}
        </label>
      </div>

      <p class="mt-3 text-xs text-gray-500 dark:text-gray-400">
        {{ d.pay.balanceNote }}
      </p>

      <p class="mt-3 text-xs text-gray-500 dark:text-gray-400">
        {{ d.pay.gatewayNote }}
      </p>

      <p v-if="error" class="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
        {{ error }}
      </p>

      <div class="mt-4 flex justify-end gap-3">
        <button
          type="button"
          class="rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
          @click="emit('close')"
        >
          {{ d.common.cancel }}
        </button>
        <button
          type="button"
          class="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
          :disabled="busy"
          @click="confirm"
        >
          {{ busy ? d.pay.paying : d.pay.confirm }}
        </button>
      </div>
    </div>
  </ModalOverlay>
</template>
