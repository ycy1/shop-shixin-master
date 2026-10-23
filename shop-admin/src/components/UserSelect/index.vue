<template>
  <div class="user-select">
    <el-input :model-value="displayText" :placeholder="placeholder" readonly :disabled="disabled" clearable
      @click="openDialog" @clear="handleClear">
      <template #append>
        <el-button :disabled="disabled" icon="Search" @click="openDialog" />
      </template>
    </el-input>

    <el-dialog :title="title" v-model="dialogVisible" width="700px" append-to-body destroy-on-close>
      <!-- 搜索 -->
      <div class="search-wrapper">
        <el-form :inline="true" @submit.prevent>
          <el-form-item>
            <el-input v-model="searchForm.keyword" placeholder="请输入昵称搜索" clearable @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleSearch">搜索</el-button>
            <el-button icon="Refresh" @click="resetSearch">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table ref="tableRef" v-loading="loading" :data="userList" height="360" row-key="id"
        :highlight-current-row="!multiple" @current-change="handleCurrentChange" @row-dblclick="handleRowDblclick"
        @selection-change="handleSelectionChange">
        <!-- 多选那一档加复选框：reserve-selection 让翻页/搜索后已选的行不掉（源码 store/index.mjs setData：
             开了 reserveSelection 就只 assertRowKey，不 clearSelection/cleanSelection），
             所以 selection-change 抛出来的是跨页累积的完整已选集合 -->
        <el-table-column v-if="multiple" type="selection" width="45" reserve-selection />
        <el-table-column label="昵称" prop="nickname" align="center" show-overflow-tooltip />
        <el-table-column label="用户名" prop="username" align="center" show-overflow-tooltip />
        <el-table-column label="手机号" prop="mobile" align="center" width="140">
          <template #default="{ row }">
            <span>{{ row.mobile || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 30, 50]" :total="total" :background="true"
          layout="total, sizes, prev, pager, next, jumper" @size-change="getList" @current-change="getList" />
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
import { getUserListApi } from '@/api/system/user'

const props = withDefaults(defineProps<{
  /** 单选传 id，多选传 id 数组 */
  modelValue?: number | number[] | undefined
  placeholder?: string
  disabled?: boolean
  title?: string
  /** 编辑回显时展示的用户名称（由父组件传入） */
  label?: string
  /** 只列启用状态的用户。默认 false：按用户搜订单/退款时要能选到停用用户 */
  onlyEnabled?: boolean
  /**
   * 只列这一类账号类型的用户（sys_user.account_type：0平台 1门店 2送货员 3消费者 4供应商 5ERP操作员）。
   * 由调用方按业务传，如供应商表单传 4、选消费者传 3；不传就是全部类型。
   * 传数组就是这几档都要（如优惠券的「负责的运营」传 [0, 1]：平台 + 门店）。
   * 取值见字典 account_type。
   */
  accountType?: number | number[]
  /** 复选框多选：确定时抛 id 数组 + 整行数组。默认 false 保持原来单击单选的行为 */
  multiple?: boolean
}>(), {
  placeholder: '请选择用户',
  disabled: false,
  title: '选择用户',
  label: '',
  onlyEnabled: false,
  accountType: undefined,
  multiple: false
})

const emit = defineEmits<{
  'update:modelValue': [value: number | number[] | undefined]
  /** 选中用户时抛出整行数据，便于父组件回填昵称、手机号等冗余字段；多选档抛的是整行数组 */
  'select': [user: any]
  /** 清空已选用户 */
  'clear': []
}>()

const dialogVisible = ref(false)
const loading = ref(false)
const userList = ref<any[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  nickname: ''
})
const searchForm = reactive({
  keyword: ''
})

