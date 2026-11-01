<script setup lang="ts">
import { computed, ref } from 'vue';
import { useCascaderAreaData } from '@vant/area-data';
import ImagePreview from '@/components/ImagePreview.vue';
import MapPicker from '@/components/MapPicker.vue';
import ModalOverlay from '@/components/ModalOverlay.vue';
import { enumLabel, useI18n } from '@/i18n';
import {
  updateMyAccount,
  uploadAvatar,
  type AccountForm as AccountFormPayload,
  type AccountInfo,
} from '@/lib/profile';
import type { PickedPoint } from '@/lib/map';

/**
 * 基础信息那一格弹窗：「个人中心 → 基础信息」上那颗「修改」点开的就是它。
 *
 * 表单照抄后台「消费者管理 → 个人信息」那一份（头像/昵称/手机号/邮箱/性别/所在地区/详细地址/经纬度/个性签名），
 * 只是外壳从 Element Plus 换成这一页在用的 Tailwind。
 * 服务端只认这几格（判据在那条 myProfile 的照抄件里），账号、账号类型、状态、角色一概不放进表单也不送 —— 传了也不认。
 *
 * 头像这一格分两步：先 POST /api/file/uploadImage 把图传上去拿回 URL，URL 跟着这张表一起送；
 * 传完不点「确定」什么都不会变（后台那份 upload-image 也是同一个顺序）。
 *
 * 省市区还是收两列（area_code 逗号串 + area_zh），数据源和后台同一个 @vant/area-data，
 * 所以拼出来的代码和中文跟后台一模一样（口径同收货地址那份表单）。
 *
 * 这张表全局 field-strategy=not_empty：空着送上去等于「不改」，不是「清空」，
 * 所以回填要读回来（areaCode / 经纬度 页面上没展示，也照样带回来摆着，不然改一次地区就把坐标串留旧的了）。
 *
 * 整张表单压在一屏内（头像并进昵称/手机号那一排、控件 py-1.5、行距 space-y-3、面板 w-[620px]）—— 他要的是弹窗里别出滚动条；
 * 面板上那条 `max-h-full overflow-y-auto` 只留作窗口太矮时的保底，真去掉的话「确 定」会滚不到、比一条滚动条更糟。
 */

const props = defineProps<{ account?: AccountInfo | null }>();
const { d } = useI18n();

const emit = defineEmits<{ saved: []; close: [] }>();

const provinces = useCascaderAreaData();

/** 性别那一排的顺序照后台（男 / 女 / 保密），文案共用 lib 里那张表 */
const SEX_OPTIONS = [1, 2, 0];

/** 表单收的是字符串，提交那一趟才转数（坐标空着要送 null，不是 0） */
interface AccountFormState {
  nickname: string;
  mobile: string;
  email: string;
  sex: number;
  /** 已经传好的图片 URL，换一张就是换一个串；空着送上去等于不改（不是清掉） */
  avatar: string;
  provinceCode: string;
  cityCode: string;
  districtCode: string;
  address: string;
  /** 这两格挂的是 type="number"，Vue 会把填进去的值转成数（空着才是空串），所以提交前不能直接 .trim() */
  longitude: string | number;
  latitude: string | number;
  signature: string;
}

/** 库里那一条拆回这页能填的形状：area_code 那串按逗号切三级，坐标转字符串 */
function toForm(user?: AccountInfo | null): AccountFormState {
  const codes = (user?.areaCode ?? '')
    .split(',')
    .map((code) => code.trim())
    .filter(Boolean);
  return {
    nickname: user?.nickname ?? '',
    mobile: user?.mobile ?? '',
    email: user?.email ?? '',
    sex: user?.sex == null ? 0 : Number(user.sex),
    avatar: user?.avatar ?? '',
    provinceCode: codes[0] ?? '',
    cityCode: codes[1] ?? '',
    districtCode: codes[2] ?? '',
    address: user?.address ?? '',
    longitude: user?.longitude == null ? '' : String(user.longitude),
    latitude: user?.latitude == null ? '' : String(user.latitude),
    signature: user?.signature ?? '',
  };
}

