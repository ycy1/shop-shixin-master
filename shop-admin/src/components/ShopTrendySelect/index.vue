<template>
  <div class="shop-trendy-select">
    <el-input v-if="trigger" :model-value="displayText" :placeholder="placeholder" readonly :disabled="disabled"
      clearable @click="openDialog" @clear="handleClear">
      <template #append>
        <el-button :disabled="disabled" icon="Search" @click="openDialog" />
      </template>
    </el-input>

    <el-dialog :title="title" v-model="dialogVisible" width="900px" append-to-body destroy-on-close>
      <!-- 筛选：这条查询服务端只支持「名称模糊 + 状态」两档，所以只给这两格 -->
      <div class="search-wrapper">
        <el-form :inline="true" @submit.prevent>
          <el-form-item>
            <el-input v-model="searchForm.keyword" placeholder="潮玩名称" clearable @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item>
            <el-select v-model="searchForm.status" placeholder="全部状态" clearable>
              <el-option label="启用" :value="1" />
              <el-option label="禁用" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleSearch">搜索</el-button>
            <el-button icon="Refresh" @click="resetSearch">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table ref="tableRef" v-loading="loading" :data="trendyList" height="360" row-key="id"
        :highlight-current-row="!multiple" @current-change="handleCurrentChange" @row-dblclick="handleRowDblclick"
        @selection-change="handleSelectionChange">
        <!-- 多选档加复选框：reserve-selection 让翻页/换筛选后已选的行不掉，所以 selection-change 抛的是跨页累积的完整集合 -->
        <el-table-column v-if="multiple" type="selection" width="45" reserve-selection />
        <!-- 封面点开查看器，但这一行的"单击选中 / 双击确定"不能被顺带做掉，所以图片上把两个事件挡掉；
             preview-teleported 必开：不 teleport 的话查看器被困在弹窗的层叠上下文里，会被遮罩压住 -->
        <el-table-column label="封面" align="center" width="70">
          <template #default="{ row }">
            <el-image v-if="row.coverImage" :src="row.coverImage" :preview-src-list="[row.coverImage]"
              preview-teleported fit="contain" class="thumb" @click.stop @dblclick.stop />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="潮玩名称" prop="trendyName" align="center" min-width="160" show-overflow-tooltip />
        <el-table-column label="说明" align="center" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.description || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
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
          <el-button type="primary" :disabled="multiple && multiSelection.length === 0" @click="handleConfirm">确 定
          </el-button>
          <el-button @click="dialogVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { listShopTrendyApi } from '@/api/trendy/trendy'

/**
 * 潮玩系列选择器（公共件）
 *
 * 形状和 ShopProductSelect 一致：单选档那颗输入框 + 弹窗挑一条，多选档只要弹窗（trigger=false）由调用方
 * 通过 ref 的 openDialog() 开、底部确定抛一批。query 由调用方压死一条筛选（比如 IP 那侧的 noIp=true），
 * 弹窗里不给改 —— 口径同商品弹窗给潮玩用的那一套。
 */
const props = withDefaults(defineProps<{
  /** 选中的系列 id（shop_trendy.id），多选档是 id 数组 */
  modelValue?: number | string | Array<number | string>
  /** 编辑回显时展示的名称（由父组件传入，本组件不额外查详情） */
  label?: string
  placeholder?: string
  disabled?: boolean
  title?: string
  /** 复选框多选：确定时抛 id 数组 + 整行数组。默认 false 保持单击单选的行为 */
  multiple?: boolean
  /** 要不要那颗触发用的输入框：只做「按钮开弹窗」时给 false */
  trigger?: boolean
  /** 调用方按业务钉死的筛选条件，合并进列表请求（如 { noIp: true }） */
  query?: Record<string, any>
}>(), {
  modelValue: undefined,
  label: '',
  placeholder: '请选择潮玩系列',
  disabled: false,
  title: '选择潮玩系列',
  multiple: false,
  trigger: true,
  query: undefined
})

const emit = defineEmits<{
  'update:modelValue': [value: number | string | Array<number | string> | undefined]
  /** 选中时抛出整行数据，便于父组件回填名称等冗余字段；多选档抛的是整行数组 */
  'select': [trendy: any]
  'clear': []
}>()

const dialogVisible = ref(false)
const loading = ref(false)
const trendyList = ref<any[]>([])
const total = ref(0)

const searchForm = reactive<{ keyword: string; status: any }>({
  keyword: '',
  status: undefined
})
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10
})

const currentRow = ref<any>()
const tableRef = ref<any>()
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
  // 值为空时一律不能再用内部缓存，否则外部重置后还会显示上一次选中的那条
  if (!props.modelValue) {
    return props.label || ''
  }
  return selectedName.value || props.label || ''
})

/**
 * 外部把值改掉（表单重置、切换到另一条记录）时清掉内部缓存的名称，
 * 改由父组件传的 label 决定显示，否则会残留上一个系列
 */
watch(() => props.modelValue, (value) => {
  if (props.multiple) return
  if (String(value ?? '') !== String(lastEmitted.value ?? '')) {
    selectedName.value = ''
  }
})

const getList = async () => {
  loading.value = true
  try {
    const { data } = await listShopTrendyApi({
      ...queryParams,
      trendyName: searchForm.keyword.trim() || undefined,
      status: searchForm.status,
      ...props.query
    })
    trendyList.value = data.records || []
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
  searchForm.status = undefined
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

const openDialog = () => {
  if (props.disabled) return
  multiSelection.value = []
  getList()
  dialogVisible.value = true
  // destroy-on-close，表格每次打开都是新实例，所以每次都要重新把已选的那几行勾回去
  nextTick(() => {
    if (props.multiple) {
      const ids = selectedIds.value
      trendyList.value.forEach((row) => {
        if (ids.includes(String(row.id))) {
          tableRef.value?.toggleRowSelection(row, true)
        }
      })
      return
    }
    // 回显已选高亮：只在这一页里找，找不到就不高亮（值可能来自上一次的筛选结果）
    currentRow.value = trendyList.value.find((item) => String(item.id) === String(props.modelValue))
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
    selectedName.value = currentRow.value.trendyName
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

defineExpose({ openDialog })
</script>

<style lang="scss" scoped>
.shop-trendy-select {
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
