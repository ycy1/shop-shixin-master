<script setup lang="ts">
/**
 * 园区详情上那颗「买票」开出来的弹窗：挑日期、挑时段、填张数和持票人 → POST /api/comm/park/ticket/buy。
 *
 * 三件事在这一个组件里说清：
 * - **给谁**不由这里定：请求体里没有 userId，服务端按登录态记（参数能点名别人就等于替别人买票）。
 *   没登录时父组件那颗按钮挂的是 data-open-login（点击归页头那扇登录窗认领），走不到这一层。
 * - 票面那几格（票名/类型/票价/园区）不在这里填：出票那一刻由服务端从票种行上定格成快照。
 * - 只报钱不扣钱：这里给的「合计 / 应付」都是算出来的数，支付那条口还没接上（口径同抽盒），
 *   所以下面那句说明要留着，别让人以为买完就扣了款。
 *
 * 日期和张数这两格前端先拦一道（不早于今天、不超每单限购），服务端那一条也会判 ——
 * 这里拦是为了别让人填完一整屏才被告知「游玩日期不能早于今天」。
 */
import { computed, reactive, ref } from 'vue';
import ModalOverlay from '@/components/ModalOverlay.vue';
import { useI18n } from '@/i18n';
import { dictLabel } from '@/lib/dict';
import { buyParkTicket, parseSlotCodes, type BuyResult, type ParkTicket } from '@/lib/trendy';

const props = defineProps<{ ticket: ParkTicket }>();

const emit = defineEmits<{ close: [] }>();

const { d, fill, href } = useI18n();
const t = d.trendy.buy;

/** 库里那串（"1,2"）拆出来的可选时段；只卖「全天」那一档时这一串就是 [0] */
const slotCodes = computed(() => {
  const codes = parseSlotCodes(props.ticket.timeSlots);
  return codes.length ? codes : [0];
});

