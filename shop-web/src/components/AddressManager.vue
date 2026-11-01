<script setup lang="ts">
/**
 * 收货地址管理弹窗：结算页那颗「管理地址」开的就是这一颗。
 *
 * 摆在这儿是为了不用把人赶回个人中心再跑回来 —— 新增/修改用的还是 AddressForm 那一份，
 * 两处共用同一个表单，也就不会写出两种回填口径（省市区那两列、整条覆盖那些判据都在它那边）。
 *
 * 只给「添加」和「修改」，没给删除：正被订单引用的地址在这儿一删，那单的收货信息就断了，
 * 而结算页那一条判据是「坐标和地区都没有的才选不上」（见 lib/profile 的 canShipTo），删不是这一页该解的结。
 *
 * saved 之后除了自己重拉一遍（默认那条会被服务端换掉），还向上传一条 changed：
 * 调用方那份地址列表是它自己捞的，钱也跟着地址走（配送费按它就近分仓算），得让它重算。
 */
import { onMounted, ref } from 'vue';
import AddressForm from '@/views/user/AddressForm.vue';
import ModalOverlay from '@/components/ModalOverlay.vue';
import { useI18n } from '@/i18n';
import { canShipTo, fetchMyAddresses, type AddressRow } from '@/lib/profile';

/** 语种由挂它的那颗岛（结算页 / 个人中心）沿棵传下来，这一颗自己不用收 locale */
const { d } = useI18n();

const emit = defineEmits<{ changed: []; close: [] }>();

const rows = ref<AddressRow[]>([]);
const loading = ref(false);
const error = ref('');

/** 新增和修改共用一份表单：formOpen 管挂不挂，editing 管带不带现成那一条回去 */
const formOpen = ref(false);
const editing = ref<AddressRow | null>(null);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const page = await fetchMyAddresses(1, 50);
    rows.value = page.records;
  } catch (err) {
    error.value = (err as Error).message;
  } finally {
    loading.value = false;
  }
}

function openForm(row: AddressRow | null) {
  editing.value = row;
  formOpen.value = true;
}

async function onSaved() {
  formOpen.value = false;
  emit('changed');
  await load();
}

onMounted(load);
</script>

<template>
  <ModalOverlay @close="emit('close')">
    <div
      role="dialog"
      aria-modal="true"
      :aria-label="d.address.dialogAria"
      class="max-h-full w-[600px] max-w-full overflow-y-auto rounded-xl bg-white p-5 shadow-xl dark:bg-gray-800"
    >
      <div class="mb-3 flex items-center justify-between">
        <h2 class="text-lg font-medium">{{ d.address.title }}</h2>
        <button
          type="button"
          class="text-2xl leading-none text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
          :aria-label="d.common.close"
          @click="emit('close')"
        >
          ×
        </button>
      </div>

      <button
        type="button"
        class="mb-3 rounded-lg bg-blue-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-blue-700"
        @click="openForm(null)"
      >
        {{ d.address.add }}
      </button>

      <p v-if="loading" class="py-3 text-sm text-gray-500 dark:text-gray-400">{{ d.address.loading }}</p>
      <p v-else-if="error" class="mb-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
        {{ error }}
      </p>
      <p v-else-if="!rows.length" class="py-3 text-sm text-gray-500 dark:text-gray-400">
        {{ d.address.empty }}
      </p>

      <ul v-else class="divide-y divide-gray-100 dark:divide-gray-700">
        <li v-for="row in rows" :key="row.id" class="py-3">
          <div class="flex items-baseline gap-2 text-sm">
            <span class="font-medium">{{ row.receiverName || d.common.dash }}</span>
            <span class="text-gray-500 dark:text-gray-400">{{ row.receiverPhone || d.common.dash }}</span>
            <span
              v-if="row.isDefault === 1"
              class="rounded bg-green-50 px-1.5 text-xs text-green-700 dark:bg-green-900/40 dark:text-green-300"
            >{{ d.address.defaultTag }}</span>
            <span
              v-if="row.tag"
              class="rounded bg-gray-100 px-1.5 text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-300"
            >{{ row.tag }}</span>
            <button
              type="button"
              class="ml-auto shrink-0 text-sm text-blue-600 hover:underline dark:text-blue-400"
              @click="openForm(row)"
            >
              {{ d.common.edit }}
            </button>
          </div>
          <p class="mt-1 text-sm text-gray-600 dark:text-gray-300">
            {{ [row.areaZh, row.detail].filter(Boolean).join(' ') || d.address.noDetail }}
          </p>
          <p v-if="!canShipTo(row)" class="mt-1 text-xs text-amber-600 dark:text-amber-400">
            {{ d.address.noCoord }}
          </p>
        </li>
      </ul>
    </div>

    <AddressForm v-if="formOpen" :address="editing" @saved="onSaved" @close="formOpen = false" />
  </ModalOverlay>
</template>
