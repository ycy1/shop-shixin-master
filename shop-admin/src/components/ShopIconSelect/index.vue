<template>
  <el-dialog v-model="dialogVisible" title="选择图标" width="800px" append-to-body top="5vh" @open="handleOpen">
    <div class="icon-container">
      <div class="search-bar">
        <el-input v-model="searchText" placeholder="搜索图标名称或编码" clearable>
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <!-- 调用方已经用 categoryId 锁定范围时不再提供类目筛选，否则两个条件会互相矛盾 -->
        <shop-category-select v-if="!categoryId" v-model="filterCategoryId" class="filter-category"
          :data="categoryOptions" placeholder="全部类目" />
        <el-button v-permission="['shop:icon:add']" type="primary" plain icon="Plus" @click="formOpen = true">
          新增图标
        </el-button>
      </div>
      <el-scrollbar height="400px" v-loading="loading">
        <div v-if="filteredIcons.length" class="icon-list">
          <div v-for="item in filteredIcons" :key="item.id" class="icon-item"
            :class="{ active: isActive(item.id) }" @click="selectIcon(item)">
            <shop-icon :type="item.iconType" :value="item.iconValue" :size="28" :alt="item.iconName" />
            <span class="icon-name">{{ item.iconName }}</span>
          </div>
        </div>
        <el-empty v-else :description="searchText || filterCategoryId ? '无匹配图标' : '暂无可用图标'" :image-size="80" />
      </el-scrollbar>

      <!-- append-to-body 会把这个弹窗 teleport 到 body，嵌在选择器弹窗里不会互相裁剪 -->
      <shop-icon-form v-model:visible="formOpen" :preset-category-id="categoryId ?? filterCategoryId"
        @saved="handleSaved" />
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { optionsShopIconApi } from '@/api/shop/icon'
import { getCategoryTreeApi } from '@/api/shop/category'
import ShopIcon from '@/components/ShopIcon/index.vue'
import ShopIconForm from '@/components/ShopIconForm/index.vue'
import ShopCategorySelect from '@/components/ShopCategorySelect/index.vue'

const props = defineProps<{
  /** 选中的图标 id */
  modelValue?: number | string
  visible: boolean
  /** 只列某个类目下的图标，不传则列全部启用图标 */
  categoryId?: number | string
}>()

// 选中时除了回写 id，把整条记录也带出去，省得调用方再发一次详情请求
const emit = defineEmits<{
  'update:modelValue': [id: number | string]
  'update:visible': [visible: boolean]
  'select': [icon: any]
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value)
})

const loading = ref(false)
const searchText = ref('')
const icons = ref<any[]>([])
const filterCategoryId = ref<number | string | undefined>(undefined)
const categoryOptions = ref<any[]>([])

const loadIcons = async () => {
  loading.value = true
  try {
    const { data } = await optionsShopIconApi(props.categoryId)
    icons.value = data || []
  } catch (error) {
    icons.value = []
  } finally {
    loading.value = false
  }
}

// 类目树只跟弹窗取一次，同一页面反复打开不必重复请求
const loadCategories = async () => {
  if (categoryOptions.value.length) return
  try {
    const { data } = await getCategoryTreeApi({})
    categoryOptions.value = data || []
  } catch (error) {
    categoryOptions.value = []
  }
}

const handleOpen = () => {
  loadIcons()
  if (!props.categoryId) loadCategories()
}

// 数据量是一屏能翻完的量级，直接在前端过滤，不额外发请求
const filteredIcons = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  const category = filterCategoryId.value
  return icons.value.filter((item) => {
    if (category !== undefined && category !== null && category !== ''
      && String(item.categoryId) !== String(category)) return false
    if (!keyword) return true
    return `${item.iconName || ''}`.toLowerCase().includes(keyword) ||
      `${item.iconCode || ''}`.toLowerCase().includes(keyword)
  })
})

// 调用方存的多是字符串，后端返回的是数字，严格相等会匹配不上
const isActive = (id: number | string) =>
  props.modelValue !== undefined && props.modelValue !== null && String(props.modelValue) === String(id)

const selectIcon = (icon: any) => {
  emit('update:modelValue', icon.id)
  emit('select', icon)
  emit('update:visible', false)
}

const formOpen = ref(false)

// 新建完最好立刻可用：重取列表后按 id 找得到就走和点选一样的路径（顺带关窗并回写给调用方），
// 找不到通常是状态选了禁用或被当前筛选条件排除，这种情况留着选择器让用户自己确认
const handleSaved = async (icon: any) => {
  await loadIcons()
  const created = icons.value.find((item) => String(item.id) === String(icon.id))
  if (created) {
    selectIcon(created)
  } else {
    ElMessage.warning(`图标「${icon.iconName}」已保存，但不在当前列表的筛选范围内`)
  }
}
</script>

<style scoped lang="scss">
.icon-container {
  padding: 0 4px;
}

.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;

  .el-input {
    flex: 1;
    min-width: 0;
  }

  .el-button {
    flex-shrink: 0;
  }

  /* 类名落在孙子组件的根元素上，用 :deep 才不会依赖 scope 属性的向下传递 */
  :deep(.filter-category) {
    flex: 0 0 200px;
    width: 200px;
  }
}

.icon-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
  gap: 10px;
}

.icon-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: var(--el-color-primary);
    color: var(--el-color-primary);
  }

  &.active {
    border-color: var(--el-color-primary);
    background-color: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
  }
}

.icon-name {
  max-width: 100%;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
