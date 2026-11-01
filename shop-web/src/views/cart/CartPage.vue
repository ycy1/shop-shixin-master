<script setup lang="ts">
/**
 * 我的购物车：一行是一个规格（库里 shop_consumer_cart 那一行，同一件商品的两个规格就是两行、各自一个价）。
 *
 * 这一页所有数都是从服务端联出来的（名称、单价、规格文本、规格图、可用库存、还能不能买），
 * 前端只改「勾选」和「数量」两格，改完就地翻一下画面、失败再退回库里那一份 —— 单价和库存不是这里说了算的。
 *
 * 合计只算「还能买而且勾着」的那几行；商品下架了的行留在列表里画成灰行（勾是禁用着的），
 * 一是让人看得见自己车里有过什么，二是那些行还得能在这儿删掉，不然清不出去。
 */
import { computed, onMounted, ref, watch } from 'vue';
import { FwbButton } from 'flowbite-vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import { useI18n } from '@/i18n';
import {
  boxPieceCount,
  boxTagOf,
  isWholeBoxLine,
  lineImageOf,
  payPriceOf,
  stockCapOf,
  type CartLine,
} from '@/lib/cart';
import { useAuthStore } from '@/stores/auth';
import { useCartStore } from '@/stores/cart';

const auth = useAuthStore();
const cart = useCartStore();
/** locale 由页面壳递进来：岛里拿不到 Astro 对象（见 @/i18n 顶上那段） */
defineProps<{ locale?: string }>();
const { d, fill, href } = useI18n();

/** 登录态读 localStorage，服务端那一趟没有 ⇒ 第一趟两边都画「没登录」那一块，否则撞 hydration */
const ready = ref(false);
const signedIn = computed(() => ready.value && auth.isLoggedIn);

/**
 * 整页一次只让一颗写按得下去（哪一颗在跑记在这儿，只用来把控件置灰）。
 * 不做成"逐行各自能按"是因为每一颗写失败都要重拉一次列表回退，两行同时在跑就会互相把对方的画面换掉。
 */
const busyId = ref<number | null>(null);
/** 整块级别的那两颗：全选、批量删除 */
const batchBusy = ref(false);
const actionError = ref('');
/** 待确认的删除：要删的 id + 念给人看的那一句（一行那颗说商品名，批量那颗说删几条） */
const pendingDelete = ref<{ ids: number[]; message: string } | null>(null);

onMounted(() => {
  ready.value = true;
});

watch(signedIn, (on) => {
  if (on) void cart.load();
});

const selectedIds = computed(() => cart.selectedLines.map((line) => line.id));
const hasSelection = computed(() => selectedIds.value.length > 0);

/**
 * 列表顶上那排筛选：全部 / 普通商品 / 盲盒（整盒也在盲盒那一档 —— 端盒那一行 source_type 同样是 1）。
 *
 * 只管画哪几行：勾选、已选件数、合计和结算仍是整车那一份（结算页按库里记着的勾选状态重新拉一遍），
 * 否则「筛到盲盒、全选、去结算」会悄悄少买几件。
 */
const cartFilter = ref(0);
const CART_FILTERS = computed(() => [
  { label: d.cart.filterAll, value: 0 },
  { label: d.cart.filterNormal, value: 1 },
  { label: d.cart.filterBox, value: 2 },
]);
const visibleLines = computed(() =>
  cartFilter.value === 0
    ? cart.lines
    : cart.lines.filter((line) => (cartFilter.value === 2) === (line.sourceType === 1)),
);

/** 规格文本这一格后端可空（无规格的默认SKU），页面自己补一个说法，口径同详情页 */
function specLabel(line: CartLine) {
  return line.specText?.trim() || d.cart.defaultSpec;
}

/** 行小计：单价读车里那一格（抽中入车的挂的是盲盒单抽价），不是联出来的规格原价 */
function lineTotal(line: CartLine) {
  return (payPriceOf(line) * line.quantity).toFixed(2);
}