// 弹窗内当前高亮行
const currentRow = ref<any>()
// 弹窗表格实例（多选档要拿它 toggleRowSelection 回显已选）
const tableRef = ref<any>()
// 多选档弹窗里当前勾中的行（reserve-selection，所以是跨页累积的完整集合）
const multiSelection = ref<any[]>([])
// 最近一次从弹窗选择得到的用户名称
const selectedName = ref('')
// 最近一次由本组件选择产生的值，用来区分"用户自己选的"和"外部改动"
const lastEmitted = ref<number | number[] | undefined>()

/** 多选档把 modelValue 收成 id 数组；单选档给空数组 */
const selectedIds = computed<string[]>(() => {
  if (!props.multiple || !Array.isArray(props.modelValue)) return []
  return props.modelValue.map((id) => String(id))
})

const displayText = computed(() => {
  // 多选档不缓存名称：值本身就是 id 数组，名称由父组件的 label 决定，这里只报数量
  if (props.multiple) {
    return props.label || (selectedIds.value.length ? `已选 ${selectedIds.value.length} 项` : '')
  }
  // 值为空时一律不能再用内部缓存，否则外部重置后还会显示上一次选中的用户。
  // 这里放在 computed 里判断而不是只靠 watch，是为了不依赖改动时序
  if (!props.modelValue) {
    return props.label || ''
  }
  return selectedName.value || props.label || ''
})

/**
 * 外部把值改掉（搜索表单重置、切换到另一条记录）时，
 * 清掉内部缓存的名称，改由父组件传的 label 决定显示，否则会残留上一个用户
 */
watch(() => props.modelValue, (value) => {
  if (props.multiple) return
  if (value !== lastEmitted.value) {
    selectedName.value = ''
  }
})

const getList = async () => {
  loading.value = true
  try {
    // 数组走 accountTypes（逗号串，Spring 直接转成 List<Integer>），单值走 accountType：
    // 后端那两条 <if> 是各自独立的，同时传就成了 AND
    const { data } = await getUserListApi({
      ...queryParams,
      status: props.onlyEnabled ? 1 : undefined,
      accountType: Array.isArray(props.accountType) ? undefined : props.accountType,
      accountTypes: Array.isArray(props.accountType) ? props.accountType.join(',') : undefined
    })
    userList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.nickname = searchForm.keyword
  queryParams.pageNum = 1
  getList()
}

const resetSearch = () => {
  searchForm.keyword = ''
  queryParams.nickname = ''
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
  await getList()
  dialogVisible.value = true
  // destroy-on-close，表格每次打开都是新实例，所以每次都要重新把已选的那几行勾回去
  nextTick(() => {
    if (props.multiple) {
      const ids = selectedIds.value
      userList.value.forEach((row) => {
        if (ids.includes(String(row.id))) {
          tableRef.value?.toggleRowSelection(row, true)
        }
      })
      return
    }
    // 回显已选用户高亮
    if (props.modelValue) {
      const found = userList.value.find((u) => String(u.id) === String(props.modelValue))
      currentRow.value = found
    }
  })
}

const handleConfirm = () => {
  if (props.multiple) {
    const rows = multiSelection.value
    const ids = rows.map((row) => row.id)
    lastEmitted.value = ids
    emit('update:modelValue', ids)
    emit('select', rows)
    dialogVisible.value = false
    return
  }
  if (currentRow.value) {
    selectedName.value = currentRow.value.nickname || currentRow.value.username
    lastEmitted.value = currentRow.value.id
    emit('update:modelValue', currentRow.value.id)
    emit('select', currentRow.value)
  }
  dialogVisible.value = false
}

const handleClear = () => {
  selectedName.value = ''
  lastEmitted.value = undefined
  if (props.multiple) {
    multiSelection.value = []
    emit('update:modelValue', [])
    emit('clear')
    return
  }
  emit('update:modelValue', undefined)
  emit('clear')
}
</script>

<style lang="scss" scoped>
.user-select {
  width: 100%;

  :deep(.el-input-group__append) {
    width: 5%;
  }
}
</style>
