<script setup lang="ts">
/**
 * 评论区：商品/门店/品牌/广场四处详情页底下共用这一块，props 那两格定住「评的是哪一个」。
 *
 * 一列指四张表（business_id 由 comment_type 决定指向哪一张），所以两格必须一起给，少一格后端直接报。
 * 读走自己那一端那条 /api/web（未登录也发得出去，评论区不能只有登录的人看得见），
 * 发表/点赞/删除三颗动作走 /api/comm —— 同一个动作在 Web 和 App 各写一遍判据早晚对不上。
 *
 * 回复层数不限（回复的回复还能再回复），所以整棵评论树在这里铺平成一条串：每条紧跟它的父行，
 * 缩进只到第二格就封顶，再往下靠「回复 @某人」那一句认对象（它读接口联出来的 replyNickname）。
 * 发表/回复那一格也在这条串里：没在回复时排最前，点「回复」以后它搬到那一条的下一行、跟着同级缩进
 * —— 框子钉在顶上的话，滚到深处点回复就看不见输入框了（位置见 flow）。
 *
 * 表情和图片都跟着这一条链走：emoji 就是插进正文的普通字符（不另设一列、也不转成「[微笑]」那种代码）；
 * 图先走 POST /api/file/uploadImage 传上去拿回 URL，再把 `[图片](https://…)` 这一串拼进正文，
 * 和文字存在同一格 content 里 —— 不建图片列，「发表」那一条也只收 content。
 * 画的时候把这一串拆成「文字段 + 图片段」交替铺出来：文字段仍是纯文本插值（绝不 v-html，评论是访客填的），
 * 图片段只是把地址交给 img 的 src。字数那道闸按摘掉图片串之后的文字算，一个 emoji 占 2 字。
 *
 * 登录门槛照那颗收藏按钮的写法：没登录时按钮照画，只是挂上 data-open-login 让页头那扇登录窗认领；
 * 那一格绑的是 null 而不是 false（data-* 不是布尔属性，留着 "false" 就是登录后点它还要弹登录窗）。
 * 登录态一流转就回读一次列表 —— is_like 和「这条是不是我写的」都由登录态决定，不重新捞会停在空心。
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { FwbButton } from 'flowbite-vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import ImagePreview from '@/components/ImagePreview.vue';
import TabDropper from '@/components/TabDropper.vue';
import { useI18n } from '@/i18n';
import { useAuthStore } from '@/stores/auth';
import {
  COMMENT_EMOJIS,
  COMMENT_MAX_IMAGES,
  COMMENT_MAX_LENGTH,
  commentImageMark,
  commentText,
  deleteComment,
  fetchComments,
  parseCommentContent,
  publishComment,
  removeCommentImage,
  toggleCommentLike,
  uploadCommentImage,
  type CommentRow,
  type CommentSort,
} from '@/lib/comment';

const props = defineProps<{
  /** 1商品 2门店 3品牌 4广场（字典 comment_type），businessId 指向哪张表由它定 */
  commentType: number;
  businessId: number;
  /** 当岛直接挂时由页面壳递进来（岛里拿不到 Astro 对象，见 @/i18n 顶上那段）；嵌在别的岛里时不用给 */
  locale?: string;
  /**
   * 预览态（详情页那一档）：只捞最热的前 5 条，不给排序、不给顶上那个发表框、不给翻页，
   * 底下换成一颗「查看全部 N 条评论」跳 /comments 那一页 —— 详情页不是来看评论的，铺一整页会把它该买的货挤下去。
   * 点赞和回复在这两档里都给：这两个动作各只涉及一条，摆不下整页评论也不影响详情页排版。
   */
  preview?: boolean;
  /**
   * 要高亮定位的那条评论 id（「全部评论」页从消息中心点进来时由 URL 的 highlight 递进来）。
   * 回复跟着它的父行整棵子树一起回来，所以这一条不在第一页时下面会一页页往下捞。
   * 带上 undefined 是因为从详情页那一档传进来时就是「没带就不给」，strictest 不许显式写 undefined。
   */
  highlightId?: number | undefined;
}>();

