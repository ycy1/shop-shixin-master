<template>
  <div class="shop-product-select">
    <el-input :model-value="displayText" :placeholder="placeholder" readonly :disabled="disabled" clearable
      @click="openDialog" @clear="handleClear">
      <template #append>
        <el-button :disabled="disabled" icon="Search" @click="openDialog" />
      </template>
    </el-input>

    <el-dialog :title="title" v-model="dialogVisible" width="900px" append-to-body destroy-on-close>
      <!-- 筛选：商品库自己那三格（关键字 / 分类 / 品牌） -->
      <div class="search-wrapper">
        <el-form :inline="true" @submit.prevent>
          <el-form-item>
            <el-input v-model="searchForm.keyword" placeholder="商品名称/编码" clearable @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item>
            <shop-category-select v-model="searchForm.categoryId" :data="categoryOptions" placeholder="全部分类"
              @change="handleCategoryChange" />
          </el-form-item>
          <el-form-item>
            <el-select v-model="searchForm.brandId" placeholder="全部品牌" clearable filterable>
              <el-option v-for="item in brandOptions" :key="item.id" :label="item.brandName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleSearch">搜索</el-button>
            <el-button icon="Refresh" @click="resetSearch">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table ref="tableRef" v-loading="loading" :data="productList" height="360" row-key="id"
        :highlight-current-row="!multiple" @current-change="handleCurrentChange" @row-dblclick="handleRowDblclick"
        @selection-change="handleSelectionChange">
        <!-- 多选那一档加复选框：reserve-selection 让翻页/换筛选后已选的行不掉（源码 store/index.mjs setData：
             开了 reserveSelection 就只 assertRowKey，不 clearSelection/cleanSelection），
             所以 selection-change 抛出来的是跨页累积的完整已选集合 -->
        <el-table-column v-if="multiple" type="selection" width="45" reserve-selection />
        <!-- 主图能点开查看器，但这一行的"单击选中 / 双击确定"不能被顺带做掉，所以图片上把两个事件挡掉；
             preview-teleported 必开：不 teleport 的话查看器被困在弹窗的层叠上下文里，会被遮罩压住 -->
        <el-table-column label="主图" align="center" width="70">
          <template #default="{ row }">
            <el-image v-if="row.mainImage" :src="row.mainImage" :preview-src-list="[row.mainImage]"
              preview-teleported fit="cover" class="thumb" @click.stop @dblclick.stop />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="商品编码" prop="productCode" align="center" width="150" show-overflow-tooltip />
        <el-table-column label="商品名称" prop="productName" align="center" min-width="160" show-overflow-tooltip />
        <el-table-column label="所属分类" prop="categoryName" align="center" width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.categoryName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="品牌" align="center" width="140">
          <template #default="{ row }">
            <shop-brand-option v-if="row.brandName" :brand-name="row.brandName" :logo="row.brandLogo" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="单价" align="center" width="100">
          <template #default="{ row }">
            <span>{{ row.productPrice == null || row.productPrice === '' ? '-' : `￥${Number(row.productPrice).toFixed(2)}` }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
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
import { listShopProductApi } from '@/api/shop/product'
import { getCategoryTreeApi } from '@/api/shop/category'
import { optionsShopBrandApi } from '@/api/shop/brand'
import ShopCategorySelect from '@/components/ShopCategorySelect/index.vue'
import ShopBrandOption from '@/components/ShopBrandOption/index.vue'

const props = withDefaults(defineProps<{
  /** 选中的商品 id（shop_product.id），多选档是 id 数组 */
  modelValue?: number | string | Array<number | string>
  /** 编辑回显时展示的商品名称（由父组件传入，本组件不额外查详情） */
  label?: string
  placeholder?: string
  disabled?: boolean
  title?: string
  /** 复选框多选：确定时抛 id 数组 + 整行数组。默认 false 保持原来单击单选的行为 */
  multiple?: boolean
}>(), {
  modelValue: undefined,
  label: '',
  placeholder: '请选择商品',
  disabled: false,
  title: '选择商品',
  multiple: false
})

const emit = defineEmits<{
  'update:modelValue': [value: number | string | Array<number | string> | undefined]
  /** 选中商品时抛出整行数据，便于父组件回填名称、编码等冗余字段；多选档抛的是整行数组 */
  'select': [product: any]
  'clear': []
}>()

const dialogVisible = ref(false)
const loading = ref(false)
const productList = ref<any[]>([])
const total = ref(0)

const searchForm = reactive<{ keyword: string; categoryId: any; brandId: any }>({
  keyword: '',
  categoryId: undefined,
  brandId: undefined
})
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10
})

