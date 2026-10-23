<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { FwbButton } from 'flowbite-vue';
import ProductCard from '@/views/product/ProductCard.vue';
import SearchBox from '@/views/search/SearchBox.vue';
import { fetchProducts, type WebProduct } from '@/lib/products';
import {
  fetchBrands,
  fetchStores,
  recordHotWord,
  type WebBrand,
  type WebStore,
} from '@/lib/search';
import { activityLabel, activityTimeText, fetchActivityPage, type WebActivityRow } from '@/lib/activities';
import { fetchPlazaPage, plazaLabel, plazaLeadImage, type PlazaMessage } from '@/lib/plaza';
import { enumLabel, useI18n } from '@/i18n';

/**
 * 搜索结果页：商品 / 门店 / 品牌 / 活动 / 广场 五个 tab。
 *
 * 关键字和落在哪个 tab 都由 search.astro 从 URL 上读好当 props 传进来，这一份不碰 window ——
 * 岛在服务端那一趟也要跑，读 window 直接 500；props 进去之后换页、刷新、分享链接都回得到同一张画面。
 * 翻页照个人中心那套「再看 N 个」往下续，不做首页那种滚动加载：搜索结果得让人看见一共捞到多少。
 */
type TabKey = 'product' | 'store' | 'brand' | 'activity' | 'plaza';

/** locale 由页面壳递进来：岛里拿不到 Astro 对象（见 @/i18n 顶上那段） */
const props = defineProps<{ keyword?: string; tab?: string; locale?: string }>();
const { d, fill, href } = useI18n();

const PAGE_SIZE = 12;

interface Block<T> {
  rows: T[];
  page: number;
  pages: number;
  total: number;
  loading: boolean;
  error: string;
}

type AnyBlock = Block<unknown>;
type Fetcher = (page: number, size: number) => Promise<{ records: unknown[]; total: number; pages: number }>;

const products = reactive<Block<WebProduct>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' });
const stores = reactive<Block<WebStore>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' });
const brands = reactive<Block<WebBrand>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' });
const activities = reactive<Block<WebActivityRow>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' });
const plazas = reactive<Block<PlazaMessage>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' });

/** 空白串不算关键字：后端那两条筛选用的是 isNotBlank，前端也按同一口径决定标题和空结果怎么说 */
const kw = computed(() => props.keyword?.trim() || undefined);

interface TabItem {
  key: TabKey;
  label: string;
  /** 「共 12 件」那个量词，三块各叫各的 */
  unit: string;
  noun: string;
  block: AnyBlock;
  fetch: Fetcher;
}

const TABS: TabItem[] = [
  {
    key: 'product',
    label: d.enum.product,
    unit: d.search.unitProduct,
    noun: d.search.nounProduct,
    block: products,
    fetch: (page, size) => fetchProducts({ keyword: kw.value, pageNum: page, pageSize: size }),
  },
  {
    key: 'store',
    label: d.enum.store,
    unit: d.search.unitStore,
    noun: d.search.nounStore,
    block: stores,
    fetch: (page, size) => fetchStores({ keyword: kw.value, pageNum: page, pageSize: size }),
  },
  {
    key: 'brand',
    label: d.enum.brand,
    unit: d.search.unitBrand,
    noun: d.search.nounBrand,
    block: brands,
    fetch: (page, size) => fetchBrands({ keyword: kw.value, pageNum: page, pageSize: size }),
  },
  {
    key: 'activity',
    label: d.site.activity,
    unit: d.search.unitActivity,
    noun: d.search.nounActivity,
    block: activities,
    // 读的是头部菜单那一页同一条接口，只是多带一个关键字（后端按名称或标题命中都行），判据没有第二套
    fetch: (page, size) => fetchActivityPage(page, size, kw.value),
  },
  {
    key: 'plaza',
    label: d.site.plaza,
    unit: d.search.unitPlaza,
    noun: d.search.nounPlaza,
    block: plazas,
    // 同样是复用广场那一页的列表接口：keyword 命中标题、正文摘要和发布者昵称（那一刀在 SQL 里），
    // 可见性还是服务层钉的 status=1 且 audit_status=1 —— 搜索这一头插不进第二套判据。
    // 唯一多带的一格是 orderByLike：搜索结果里这一块按赞最多的排最前（广场那一页本身的流不动）。
    fetch: (page, size) => fetchPlazaPage(page, size, { keyword: kw.value, orderByLike: 1 }),
  },
];

