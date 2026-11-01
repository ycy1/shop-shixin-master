<script setup lang="ts">
/**
 * 首页那两台「盲盒机」——机身正面摆正（不侧转），玻璃窗压出凹陷、屏幕转起来是一面滚筒
 *（符号一枚一枚轮流前倾后仰地过窗，不是平推），机身下面一颗街机式大圆按钮，按下去才制动。
 *
 * 两档（`mode`）是同一台机器的两种按法：
 * - draw（首页那两台 + 盲盒详情那一台，2026-10-30 起首页也真抽了）= 先打
 *   POST /api/comm/trendy/box/{id}/draw 真抽一把（要登录态，没登录那颗按钮挂 data-open-login 交给页头登录窗），
 *   机台再按服务端回来的那几枚定格，弹窗里报「抽中几件 / 本趟应付多少 / 已加入购物车几件」。
 *   那一条服务端只报钱不扣钱，所以机身下面和弹窗里那句说明都得留着，别让人以为钱已经付了；
 *   机台三根轴只摆得下三枚，抽中多于三件（端盒那种）时轴上落前三枚、弹窗里全列出来。
 * - preview = 把这台盒里的款式转给人看，不记抽盒、不扣钱 —— 现在没有页面用这一档，
 *   留着是给"只摆个机器看看"的位置（比如活动页的展示位）。
 *
 * 滚得明显这件事靠四样叠：圈数够多（4/5/6 圈，不是两三格）、跑动中那条带子糊掉（motion blur）、
 * 滚筒的曲面（前倾后仰 + 侧边收窄）、停格那一下回弹（多走 14px 再弹回来）。
 * 三根轴各慢 600ms 依次停，最后那根停完才弹窗。
 *
 * 轴的动画：每根先换上一条「若干整圈 + 落点 + 落点后一格」的带子并把起点归零（这一帧不带过渡），
 * 下一帧再挂上过渡一路滑到「落点居中」那一格。收尾时把带子换回三格（落点居中、偏移归零）并摘掉过渡，
 * DOM 里始终只有几十枚符号，不会越转越长。
 *
 * 深浅两档：机身的形状是渐变 + 内外阴影叠出来的，工具类表达不了，全写在这份 scoped 里。
 * 深色那一档的选择器必须写成 `.dark .xxx`（主题那个 .dark 挂在 <html> 上，是祖先）——
 * 写成 `:global(html.dark) .xxx` 会被 scoped 编译整条塌成 `html.dark`（后面那截连同 data-v 一起丢），
 * 结果是深色样式一条都不生效、也不报错，只在暗档下看着"没做适配"。
 */
import { computed, nextTick, onMounted, ref } from 'vue';
import ModalOverlay from '@/components/ModalOverlay.vue';
import { useI18n } from '@/i18n';
import { useAuthStore } from '@/stores/auth';
import { drawTrendyBox, type BoxStyle, type DrawResult, type TrendyBox } from '@/lib/trendy';

const props = withDefaults(defineProps<{ box: TrendyBox; mode?: 'preview' | 'draw' }>(), { mode: 'preview' });

/** 真抽完递给父组件：详情页要重捞一次，不然「还剩几次」那一格一直停在抽之前 */
const emit = defineEmits<{ drawn: [result: DrawResult] }>();

const { d, fill, href } = useI18n();
const t = d.trendy.slot;
const card = d.trendy.card;

const isDraw = computed(() => props.mode === 'draw');

/**
 * 登录态：页头那颗登录窗按 `data-open-login` 认领点击（astro 每颗岛一个 Vue 应用，store 不共享），
 * 所以没登录时按钮照画、只挂上那一格，不在这里自己弹登录框。
 * 那一格绑的是 null 而不是 false：Vue 会把 false 原样写成 data-open-login="false"，
 * 而登录窗认的是属性存在与否，留着 "false" 就等于登录后点它还要弹登录窗。
 * 登录态读的是 localStorage，服务端那一趟读不到 ⇒ 挂起来之后再认，不然撞 hydration mismatch。
 */
const auth = useAuthStore();
const ready = ref(false);
const signedIn = computed(() => ready.value && auth.isLoggedIn);
onMounted(() => {
  ready.value = true;
});

/** 一格多高（px）：三格一窗，落点居中，所以带子的位移都是这一格的整数倍（样式里那条中奖线也按它对齐） */
const CELL = 88;
/** 三根轴各自转几圈、各慢多久：圈数和时长都递增，看着就是依次停 */
const CYCLES = [4, 5, 6];
const DURATION_MS = [1500, 2100, 2700];
/** 停格那一下多冲出去多少 px、再花多久弹回（老虎机那记「咔」） */
const BOUNCE_PX = 14;
const BOUNCE_BACK_MS = 170;

