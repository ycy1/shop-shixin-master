<script setup lang="ts">
/**
 * 发布广场消息那张弹窗：正文这一格是富文本（和后台活动管理同款 WangEditor），图片直接插进正文里，
 * 所以后端正文只收一串 HTML —— 不按「图文/纯文」分档、也不限张数。
 * 正文以外还有一格广场消息图：可以选多张、一张一颗先传上去（uploadPlazaImage），回来的地址按顺序用英文逗号
 * 拼在 plazaImage 上；卡片配图取第一张、详情页在正文下方整组铺开。不选就是这条没图，服务端不会从正文里抓图来补。
 *
 * 编辑器里传图不照抄后台那一份（后台拿 VITE_APP_BASE_API 自己拼 URL 和 Authorization），
 * 这里交给 customUpload 走本站那条 apiUpload：baseURL、token、拆信封都由 lib/api 那一层管，
 * 前端不用再多配一个后端地址环境变量。
 *
 * 发完不会立刻上广场：落库一律是「待审核」，后台判通过的那一刻才出现在广场上并给分，
 * 所以成功后先把这句话念清楚（pendingTip），再把弹窗关掉、让父级重捞列表。
 *
 * 递进来一个 post 就是「改自己发过的这一条」（个人中心「广场消息」那一档的行内「修改」）：
 * 标题/正文/图片/关联四格预填、提交改走 updatePlaza、顶上标题和底下那句提示跟着换。post 要带 content ——
 * 列表那一条接口不取正文列，所以父级点开这颗之前得先读一次详情。
 * 图片那一格预填尤其不能省：服务端每次都照表单写这一格，不带上来就等于提交那一下把这些图清掉了。
 *
 * 关联对象那一格不是必填：默认「无关联」，选了四档之一就要按名称搜出候选、点一下记下 id 和名字，
 * 光选类型没挑对象不给提交（后端那一条也是这么判的，在这儿拦一句省得跑一趟）。
 */
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue';
import type { Component } from 'vue';
import '@wangeditor/editor/dist/css/style.css';
import ModalOverlay from '@/components/ModalOverlay.vue';
import { apiUpload } from '@/lib/api';
import { activityLabel, fetchActivityPage } from '@/lib/activities';
import {
  PLAZA_PUBLISH_POINTS,
  PLAZA_RELATION_ACTIVITY,
  PLAZA_RELATION_BRAND,
  PLAZA_RELATION_NONE,
  PLAZA_RELATION_PRODUCT,
  PLAZA_RELATION_STORE,
  plazaImages,
  publishPlaza,
  updatePlaza,
  uploadPlazaImage,
  type PlazaMessage,
  type PlazaPublishForm,
} from '@/lib/plaza';
import { fetchProducts } from '@/lib/products';
import { fetchBrands, fetchStores } from '@/lib/search';
import { useI18n } from '@/i18n';

/** 关联候选那一行的形状：四张表的名字/图各叫各的，捞出来先拢成这一份 */
interface RelationOption {
  id: number;
  name: string;
  image: string | null;
}

const props = defineProps<{ post?: PlazaMessage | null }>();
const emit = defineEmits<{ close: []; published: []; saved: [] }>();

const { d, fill } = useI18n();

/** 标题/正文那两格在编辑器挂载前就填好（WangEditor 吃的是 v-model 的初值） */
const title = ref(props.post?.title ?? '');
const html = ref(props.post?.content ?? '');
const submitting = ref(false);
const error = ref('');

/** 广场消息图：编辑态一定把原来那一串带上来（服务端照表单写这一格，不带就是清掉） */
const images = ref<string[]>(plazaImages(props.post));
const imageUploading = ref(false);
/** 张数上限和服务端那一道闸同一个数（PlazaContentUtils） */
const MAX_IMAGES = 9;
/** 满了就不给再点：那颗「再加几张」的格子置灰、文件框也 disable */
const imageFull = computed(() => images.value.length >= MAX_IMAGES);

