<template>
  <div class="dept-select">
    <el-input
      :model-value="displayNames"
      :placeholder="placeholder"
      readonly
      :disabled="disabled"
      clearable
      @click="openDialog"
      @clear="handleClear"
    >
      <template #append>
        <el-button :disabled="disabled" icon="Search" @click="openDialog" />
      </template>
    </el-input>

    <el-dialog
      :title="title"
      v-model="dialogVisible"
      width="500px"
      append-to-body
      destroy-on-close
    >
      <el-scrollbar height="400px">
        <el-tree
          ref="treeRef"
          :data="treeData"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          show-checkbox
          check-strictly
          default-expand-all
          :check-on-click-node="true"
        />
      </el-scrollbar>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="handleConfirm">确 定</el-button>
          <el-button @click="dialogVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { getDeptTreeApi } from '@/api/system/dept'

interface DeptNode {
  id: number
  name: string
  children?: DeptNode[]
}

const props = withDefaults(defineProps<{
  modelValue: number[]
  placeholder?: string
  disabled?: boolean
  title?: string
}>(), {
  placeholder: '请选择部门',
  disabled: false,
  title: '选择部门'
})

const emit = defineEmits<{
  'update:modelValue': [value: number[]]
}>()

const dialogVisible = ref(false)
const treeData = ref<DeptNode[]>([])
const treeRef = ref<any>()
const nameMap = ref<Map<number, string>>(new Map())

// 展示已选部门名称
const displayNames = computed(() => {
  return props.modelValue
    .map((id) => nameMap.value.get(id))
    .filter(Boolean)
    .join('、')
})

// 扁平化部门树并构建 id -> name 映射
const buildNameMap = (nodes: DeptNode[]) => {
  const map = new Map<number, string>()
  const walk = (list: DeptNode[]) => {
    list.forEach((node) => {
      map.set(node.id, node.name)
      if (node.children && node.children.length) {
        walk(node.children)
      }
    })
  }
  walk(nodes)
  return map
}

const loadTree = async () => {
  try {
    const { data } = await getDeptTreeApi()
    treeData.value = data
    nameMap.value = buildNameMap(data)
  } catch (error) {
    // 忽略，弹窗内展示空树
  }
}

const openDialog = async () => {
  if (props.disabled) return
  await loadTree()
  dialogVisible.value = true
  nextTick(() => {
    if (treeRef.value) {
      treeRef.value.setCheckedKeys([])
      props.modelValue.forEach((id) => treeRef.value.setChecked(id, true, false))
    }
  })
}

const handleClear = () => {
  emit('update:modelValue', [])
}

const handleConfirm = () => {
  const checkedKeys = treeRef.value ? treeRef.value.getCheckedKeys() : []
  emit('update:modelValue', checkedKeys.map((id: any) => Number(id)))
  dialogVisible.value = false
}

onMounted(() => {
  loadTree()
})
</script>

<style lang="scss" scoped>
.dept-select {
  width: 100%;

  .el-input {
    cursor: pointer;
  }

  /* 右侧图标按钮收窄（固定方形，保证图标可见） */
  :deep(.el-input-group__append) {
    padding: 0;

    .el-button {
      width: 32px;
      height: 32px;
      margin: 0;
      padding: 0;
    }
  }
}
</style>
