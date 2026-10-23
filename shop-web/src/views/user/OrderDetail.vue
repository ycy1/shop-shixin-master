<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { enumLabel, useI18n } from '@/i18n';
import ModalOverlay from '@/components/ModalOverlay.vue';
import OrderPayDialog from '@/components/OrderPayDialog.vue';
import { fetchMyOrderDetail, type OrderDetail as OrderDetailRow } from '@/lib/profile';

const props = defineProps<{ id: number }>();
/** paid 只在待支付那一单真付成了才抛：底下那一列订单是别人捞的，状态跟着得重拉 */
const emit = defineEmits<{ close: []; paid: [] }>();

const { d, fill, href } = useI18n();

const order = ref<OrderDetailRow | null>(null);
const loading = ref(false);
const error = ref('');
const payOpen = ref(false);

/** 后端把「不是自己名下」也收成同一句「订单不存在」，所以这一句直接当提示用，不再分档 */
async function load() {
  loading.value = true;
  error.value = '';
  try {
    order.value = await fetchMyOrderDetail(props.id);
  } catch (err) {
    error.value = (err as Error).message;
  } finally {
    loading.value = false;
  }
}

function dayOf(value: string | null) {
  return value ? value.slice(0, 10) : d.common.dash;
}

function timeOf(value: string | null) {
  return value || d.common.dash;
}

function moneyOf(value: number | null) {
  return value == null ? d.common.dash : `¥${value.toFixed(2)}`;
}

/** 只有待支付那一单给付（0 是唯一还能付的状态；3/4/5 那边当场会拒） */
const payable = computed(() => order.value?.status === 0);

/** 付完了重拉这一份：状态、支付方式、支付时间三格都是服务端在那一趟里写的 */
async function onPaid() {
  payOpen.value = false;
  await load();
  emit('paid');
}

onMounted(load);
</script>

