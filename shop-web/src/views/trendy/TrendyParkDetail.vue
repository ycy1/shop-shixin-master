<script setup lang="ts">
/**
 * 一家园区的详情页：顶上那张照片卡片（左边图、右边地址那一块，同一张卡片）+ 底下一排两档（在架票种 / 游客评论）。
 *
 * 照片卡片这一块的来历：原先是一条封面横条，后来换成照片墙，中间还挂过自动轮播 —— 现在不轮了：
 * - 卡片里就摆 3 列、最多 6 张（精选在前），有几张铺几行，两张就是一行，不补空格也不翻页；
 * - 点一格还是开大图（公共的 ImagePreview，一组图带左右两颗和 ←/→），翻的就是卡片上这几张；
 * - 一张照片都没有时那一格退回封面（封面是园区自己那一格，不算进照片里，但总得给人看张图）；
 * - 地址、营业时间、介绍在卡片右栏 —— 左边看图、右边看这几个景在哪、什么时候开门；
 *   窄屏（md 以下）收成上下两段，中间那道分隔线从竖的换成横的；
 * - 还有更多张的走「全部照片」那一页（20 张一页），详情那条只带前 6 张。
 *
 * 买票那一条要登录态：没登录时那颗按钮照画，只是挂上 data-open-login 让页头那扇登录窗认领
 *（口径同收藏按钮、评论区那颗发布）。买成的那几张（含票码和票面二维码）在同一个弹窗里给出来。
 *
 * 读的是 /api/web/shop/trendy/park/{id}：停业的、下架的、没这个 id 的，后端回同一句话，
 * 这一页直接把那句当提示用。列表那条只带「几个票种 / 最低价」，票和照片的明细只有这一趟才给。
 */
import { computed, onMounted, ref } from 'vue';
import CommentSection from '@/components/CommentSection.vue';
import FavoriteButton from '@/components/FavoriteButton.vue';
import ImagePreview from '@/components/ImagePreview.vue';
import ParkTicketBuyDialog from './ParkTicketBuyDialog.vue';
import TabDropper from '@/components/TabDropper.vue';
import { useI18n } from '@/i18n';
import { useAuthStore } from '@/stores/auth';
import { dictLabel, loadDict } from '@/lib/dict';
import { COMMENT_TYPE_PARK, fetchCommentTotal } from '@/lib/comment';
import { fetchTrendyParkDetail, parseSlotCodes, type Park, type ParkPhoto, type ParkTicket } from '@/lib/trendy';

const props = defineProps<{ parkId: number; locale?: string }>();

const { d, fill, href } = useI18n();
const t = d.trendy;

/** 卡片上摆几张（3 列 × 2 行）；详情那条也只带这么多张，其余走「全部照片」那一页 */
const WALL = 6;

const park = ref<Park | null>(null);
const loading = ref(true);
const error = ref('');

const auth = useAuthStore();
/** 登录态读的是 localStorage，服务端那一趟读不到 ⇒ 挂起来之后再认，不然撞 hydration mismatch */
const ready = ref(false);
const signedIn = computed(() => ready.value && auth.isLoggedIn);

/** 正在买的那张票种（null = 弹窗没开） */
const buying = ref<ParkTicket | null>(null);
const viewer = ref<{ urls: string[]; index: number } | null>(null);

/** 卡片上那几张：审核通过的在前（次序是服务端给的，精选优先），这里只截数量、不再自己排 */
const wallPhotos = computed<ParkPhoto[]>(() => (park.value?.photos ?? []).slice(0, WALL));

/** 点开大图时翻的那一串：没图的格子不排进去，不然翻得到空位 */
const wallUrls = computed(() => wallPhotos.value.map((one) => one.imageUrl).filter((url): url is string => !!url));