const spinning = ref(false);
const landed = ref<BoxStyle[]>([]);
const resultOpen = ref(false);
/** 哪几根轴正在跑：跑着的那条带子糊掉，停下就摘 —— 滚动感的另一半是「快」这个读数 */
const rolling = ref<boolean[]>([false, false, false]);
/** 真抽那一路：服务端回来的那一整份（抽中几件、本趟应付多少），弹窗念的是它、不是机台上那三枚 */
const won = ref<DrawResult | null>(null);
/** 购买整盒那一路的进行中：它不转动（不是"抽"），只用来在那一趟里禁掉两颗按钮 */
const buying = ref(false);
/** 抽失败那句（后端那句业务提示比状态码准），印在机身下面 */
const fail = ref('');

/** 符号池：这台盒名下在架的款式。少于三枚就重复凑，不然一窗都填不满 */
const pool = computed<BoxStyle[]>(() => {
  const styles = props.box.styles ?? [];
  if (styles.length === 0) return [];
  if (styles.length >= 3) return styles;
  const padded = [...styles];
  while (padded.length < 3) padded.push(...styles);
  return padded;
});

/** 起步那一屏：三根轴各错开一格，看着不像复制粘贴（带子正好三枚，偏移 0 就是中间那枚压线） */
const strips = ref<BoxStyle[][]>([0, 1, 2].map((r) => window3(pool.value, r)));
const offsets = ref<number[]>([0, 0, 0]);
const durations = ref<number[]>([0, 0, 0]);

/** 从第 shift 枚开始取三枚（shift 可为负，绕回池头；池子非空时正好三枚） */
function window3(list: BoxStyle[], shift: number): BoxStyle[] {
  const out: BoxStyle[] = [];
  for (let i = 0; i < 3; i += 1) {
    const item = list[(((shift + i) % list.length) + list.length) % list.length];
    if (item) out.push(item);
  }
  return out;
}

/** 数组里换一格（三根轴各自到点各自动一下，不能整条替换成同一个值） */
function withAt<T>(list: T[], index: number, value: T): T[] {
  return list.map((item, i) => (i === index ? value : item));
}

/** 落点那枚在池子里的位置：真抽回来的是服务端那份快照（新对象），所以按名字和图认，不按引用 */
function indexOfStyle(list: BoxStyle[], one: BoxStyle): number {
  return list.findIndex((item) => item.styleName === one.styleName && item.styleImage === one.styleImage);
}

/** 收尾那一屏：落点居中、左右各陪一枚（落点是池子里没有的款就直接用三枚它自己，不硬凑邻居） */
function centeredStrip(one: BoxStyle, list: BoxStyle[]): BoxStyle[] {
  const at = indexOfStyle(list, one);
  if (at < 0) return [one, one, one];
  return window3(list, at - 1);
}

/** 预览档：三根轴各随机落一枚 */
function randomPicks(list: BoxStyle[]): BoxStyle[] {
  return [0, 1, 2]
    .map(() => list[Math.floor(Math.random() * list.length)])
    .filter((item): item is BoxStyle => item !== undefined);
}

/** 真抽回来的那几条记录换成机台符号的形状（页面上只认名/图/是否隐藏这三格快照） */
function toSymbols(result: DrawResult): BoxStyle[] {
  return (result.records ?? []).map((one) => ({
    boxId: one.boxId,
    styleName: one.styleName,
    styleImage: one.styleImage,
    isHidden: one.isHidden,
    probability: null,
    remainCount: null,
  }));
}

/** 轴只有三根：抽中多于三件（端盒那种）时前三枚上轴、全部进弹窗那份清单；不足三枚用随机陪衬 */
function reelPicks(list: BoxStyle[], won: BoxStyle[]): BoxStyle[] {
  const out = won.slice(0, 3);
  while (out.length < 3) out.push(...randomPicks(list).slice(0, 3 - out.length));
  return out;
}

/**
 * 换带子与挂过渡之间必须隔一帧，否则浏览器把两次赋值合成一趟、直接从旧位置跳到新位置，看不见滚。
 * 这里用 setTimeout 而不是 requestAnimationFrame：后台标签页里 rAF 是整条挂起的（切走那一瞬拉把手就永远停在
 * 「转动中」），20ms 的定时器照样跑。
 */
function nextFrame(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 20));
}