const form = ref(toForm(props.account));

const submitting = ref(false);
const error = ref('');

/** 下一级候选：选了哪一级就拿那一级的 children，没 children（东莞、中山这类不建区的市）就只剩两级 */
const cities = computed(() => provinces.find((p) => p.value === form.value.provinceCode)?.children ?? []);
const districts = computed(() => cities.value.find((c) => c.value === form.value.cityCode)?.children ?? []);
const hasDistrict = computed(() => districts.value.length > 0);

/** 选到省就把市、区清空，选到市就清区：留着上级的值会拼出一条不存在的代码串 */
function onProvinceChange() {
  form.value.cityCode = '';
  form.value.districtCode = '';
}
function onCityChange() {
  form.value.districtCode = '';
}

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

/** 手机号和后端同一套（1[3-9] 开头十一位，不查重）；邮箱后端不验形状，只在这一侧拦一下 */
const badMobile = computed(() => !!form.value.mobile && !/^1[3-9]\d{9}$/.test(form.value.mobile.trim()));
const badEmail = computed(() => !!form.value.email && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.value.email.trim()));

/** 上传中不给提交：URL 还没回来，那一趟送的是旧头像（或者空），点了确定等于白传一次 */
const uploading = ref(false);

/** 这一层压在弹窗（z-50）上面，所以点开的是 form.avatar 当前那一个串 —— 换过一张再看就是新的那张 */
const avatarPreviewOpen = ref(false);

const canSubmit = computed(
  () => !submitting.value && !uploading.value && !!form.value.nickname.trim() && !badMobile.value && !badEmail.value,
);

/** 那颗按钮上那句话跟着状态走：没传过说「选」、传过说「换」、正在传就别让人再点 */
const avatarButtonText = computed(() =>
  uploading.value
    ? d.account.accountForm.avatarUploading
    : form.value.avatar
      ? d.account.accountForm.avatarChange
      : d.account.accountForm.avatarPick,
);

/**
 * 选一张图就先传上去（顺序同后台那份 upload-image：传回来的是 URL，落到库里要等这张表提交那一下）。
 * input.value 清空是为了「同一张图再选一次」也能触发 change；只收 image/*，后端那侧会压缩、
 * 存储平台按默认那一个走，前台不管这些，只管把回来的 URL 摆进预览。
 */
async function onAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files && input.files[0];
  input.value = '';
  if (!file) return;
  if (!file.type.startsWith('image/')) {
    error.value = d.account.accountForm.onlyImage;
    return;
  }
  uploading.value = true;
  error.value = '';
  try {
    form.value.avatar = await uploadAvatar(file);
  } catch (err) {
    error.value = (err as Error).message;
  } finally {
    uploading.value = false;
  }
}

/**
 * 地图选点回来：坐标一定写（用户就是点了那一下），详细地址只在高德真的给出地址时才覆盖 ——
 * 只是打开看了眼位置（address 为空）不能把用户手写的门牌换成高德给的街道名（同收货地址那份判据）。
 */
function handlePick(point: PickedPoint) {
  form.value.longitude = point.longitude.toFixed(6);
  form.value.latitude = point.latitude.toFixed(6);
  if (point.address) form.value.address = point.address;
}

