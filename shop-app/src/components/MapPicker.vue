<script lang="ts" setup>
defineOptions({
  name: 'MapPicker',
})

/**
 * 地图选点：照 shop-web 的 components/MapPicker.vue（再由它追到 shop-admin 的 ShopMap）移植过来的。
 *
 * 判据一条没改：关键字检索定位、点地图落 marker、坐标反推地址、过期回来的逆地理结果丢掉、
 * 关掉弹层只是藏起来（地图实例留着，下一次开只 resize），页面走掉时才 map.destroy()，
 * 以及最要紧的那一句 —— **点确定才抛一次**，弹层里反复改点不影响外面。
 * 没挪过点就只当「打开看了眼位置」，抛出去的 address 置空，免得把用户手写的详细地址换成高德给的街道名。
 *
 * 三端走两条路（高德那份 JSAPI 是浏览器端的脚本，小程序和 App 里没有 DOM，加载不起来）：
 * - H5：高德 JSAPI，key 和安全密钥从 env 读（VITE_AMAP_API_KEY / VITE_AMAP_SECURITY_JS_CODE，同 web 那两份）；
 * - 微信小程序 / 原生 App：uni.chooseLocation 弹各自内置的选点页，回来的经纬度也是 GCJ-02，
 *   和高德那两位同一个坐标系，所以库里这两列不用换算法。
 *   小程序那一路要 manifest 里声明 chooseLocation 权限，改完得重新编译；
 *   App 那一路要 manifest 的 App 模块配置里勾上地图（sdkConfigs.maps 的高德 appkey 分 Android / iOS 两格，
 *   和 JSAPI 那个「Web端」key 不是同一张），真机跑官方标准基座时先用 DCloud 那份公共 key 就够，正式打包才必须填自己的。
 *
 * 地图挂了这条路还留着：这一格抛不出数，页面上那两个手填的经纬度输入框照旧收（口径同后台那句提示）。
 *
 * 两处和母本不一样，都是被这一格的用处逼出来的：弹层里地图不一定看得见动，所以视图收 2D、检索命中就瞬时跳过去，
 * 并把「确定」摆在「检索」后面（搜到即能定，不必靠点图）；另外关掉弹层不销毁地图，只 resize，销毁留给页面走掉。
 */

const props = withDefaults(defineProps<{
  /** 表单里现有的坐标：决定按钮文案，也是地图的初始中心 */
  longitude?: number | null
  latitude?: number | null
}>(), {
  longitude: null,
  latitude: null,
})

const emit = defineEmits<{
  confirm: [point: { longitude: number, latitude: number, address: string }]
}>()

const show = ref(false)
const keyword = ref('')
/** 已选中的那一点（还没提交，点确定才抛出去） */
const picked = ref<{ longitude: number, latitude: number, address: string } | null>(null)
const message = ref('')
const loadError = ref('')
const busy = ref(false)

/** 一个页面可能挂两张地图，容器 id 不能写死 */
const containerId = `amap-${Math.random().toString(36).slice(2, 8)}`

/** 高德默认落在北京；没有现有坐标时地图开在这一格 */
const DEFAULT_CENTER: [number, number] = [116.397428, 39.90923]

const hasCoordinate = computed(() => props.longitude != null && props.latitude != null)
const buttonText = computed(() => hasCoordinate.value ? '重新选点' : '地图选点')
/** 弹层下面那一行小字：让选完的人看得见自己点到哪儿了 */
const pickedText = computed(() => {
  if (!picked.value) {
    return '点地图上的位置，或上面搜一下地址'
  }
  const point = `${picked.value.longitude.toFixed(6)}, ${picked.value.latitude.toFixed(6)}`
  if (picked.value.address) {
    return `${point} · ${picked.value.address}`
  }
  return message.value ? `${point} · ${message.value}` : point
})

// #ifdef H5
let AMap: any = null
let map: any = null
let marker: any = null
let geocoder: any = null
/** 那份 loader 的默认导出：失败时要靠它 reset 回可重取的状态（它是个全局单例，自己记着加载状态） */
let loader: any = null
/** 最后一次点选，用来挡掉过期回来的逆地理结果 */
let lastPick: { longitude: number, latitude: number, address: string } | null = null
/** Geocoder 插件没挂上时的原因：JSAPI 能显示地图但服务接口全挂 */
let geocodeError = ''

const LOAD_TIMEOUT_MS = 15000

function movePoint(position: [number, number]) {
  if (marker) {
    marker.setPosition(position)
  }
  else {
    marker = new AMap.Marker({ position })
    map.add(marker)
  }
}

