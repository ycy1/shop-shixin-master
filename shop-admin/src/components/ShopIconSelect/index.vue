<template>
  <el-dialog v-model="dialogVisible" title="选择图标" width="800px" append-to-body top="5vh" @open="handleOpen">
    <div class="icon-container">
      <!-- 三个 tab 就是 sys_icon.icon_category 的三档：菜单挑图标默认落在系统图标，商品类目那侧落在商品分类 -->
      <el-tabs v-model="activeCategory" class="icon-tabs">
        <el-tab-pane v-for="item in ICON_CATEGORIES" :key="item.value" :label="item.label" :name="item.value" />
      </el-tabs>

      <div class="search-bar">
        <el-input v-model="searchText" placeholder="搜索图标名称或编码" clearable>
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <!-- 类目只是商品分类那一档自己的维度，另外两档没有「商品类目」的说法；
             调用方已经用 categoryId 锁定范围时也不给这个筛选，否则两个条件会互相矛盾 -->
        <shop-category-select v-if="!categoryId && activeCategory === CATEGORY_PRODUCT" v-model="filterCategoryId"
          class="filter-category" :data="categoryOptions" placeholder="全部类目" />
        <el-button v-permission="['sys:icon:add']" type="primary" plain icon="Plus" @click="formOpen = true">
          新增图标
        </el-button>
      </div>
      <el-scrollbar height="400px" v-loading="loading">
        <div v-if="filteredIcons.length" class="icon-list">
          <div v-for="item in filteredIcons" :key="item.id" class="icon-item" :class="{ active: isActive(item.id) }"
            @click="selectIcon(item)">
            <shop-icon :type="item.iconType" :value="item.iconValue" :size="28" :alt="item.iconName" />
            <span class="icon-name">{{ item.iconName }}</span>
          </div>
        </div>
        <el-empty v-else :description="emptyText" :image-size="80" />
      </el-scrollbar>

      <!-- append-to-body 会把这个弹窗 teleport 到 body，嵌在选择器弹窗里不会互相裁剪 -->
      <shop-icon-form v-model:visible="formOpen" :preset-category-id="categoryId ?? filterCategoryId"
        :preset-icon-category="activeCategory" @saved="handleSaved" />
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { optionsIconApi } from '@/api/system/icon'
import { getCategoryTreeApi } from '@/api/shop/category'
import ShopIcon from '@/components/ShopIcon/index.vue'
import ShopIconForm from '@/components/ShopIconForm/index.vue'
import ShopCategorySelect from '@/components/ShopCategorySelect/index.vue'
import { ICON_CATEGORIES, ICON_CATEGORY_DEFAULT } from '@/components/ShopIcon/constants'

/** 商品分类那一档的 value：只有这一档有「所属类目」这个维度 */
const CATEGORY_PRODUCT = 2

const props = defineProps<{
  /** 选中的图标 id */
  modelValue?: number | string
  visible: boolean
  /** 只列某个商品类目下的图标，不传则列当前 tab 的全部启用图标 */
  categoryId?: number | string
  /** 打开时先落在哪个 tab；不传时，锁定了商品类目的调用方按商品分类，其余按默认档 */
  iconCategory?: number | string
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
const activeCategory = ref<number>(Number(props.iconCategory ?? (props.categoryId ? CATEGORY_PRODUCT : ICON_CATEGORY_DEFAULT)))
const filterCategoryId = ref<number | string | undefined>(undefined)
const categoryOptions = ref<any[]>([])

const loadIcons = async () => {
  loading.value = true
  try {
    // 类目筛选只在商品分类那一档有意义，切到别的 tab 时不把上一次的筛选带过去
    const categoryId = activeCategory.value === CATEGORY_PRODUCT
      ? (props.categoryId ?? filterCategoryId.value)
      : undefined
    const { data } = await optionsIconApi(activeCategory.value, categoryId)
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
  // 每次打开都回到调用方指定的那一档，免得上一轮停在别的 tab 上让人以为没传对参数
  const next = Number(props.iconCategory ?? (props.categoryId ? CATEGORY_PRODUCT : ICON_CATEGORY_DEFAULT))
  searchText.value = ''
  filterCategoryId.value = undefined
  if (activeCategory.value === next) {
    loadIcons()
  } else {
    // 值变了，下面那个 watch 会去取这一档的数据，这里不用再发一次
    activeCategory.value = next
  }
  if (!props.categoryId) loadCategories()
}

// 切 tab 就是换一批数据；类目筛选跟着归零，否则会拿着上个 tab 选中的类目去筛这一档
watch(activeCategory, () => {
  filterCategoryId.value = undefined
  // 类目候选是第一次落到商品分类时才要（handleOpen 那次可能停在别的 tab 上）
  if (activeCategory.value === CATEGORY_PRODUCT) loadCategories()
  loadIcons()
})

// 类目筛选交给服务端（这一档的数据量本来就按 tab 收窄过），前端只留关键字
const filteredIcons = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  if (!keyword) return icons.value
  return icons.value.filter((item) =>
    `${item.iconName || ''}`.toLowerCase().includes(keyword) ||
    `${item.iconCode || ''}`.toLowerCase().includes(keyword)
  )
})

/** 空列表那句说明分两种：这一档本来就没图标，还是被关键字筛空了 */
const emptyText = computed(() => {
  if (searchText.value.trim()) return '无匹配图标'
  return activeCategory.value === CATEGORY_PRODUCT && filterCategoryId.value
    ? '该类目下暂无可用图标'
    : `「${ICON_CATEGORIES.find((item) => item.value === activeCategory.value)?.label}」下暂无图标，可点右上「新增图标」`
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
  // 表单里能把分类改到别的档：那种情况下这条新图标不在当前 tab，不自动选中（否则回写的 id
  // 是列表里看不到的东西），只提示去哪找它
  if (icon.iconCategory && Number(icon.iconCategory) !== activeCategory.value) {
    const label = ICON_CATEGORIES.find((item) => item.value === Number(icon.iconCategory))?.label
    ElMessage.warning(`图标「${icon.iconName}」保存到了「${label || '其它'}」分类，切到那个 tab 才能选它`)
    await loadIcons()
    return
  }
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

.icon-tabs {
  margin-bottom: 4px;
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
