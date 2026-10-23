<script setup lang="ts">
import MapPane from './MapPane.vue'
import type { PickedPoint } from './types'

const props = withDefaults(defineProps<{
  /** 当前坐标：决定按钮文案，也是弹窗里地图的初始中心 */
  longitude?: number | string | null
  latitude?: number | string | null
  title?: string
  width?: string
  height?: string
}>(), {
  longitude: null,
  latitude: null,
  title: '选择位置',
  width: '900px',
  height: '460px'
})

/** 点确定才抛一次；弹窗里反复改点不会影响外部 */
const emit = defineEmits<{ confirm: [point: PickedPoint] }>()

const dialogVisible = ref(false)
const picked = ref<PickedPoint | null>(null)

const hasCoordinate = computed(
  () => props.longitude !== null && props.longitude !== undefined && props.longitude !== '' &&
    props.latitude !== null && props.latitude !== undefined && props.latitude !== ''
)

const openDialog = () => {
  // 以外部现有坐标起头，不重新点也能直接确定
  picked.value = hasCoordinate.value
    ? { longitude: Number(props.longitude), latitude: Number(props.latitude), address: '', resolving: false }
    : null
  dialogVisible.value = true
}

const handleChange = (point: PickedPoint) => {
  picked.value = point
}

const handleConfirm = () => {
  if (picked.value) {
    const moved =
      !hasCoordinate.value ||
      picked.value.longitude !== Number(props.longitude) ||
      picked.value.latitude !== Number(props.latitude)
    // 没挪点就只是"打开看了眼位置"，别把用户手写的详细地址换成高德给的街道名
    emit('confirm', moved ? picked.value : { ...picked.value, address: '' })
  }
  dialogVisible.value = false
}
</script>

<template>
  <div class="shop-map-picker">
    <el-button icon="MapLocation" plain @click="openDialog">
      {{ hasCoordinate ? '重新选点' : '地图选点' }}
    </el-button>

    <el-dialog v-model="dialogVisible" :title="title" :width="width" append-to-body destroy-on-close>
      <map-pane :longitude="longitude" :latitude="latitude" :height="height" @change="handleChange" />
      <div class="picked-tip">
        <template v-if="picked">
          {{ Number(picked.longitude).toFixed(6) }}, {{ Number(picked.latitude).toFixed(6) }}
          <span v-if="picked.address">　{{ picked.address }}</span>
          <span v-else-if="picked.resolving">　地址解析中…</span>
          <span v-else-if="picked.message">　地址解析失败（{{ picked.message }}），详细地址自己填</span>
          <span v-else>　这是表单里现有的坐标，点地图可以换位置</span>
        </template>
        <template v-else>点击地图任意位置落点，可反复改</template>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取 消</el-button>
          <el-button type="primary" @click="handleConfirm">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.shop-map-picker {
  width: 100%;
}

/* 选点结果提示：地图下面的一行小字 */
.picked-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}
</style>