/** 逆地理：坐标先记下，地址回来再补一次（不管成没成都收尾） */
function resolveAddress(lng: number, lat: number) {
  if (!geocoder) {
    picked.value = { longitude: lng, latitude: lat, address: '' }
    message.value = geocodeError || '地址反推用不了，坐标照旧能提'
    return
  }
  busy.value = true
  message.value = ''
  picked.value = { longitude: lng, latitude: lat, address: '' }
  lastPick = picked.value

  geocoder.getAddress([lng, lat], (status: string, result: any) => {
    // 详细地址列只放街道门牌，省市区另存 area_code / area_zh，所以优先取短格式
    const re = status === 'complete' && result.info === 'OK' ? result.regeocode : null
    const address = re ? re.address || re.formattedAddress : ''
    // 失败就把高德的 status / info 原样带出去，不然页面上只看到「解析不出来」，定不了因
    message.value = address ? '' : `${status} / ${result?.info || '没返回地址'}`
    // 异步回调可能晚于下一次点选回来，过期结果直接丢，别把新点覆盖成旧坐标
    if (lastPick && lastPick.longitude === lng && lastPick.latitude === lat) {
      picked.value = { longitude: lng, latitude: lat, address }
      busy.value = false
    }
  })
}

function pick(lng: number, lat: number) {
  movePoint([lng, lat])
  resolveAddress(lng, lat)
}

/** 输入地址检索：命中就把地图移到该点并落 marker，等同于一次点选 */
function handleSearch() {
  const kw = keyword.value.trim()
  if (!kw) {
    return
  }
  if (!geocoder) {
    message.value = geocodeError || '地图还没起来'
    return
  }
  busy.value = true
  geocoder.getLocation(kw, (status: string, result: any) => {
    busy.value = false
    const geo = status === 'complete' && result.info === 'OK' ? result.geocodes?.[0] : null
    if (!geo) {
      message.value = `没找到「${kw}」${status === 'complete' ? '，写详细一点再试' : ` / ${result?.info || '高德没返回结果'}`}`
      return
    }
    message.value = ''
    const lng = geo.location.getLng()
    const lat = geo.location.getLat()
    // 第三个参数是 immediately：默认那一档平移靠动画帧推，页面在后台或被弹层挡着时 rAF 一冻，
    // 地图就停在原地不动（坐标其实已经换了，看不出）。直接跳过去，不指望动画
    map.setZoomAndCenter(16, [lng, lat], true)
    pick(lng, lat)
  })
}

function reportLoadFailure(err: unknown) {
  console.error('高德地图加载失败：', err)
  // 这一格不能省：落到 failed 之后每一次 load() 都当场 reject、再也不去取脚本；
  // 而它把 script 挂在 body 上，换页时整条 body 被换掉既不抛 onload 也不抛 onerror ⇒ 状态永远停在 loading
  loader?.reset()
  AMap = null
  map = null
  loadError.value = '地图加载失败，经纬度仍可手动填写'
}

async function loadMap() {
  if (!loader) {
    loader = (await import('@amap/amap-jsapi-loader')).default
  }
  // 安全密钥要在 loader 之前挂到 window 上，否则 2.0 的 JSAPI 会拒
  ;(window as any)._AMapSecurityConfig = {
    securityJsCode: import.meta.env.VITE_AMAP_SECURITY_JS_CODE as string,
  }
  const timeout = new Promise((_, reject) => {
    setTimeout(() => reject(new Error(`高德 JSAPI ${LOAD_TIMEOUT_MS / 1000}s 内没回来`)), LOAD_TIMEOUT_MS)
  })
  return Promise.race([
    loader.load({
      key: import.meta.env.VITE_AMAP_API_KEY as string,
      version: '2.0',
      plugins: ['AMap.Geocoder'],
    }),
    timeout,
  ])
    .then((res: any) => {
      AMap = res
      const center: [number, number] = hasCoordinate.value
        ? [Number(props.longitude), Number(props.latitude)]
        : DEFAULT_CENTER
      map = new AMap.Map(containerId, {
        // 母本那份是 3D；这一份收进弹层就改 2D —— 3D 那档靠 WebGL 逐帧合成，
        // 页面一没被看得见（rAF 冻住）画布就停在旧那一张，看着像「检索了地图不动」
        viewMode: '2D',
        zoom: hasCoordinate.value ? 16 : 11,
        center,
        resizeEnable: true,
      })
      if (hasCoordinate.value) {
        movePoint(center)
      }
      map.on('click', (e: any) => pick(e.lnglat.getLng(), e.lnglat.getLat()))

      // loader 的 plugins 参数在 2.0 下偶尔到回调时还没挂上，再用 AMap.plugin 兜一层
      AMap.plugin(['AMap.Geocoder'], () => {
        if (typeof AMap.Geocoder === 'function') {
          geocoder = new AMap.Geocoder({ city: '全国' })
        }
        else {
          geocodeError = '地址反推没挂上'
        }
        if (hasCoordinate.value) {
          resolveAddress(Number(props.longitude), Number(props.latitude))
        }
      })
    })
    .catch(reportLoadFailure)
}