function openPreview(at: number) {
  const url = wallPhotos.value[at]?.imageUrl;
  if (!url) return;
  const index = wallUrls.value.indexOf(url);
  viewer.value = { urls: wallUrls.value, index: index < 0 ? 0 : index };
}

function openBuy(ticket: ParkTicket) {
  // 没登录这一支走不到这里：那颗按钮上挂的是 data-open-login，点击归页头那扇登录窗认领
  if (!signedIn.value) return;
  buying.value = ticket;
}

/** 一张票的时段那串码（"1,2"）翻成字典里的称呼；0 全天那一档单独说一句「全天」 */
function slotTexts(raw: string | null): string[] {
  const codes = parseSlotCodes(raw);
  if (codes.length === 0 || codes.includes(0)) return [t.parkCard.allDay];
  return codes.map((code) => dictLabel('ticket_time_slot', code) || String(code));
}

/** 底下那一排两档：在架票种 / 游客评论（形状同品牌详情页那两档）。评论不是一种查询条件，所以单开一个开关，切过去不碰别的状态 */
const showComment = ref(false);
/** 评论那一档标签上的条数单独捞一次（一页一条、只读 total）：tab 在点开之前就得有数；捞失败留 null，那一格不出现 */
const commentCount = ref<number | null>(null);
const tabItems = computed(() => [
  { key: 'tickets', label: t.park.ticketsTitle },
  { key: 'comment', label: t.park.commentsTitle, count: commentCount.value },
]);
/** 往后切面板从右边移进来、往前切从左边（CSS 在 global.css 最后那几条）；sliding 只在那段过渡里挂 */
const slideName = ref('tab-next');
const sliding = ref(false);

/** 点这一排的哪一档就往哪个方向移，认方向得在改状态之前 */
function pickPanel(show: boolean) {
  if (showComment.value === show) return;
  slideName.value = show ? 'tab-next' : 'tab-prev';
  showComment.value = show;
}

async function loadCommentCount() {
  try {
    commentCount.value = await fetchCommentTotal(COMMENT_TYPE_PARK, props.parkId);
  } catch {
    commentCount.value = null;
  }
}

/**
 * 消息中心那条「有人回复了你 / 赞了你的评论」点进来时带的两格（lib/notification 的 messageTarget 拼的）：
 * ?tab=comment 直接落在评论那档（不先画一眼票种），?highlight=<评论 id> 交给评论区滚到那一条并挂一下底色。
 *
 * 在 onMounted 里认而不是 setup 头顶：服务端那一趟没有 window，也不该按查询串先画出评论那档 ——
 * 两边画得不一样就是 hydration 对不上。
 */
const highlightId = ref<number | undefined>(undefined);

function readEntryQuery() {
  const params = new URLSearchParams(window.location.search);
  const target = Number(params.get('highlight'));
  highlightId.value = Number.isInteger(target) && target > 0 ? target : undefined;
  if (params.get('tab') === 'comment') showComment.value = true;
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    park.value = await fetchTrendyParkDetail(props.parkId);
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  ready.value = true;
  // 先认消息带来的那两格，再捞数据：评论区是 v-if，落在哪一档要在它挂上去之前定好
  readEntryQuery();
  loadDict(['ticket_type', 'ticket_time_slot']);
  void load();
  // 条数那一格和详情并行：它是 tab 标签上的一部分，等详情回来再捞会让那一格先空着闪一下
  void loadCommentCount();
});
</script>

