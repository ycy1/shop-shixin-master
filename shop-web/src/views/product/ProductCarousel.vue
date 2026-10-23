<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, type CSSProperties } from 'vue';
import ProductCard from './ProductCard.vue';
import type { WebProduct } from '@/lib/products';

const props = defineProps<{ products: WebProduct[] }>();

/** 卡片固定宽 288（w-72），环的半径按它算 */
const CARD_WIDTH = 288;
/**
 * 相邻两张之间夹 30°，不是 360/n。
 *
 * 按 360/n 分的话四张商品就是 90° 一张 —— 侧边那张正好侧对着人，看不见也点不着，
 * 「移到哪张哪张转到最前」就无从谈起。固定 30° 是一圈里能同时看见 5~6 张、
 * 每张都还朝着人的密度，转一格就是左右邻那张到正中。
 */
const STEP = 30;
/** 自动轮播的间隔。比那 500ms 的过渡长，转一格停一下让人看一眼 */
const AUTOPLAY_MS = 3500;

/** 转到正前方的是第几张 */
const active = ref(0);

/**
 * 自动轮播走一格的方向，走到头折返。
 *
 * 30° 一格要十二格才转得满一圈，商品不满 12 张时环上就有空槽：只朝一个方向走的话
 * 空槽会转到正前方来（现在六张上架商品，十二格里只有六格看得见东西）。
 */
let dir = 1;

/** 鼠标停在这一块上就别抢：指针明明压在侧边那张上，正面还在一格一格换就没法看 */
const hovered = ref(false);

/** 半宽 ÷ tan(半夹角) = 内切圆半径，这样相邻两张边贴边围成一圈，不会互相穿模 */
const radius = computed(() =>
  Math.round(CARD_WIDTH / 2 / Math.tan(((STEP / 2) * Math.PI) / 180)),
);

/**
 * 整圈往后退一个半径、再原地转：转到正前方那张刚好落在容器平面上（z=0、x=0），两侧的往后缩。
 *
 * 两步的顺序不能反（CSS 的 transform 从右往左作用）。写成 `rotateY() translateZ()` 是「先转、
 * 再沿转过的那根轴退」，退的方向被旋转带歪，正前方那张会偏到右边去（实测偏 268px）。
 * 完全不退这一步的话，正前方那张停在 translateZ(radius) 上，被透视放大到近两倍，窄屏直接溢出屏幕。
 */
const ringStyle = computed(() => ({
  transform: `translateZ(-${radius.value}px) rotateY(${-active.value * STEP}deg)`,
}));

/** 相对正前方的夹角，折到 [-180,180]：正对人是 0，转到侧面是 ±90，转到环后面就是背对了 */
function angleOf(index: number): number {
  let angle = ((index - active.value) * STEP) % 360;
  if (angle > 180) angle -= 360;
  if (angle < -180) angle -= 360;
  return angle;
}

function cardStyle(index: number): CSSProperties {
  const angle = angleOf(index);
  // 转过 ±90° 的那几张已经在环的另一侧，背面朝人：连点都不给，免得隔着环抢走悬停
  // （放大现在靠 elementFromPoint 认命中，这个 none 顺带把背面那几张从命中里摘干净）
  const turned = Math.abs(angle) > 90;
  return {
    transform:
      `rotateY(${index * STEP}deg) translateZ(${radius.value}px)` +
      // 移到哪张、这一张自己放大（整圈转过去那段由 ringStyle 管）
      (index === hoverIndex.value ? ` scale(${HOVER_SCALE})` : ''),
    opacity: turned ? '0' : String(1 - (Math.abs(angle) / 90) * 0.65),
    pointerEvents: turned ? 'none' : 'auto',
  };
}

/** 移到哪张就把它放大到这么大 */
const HOVER_SCALE = 1.08;
const hoverIndex = ref<number | null>(null);

