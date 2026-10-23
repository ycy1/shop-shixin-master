<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { enumLabel, useI18n } from '@/i18n';
import ModalOverlay from '@/components/ModalOverlay.vue';
import { fetchMySignins, signinMakeup, signinToday, type SigninRow } from '@/lib/profile';

/**
 * close = 收框；changed = 签成了（带刚落的那一行）。
 * 签到会给分，所以外面那一页要听这一句去重拉积分那一格（同后台「个人信息」那头 @changed 刷账户行）。
 */
const emit = defineEmits<{ close: []; changed: [row: SigninRow] }>();

const { d, fill } = useI18n();

/** 日历一次只看一个月：这一格是 'yyyy-MM'，换月就重捞（判据同后台那份日历） */
const month = ref('');
/** 这个月签过的那几天，按 'yyyy-MM-dd' 收：接口不给没签的日子，格子由这里画成空的 */
const records = ref<Record<string, SigninRow>>({});
const loading = ref(false);
/** 整月至少捞成过一次：只有头一趟才挂那块「加载中」，翻月途中不许它压扁正在看的这一片 */
const ready = ref(false);
const error = ref('');
const notice = ref('');
/** 两动作各自一颗忙，别签今天的把补签那颗也灰掉 */
const busy = ref('');
/**
 * 最近连续几天：这一列不在表里（原来那列 continuous_days 已删），服务端从这个人最晚的那一天
 * 往前逐日回数（最多 7 天）现算，同一个数盖在返回的每一行上 ⇒ 读第一行就够了。
 * 它和"翻到哪一个月"不相干，所以换月捞到一个空月份时不跟着清成 0（那个月没行，数还是那一个数）。
 */
const currentContinuous = ref(0);

const WEEK_DAYS = d.signin.weekDays;
const todayText = ref('');
/** 本人补签的窗口，和后端 SELF_MAKEUP_DAYS 同一颗数（服务端那一句才是真闸，这里只是不给必然被拒的格子挂按钮） */
const SELF_MAKEUP_DAYS = 7;
const earliestMakeup = ref('');
/** 翻月往哪边滑（'yyyy-MM' 的字面序就是时间序）：滑之前要先定好，见 global.css 最后那一条 */
const slideName = ref('tab-next');

