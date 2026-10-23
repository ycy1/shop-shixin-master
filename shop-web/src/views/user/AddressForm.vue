<script setup lang="ts">
import { computed, ref } from 'vue';
import { useCascaderAreaData } from '@vant/area-data';
import { useI18n } from '@/i18n';
import MapPicker from '@/components/MapPicker.vue';
import ModalOverlay from '@/components/ModalOverlay.vue';
import { ADDRESS_TAGS, addMyAddress, updateMyAddress, type AddressForm as AddressFormPayload, type AddressRow } from '@/lib/profile';
import type { PickedPoint } from '@/lib/map';

/**
 * 收货地址那一格弹窗：新增（列表上方那颗「添加地址」）和修改（列表每行那颗「编辑」）共用这一份
 *
 * 表单照抄后台收货地址那页的新增弹窗，只去掉「消费者」那一格 ——
 * 前台这一格没有可挑的人，userId 由服务端钉成登录 id（见 lib/profile 那句注释）。
 * 必填就三样：收货人姓名 / 电话 / 详细地址，和表上那三列 NOT NULL 对上，其余都可空。
 *
 * 省市区按项目统一口径收两列：area_code（逗号分隔的行政区划代码）+ area_zh（拼好的中文），
 * 数据源和后台那份是同一个 @vant/area-data，所以两处选出来的代码、拼出来的中文一模一样。
 *
 * 修改是整条覆盖式提交（后台那条 update 同形）：area_code / area_zh / tag / 经纬度 不填就是清成空，
 * 所以回填要把列表带回来的每列都摆上 —— 页面上没展示的邮编也照样带回来原样送回，免得替用户抹掉。
 */

const props = defineProps<{ address?: AddressRow | null }>();

const emit = defineEmits<{ saved: []; close: [] }>();

const { d } = useI18n();

/** 级联数据只在弹窗挂起来时才用（模块级取一次就够，后台那份 composable 同形） */
const provinces = useCascaderAreaData();


/** 修改态：带着现成那一条来的（id 在请求体里送，不走路径参数，形状同后台那份 update） */
const editing = computed(() => props.address ?? null);
const title = computed(() => (editing.value ? d.addressForm.editTitle : d.addressForm.addTitle));
/** 修改那一趟是整条覆盖，不提醒一句「空着就是清掉」用户不会知道标签/坐标被抹了 */
const tip = computed(() => (editing.value ? d.addressForm.editTip : d.addressForm.addTip));

/** 表单收的是字符串，提交那一趟才转数（坐标空着要送 null，不是 0） */
interface AddressFormState {
  receiverName: string;
  receiverPhone: string;
  provinceCode: string;
  cityCode: string;
  districtCode: string;
  detail: string;
  tag: string;
  /** 页面上没这一格，只从列表那一行接过来原样送回（不这么送会被整条覆盖清成空） */
  postalCode: string;
  /** 这两格挂的是 type="number"，Vue 会把填进去的值转成数（空着才是空串），所以提交前不能直接 .trim() */
  longitude: string | number;
  latitude: string | number;
  isDefault: boolean;
}

/** 列表那一行的 area_code 拆回三级下拉；只有两级的市拆不出第三段，那一格本来就挂不上 */
function toForm(address: AddressRow | null | undefined): AddressFormState {
  const codes = (address?.areaCode ?? '')
    .split(',')
    .map((code) => code.trim())
    .filter(Boolean);
  return {
    receiverName: address?.receiverName ?? '',
    receiverPhone: address?.receiverPhone ?? '',
    provinceCode: codes[0] ?? '',
    cityCode: codes[1] ?? '',
    districtCode: codes[2] ?? '',
    detail: address?.detail ?? '',
    tag: address?.tag ?? '',
    postalCode: address?.postalCode ?? '',
    longitude: address?.longitude == null ? '' : String(address.longitude),
    latitude: address?.latitude == null ? '' : String(address.latitude),
    isDefault: address?.isDefault === 1,
  };
}

const form = ref(toForm(props.address));

const submitting = ref(false);
const error = ref('');

/** 下一级候选：选了哪一级就拿那一级的 children，没 children（东莞、中山这类不建区的市）就只剩两级 */
const cities = computed(() => provinces.find((p) => p.value === form.value.provinceCode)?.children ?? []);
const districts = computed(() => cities.value.find((c) => c.value === form.value.cityCode)?.children ?? []);
/** 这一格有没有第三级可选：不建区的市不给挂那个空下拉 */
const hasDistrict = computed(() => districts.value.length > 0);

/** 选到省就把市、区清空，选到市就清区：留着上级的值会拼出一条不存在的代码串 */
function onProvinceChange() {
  form.value.cityCode = '';
  form.value.districtCode = '';
}
function onCityChange() {
  form.value.districtCode = '';
}

/** 已选中的三段代码（可能只选到两级），和 area_zh 一起送给后端 */
const pickedCodes = computed(() =>
  [form.value.provinceCode, form.value.cityCode, form.value.districtCode].filter(Boolean),
);
const areaZh = computed(() => {
  const names = [
    provinces.find((p) => p.value === form.value.provinceCode)?.text,
    cities.value.find((c) => c.value === form.value.cityCode)?.text,
    districts.value.find((d) => d.value === form.value.districtCode)?.text,
  ];
  return names.filter(Boolean).join(' ');
});

const canSubmit = computed(
  () =>
    !submitting.value &&
    !!form.value.receiverName.trim() &&
    !!form.value.receiverPhone.trim() &&
    !!form.value.detail.trim(),
);