async function spin() {
  const list = pool.value;
  if (spinning.value || list.length === 0) return;
  // 没开场的盒压根不发这一趟（那颗按钮也是禁用状态，这里是防键盘 Enter 绕过去）
  if (!canDraw.value) return;
  // 没登录这一支走不到这里：那颗按钮上挂的是 data-open-login，点击归页头那扇登录窗认领
  if (isDraw.value && !signedIn.value) return;
  spinning.value = true;
  landed.value = [];
  resultOpen.value = false;
  fail.value = '';
  won.value = null;

  // 弹窗列的是「这一把抽中了什么」，轴上只落它前三枚（三根轴摆不下更多）；预览那一路就是随机三枚
  let shown: BoxStyle[];
  if (isDraw.value) {
    try {
      const result = await drawTrendyBox(props.box.id);
      won.value = result;
      emit('drawn', result);
      shown = toSymbols(result);
    } catch (err) {
      // 后端那句业务提示（「这个盲盒当前不在进行中」这种）比状态码准，原样印在机身下面
      spinning.value = false;
      fail.value = err instanceof Error ? err.message : String(err);
      return;
    }
  } else {
    shown = randomPicks(list);
  }
  const picked = reelPicks(list, shown);
  landed.value = shown;
  if (picked.length < 3) {
    spinning.value = false;
    return;
  }

  strips.value = picked.map((target, r) => {
    const belt: BoxStyle[] = [];
    // 下标取值这一档 TS 不知道一定落在范围内（noUncheckedIndexedAccess），兜一下默认值而不是加断言
    for (let c = 0; c < (CYCLES[r] ?? 4); c += 1) belt.push(...list);
    const at = indexOfStyle(list, target);
    const after = at >= 0 ? (list[(at + 1) % list.length] ?? target) : target;
    belt.push(target, after);
    return belt;
  });
  durations.value = [0, 0, 0];
  offsets.value = [0, 0, 0];
  rolling.value = [true, true, true];

  await nextTick();
  await nextFrame();

  const finalOffsets = strips.value.map((belt) => -(belt.length - 3) * CELL);
  durations.value = [...DURATION_MS];
  // 落点是带子的倒数第二枚（后面还留一枚），位移到「它居中」那一格 —— 窗口三格，居中就是减一
  offsets.value = [...finalOffsets];

  // 每根轴到自己那一拍停：糊掉的那条先清晰、多冲 14px 再弹回，最后那根停完才弹窗
  DURATION_MS.forEach((ms, r) => {
    window.setTimeout(() => {
      rolling.value = withAt(rolling.value, r, false);
      durations.value = withAt(durations.value, r, BOUNCE_BACK_MS - 20);
      offsets.value = withAt(offsets.value, r, (finalOffsets[r] ?? 0) + BOUNCE_PX);

      window.setTimeout(() => {
        durations.value = withAt(durations.value, r, 0);
        offsets.value = withAt(offsets.value, r, 0);
        const target = picked[r];
        if (target) {
          strips.value = withAt(strips.value, r, centeredStrip(target, list));
        }
        if (r === 2) {
          // landed 在开趟前就按「抽中了什么」填好了（多于三件时比轴上那三枚全），这里只管停和弹
          spinning.value = false;
          resultOpen.value = true;
        }
      }, BOUNCE_BACK_MS);
    }, ms);
  });
}

const drawText = computed(() => (props.box.price == null ? null : Number(props.box.price).toFixed(2)));
const endBoxText = computed(() =>
  props.box.endBoxPrice == null ? null : Number(props.box.endBoxPrice).toFixed(2),
);
const remainText = computed(() =>
  props.box.remainCount == null ? card.unlimited : fill(card.remain, { n: props.box.remainCount }),
);
/** 盒的状态：2 进行中才让抽（服务端也判这一条，但别让人按下去才知道） */
const BOX_RUNNING = 2;
const canDraw = computed(() => !isDraw.value || props.box.status === BOX_RUNNING);
/**
 * 不让按的时候把原因摆到台面上：没开场的盒、没配款式的盒，原先都是"点一下什么都不发生"
 *（轴不转、弹窗也不出，只有机身下面那行点完才有的小字）—— 看着就是"抽取没反应"。
 */
const blocked = computed(() => {
  if (!pool.value.length) return t.empty;
  if (!canDraw.value) return card.notStartTip;
  return '';
});
/**
 * 购买整盒：一次买定一整盒，**不转老虎机** —— 转三根轴是"抽一把"那件事的动作，
 * 这一趟没有随机可言（服务端给的是"现在还有余量的款各一件"），让机器空转一下反而像在抽。
 * 按完直接弹那一份结果（盒里有哪几款、该付多少、车里刚多了整盒那一行），机台纹丝不动。
 */
