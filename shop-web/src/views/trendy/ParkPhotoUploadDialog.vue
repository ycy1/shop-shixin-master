<script setup lang="ts">
/**
 * 「全部照片」那一页上那颗「上传照片」开出来的弹窗：挑一张图 → 先传文件拿 URL → 填一句图注 → 投稿。
 *
 * 三件事要说清：
 * - **先传文件、再投稿**（顺序同头像和广场动态那两处）：文件走全站那条 /api/file/uploadImage，
 *   这一条只把回来的 URL 递过去；服务端那一条不收文件，也就不必再抄一遍压缩/存储平台那套判据。
 * - **给谁投的、投到哪家园区之外的一切都由服务端定**：来源钉 2 用户上传、状态钉 0 待审核、
 *   所属 IP 跟着园区走，请求体里没有这几格 —— 让人自填 status=1 就等于自己给自己过审。
 * - **投完不会立刻上墙**：详情页那圈和这一页都只读审核通过的，所以成功那一屏要写明在等审核，
 *   别让人以为刷新一下就能看见自己的图。
 *
 * 大小和类型这道前端先拦一句（服务端那一条看的是全站的文件限制），超了当场说，不必等一趟往返。
 */
import { computed, ref } from 'vue';
import ModalOverlay from '@/components/ModalOverlay.vue';
import { useI18n } from '@/i18n';
import { submitParkPhoto, uploadParkPhotoFile, type ParkPhotoSubmission } from '@/lib/trendy';

const props = defineProps<{ parkId: number }>();

const emit = defineEmits<{ close: [] }>();

const { d, fill } = useI18n();
const t = d.trendy.upload;

/** 单张图的大小上限：存储那边压完也就几百 KB，挑到几十 MB 的原图基本是选错了文件，先拦下来 */
const MAX_BYTES = 10 * 1024 * 1024;
/** 和服务端 description 那一格同一数（ShopTrendyParkPhoto.DESCRIPTION_MAX） */
const DESC_MAX = 64;

const inputRef = ref<HTMLInputElement | null>(null);
const imageUrl = ref('');
const description = ref('');
const uploading = ref(false);
const submitting = ref(false);
const error = ref('');
const done = ref<ParkPhotoSubmission | null>(null);

const canSubmit = computed(() => !!imageUrl.value && !uploading.value && !submitting.value);

function pick() {
  inputRef.value?.click();
}

async function onPick(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  // 清空才要紧：同一张图选两次，不清就不触发 change（口径同广场那颗选图）
  input.value = '';
  if (!file) return;
  if (!file.type.startsWith('image/')) {
    error.value = t.notImage;
    return;
  }
  if (file.size > MAX_BYTES) {
    error.value = fill(t.tooBig, { n: Math.round(MAX_BYTES / 1024 / 1024) });
    return;
  }
  error.value = '';
  uploading.value = true;
  try {
    imageUrl.value = await uploadParkPhotoFile(file);
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err);
  } finally {
    uploading.value = false;
  }
}

function dropImage() {
  imageUrl.value = '';
  error.value = '';
}

async function submit() {
  if (!canSubmit.value) return;
  submitting.value = true;
  error.value = '';
  try {
    const text = description.value.trim();
    done.value = await submitParkPhoto({
      parkId: props.parkId,
      imageUrl: imageUrl.value,
      ...(text ? { description: text } : {}),
    });
  } catch (err) {
    // 后端那句业务提示（「你还有 9 张投稿在等审核」这种）比状态码准，原样印出来
    error.value = err instanceof Error ? err.message : String(err);
  } finally {
    submitting.value = false;
  }
}

/** 再投一张：回到挑图那一屏，图注也清掉（一家园区连投几张是常事，别让人关一层再开一层） */
function again() {
  done.value = null;
  imageUrl.value = '';
  description.value = '';
  error.value = '';
}
</script>

<template>
  <ModalOverlay @close="emit('close')">
    <div
      role="dialog"
      aria-modal="true"
      :aria-label="t.title"
      class="max-h-[86vh] w-full max-w-md overflow-y-auto rounded-2xl border border-gray-200 bg-white p-5 shadow-xl dark:border-gray-700 dark:bg-gray-800"
    >
      <!-- 一、挑图那一屏 -->
      <template v-if="!done">
        <h3 class="text-base font-semibold">{{ t.title }}</h3>
        <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">{{ t.tip }}</p>

        <input ref="inputRef" type="file" accept="image/*" class="sr-only" :disabled="uploading" @change="onPick" />

        <div class="mt-4 flex items-start gap-3">
          <img
            v-if="imageUrl"
            :src="imageUrl"
            :alt="t.preview"
            class="h-28 w-28 shrink-0 rounded-lg border border-gray-200 object-cover dark:border-gray-700"
          />
          <button
            v-else
            type="button"
            class="flex h-28 w-28 shrink-0 flex-col items-center justify-center gap-1 rounded-lg border border-dashed border-gray-300 text-xs text-gray-500 hover:bg-gray-100 dark:border-gray-600 dark:hover:bg-gray-700"
            :disabled="uploading"
            @click="pick"
          >
            <span aria-hidden="true" class="text-2xl leading-none">+</span>
            {{ uploading ? t.uploading : t.pick }}
          </button>
          <div class="flex min-w-0 flex-col gap-2">
            <button
              type="button"
              class="self-start rounded-lg border border-gray-200 px-3 py-1.5 text-sm hover:bg-gray-100 dark:border-gray-600 dark:hover:bg-gray-700"
              :disabled="uploading"
              @click="pick"
            >
              {{ imageUrl ? t.repick : t.pickAgain }}
            </button>
            <button
              v-if="imageUrl"
              type="button"
              class="self-start text-sm text-gray-500 hover:underline dark:text-gray-400"
              @click="dropImage"
            >
              {{ t.remove }}
            </button>
          </div>
        </div>

        <label class="mt-4 flex flex-col gap-1 text-sm">
          <span class="text-gray-600 dark:text-gray-300">
            {{ t.desc }}
            <span class="ml-1 text-xs text-gray-400">{{ fill(t.descCount, { n: DESC_MAX }) }}</span>
          </span>
          <input
            v-model="description"
            type="text"
            :maxlength="DESC_MAX"
            :placeholder="t.descPlaceholder"
            class="rounded-lg border border-gray-300 px-3 py-1.5 text-sm outline-none focus:border-blue-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
          />
        </label>

        <p v-if="error" class="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
          {{ error }}
        </p>

        <div class="mt-5 flex items-center justify-end gap-2">
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
      </template>

      <!-- 二、投成那一屏：图 + 一句「在等审核」 -->
      <template v-else>
        <h3 class="text-base font-semibold">{{ t.doneTitle }}</h3>
        <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">{{ t.doneTip }}</p>
        <div class="mt-4 flex items-center gap-3">
          <img
            :src="done.imageUrl"
            :alt="done.description || t.preview"
            class="h-20 w-20 shrink-0 rounded-lg border border-gray-200 object-cover dark:border-gray-700"
          />
          <span class="flex min-w-0 flex-col text-sm">
            <span v-if="done.description" class="truncate">{{ done.description }}</span>
            <span class="text-xs text-gray-500 dark:text-gray-400">{{ t.doneId }}：{{ done.id }}</span>
          </span>
        </div>
        <div class="mt-5 flex items-center justify-end gap-2">
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
      </template>
    </div>
  </ModalOverlay>
</template>
