<template>
  <!-- 单选档还是内嵌树形下拉（商品表单的「所属分类」、商品选择器里的分类筛选都是这一档），
       只有多选才走弹框：树里勾多个靠的是复选框，输入框里也放不下 -->
  <el-tree-select v-if="!multiple" v-model="selected" :data="data" :props="TREE_PROPS" value-key="id" filterable
    check-strictly :render-after-expand="false" :clearable="clearable" :placeholder="placeholder" size="large">
    <template #default="{ data: item }">
      <div class="category-option">
        <!-- <span class="category-option__icon">
          <shop-icon v-if="item.iconValue" :type="item.iconType" :value="item.iconValue" :size="24"
            :alt="item.categoryName" />
        </span> -->
        <span class="category-option__name">{{ item.categoryName }}</span>
      </div>
    </template>
    <!-- 选中后输入框里也带上图标 -->
    <template #label="{ label }">
      <div v-if="selectedNode" class="category-option">
        <span class="category-option__icon">
          <shop-icon v-if="selectedNode.iconValue" :type="selectedNode.iconType" :value="selectedNode.iconValue"
            :size="24" />
        </span>
        <span class="category-option__name">{{ selectedNode.categoryName }}</span>
      </div>
      <!-- 值在树里找不到（分类被删了、树还没回来）时退回文字，否则输入框会整块空掉 -->
      <span v-else>{{ label }}</span>
    </template>
  </el-tree-select>

  <div v-else class="shop-category-select">
    <el-input :model-value="displayText" :placeholder="placeholder" readonly clearable @click="openDialog"
      @clear="handleClear">
      <template #append>
        <el-button icon="Search" @click="openDialog" />
      </template>
    </el-input>

    <el-dialog :title="title" v-model="dialogVisible" width="500px" append-to-body destroy-on-close>
      <div class="search-wrapper">
        <el-form :inline="true" @submit.prevent>
          <el-form-item>
            <el-input v-model="keyword" placeholder="请输入分类名称" clearable />
          </el-form-item>
          <el-form-item>
            <el-button icon="Refresh" @click="resetFilter">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-scrollbar max-height="360px">
        <!-- check-strictly：勾父级不把整串下级拖进来，勾选结果就是用户点的那几个 -->
        <el-tree ref="treeRef" :data="data" :props="TREE_NODE_PROPS" node-key="id" show-checkbox check-strictly
          :default-expand-all="true" :default-checked-keys="checkedKeys" :filter-node-method="filterNode"
          @check="handleCheck" />
      </el-scrollbar>

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :disabled="checkedCount === 0" @click="handleConfirm">确 定</el-button>
          <el-button @click="dialogVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import ShopIcon from '@/components/ShopIcon/index.vue'

const props = withDefaults(defineProps<{
  /** 分类树，节点需带 id/categoryName，图标取自联表回来的 iconType/iconValue */
  data?: any[]
  placeholder?: string
  clearable?: boolean
  /** 复选框多选：这一档改弹框呈现，确定时抛 id 数组 + 节点数组。默认 false 保持内嵌树下拉 */
  multiple?: boolean
  title?: string
}>(), {
  data: () => [],
  placeholder: '请选择分类',
  clearable: true,
  multiple: false,
  title: '选择分类'
})

const emit = defineEmits(['select'])

const selected = defineModel<number | string | Array<number | string> | undefined>()

const TREE_PROPS = { label: 'categoryName', value: 'id' }
const TREE_NODE_PROPS = { label: 'categoryName', children: 'children' }

const dialogVisible = ref(false)
const keyword = ref('')
const treeRef = ref<any>()
// 弹窗里当前勾中的数量：只用来钉确定按钮，勾选状态本身归 el-tree
const checkedCount = ref(0)

/**
 * #label 插槽只回传文字、回传不了节点，图标只能自己按当前值回查。
 * parentId 用 0 表示顶级，所以这里不能用真值判断把 0 当成"没选"。
 */
const findNode = (nodes: any[], id: number | string): any => {
  for (const node of nodes || []) {
    if (String(node.id) === String(id)) return node
    const matched = findNode(node.children, id)
    if (matched) return matched
  }
  return null
}

const selectedNode = computed(() => {
  const value = selected.value
  if (value === undefined || value === null || value === '') return null
  return findNode(props.data, value)
})

/** 多选档的 modelValue 是 id 数组；单选档给空数组 */
const selectedIds = computed<string[]>(() => {
  if (!props.multiple || !Array.isArray(selected.value)) return []
  return selected.value.map((id) => String(id))
})

const displayText = computed(() => {
  const count = selectedIds.value.length
  return count ? `已选 ${count} 项` : ''
})

/**
 * default-checked-keys 要的是 node-key 原本那个值（这里是数字 id），
 * 而 modelValue 里的 id 常是字符串，所以按字符串比一遍、把树里那批原始值取出来。
 */
const collectKeys = (nodes: any[], ids: string[], out: any[] = []): any[] => {
  for (const node of nodes || []) {
    if (ids.includes(String(node.id))) out.push(node.id)
    collectKeys(node.children, ids, out)
  }
  return out
}

const checkedKeys = computed(() => collectKeys(props.data, selectedIds.value))

/** el-tree 的 filter 只在传了 filter-node-method 时才用我们的判据，否则它会拿 label 正则去比 */
const filterNode = (value: string, data: any) => {
  if (!value) return true
  return String(data.categoryName || '').toLowerCase().includes(value.trim().toLowerCase())
}

watch(keyword, (value) => {
  treeRef.value?.filter(value)
})

const resetFilter = () => {
  keyword.value = ''
  // 清空输入框走的是上面那个 watch，但值没变（本来就是空）时不会触发，所以这里补一次
  treeRef.value?.filter('')
}

const openDialog = () => {
  keyword.value = ''
  // 树还没挂载就点确定会当成"一个没勾"，所以先把已选的数量垫上
  checkedCount.value = selectedIds.value.length
  dialogVisible.value = true
}

/** el-tree 的 check 事件只在用户点复选框时来，勾选结果自己从树里取 */
const handleCheck = () => {
  checkedCount.value = treeRef.value?.getCheckedNodes().length || 0
}

const handleConfirm = () => {
  const nodes = treeRef.value?.getCheckedNodes() || []
  selected.value = nodes.map((node: any) => node.id)
  emit('select', nodes)
  dialogVisible.value = false
}

const handleClear = () => {
  selected.value = []
}

watch(dialogVisible, (visible) => {
  if (!visible) checkedCount.value = 0
})
</script>

<style scoped lang="scss">
/* el-select-dropdown__item 是固定 34px 行高、非 flex、overflow:hidden 的块，
   所以居中只能做在自己的元素上；text-overflow 也只对父项自己的文字生效，
   长分类名要在 .category-option__name 上单独截断。 */
.category-option {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 100%;
  line-height: 30px;
}

/* 没有图标的节点也留着这块位置，名称才不会一列不齐 */
.category-option__icon {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
}

.category-option__name {
  min-width: 0;
  // overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shop-category-select {
  width: 100%;

  :deep(.el-input-group__append) {
    width: 5%;
  }
}
</style>