async function submit() {
  if (!canSubmit.value) return;
  submitting.value = true;
  error.value = '';
  try {
    const payload: AccountFormPayload = {
      nickname: form.value.nickname.trim(),
      mobile: form.value.mobile.trim() || null,
      email: form.value.email.trim() || null,
      sex: form.value.sex,
      avatar: form.value.avatar.trim() || null,
      areaCode: pickedCodes.value.join(',') || null,
      areaZh: areaZh.value || null,
      address: form.value.address.trim() || null,
      // 这两格是可空列：填了就送数，空着就送 null（不是 0，也不是空串）
      longitude: String(form.value.longitude ?? '').trim() ? Number(form.value.longitude) : null,
      latitude: String(form.value.latitude ?? '').trim() ? Number(form.value.latitude) : null,
      signature: form.value.signature.trim() || null,
    };
    await updateMyAccount(payload);
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
      :aria-label="d.account.accountForm.dialogTitle"
      class="max-h-full w-[620px] max-w-full overflow-y-auto rounded-xl bg-white p-5 shadow-xl dark:bg-gray-800"
    >
      <div class="mb-3 flex items-center justify-between">
        <h2 class="text-lg font-medium">{{ d.account.accountForm.dialogTitle }}</h2>
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

      <p v-if="error" class="mb-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
        {{ error }}
      </p>

      <form class="space-y-3" @submit.prevent="submit">
        <!-- 头像和昵称/手机号挤在同一排：这张表单矮一截，弹窗里就不必滚。换图仍是先传上去拿回 URL，落到库里要等这张表提交那一下 -->
        <div class="flex flex-wrap items-center gap-3">
          <button
            v-if="form.avatar"
            type="button"
            :aria-label="d.account.accountForm.zoomAvatar"
            class="h-12 w-12 shrink-0 cursor-zoom-in overflow-hidden rounded-full border border-gray-200 dark:border-gray-600"
            @click="avatarPreviewOpen = true"
          >
            <img :src="form.avatar" :alt="d.account.accountForm.avatarAlt" class="h-full w-full object-cover" />
          </button>
          <span
            v-else
            class="flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded-full border border-gray-200 text-gray-400 dark:border-gray-600"
            role="img"
            :aria-label="d.account.accountForm.noAvatar"
          >
            <svg class="h-7 w-7" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
              <circle cx="12" cy="8" r="4" />
              <path stroke-linecap="round" d="M4.5 20a7.5 7.5 0 0 1 15 0" />
            </svg>
          </span>
          <label
            class="shrink-0 cursor-pointer rounded-md border border-gray-300 px-2.5 py-1 text-xs text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
          >
            {{ avatarButtonText }}
            <input type="file" accept="image/*" class="sr-only" :disabled="uploading" @change="onAvatarChange" />
          </label>

          <div class="grid min-w-0 flex-1 grid-cols-1 gap-3 sm:grid-cols-2">
            <label class="block text-sm">
              <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.account.accountForm.nickname }}</span>
              <input
                v-model.trim="form.nickname"
                type="text"
                maxlength="30"
                class="w-full rounded-lg border border-gray-300 px-3 py-1.5 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
              />
            </label>
            <label class="block text-sm">
              <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.account.accountForm.mobile }}</span>
              <input
                v-model.trim="form.mobile"
                type="tel"
                maxlength="20"
                autocomplete="tel"
                class="w-full rounded-lg border border-gray-300 px-3 py-1.5 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
              />
              <span v-if="badMobile" class="mt-1 block text-xs text-red-600 dark:text-red-400">{{ d.account.accountForm.mobileHint }}</span>
            </label>
          </div>
        </div>

        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <label class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.account.accountForm.email }}</span>
            <input
              v-model.trim="form.email"
              type="email"
              maxlength="50"
              autocomplete="email"
              class="w-full rounded-lg border border-gray-300 px-3 py-1.5 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            />
            <span v-if="badEmail" class="mt-1 block text-xs text-red-600 dark:text-red-400">{{ d.account.accountForm.emailHint }}</span>
          </label>
          <fieldset class="text-sm">
            <legend class="mb-1 text-gray-600 dark:text-gray-300">{{ d.account.accountForm.sex }}</legend>
            <div class="flex gap-4 pt-1">
              <label v-for="value in SEX_OPTIONS" :key="value" class="flex items-center gap-2">
                <input v-model="form.sex" type="radio" :value="value" class="h-4 w-4 border-gray-300" />
                <span>{{ enumLabel(d.enum.sex, value) }}</span>
              </label>
            </div>
          </fieldset>
        </div>

        <!-- 省市区：收两列（代码 + 中文），口径同后台那份、也同本页收货地址那份 -->
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <label class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.account.accountForm.province }}</span>
            <select
              v-model="form.provinceCode"
              class="w-full rounded-lg border border-gray-300 px-3 py-1.5 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
              @change="onProvinceChange"
            >
              <option value="">{{ d.account.accountForm.selectPlaceholder }}</option>
              <option v-for="p in provinces" :key="p.value" :value="p.value">{{ p.text }}</option>
            </select>
          </label>
          <label class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.account.accountForm.city }}</span>
            <select
              v-model="form.cityCode"
              :disabled="!cities.length"
              class="w-full rounded-lg border border-gray-300 px-3 py-1.5 disabled:opacity-60 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
              @change="onCityChange"
            >
              <option value="">{{ d.account.accountForm.selectPlaceholder }}</option>
              <option v-for="c in cities" :key="c.value" :value="c.value">{{ c.text }}</option>
            </select>
          </label>
          <label v-if="hasDistrict" class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.account.accountForm.district }}</span>
            <select
              v-model="form.districtCode"
              class="w-full rounded-lg border border-gray-300 px-3 py-1.5 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            >
              <option value="">{{ d.account.accountForm.selectPlaceholder }}</option>
              <option v-for="d in districts" :key="d.value" :value="d.value">{{ d.text }}</option>
            </select>
          </label>
        </div>

        <label class="block text-sm">
          <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.account.accountForm.detailLabel }}</span>
          <input
            v-model.trim="form.address"
            type="text"
            maxlength="255"
            :placeholder="d.account.accountForm.detailPlaceholder"
            class="w-full rounded-lg border border-gray-300 px-3 py-1.5 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
          />
        </label>

        <!-- 经纬度：地图选点给的两个数，也可以自己填（地图挂了这条路还留着，同收货地址那份） -->
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <label class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.account.accountForm.longitude }}</span>
            <input
              v-model.trim="form.longitude"
              type="number"
              step="0.000001"
              placeholder="GCJ-02"
              class="w-full rounded-lg border border-gray-300 px-3 py-1.5 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>
          <label class="block text-sm">
            <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.account.accountForm.latitude }}</span>
            <input
              v-model.trim="form.latitude"
              type="number"
              step="0.000001"
              placeholder="GCJ-02"
              class="w-full rounded-lg border border-gray-300 px-3 py-1.5 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>
          <div class="flex items-end pb-1">
            <MapPicker :longitude="Number(form.longitude) || null" :latitude="Number(form.latitude) || null" @confirm="handlePick" />
          </div>
        </div>

        <label class="block text-sm">
          <span class="mb-1 block text-gray-600 dark:text-gray-300">{{ d.account.accountForm.signature }}</span>
          <textarea
            v-model.trim="form.signature"
            rows="2"
            maxlength="255"
            class="w-full resize-none rounded-lg border border-gray-300 px-3 py-1.5 leading-5 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
          ></textarea>
        </label>

        <p class="text-xs text-gray-500 dark:text-gray-400">
          {{ d.account.accountForm.note }}
        </p>

        <div class="flex justify-end gap-2 pt-1">
          <button
            type="button"
            class="rounded-lg border border-gray-300 px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
            @click="emit('close')"
          >
            {{ d.common.cancel }}
          </button>
          <button
            type="submit"
            class="rounded-lg bg-blue-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="!canSubmit"
          >
            {{ submitting ? d.account.accountForm.saving : d.common.confirm }}
          </button>
        </div>
      </form>
    </div>

    <ImagePreview v-if="avatarPreviewOpen" :src="form.avatar" :alt="d.account.accountForm.avatarAlt" @close="avatarPreviewOpen = false" />
  </ModalOverlay>
</template>