/**
 * 悬停转格的闸门：按「指针真的挪了几个像素」认，不按时间认。
 *
 * 要挡的是这个自激：侧边那张被 focus 转到正中 ⇒ 它从指针底下游走了，顶上它原位的是邻一张 ⇒
 * 浏览器会朝静止的指针补抛一次 mouseenter ⇒ 又转一格 ⇒ 500ms 过渡里一帧一帧地跳。
 * 上一版这里是一条 550ms 的时间锁（比 duration-500 稍长），但时间一到、手还压在邻卡上，一晃又转，
 * 他还是报跳。改成位移：真把手挪过 24px 才算"我要换一张"。
 *
 * **放大不归这条闸管**（上一版两者绑在一起 ⇒ 他报「现在只有转到中间那张才放大」）：只挪几像素进邻卡时
 * 闸门一挡就不给放大，而 enter 是边沿触发的、之后不会再补发一回 ⇒ 那张一路不放大，非得移出去再移回来。
 * 现在放大和转环都挂在同一个 mousemove 上，各自认各自的：放大按实时命中哪张来定（挪一点就换），转环要过位移闸。
 * 卡片上不再收 enter/leave ⇒ 环自己换掉指针底下那张时压根不产生事件，上面那条自激链从源头就断了。
 */
const MOVE_GATE_PX = 24;
/** 环转过去那一段的时长，跟模板上的 duration-500 钉在一起 */
const RING_MS = 500;
const pointer = { x: 0, y: 0 };
// 初值放得很远：第一回移上去不该被闸门挡掉
const gateAt = { x: -10000, y: -10000 };
/** 环还在转的话，指针底下压着的是哪张要等转完再认（转的时候卡片正从指针底下扫过去） */
let ringStillUntil = 0;

/** 这一环的 DOM，用来问「指针此刻压在谁身上」 */
const ringEl = ref<HTMLElement>();

function cardUnderPointer(): number | null {
  const ring = ringEl.value;
  if (!ring) return null;
  const hit = document.elementFromPoint(pointer.x, pointer.y);
  if (!hit) return null;
  const index = [...ring.children].findIndex(card => card === hit || card.contains(hit));
  return index >= 0 ? index : null;
}

function trackPointer(event: MouseEvent) {
  pointer.x = event.clientX;
  pointer.y = event.clientY;
  if (Date.now() >= ringStillUntil) {
    const under = cardUnderPointer();
    if (under !== null) hoverIndex.value = under;
  }
  const target = hoverIndex.value;
  if (target === null || target === active.value) return;
  if (Math.hypot(pointer.x - gateAt.x, pointer.y - gateAt.y) < MOVE_GATE_PX) return;
  focus(target);
}

/** 点也算一次「要这一张」，顺手把放大挂到它身上；闸门跟着挪到当前指针位置，免得转完那一下的补抛又走一遍 */
function pick(index: number) {
  hoverIndex.value = index;
  focus(index);
}

/** 停在端点上还照老方向走的话下一格就撞墙，顺手把方向掰回来 */
function focus(index: number) {
  active.value = index;
  gateAt.x = pointer.x;
  gateAt.y = pointer.y;
  ringStillUntil = Date.now() + RING_MS;
  if (index >= props.products.length - 1) dir = -1;
  else if (index <= 0) dir = 1;
}

let timer: ReturnType<typeof setInterval> | undefined;

onMounted(() => {
  if (props.products.length < 2) return;
  timer = setInterval(() => {
    if (hovered.value) return;
    const last = props.products.length - 1;
    if (active.value + dir > last || active.value + dir < 0) dir = -dir;
    active.value += dir;
  }, AUTOPLAY_MS);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <!-- 透视挂在环外、preserve-3d 挂在环上：整圈一起转，转的是这一圈而不是单张卡片自己。
       横向照旧要裁（两侧摊出去的卡片在窄屏上会顶出横向滚动条），但纵向这一截不能靠"只裁一个轴"来留：
       实测 Chrome 把 overflow-x:clip + overflow-y:visible 当成两个轴一起裁（overflow-clip-margin 也不顶事），
       卡片放大长出来的那 17px（420 的 8%÷2）上下各被切一截。所以改成给外层留 20px 的纵向余量，
       让放大后的盒子整个落在裁切盒里，再用 -my-5 把版面拉回原位（实测下面那块的位置一格没动）。
       HOVER_SCALE 再往上调的话这里的 py-5 要跟着加宽。 -->
  <div
    class="w-full perspective-distant overflow-x-clip py-5 -my-5"
    @mousemove="trackPointer"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false; hoverIndex = null"
  >
    <div
      ref="ringEl"
      class="relative mx-auto h-[420px] w-72 transform-3d transition-transform duration-500"
      :style="ringStyle"
    >
      <div
        v-for="(product, index) in products"
        :key="product.id"
        class="absolute inset-0 backface-hidden transition duration-300"
        :style="cardStyle(index)"
        @click="pick(index)"
      >
        <ProductCard :product="product" :active="index === active" />
      </div>
    </div>
  </div>
</template>
