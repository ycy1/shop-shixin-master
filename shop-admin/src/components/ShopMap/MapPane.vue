<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import AMapLoader from '@amap/amap-jsapi-loader'
import type { PickedPoint } from './types'

const props = withDefaults(defineProps<{
  /** 初始中心：传已有的经纬度，不传则落在默认城市 */
  longitude?: number | string | null
  latitude?: number | string | null
  height?: string
}>(), {
  longitude: null,
  latitude: null,
  height: '460px'
})

// 点选结果整体抛给父组件，由父组件决定写回哪些字段
const emit = defineEmits<{ change: [point: PickedPoint] }>()

/** 一个页面可能挂多张地图，容器 id 不能写死 */
const containerId = `amap-${Math.random().toString(36).slice(2, 8)}`
const containerStyle = computed(() => ({ width: '100%', height: props.height }))

const DEFAULT_CENTER: [number, number] = [116.397428, 39.90923]

const keyword = ref('')
const searching = ref(false)

let AMap: any = null
let map: any = null
let marker: any = null
let geocoder: any = null
/** 最后一次点选，用来挡掉过期回来的逆地理结果 */
let lastPick: PickedPoint | null = null
/** Geocoder 插件没加载出来时的原因，JSAPI 能显示地图但服务接口全挂 */
let geocodeError = ''

const hasCenter = () =>
  props.longitude !== null && props.longitude !== undefined && props.longitude !== '' &&
  props.latitude !== null && props.latitude !== undefined && props.latitude !== ''

const moveMarker = (position: [number, number]) => {
  if (marker) {
    marker.setPosition(position)
  } else {
    marker = new AMap.Marker({ position })
    map.add(marker)
  }
}

const emitPoint = (point: PickedPoint) => {
  lastPick = point
  emit('change', point)
}

/** 逆地理：坐标先抛一次，地址回来再抛一次（不管成没成都收尾） */
const resolveAddress = (lng: number, lat: number) => {
  if (!geocoder) {
    emitPoint({
      longitude: lng,
      latitude: lat,
      address: '',
      resolving: false,
      message: geocodeError || '逆地理服务不可用'
    })
    return
  }
  emitPoint({ longitude: lng, latitude: lat, address: '', resolving: true })

  geocoder.getAddress([lng, lat], (status: string, result: any) => {
    // 详细地址列只放街道门牌，省市区另存 area_code / area_zh，所以优先取短格式；
    // 短格式偶尔为空（海上、边界点），兜底用完整地址
    const re = status === 'complete' && result.info === 'OK' ? result.regeocode : null
    const address = re ? re.address || re.formattedAddress : ''
    // 失败就把高德的 status / info 原样带出去，不然页面上只看到"解析不出来"，定不了因
    const message = address ? '' : `${status} / ${result?.info || '没有地址返回'}`
    // 异步回调可能晚于下一次点选回来，过期结果直接丢，别把新点覆盖成旧坐标
    if (lastPick && lastPick.longitude === lng && lastPick.latitude === lat) {
      emitPoint({ longitude: lng, latitude: lat, address, resolving: false, message })
    }
  })
}

/** 落点：先移 marker，再解析地址 */
const pick = (lng: number, lat: number) => {
  moveMarker([lng, lat])
  resolveAddress(lng, lat)
}

/** 输入地址检索：命中就把地图移到该点并落 marker，等同于一次点选 */
const handleSearch = () => {
  const kw = keyword.value.trim()
  if (!kw) return
  if (!geocoder) {
    ElMessage.warning(geocodeError || '地图还没加载完成，请稍候')
    return
  }
  searching.value = true
  geocoder.getLocation(kw, (status: string, result: any) => {
    searching.value = false
    const geo = status === 'complete' && result.info === 'OK' ? result.geocodes?.[0] : null
    if (!geo) {
      const reason = status === 'complete' ? '地址写详细一点再试' : `${status} / ${result?.info || '无结果'}`
      ElMessage.warning(`没找到「${kw}」，${reason}`)
      return
    }
    const lng = geo.location.getLng()
    const lat = geo.location.getLat()
    map.setZoomAndCenter(16, [lng, lat])
    pick(lng, lat)
  })
}

onMounted(() => {
  (window as any)._AMapSecurityConfig = { securityJsCode: import.meta.env.VITE_AMAP_SECURITY_JS_CODE }

  AMapLoader.load({
    key: import.meta.env.VITE_AMAP_API_KEY,
    version: '2.0',
    plugins: ['AMap.Geocoder']
  })
    .then((res: any) => {
      AMap = res
      const center: [number, number] = hasCenter()
        ? [Number(props.longitude), Number(props.latitude)]
        : DEFAULT_CENTER

      map = new AMap.Map(containerId, {
        viewMode: '3D',
        zoom: hasCenter() ? 16 : 11,
        center,
        resizeEnable: true
      })

      // 已有经纬度时先标出原点
      if (hasCenter()) moveMarker(center)

      map.on('click', (e: any) => pick(e.lnglat.getLng(), e.lnglat.getLat()))

      // loader 的 plugins 参数在 2.0 下偶尔到回调时还没挂上，再用 AMap.plugin 兜一层
      // （已加载时它只是同步回调一次）；放在 click 绑定之后，插件出问题也不影响取点
      AMap.plugin(['AMap.Geocoder'], () => {
        if (typeof AMap.Geocoder === 'function') {
          geocoder = new AMap.Geocoder({ city: '全国' })
        } else {
          geocodeError = 'Geocoder 插件未加载'
        }
        // 打开弹窗时把现有坐标反推成地址，不然只有一串数字看不出落在哪儿
        if (hasCenter()) resolveAddress(Number(props.longitude), Number(props.latitude))
      })
    })
    .catch((e: any) => {
      console.error('高德地图加载失败：', e)
      ElMessage.error('地图加载失败，经纬度仍可手动填写')
    })
})

onBeforeUnmount(() => {
  map?.destroy()
  map = null
  marker = null
  geocoder = null
  AMap = null
  lastPick = null
})
</script>

<template>
  <div class="shop-map">
    <el-input v-model="keyword" placeholder="输入地址（省市区+街道门牌）回车定位" clearable @keyup.enter="handleSearch">
      <template #append>
        <el-button :loading="searching" @click="handleSearch">搜索</el-button>
      </template>
    </el-input>
    <div :id="containerId" :style="containerStyle"></div>
  </div>
</template>

<style scoped>
.shop-map {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
</style>