/** yyyy-MM-dd（和本地时区一致，别让 UTC 把今天算成昨天） */
function today(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

const form = reactive({
  visitDate: today(),
  timeSlot: slotCodes.value[0] ?? 0,
  quantity: 1,
  holderName: '',
  holderPhone: '',
});

const submitting = ref(false);
const fail = ref('');
const done = ref<BuyResult | null>(null);

const maxQty = computed(() => props.ticket.maxPerOrder ?? null);
/** 后端给回的价格是字符串（decimal），这里只做展示用的取数 */
const unitPrice = computed(() => Number(props.ticket.price ?? 0));
const totalText = computed(() => (unitPrice.value * form.quantity).toFixed(2));

/** 日期那格不能选昨天：前端先把提交置灰，服务端那一条同样拦（两处各判一次，谁先到都不亏） */
const dateOk = computed(() => !!form.visitDate && form.visitDate >= today());
const canSubmit = computed(() => !submitting.value && dateOk.value && form.quantity >= 1);

async function submit() {
  if (!canSubmit.value) return;
  submitting.value = true;
  fail.value = '';
  try {
    const name = form.holderName.trim();
    const phone = form.holderPhone.trim();
    done.value = await buyParkTicket({
      ticketId: props.ticket.id,
      visitDate: form.visitDate,
      timeSlot: form.timeSlot,
      quantity: form.quantity,
      // 空着就整个不给这一格（exactOptionalPropertyTypes 下显式 undefined 不算「没填」）
      ...(name ? { holderName: name } : {}),
      ...(phone ? { holderPhone: phone } : {}),
    });
  } catch (err) {
    // 后端那句业务提示（「园区已停业」「每单限购 8 张」）比状态码准，原样印出来
    fail.value = err instanceof Error ? err.message : String(err);
  } finally {
    submitting.value = false;
  }
}

/** 买完还想再来一单：把这一份表单清空回到第一屏（票种不换，所以那几格不用重挑） */
function again() {
  done.value = null;
  fail.value = '';
  form.quantity = 1;
  form.holderName = '';
  form.holderPhone = '';
}
</script>

<template>
  <ModalOverlay @close="emit('close')">
    <div
      role="dialog"
      aria-modal="true"
      :aria-label="fill(t.title, { name: ticket.ticketName })"
      class="max-h-[86vh] w-full max-w-lg overflow-y-auto rounded-2xl border border-gray-200 bg-white p-5 shadow-xl dark:border-gray-700 dark:bg-gray-800"
    >
      <!-- 一、下单那一屏 -->
      <template v-if="!done">
        <h3 class="text-base font-semibold">{{ fill(t.title, { name: ticket.ticketName }) }}</h3>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          <span v-if="dictLabel('ticket_type', ticket.ticketType)" class="mr-2">
            {{ dictLabel('ticket_type', ticket.ticketType) }}
          </span>
          ¥{{ unitPrice.toFixed(2) }}
        </p>

        <div class="mt-4 flex flex-col gap-4">
          <label class="flex flex-col gap-1 text-sm">
            <span class="text-gray-600 dark:text-gray-300">{{ t.date }}</span>
            <input
              v-model="form.visitDate"
              type="date"
              :min="today()"
              class="rounded-lg border border-gray-300 px-3 py-1.5 text-sm outline-none focus:border-blue-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            />
            <span v-if="!dateOk" class="text-xs text-red-600 dark:text-red-400">{{ t.dateBad }}</span>
          </label>

          <div class="flex flex-col gap-1 text-sm">
            <span class="text-gray-600 dark:text-gray-300">{{ t.slot }}</span>
            <!-- 只卖一档「全天」时这一排就一颗，且默认选中，不用挑 -->
            <div class="flex flex-wrap gap-2">
              <button
                v-for="code in slotCodes"
                :key="code"
                type="button"
                class="rounded-lg border px-3 py-1.5 text-sm transition-colors"
                :class="
                  form.timeSlot === code
                    ? 'border-blue-600 bg-blue-50 text-blue-700 dark:border-blue-400 dark:bg-blue-900/40 dark:text-blue-300'
                    : 'border-gray-300 text-gray-600 hover:bg-gray-100 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700'
                "
                @click="form.timeSlot = code"
              >
                {{ dictLabel('ticket_time_slot', code) || String(code) }}
              </button>
            </div>
          </div>

          <label class="flex flex-col gap-1 text-sm">
            <span class="text-gray-600 dark:text-gray-300">
              {{ t.qty }}
              <span v-if="maxQty" class="ml-1 text-xs text-gray-400">{{ fill(d.trendy.parkCard.maxPerOrder, { n: maxQty }) }}</span>
            </span>
            <input
              v-model.number="form.quantity"
              type="number"
              min="1"
              :max="maxQty ?? undefined"
              class="w-24 rounded-lg border border-gray-300 px-3 py-1.5 text-sm outline-none focus:border-blue-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>

          <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <label class="flex flex-col gap-1 text-sm">
              <span class="text-gray-600 dark:text-gray-300">{{ t.holder }}</span>
              <input
                v-model="form.holderName"
                type="text"
                maxlength="50"
                class="rounded-lg border border-gray-300 px-3 py-1.5 text-sm outline-none focus:border-blue-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
              />
            </label>
            <label class="flex flex-col gap-1 text-sm">
              <span class="text-gray-600 dark:text-gray-300">{{ t.phone }}</span>
              <input
                v-model="form.holderPhone"
                type="tel"
                maxlength="20"
                class="rounded-lg border border-gray-300 px-3 py-1.5 text-sm outline-none focus:border-blue-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
              />
            </label>
          </div>
          <p class="text-xs text-gray-400">{{ t.holderTip }}</p>
        </div>

        <p v-if="fail" class="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
          {{ fail }}
        </p>

        <div class="mt-5 flex items-center justify-between gap-3">
          <span class="text-lg font-semibold text-red-600 dark:text-red-400">{{ fill(t.total, { c: totalText }) }}</span>
          <div class="flex gap-2">
            <button
              type="button"
              class="rounded-lg border border-gray-200 px-4 py-1.5 text-sm hover:bg-gray-100 dark:border-gray-600 dark:hover:bg-gray-700"
              @click="emit('close')"
            >
              {{ d.common.cancel }}
            </button>
            <button
              type="button"
              class="rounded-lg bg-blue-600 px-4 py-1.5 text-sm text-white hover:bg-blue-700 disabled:opacity-60 dark:bg-blue-500"
              :disabled="!canSubmit"
              @click="submit"
            >
              {{ submitting ? t.submitting : t.submit }}
            </button>
          </div>
        </div>
      </template>

      <!-- 二、买成那一屏：几张票、应付多少、每张的票码和票面图 -->
      <template v-else>
        <h3 class="text-base font-semibold">{{ fill(t.doneTitle, { n: done.count }) }}</h3>
        <p class="mt-1 text-sm">
          <span class="text-gray-600 dark:text-gray-300">{{ fill(t.amount, { c: Number(done.amount ?? 0).toFixed(2) }) }}</span>
          <span class="ml-2 text-xs text-gray-400">{{ t.noPayTip }}</span>
        </p>
        <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
          {{ done.visitDate }} · {{ dictLabel('ticket_time_slot', done.timeSlot ?? 0) }}
        </p>

        <ul class="mt-4 flex flex-col gap-3">
          <li
            v-for="one in done.tickets ?? []"
            :key="one.id"
            class="flex items-center gap-3 rounded-xl border border-gray-200 p-3 dark:border-gray-700"
          >
            <!-- 票面图不 lazy：这一张是拿去闸机扫的，弹窗都开在眼前了还排在延迟加载后面就没意义 -->
            <img
              v-if="one.qrImg"
              :src="one.qrImg"
              :alt="one.ticketCode"
              class="h-16 w-16 shrink-0 rounded-lg object-contain"
            />
            <span
              v-else
              class="flex h-16 w-16 shrink-0 items-center justify-center rounded-lg bg-gray-100 px-1 text-center text-[10px] text-gray-400 dark:bg-gray-700"
            >
              {{ t.qrEmpty }}
            </span>
            <span class="flex min-w-0 flex-col">
              <span class="truncate text-sm font-medium">{{ one.ticketName }}</span>
              <span class="text-xs text-gray-500 dark:text-gray-400">{{ t.code }}：{{ one.ticketCode }}</span>
              <span class="text-xs text-gray-500 dark:text-gray-400">¥{{ Number(one.price ?? 0).toFixed(2) }}</span>
            </span>
          </li>
        </ul>

        <div class="mt-5 flex items-center justify-between gap-3">
          <a
            class="text-sm text-blue-600 hover:underline dark:text-blue-400"
            :href="href('/user?section=orders')"
          >
            {{ t.goMine }} ›
          </a>
          <div class="flex gap-2">
            <button
              type="button"
              class="rounded-lg border border-gray-200 px-4 py-1.5 text-sm hover:bg-gray-100 dark:border-gray-600 dark:hover:bg-gray-700"
              @click="again"
            >
              {{ t.again }}
            </button>
            <button
              type="button"
              class="rounded-lg bg-blue-600 px-4 py-1.5 text-sm text-white hover:bg-blue-700 dark:bg-blue-500"
              @click="emit('close')"
            >
              {{ d.common.close }}
            </button>
          </div>
        </div>
      </template>
    </div>
  </ModalOverlay>
</template>
