<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue';
import { useI18n } from '@/i18n';
import ModalOverlay from '@/components/ModalOverlay.vue';
import type { PickedPoint } from '@/lib/map';

/**
 * 地图选点（照 shop-admin/src/components/ShopMap 那一份移植：对话框 + 地图面板 + 逆地理）
 *
 * shop-admin 那份用的是 el-dialog / el-input / el-button，这一页（商城前台）没有 Element Plus，
 * 所以外壳换成 Tailwind 手写，形状和判据一条没改：
 * 关键字检索定位、点地图落 marker、坐标反推地址、过期回来的逆地理结果丢弃、卸载 map.destroy()，
 * 以及最要紧的那一句 —— **点确定才抛一次**，弹窗里反复改点不影响外部；
 * 没挪过点就只当"打开看了眼位置"，抛出去的 address 置空，免得把用户手写的详细地址换成高德给的街道名。
 *
 * 地图挂了也能走完这一格：容器显示一句「地图加载失败，经纬度仍可手动填写」，
 * 表单上那两个数字输入框照旧收（口径同后台那份）。
 */

const props = withDefaults(
  defineProps<{
    /** 表单里现有的坐标：决定按钮文案，也是弹窗里地图的初始中心 */
    longitude?: number | null;
    latitude?: number | null;
  }>(),
  { longitude: null, latitude: null },
);

const emit = defineEmits<{ confirm: [point: PickedPoint] }>();

const { d, fill } = useI18n();

const open = ref(false);
const picked = ref<PickedPoint | null>(null);
/** 地址正在反推 / 反推失败的原因，只给地图下面那一行小字用 */
const resolving = ref(false);
const message = ref('');
const loadError = ref('');
const keyword = ref('');
const searching = ref(false);

/** 一个页面可能挂两张地图，容器 id 不能写死 */
const containerId = `amap-${Math.random().toString(36).slice(2, 8)}`;

/** 高德默认落在北京；没有现有坐标时地图开在这一格 */
const DEFAULT_CENTER: [number, number] = [116.397428, 39.90923];

const hasCoordinate = computed(
  () => props.longitude != null && props.latitude != null,
);
const buttonText = computed(() => (hasCoordinate.value ? d.map.repick : d.map.pick));

let AMap: any = null;
let map: any = null;
let marker: any = null;
let geocoder: any = null;
/** 那份 loader 的默认导出（动态引进来之后留着，失败时要靠它 reset 回可重取的那格状态）。
 *    它随包的 .d.ts 只声明了 load、没有 reset，所以这里不收类型，用 any 走。 */
let loader: any = null;
/** 高德在国内正常一兩秒就回来，给 15 秒是掐「卡在 loading 永远不 settle」那一头的 */
const LOAD_TIMEOUT_MS = 15000;
/** 最后一次点选，用来挡掉过期回来的逆地理结果 */
let lastPick: PickedPoint | null = null;
/** Geocoder 插件没挂上时的原因：JSAPI 能显示地图但服务接口全挂 */
let geocodeError = '';

function moveMarker(position: [number, number]) {
  if (marker) {
    marker.setPosition(position);
  } else {
    marker = new AMap.Marker({ position });
    map.add(marker);
  }
}

/** 逆地理：坐标先抛一次，地址回来再抛一次（不管成没成都收尾） */
function resolveAddress(lng: number, lat: number) {
  if (!geocoder) {
    resolving.value = false;
    message.value = geocodeError || d.map.geocodeUnavailable;
    picked.value = { longitude: lng, latitude: lat, address: '' };
    return;
  }
  resolving.value = true;
  message.value = '';
  picked.value = { longitude: lng, latitude: lat, address: '' };
  lastPick = picked.value;

  geocoder.getAddress([lng, lat], (status: string, result: any) => {
    // 详细地址列只放街道门牌，省市区另存 area_code / area_zh，所以优先取短格式；
    // 短格式偶尔为空（海上、边界点），兜底用完整地址
    const re = status === 'complete' && result.info === 'OK' ? result.regeocode : null;
    const address = re ? re.address || re.formattedAddress : '';
    // 失败就把高德的 status / info 原样带出去，不然页面上只看到"解析不出来"，定不了因
    message.value = address ? '' : `${status} / ${result?.info || d.map.noAddressReturned}`;
    // 异步回调可能晚于下一次点选回来，过期结果直接丢，别把新点覆盖成旧坐标
    if (lastPick && lastPick.longitude === lng && lastPick.latitude === lat) {
      picked.value = { longitude: lng, latitude: lat, address };
      resolving.value = false;
    }
  });
}