const { d, fill, href } = useI18n();
const auth = useAuthStore();

const PAGE_SIZE = 10;
/** 预览态只铺这一页：5 条最热，剩下的交给「查看全部评论」那一页 */
const PREVIEW_SIZE = 5;
const pageSize = () => (props.preview ? PREVIEW_SIZE : PAGE_SIZE);

/** 登录态读 localStorage，服务端那一趟读不到 ⇒ 第一趟一律当没登录，否则整块文案撞 hydration */
const ready = ref(false);
const signedIn = computed(() => ready.value && auth.isLoggedIn);
const myId = computed(() => auth.user?.id ?? null);

const rows = ref<CommentRow[]>([]);
const loading = ref(true);
const loadError = ref('');
const pageNum = ref(1);
const pageCount = ref(0);
/** 这个对象名下的评论总条数（顶层 + 回复一起算），只用来印「共 N 条」 */
const total = ref(0);
const moreLoading = ref(false);
const moreError = ref('');
/** 预览态没有排序那排，钉死在「最热」；完整态默认最新 */
const sortType = ref<CommentSort>(props.preview ? 'like' : 'time');
/** 递给 TabDropper 的那两档排序（wash 那一档：压在选中文字背后的一块软底，不凭空添一条线） */
const sortItems = computed(() => SORTS.map(one => ({ key: one.key as string, label: one.label })));

const draft = ref('');
const posting = ref(false);
const postError = ref('');
const replyTo = ref<CommentRow | null>(null);
/** 一次只让一颗点赞/删除在动，免得连点把同一条赞两次 */
const busyId = ref<number | null>(null);
const actionError = ref('');
const deleteTarget = ref<CommentRow | null>(null);

/** 表情面板开合：emoji 只有这一处入口，正文里存的就是普通字符 */
const emojiOpen = ref(false);
/** 还在传几张：传图期间不让发表，否则正文里少拼几串地址 */
const uploadingCount = ref(0);
const imageError = ref('');
/** 看大图那一层：一组图 + 从第几张开始（ImagePreview 多于一张时自己出左右两颗） */
const viewer = ref<{ urls: string[]; index: number } | null>(null);

/** 正文拆出来的一段：文字照纯文本画，图片只把地址交给 img，顺带记它是这串里的第几张 */
type CommentImage = { type: 'image'; url: string; index: number };
type CommentView = { type: 'text'; text: string } | CommentImage;

function toView(content: string | null | undefined): CommentView[] {
  let n = 0;
  const out: CommentView[] = [];
  for (const seg of parseCommentContent(content)) {
    if (seg.type === 'text') out.push({ type: 'text', text: seg.text });
    else out.push({ type: 'image', url: seg.url, index: n++ });
  }
  return out;
}

function imagesOf(view: CommentView[]): string[] {
  return view.filter((seg): seg is CommentImage => seg.type === 'image').map((seg) => seg.url);
}

const SORTS: { key: CommentSort; label: string }[] = [
  { key: 'time', label: d.comment.sortTime },
  { key: 'like', label: d.comment.sortLike },
];

const hasMore = computed(() => !props.preview && pageNum.value < pageCount.value);
/** 输入框里已经拼进去的那几串图（待发缩略图、张数、剩余槽位都读这一份） */
const draftView = computed(() => toView(draft.value));
const draftImages = computed(() => draftView.value.filter((seg): seg is CommentImage => seg.type === 'image'));
/** 字数只数文字：图片那一整串（连语法带地址）摘掉再量，和服务层同一口径 */
const pureText = computed(() => commentText(draft.value));
const textLength = computed(() => pureText.value.length);
const overLimit = computed(() => textLength.value > COMMENT_MAX_LENGTH);
const remain = computed(() => Math.max(0, COMMENT_MAX_LENGTH - textLength.value));
const canPost = computed(
  () => signedIn.value && !posting.value && uploadingCount.value === 0 && pureText.value.trim().length > 0 && !overLimit.value,
);
const leftSlots = computed(() => COMMENT_MAX_IMAGES - draftImages.value.length);

