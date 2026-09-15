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

      <el-table v-loading="loading" :data="userList" height="360" highlight-current-row
        @current-change="handleCurrentChange" @row-dblclick="handleConfirm">
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
          <el-button type="primary" @click="handleConfirm">确 定</el-button>
          <el-button @click="dialogVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { getUserListApi } from '@/api/system/user'

const props = withDefaults(defineProps<{
  modelValue?: number | undefined
  placeholder?: string
  disabled?: boolean
  title?: string
  /** 编辑回显时展示的用户名称（由父组件传入） */
  label?: string
}>(), {
  placeholder: '请选择用户',
  disabled: false,
  title: '选择用户',
  label: ''
})

const emit = defineEmits<{
  'update:modelValue': [value: number | undefined]
  /** 选中用户时抛出整行数据，便于父组件回填昵称、手机号等冗余字段 */
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
// 最近一次从弹窗选择得到的用户名称
const selectedName = ref('')
// 最近一次由本组件选择产生的值，用来区分"用户自己选的"和"外部改动"
const lastEmitted = ref<number | undefined>()

const displayText = computed(() => {
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
  if (value !== lastEmitted.value) {
    selectedName.value = ''
  }
})

const getList = async () => {
  loading.value = true
  try {
    const { data } = await getUserListApi(queryParams)
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

const openDialog = async () => {
  if (props.disabled) return
  await getList()
  dialogVisible.value = true
  // 回显已选用户高亮
  nextTick(() => {
    if (props.modelValue) {
      const found = userList.value.find((u) => u.id === props.modelValue)
      if (found) {
        currentRow.value = found
      } else {
        currentRow.value = undefined
      }
    }
  })
}

const handleConfirm = () => {
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