const relationType = ref(props.post?.relationType ?? PLAZA_RELATION_NONE);
const relationId = ref<number | null>(props.post?.relationId ?? null);
/** 已关联那个对象的名字：只用来回显这一句，落库靠的是 relationId */
const relationName = ref(props.post?.relationName ?? '');
const keyword = ref('');
const candidates = ref<RelationOption[]>([]);
const searching = ref(false);
const searchError = ref('');
/** 搜过一次的记号：没搜过就铺「没搜到」那一行，点开弹窗第一眼会看到一句没头没尾的话 */
const searched = ref(false);

const editing = computed(() => props.post != null);
/** 类型那一格叫什么：下拉候选、搜索框的提示语都读它 */
const relationLabel = computed(() => d.enum.plazaRelation[relationType.value] || d.enum.other);
/** 已经挑中一个对象（编辑模式带进来的那一格也算）：这时不再铺搜索框和候选 */
const picked = computed(() => relationId.value != null);

const editorRef = shallowRef<{ destroy: () => void } | null>(null);

/**
 * 编辑器和工具栏这两颗要等挂载后再动态取：岛在服务端也有一趟渲染，而 wangEditor 那份 dist 一加载就往
 * navigator 上写东西（node 里那是只读 getter），写在顶上 import 就等于让 SSR 那趟去撞它。
 * 取到之前这一格先铺一句「加载中」，工具栏和编辑器都只在这之后挂 —— 正文的初值早于挂载填进 html，不丢。
 */
const Editor = shallowRef<Component | null>(null);
const Toolbar = shallowRef<Component | null>(null);
onMounted(() => {
  import('@wangeditor/editor-for-vue').then((m) => {
    Editor.value = m.Editor;
    Toolbar.value = m.Toolbar;
  });
});

/**
 * mode 用的 simple（工具栏那一排要短），而 simple 那一档默认不给文字选中的浮条 —— 选中一段字什么浮不出来就是这里来的。
 * 所以把 hoverbarKeys 里的 text 那一档照抄后台（后台是 default 模式自带的同一份），选中文字就能设标题/加粗/颜色/清格式。
 */
const editorConfig = {
  placeholder: d.plazaPublish.content,
  hoverbarKeys: {
    text: {
      menuKeys: ['headerSelect', 'insertLink', 'bulletedList', '|', 'bold', 'through', 'color', 'bgColor', 'clearStyle'],
    },
  },
  MENU_CONF: {
    uploadImage: {
      fieldName: 'file',
      maxFileSize: 5 * 1024 * 1024,
      maxNumberOfFiles: 9,
      acceptedTypes: ['image/png', 'image/jpeg', 'image/gif', 'image/webp'],
      /** 交给我们自己传：编辑器内置那套是 fetch 拼 URL，本站的 baseURL/token/信封都在 lib/api 里 */
      async customUpload(file: File, insertFn: (url: string, alt: string, href: string) => void) {
        try {
          const url = await apiUpload<string>('/api/file/uploadImage', file, { source: 'plaza' }, undefined, { timeout: 60000 });
          insertFn(url, '', url);
        } catch (err) {
          error.value = (err as Error).message;
        }
      },
    },
  },
};

/** 编辑器里那套工具栏不动默认配置，只把实例记下来（销毁要用它，忘了记就会 leak） */
function onCreated(editor: { destroy: () => void }) {
  editorRef.value = editor;
}

onBeforeUnmount(() => {
  editorRef.value?.destroy();
});

/** 空正文的判据：编辑器清空后给的是 `<p><br></p>`，光比字符串会漏掉「只剩几个空段落」；纯图片那种要有图才算有内容 */
function hasContent() {
  const text = html.value.replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim();
  if (text) return true;
  return /<img\b/i.test(html.value);
}

/**
 * 四档各走自己那条前台读接口（商品/门店/品牌/活动），一律只捞第一页十条：
 * 这一格是「挑中一个对象」，不是让人在这儿浏览一个列表，十条挑不到就该换个词。
 * 关键字空着也发请求 —— 那就是给这一档的第一屏候选。
 */