/** 一棵评论树铺成一条串：回复的回复一直往下铺，模板只写一份行形状，缩进靠 indent。
 *  indent 到第二格就封顶 —— 手机上层层缩下去会把正文挤成一条竖缝，深到第三层以后只看「回复 @某人」那一句。
 *  正文的拆串结果单独算一份而不是摊回 row 上 —— 点赞那颗要改的是 rows 里那个响应式对象本身，
 *  改成副本的话按完赞数就丢了（重新捞一页才回来）*/
const INDENT_CLASS = ['', 'ml-8', 'ml-16'];
type CommentItem = { key: string; row: CommentRow; view: CommentView[]; images: string[]; indent: number };
const items = computed(() => {
  const walk = (r: CommentRow, depth: number): CommentItem[] => {
    const view = toView(r.content);
    return [
      { key: String(r.id), row: r, view, images: imagesOf(view), indent: Math.min(depth, INDENT_CLASS.length - 1) },
      ...(r.children ?? []).flatMap((child) => walk(child, depth + 1)),
    ];
  };
  return rows.value.flatMap((row) => walk(row, 0));
});

/** 缩进那一格的类名：回复行左边一条竖线，回复框搬到哪一级就照那一级的样子挂 */
function indentClass(indent: number): string {
  return indent ? `${INDENT_CLASS[indent]} border-l border-gray-100 pl-4 dark:border-gray-700` : '';
}

/** 列表里的一个位置：一条评论，或者「发表/回复」那一格（整块模板只有一份，位置由这里定） */
type FlowLine = ({ kind: 'comment' } & CommentItem) | { kind: 'composer'; key: 'composer'; indent: number };
const flow = computed<FlowLine[]>(() => {
  const lines: FlowLine[] = items.value.map((item) => ({ kind: 'comment', ...item }));
  // 预览态平时不排这一格（要新发表就去「全部评论」那一页），两种情况例外：点了「回复」，以及一条评论都还没有 ——
  // 后者底下那颗「查看全部」本来也不画（没东西可看），不就地给输入框的话，第一条压根发不出去。
  if (props.preview && !replyTo.value && items.value.length) return lines;
  const target = replyTo.value;
  const at = target ? lines.findIndex((line) => line.kind === 'comment' && line.row.id === target.id) : -1;
  // 被回复那一条不在当前这一页（换了排序、或那一支刚被删）时退回最前，总比输入框凭空没了强
  const above = at < 0 ? null : items.value[at];
  lines.splice(at + 1, 0, { kind: 'composer', key: 'composer', indent: above ? above.indent : 0 });
  return lines;
});

/** 「查看全部评论」那一页：类型和对象 id 一起递过去（一列指四张表，少了 type 会撞号） */
const allHref = computed(() => href(`/comments?type=${props.commentType}&id=${props.businessId}`));

const composerBox = ref<HTMLTextAreaElement | null>(null);

/** 点「回复」：框子挪到那一条下面，顺手把光标递进去（focus 自己会把它滚进视野） */
function startReply(row: CommentRow) {
  replyTo.value = row;
  void nextTick(() => composerBox.value?.focus());
}

async function loadPage(page: number) {
  const data = await fetchComments(props.commentType, props.businessId, page, pageSize(), sortType.value);
  if (page === 1) {
    rows.value = data.records;
  } else {
    // 两页之间有人发新评论，同一条可能被捞两次 ⇒ 按 id 去重再拼（和商品续页同一处理）
    const seen = new Set(rows.value.map((row) => row.id));
    rows.value = [...rows.value, ...data.records.filter((row) => !seen.has(row.id))];
  }
  pageNum.value = page;
  pageCount.value = data.pages;
  total.value = data.total;
}

async function loadFirst() {
  loading.value = true;
  loadError.value = '';
  try {
    await loadPage(1);
  } catch (err) {
    loadError.value = fill(d.comment.loadFailed, { msg: (err as Error).message });
  } finally {
    loading.value = false;
  }
}

