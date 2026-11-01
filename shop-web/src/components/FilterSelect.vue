<script setup lang="ts">
/**
 * 可输入的筛选下拉（首页那三档：分类 / 品牌 / 门店，2026-10-05 从原生 select 换过来）。
 *
 * 为什么不是 flowbite-vue 的 FwbSelect：那颗就是原生 select 的样式壳，候选上百条时既不能输入过滤、
 * 也没地方摆搜索框（库里也没有 combobox/listbox 这种控件）。所以自己搭，样式跟这一页其余控件对齐。
 *
 * 顺带修掉原生 select 那一格的老毛病：它的宽度是「按当前这一项的内容」收缩的，
 * 名字一长，文字就和右边那颗下拉箭头挤在一块 —— 这里那颗箭头是 `shrink-0` 的 svg，
 * 文字 `truncate`，trigger 钉死宽度，两头永远隔着一个 gap-2。
 *
 * 0 当「不限」（和父级那三格同一个哨兵），所以「全部XX」就是列表的第一行，不另开清除按钮。
 * 候选的层级缩进由父级带进来（分类那档 label 前面是不间断空格），这里只负责显示。
 */
import { computed, nextTick, onUnmounted, ref, watch } from 'vue';
import { useI18n } from '@/i18n';

interface Option {
  id: number;
  label: string;
}

const props = defineProps<{
  /** 选中的 id，0 = 不限 */
  modelValue: number;
  /** 候选（不含「全部」那一行） */
  options: Option[];
  /** 这一档叫什么，用来拼「全部XX」和输入框的提示 */
  label: string;
  /** 请求期间禁用：免得两条列表请求在路上抢着画结果 */
  disabled?: boolean;
}>();

const emit = defineEmits<{ (event: 'update:modelValue', value: number): void }>();

const { d, fill } = useI18n();

/** 触发按钮那一格（面板是靠它定位的，点外面收起来也按它判） */
const root = ref<HTMLElement | null>(null);
const inputEl = ref<HTMLInputElement | null>(null);

const open = ref(false);
const keyword = ref('');
/** 键盘高亮落在列表第几行，-1 = 还没用键盘动过 */
const cursor = ref(0);
/** 选过时记一下名字：父级也可能从外头改 modelValue（活动轮播点分类那种），那种情况照候选里捞 */
const pickedLabel = ref('');

const allRow = computed<Option>(() => ({ id: 0, label: fill(d.filter.all, { label: props.label }) }));

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) return props.options;
  return props.options.filter((one) => one.label.toLowerCase().includes(kw));
});

/** 「全部」永远在第一行，不参与过滤 */
const rows = computed<Option[]>(() => [allRow.value, ...filtered.value]);

const selectedLabel = computed(() => {
  if (!props.modelValue) return allRow.value.label;
  return props.options.find((one) => one.id === props.modelValue)?.label || pickedLabel.value || allRow.value.label;
});

function show() {
  open.value = true;
  keyword.value = '';
  cursor.value = 0;
  void nextTick(() => inputEl.value?.focus());
  document.addEventListener('mousedown', onDocMouseDown, true);
}

function close() {
  open.value = false;
  document.removeEventListener('mousedown', onDocMouseDown, true);
}

function toggle() {
  if (props.disabled) return;
  if (open.value) close();
  else show();
}

function onDocMouseDown(event: MouseEvent) {
  if (root.value && !root.value.contains(event.target as Node)) close();
}

function pick(row: Option) {
  pickedLabel.value = row.id ? row.label : '';
  emit('update:modelValue', row.id);
  close();
}

function moveCursor(delta: number) {
  const last = rows.value.length - 1;
  const target = cursor.value + delta;
  cursor.value = target < 0 ? last : target > last ? 0 : target;
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    close();
    return;
  }
  if (event.key === 'ArrowDown') {
    event.preventDefault();
    moveCursor(1);
  } else if (event.key === 'ArrowUp') {
    event.preventDefault();
    moveCursor(-1);
  } else if (event.key === 'Enter') {
    event.preventDefault();
    const row = rows.value[cursor.value];
    if (row) pick(row);
  }
}

// 请求期间被禁用了就把面板收掉：按钮点不动，浮层还开着会是个收不回去的空框
watch(
  () => props.disabled,
  (disabled) => {
    if (disabled && open.value) close();
  },
);

onUnmounted(() => {
  document.removeEventListener('mousedown', onDocMouseDown, true);
});
</script>

<template>
  <div ref="root" class="relative">
    <button
      type="button"
      :disabled="disabled"
      :aria-label="fill(d.filter.filterBy, { label })"
      :aria-expanded="open"
      class="flex w-40 items-center justify-between gap-2 rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-left text-sm text-gray-700 outline-none focus:border-blue-500 disabled:opacity-60 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-200"
      @click="toggle"
    >
      <span class="truncate" :title="selectedLabel">{{ selectedLabel }}</span>
      <svg
        class="h-4 w-4 shrink-0 text-gray-400"
        :class="open ? 'rotate-180' : ''"
        viewBox="0 0 20 20"
        fill="currentColor"
        aria-hidden="true"
      >
        <path
          fill-rule="evenodd"
          d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 11.17l3.71-3.94a.75.75 0 1 1 1.08 1.04l-4.25 4.5a.75.75 0 0 1-1.08 0l-4.25-4.5a.75.75 0 0 1 .02-1.06Z"
          clip-rule="evenodd"
        />
      </svg>
    </button>

    <!-- overflow-hidden 只为了裁圆角：行是方的，最后一行高亮时会把外面那圈 rounded-lg 顶出两个方角。
         这一层不能同时再给高度上限 —— 一加 max-h 这条 overflow 就变回滚动条 -->
    <div
      v-if="open"
      class="absolute left-0 top-full z-20 mt-1 w-64 overflow-hidden rounded-lg border border-gray-200 bg-white shadow-lg dark:border-gray-700 dark:bg-gray-900"
      @keydown="onKeydown"
    >
      <input
        ref="inputEl"
        v-model="keyword"
        type="text"
        :aria-label="fill(d.filter.typeToFilter, { label })"
        :placeholder="fill(d.filter.typeToFilter, { label })"
        class="w-full rounded-t-lg border-b border-gray-100 bg-transparent px-3 py-2 text-sm text-gray-700 outline-none dark:border-gray-700 dark:text-gray-200"
      />
      <!-- 候选整列铺开、不限高也不滚：浮层里那条滚动条他不要，所以靠收行距（py-1）把整块压下来；
           行上也不写 truncate —— 名字长就多撑一行，别裁 -->
      <ul class="py-1">
        <li v-for="(one, index) in rows" :key="one.id">
          <button
            type="button"
            class="block w-full px-3 py-1 text-left text-sm leading-5 text-gray-700 dark:text-gray-200"
            :class="index === cursor ? 'bg-blue-50 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300' : 'hover:bg-gray-50 dark:hover:bg-gray-800'"
            @click="pick(one)"
            @mouseenter="cursor = index"
          >
            {{ one.label }}
          </button>
        </li>
        <li v-if="!filtered.length" class="px-3 py-2 text-sm text-gray-500 dark:text-gray-400">
          {{ fill(d.filter.noMatch, { label }) }}
        </li>
      </ul>
    </div>
  </div>
</template>