const categoryOptions = ref<any[]>([])
const brandOptions = ref<any[]>([])
let optionsLoaded = false

const currentRow = ref<any>()
// 弹窗表格实例（多选档要拿它 toggleRowSelection 回显已选）
const tableRef = ref<any>()
// 多选档弹窗里当前勾中的行（reserve-selection，所以是跨页累积的完整集合）
const multiSelection = ref<any[]>([])
const selectedName = ref('')
// 最近一次由本组件选择产生的值，用来区分"用户自己选的"和"外部改动"
const lastEmitted = ref<number | string | Array<number | string> | undefined>()

/** 多选档把 modelValue 收成字符串 id 数组；单选档给空数组 */
const selectedIds = computed<string[]>(() => {
  if (!props.multiple || !Array.isArray(props.modelValue)) return []
  return props.modelValue.map((id) => String(id))
})

const displayText = computed(() => {
  // 多选档不缓存名称：值本身就是 id 数组，名称由父组件的 label 决定，这里只报数量
  if (props.multiple) {
    return props.label || (selectedIds.value.length ? `已选 ${selectedIds.value.length} 项` : '')
  }
  // 值为空时一律不能再用内部缓存，否则外部重置后还会显示上一次选中的商品
  if (!props.modelValue) {
    return props.label || ''
  }
  return selectedName.value || props.label || ''
})

/**
 * 外部把值改掉（表单重置、切换到另一条记录）时清掉内部缓存的名称，
 * 改由父组件传的 label 决定显示，否则会残留上一个商品
 */
watch(() => props.modelValue, (value) => {
  if (props.multiple) return
  if (String(value ?? '') !== String(lastEmitted.value ?? '')) {
    selectedName.value = ''
  }
})

/** 分类树和品牌：只有点开弹窗才用得上，所以第一次打开时才取，不进页面就打请求 */
const loadOptions = async () => {
  if (optionsLoaded) return
  try {
    const { data } = await getCategoryTreeApi({})
    categoryOptions.value = data || []
    optionsLoaded = true
  } catch (error) {
  }
  await getBrandOptions(searchForm.categoryId)
}

/** 品牌候选：口径同商品管理表单，传分类时返回"该分类及其上级 + 未分类"，不传就是启用中的全部品牌 */
const getBrandOptions = async (categoryId?: any) => {
  try {
    const { data } = await optionsShopBrandApi(categoryId)
    brandOptions.value = data || []
  } catch (error) {
  }
}

/**
 * 切分类时重载品牌候选。
 * 商品表单那里刻意不清已选品牌（那是要存库的值），这里是筛选条件：
 * 留着新候选里已经没有的品牌，只会筛出一个空列表，所以直接清掉。
 */
const handleCategoryChange = async (categoryId: any) => {
  await getBrandOptions(categoryId)
  if (searchForm.brandId && !brandOptions.value.some((item: any) => String(item.id) === String(searchForm.brandId))) {
    searchForm.brandId = undefined
  }
  handleSearch()
}

const getList = async () => {
  loading.value = true
  try {
    const { data } = await listShopProductApi({
      ...queryParams,
      keyword: searchForm.keyword.trim() || undefined,
      categoryId: searchForm.categoryId,
      brandId: searchForm.brandId
    })
    productList.value = data.records || []
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
  searchForm.brandId = undefined
  // 分类清掉了，品牌候选也要退回全量，否则下拉里还留着上一个分类那几家
  getBrandOptions(undefined)
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
      productList.value.forEach((row) => {
        if (ids.includes(String(row.id))) {
          tableRef.value?.toggleRowSelection(row, true)
        }
      })
      return
    }
    // 回显已选商品高亮：只在这一页里找，找不到就不高亮（值可能来自上一次的筛选结果）
    currentRow.value = productList.value.find((item) => String(item.id) === String(props.modelValue))
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
    selectedName.value = currentRow.value.productName
    lastEmitted.value = currentRow.value.id
    emit('update:modelValue', currentRow.value.id)
    emit('select', currentRow.value)
  }
  dialogVisible.value = false
}

const handleClear = () => {
  selectedName.value = ''
  lastEmitted.value = undefined
  currentRow.value = undefined
  multiSelection.value = []
  emit('update:modelValue', props.multiple ? [] : undefined)
  emit('clear')
}
</script>

<style lang="scss" scoped>
.shop-product-select {
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
</style>