/**
 * 步进封顶：普通行封到这一条规格的可用库存（库里没有那一行的库存行时后端给 0，这时候不给往上加，
 * 下单那一刻也会拦）；一整盒那一行不封顶 —— 它按「几盒」加减，某一款的仓存不是这一行的上限，
 * 盒内每款的库存要等下单那一刻逐款复核。
 */
function stockOf(line: CartLine) {
  return stockCapOf(line);
}

/** 这一行念成什么：整盒那一行没有商品名（它不挂商品），念「一整盒 · 盒名」，口径同上面那枚 tip */
function titleOf(line: CartLine) {
  if (isWholeBoxLine(line)) {
    return line.sourceName ? `${d.cart.wholeBox} · ${line.sourceName}` : d.cart.wholeBox;
  }
  return line.productName ?? d.cart.unnamedProduct;
}

/** 展开看盒内款式的那几行是就地展开、不跳页，所以自己记着哪几盒开着 */
const openedBoxes = ref<Set<number>>(new Set());

function toggleBoxOpen(id: number) {
  const next = new Set(openedBoxes.value);
  if (next.has(id)) {
    next.delete(id);
  } else {
    next.add(id);
  }
  openedBoxes.value = next;
}

async function stepQuantity(line: CartLine, delta: number) {
  const next = line.quantity + delta;
  if (next < 1 || next > stockOf(line) || busyId.value !== null) return;
  await runOnLine(line, () => cart.setQuantity(line.id, next));
}

async function toggleSelected(line: CartLine) {
  if (line.buyable !== 1 || busyId.value !== null) return;
  await runOnLine(line, () => cart.setSelected(line.id, line.selected === 1 ? 0 : 1));
}

/** 单颗写的失败就地印后端那句（「数量要大于 0」这种），store 里已经把那一行退回原值 */
async function runOnLine(line: CartLine, write: () => Promise<void>) {
  busyId.value = line.id;
  actionError.value = '';
  try {
    await write();
  } catch (err) {
    actionError.value = (err as Error).message;
  } finally {
    busyId.value = null;
  }
}

async function toggleAll() {
  if (batchBusy.value || !cart.buyableLines.length) return;
  const next = cart.allSelected ? 0 : 1;
  batchBusy.value = true;
  actionError.value = '';
  try {
    await cart.setAllSelected(next as 0 | 1);
  } catch (err) {
    actionError.value = (err as Error).message;
  } finally {
    batchBusy.value = false;
  }
}

/** 一行那颗「删除」和上面那颗「删除选中」走同一颗确认框，只是念出来的那句不一样 */
function askDelete(line: CartLine) {
  pendingDelete.value = {
    ids: [line.id],
    message: fill(d.cart.removeLineConfirm, {
      name: titleOf(line),
      spec: specLabel(line),
    }),
  };
}

function askDeleteSelected() {
  const ids = selectedIds.value;
  if (!ids.length) return;
  pendingDelete.value = { ids, message: fill(d.cart.removeSelectedConfirm, { n: ids.length }) };
}