<template>
  <section>
    <p v-if="loading" class="text-sm text-gray-500">{{ d.common.loading }}</p>
    <p v-else-if="error" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
      {{ fill(t.home.loadFailed, { msg: error }) }}
      <button type="button" class="ml-2 underline" @click="load">{{ t.home.retry }}</button>
    </p>

    <template v-else-if="park">
      <!-- 一、照片卡片：左边那几格是名下照片（点一格看大图），右边同一张卡片里是地址那一块；
           整块不限宽，和下面票种那一排同宽（卡片左右两边对齐才看得出是一页里的两块）；
           窄屏（md 以下）收成上下两段，中间那道分隔线也跟着从竖的换成横的 -->
      <div class="overflow-hidden rounded-2xl border border-gray-200 dark:border-gray-700">
        <div class="flex flex-col md:flex-row md:items-stretch">
          <!-- 有照片：3 列铺开，有几张算几张（最多 6 张，不补空格也不翻页）；左栏占一半，
               另一半给地址 —— 照片要小一，两栏各半比 7/12 对半分更稳（宽屏上每格约 165px） -->
          <div v-if="wallPhotos.length" class="grid min-w-0 grid-cols-3 gap-1.5 p-1.5 md:w-1/2">
            <button
              v-for="(one, i) in wallPhotos"
              :key="one.id"
              type="button"
              class="group relative aspect-square overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-700"
              :aria-label="one.description || park.parkName"
              @click="openPreview(i)"
            >
              <img
                v-if="one.imageUrl"
                :src="one.imageUrl"
                :alt="one.description || park.parkName"
                class="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                loading="lazy"
              />
              <span v-else class="flex h-full w-full items-center justify-center text-xs text-gray-400">
                {{ t.parkCard.noImage }}
              </span>
              <span
                v-if="one.description"
                class="absolute bottom-0 left-0 right-0 truncate bg-black/45 px-2 py-1 text-left text-[11px] text-white"
              >
                {{ one.description }}
              </span>
            </button>
          </div>

          <!-- 一张照片都没有：那一格退回封面（封面不算照片，但总得给人看张图） -->
          <div v-else class="relative aspect-[16/9] min-w-0 bg-gray-100 dark:bg-gray-700 md:aspect-auto md:w-1/2">
            <img v-if="park.coverImage" :src="park.coverImage" :alt="park.parkName" class="h-full w-full object-cover" />
            <span v-else class="flex h-full w-full items-center justify-center text-sm text-gray-400">
              {{ t.parkCard.noImage }}
            </span>
          </div>

          <!-- 地址那一块：卡片右栏（窄屏落在照片下面） -->
          <div class="flex min-w-0 flex-1 flex-col gap-1.5 border-t border-gray-200 px-4 py-3 md:border-l md:border-t-0 dark:border-gray-700">
            <h1 class="text-lg font-semibold">{{ park.parkName }}</h1>
            <p class="text-sm text-gray-600 dark:text-gray-300">
              {{ [park.areaZh, park.address].filter(Boolean).join(' ') || d.common.dash }}
            </p>
            <p v-if="park.openTime" class="text-sm text-gray-600 dark:text-gray-300">
              {{ t.parkCard.openTime }}：{{ park.openTime }}
            </p>
            <p v-if="park.description" class="mt-1 text-sm text-gray-600 dark:text-gray-300">{{ park.description }}</p>

            <!-- 全部照片那一页的入口：卡片上只有这几张，其余去分页那页看 -->
            <div v-if="wallPhotos.length" class="mt-auto flex items-center justify-between gap-3 pt-2">
              <span class="text-xs text-gray-500 dark:text-gray-400">
                {{ fill(t.photos.countLine, { n: wallPhotos.length }) }}
              </span>
              <a
                :href="href(`/trendy/park/${park.id}/photo`)"
                class="shrink-0 text-sm text-blue-600 hover:underline dark:text-blue-400"
              >
                {{ t.photos.all }} ›
              </a>
            </div>
          </div>
        </div>
      </div>

      <!-- 二、底下这一排两档：在架票种 / 游客评论（评论是 comment_type 的第五档，详情页只铺最热 5 条） -->
      <section class="mt-8">
        <TabDropper
          :label="t.park.ticketsTitle"
          list-class="mb-4 gap-6"
          :model-value="showComment ? 'comment' : 'tickets'"
          :items="tabItems"
          @update:model-value="(key: string) => pickPanel(key === 'comment')"
        />

        <!-- 跟着当前那一档换 key ⇒ Vue 认成换了一个元素，<Transition> 就把旧那块左右移走完事再挂新的；
             外层这一格只在过渡那一段挂 overflow-x-clip，28px 的位移会探出容器，不裁就闪一条横向滚动条 -->
        <div :class="{ 'overflow-x-clip': sliding }">
          <Transition :name="slideName" mode="out-in" @before-leave="sliding = true" @after-enter="sliding = false">
            <div
              :key="showComment ? 'comment' : 'tickets'"
              role="tabpanel"
              :aria-label="showComment ? t.park.commentsTitle : t.park.ticketsTitle"
            >
              <!-- 园区不存在/停用那一趟已经整块红了，这一页不会带着错 id 去捞评论 -->
              <CommentSection
                v-if="showComment"
                preview
                :comment-type="COMMENT_TYPE_PARK"
                :business-id="parkId"
                :highlight-id="highlightId"
              />

              <template v-else>
                <p v-if="!park.tickets?.length" class="text-sm text-gray-500 dark:text-gray-400">
                  {{ t.park.ticketEmpty }}
                </p>
                <ul v-else class="flex flex-col gap-3">
                  <li
                    v-for="ticket in park.tickets"
                    :key="ticket.id"
                    class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800"
                  >
                    <div class="flex flex-wrap items-baseline justify-between gap-2">
                      <span class="text-base font-medium">{{ ticket.ticketName }}</span>
                      <span class="text-lg font-semibold text-red-600 dark:text-red-400">
                        ¥{{ Number(ticket.price ?? 0).toFixed(2) }}
                      </span>
                    </div>
                    <p class="mt-1 flex flex-wrap gap-x-3 text-xs text-gray-500 dark:text-gray-400">
                      <span v-if="dictLabel('ticket_type', ticket.ticketType)">
                        {{ dictLabel('ticket_type', ticket.ticketType) }}
                      </span>
                      <span>{{ t.parkCard.slots }}：{{ slotTexts(ticket.timeSlots).join(' / ') }}</span>
                      <span v-if="ticket.validDays != null">{{ fill(t.parkCard.validDays, { n: ticket.validDays }) }}</span>
                      <span v-else>{{ t.parkCard.validForever }}</span>
                      <span v-if="ticket.maxPerOrder != null">{{ fill(t.parkCard.maxPerOrder, { n: ticket.maxPerOrder }) }}</span>
                    </p>
                    <p v-if="ticket.description" class="mt-2 text-sm text-gray-600 dark:text-gray-300">
                      {{ ticket.description }}
                    </p>
                    <div class="mt-3 flex items-center gap-3">
                      <button
                        type="button"
                        class="rounded-lg bg-blue-600 px-4 py-1.5 text-sm text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600"
                        :data-open-login="signedIn ? null : ''"
                        @click="openBuy(ticket)"
                      >
                        {{ t.buy.button }}
                      </button>
                      <span v-if="!signedIn" class="text-xs text-gray-400">{{ t.buy.loginFirst }}</span>
                    </div>
                  </li>
                </ul>
                <p class="mt-3 text-xs text-gray-400">{{ t.detail.ticketNote }}</p>
              </template>
            </div>
          </Transition>
        </div>
      </section>
    </template>

    <!-- 收藏那颗钉在根这一层：只吃 parkId，详情捞没捞回来都不影响它点得动（写法同品牌详情页那颗） -->
    <FavoriteButton :target-type="4" :target-id="parkId" :name="park?.parkName" />

    <ParkTicketBuyDialog v-if="buying" :ticket="buying" @close="buying = null" />

    <ImagePreview
      v-if="viewer"
      :list="viewer.urls"
      :index="viewer.index"
      :alt="park?.parkName ?? t.photos.title"
      @close="viewer = null"
    />
  </section>
</template>