/** 落点：先移 marker，再解析地址 */
function pick(lng: number, lat: number) {
  moveMarker([lng, lat]);
  resolveAddress(lng, lat);
}

/** 输入地址检索：命中就把地图移到该点并落 marker，等同于一次点选 */
function handleSearch() {
  const kw = keyword.value.trim();
  if (!kw) return;
  if (!geocoder) {
    message.value = geocodeError || d.map.notLoaded;
    return;
  }
  searching.value = true;
  geocoder.getLocation(kw, (status: string, result: any) => {
    searching.value = false;
    const geo = status === 'complete' && result.info === 'OK' ? result.geocodes?.[0] : null;
    if (!geo) {
      message.value = fill(d.map.notFound, {
        kw,
        reason: status === 'complete' ? d.map.tryDetailed : `${status} / ${result?.info || d.map.noResult}`,
      });
      return;
    }
    message.value = '';
    const lng = geo.location.getLng();
    const lat = geo.location.getLat();
    map.setZoomAndCenter(16, [lng, lat]);
    pick(lng, lat);
  });
}

async function openDialog() {
  // 以外部现有坐标起头，不重新点也能直接确定
  picked.value = hasCoordinate.value
    ? { longitude: Number(props.longitude), latitude: Number(props.latitude), address: '' }
    : null;
  resolving.value = false;
  message.value = '';
  open.value = true;
  // 弹窗是 v-if 挂的，这一刻容器还不存在 —— 等 DOM 到位再加载 JSAPI
  await nextTick();
  if (!map) {
    // 引进来这一段本身也可能挂（网络、包没装），和 JSAPI 加载失败合成同一句提示
    loadMap().catch(reportLoadFailure);
  } else {
    map.resize();
  }
}

function reportLoadFailure(err: unknown) {
  console.error('高德地图加载失败：', err);
  // 这一格不能省：那份 loader 是个全局单例，它自己记着 AMap 那一格的状态，一旦落到 failed，
  // 之后每一次 load() 都当场 reject 一个空串、再也不去取脚本（它留 reset() 就是给这个用的）。
  // 换了客户端路由还可能是另一种更难的：它把 script 挂在 document.body 上，换页时 astro 是把 body 整条换掉的，
  // 一个正在加载的 script 被摘走既不抛 onload 也不抛 onerror ⇒ 状态永远停在 loading，之后每一趟都只是排进那条
  // 永不会触发的回调队列（表现是弹窗开着一片空白、连这句失败提示都看不到）。reset() 两种都收得回来。
  loader?.reset();
  AMap = null;
  map = null;
  loadError.value = d.map.loadFailed;
}

async function loadMap() {
  // 这个包在模块顶层就摸 window（`window._AMapSecurityConfig` 那份），静态 import 会让 SSR 那一趟直接抛
  // 「window is not defined」，整张 /user 页 500 —— 所以等到真要开地图这一刻才动态引进来
  if (!loader) {
    loader = (await import('@amap/amap-jsapi-loader')).default;
  }
  // 安全密钥要在 loader 之前挂到 window 上，否则 2.0 的 JSAPI 会拒（这一格从 .env 读，同后台那份）
  (window as any)._AMapSecurityConfig = {
    securityJsCode: import.meta.env.PUBLIC_AMAP_SECURITY_JS_CODE as string,
  };
  // 上面说的那种「卡在 loading」不会自己reject，所以这边自己掐一条时间，到点当失败处理、把状态清回去重取
  const timeout = new Promise((_, reject) => {
    setTimeout(() => reject(new Error(`高德 JSAPI ${LOAD_TIMEOUT_MS / 1000}s 内没回来`)), LOAD_TIMEOUT_MS);
  });
  return Promise.race([
    loader.load({
      key: import.meta.env.PUBLIC_AMAP_API_KEY as string,
      version: '2.0',
      plugins: ['AMap.Geocoder'],
    }),
    timeout,
  ])
    .then((res: any) => {
      AMap = res;
      const center: [number, number] = hasCoordinate.value
        ? [Number(props.longitude), Number(props.latitude)]
        : DEFAULT_CENTER;
      map = new AMap.Map(containerId, {
        viewMode: '3D',
        zoom: hasCoordinate.value ? 16 : 11,
        center,
        resizeEnable: true,
      });
      if (hasCoordinate.value) moveMarker(center);
      map.on('click', (e: any) => pick(e.lnglat.getLng(), e.lnglat.getLat()));

      // loader 的 plugins 参数在 2.0 下偶尔到回调时还没挂上，再用 AMap.plugin 兜一层
      //（已加载时它只是同步回调一次）；放在 click 绑定之后，插件出问题也不影响取点
      AMap.plugin(['AMap.Geocoder'], () => {
        if (typeof AMap.Geocoder === 'function') {
          geocoder = new AMap.Geocoder({ city: '全国' });
        } else {
          geocodeError = d.map.geocoderMissing;
        }
        // 打开时把现有坐标反推成地址，不然只有一串数字看不出落在哪儿
        if (hasCoordinate.value) resolveAddress(Number(props.longitude), Number(props.latitude));
      });
    })
    .catch(reportLoadFailure);
}