async function confirmDelete() {
  const pending = pendingDelete.value;
  if (!pending) return;
  batchBusy.value = true;
  actionError.value = '';
  try {
    await cart.remove(pending.ids);
    pendingDelete.value = null;
  } catch (err) {
    actionError.value = (err as Error).message;
    // 删失败可能是库里那几条已经不是这个状态了（另一个标签页删过、后台代客清了），回库里那一份重画
    pendingDelete.value = null;
    await cart.load();
  } finally {
    batchBusy.value = false;
  }
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-semibold">{{ d.cart.title }}</h1>
      <a :href="href('/')" class="text-sm text-blue-600 hover:underline dark:text-blue-400">&larr; {{ d.cart.browse }}</a>
    </div>

    <!-- 没登录这一页就没内容：给一颗开登录窗的按钮（头部那颗岛认 data-open-login） -->
    <div
      v-if="!signedIn"
      class="rounded-xl border border-gray-200 bg-white p-8 text-center dark:border-gray-700 dark:bg-gray-800"
    >
      <p class="text-gray-600 dark:text-gray-300">{{ d.cart.signedOut }}</p>
      <button
        type="button"
        data-open-login
        class="mt-4 rounded-lg bg-blue-600 px-5 py-2 text-sm font-medium text-white hover:bg-blue-700"
      >
        {{ d.auth.entry }}
      </button>
    </div>

    <template v-else>
      <p v-if="cart.loading && !cart.lines.length" class="py-10 text-center text-sm text-gray-500 dark:text-gray-400">
        {{ d.common.loading }}
      </p>

      <p
        v-else-if="cart.error"
        class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300"
      >
        {{ cart.error }}
      </p>

      <!-- 车里一条都没有：那一排 toolbar 和合计条都没东西可管，整块收掉，只留这一格 -->
      <div
        v-else-if="!cart.lines.length"
        class="rounded-xl border border-gray-200 bg-white p-10 text-center dark:border-gray-700 dark:bg-gray-800"
      >
        <p class="text-gray-600 dark:text-gray-300">{{ d.cart.empty }}</p>
        <a
          :href="href('/')"
          class="mt-4 inline-block rounded-lg bg-blue-600 px-5 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          {{ d.cart.startPicking }}
        </a>
      </div>

      <template v-else>
        <!-- 某一行写失败了就单开一行印后端那句（点第二下一句自然被顶掉），store 已经把那一行退回去了 -->
        <p
          v-if="actionError"
          class="rounded-lg bg-red-50 px-4 py-2 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300"
        >
          {{ actionError }}
        </p>

        <!-- 列表顶上那排只看不写：全部 / 普通商品 / 盲盒。勾选、合计、结算仍是整车那一份，换档不动它们 -->
        <div class="flex flex-wrap items-center gap-2">
          <button
            v-for="filter in CART_FILTERS"
            :key="filter.value"
            type="button"
            class="rounded-full border px-3 py-1 text-sm transition-colors"
            :class="
              cartFilter === filter.value
                ? 'border-blue-600 bg-blue-50 font-medium text-blue-700 dark:border-blue-400 dark:bg-blue-900/40 dark:text-blue-300'
                : 'border-gray-300 text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700'
            "
            @click="cartFilter = filter.value"
          >
            {{ filter.label }}
          </button>
        </div>

        <!-- 改这一整列的东西（勾选、删除）的按钮开一行放在列表上面，不塞进下面那条合计里 -->
        <div class="flex flex-wrap items-center gap-4 rounded-lg border border-gray-200 bg-white px-4 py-3 text-sm dark:border-gray-700 dark:bg-gray-800">
          <label class="flex items-center gap-2">
            <input
              type="checkbox"
              class="h-4 w-4 rounded border-gray-300 dark:border-gray-600"
              :checked="cart.allSelected"
              :disabled="batchBusy || !cart.buyableLines.length"
              @change="toggleAll"
            />
            <span>{{ d.cart.selectAll }}</span>
          </label>
          <span class="text-gray-500 dark:text-gray-400">{{ fill(d.cart.selectedCount, { n: cart.selectedCount }) }}</span>
          <FwbButton
            size="sm"
            color="alternative"
            :disabled="batchBusy || !hasSelection"
            @click="askDeleteSelected"
          >
            {{ d.cart.removeSelected }}
          </FwbButton>
        </div>

        <ul class="space-y-3">
          <li
            v-for="line in visibleLines"
            :key="line.id"
            class="flex flex-wrap items-center gap-4 rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800"
            :class="line.buyable === 1 ? '' : 'opacity-60'"
          >
            <input
              type="checkbox"
              class="h-4 w-4 shrink-0 rounded border-gray-300 dark:border-gray-600"
              :checked="line.selected === 1 && line.buyable === 1"
              :disabled="line.buyable !== 1 || busyId !== null"
              :aria-label="fill(d.cart.selectLineAria, { name: titleOf(line), spec: specLabel(line) })"
              @change="toggleSelected(line)"
            />

            <div class="h-20 w-20 shrink-0 overflow-hidden rounded-lg border border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-900">
              <img
                v-if="lineImageOf(line)"
                :src="lineImageOf(line) || ''"
                :alt="titleOf(line)"
                class="h-full w-full object-cover"
              />
            </div>

            <div class="min-w-0 flex-1">
              <p class="truncate font-medium text-gray-900 dark:text-gray-100">
                <!-- 一整盒那一行不挂在某个商品上（productId 是空的），点回那台盒 -->
                <a
                  v-if="isWholeBoxLine(line) && line.sourceId"
                  :href="href(`/trendy/box/${line.sourceId}`)"
                  class="hover:text-blue-600 hover:underline dark:hover:text-blue-400"
                >{{ titleOf(line) }}</a>
                <a
                  v-else-if="line.productId"
                  :href="href(`/product/${line.productId}`)"
                  class="hover:text-blue-600 hover:underline dark:hover:text-blue-400"
                >{{ line.productName }}</a>
                <span v-else>{{ line.productName ?? d.cart.goneProduct }}</span>
              </p>
              <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
                <!-- 抽中入车的那一行和同一规格自己加购的那一行并排躺着，不标出来看着就像重复加了两次；
                     两台盒抽中同一款也是并排两行，所以这一枚顺带报是哪台盒；端盒那一趟是一整盒一行，说端盒 -->
                <span
                  v-if="line.sourceType === 1"
                  class="mr-2 rounded bg-amber-100 px-1.5 py-0.5 text-[11px] text-amber-700 dark:bg-amber-900/40 dark:text-amber-300"
                >{{ boxTagOf(isWholeBoxLine(line) ? d.cart.endBoxLine : d.cart.boxLine, line) }}</span>
                <button
                  v-if="isWholeBoxLine(line)"
                  type="button"
                  class="inline-flex items-center gap-1.5 rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-600 hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
                  @click="toggleBoxOpen(line.id)"
                ><span
                    class="inline-block text-[10px] transition-transform"
                    :class="openedBoxes.has(line.id) ? 'rotate-90' : ''"
                    aria-hidden="true"
                  >▶</span>{{ fill(d.cart.boxPieces, { n: boxPieceCount(line) }) }}</button>
                <template v-else>
                  {{ fill(d.cart.spec, { n: specLabel(line) }) }}
                  <span v-if="line.storeName"> · {{ line.storeName }}</span>
                  <span v-if="line.categoryName"> · {{ line.categoryName }}</span>
                </template>
              </p>
              <!-- 展开看盒内有哪几款、各几件：只给说法。钱是整盒那一行自己的单价，不往每款上摊给买家看
                   （下单拆成每款一条明细时服务端才摊，那是账本上的事） -->
              <ul
                v-if="isWholeBoxLine(line) && openedBoxes.has(line.id)"
                class="mt-2 space-y-1 text-xs text-gray-500 dark:text-gray-400"
              >
                <li
                  v-for="(one, i) in line.boxItemList"
                  :key="`${line.id}-${i}`"
                  class="flex items-center gap-2"
                >
                  <img
                    v-if="one.styleImage"
                    :src="one.styleImage"
                    :alt="one.styleName ?? ''"
                    class="h-6 w-6 shrink-0 rounded object-cover"
                  />
                  <span class="truncate">{{ one.styleName ?? d.cart.unnamedProduct }}</span>
                  <!-- 款式清单是「一盒里有什么」，这一行是几盒就翻几倍（同一台盒端第二次并成一行） -->
                  <span class="shrink-0">× {{ Number(one.quantity ?? 1) * line.quantity }}</span>
                </li>
              </ul>
              <!-- 买不了只有「商品已下架」这一句话：规格的 sale_enable 是 ERP 建销售单那边的开关，不管前台 -->
              <p v-if="line.buyable !== 1" class="mt-1 text-xs text-amber-600 dark:text-amber-400">
                {{ d.cart.unbuyable }}
              </p>
            </div>

            <p class="w-24 shrink-0 text-sm text-gray-700 dark:text-gray-300">
              ¥{{ payPriceOf(line).toFixed(2) }}
            </p>

            <!-- overflow-hidden 和详情页那颗一样：步进按钮自己没圆角，外层 rounded-lg 不裁子元素，
                 鼠标移入时方块的 hover 底色会顶出圆角 -->
            <div
              class="flex shrink-0 items-center overflow-hidden rounded-lg border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-900"
            >
              <button
                type="button"
                class="px-3 py-1.5 text-gray-500 hover:bg-gray-50 disabled:cursor-not-allowed disabled:text-gray-300 dark:text-gray-400 dark:hover:bg-gray-700 dark:disabled:text-gray-600"
                :disabled="line.quantity <= 1 || busyId !== null"
                @click="stepQuantity(line, -1)"
              >
                −
              </button>
              <span class="w-10 text-center text-sm font-medium">{{ line.quantity }}</span>
              <button
                type="button"
                class="px-3 py-1.5 text-gray-500 hover:bg-gray-50 disabled:cursor-not-allowed disabled:text-gray-300 dark:text-gray-400 dark:hover:bg-gray-700 dark:disabled:text-gray-600"
                :disabled="line.buyable !== 1 || line.quantity >= stockOf(line) || busyId !== null"
                :title="line.quantity >= stockOf(line) ? fill(d.cart.stockLimitTitle, { n: stockOf(line) }) : ''"
                @click="stepQuantity(line, 1)"
              >
                +
              </button>
            </div>

            <p class="w-24 shrink-0 text-right text-sm font-medium text-red-600 dark:text-red-400">
              ¥{{ lineTotal(line) }}
            </p>

            <button
              type="button"
              class="shrink-0 text-sm text-gray-500 hover:text-red-600 dark:text-gray-400 dark:hover:text-red-400"
              @click="askDelete(line)"
            >
              {{ d.common.delete }}
            </button>
          </li>
        </ul>

        <!-- 车里有钱、只是这一档没有：说一句，别把「购物车还是空的」那段拿来骗人 -->
        <div
          v-if="!visibleLines.length"
          class="rounded-xl border border-gray-200 bg-white p-8 text-center text-sm text-gray-600 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300"
        >
          {{ d.cart.emptyFiltered }}
        </div>

        <div class="flex flex-wrap items-center justify-end gap-4 rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm dark:border-gray-700 dark:bg-gray-800">
          <span class="text-gray-500 dark:text-gray-400">
            {{ fill(d.cart.summary, { a: cart.lines.length, b: cart.selectedCount }) }}
          </span>
          <span class="text-lg font-semibold text-red-600 dark:text-red-400">
            {{ d.cart.total }} ¥{{ cart.selectedTotal.toFixed(2) }}
          </span>
          <!-- 去结算：勾着的那几行（含它们勾选状态，库里已经记着）交给结算页，那一页自己重拉一遍 -->
          <a
            v-if="hasSelection"
            :href="href('/checkout')"
            class="rounded-lg bg-orange-500 px-5 py-2 text-sm font-medium text-white hover:bg-orange-600"
          >
            {{ d.cart.goCheckout }}
          </a>
          <span v-else class="text-sm text-gray-400 dark:text-gray-500">{{ d.cart.needSelection }}</span>
        </div>
      </template>
    </template>

    <!-- 确认框挂在最外面：删一条和删一批共用这一颗，念出来的是那一句由 pendingDelete 定 -->
    <ConfirmDialog
      v-if="pendingDelete"
      :title="d.cart.deleteTitle"
      :message="pendingDelete.message"
      :confirm-text="d.common.delete"
      :busy="batchBusy"
      @confirm="confirmDelete"
      @cancel="pendingDelete = null"
    />
  </div>
</template>