/**
 * 地图选点回来：坐标一定写（用户就是点了那一下），详细地址只在高德真的给出地址时才覆盖 ——
 * 只是打开看了眼位置（address 为空）不能把用户手写的门牌换成高德给的街道名（后台那条同判据）。
 */
function handlePick(point: PickedPoint) {
  form.value.longitude = point.longitude.toFixed(6);
  form.value.latitude = point.latitude.toFixed(6);
  if (point.address) form.value.detail = point.address;
}

async function submit() {
  if (!canSubmit.value) return;
  submitting.value = true;
  error.value = '';
  try {
    const payload: AddressFormPayload = {
      receiverName: form.value.receiverName.trim(),
      receiverPhone: form.value.receiverPhone.trim(),
      areaCode: pickedCodes.value.join(','),
      areaZh: areaZh.value,
      detail: form.value.detail.trim(),
      tag: form.value.tag.trim(),
      // 这一格页面不展示，只是把库里原来那个值带回来再送回去；新增时是空串 ⇒ 送 null
      postalCode: form.value.postalCode.trim() || null,
      // 这两格库里是可空列：填了就送数，空着就送 null（不是 0，也不是空串）
      longitude: String(form.value.longitude ?? '').trim() ? Number(form.value.longitude) : null,
      latitude: String(form.value.latitude ?? '').trim() ? Number(form.value.latitude) : null,
      isDefault: form.value.isDefault ? 1 : 0,
    };
    const row = editing.value;
    await (row ? updateMyAddress({ ...payload, id: row.id }) : addMyAddress(payload));
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
      class="max-h-full w-[560px] max-w-full overflow-y-auto rounded-xl bg-white p-6 shadow-xl dark:bg-gray-800"
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
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <label class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.addressForm.receiverName }} *</span>
            <input
              v-model.trim="form.receiverName"
              type="text"
              autocomplete="name"
              class="w-full rounded-lg border border-gray-300 px-3 py-2 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>
          <label class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.addressForm.receiverPhone }} *</span>
            <input
              v-model.trim="form.receiverPhone"
              type="tel"
              autocomplete="tel"
              class="w-full rounded-lg border border-gray-300 px-3 py-2 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>
        </div>

        <!-- 省市区：收两列（代码 + 中文），口径同后台收货地址那页 -->
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <label class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.addressForm.province }}</span>
            <select
              v-model="form.provinceCode"
              class="w-full rounded-lg border border-gray-300 px-3 py-2 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
              @change="onProvinceChange"
            >
              <option value="">{{ d.addressForm.pleaseSelect }}</option>
              <option v-for="p in provinces" :key="p.value" :value="p.value">{{ p.text }}</option>
            </select>
          </label>
          <label class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.addressForm.city }}</span>
            <select
              v-model="form.cityCode"
              :disabled="!cities.length"
              class="w-full rounded-lg border border-gray-300 px-3 py-2 disabled:opacity-60 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
              @change="onCityChange"
            >
              <option value="">{{ d.addressForm.pleaseSelect }}</option>
              <option v-for="c in cities" :key="c.value" :value="c.value">{{ c.text }}</option>
            </select>
          </label>
          <label v-if="hasDistrict" class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.addressForm.district }}</span>
            <select
              v-model="form.districtCode"
              class="w-full rounded-lg border border-gray-300 px-3 py-2 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            >
              <option value="">{{ d.addressForm.pleaseSelect }}</option>
              <option v-for="d in districts" :key="d.value" :value="d.value">{{ d.text }}</option>
            </select>
          </label>
        </div>

        <label class="block text-sm">
          <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.addressForm.detail }} *</span>
          <input
            v-model.trim="form.detail"
            type="text"
            :placeholder="d.addressForm.detailPlaceholder"
            class="w-full rounded-lg border border-gray-300 px-3 py-2 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
          />
        </label>

        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <label class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.addressForm.tag }}</span>
            <input
              v-model.trim="form.tag"
              list="address-tag-options"
              :placeholder="d.addressForm.tagPlaceholder"
              class="w-full rounded-lg border border-gray-300 px-3 py-2 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            />
            <datalist id="address-tag-options">
              <option v-for="item in ADDRESS_TAGS" :key="item" :value="item"></option>
            </datalist>
          </label>
          <div class="flex items-end pb-1">
            <label class="flex items-center gap-2 text-sm">
              <input v-model="form.isDefault" type="checkbox" class="h-4 w-4 rounded border-gray-300" />
              <span class="text-gray-600 dark:text-gray-300">{{ d.addressForm.setDefault }}</span>
            </label>
          </div>
        </div>

        <!-- 经纬度：地图选点给的两个数，也可以自己填（地图挂了这条路还留着，同后台那句提示） -->
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <label class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.addressForm.longitude }}</span>
            <input
              v-model.trim="form.longitude"
              type="number"
              step="0.000001"
              placeholder="GCJ-02"
              class="w-full rounded-lg border border-gray-300 px-3 py-2 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>
          <label class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.addressForm.latitude }}</span>
            <input
              v-model.trim="form.latitude"
              type="number"
              step="0.000001"
              placeholder="GCJ-02"
              class="w-full rounded-lg border border-gray-300 px-3 py-2 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>
          <div class="flex items-end pb-1">
            <MapPicker :longitude="Number(form.longitude) || null" :latitude="Number(form.latitude) || null" @confirm="handlePick" />
          </div>
        </div>

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
            {{ submitting ? d.addressForm.saving : d.common.confirm }}
          </button>
        </div>
      </form>
    </div>
  </ModalOverlay>
</template>
