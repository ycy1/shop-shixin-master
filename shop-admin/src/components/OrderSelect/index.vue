<template>
  <div class="order-select">
    <el-input :model-value="displayText" :placeholder="placeholder" readonly :disabled="disabled" clearable
      @click="openDialog" @clear="handleClear">
      <template #append>
        <el-button :disabled="disabled" icon="Search" @click="openDialog" />
      </template>
    </el-input>

    <el-dialog :title="title" v-model="dialogVisible" width="900px" append-to-body destroy-on-close>
      <!-- 搜索 -->
      <div class="search-wrapper">
        <el-form :inline="true" @submit.prevent>
          <el-form-item>
            <el-input v-model="queryParams.orderNo" placeholder="请输入订单编号" clearable @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="queryParams.mobile" placeholder="请输入手机号" clearable @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleSearch">搜索</el-button>
            <el-button icon="Refresh" @click="resetSearch">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table v-loading="loading" :data="orderList" height="360" highlight-current-row
        @current-change="handleCurrentChange" @row-dblclick="handleConfirm">
        <el-table-column label="订单编号" prop="orderNo" show-overflow-tooltip />
        <el-table-column label="用户" prop="userName" width="110" show-overflow-tooltip />
        <el-table-column label="手机号" prop="mobile" width="120" />
        <el-table-column label="实付金额" width="110" align="right">
          <template #default="{ row }">
            <span>{{ validate.formatAmount(row.payAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="订单状态" align="center" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag('order_status', row.status)">
              {{ dict.label('order_status', row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" width="170">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.createTime, 'YYYY-MM-DD HH') }}</span>
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
import { listSysOrderApi } from '@/api/order/sysorder'
import { useDict } from '@/utils/dictCache'
import validate from '@/utils/validate'

const props = withDefaults(defineProps<{
  /** 选中订单的主键（sys_order.id） */
  modelValue?: number | undefined
  placeholder?: string
  disabled?: boolean
  title?: string
  /** 编辑回显时展示的订单编号（由父组件传入） */
  label?: string
}>(), {
  placeholder: '请选择订单',
  disabled: false,
  title: '选择订单',
  label: ''
})

const emit = defineEmits<{
  'update:modelValue': [value: number | undefined]
  /** 选中订单时抛出整行数据，便于父组件回填订单编号等字段 */
  'select': [order: any]
  /** 清空已选订单 */
  'clear': []
}>()

const dict = useDict('order_status')

const dialogVisible = ref(false)
const loading = ref(false)
const orderList = ref<any[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  orderNo: '',
  mobile: '',
  // 只列出已付款的订单（已支付/已完成），未付款的不该被关联退款
  paidOnly: true
})

// 弹窗内当前高亮行
const currentRow = ref<any>()
// 最近一次从弹窗选择得到的订单编号
const selectedLabel = ref('')
// 最近一次由本组件选择产生的值，用来区分"用户自己选的"和"外部改动"
const lastEmitted = ref<number | undefined>()

const displayText = computed(() => {
  // 值为空时一律不能再用内部缓存，否则外部重置后还会显示上一次选中的订单。
  // 这里放在 computed 里判断而不是只靠 watch，是为了不依赖改动时序
  if (!props.modelValue) {
    return props.label || ''
  }
  return selectedLabel.value || props.label || ''
})

/**
 * 外部把值改掉（表单重置、切换到另一条记录）时，
 * 清掉内部缓存的编号，改由父组件传的 label 决定显示，否则会残留上一个订单
 */
watch(() => props.modelValue, (value) => {
  if (value !== lastEmitted.value) {
    selectedLabel.value = ''
  }
})

const getList = async () => {
  loading.value = true
  try {
    const { data } = await listSysOrderApi(queryParams)
    orderList.value = data.records || []
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
  queryParams.orderNo = ''
  queryParams.mobile = ''
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
  // 回显已选订单高亮
  nextTick(() => {
    if (props.modelValue) {
      const found = orderList.value.find((o) => o.id === props.modelValue)
      currentRow.value = found || undefined
    }
  })
}

const handleConfirm = () => {
  if (currentRow.value) {
    selectedLabel.value = currentRow.value.orderNo
    lastEmitted.value = currentRow.value.id
    emit('update:modelValue', currentRow.value.id)
    emit('select', currentRow.value)
  }
  dialogVisible.value = false
}

const handleClear = () => {
  selectedLabel.value = ''
  lastEmitted.value = undefined
  emit('update:modelValue', undefined)
  emit('clear')
}
</script>

<style lang="scss" scoped>
.order-select {
  width: 100%;
}
</style>
