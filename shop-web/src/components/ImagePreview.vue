<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useI18n } from '@/i18n';
import ModalOverlay from '@/components/ModalOverlay.vue';

/**
 * 点一张图开一层看原图：个人中心这一页三处头像（账号那一排、基础信息那一块、改资料弹窗里那颗）、
 * 顶上那一排挂件徽章和评论区那组图要的是同一个动作，所以是公共组件。
 *
 * 为什么手写这一层：flowbite-vue 没有图片灯箱，而这一层只要「遮罩 + 居中原图 + 三种关法」
 *（点遮罩、右上角那颗 X、按 esc），为一个预览引一套依赖不值当（口径同 MapPicker 那次移植）。
 * 遮罩本身用的是公共的 ModalOverlay，锁滚动跟着那一层走。
 *
 * overlay-class 传得跟默认那一条不一样是有原因的：要压在 z-50 的弹窗上面（改资料那颗就是在自己表单里点开），
 * 而且底下没有白卡，这一层自己就是那个对话框，所以 aria-label 交给 ModalOverlay 挂 role。
 * 只给 URL 不给别的（文件、尺寸都不在这儿算）；传了 list + index 就多出左右两颗和 ← → 两个键
 *（评论区一条能带九张图，看完一张得能翻下一张，不用关一层再开一层），头像那几处只传 src 就还是单张。
 * caption 是可选的那一句说明（挂件点开要连带看徽章文字），头像和评论图那几处不传，这一层就不铺底下那一行。
 */
const props = defineProps<{
  src?: string;
  alt?: string;
  caption?: string;
  /** 一组图（评论那种九宫格）：给了它就看这一组，src 那格留空 */
  list?: string[];
  /** 从第几张开始看 */
  index?: number;
}>();

const { d } = useI18n();

const emit = defineEmits<{ close: [] }>();

const urls = computed(() => (props.list?.length ? props.list : props.src ? [props.src] : []));
const current = ref(Math.min(Math.max(props.index ?? 0, 0), Math.max(urls.value.length - 1, 0)));
const show = computed(() => urls.value[current.value] ?? '');
/** 一张图就没得翻，左右两颗和键都不出现 */
const switchable = computed(() => urls.value.length > 1);

function step(delta: number) {
  if (!switchable.value) return;
  current.value = (current.value + delta + urls.value.length) % urls.value.length;
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    emit('close');
    return;
  }
  if (event.key === 'ArrowLeft') step(-1);
  if (event.key === 'ArrowRight') step(1);
}

/** 键盘监听挂在这一层挂起来的时候、摘在它卸载的时候（不摸 document 于 setup 之外，岛里照旧避 hydration） */
onMounted(() => document.addEventListener('keydown', onKeydown));
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown));
</script>

<template>
  <ModalOverlay
    :aria-label="props.alt || d.preview.image"
    overlay-class="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 p-6"
    @close="emit('close')"
  >
    <img :src="show" :alt="props.alt || d.preview.image" class="max-h-full max-w-full rounded-lg object-contain" />
    <!-- 说明挂在底下而不是跟图排一列：这一层是 flex 居中，多塞一个兄弟会把图往左挤 -->
    <p v-if="props.caption" class="absolute inset-x-6 bottom-4 text-center text-sm text-white/85">
      {{ props.caption }}
    </p>

    <button
      v-if="switchable"
      type="button"
      :aria-label="d.preview.prev"
      class="absolute left-3 top-1/2 -translate-y-1/2 rounded-full bg-white/10 p-2 text-white/80 hover:bg-white/20 hover:text-white"
      @click="step(-1)"
    >
      <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
        <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7" />
      </svg>
    </button>
    <button
      v-if="switchable"
      type="button"
      :aria-label="d.preview.next"
      class="absolute right-14 top-1/2 -translate-y-1/2 rounded-full bg-white/10 p-2 text-white/80 hover:bg-white/20 hover:text-white"
      @click="step(1)"
    >
      <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
        <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
      </svg>
    </button>

    <button
      type="button"
      :aria-label="d.preview.close"
      class="absolute right-4 top-4 rounded p-2 text-white/80 hover:text-white"
      @click="emit('close')"
    >
      <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
        <path stroke-linecap="round" d="M6 6l12 12M18 6L6 18" />
      </svg>
    </button>
  </ModalOverlay>
</template>
