<template>
  <div class="shop-product-sku-select">
    <el-input :model-value="displayText" :placeholder="placeholder" readonly :disabled="disabled" clearable
      @click="openDialog" @clear="handleClear">
      <template #append>
        <el-button :disabled="disabled" icon="Search" @click="openDialog" />
      </template>
    </el-input>

    <el-dialog :title="title" v-model="dialogVisible" width="1000px" append-to-body destroy-on-close>
      <!-- 筛选：一行一个规格，所以既能按商品那侧找（关键字 / 分类），也能按这一行自己的 SKU 编码找；
           锁定了 productId 时商品那一格没意义，分类筛选一并收掉 -->
      <div class="search-wrapper">
        <el-form :inline="true" @submit.prevent>
          <el-form-item>
            <el-input v-model="searchForm.keyword" placeholder="商品名称/编码/SKU编码" clearable
              @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item v-if="!productId">
            <shop-category-select v-model="searchForm.categoryId" :data="categoryOptions" placeholder="全部分类"
              @change="handleSearch" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleSearch">搜索</el-button>
            <el-button icon="Refresh" @click="resetSearch">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table ref="tableRef" v-loading="loading" :data="skuList" height="360" row-key="id"
        :highlight-current-row="!multiple" @current-change="handleCurrentChange" @row-dblclick="handleRowDblclick"
        @selection-change="handleSelectionChange">
        <!-- 多选档加复选框 + reserve-selection（口径同商品库选择器：翻页/换筛选后已选不掉） -->
        <el-table-column v-if="multiple" type="selection" width="45" reserve-selection />
        <!-- 这一格没有规格图就退回商品主图，和 SKU 弹窗同一口径（sku_image 的注释写着 NULL=沿用 main_image） -->
        <el-table-column label="规格图" align="center" width="70">
          <template #default="{ row }">
            <el-image v-if="row.skuImage || row.mainImage" :src="row.skuImage || row.mainImage"
              :preview-src-list="[row.skuImage || row.mainImage]" preview-teleported fit="cover" class="thumb"
              @click.stop @dblclick.stop />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="商品名称" prop="productName" align="center" min-width="160" show-overflow-tooltip />
        <el-table-column label="规格" align="center" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.specText || '默认规格' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="SKU编码" prop="skuCode" align="center" width="150" show-overflow-tooltip />
        <el-table-column label="单价" align="center" width="90">
          <template #default="{ row }">
            <span>{{ row.productPrice == null || row.productPrice === '' ? '-' : `￥${Number(row.productPrice).toFixed(2)}` }}</span>
          </template>
        </el-table-column>
        <el-table-column label="可用库存" align="center" width="90">
          <template #default="{ row }">
            <span>{{ row.availableStock ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="row.productStatus === 1 ? 'success' : 'info'">
              {{ row.productStatus === 1 ? '上架' : '下架' }}
            </el-tag>
            <el-tag v-if="row.saleEnable !== 1" size="small" type="warning" class="ml4">不可售</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 30, 50]" :total="total" :background="true"
          layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange"
          @current-change="getList" />
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :disabled="multiple && multiSelection.length === 0" @click="handleConfirm">确 定</el-button>
          <el-button @click="dialogVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { listShopProductSkuApi } from '@/api/shop/productSku'
import { getCategoryTreeApi } from '@/api/shop/category'
import ShopCategorySelect from '@/components/ShopCategorySelect/index.vue'

const props = withDefaults(defineProps<{
  /** 选中的规格 id（shop_product_sku.id），多选档是 id 数组 */
  modelValue?: number | string | Array<number | string>
  /** 编辑回显时展示的文本（由父组件传入，本组件不额外查详情） */
  label?: string
  placeholder?: string
  disabled?: boolean
  title?: string
  /** 复选框多选：确定时抛 id 数组 + 整行数组。默认 false 保持单击单选的行为 */
  multiple?: boolean
  /** 锁定在这个商品名下挑规格：传了就只列它的 SKU，分类筛选收掉 */
  productId?: number | string
  /** 候选再收窄一档的四个筛选，都由调用方按业务定死，弹窗里不给改 */
  storeId?: number | string
  /** 只列这个潮玩系列下的商品规格（筛的是商品那一侧的 trendy_id） */
  trendyId?: number | string
  saleEnable?: number
  productStatus?: number
}>(), {
  modelValue: undefined,
  label: '',
  placeholder: '请选择规格',
  disabled: false,
  title: '选择规格',
  multiple: false,
  productId: undefined,
  storeId: undefined,
  trendyId: undefined,
  saleEnable: undefined,
  productStatus: undefined
})

const emit = defineEmits<{
  'update:modelValue': [value: number | string | Array<number | string> | undefined]
  /** 选中规格时抛出整行数据（含 productId/productName/productCode/specText/productPrice），便于父组件回填；多选档抛整行数组 */
  'select': [sku: any]
  'clear': []
}>()

const dialogVisible = ref(false)
const loading = ref(false)
const skuList = ref<any[]>([])
const total = ref(0)

const searchForm = reactive<{ keyword: string; categoryId: any }>({
  keyword: '',
  categoryId: undefined
})
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10
})

