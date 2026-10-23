<template>
  <div class="image-list">
    <div v-for="(item, index) in list" :key="item.url" class="image-item">
      <el-image :src="item.url" fit="cover" class="thumb" :preview-src-list="previewList" :initial-index="index"
        preview-teleported />
      <el-input v-if="valueMode === 'json'" v-model="item[labelKey]" size="small" :placeholder="placeholder" maxlength="50" @change="emitChange" />
      <el-button class="remove-btn" type="danger" link icon="Delete" @click="handleRemove(index)" />
    </div>

    <!-- 不用 el-upload 自带的 limit：它按内部累计的文件数判断，而 show-file-list=false 时那份累计永不清零，
         选几轮之后就会再也选不进去；名额由这里的 v-if 和 beforeUpload 一起兜 -->
    <el-upload v-if="list.length < limit" :action="uploadUrl" :headers="headers" :show-file-list="false" multiple
      :before-upload="beforeUpload" :on-success="handleSuccess" :on-error="handleError" accept="image/*"
      class="uploader">
      <div class="upload-trigger">
        <el-icon>
          <Plus />
        </el-icon>
      </div>
    </el-upload>

    <div class="footer">
      <span class="tip">最多 {{ limit }} 张，可一次多选，支持 jpg/png/gif/webp，单张不超过 {{ fileSize }}MB</span>
      <!-- 整组抹掉之后只能重新一个个选文件，所以确认一下再走 -->
      <el-popconfirm v-if="list.length" :title="`确定清空这 ${list.length} 张图片？`" width="220"
        confirm-button-text="清空" cancel-button-text="取消" confirm-button-type="danger" @confirm="handleClearAll">
        <template #reference>
          <el-button type="danger" link size="small" icon="Delete">清空</el-button>
        </template>
      </el-popconfirm>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { UploadProps } from 'element-plus'
import { getToken } from '@/utils/auth'
import { parseCommaUrls, parseImageJson, toCommaUrls } from '@/utils/imageJson'

interface ImageItem {
  url: string
  [key: string]: any
}