async function searchRelation() {
  if (searching.value) return;
  searching.value = true;
  searchError.value = '';
  const kw = keyword.value.trim() || undefined;
  try {
    if (relationType.value === PLAZA_RELATION_PRODUCT) {
      const page = await fetchProducts({ keyword: kw, pageNum: 1, pageSize: 10 });
      candidates.value = page.records.map((one) => ({ id: one.id, name: one.productName, image: one.mainImage }));
    } else if (relationType.value === PLAZA_RELATION_STORE) {
      const page = await fetchStores({ keyword: kw, pageNum: 1, pageSize: 10 });
      candidates.value = page.records.map((one) => ({ id: one.id, name: one.storeName, image: one.logo }));
    } else if (relationType.value === PLAZA_RELATION_BRAND) {
      const page = await fetchBrands({ keyword: kw, pageNum: 1, pageSize: 10 });
      candidates.value = page.records.map((one) => ({ id: one.id, name: one.brandName, image: one.logo }));
    } else if (relationType.value === PLAZA_RELATION_ACTIVITY) {
      const page = await fetchActivityPage(1, 10, kw);
      candidates.value = page.records.map((one) => ({ id: one.id, name: activityLabel(one, d), image: one.image }));
    } else {
      candidates.value = [];
    }
    searched.value = true;
  } catch (err) {
    searchError.value = (err as Error).message;
    candidates.value = [];
  } finally {
    searching.value = false;
  }
}

function pickRelation(one: RelationOption) {
  relationId.value = one.id;
  relationName.value = one.name;
  candidates.value = [];
  searched.value = false;
}

/** 换类型和取消关联走同一份：四张表的 id 会撞号，上一档挑的那个留在这一档名下就是在关联错东西 */
function resetRelation() {
  relationId.value = null;
  relationName.value = '';
  candidates.value = [];
  searched.value = false;
  searchError.value = '';
}

/**
 * 选图就先传（顺序同头像那一颗：一次一张，传回来的地址排进数组，提交时拼成逗号串落 plaza_image）。
 * input.value 清空是为了「同一批图再选一次」也能触发 change；只收 image/*，压缩、存储平台都在后端，
 * 这一格只管把回来的 URL 摆进缩略图。挑多了不报错，只把超出上限的那几张丢掉并念一句「最多 N 张」。
 */
async function onPickImages(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  input.value = '';
  if (!files.length) return;
  if (files.some((file) => !file.type.startsWith('image/'))) {
    error.value = d.plazaPublish.imageOnlyImage;
    return;
  }
  const room = MAX_IMAGES - images.value.length;
  if (room <= 0) {
    error.value = fill(d.plazaPublish.imageMax, { n: MAX_IMAGES });
    return;
  }
  error.value = '';
  imageUploading.value = true;
  try {
    // 串行传：这一格一次可能挑好几张，并发打全站唯一那个收文件的口，dev 上容易排队乱掉、也难说哪颗失败了
    for (const file of files.slice(0, room)) {
      const url = await uploadPlazaImage(file);
      if (!images.value.includes(url)) images.value.push(url);
    }
    if (files.length > room) error.value = fill(d.plazaPublish.imageMax, { n: MAX_IMAGES });
  } catch (err) {
    error.value = (err as Error).message;
  } finally {
    imageUploading.value = false;
  }
}

/** 移除某一张：只是把这一格从数组里摘掉，不删存储上那个文件（正文里可能还有同一张图在用） */
function removeImage(url: string) {
  images.value = images.value.filter((one) => one !== url);
}

async function submit() {
  if (submitting.value) return;
  if (!hasContent()) {
    error.value = d.plazaPublish.contentRequired;
    return;
  }
  if (relationType.value !== PLAZA_RELATION_NONE && !relationId.value) {
    error.value = d.plazaPublish.relationRequired;
    return;
  }
  error.value = '';
  submitting.value = true;
  try {
    // 没填标题就不给这一格（不给空串），免得库里留一条空标题的行
    const form: PlazaPublishForm = { content: html.value };
    const name = title.value.trim();
    if (name) form.title = name;
    if (images.value.length) form.plazaImage = images.value.join(',');
    if (relationType.value !== PLAZA_RELATION_NONE && relationId.value) {
      form.relationType = relationType.value;
      form.relationId = relationId.value;
    }
    if (props.post) {
      await updatePlaza({ ...form, id: props.post.id });
      emit('saved');
      return;
    }
    await publishPlaza(form);
    emit('published');
  } catch (err) {
    error.value = (err as Error).message;
    submitting.value = false;
  }
}
</script>