function dayText(date: Date) {
  const m = `${date.getMonth() + 1}`.padStart(2, '0');
  const d = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${m}-${d}`;
}

function monthText(year: number, m: number) {
  return `${year}-${`${m}`.padStart(2, '0')}`;
}

/** 'yyyy-MM' 拆两格：split 出来的类型上是「可能没有」，这一格定宽，按位切干净 */
function parseMonth(value: string): [number, number] {
  return [Number(value.slice(0, 4)), Number(value.slice(5, 7))];
}

/** 这一格签过没有：拿 'yyyy-MM-dd' 直接索引，接口给的那一格就是这一串 */
function recordOf(day: string) {
  return records.value[day];
}

/**
 * 今天签没签：这一句和「正看着哪一个月」不相干 —— 捞回来的是那一个月的行，翻到没有今天的那个月
 * 就跟着清成没签，这颗按钮会从「今天已签到」退回「今天签到」，点下去必被服务端拒。所以只钉成真、不往回放。
 */
const todaySigned = ref(false);
const signedDays = computed(() => Object.keys(records.value).length);
const makeupDays = computed(
  () => Object.values(records.value).filter((item) => Number(item.signType) === 2).length,
);
const monthPoints = computed(() =>
  Object.values(records.value).reduce((sum, item) => sum + Number(item.points || 0), 0),
);

/**
 * 日历那一片格子：竖着排满一周七天，月初前面补空（周一开头，和后台那页一致）。
 * 邻月的格子压根不画 —— 这一页按归属月捞记录，邻月那几天属于另一本账。
 */
const cells = computed(() => {
  const [year, m] = parseMonth(month.value);
  if (!year || !m) return [];
  // 1 号是本周第几天（周一记 0）：getDay() 给的是 0=周日，所以整体挪一位
  const leading = (new Date(year, m - 1, 1).getDay() + 6) % 7;
  const total = new Date(year, m, 0).getDate();
  const list: (string | null)[] = [];
  for (let i = 0; i < leading; i += 1) list.push(null);
  for (let d = 1; d <= total; d += 1) list.push(dayText(new Date(year, m - 1, d)));
  return list;
});

/**
 * 能不能补：过去的、没签过的、而且在窗口里。
 * 那一句「只能补最近 7 天」服务端有（ShopSigninServiceImpl#makeupSelf 的 SELF_MAKEUP_DAYS），
 * 前端收同一颗数只为不把按钮挂在点必然弹红的格子上，后台那份日历也是这一个判据。
 */
function canMakeup(day: string) {
  return day < todayText.value && day >= earliestMakeup.value && !records.value[day];
}

/** 捞某一个月的行，按日期索引返回；只读数据，不动页面上正看着的那一个月 */
async function fetchMonth(to: string) {
  const rows = await fetchMySignins(to);
  const map: Record<string, SigninRow> = {};
  for (const row of rows || []) {
    if (row.signDate) map[row.signDate] = row;
  }
  // 这个月里有今天那一行 ⇒ 今天签过了（翻到别月时不会再把这一句抹掉，见 todaySigned 那一条）
  if (map[todayText.value]) todaySigned.value = true;
  // 那一个数与月无关（服务端盖在返回的每一行上），所以这一页没行的那个月不去动它
  if (rows?.length) currentContinuous.value = Number(rows[0]?.currentContinuous ?? 0);
  return map;
}

/** 重捞正看着的这一个月：月没换、key 没变，格子就地补齐，不该滑 */
async function loadMonth() {
  loading.value = true;
  error.value = '';
  try {
    records.value = await fetchMonth(month.value);
    ready.value = true;
  } catch (err) {
    error.value = (err as Error).message;
  } finally {
    loading.value = false;
  }
}

/**
 * 换到某一个月：先捞目标月，数据到手那一刻才改 month（同一次提交里连 records 一起改）⇒
 * 移进来的那一块一开头就是那一个月的格子。先改月再捞，中途这一格会挂上「加载中」把日历压扁、
 * 「回到本月」也跟着冒出来，等数据回来又弹回去 —— 那就是翻月时看到的抖动闪。
 * 方向要在改 month 之前定（<Transition> 换 key 时就要拿到）；捞的这一段只把两颗翻月按钮置灰。
 */
async function goMonth(to: string) {
  if (to === month.value || loading.value) return;
  loading.value = true;
  error.value = '';
  try {
    const map = await fetchMonth(to);
    slideName.value = to > month.value ? 'tab-next' : 'tab-prev';
    month.value = to;
    records.value = map;
    ready.value = true;
  } catch (err) {
    error.value = (err as Error).message;
  } finally {
    loading.value = false;
  }
}

function shiftMonth(step: number) {
  const [year, m] = parseMonth(month.value);
  const next = new Date(year, m - 1 + step, 1);
  void goMonth(monthText(next.getFullYear(), next.getMonth() + 1));
}

function backToMonth(date: Date) {
  void goMonth(monthText(date.getFullYear(), date.getMonth() + 1));
}

/** 签今天 / 补签都只要一件事：把这一格补上，然后重捞这个月（分给了多少由服务端算，这里只回显它给的） */
async function act(kind: 'today' | string) {
  busy.value = kind;
  error.value = '';
  try {
    const row = kind === 'today' ? await signinToday() : await signinMakeup(kind);
    // 签的是今天，而看着的可能是别月 ⇒ 不能等下面那一趟重捞（那个月里没有今天这一行），当场钉上
    if (kind === 'today') todaySigned.value = true;
    await loadMonth();
    emit('changed', row);
    notice.value =
      kind === 'today'
        ? fill(d.signin.successToday, { n: row?.currentContinuous ?? 1, points: row?.points ?? 0 })
        : fill(d.signin.successMakeup, { day: kind, points: row?.points ?? 0 });
  } catch (err) {
    error.value = (err as Error).message;
  } finally {
    busy.value = '';
  }
}

onMounted(() => {
  const now = new Date();
  todayText.value = dayText(now);
  earliestMakeup.value = dayText(new Date(now.getFullYear(), now.getMonth(), now.getDate() - SELF_MAKEUP_DAYS));
  month.value = monthText(now.getFullYear(), now.getMonth() + 1);
  void loadMonth();
});
</script>

<template>
  <ModalOverlay @close="emit('close')">
    <!-- 竖向那条 bar 藏掉（Firefox 的 scrollbar-width + Chrome/Safari 的 ::-webkit-scrollbar），overflow-y-auto 留着 ⇒
         滚轮、拖内容、触屏滑动都照旧能滚，只是不画那条 bar。他 2026-10-06 点名「滚动条隐藏 就行」。 -->
    <div
      role="dialog"
      aria-modal="true"
      :aria-label="d.signin.title"
      class="max-h-full w-[640px] max-w-full overflow-y-auto rounded-xl bg-white p-6 shadow-xl [scrollbar-width:none] dark:bg-gray-800 [&::-webkit-scrollbar]:hidden"
    >
      <div class="mb-4 flex items-center justify-between">
        <h2 class="text-lg font-medium">{{ d.signin.title }}</h2>
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

      <!-- 汇总和那颗「今天签到」都摆在日历上面（后台那页同一位置）：这一页只读，唯一两个写入口就在这一排和格子里 -->
      <div class="mb-3 flex flex-wrap items-center justify-between gap-2 text-sm">
        <span class="text-gray-600 dark:text-gray-300">
          {{
            fill(d.signin.summary, {
              month: month,
              days: signedDays,
              makeup: makeupDays,
              points: monthPoints,
              continuous: currentContinuous,
            })
          }}
        </span>
        <button
          type="button"
          class="rounded-lg bg-blue-600 px-4 py-1.5 font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="busy !== '' || todaySigned"
          @click="act('today')"
        >
          {{ todaySigned ? d.signin.signedToday : busy === 'today' ? d.signin.signing : d.signin.signInToday }}
        </button>
      </div>

      <p v-if="notice" class="mb-3 rounded-lg bg-green-50 px-3 py-2 text-sm text-green-700 dark:bg-green-900/40 dark:text-green-300">
        {{ notice }}
      </p>
      <p v-if="error" class="mb-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
        {{ error }}
      </p>
      <p class="mb-3 text-xs text-gray-500 dark:text-gray-400">
        {{ fill(d.signin.makeupTip, { n: SELF_MAKEUP_DAYS }) }}
      </p>

      <div class="mb-1 flex items-center justify-between">
        <button
          type="button"
          class="rounded border border-gray-300 px-2 py-1 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
          :disabled="loading"
          @click="shiftMonth(-1)"
        >
          ‹ {{ d.signin.prevMonth }}
        </button>
        <span class="text-sm font-medium">{{ month }}</span>
        <button
          type="button"
          class="rounded border border-gray-300 px-2 py-1 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
          :disabled="loading"
          @click="shiftMonth(1)"
        >
          {{ d.signin.nextMonth }} ›
        </button>
      </div>
      <!-- 这一行高度钉死（有没有那颗都占 32px）：换月时这颗会冒出来或收回去，不钉住整片日历就跟着上下跳一下 -->
      <div class="mb-2 flex min-h-6 items-center">
        <button
          v-if="month !== todayText.slice(0, 7)"
          type="button"
          class="text-sm text-blue-600 hover:underline dark:text-blue-400"
          :disabled="loading"
          @click="backToMonth(new Date())"
        >
          {{ d.signin.backToThisMonth }}
        </button>
      </div>

      <div class="grid grid-cols-7 gap-1 text-center text-xs text-gray-500 dark:text-gray-400">
        <span v-for="w in WEEK_DAYS" :key="w">{{ w }}</span>
      </div>

      <!-- 整片格子跟着 month 换 key ⇒ 翻月是旧那份左右移走、新那份移进来（方向和判据同 global.css 最后那一条）。
           「加载中」只有头一趟挂（见 ready 那一条），翻月途中旧格子一直在这儿，所以移出去的是日历、不是那句提示。
           外面这一格常年挂 overflow-x-clip：弹窗有 overflow-y-auto ⇒ 它的横向也跟着可滚（那条 bar 虽已藏掉，不裁还是能往右拖出一截）。
           这里不跟别的页那样只在过渡那一段挂 —— 临时加 overflow 会断掉子元素的外边距合并，日历整片跟着挪 4px，
           而这弹窗里没有 hover 放大的卡片，常年裁掉横向探出的那点不影响任何东西。 -->
      <div class="overflow-x-clip">
        <Transition :name="slideName" mode="out-in">
          <div :key="month">
            <div v-if="!ready" class="py-10 text-center text-sm text-gray-500 dark:text-gray-400">{{ d.common.loading }}</div>
            <div v-else class="mt-1 grid grid-cols-7 gap-1">
              <div v-for="(day, index) in cells" :key="index" class="min-h-16 rounded border border-gray-100 p-1 text-left dark:border-gray-700">
                <template v-if="day">
                  <div class="flex items-center justify-between">
                    <span class="text-sm">{{ Number(day.slice(8, 10)) }}</span>
                    <span
                      v-if="recordOf(day)"
                      class="rounded px-1 text-[10px]"
                      :class="
                        Number(recordOf(day)?.signType) === 2
                          ? 'bg-amber-50 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300'
                          : 'bg-green-50 text-green-700 dark:bg-green-900/40 dark:text-green-300'
                      "
                    >
                      {{ enumLabel(d.enum.signType, Number(recordOf(day)?.signType), d.enum.signedIn) }}
                    </span>
                  </div>
                  <p v-if="recordOf(day)" class="mt-1 text-[11px] leading-tight text-gray-500 dark:text-gray-400">
                    {{ fill(d.signin.dayPoints, { n: recordOf(day)?.points ?? 0 }) }}
                  </p>
                  <button
                    v-else-if="canMakeup(day)"
                    type="button"
                    class="mt-1 text-[11px] text-blue-600 hover:underline disabled:opacity-50 dark:text-blue-400"
                    :disabled="busy !== ''"
                    @click="act(day)"
                  >
                    {{ busy === day ? d.signin.makingUp : d.signin.makeup }}
                  </button>
                </template>
              </div>
            </div>
          </div>
        </Transition>
      </div>

      <div class="mt-5 flex justify-end">
        <button
          type="button"
          class="rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
          @click="emit('close')"
        >
          {{ d.common.close }}
        </button>
      </div>
    </div>
  </ModalOverlay>
</template>
