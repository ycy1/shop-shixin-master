<script setup lang="ts">
import { computed } from 'vue';
import { FwbButton, FwbModal } from 'flowbite-vue';
import { useI18n } from '@/i18n';
import { useBodyScrollLock } from '@/lib/scroll-lock';

/**
 * 破坏性动作的确认框：谁都要用一颗，所以是公共组件（个人中心那一页三处共用：退出登录、删地址、删收藏）。
 *
 * 为什么不用 window.confirm：那是浏览器系统级的框，跟页面两套样式、暗色模式不跟着翻，
 * 而且它把返回值交给调用方自己去比，两句话的动作会写成 if (!confirm(...)) return 那种形状。
 *
 * 这一版（flowbite-vue 0.4.0）的 FwbModal 没有 modelValue，也没有 title —— 开合看的是「挂没挂载」，
 * 所以父级用 v-if 控制（和这一页另外三个弹窗同一个用法）。它自己那三种关法（右上角 X、点遮罩、按 esc）
 * 最后都会抛成 close（点遮罩还会先多抛一条 click:outside），所以只接 close 这一条就够，换成 cancel 抛给父级。
 * 内容必须走 header / body / footer 三个具名插槽：这个组件没有默认插槽，塞进去不渲染，框就是一片空白。
 */
const props = withDefaults(
  defineProps<{
    title: string;
    /** 要确认的那一句：把动的到底是哪一条念出来，点错的人也能看清自己按了什么 */
    message: string;
    confirmText?: string;
    cancelText?: string;
    /** 处理中：两颗按钮一起按住，别让连点把同一条删两次 */
    busy?: boolean;
  }>(),
  {
    // 兜底话不在这里写死中文：withDefaults 在组件定义期就定了，认不出当前语种，
    // 所以默认值落成空串，真正显示哪句由下面按语种现取（调用方传了照调用方的）
    confirmText: '',
    cancelText: '',
    busy: false,
  },
);

const emit = defineEmits<{ confirm: []; cancel: [] }>();

const { d, fill } = useI18n();

/** 只有这一颗没走公共遮罩：那一层在 flowbite 的 FwbModal 自己手里，锁滚动只能在这儿钉一下 */
useBodyScrollLock();

const cancelLabel = computed(() => props.cancelText || d.common.cancel);

/** 处理中那句跟着动词走：传「删除」就是「删除中…」，不用调用方再给一套文案 */
const confirmLabel = computed(() => {
  const text = props.confirmText || d.common.confirm;
  return props.busy ? fill(d.confirm.processing, { text }) : text;
});
</script>

<template>
  <!-- persistent 默认就是 false：点遮罩、按 esc 都能关，右上角也会挂出那颗 X -->
  <FwbModal @close="emit('cancel')">
    <template #header>
      <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ title }}</h3>
    </template>

    <template #body>
      <p class="text-sm leading-6 text-gray-600 dark:text-gray-300">{{ message }}</p>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <FwbButton type="button" color="alternative" :disabled="busy" @click="emit('cancel')">
          {{ cancelLabel }}
        </FwbButton>
        <FwbButton type="button" color="red" :disabled="busy" @click="emit('confirm')">
          {{ confirmLabel }}
        </FwbButton>
      </div>
    </template>
  </FwbModal>
</template>