async function buyWholeBox() {
  if (spinning.value || buying.value || !canDraw.value || !pool.value.length) return;
  // 没登录点不到这里：那颗按钮上挂的是 data-open-login，点击归页头那扇登录窗认领
  if (!signedIn.value) return;
  buying.value = true;
  resultOpen.value = false;
  fail.value = '';
  won.value = null;
  try {
    const result = await drawTrendyBox(props.box.id, true);
    won.value = result;
    resultOpen.value = true;
    emit('drawn', result);
  } catch (err) {
    fail.value = err instanceof Error ? err.message : String(err);
  } finally {
    buying.value = false;
  }
}

/** 机身下面那一行：不让按的时候说原因；真抽/整盒买那一档不再多说（结果弹窗已经说了这一趟干了什么） */
const tipText = computed(() => blocked.value || (!isDraw.value ? t.tip : ''));
/** 盒的类型（1 单抽 2 端盒 3 一番赏 4 抽签；后端没给当单抽） */
const boxType = computed(() => Number(props.box.boxType ?? 1));
const isEndBoxType = computed(() => isDraw.value && boxType.value === 2);
/**
 * 那颗「购买整盒」画不画：配了端盒价才画（后端那道闸同一条 —— 没价就只能按单抽价卖），
 * 抽签那一档不发实物、没有整盒可买所以也不画。端盒（2）那种盒主按钮本身就是整盒，不需要两颗。
 */
const canBuyBox = computed(() =>
  isDraw.value && props.box.endBoxPrice != null && boxType.value !== 4);
const buyBoxText = computed(() =>
  props.box.endBoxPrice == null ? '' : `¥${Number(props.box.endBoxPrice).toFixed(2)}`);
/** 弹窗列的那一叠：真抽/整盒那两档念服务端回来的（比轴上那三枚全），预览那档念轴上落的 */
const dialogItems = computed<BoxStyle[]>(() =>
  isDraw.value && won.value ? toSymbols(won.value) : landed.value);
/** 这一份结果是「买了一整盒」还是「抽了一把」：弹窗那两句文案跟着换 */
const wholeBoxResult = computed(() => won.value?.wholeBox === true);

/** 按钮上那行字：转着的时候不许再按，转过一把之后说「再来一次」；真抽那档换个动词，别和预览混成一句 */
const actionLabel = computed(() => {
  if (spinning.value) return t.spinning;
  // 端盒那一档（boxType=2）的「抽一次」本来就是一整盒：那颗直接说成「购买整盒」，不摆两颗做同一件事的按钮
  if (isEndBoxType.value) return t.buyBox;
  if (isDraw.value) return landed.value.length ? t.drawAgain : t.draw;
  return landed.value.length ? t.again : t.pull;
});
/** 弹窗里那一句应付（服务端给的是「这一趟该付多少」，钱还没扣） */
const costText = computed(() =>
  won.value?.cost == null ? null : fill(t.cost, { c: Number(won.value.cost).toFixed(2) }),
);
/**
 * 抽中的实物当场就是购物车里的一行（结算走的是正常商品那一条，这一页不管钱）。
 * 件数听服务端报的那一格，不是本趟抽中几个：款式没挂规格、挂的商品刚好下了架的那几种进不了车。
 */
const cartedText = computed(() =>
  won.value?.cartCount ? fill(t.carted, { n: won.value.cartCount }) : null,
);
</script>