<template>
  <ModalOverlay @close="emit('close')">
    <div
      role="dialog"
      aria-modal="true"
      :aria-label="editing ? d.plazaPublish.dialogEdit : d.plazaPublish.dialog"
      class="flex max-h-[90vh] w-full max-w-2xl flex-col overflow-hidden rounded-lg bg-white shadow-lg dark:bg-gray-800"
    >
      <div class="flex items-center justify-between border-b border-gray-200 px-4 py-3 dark:border-gray-700">
        <h2 class="text-base font-semibold">{{ editing ? d.plazaPublish.dialogEdit : d.plazaPublish.dialog }}</h2>
        <button type="button" class="text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200" @click="emit('close')">
          ✕
        </button>
      </div>

      <div class="min-h-0 flex-1 overflow-y-auto px-4 py-3">
        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300" for="plaza-title">{{ d.plazaPublish.title }}</label>
        <input
          id="plaza-title"
          v-model="title"
          type="text"
          maxlength="100"
          :placeholder="d.plazaPublish.titlePlaceholder"
          class="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-blue-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
        />

        <!-- 广场消息图：正文之外的一组配图（一次可选几张，串行传上去，地址排进数组、提交时拼成逗号串落 plaza_image）。
             卡片配图读第一张，详情页在正文下方整组铺开。方框、右上角那枚 ✕ 和「再加几张」那颗格子的几何都在 global.css 的 .plaza-thumb：
             这一颗是 island 里新加的形状，写成原子类换页那一趟会掉（口径同上面编辑器那格）。 -->
        <p class="mt-4 flex items-baseline gap-2 text-sm font-medium text-gray-700 dark:text-gray-300">
          {{ d.plazaPublish.image }}
          <span class="text-xs font-normal text-gray-500 dark:text-gray-400">{{ images.length }}/{{ MAX_IMAGES }}</span>
        </p>
        <div class="mt-1 flex flex-wrap gap-2">
          <div v-for="url in images" :key="url" class="plaza-thumb">
            <img :src="url" alt="" />
            <button type="button" class="plaza-thumb-remove" :aria-label="d.plazaPublish.imageRemove" @click="removeImage(url)">
              ✕
            </button>
          </div>

          <label class="plaza-thumb plaza-thumb-add" :class="imageFull ? 'plaza-thumb-off' : ''">
            <span>{{ imageUploading ? d.common.loading : images.length ? d.plazaPublish.imageChange : d.plazaPublish.imageUpload }}</span>
            <input type="file" accept="image/*" multiple class="sr-only" :disabled="imageUploading || imageFull" @change="onPickImages" />
          </label>
        </div>
        <p v-if="!images.length && !imageUploading" class="mt-1 text-xs text-gray-500 dark:text-gray-400">
          {{ d.plazaPublish.imageEmpty }}
        </p>

        <!-- 关联对象：类型只有四档，用原生 select 就够（候选是四张表各捞十条，得按名称搜，不是 FilterSelect 那种本地过滤） -->
        <p class="mt-4 text-sm font-medium text-gray-700 dark:text-gray-300">{{ d.plazaPublish.relation }}</p>
        <div class="mt-1 flex flex-wrap items-center gap-2">
          <select
            v-model.number="relationType"
            :aria-label="d.plazaPublish.relationType"
            class="w-28 rounded-lg border border-gray-300 px-2 py-1.5 text-sm outline-none focus:border-blue-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            @change="resetRelation"
          >
            <option v-for="(label, index) in d.enum.plazaRelation" :key="index" :value="index">{{ label }}</option>
          </select>

          <template v-if="picked">
            <span class="text-sm text-gray-700 dark:text-gray-300">
              {{ fill(d.plazaPublish.relationPicked, { name: relationName || `${relationId}` }) }}
            </span>
            <button type="button" class="text-sm text-blue-600 hover:underline dark:text-blue-400" @click="resetRelation">
              {{ d.plazaPublish.relationClear }}
            </button>
          </template>

          <template v-else-if="relationType">
            <input
              v-model="keyword"
              type="text"
              :placeholder="fill(d.plazaPublish.relationKeyword, { label: relationLabel })"
              class="w-56 rounded-lg border border-gray-300 px-3 py-1.5 text-sm outline-none focus:border-blue-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
              @keydown.enter.prevent="searchRelation"
            />
            <button
              type="button"
              class="rounded-lg border border-gray-300 px-3 py-1.5 text-sm hover:bg-gray-100 disabled:opacity-60 dark:border-gray-600 dark:hover:bg-gray-700"
              :disabled="searching"
              @click="searchRelation"
            >
              {{ searching ? d.common.loading : d.plazaPublish.relationSearch }}
            </button>
          </template>
        </div>

        <!-- 候选只捞十条、点一下就收进上面那一行；这一层自己滚，免得十条把弹窗顶得老高 -->
        <ul
          v-if="candidates.length"
          class="mt-2 max-h-44 divide-y divide-gray-100 overflow-y-auto rounded-lg border border-gray-200 dark:divide-gray-700 dark:border-gray-700"
        >
          <li v-for="one in candidates" :key="one.id">
            <button
              type="button"
              class="flex w-full items-center gap-2 px-2 py-1.5 text-left text-sm hover:bg-gray-50 dark:hover:bg-gray-700"
              @click="pickRelation(one)"
            >
              <img v-if="one.image" :src="one.image" alt="" loading="lazy" class="h-8 w-8 shrink-0 rounded object-cover" />
              <span class="truncate">{{ one.name }}</span>
            </button>
          </li>
        </ul>
        <p v-else-if="searched && relationType && !searching" class="mt-2 text-xs text-gray-500 dark:text-gray-400">
          {{ d.plazaPublish.relationEmpty }}
        </p>
        <p v-if="searchError" class="mt-2 text-xs text-red-600 dark:text-red-400">{{ searchError }}</p>

        <p class="mt-4 text-sm font-medium text-gray-700 dark:text-gray-300">{{ d.plazaPublish.content }}</p>
        <!-- 这一格外层不能带 overflow-hidden，编辑器那一格也不能：选中文字浮出来的那一排和它的下拉列表要往格子外面铺（几何见 global.css 的 .plaza-editor） -->
        <div class="plaza-editor mt-1 rounded-lg border border-gray-300 dark:border-gray-600">
          <template v-if="Editor">
            <component :is="Toolbar" :editor="editorRef" :default-config="{}" mode="simple" class="border-b border-gray-200 dark:border-gray-700" />
            <component
              :is="Editor"
              v-model="html"
              :default-config="editorConfig"
              mode="simple"
              style="min-height: 320px"
              @on-created="onCreated"
            />
          </template>
          <p v-else class="flex items-center justify-center text-sm text-gray-500 dark:text-gray-400" style="height: 320px">
            {{ d.common.loading }}
          </p>
        </div>

        <p v-if="error" class="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">{{ error }}</p>
        <p class="mt-3 text-xs text-gray-500 dark:text-gray-400">
          {{ editing ? d.plazaPublish.editTip : fill(d.plazaPublish.pendingTip, { n: PLAZA_PUBLISH_POINTS }) }}
        </p>
      </div>

      <div class="flex items-center justify-end gap-2 border-t border-gray-200 px-4 py-3 dark:border-gray-700">
        <button
          type="button"
          class="rounded-lg border border-gray-300 px-4 py-2 text-sm hover:bg-gray-100 dark:border-gray-600 dark:hover:bg-gray-700"
          :disabled="submitting"
          @click="emit('close')"
        >
          {{ d.common.cancel }}
        </button>
        <button
          type="button"
          class="rounded-lg bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 disabled:opacity-60"
          :disabled="submitting"
          @click="submit"
        >
          {{ submitting ? d.plazaPublish.submitting : editing ? d.common.save : d.plazaPublish.fab }}
        </button>
      </div>
    </div>
  </ModalOverlay>
</template>