function confirm() {
  if (picked.value) {
    const moved =
      !hasCoordinate.value ||
      picked.value.longitude !== Number(props.longitude) ||
      picked.value.latitude !== Number(props.latitude);
    // 没挪点就只是"打开看了眼位置"，别把用户手写的详细地址换成高德给的街道名
    emit('confirm', moved ? { ...picked.value } : { ...picked.value, address: '' });
  }
  open.value = false;
}

function close() {
  open.value = false;
}

onBeforeUnmount(() => {
  map?.destroy();
  map = null;
  marker = null;
  geocoder = null;
  AMap = null;
  lastPick = null;
});
</script>

<template>
  <span class="inline-flex flex-col gap-1">
    <button
      type="button"
      class="rounded-lg border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
      @click="openDialog"
    >
      {{ buttonText }}
    </button>

    <ModalOverlay v-if="open" @close="close">
      <div
        role="dialog"
        aria-modal="true"
        :aria-label="d.map.dialogAria"
        class="max-h-full w-[900px] max-w-full overflow-y-auto rounded-xl bg-white p-6 shadow-xl dark:bg-gray-800"
      >
        <div class="mb-4 flex items-center justify-between">
          <h2 class="text-lg font-medium">{{ d.map.dialogAria }}</h2>
          <button
            type="button"
            :aria-label="d.common.close"
            class="rounded p-1 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
            @click="close"
          >
            <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path stroke-linecap="round" d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>

        <div class="mb-2 flex gap-2">
          <input
            v-model.trim="keyword"
            type="search"
            :placeholder="d.map.addressPlaceholder"
            class="min-w-0 flex-1 rounded-lg border border-gray-300 px-3 py-1.5 text-sm dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            @keyup.enter="handleSearch"
          />
          <button
            type="button"
            class="rounded-lg border border-gray-300 px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-60 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
            :disabled="searching"
            @click="handleSearch"
          >
            {{ searching ? d.map.locating : d.site.search }}
          </button>
        </div>

        <div :id="containerId" class="h-115 w-full overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-900"></div>
        <p v-if="loadError" class="mt-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
          {{ loadError }}
        </p>

        <!-- 选点结果那一行小字（后台那一份的 picked-tip 同一个位置） -->
        <p class="mt-2 break-all text-xs text-gray-500 dark:text-gray-400">
          <template v-if="picked">
            {{ picked.longitude.toFixed(6) }}, {{ picked.latitude.toFixed(6) }}
            <span v-if="picked.address">　{{ picked.address }}</span>
            <span v-else-if="resolving">　{{ d.map.resolvingAddress }}</span>
            <span v-else-if="message">　{{ fill(d.map.detailByHand, { message }) }}</span>
            <span v-else>　{{ d.map.existingCoord }}</span>
          </template>
          <template v-else>{{ d.map.clickToPick }}</template>
        </p>

        <div class="mt-5 flex justify-end gap-2">
          <button
            type="button"
            class="rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
            @click="close"
          >
            {{ d.common.cancel }}
          </button>
          <button
            type="button"
            class="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="!picked"
            @click="confirm"
          >
            {{ d.common.confirm }}
          </button>
        </div>
      </div>
    </ModalOverlay>
  </span>
</template>