/**
 * 五个 tab 上的命中数要一进来就都摆出来（不等到切过去才知道那块空不空）。
 * 后端没有现成的 count 口，所以按 pageSize=1 各发一条、只读信封里那个 total —— 比另开五条接口便宜。
 * null = 还没查到（那一格数字不出现，不写 0，0 会被当成「真的一条都没有」）。
 */
const counts = reactive<Record<TabKey, number | null>>({
  product: null,
  store: null,
  brand: null,
  activity: null,
  plaza: null,
});

/** URL 上那串 tab 认不出来（手打的 /search?tab=xxx）就当没填，落回商品那一块 */
function pickTab(tab: string | undefined): TabKey {
  return TABS.some((item) => item.key === tab) ? (tab as TabKey) : 'product';
}

const active = ref<TabKey>(pickTab(props.tab));
/** 这次是往后切还是往前切（决定新块从哪边移进来）；sliding 只在过渡那一段挂上，见 global.css 最后那一条 */
const slideName = ref('tab-next');
const sliding = ref(false);
const current = computed(() => TABS.find((item) => item.key === active.value)!);
const hasMore = computed(() => current.value.block.page < current.value.block.pages);

/**
 * 五块的面板各画各的，所以行也各给一条带类型的口子：
 * 模板里不做 `as` 断言（v-for 的表达式源不认这套），current.block.rows 那格是 unknown[]，只能在这里收窄。
 */
const productRows = computed(() => products.rows);
const storeRows = computed(() => stores.rows);
const brandRows = computed(() => brands.rows);
const activityRows = computed(() => activities.rows);
const plazaRows = computed(() => plazas.rows);

/** 广场那一行第二个字段用的作者：账号注销了联不上昵称，只剩 id（口径同广场列表那张卡片） */
function plazaAuthor(one: PlazaMessage) {
  const name = one.userName?.trim();
  if (name) return name;
  return one.userId ? fill(d.plazaList.authorUnknown, { n: one.userId }) : d.common.dash;
}

/** 字典列都可能回 null（老数据没填），取不到说法就当那一格没有 */
function storeTypeText(type: number | null) {
  return enumLabel(d.enum.storeType, type, '');
}

function businessStatusText(status: number | null) {
  return enumLabel(d.enum.businessStatus, status, '');
}

/**
 * 换 tab 顺手把它写进地址栏（replaceState，不新增一条历史）：
 * 顶上的搜索框读的就是这一格，所以「在这个 tab 里换个词再搜」和手动刷新都还留在这一块。
 * 商品是默认那一块，回落到不带 tab，地址栏短一截，别人复制出去的链接也不会钉死在商品上。
 */
function syncUrl(key: TabKey) {
  const url = new URL(window.location.href);
  if (key === 'product') {
    url.searchParams.delete('tab');
  } else {
    url.searchParams.set('tab', key);
  }
  window.history.replaceState(null, '', url);
}

async function load(item: TabItem) {
  const block = item.block;
  block.loading = true;
  block.error = '';
  try {
    const data = await item.fetch(block.page + 1, PAGE_SIZE);
    // 两页之间有人上下架，同一条可能被捞两次 ⇒ 按 id 去重再拼
    const seen = new Set(block.rows.map((row) => (row as { id: number }).id));
    block.rows.push(...data.records.filter((row) => !seen.has((row as { id: number }).id)));
    block.page += 1;
    block.pages = data.pages ?? 0;
    block.total = data.total ?? 0;
    // 这一块捞过了，tab 上那个数就以它自己的 total 为准（那条 pageSize=1 的探数可能比它早回、也可能压根没发）
    counts[item.key] = block.total;
  } catch (err) {
    block.error = (err as Error).message;
  } finally {
    block.loading = false;
  }
}