async function loadMore() {
  if (moreLoading.value || !hasMore.value) return;
  moreLoading.value = true;
  moreError.value = '';
  try {
    await loadPage(pageNum.value + 1);
  } catch (err) {
    // 续页失败只红下面那一行 + 一颗重试，别把已经画出来的评论撤走
    moreError.value = fill(d.comment.loadFailed, { msg: (err as Error).message });
  } finally {
    moreLoading.value = false;
  }
}

/** 切排序从第一页重拉：两档的页码进度对不上，接着上一档往下翻会漏行 */
async function pickSort(key: CommentSort) {
  if (sortType.value === key || loading.value) return;
  sortType.value = key;
  replyTo.value = null;
  await loadFirst();
}

/** 表情只往正文末尾追加：一个 emoji 占 2 个字，装不下就不插（不等服务端拒） */
function insertEmoji(emoji: string) {
  if (textLength.value + emoji.length > COMMENT_MAX_LENGTH) return;
  draft.value += emoji;
}

/**
 * 选完图就逐张传，传回来的地址当场拼成一串 `[图片](url)` 追加进正文（发表那一条只收 content）。
 * input.value 要先清掉，否则同一张图第二次选会被浏览器当成没变化而不触发 change。
 */
async function pickImages(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  input.value = '';
  if (!files.length) return;

  imageError.value = '';
  const room = COMMENT_MAX_IMAGES - draftImages.value.length;
  if (room <= 0) {
    imageError.value = fill(d.comment.imageTooMany, { n: COMMENT_MAX_IMAGES });
    return;
  }
  if (files.length > room) {
    imageError.value = fill(d.comment.imageTooMany, { n: COMMENT_MAX_IMAGES });
  }
  uploadingCount.value += 1;
  try {
    for (const file of files.slice(0, room)) {
      const url = await uploadCommentImage(file);
      const mark = commentImageMark(url);
      // 同一个地址拼两次会画出一模一样的两张，正文里已经有这一串就跳过
      if (!draft.value.includes(mark)) draft.value += mark;
    }
  } catch (err) {
    imageError.value = fill(d.comment.imageFailed, { msg: (err as Error).message });
  } finally {
    uploadingCount.value -= 1;
  }
}

/** 撤掉待发的那张图：按正文里的第几张来数，摘掉的是连语法的一整串 */
function removePicked(index: number) {
  draft.value = removeCommentImage(draft.value, index);
}

async function submit() {
  // 没登录这一支走不到这里：输入框那一格换成了带 data-open-login 的按钮，点击归登录窗认领
  if (!canPost.value) return;
  posting.value = true;
  postError.value = '';
  try {
    await publishComment({
      commentType: props.commentType,
      businessId: props.businessId,
      content: draft.value.trim(),
      replyToId: replyTo.value?.id,
    });
    draft.value = '';
    imageError.value = '';
    emojiOpen.value = false;
    replyTo.value = null;
    // 发完按时间排：刚发的那一条在「最热」那一档多半不在第一页，画出来看不见会以为没发出去
    // 预览态不动这一格：那 5 条钉死在「最热」，头顶那句写的就是它，悄悄换成最新会把说明那句说错
    if (!props.preview) sortType.value = 'time';
    await loadFirst();
  } catch (err) {
    postError.value = fill(d.comment.postFailed, { msg: (err as Error).message });
  } finally {
    posting.value = false;
  }
}

async function like(row: CommentRow) {
  if (!signedIn.value || busyId.value !== null) return;
  busyId.value = row.id;
  actionError.value = '';
  try {
    const on = await toggleCommentLike(row.id);
    // 接口只回按完的状态，那一格数不在里面 ⇒ 本地按方向加减（服务端那一句同款逻辑）
    const delta = on === row.isLike ? 0 : on ? 1 : -1;
    row.isLike = on;
    row.likeCount = Math.max(0, (row.likeCount ?? 0) + delta);
  } catch (err) {
    actionError.value = fill(d.comment.actionFailed, { msg: (err as Error).message });
  } finally {
    busyId.value = null;
  }
}