<template>
  <div class="flex flex-col gap-2">
    <div class="machine">
      <!-- 机身：招牌 + 玻璃窗（三根轴）+ 出币口那一排数 -->
      <div class="cab">
        <div class="cab-face">
          <p class="marquee">{{ box.boxName }}</p>

          <div class="glass">
            <p v-if="!pool.length" class="flex h-40 items-center justify-center text-sm text-gray-400">
              {{ t.empty }}
            </p>
            <div v-else class="reels">
              <div v-for="(strip, r) in strips" :key="r" class="reel" :style="{ height: `${CELL * 3}px` }">
                <div
                  class="strip"
                  :class="{ rolling: rolling[r] }"
                  :style="{
                    transform: `translateY(${offsets[r]}px)`,
                    transition: durations[r] ? `transform ${durations[r]}ms cubic-bezier(.18,.72,.28,1)` : 'none',
                  }"
                >
                  <div
                    v-for="(item, i) in strip"
                    :key="`${r}-${i}`"
                    class="cell"
                    :style="{ height: `${CELL}px` }"
                    :title="item.styleName || ''"
                  >
                    <!-- 滚筒那一层：每枚按自己在带子上的位置错峰前倾后仰（延迟由 --i 排），
                         跑起来是一面弯的鼓在转，不是一张贴纸平推过窗 -->
                    <div class="drum" :style="{ '--i': i }">
                      <img
                        v-if="item.styleImage"
                        :src="item.styleImage"
                        :alt="item.styleName || ''"
                        class="h-full w-full rounded-md object-cover"
                        loading="lazy"
                      />
                      <span
                        v-else
                        class="flex h-full w-full items-center justify-center rounded-md bg-gray-200 px-1 text-center text-[11px] text-gray-500 dark:bg-slate-200/15 dark:text-slate-200"
                      >
                        {{ item.styleName || card.noImage }}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 玻璃的两道弧面阴影 + 压在中间那一格的中奖线 -->
            <span v-if="pool.length" class="fade fade-top" aria-hidden="true" />
            <span v-if="pool.length" class="fade fade-bottom" aria-hidden="true" />
            <span v-if="pool.length" class="payline" aria-hidden="true" />
          </div>

          <div class="tray">
            <p class="flex flex-wrap items-baseline gap-x-3 text-sm">
              <span v-if="drawText" class="text-xl font-semibold text-red-600 dark:text-red-400">
                ¥{{ drawText }}<span class="text-xs font-normal"> {{ card.drawPrice }}</span>
              </span>
              <span v-if="endBoxText" class="text-gray-600 dark:text-gray-300">
                ¥{{ endBoxText }}<span class="text-xs"> {{ card.endBoxPrice }}</span>
              </span>
              <span class="text-gray-500 dark:text-gray-400">{{ remainText }}</span>
            </p>
            <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
              {{ box.trendyName || card.noSeries }}
              <template v-if="box.itemCount != null">
                · {{ card.styles }} {{ box.itemCount }} {{ card.stylesUnit }}
              </template>
            </p>
          </div>

          <!-- 制动：街机式大圆按钮，按下去三根轴才开始跑（它是 <button>，键盘 Enter/Space 一样能按）。
               端盒那类盒的大按钮本身就是「购买整盒」，走的是不转动画那一条（见 buyWholeBox） -->
          <div class="console">
            <button
              type="button"
              class="fire"
              :disabled="spinning || buying || !pool.length || !canDraw"
              :data-open-login="isDraw && canDraw && pool.length && !signedIn ? '' : null"
              @click="isEndBoxType ? buyWholeBox() : spin()"
            >
              <span class="cap">{{ actionLabel }}</span>
            </button>
            <!-- 单抽/一番赏那两类盒旁边这一颗是「一趟买一整盒」（还有余量的款各一件、按端盒价结、
                 车里落一整盒一行）；它不转动，也不动那三根轴 -->
            <button
              v-if="canBuyBox && !isEndBoxType"
              type="button"
              class="rounded-lg border border-amber-300 bg-amber-50 px-4 py-2 text-sm font-medium text-amber-800 hover:bg-amber-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-amber-700/60 dark:bg-amber-900/30 dark:text-amber-200 dark:hover:bg-amber-900/50"
              :disabled="spinning || buying || !pool.length || !canDraw"
              :data-open-login="canDraw && pool.length && !signedIn ? '' : null"
              @click="buyWholeBox"
            >
              {{ t.buyBox }}<span v-if="buyBoxText" class="ml-1 tabular-nums">{{ buyBoxText }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <p v-if="fail" class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
      {{ fail }}
    </p>
    <!-- 不让按的那句用琥珀色压一下：它是这台机器的状态（还没开场 / 没配款式），不是刚才是谁点错了 -->
    <p v-else :class="blocked ? 'text-xs text-amber-600 dark:text-amber-400' : 'text-xs text-gray-400'">
      {{ tipText }}
    </p>

    <!-- 抽完弹窗说结果（机身右边不再摆文字）：三枚符号一排，隐藏款照样打标 -->
    <ModalOverlay v-if="resultOpen && dialogItems.length" @close="resultOpen = false">
      <div
        role="dialog"
        aria-modal="true"
        :aria-label="t.resultTitle"
        class="w-full max-w-md rounded-2xl border border-gray-200 bg-white p-5 shadow-xl dark:border-gray-700 dark:bg-gray-800"
      >
        <!-- 整盒那一趟不转动，弹窗说的也是「这一整盒」而不是「这一把抽到」 -->
        <h3 class="text-center text-base font-semibold">
          {{ wholeBoxResult ? t.boxResultTitle : (isDraw ? t.resultDrawTitle : t.resultTitle) }}
        </h3>

        <div class="mt-4 grid grid-cols-3 gap-3">
          <div v-for="(item, i) in dialogItems" :key="i" class="text-center">
            <div class="relative aspect-square overflow-hidden rounded-xl bg-gray-100 dark:bg-gray-700">
              <img
                v-if="item.styleImage"
                :src="item.styleImage"
                :alt="item.styleName || ''"
                class="h-full w-full object-cover"
              />
              <span
                v-else
                class="flex h-full w-full items-center justify-center px-1 text-[11px] text-gray-400"
              >
                {{ card.noImage }}
              </span>
              <span
                v-if="item.isHidden === 1"
                class="absolute left-1 top-1 rounded-full bg-amber-500 px-2 py-0.5 text-[10px] text-white"
              >
                {{ card.hidden }}
              </span>
            </div>
            <p class="mt-1 truncate text-xs text-gray-600 dark:text-gray-300">
              {{ item.styleName || d.common.dash }}
            </p>
          </div>
        </div>

        <!-- 真抽那档多报两句：这一把中了几个款（整盒那趟说「盒内共几件」）、这一趟该付多少（钱还没扣，下面那句说白） -->
        <p v-if="isDraw && won" class="mt-3 text-center text-sm">
          {{ fill(wholeBoxResult ? t.boxResultCount : t.resultCount, { n: won.drawnCount }) }}
          <span v-if="costText" class="ml-2 font-semibold text-red-600 dark:text-red-400">{{ costText }}</span>
        </p>

        <!-- 抽中的实物当场进了购物车，说一句让人知道去哪儿结算（抽签那一档不发实物，服务端给 0、这句就不出） -->
        <p v-if="cartedText" class="mt-1 text-center text-xs text-gray-500 dark:text-gray-400">
          {{ cartedText }}
          <a :href="href('/cart')" class="ml-1 text-blue-600 hover:underline dark:text-blue-400">
            {{ d.cart.title }} ›
          </a>
        </p>

        <p class="mt-3 text-center text-xs text-gray-400">{{ tipText }}</p>

        <div class="mt-4 flex items-center justify-center gap-4">
          <!-- 首页那台机才给这一颗（点它是去详情页）；详情页那台的款式弹窗就在机器旁边，不用给自己发一张跳转 -->
          <a
            v-if="!isDraw"
            class="text-sm text-blue-600 hover:underline dark:text-blue-400"
            :href="href(`/trendy/box/${box.id}`)"
          >
            {{ d.trendy.detail.stylesTitle }} ›
          </a>
          <button
            type="button"
            class="rounded-lg border border-gray-200 px-4 py-1.5 text-sm hover:bg-gray-100 dark:border-gray-600 dark:hover:bg-gray-700"
            @click="resultOpen = false"
          >
            {{ d.common.close }}
          </button>
        </div>
      </div>
    </ModalOverlay>
  </div>
</template>

<style scoped>
.machine {
  display: flex;
  align-items: flex-end;
  /* 深浅两档共用同一套形状，只把四处"读数"抽成变量（深色下不换就会糊成一片黑）：
     --drum-dim 滚筒侧面的暗度 —— 符号本来就压在深底上，再压到 0.55 侧边那几枚直接看不见，所以深色给得浅一档；
     --roll-blur 跑动中的糊度 —— 深色下糊 2px 会把符号边缘糊成一团灰，收一点；
     --fade 窗上下那两道弧面影 —— 浅色用深蓝灰，深色必须用纯黑才读得出来；
     --reel-face 鼓面本身 —— 浅色是一张浅灰纸，深色给一档上亮下暗的渐变，看着像有弧面。 */
  --drum-dim: 0.55;
  --roll-blur: 2px;
  --fade: rgb(15 23 42 / 0.3);
  --reel-face: rgb(248 250 252);
}
/* 深色档整块按"暗房里一台亮着的机器"来定：机身必须比页面底（gray-900，亮度约 0.013）亮一档，
   不然机身下半截等于背景，机器直接溶进背景里 = 他说的「机器变暗」。凹陷靠内阴影读，不靠把颜色压到近黑。 */
.dark .machine {
  --drum-dim: 0.82;
  --roll-blur: 1.3px;
  --fade: rgb(0 0 0 / 0.5);
  --reel-face: linear-gradient(180deg, #475569, #1f2937);
}
/* 机身摆正：侧转那一下（rotateY/rotateX）和配它存在的右侧「厚度」斜切条都撤了 ——
   立体的读数改成只靠玻璃窗的内阴影、机顶招牌和地上那道投影给（滚筒转动时的弧面照旧） */
.cab {
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
}
.cab-face {
  position: relative;
  border: 1px solid rgb(203 213 225);
  border-radius: 22px;
  padding: 14px 14px 16px;
  background: linear-gradient(165deg, #fff 0%, #eef2f7 46%, #dbe3ec 100%);
  box-shadow:
    0 24px 34px -20px rgb(15 23 42 / 0.45),
    inset 0 1px 0 rgb(255 255 255 / 0.9),
    inset 0 -20px 26px -20px rgb(15 23 42 / 0.2);
}
.dark .cab-face {
  border-color: rgb(100 116 139 / 0.55);
  background: linear-gradient(165deg, #64748b 0%, #475569 46%, #334155 100%);
  box-shadow:
    0 24px 34px -20px rgb(0 0 0 / 0.8),
    inset 0 1px 0 rgb(255 255 255 / 0.2),
    inset 0 -20px 26px -20px rgb(0 0 0 / 0.55);
}
/* 机身外面那圈冷光：深色背景上让机器"亮着"，边界也一眼分得清 */
.dark .cab {
  filter: drop-shadow(0 0 26px rgb(59 130 246 / 0.16));
}

.marquee {
  border-radius: 12px;
  padding: 6px 10px;
  text-align: center;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(180deg, #60a5fa, #2563eb);
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 0.45), 0 2px 6px rgb(37 99 235 / 0.35);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
/* 深色下这块招牌压暗一档：浅蓝那一条在深底上亮得像贴纸，收成一档宝蓝 + 外圈一点光，
   还是"机顶的灯箱"，但不刺眼 */
.dark .marquee {
  background: linear-gradient(180deg, #3b82f6, #1d4ed8);
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 0.35), 0 2px 14px rgb(59 130 246 / 0.5);
}

/* 玻璃窗：内阴影压出凹陷，上面那道高光当弧面 */
.glass {
  position: relative;
  margin-top: 12px;
  border-radius: 16px;
  padding: 10px;
  background: radial-gradient(120% 100% at 50% 0%, #fff 0%, #e2e8f0 58%, #cbd5e1 100%);
  box-shadow: inset 0 2px 7px rgb(15 23 42 / 0.35), inset 0 -2px 5px rgb(15 23 42 / 0.14);
}
.dark .glass {
  /* 窗比机身暗一档（读"陷进去"），但不能暗到接近页面底 —— 那样整块就是一块黑斑；
     凹陷交给内阴影那两刀 + 一道玻璃边高光，颜色只压到 slate-800 上下 */
  background: radial-gradient(120% 100% at 50% 0%, #3f4d63 0%, #2a3648 55%, #1b2534 100%);
  box-shadow:
    inset 0 2px 8px rgb(0 0 0 / 0.7),
    inset 0 -2px 6px rgb(0 0 0 / 0.45),
    inset 0 1px 0 rgb(148 163 184 / 0.3);
}
.reels {
  display: flex;
  gap: 8px;
}
.reel {
  position: relative;
  flex: 1 1 0;
  min-width: 0;
  overflow: hidden;
  border-radius: 12px;
  background: var(--reel-face);
  box-shadow: inset 0 0 0 1px rgb(148 163 184 / 0.4);
}
.dark .reel {
  box-shadow:
    inset 0 0 0 1px rgb(148 163 184 / 0.2),
    inset 0 10px 16px -12px rgb(0 0 0 / 0.95);
}
.strip {
  display: flex;
  flex-direction: column;
}
/* 正在跑的那条带子糊掉一点：滚动感的另一半是「快」这个读数（糊太重就看不见符号了） */
.strip.rolling {
  filter: blur(var(--roll-blur));
}
.cell {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  padding: 4px;
}
/* 滚筒那一层：每枚符号按自己在带子上的位置错峰前倾后仰（delay 由 --i 排出来），
   跑起来是一面弯的鼓在转，不是一张贴纸平推过窗；停格时这条动画自然摘掉，符号回正 */
.drum {
  width: 100%;
  height: 100%;
  transform-style: preserve-3d;
}
.strip.rolling .drum {
  animation: drum-bend 520ms linear infinite;
  animation-delay: calc(var(--i, 0) * -85ms);
}
@keyframes drum-bend {
  0%,
  100% {
    transform: perspective(300px) rotateX(26deg) scale(0.9);
    opacity: var(--drum-dim, 0.55);
  }
  50% {
    transform: perspective(300px) rotateX(0deg) scale(1);
    opacity: 1;
  }
}
.fade {
  position: absolute;
  left: 10px;
  right: 10px;
  height: 26px;
  pointer-events: none;
}
.fade-top {
  top: 10px;
  border-radius: 12px 12px 0 0;
  background: linear-gradient(180deg, var(--fade), transparent);
}
.fade-bottom {
  bottom: 10px;
  border-radius: 0 0 12px 12px;
  background: linear-gradient(0deg, var(--fade), transparent);
}
/* 中奖线：正好框住中间那一格（10px 是玻璃窗的内边距，88px 是一格高） */
.payline {
  position: absolute;
  left: 8px;
  right: 8px;
  top: calc(10px + 88px);
  height: 88px;
  border-top: 2px solid rgb(245 158 11 / 0.9);
  border-bottom: 2px solid rgb(245 158 11 / 0.9);
  pointer-events: none;
}
/* 深色给这条线一点外溢的光：黑底上两条 2px 实线太"贴纸"，发光才像机器里透出来的 */
.dark .payline {
  border-color: rgb(251 191 36 / 0.95);
  box-shadow: 0 0 16px rgb(245 158 11 / 0.35);
}

.tray {
  margin-top: 12px;
  border-radius: 12px;
  padding: 10px 12px;
  background: linear-gradient(180deg, rgb(255 255 255 / 0.85), rgb(241 245 249 / 0.7));
  box-shadow: inset 0 0 0 1px rgb(203 213 225 / 0.7);
}
.dark .tray {
  background: linear-gradient(180deg, rgb(148 163 184 / 0.22), rgb(100 116 139 / 0.14));
  box-shadow: inset 0 0 0 1px rgb(148 163 184 / 0.3);
}

/* 制动台：机身下面一颗街机式大圆按钮，按下去有行程（下面那道实心影就是"按到底"的读数）、松手弹回 */
.console {
  display: flex;
  align-items: center;
  justify-content: center;
  /* 大圆按钮旁边还可能站一颗「购买整盒」（单抽盒配了端盒价才有），两颗之间留一道缝 */
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 14px;
}
.fire {
  width: 104px;
  height: 44px;
  border: 0;
  padding: 0;
  border-radius: 9999px;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  background: linear-gradient(180deg, #f87171, #b91c1c);
  box-shadow:
    inset 0 2px 0 rgb(255 255 255 / 0.5),
    inset 0 -6px 10px rgb(127 29 29 / 0.55),
    0 6px 0 #7f1d1d,
    0 14px 20px -8px rgb(15 23 42 / 0.5);
  transition: transform 0.12s ease, box-shadow 0.12s ease, filter 0.12s ease;
}
.fire:hover:not(:disabled) {
  filter: brightness(1.07);
}
.fire:active:not(:disabled) {
  transform: translateY(5px);
  box-shadow:
    inset 0 2px 0 rgb(255 255 255 / 0.3),
    inset 0 -3px 8px rgb(127 29 29 / 0.6),
    0 1px 0 #7f1d1d,
    0 5px 10px -6px rgb(15 23 42 / 0.5);
}
/* 转着的时候这颗按不动：压下去一截、褪成灰的，看着就是"锁着" */
.fire:disabled {
  cursor: default;
  filter: saturate(0.3) brightness(0.95);
  transform: translateY(3px);
  box-shadow:
    inset 0 2px 0 rgb(255 255 255 / 0.25),
    0 3px 0 #6b7280;
}
.fire:focus-visible {
  outline: 2px solid rgb(37 99 235);
  outline-offset: 4px;
}
/* 深色：那颗"行程影"收近黑（#7f1d1d 在深底上会读成一圈脏红），高光也从 50% 压到 28%，
   不然这颗按钮在暗档里像贴了张亮纸，不像陷在面板里 */
.dark .fire {
  background: linear-gradient(180deg, #ef4444, #991b1b);
  box-shadow:
    inset 0 2px 0 rgb(255 255 255 / 0.28),
    inset 0 -6px 10px rgb(69 10 10 / 0.75),
    0 6px 0 #450a0a,
    0 16px 22px -8px rgb(0 0 0 / 0.85);
}
.dark .fire:active:not(:disabled) {
  box-shadow:
    inset 0 2px 0 rgb(255 255 255 / 0.18),
    inset 0 -3px 8px rgb(69 10 10 / 0.8),
    0 1px 0 #450a0a,
    0 5px 10px -6px rgb(0 0 0 / 0.8);
}
.cap {
  display: block;
  white-space: nowrap;
  text-shadow: 0 1px 0 rgb(127 29 29 / 0.8);
}
</style>