/** 只探还没捞过的那几块：已经捞过的块上面那条已经把真数写进去了，再探一遍就是多一趟后端 */
function readCount(item: TabItem) {
  return item
    .fetch(1, 1)
    .then((data) => {
      if (item.block.page === 0) counts[item.key] = data.total ?? 0;
    })
    .catch(() => {
      // 数没探着不拦页面：那一格本来就空着，等他真切过去那一块自己会报错
    });
}

function selectTab(key: TabKey) {
  if (active.value === key) return;
  const from = TABS.findIndex((tab) => tab.key === active.value);
  slideName.value = TABS.findIndex((tab) => tab.key === key) > from ? 'tab-next' : 'tab-prev';
  active.value = key;
  syncUrl(key);
  const item = TABS.find((tab) => tab.key === key)!;
  // page === 0 才是「这一块还没捞过」；空结果也算捞过，否则每次切回来都重发一遍
  if (item.block.page === 0 && !item.block.loading) void load(item);
}

function loadMore() {
  if (current.value.block.loading) return;
  void load(current.value);
}

onMounted(() => {
  // 当前这一块直接捞整页（那一页回来就带着真数），其余四块各探一条 pageSize=1 先把数摆上 tab
  void load(current.value);
  void Promise.all(TABS.filter((item) => item.key !== active.value).map(readCount));

  // 给首页那一排热搜记一票：只有这一趟真是一次搜索。没关键字就是「浏览全部」，不计。
  // 切 tab、翻页都不再来一趟 —— 那一趟是按 pageSize=1 给四块各探一次数，挂在读接口上就成了搜一次计四票。
  if (kw.value) {
    recordHotWord(kw.value);
  }
});
</script>