<template>
  <ModalOverlay @close="emit('close')">
    <div
      role="dialog"
      aria-modal="true"
      :aria-label="d.order.title"
      class="max-h-full w-[720px] max-w-full overflow-y-auto rounded-xl bg-white p-6 shadow-xl dark:bg-gray-800"
    >
      <div class="mb-4 flex items-center justify-between">
        <h2 class="text-lg font-medium">{{ d.order.title }}</h2>
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

      <div v-if="loading" class="py-10 text-center text-sm text-gray-500 dark:text-gray-400">{{ d.common.loading }}</div>
      <p v-else-if="error" class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
        {{ error }}
      </p>

      <template v-else-if="order">
        <dl class="grid grid-cols-1 gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
          <div class="flex gap-2">
            <dt class="w-16 shrink-0 text-gray-500 dark:text-gray-400">{{ d.order.orderNo }}</dt>
            <dd class="min-w-0 break-all font-mono">{{ order.orderNo }}</dd>
          </div>
          <div class="flex gap-2">
            <dt class="w-16 shrink-0 text-gray-500 dark:text-gray-400">{{ d.order.status }}</dt>
            <dd>{{ enumLabel(d.enum.orderStatus, order.status, fill(d.enum.unknown, { n: order.status })) }}</dd>
          </div>
          <div class="flex gap-2">
            <dt class="w-16 shrink-0 text-gray-500 dark:text-gray-400">{{ d.order.orderType }}</dt>
            <dd>
              {{
                order.orderType == null
                  ? d.common.dash
                  : enumLabel(d.enum.orderType, order.orderType, fill(d.enum.unknown, { n: order.orderType }))
              }}
            </dd>
          </div>
          <div class="flex gap-2">
            <dt class="w-16 shrink-0 text-gray-500 dark:text-gray-400">{{ d.order.created }}</dt>
            <dd>{{ timeOf(order.createTime) }}</dd>
          </div>
          <div class="flex gap-2">
            <dt class="w-16 shrink-0 text-gray-500 dark:text-gray-400">{{ d.order.itemsColumn }}</dt>
            <dd>{{ fill(d.order.itemsSummary, { lines: order.itemCount ?? 0, qty: order.totalQuantity ?? 0 }) }}</dd>
          </div>
          <div class="flex gap-2">
            <dt class="w-16 shrink-0 text-gray-500 dark:text-gray-400">{{ d.order.store }}</dt>
            <dd class="min-w-0 truncate">{{ order.storeNames || d.common.dash }}</dd>
          </div>
          <div class="flex gap-2">
            <dt class="w-16 shrink-0 text-gray-500 dark:text-gray-400">{{ d.order.totalAmount }}</dt>
            <dd>{{ moneyOf(order.totalAmount) }}</dd>
          </div>
          <div class="flex gap-2">
            <dt class="w-16 shrink-0 text-gray-500 dark:text-gray-400">{{ d.order.deliveryFee }}</dt>
            <dd>{{ moneyOf(order.deliveryFee) }}</dd>
          </div>
          <div class="flex gap-2">
            <dt class="w-16 shrink-0 text-gray-500 dark:text-gray-400">{{ d.order.discountAmount }}</dt>
            <dd>{{ moneyOf(order.discountAmount) }}</dd>
          </div>
          <div class="flex gap-2">
            <dt class="w-16 shrink-0 text-gray-500 dark:text-gray-400">{{ d.order.payAmount }}</dt>
            <dd class="font-medium text-red-500 dark:text-red-400">{{ moneyOf(order.payAmount) }}</dd>
          </div>
          <div class="flex gap-2">
            <dt class="w-16 shrink-0 text-gray-500 dark:text-gray-400">{{ d.order.payType }}</dt>
            <dd>
              {{
                order.payType == null
                  ? d.enum.unPaid
                  : enumLabel(d.enum.payType, order.payType, fill(d.enum.unknown, { n: order.payType }))
              }}
            </dd>
          </div>
          <div class="flex gap-2">
            <dt class="w-16 shrink-0 text-gray-500 dark:text-gray-400">{{ d.order.payTime }}</dt>
            <dd>{{ timeOf(order.payTime) }}</dd>
          </div>
          <div v-if="order.deliveryNo" class="flex gap-2">
            <dt class="w-16 shrink-0 text-gray-500 dark:text-gray-400">{{ d.order.deliveryNo }}</dt>
            <dd class="min-w-0 break-all font-mono">{{ order.deliveryNo }}</dd>
          </div>
          <div class="flex gap-2 sm:col-span-2">
            <dt class="w-16 shrink-0 text-gray-500 dark:text-gray-400">{{ d.order.remark }}</dt>
            <dd class="min-w-0">{{ order.remark || d.common.dash }}</dd>
          </div>
        </dl>

        <h3 class="mb-2 mt-6 font-medium">{{ d.order.itemsTitle }}</h3>
        <ul class="divide-y divide-gray-100 rounded-lg border border-gray-200 dark:divide-gray-700 dark:border-gray-700">
          <li v-for="item in order.items || []" :key="item.id" class="flex items-start gap-3 p-3">
            <span class="h-12 w-12 shrink-0 overflow-hidden rounded bg-gray-100 dark:bg-gray-700">
              <img v-if="item.image" :src="item.image" :alt="item.productName || ''" class="h-full w-full object-cover" />
            </span>
            <span class="min-w-0 flex-1 text-sm">
              <a
                v-if="item.productId"
                :href="href(`/product/${item.productId}`)"
                class="block truncate text-blue-600 hover:underline dark:text-blue-400"
                @click.stop
              >{{ item.productName || d.order.productGone }}</a>
              <span v-else class="block truncate">{{ item.productName || d.order.productGone }}</span>
              <span class="block truncate text-xs text-gray-500 dark:text-gray-400">
                {{ [item.productCode, item.specText, item.storeName].filter(Boolean).join(' · ') || d.common.dash }}
              </span>
              <span class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-gray-500 dark:text-gray-400">
                <span>{{ moneyOf(item.price) }} × {{ item.quantity ?? 0 }}</span>
                <span>
                  {{
                    item.shipStatus == null
                      ? d.common.dash
                      : enumLabel(d.enum.shipStatus, item.shipStatus, fill(d.enum.unknown, { n: item.shipStatus }))
                  }}
                </span>
                <span v-if="item.returnedQuantity">{{ fill(d.order.returnedItems, { n: item.returnedQuantity ?? 0 }) }}</span>
              </span>
            </span>
            <span class="shrink-0 text-sm font-medium text-red-500 dark:text-red-400">{{ moneyOf(item.totalAmount) }}</span>
          </li>
          <li v-if="!order.items?.length" class="p-3 text-sm text-gray-500 dark:text-gray-400">{{ d.order.noItems }}</li>
        </ul>

        <p class="mt-3 text-xs text-gray-500 dark:text-gray-400">
          {{ fill(d.order.footnote, { day: dayOf(order.createTime) }) }}
        </p>
      </template>

      <div class="mt-5 flex justify-end gap-3">
        <button
          v-if="payable"
          type="button"
          class="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
          @click="payOpen = true"
        >
          {{ d.order.payNow }}
        </button>
        <button
          type="button"
          class="rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
          @click="emit('close')"
        >
          {{ d.common.close }}
        </button>
      </div>
    </div>

    <OrderPayDialog
      v-if="payOpen && order"
      :order-id="order.id"
      :order-no="order.orderNo"
      :pay-amount="order.payAmount"
      @paid="onPaid"
      @close="payOpen = false"
    />
  </ModalOverlay>
</template>