/** 弹层刚显出来那一刻容器可能还是 0×0，地图按这个尺寸建出来，marker 的坐标换算会得到 NaN */
async function waitForContainer() {
  for (let i = 0; i < 20; i++) {
    const el = document.getElementById(containerId)
    if (el && el.clientWidth > 0) {
      return
    }
    await new Promise(r => setTimeout(r, 50))
  }
}

async function openH5Map() {
  picked.value = hasCoordinate.value
    ? { longitude: Number(props.longitude), latitude: Number(props.latitude), address: '' }
    : null
  message.value = ''
  show.value = true
  // 弹层是 v-if 挂的，这一刻容器还不存在 —— 等 DOM 到位再加载 JSAPI
  await nextTick()
  await waitForContainer()
  if (!map) {
    loadMap().catch(reportLoadFailure)
  }
  else {
    map.resize()
  }
}

function close() {
  // 地图不跟着关掉销毁（同 web 那一份：下一次开只是 resize）。
  // 一个实例里就一张地图，攒不出第二张，页面走掉时由 onBeforeUnmount 收
  show.value = false
  loadError.value = ''
}

function confirm() {
  if (!picked.value) {
    return
  }
  const moved
    = !hasCoordinate.value
      || picked.value.longitude !== Number(props.longitude)
      || picked.value.latitude !== Number(props.latitude)
  emit('confirm', moved ? { ...picked.value } : { ...picked.value, address: '' })
  close()
}

onBeforeUnmount(() => {
  if (map) {
    map.destroy()
    map = null
  }
})
// #endif

// #ifdef MP-WEIXIN || APP-PLUS
function openNativePicker() {
  message.value = ''
  // 微信自己的选点页 / App 的内置选点页：回来的经纬度是 GCJ-02，和 H5 那一趟高德的同一个坐标系
  uni.chooseLocation({
    success: (res) => {
      // 这一路是微信那个页面里的「发送」按的，回来就等于已经定了，直接抛
      emit('confirm', {
        longitude: Number(res.longitude),
        latitude: Number(res.latitude),
        address: res.name || res.address || '',
      })
    },
    fail: (err) => {
      // 用户按返回也走 fail，那一句不用当错误给他看
      if (!/cancel/i.test(err.errMsg || '')) {
        uni.showModal({
          title: '选点没打开',
          content: `${err.errMsg || '未知原因'}（这一格仍可自己填经纬度）`,
          showCancel: false,
        })
      }
    },
  })
}
// #endif

function open() {
  // #ifdef H5
  openH5Map()
  // #endif
  // #ifdef MP-WEIXIN || APP-PLUS
  openNativePicker()
  // #endif
}
</script>

<template>
  <view>
    <view hover-class="fx-tap" class="fx-press flex items-center px-32rpx py-24rpx" @click="open">
      <view class="w-[140rpx] shrink-0 text-[28rpx] text-[#333]">
        地图选点
      </view>
      <view class="min-w-0 flex-1 text-right text-[26rpx]">
        <text v-if="hasCoordinate" class="text-[#333]">
          {{ longitude }}, {{ latitude }}
        </text>
        <text v-else class="text-gray-400">
          选填，点了按位置就近分仓
        </text>
      </view>
      <text class="ml-16rpx shrink-0 text-[26rpx] text-[#d14328]">
        {{ buttonText }}
      </text>
    </view>

    <!-- #ifdef H5 -->
    <wd-popup v-model="show" position="bottom" safe-area-inset-bottom closable @close="close">
      <view class="bg-white pb-24rpx">
        <view class="px-32rpx py-24rpx text-center text-[30rpx] font-medium">
          选择位置
        </view>
        <view class="flex gap-16rpx px-32rpx">
          <wd-input
            v-model="keyword"
            no-border
            custom-class="flex-1 rounded-lg bg-gray-100"
            placeholder="输入地址或小区名"
            :maxlength="60"
            confirm-type="search"
            @confirm="handleSearch"
          />
          <wd-button size="small" :loading="busy" @click="handleSearch">
            检索
          </wd-button>
          <!-- 搜到就先能定：地图那一格不一定看得见动（弹层里、后台页面），这一颗让他不必靠点图 -->
          <wd-button size="small" type="error" :disabled="!picked" @click="confirm">
            确定
          </wd-button>
        </view>
        <!-- 选中那一点的位置摆在检索框下面：这一行是结果，比地图那一格先看得见 -->
        <view class="mt-16rpx break-all px-32rpx text-[26rpx] text-[#333] leading-[36rpx]">
          {{ pickedText }}
        </view>
        <view :id="containerId" class="mx-32rpx mt-16rpx h-[520rpx] overflow-hidden rounded-lg bg-gray-100" />
        <view v-if="loadError" class="mx-32rpx mt-16rpx rounded-lg bg-red-50 px-24rpx py-16rpx text-[24rpx] text-[#d14328]">
          {{ loadError }}
        </view>
      </view>
    </wd-popup>
    <!-- #endif -->
  </view>
</template>