<template>
  <section :class="{ 'overflow-x-clip': sliding }">
    <SearchBox :keyword="props.keyword" :tab="active" class="mx-auto mb-6 max-w-2xl" />

    <p class="mb-4 text-center text-sm text-gray-500 dark:text-gray-400">
      <template v-if="kw">{{ fill(d.search.resultFor, { kw: kw ?? '' }) }}</template>
      <template v-else>{{ fill(d.search.showAllNoKw, { noun: current.noun }) }}</template>
    </p>

    <div
      role="tablist"
      :aria-label="d.search.scope"
      class="mb-6 flex justify-center gap-6 border-b border-gray-200 dark:border-gray-700"
    >
      <button
        v-for="item in TABS"
        :key="item.key"
        type="button"
        role="tab"
        :aria-selected="active === item.key"
        class="-mb-px border-b-2 pb-2 text-sm font-medium transition-colors"
        :class="
          active === item.key
            ? 'border-blue-600 text-blue-600 dark:border-blue-400 dark:text-blue-400'
            : 'border-transparent text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-200'
        "
        @click="selectTab(item.key)"
      >
        {{ item.label }}
        <span v-if="counts[item.key] != null" class="ml-1 text-xs text-gray-400">{{ counts[item.key] }}</span>
      </button>
    </div>

    <!-- 只画当前这一块：另两块切过去才捞，DOM 里也不留着另两块的行。
         换 key ⇒ Vue 认成换了一个元素，<Transition> 于是把旧那块左右移走完事再挂新的（方向见 slideName，CSS 在 global.css 最后那一条）。
         sliding 只在这段过渡里给整页挂 overflow-x-clip：那 28px 的位移会探出容器，不裁就闪一条横向滚动条；
         平时不挂，免得把商品卡片 hover 放大那 8% 也一起裁掉。 -->
    <Transition :name="slideName" mode="out-in" @before-leave="sliding = true" @after-enter="sliding = false">
      <div :key="active" role="tabpanel" :aria-label="fill(d.search.tabPanelAria, { label: current.label })">
        <!-- 报错只在这一块还空着的时候顶掉整块：已经捞到几页了就说下面那一行，别把画出来的东西撤走（和首页续页同一处理） -->
        <p v-if="current.block.loading && !current.block.rows.length" class="py-10 text-center text-sm text-gray-500 dark:text-gray-400">
          {{ d.common.loading }}
        </p>

        <p v-else-if="current.block.error && !current.block.rows.length" class="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
          {{ current.block.error }}
          <button type="button" class="ml-2 underline" @click="loadMore">{{ d.search.retry }}</button>
        </p>

        <p v-else-if="!current.block.rows.length" class="py-10 text-center text-sm text-gray-500 dark:text-gray-400">
          {{ kw ? fill(d.search.noMatch, { noun: current.noun }) : fill(d.search.noneYet, { noun: current.noun }) }}
        </p>

        <!-- 商品：和首页一排同样的卡片、同样的放大 -->
        <div v-else-if="active === 'product'" class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <div
            v-for="row in productRows"
            :key="row.id"
            class="relative h-[420px] transition-transform duration-300 hover:z-10 hover:scale-[1.08]"
          >
            <ProductCard :product="row" />
          </div>
        </div>

        <!-- 门店 -->
        <div v-else-if="active === 'store'" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <a
            v-for="row in storeRows"
            :key="row.id"
            :href="href(`/store/${row.id}`)"
            class="group flex cursor-pointer gap-3 rounded-lg border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800"
          >
            <img
              v-if="row.logo"
              :src="row.logo"
              :alt="row.storeName"
              class="h-14 w-14 shrink-0 rounded object-cover"
              loading="lazy"
            />
            <span
              v-else
              class="flex h-14 w-14 shrink-0 items-center justify-center rounded bg-gray-100 text-lg font-semibold text-gray-400 dark:bg-gray-700"
            >
              {{ row.storeName.charAt(0) }}
            </span>

            <div class="min-w-0 flex-1">
              <div class="flex items-start justify-between gap-2">
                <h3 class="truncate text-base font-semibold text-gray-900 group-hover:underline dark:text-gray-100" :title="row.storeName">
                  {{ row.storeName }}
                </h3>
                <span
                  v-if="row.businessStatus != null"
                  class="shrink-0 rounded px-1.5 py-0.5 text-xs"
                  :class="
                    row.businessStatus === 1
                      ? 'bg-green-50 text-green-700 dark:bg-green-900/40 dark:text-green-300'
                      : 'bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-400'
                  "
                >
                  {{ businessStatusText(row.businessStatus) }}
                </span>
              </div>

              <p class="mt-1 flex flex-wrap gap-x-2 text-xs text-gray-500 dark:text-gray-400">
                <span v-if="storeTypeText(row.storeType)">{{ storeTypeText(row.storeType) }}</span>
                <span v-if="row.isDelivery === 1" class="text-blue-600 dark:text-blue-400">{{ d.search.deliverySupported }}</span>
              </p>

              <p class="mt-1.5 line-clamp-2 text-sm text-gray-700 dark:text-gray-300" :title="row.address ?? undefined">
                {{ [row.areaZh, row.address].filter(Boolean).join(' ') || d.search.noAddress }}
              </p>
              <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                <span v-if="row.contactPhone">{{ row.contactPhone }}</span>
                <span v-if="row.businessHours" class="ml-2">{{ row.businessHours }}</span>
              </p>
            </div>
          </a>
        </div>

        <!-- 品牌 -->
        <div v-else-if="active === 'brand'" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <a
            v-for="row in brandRows"
            :key="row.id"
            :href="href(`/brand/${row.id}`)"
            class="group flex cursor-pointer gap-3 rounded-lg border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800"
          >
            <img
              v-if="row.logo"
              :src="row.logo"
              :alt="row.brandName"
              class="h-14 w-14 shrink-0 rounded object-contain"
              loading="lazy"
            />
            <span
              v-else
              class="flex h-14 w-14 shrink-0 items-center justify-center rounded bg-gray-100 text-lg font-semibold text-gray-400 dark:bg-gray-700"
            >
              {{ row.brandName.charAt(0) }}
            </span>

            <div class="min-w-0 flex-1">
              <h3 class="truncate text-base font-semibold text-gray-900 group-hover:underline dark:text-gray-100" :title="row.brandName">
                {{ row.brandName }}
              </h3>
              <p class="mt-1 line-clamp-2 text-sm text-gray-500 dark:text-gray-400" :title="row.description ?? undefined">
                {{ row.description || d.search.noBrandDesc }}
              </p>
            </div>
          </a>
        </div>

        <!-- 活动：形状照门店/品牌那两块（图 + 名 + 一行次要信息），点进去是活动详情页，不是这条活动自己的跳转目标
             —— 目标在那一页上才摆得清（它还要先让人看到活动内容）。名称和投放起止的算法和 /activity 那一页共用 lib 那两份。 -->
        <div v-else-if="active === 'activity'" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <a
            v-for="row in activityRows"
            :key="row.id"
            :href="href(`/activity/${row.id}`)"
            class="group flex cursor-pointer gap-3 rounded-lg border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800"
          >
            <img
              v-if="row.image"
              :src="row.image"
              :alt="activityLabel(row, d)"
              class="h-14 w-14 shrink-0 rounded object-cover"
              loading="lazy"
            />
            <span
              v-else
              class="flex h-14 w-14 shrink-0 items-center justify-center rounded bg-gray-100 text-lg font-semibold text-gray-400 dark:bg-gray-700"
            >
              {{ activityLabel(row, d).charAt(0) }}
            </span>

            <div class="min-w-0 flex-1">
              <h3 class="truncate text-base font-semibold text-gray-900 group-hover:underline dark:text-gray-100" :title="activityLabel(row, d)">
                {{ activityLabel(row, d) }}
              </h3>
              <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
                {{ activityTimeText(row, d) || d.search.activityRunning }}
              </p>
            </div>
          </a>
        </div>

        <!-- 广场：形状照门店/品牌/活动那三块（配图 + 标题 + 两行次要信息），点进去是这条消息的详情页。
             标题的算法（没写标题就拿正文摘要顶上前 40 字）和广场列表、右侧推荐位共用 lib/plaza 那一颗，
             第二行也是同一份「发布者 · 发布日期」，只是这里到日就够（列表那张卡片到分钟）。
             第三行是点赞量与分享量：只念数不给按钮（这一整块是条链接，套不进两个可点的控件），
             说法复用 plazaDetail 那两格（广场列表卡片上的按钮印的就是同样两个词）。
             配图那一格和广场列表共用 global.css 的 .plaza-cover（一串里取第一张；尺寸不能写成新添的工具类，理由那一段说了）。 -->
        <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <a
            v-for="row in plazaRows"
            :key="row.id"
            :href="href(`/plaza/${row.id}`)"
            class="group flex cursor-pointer gap-3 rounded-lg border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800"
          >
            <img v-if="plazaLeadImage(row)" :src="plazaLeadImage(row)" alt="" loading="lazy" class="plaza-cover" />
            <span v-else class="plaza-cover">{{ plazaLabel(row, d.plazaList.noTitle).charAt(0) }}</span>

            <div class="min-w-0 flex-1">
              <h3
                class="truncate text-base font-semibold text-gray-900 group-hover:underline dark:text-gray-100"
                :title="plazaLabel(row, d.plazaList.noTitle)"
              >
                {{ plazaLabel(row, d.plazaList.noTitle) }}
              </h3>
              <p class="mt-1 truncate text-sm text-gray-500 dark:text-gray-400">
                {{ plazaAuthor(row) }} · {{ (row.publishTime || row.createTime || '').slice(0, 10) || d.common.dash }}
              </p>
              <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
                {{ d.plazaDetail.like }} · {{ row.likeCount ?? 0 }}
                <span class="ml-2">{{ d.plazaDetail.share }} · {{ row.shareCount ?? 0 }}</span>
              </p>
            </div>
          </a>
        </div>

        <div v-if="current.block.rows.length" class="mt-6 flex flex-col items-center gap-2">
          <p v-if="current.block.error" class="text-sm text-red-600 dark:text-red-400">
            {{ fill(d.search.nextFailed, { error: current.block.error }) }}
            <button type="button" class="ml-2 underline" @click="loadMore">{{ d.search.retry }}</button>
          </p>
          <span class="text-sm text-gray-500 dark:text-gray-400">{{ fill(d.common.total, { n: current.block.total, unit: current.unit }) }}</span>
          <FwbButton v-if="hasMore && !current.block.error" color="blue" size="sm" :disabled="current.block.loading" @click="loadMore">
            {{ current.block.loading ? d.common.loading : fill(d.search.more, { n: PAGE_SIZE, unit: current.unit }) }}
          </FwbButton>
        </div>
      </div>
    </Transition>
  </section>
</template>