const props = withDefaults(defineProps<{
  modelValue?: string
  limit?: number
  fileSize?: number
  source?: string
  /** 每项里那行文字的键名：商品轮播图是 title，品牌代言人按后端约定是 name */
  labelKey?: string
  placeholder?: string
  /** 那一列存的形状：json 列（商品图/门店封面）默认；comma 是 plaza_image 那种逗号分隔的地址串，没有每图一行的标题 */
  valueMode?: 'json' | 'comma'
}>(), {
  modelValue: '',
  limit: 9,
  fileSize: 8,
  source: 'product',
  labelKey: 'title',
  placeholder: '图片标题',
  valueMode: 'json'
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const list = ref<ImageItem[]>([])

const uploadUrl = `${import.meta.env.VITE_APP_BASE_API}/file/uploadImage?source=${props.source}&timestamp=${Date.now()}`

const headers = {
  Authorization: getToken()
}

const previewList = computed(() => list.value.map((item) => item.url))

// shop_product.images、shop_brand.brand_spokesperson 这类 json 列，实体侧按项目约定映射成 String，
// 所以这里负责 JSON 字符串与 [{ [labelKey]: '', url }] 数组之间的互转；解析本身走 utils/imageJson。
// valueMode='comma' 那一档（shop_plaza_message.plaza_image）存的是逗号分隔的地址串，没有每项的标题，只有 url
const parse = (raw?: string): ImageItem[] => {
  // 已经存进库的老数据可能就有重复项，按 URL 去重后再渲染（v-for 的 key 也是 URL）
  if (props.valueMode === 'comma') {
    return parseCommaUrls(raw).map((url) => ({ url }))
  }
  const seen = new Set<string>()
  return parseImageJson(raw)
    .filter((item) => !seen.has(item.url) && seen.add(item.url))
    .map((item) => ({ ...item, [props.labelKey]: item[props.labelKey] || '' }))
}

let lastEmitted = ''

const emitChange = () => {
  if (props.valueMode === 'comma') {
    // 这一列是可空 varchar，抹掉整组就交空串（服务端每次都照请求体写，空串等于「这条没配图」）
    lastEmitted = toCommaUrls(list.value.map((item) => item.url))
    emit('update:modelValue', lastEmitted)
    return
  }
  // 空列表提交 "[]" 而不是 ''：images 是 MySQL json 列，空字符串不是合法 JSON，写入会报错
  lastEmitted = JSON.stringify(list.value)
  emit('update:modelValue', lastEmitted)
}

watch(() => props.modelValue, (raw) => {
  const value = raw || ''
  // 自己 emit 出去的值回流时不重新解析，否则会打断正在输入的标题
  if (value === lastEmitted) return
  list.value = parse(value)
}, { immediate: true })

/** 多选一批可能几十个文件，每个都弹一次提示会糊一屏，所以提示只保留最新一条 */
const notify = (type: 'success' | 'warning' | 'error', message: string) => {
  ElMessage.closeAll()
  ElMessage({ type, message })
}

/** 
 *  串行锁。EP 在同一 tick 里把一批文件的 before-upload 连着调完（upload-content 的 for 循环不 await
 *  upload()），并发上传时谁先回包谁先进 list，图片顺序跟着服务端跑；排队之后第 N+1 个一定等第 N 个
 *  终态回调放行，顺序才等于选文件的顺序。等的方式必须是 await（异步）。
 *  不能是 while 忙等——（同步）循环占着调用栈，回包回调排在任务队列里永远轮不到。 
 **/
let uploading = false
const queued: Array<() => void> = []

const acquire = () => new Promise<void>((resolve) => {
  if (uploading) {
    queued.push(resolve) // 等待锁释放
  } else {
    uploading = true
    resolve()
  }
})

// 后面还有人就把锁直接交接（uploading 保持 true），没人等才真正空闲
const release = () => {
  const next = queued.shift()
  if (next) next() // 执行下一个任务
  else uploading = false
}

const handleSuccess: UploadProps['onSuccess'] = (response: any) => {
  try {
    if (response.code === 200 && response.data) {
      list.value.push({ [props.labelKey]: '', url: response.data })
      emitChange()
      notify('success', '上传成功')
    } else {
      notify('error', response.message || '上传失败')
    }
  } finally {
    // 放在 push 之后放行：下一个人进来读到的 list 已经含这一张
    release()
  }
}

// 网络失败、4xx/5xx 都会走到这里（ajax.mjs 的 error / load 分支），是串行锁的另一个终态出口
const handleError: UploadProps['onError'] = () => {
  notify('error', '上传失败')
  release()
}

// 只从列表里摘掉，不调 deleteFileApi 删文件：
// 用户可能移除了图片又点"取消"关闭弹窗，此时库里仍引用着该 URL，
// 文件却已经被删掉了。宁可留下孤儿文件，也不要留下指向空图的死链。
const handleRemove = (index: number) => {
  list.value.splice(index, 1)
  emitChange()
}

const handleClearAll = () => {
  list.value = []
  emitChange()
  notify('success', '已清空')
}

const beforeUpload: UploadProps['beforeUpload'] = async (file) => {
  const isImage = /^image\/(jpeg|png|gif|webp|avif)$/.test(file.type)
  const isLt = file.size / 1024 / 1024 < props.fileSize

  // 这两项不需要占用锁，放在排队前，免得坏文件让后面一整串白等一轮
  if (!isImage) {
    notify('error', '只能上传 jpg/png/gif/webp 格式的图片!')
    return false
  }
  if (!isLt) {
    notify('error', `图片大小不能超过 ${props.fileSize}MB!`)
    return false
  }

  // 排队到自己才开始传；排队期间前面的回包可能已经把额度用完，超出的连传都不用传
  await acquire()

  if (list.value.length >= props.limit) {
    notify('warning', `最多 ${props.limit} 张，超出的已忽略`)
    release()
    return false
  }
  return true
}
</script>

<style scoped lang="scss">
.image-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  width: 100%;
}

.image-item {
  position: relative;
  width: 100px;

  .thumb {
    width: 100px;
    height: 100px;
    border: 1px solid var(--el-border-color);
    border-radius: 6px;
  }

  .remove-btn {
    position: absolute;
    top: 2px;
    right: 2px;
    padding: 2px;
    background: rgba(255, 255, 255, 0.85);
    border-radius: 4px;
  }

  :deep(.el-input) {
    margin-top: 6px;
  }
}

.uploader {
  :deep(.el-upload) {
    width: 100px;
    height: 100px;
    border: 1px dashed var(--el-border-color);
    border-radius: 6px;
    cursor: pointer;
    transition: border-color 0.2s;

    &:hover {
      border-color: var(--el-color-primary);
    }
  }
}

.upload-trigger {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 24px;
  color: var(--el-text-color-secondary);
}

.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.tip {
  font-size: 12px;
  color: #909399;
}
</style>
