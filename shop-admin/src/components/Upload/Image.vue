<template>
  <div class="upload-container">
    <!-- 单图上传：没图时只有 “+”，有图时整个 el-upload 不挂载，所以不会出现「缩略图 + 一个空的虚线上传框」。
         EP 的 picture-card 把 “+” 触发框渲染在文件列表的 append 插槽里，且不受 limit 影响
         （limit 只在选文件那一刻拦一下走 on-exceed），靠覆盖它的内部类名来隐藏不可靠，
         所以这里不用它自带的文件列表，方框样式也全部自己画。 -->
    <el-upload v-if="!currentUrl" :action="uploadUrl" :headers="headers" :accept="ACCEPT" :show-file-list="false"
      :before-upload="beforeUpload" :on-success="handleSuccess" :on-error="handleError">
      <div class="upload-trigger">
        <el-icon v-if="uploading" class="is-loading">
          <Loading />
        </el-icon>
        <el-icon v-else>
          <Plus />
        </el-icon>
      </div>
    </el-upload>

    <div v-else class="preview">
      <el-image :src="currentUrl" fit="cover" />
      <div class="preview-mask">
        <el-icon @click="handlePreview">
          <ZoomIn />
        </el-icon>
        <el-icon @click="handleRemove">
          <Delete />
        </el-icon>
      </div>
    </div>

    <div class="upload-tip">
      可上传{{ ACCEPT_EXT }}，且不超过 {{ fileSize }}MB
    </div>

    <!-- 嵌套在业务弹窗里，必须 append-to-body，否则层级会被父弹窗盖住 -->
    <el-dialog v-model="dialogVisible" top="5vh" title="预览图片" append-to-body>
      <img :src="dialogImageUrl" alt="Preview Image" style="width: 100%; height: 500px; object-fit: contain;" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { Delete, Loading, Plus, ZoomIn } from '@element-plus/icons-vue'
import type { UploadProps } from 'element-plus'
import { getToken } from '@/utils/auth'
import { deleteFileApi } from '@/api/file'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  size: {
    type: Number,
    default: 100
  },
  fileSize: {
    type: Number,
    default: 8
  },
  source: {
    type: String,
    default: 'default'
  }
})

const emit = defineEmits(['update:modelValue'])

const ACCEPT = 'image/jpeg,image/png,image/gif,image/webp,image/avif'
const ACCEPT_EXT = ' jpg/png/gif/webp/avif 格式图片'

// 上传地址
const uploadUrl = `${import.meta.env.VITE_APP_BASE_API}/file/uploadImage?source=${props.source}&timestamp=${new Date().getTime()}`

// 请求头
const headers = {
  Authorization: getToken()
}

const uploading = ref(false)
const dialogImageUrl = ref('')
const dialogVisible = ref(false)

const currentUrl = computed(() => props.modelValue || '')
const boxSize = computed(() => `${props.size}px`)

// 处理图片预览
const handlePreview = () => {
  dialogImageUrl.value = currentUrl.value
  dialogVisible.value = true
}

// 处理图片删除
const handleRemove = async () => {
  if (!currentUrl.value) return
  const url = currentUrl.value
  emit('update:modelValue', '')
  await deleteFileApi(url)
}

// 处理上传成功
const handleSuccess: UploadProps['onSuccess'] = (response: any) => {
  uploading.value = false
  if (response.code === 200 && response.data) {
    emit('update:modelValue', response.data)
    ElMessage.success('上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const handleError: UploadProps['onError'] = () => {
  uploading.value = false
  ElMessage.error('上传失败')
}

// 上传前的校验
const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  const isImage = /^image\/(jpeg|png|gif|webp|avif)$/.test(file.type)
  const isLt = file.size / 1024 / 1024 < props.fileSize

  if (!isImage) {
    ElMessage.error(`只能上传${ACCEPT_EXT}!`)
    return false
  }
  if (!isLt) {
    ElMessage.error(`图片大小不能超过 ${props.fileSize}MB!`)
    return false
  }
  uploading.value = true
  return true
}
</script>

<style scoped lang="scss">
.upload-container {
  .upload-tip {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-top: 8px;
  }
}

.upload-trigger {
  box-sizing: border-box;
  width: v-bind(boxSize);
  height: v-bind(boxSize);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: var(--el-text-color-secondary);
  background-color: var(--el-fill-color-lighter);
  border: 1px dashed var(--el-border-color-darker);
  border-radius: 6px;
  transition: border-color 0.2s;

  &:hover {
    border-color: var(--el-color-primary);
  }
}

.preview {
  position: relative;
  box-sizing: border-box;
  width: v-bind(boxSize);
  height: v-bind(boxSize);
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  overflow: hidden;

  :deep(.el-image) {
    width: 100%;
    height: 100%;
  }

  &:hover .preview-mask {
    display: flex;
  }
}

.preview-mask {
  position: absolute;
  inset: 0;
  display: none;
  align-items: center;
  justify-content: center;
  gap: 16px;
  font-size: 18px;
  color: #fff;
  background: rgba(0, 0, 0, 0.5);

  .el-icon {
    cursor: pointer;

    &:hover {
      color: var(--el-color-primary);
    }
  }
}
</style>