/** 只有自己写的那一条才有这颗；后端那道归属闸还在，这里只是不把按钮画给别人的评论 */
function canDelete(row: CommentRow) {
  return signedIn.value && row.userId != null && row.userId === myId.value;
}

async function confirmDelete() {
  const target = deleteTarget.value;
  if (!target || busyId.value !== null) return;
  busyId.value = target.id;
  actionError.value = '';
  try {
    await deleteComment(target.id);
    deleteTarget.value = null;
    await loadFirst();
  } catch (err) {
    actionError.value = fill(d.comment.actionFailed, { msg: (err as Error).message });
  } finally {
    busyId.value = null;
  }
}

/** 高亮定位那一格：命中的评论 id，只在这一次进页面里挂（翻页、切排序都不该再亮着） */
const highlightedId = ref<number | null>(null);
/** 一页页往下捞的上限：只为兜住「那个 id 根本不存在」，正常一页评论树就带回来了 */
const HIGHLIGHT_MAX_PAGES = 20;

function hasRow(id: number) {
  return items.value.some((item) => item.row.id === id);
}

/**
 * 从消息中心点一条互动消息进来时，那一条评论可能不在这对象的第一页：
 * 回复是跟着它的父行整棵子树一起回来的，所以要翻到那一支的顶层行所在那一页才看得见。
 * 捞着就滚到那一行、给它挂一秒底色；捞不着（删了、id 对不上）就当正常进这一页，不报错。
 */
async function locateHighlight() {
  const target = props.highlightId;
  if (!target) return;
  for (let guard = 0; guard < HIGHLIGHT_MAX_PAGES; guard++) {
    if (hasRow(target)) break;
    if (pageNum.value >= pageCount.value) break;
    try {
      await loadPage(pageNum.value + 1);
    } catch {
      break;
    }
  }
  if (!hasRow(target)) return;
  highlightedId.value = target;
  await nextTick();
  document.getElementById(`comment-${target}`)?.scrollIntoView({ block: 'center' });
  setTimeout(() => {
    if (highlightedId.value === target) highlightedId.value = null;
  }, 2000);
}

onMounted(() => {
  ready.value = true;
  void loadFirst().then(locateHighlight);
});

// 登进来/登出去都回读一次（点的是 auth.isLoggedIn，不是 signedIn —— 后者挂载那一趟自己会翻一次，白刷一页）
watch(() => auth.isLoggedIn, () => void loadFirst());
</script>