const categoryOptions = ref<any[]>([])
let optionsLoaded = false

const currentRow = ref<any>()
const tableRef = ref<any>()
const multiSelection = ref<any[]>([])
// 单选档自己缓存的名称：外部改了值就不能再用它（见下面那个 watch）
const selectedText = ref('')
const lastEmitted = ref<number | string | Array<number | string> | undefined>()

/** 这一行给输入框看的文本：商品名 + 规格，只有规格没名字不够认 */
const rowText = (row: any) => `${row.productName || ''}${row.specText ? ` ${row.specText}` : ''}`.trim()

const selectedIds = computed<string[]>(() => {
  if (!props.multiple || !Array.isArray(props.modelValue)) return []
  return props.modelValue.map((id) => String(id))
})

const displayText = computed(() => {
  if (props.multiple) {
    return props.label || (selectedIds.value.length ? `已选 ${selectedIds.value.length} 项` : '')
  }
  if (!props.modelValue) {
    return props.label || ''
  }
  return selectedText.value || props.label || ''
})

/**
 * 外部把值改掉（表单重置、切到另一条记录）时清掉内部缓存，
 * 改由父组件传的 label 决定显示，否则会残留上一个规格
 */
watch(() => props.modelValue, (value) => {
  if (props.multiple) return
  if (String(value ?? '') !== String(lastEmitted.value ?? '')) {
    selectedText.value = ''
  }
})

/** 分类树只有点开弹窗才用得上，所以第一次打开时才取 */
const loadOptions = async () => {
  if (optionsLoaded || props.productId) return
  try {
    const { data } = await getCategoryTreeApi({})
    categoryOptions.value = data || []
    optionsLoaded = true
  } catch (error) {
  }
}

const getList = async () => {
  loading.value = true
  try {
    const { data } = await listShopProductSkuApi({
      ...queryParams,
      keyword: searchForm.keyword.trim() || undefined,
      categoryId: props.productId ? undefined : searchForm.categoryId,
      productId: props.productId,
      storeId: props.storeId,
      sectionId: props.trendyId,
      saleEnable: props.saleEnable,
      productStatus: props.productStatus
    })
    skuList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.pageNum = 1
  getList()
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.categoryId = undefined
  queryParams.pageNum = 1
  getList()
}

const handleSizeChange = () => {
  queryParams.pageNum = 1
  getList()
}

const handleCurrentChange = (row: any) => {
  currentRow.value = row
}

const handleSelectionChange = (rows: any[]) => {
  multiSelection.value = rows
}

/** 双击只在单选档等于「确定」；多选档它可能只是随手一下，勾了没勾全得由确定说了算 */
const handleRowDblclick = () => {
  if (props.multiple) return
  handleConfirm()
}

const openDialog = async () => {
  if (props.disabled) return
  multiSelection.value = []
  await Promise.all([loadOptions(), getList()])
  dialogVisible.value = true
  // destroy-on-close，表格每次打开都是新实例，所以每次都要重新把已选的那几行勾回去
  nextTick(() => {
    if (props.multiple) {
      const ids = selectedIds.value
      skuList.value.forEach((row) => {
        if (ids.includes(String(row.id))) {
          tableRef.value?.toggleRowSelection(row, true)
        }
      })
      return
    }
    currentRow.value = skuList.value.find((item) => String(item.id) === String(props.modelValue))
  })
}

const handleConfirm = () => {
  if (props.multiple) {
    const rows = multiSelection.value
    lastEmitted.value = rows.map((row) => row.id)
    emit('update:modelValue', lastEmitted.value)
    emit('select', rows)
    dialogVisible.value = false
    return
  }
  if (currentRow.value) {
    selectedText.value = rowText(currentRow.value)
    lastEmitted.value = currentRow.value.id
    emit('update:modelValue', currentRow.value.id)
    emit('select', currentRow.value)
  }
  dialogVisible.value = false
}

const handleClear = () => {
  selectedText.value = ''
  lastEmitted.value = undefined
  currentRow.value = undefined
  multiSelection.value = []
  emit('update:modelValue', props.multiple ? [] : undefined)
  emit('clear')
}
</script>

<style lang="scss" scoped>
.shop-product-sku-select {
  width: 100%;

  :deep(.el-input-group__append) {
    width: 5%;
  }
}

.thumb {
  width: 40px;
  height: 40px;
  border-radius: 4px;
}

.ml4 {
  margin-left: 4px;
}
</style>
