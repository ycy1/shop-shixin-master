<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { enumLabel, useI18n } from '@/i18n';
import { dictLabel, dictReady, loadDict } from '@/lib/dict';
import ModalOverlay from '@/components/ModalOverlay.vue';
import OrderPayDialog from '@/components/OrderPayDialog.vue';
import { fetchMyOrderDetail, type OrderDetail as OrderDetailRow, type OrderItemRow } from '@/lib/profile';

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

/**
 * 明细按「一盒一张卡」收拢：一整盒在库里是盒内每款一行（锁库存、出库、退货都只认识具体规格），
 * 但人不该数出六行才知道自己端了一盒 —— 同一台盒（source_id 相同）的那几行画成一组，
 * 卡头念盒名和这几行的合计，卡里逐款列。普通商品行照旧一行一张卡。
 */
interface LineGroup {
  key: string;
  box: boolean;
  boxName: string | null;
  items: OrderItemRow[];
}

const lineGroups = computed<LineGroup[]>(() => {
  const out: LineGroup[] = [];
  const boxIndex = new Map<string, LineGroup>();
  for (const item of order.value?.items ?? []) {
    if (item.sourceType === 1 && item.sourceId != null) {
      const key = `box-${item.sourceId}`;
      const hit = boxIndex.get(key);
      if (hit) {
        hit.items.push(item);
        // 盒后来被删了就联不出名字，那一格在几行里都是 null；只要有一行认得出就用它
        if (!hit.boxName && item.sourceName) {
          hit.boxName = item.sourceName;
        }
        continue;
      }
      const group: LineGroup = { key, box: true, boxName: item.sourceName ?? null, items: [item] };
      boxIndex.set(key, group);
      out.push(group);
      continue;
    }
    out.push({ key: `line-${item.id}`, box: false, boxName: null, items: [item] });
  }
  return out;
});

function groupQuantity(group: LineGroup) {
  return group.items.reduce((sum, one) => sum + Number(one.quantity ?? 0), 0);
}

function groupTotal(group: LineGroup) {
  return group.items.reduce((sum, one) => sum + Number(one.totalAmount ?? 0), 0);
}

/** 盒那张卡默认收起：它不是一个商品，是一叠商品，摊开会把整张订单列表冲长。点卡头才下拉展开 */
const openedBoxes = ref<Set<string>>(new Set());

function toggleBoxOpen(key: string) {
  const next = new Set(openedBoxes.value);
  if (next.has(key)) {
    next.delete(key);
  } else {
    next.add(key);
  }
  openedBoxes.value = next;
}

/**
 * 订单类型读字典，不读 i18n 里那份写死的数组（那一档的定义在后台改过，写死就会跟库里的值对不上）。
 * 字典还没回来先给占位，别拿「未知」闪一下。
 */
function orderTypeOf(value: number | null) {
  if (value == null || !dictReady('order_type')) return d.common.dash;
  return dictLabel('order_type', value) ?? fill(d.enum.unknown, { n: value });
}

/** 只有待支付那一单给付（0 是唯一还能付的状态；3/4/5 那边当场会拒） */
const payable = computed(() => order.value?.status === 0);

/** 付完了重拉这一份：状态、支付方式、支付时间三格都是服务端在那一趟里写的 */
async function onPaid() {
  payOpen.value = false;
  await load();
  emit('paid');
}

onMounted(() => {
  load();
  // 订单类型那一格的标签来自这本字典，和订单本身一起要（两个请求各走各的，谁先到都行）
  void loadDict(['order_type']);
});
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
            <dd>{{ orderTypeOf(order.orderType) }}</dd>
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
          <template v-for="group in lineGroups" :key="group.key">
            <!-- 一整盒那一行和普通商品行同一个等级：左边有图（拿盒内第一款那张）、中间是盒名和款/件数、
                 右边是这几行的合计；默认收起，点这一行才下拉展开盒内每款 -->
            <li v-if="group.box" class="flex items-start gap-3 p-3">
              <span class="h-12 w-12 shrink-0 overflow-hidden rounded bg-gray-100 dark:bg-gray-700">
                <img
                  v-if="group.items[0]?.image"
                  :src="group.items[0].image"
                  :alt="group.boxName || d.cart.wholeBox"
                  class="h-full w-full object-cover"
                />
              </span>
              <button
                type="button"
                class="min-w-0 flex-1 text-left"
                @click="toggleBoxOpen(group.key)"
              >
                <span class="flex items-center gap-1.5">
                  <span
                    class="inline-block shrink-0 text-[10px] transition-transform"
                    :class="openedBoxes.has(group.key) ? 'rotate-90' : ''"
                    aria-hidden="true"
                  >▶</span>
                  <span class="truncate font-medium">
                    {{ group.boxName || d.cart.wholeBox }}
                  </span>
                  <span class="shrink-0 rounded bg-amber-100 px-1.5 py-0.5 text-[11px] text-amber-700 dark:bg-amber-900/40 dark:text-amber-300">{{ d.cart.endBoxLine }}</span>
                </span>
                <span class="mt-1 block truncate text-xs text-gray-500 dark:text-gray-400">
                  {{ fill(d.order.boxGroup, { name: group.boxName || d.cart.wholeBox, n: group.items.length, qty: groupQuantity(group) }) }}
                </span>
              </button>
              <span class="shrink-0 text-sm font-medium text-red-500 dark:text-red-400">{{ moneyOf(groupTotal(group)) }}</span>
            </li>
            <li v-for="item in (group.box && !openedBoxes.has(group.key) ? [] : group.items)" :key="item.id" class="flex items-start gap-3 p-3">
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
          </template>
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