<template>
  <div>
    <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
      <!-- 预览态不给排序（那 5 条钉死在「最热」），左边这一句只说明它们是怎么挑出来的 -->
      <span v-if="preview" class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ d.comment.hot }}</span>
      <TabDropper
        v-else
        shape="wash"
        :label="d.comment.sortAria"
        list-class="gap-4 shrink-0"
        :model-value="sortType"
        :items="sortItems"
        @update:model-value="(key: string) => pickSort(key as CommentSort)"
      />
      <span class="text-sm text-gray-500 dark:text-gray-400">
        {{ fill(d.common.total, { n: total, unit: d.comment.countUnit }) }}
      </span>
    </div>

    <!-- 一条 ul 铺到底：「发表/回复」那一格也在里面，位置跟着 flow 走 -->
    <ul class="flex flex-col gap-6">
      <template v-for="line in flow" :key="line.key">
        <!-- 发表那一格：登录了才是输入框，没登录就是一块「点这里登录」的假框 -->
        <li v-if="line.kind === 'composer'" :class="indentClass(line.indent)">
          <template v-if="signedIn">
            <p v-if="replyTo" class="mb-2 flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
              <span class="rounded bg-gray-100 px-2 py-0.5 dark:bg-gray-700">
                {{ fill(d.comment.replyingTo, { name: replyTo.nickname || d.comment.anonymous }) }}
              </span>
              <button type="button" class="underline" @click="replyTo = null">{{ d.comment.cancelReply }}</button>
            </p>
            <!-- 这一格故意不设 maxlength：正文里现在连着 [图片](url) 那一整串，地址七八十个字符，
                 按原始长度限住的话选两张图就打不了字了；字数那道闸只数摘掉图片串之后的文字（右下角那个数），超了红字 + 不让发表 -->
            <textarea
              ref="composerBox"
              v-model="draft"
              rows="3"
              :placeholder="d.comment.placeholder"
              class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 outline-none focus:border-blue-500 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
            ></textarea>

            <!-- 底下这一排：左边表情和选图两颗，右边剩字数 + 发表 -->
            <div class="mt-2 flex flex-wrap items-center justify-between gap-3">
              <div class="flex items-center gap-2">
                <button
                  type="button"
                  :aria-label="d.comment.emojiAria"
                  :aria-expanded="emojiOpen"
                  class="rounded-lg border px-2.5 py-1 text-xs transition-colors"
                  :class="
                    emojiOpen
                      ? 'border-blue-500 text-blue-600 dark:border-blue-400 dark:text-blue-400'
                      : 'border-gray-200 text-gray-600 hover:border-gray-300 dark:border-gray-600 dark:text-gray-300'
                  "
                  @click="emojiOpen = !emojiOpen"
                >
                  🙂 {{ d.comment.emoji }}
                </button>
                <!-- 选图用的是 <label> 套一颗看不见 input：点文字就开系统文件选择器 -->
                <label
                  class="cursor-pointer rounded-lg border border-gray-200 px-2.5 py-1 text-xs text-gray-600 transition-colors hover:border-gray-300 dark:border-gray-600 dark:text-gray-300"
                >
                  🖼 {{ d.comment.image }}
                  <input
                    type="file"
                    accept="image/*"
                    multiple
                    class="sr-only"
                    :disabled="uploadingCount > 0 || leftSlots <= 0"
                    @change="pickImages"
                  />
                </label>
                <span v-if="draftImages.length" class="text-xs text-gray-400 dark:text-gray-500">
                  {{ fill(d.comment.imageCount, { n: draftImages.length, max: COMMENT_MAX_IMAGES }) }}
                </span>
                <span v-if="uploadingCount > 0" class="text-xs text-gray-400 dark:text-gray-500">
                  {{ d.comment.imageUploading }}
                </span>
              </div>
              <div class="flex items-center gap-3">
                <span
                  class="text-xs"
                  :class="overLimit ? 'text-red-600 dark:text-red-400' : 'text-gray-400 dark:text-gray-500'"
                >{{ fill(d.comment.limit, { n: remain }) }}</span>
                <FwbButton type="button" color="blue" size="sm" :disabled="!canPost" @click="submit">
                  {{ posting ? d.comment.publishing : d.comment.publish }}
                </FwbButton>
              </div>
            </div>

            <!-- 表情面板：插进正文的是普通字符，所以这一格只管往 draft 后面追加，不另存一份 -->
            <div
              v-if="emojiOpen"
              :aria-label="d.comment.emojiAria"
              class="mt-2 grid grid-cols-8 gap-1 rounded-lg border border-gray-200 bg-gray-50 p-2 dark:border-gray-600 dark:bg-gray-800"
            >
              <button
                v-for="emoji in COMMENT_EMOJIS"
                :key="emoji"
                type="button"
                class="rounded p-1 text-lg leading-none transition-colors hover:bg-white dark:hover:bg-gray-700"
                @click="insertEmoji(emoji)"
              >
                {{ emoji }}
              </button>
            </div>

            <!-- 已经传好、拼进正文的那几张：点 × 摘掉对应的整串语法（正文剩下的部分不动），服务端只收还留在正文里的地址 -->
            <div v-if="draftImages.length" class="mt-2 flex flex-wrap gap-2">
              <div v-for="img in draftImages" :key="img.index" class="relative">
                <img :src="img.url" :alt="d.comment.imageAlt" class="h-16 w-16 rounded-lg border border-gray-200 object-cover dark:border-gray-600" />
                <button
                  type="button"
                  :aria-label="d.comment.imageRemove"
                  class="absolute -right-1.5 -top-1.5 flex h-5 w-5 items-center justify-center rounded-full bg-gray-800 text-xs leading-none text-white"
                  @click="removePicked(img.index)"
                >
                  ×
                </button>
              </div>
            </div>
          </template>

          <button
            v-else
            type="button"
            data-open-login=""
            class="w-full rounded-lg border border-dashed border-gray-300 px-3 py-3 text-left text-sm text-gray-500 transition-colors hover:border-blue-400 hover:text-blue-600 dark:border-gray-600 dark:text-gray-400 dark:hover:border-blue-500 dark:hover:text-blue-400"
          >
            {{ d.comment.loginToComment }}
          </button>

          <p v-if="imageError" class="mt-2 text-sm text-red-600 dark:text-red-400">{{ imageError }}</p>
          <p v-if="postError" class="mt-2 text-sm text-red-600 dark:text-red-400">{{ postError }}</p>
          <p v-if="actionError" class="mt-2 text-sm text-red-600 dark:text-red-400">{{ actionError }}</p>
        </li>

        <!-- 评论行：一条挨一条往下排，回复的回复紧跟它的父行；id 那格是给「从消息点进来要滚到这一条」用的定位锚 -->
        <li
          v-else
          :id="`comment-${line.row.id}`"
          class="flex gap-3 rounded-lg transition-colors"
          :class="[
            indentClass(line.indent),
            highlightedId === line.row.id ? 'bg-amber-50 ring-1 ring-amber-200 dark:bg-amber-900/20 dark:ring-amber-700' : '',
          ]"
        >
          <img
            v-if="line.row.avatar"
            :src="line.row.avatar"
            :alt="line.row.nickname || d.comment.anonymous"
            class="h-9 w-9 shrink-0 rounded-full border border-gray-200 object-cover dark:border-gray-700"
          />
          <span
            v-else
            class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gray-100 text-sm font-semibold text-gray-400 dark:bg-gray-700"
          >
            {{ (line.row.nickname || d.comment.anonymous).charAt(0) }}
          </span>

          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-2 text-sm">
              <span class="font-medium text-gray-900 dark:text-gray-100">
                {{ line.row.nickname || d.comment.anonymous }}
              </span>
              <!-- 置顶只有后台能设，前台只负责把它打在头名上 -->
              <span
                v-if="line.row.isStick === 1"
                class="rounded bg-amber-50 px-1.5 py-0.5 text-xs font-medium text-amber-700 dark:bg-amber-900/40 dark:text-amber-300"
              >
                {{ d.comment.pinned }}
              </span>
              <!-- 回复行说清它在回谁：缩进只到第二格就封顶，再往下全靠这一句认对象 -->
              <span
                v-if="line.indent && line.row.replyNickname"
                class="text-xs text-gray-500 dark:text-gray-400"
              >
                {{ fill(d.comment.replyingTo, { name: line.row.replyNickname }) }}
              </span>
            </div>

            <!-- 正文按「文字段 + 图片段」交替铺出来：文字段仍是纯文本插值（评论是访客填的，当 HTML 画就是存储型 XSS，
                 emoji 也在这一格里），图片段只把 [图片](url) 里那个地址交给 img 的 src。
                 点哪张就从哪张起看大图，一组图整组递给查看器（多于一张时它自己出左右两颗） -->
            <p class="mt-1 whitespace-pre-wrap break-words text-sm leading-6 text-gray-800 dark:text-gray-200">
              <template v-for="(seg, index) in line.view" :key="index">
                <span v-if="seg.type === 'text'">{{ seg.text }}</span>
                <button
                  v-else
                  type="button"
                  :aria-label="d.comment.imageAlt"
                  class="mr-1 inline-block align-top"
                  @click="viewer = { urls: line.images, index: seg.index }"
                >
                  <img
                    :src="seg.url"
                    :alt="d.comment.imageAlt"
                    class="h-20 w-20 rounded-lg border border-gray-200 object-cover transition-opacity hover:opacity-80 dark:border-gray-600"
                  />
                </button>
              </template>
            </p>

            <div class="mt-2 flex flex-wrap items-center gap-3 text-xs text-gray-400 dark:text-gray-500">
              <span>{{ line.row.createTime ? line.row.createTime.slice(0, 16) : d.common.dash }}</span>
              <span v-if="line.row.ipSource">{{ fill(d.comment.ipLocation, { place: line.row.ipSource }) }}</span>

              <button
                type="button"
                :aria-label="d.comment.like"
                :aria-pressed="line.row.isLike === true"
                :data-open-login="signedIn ? null : ''"
                :disabled="busyId !== null"
                class="flex items-center gap-1 transition-colors disabled:opacity-60"
                :class="
                  line.row.isLike
                    ? 'text-red-500 dark:text-red-400'
                    : 'hover:text-gray-600 dark:hover:text-gray-300'
                "
                @click="like(line.row)"
              >
                <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" :fill="line.row.isLike ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3" />
                </svg>
                <span>{{ line.row.isLike ? d.comment.liked : d.comment.like }}</span>
                <span v-if="line.row.likeCount">{{ line.row.likeCount }}</span>
              </button>

              <button type="button" class="hover:text-gray-600 dark:hover:text-gray-300" @click="startReply(line.row)">
                {{ d.comment.reply }}
              </button>
              <button
                v-if="canDelete(line.row)"
                type="button"
                class="hover:text-red-500 dark:hover:text-red-400"
                @click="deleteTarget = line.row"
              >
                {{ d.comment.delete }}
              </button>
            </div>
          </div>
        </li>
      </template>
    </ul>

    <p v-if="loading" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">{{ d.common.loading }}</p>

    <div v-else-if="loadError" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
      {{ loadError }}
      <button type="button" class="ml-2 underline" @click="loadFirst">{{ d.detailCommon.retry }}</button>
    </div>

    <p v-else-if="!items.length" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">
      {{ d.comment.empty }}
    </p>

    <p v-if="moreError" class="mt-4 text-center text-sm text-red-600 dark:text-red-400">
      {{ moreError }}
      <button type="button" class="ml-2 underline" @click="loadMore">{{ d.detailCommon.retry }}</button>
    </p>

    <div v-if="hasMore && !moreError" class="mt-4 flex justify-center">
      <FwbButton type="button" color="alternative" size="sm" :disabled="moreLoading" @click="loadMore">
        {{ moreLoading ? d.common.loading : fill(d.common.more, { n: PAGE_SIZE }) }}
      </FwbButton>
    </div>

    <!-- 预览态底下这一颗换成「查看全部」：走普通链接，astro 的悬浮预加载和换页那套都认它 -->
    <div v-if="preview && !loading && !loadError && items.length" class="mt-4 flex justify-center">
      <a
        :href="allHref"
        class="rounded-lg border border-gray-200 px-6 py-2 text-sm font-medium text-gray-700 transition-colors hover:border-blue-500 hover:text-blue-600 dark:border-gray-600 dark:text-gray-200 dark:hover:border-blue-400 dark:hover:text-blue-400"
        >{{ fill(d.comment.viewAll, { n: total }) }}</a
      >
    </div>

    <!-- 删自己那一条：确认框公共组件，父级用 v-if 控制挂载 -->
    <ConfirmDialog
      v-if="deleteTarget"
      :title="d.comment.deleteTitle"
      :message="d.comment.deleteConfirm"
      :confirm-text="d.comment.delete"
      :busy="busyId === deleteTarget.id"
      @confirm="confirmDelete"
      @cancel="deleteTarget = null"
    />

    <!-- 看评论配图：一组图 + 起点下标交给公共那层，左右两颗和 ← → 由它自己在多于一张时加出来 -->
    <ImagePreview
      v-if="viewer"
      :list="viewer.urls"
      :index="viewer.index"
      :alt="d.comment.imageAlt"
      @close="viewer = null"
    />
  </div>
</template>
