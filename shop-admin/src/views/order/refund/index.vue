<template>
  <div class="app-container">

    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form :model="queryParams" ref="queryFormRef" inline>
        <el-form-item label="退款单号" prop="refundNo">
          <el-input v-model="queryParams.refundNo" placeholder="请输入退款单号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="订单编号" prop="orderNo">
          <el-input v-model="queryParams.orderNo" placeholder="请输入订单编号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="申请人" prop="userId">
          <template #label>
            申请人&nbsp;&nbsp;&nbsp;
          </template>
          <UserSelect v-model="queryParams.userId" style="width: 240px" />
        </el-form-item>
        <el-form-item label="手机号" prop="mobile">
          <template #label>
            手机号&nbsp;&nbsp;&nbsp;
          </template>
          <el-input v-model="queryParams.mobile" placeholder="请输入手机号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="退款状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable @change="handleQuery">
            <el-option v-for="item in dict.options('refund_status')" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="退款渠道" prop="refundChannel">
          <el-select v-model="queryParams.refundChannel" placeholder="全部" clearable @change="handleQuery">
            <el-option v-for="item in REFUND_CHANNEL_OPTIONS" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请时间">
          <el-date-picker v-model="applyTimeRange" type="datetimerange" range-separator="至" start-placeholder="开始时间"
            end-placeholder="结束时间" value-format="YYYY-MM-DD HH:mm:ss" style="width: 380px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-card class="box-card">
      <!-- 操作工具栏 -->
      <template #header>
        <div class="card-header">
          <ButtonGroup>
            <el-button type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
            <el-button type="danger" plain icon="Delete" :disabled="selectedIds.length === 0"
              @click="handleBatchDelete">批量删除</el-button>
          </ButtonGroup>
        </div>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" style="width: 100%" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="退款单号" align="center" prop="refundNo" width="200" show-overflow-tooltip />
        <el-table-column label="订单编号" align="center" prop="orderNo" width="200" show-overflow-tooltip />
        <el-table-column label="申请人" align="center" prop="userName" width="120" show-overflow-tooltip />
        <el-table-column label="手机号" align="center" prop="mobile" width="130" />
        <el-table-column label="退款类型" align="center" width="100">
          <template #default="scope">
            <el-tag size="small" :type="refundTypeTag(scope.row.refundType)">
              {{ labelOf(REFUND_TYPE_OPTIONS, scope.row.refundType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请金额" align="right" width="120">
          <template #default="scope">{{ validate.formatAmount(scope.row.refundAmount) }}</template>
        </el-table-column>
        <el-table-column label="实退金额" align="right" width="120">
          <template #default="scope">
            <span style="font-weight: 600; color: var(--el-color-danger)">
              {{ validate.formatAmount(scope.row.actualRefundAmount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="退款状态" align="center" width="110">
          <template #default="scope">
            <el-tag size="small" :type="dict.tag('refund_status', scope.row.status)">
              {{ dict.label('refund_status', scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="退款渠道" align="center" width="100">
          <template #default="scope">{{ labelOf(REFUND_CHANNEL_OPTIONS, scope.row.refundChannel) }}</template>
        </el-table-column>
        <el-table-column label="审核人" align="center" prop="auditorName" width="120" show-overflow-tooltip />
        <el-table-column label="申请时间" align="center" prop="applyTime" width="170">
          <template #default="scope">{{ validate.formatTime(scope.row.applyTime, 'YYYY-MM-DD HH') }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="200" fixed="right">
          <template #default="scope">
            <TableMoreActions :actions="[
              { label: '审核', icon: 'Checked', disabled: scope.row.status !== 0, command: { type: 'audit', row: scope.row } },
              { label: '编辑', icon: 'Edit', command: { type: 'edit', row: scope.row } },
              { label: '删除', type: 'danger', icon: 'Delete', command: { type: 'delete', row: scope.row } }
            ]" @command="handleActionCommand" />
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页工具栏 -->
      <div class="pagination-container">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 30, 50]" :total="total" background layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>

      <!-- 审核对话框 -->
      <el-dialog v-model="auditOpen" title="退款审核" width="640px" append-to-body>
        <el-descriptions :column="1" border size="small" style="margin-bottom: 18px">
          <el-descriptions-item label="退款单号">{{ auditForm.refundNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="关联订单">{{ auditForm.orderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ auditForm.userName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="申请金额">{{ validate.formatAmount(auditForm.refundAmount) }}</el-descriptions-item>
          <el-descriptions-item label="退款原因">{{ auditForm.refundReason || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-form ref="auditFormRef" :model="auditForm" :rules="auditRules" label-width="120px">
          <el-form-item label="审核结果" prop="status">
            <el-radio-group v-model="auditForm.status">
              <el-radio :value="1">审核通过</el-radio>
              <el-radio :value="2">审核拒绝</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="auditForm.status === 1" label="实际退款金额" prop="actualRefundAmount">
            <el-input-number v-model="auditForm.actualRefundAmount" :min="0" :max="auditForm.refundAmount"
              :precision="2" controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item label="审核备注" prop="auditRemark">
            <el-input v-model="auditForm.auditRemark" type="textarea" :rows="3" maxlength="200" show-word-limit
              :placeholder="auditForm.status === 1 ? '请输入审核备注' : '请输入拒绝原因'" />
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="auditOpen = false">取 消</el-button>
            <el-button type="primary" :loading="auditSubmitting" @click="submitAudit">确 定</el-button>
          </div>
        </template>
      </el-dialog>

      <!-- 添加或修改对话框 -->
      <el-dialog v-model="open" :title="title" width="860px" append-to-body>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
          <el-divider content-position="left">退款信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="退款单号" prop="refundNo">
                <el-input v-model="form.refundNo" placeholder="保存后由系统生成" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="关联订单" prop="orderId">
                <OrderSelect v-model="form.orderId" :label="form.orderNo" style="width: 100%"
                  @select="handleOrderSelect" @clear="handleOrderClear" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="退款类型" prop="refundType">
                <el-select v-model="form.refundType" placeholder="请选择退款类型" style="width: 100%">
                  <el-option v-for="item in REFUND_TYPE_OPTIONS" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="币种" prop="currency">
                <el-select v-model="form.currency" placeholder="请选择币种" style="width: 100%">
                  <el-option label="人民币 CNY" value="CNY" />
                  <el-option label="美元 USD" value="USD" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="退款原因" prop="refundReason">
                <el-input v-model="form.refundReason" type="textarea" :rows="2" maxlength="200" show-word-limit
                  placeholder="申请人填写的退款原因" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">申请人</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="申请人" prop="userId">
                <UserSelect v-model="form.userId" :label="form.userName" style="width: 100%" @select="handleUserSelect"
                  @clear="handleUserClear" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="手机号" prop="mobile">
                <el-input v-model="form.mobile" placeholder="选择用户后自动带出" disabled />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">金额信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="申请退款金额" prop="refundAmount">
                <el-input-number v-model="form.refundAmount" :min="0" :precision="2" controls-position="right"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="实际退款金额" prop="actualRefundAmount">
                <el-input-number v-model="form.actualRefundAmount" :min="0" :precision="2" controls-position="right"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">处理信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="退款状态" prop="status">
                <el-select v-model="form.status" placeholder="请选择退款状态" style="width: 100%">
                  <el-option v-for="item in dict.options('refund_status')" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="退款渠道" prop="refundChannel">
                <el-select v-model="form.refundChannel" placeholder="请选择退款渠道" clearable style="width: 100%">
                  <el-option v-for="item in REFUND_CHANNEL_OPTIONS" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="第三方退款单号" prop="thirdRefundNo">
                <el-input v-model="form.thirdRefundNo" placeholder="微信/支付宝返回的退款单号" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="申请时间" prop="applyTime">
                <el-date-picker v-model="form.applyTime" type="datetime" placeholder="请选择申请时间"
                  value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="退款成功时间" prop="refundTime">
                <el-date-picker v-model="form.refundTime" type="datetime" placeholder="请选择退款成功时间"
                  value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="失败原因" prop="failReason">
                <el-input v-model="form.failReason" type="textarea" :rows="2" maxlength="200" show-word-limit
                  placeholder="退款失败时填写" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="备注" prop="remark">
                <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit
                  placeholder="请输入备注" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="cancel">取 消</el-button>
            <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
          </div>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  listSysRefundApi,
  detailSysRefundApi,
  deleteSysRefundApi,
  addSysRefundApi,
  updateSysRefundApi,
  auditSysRefundApi
} from '@/api/order/sysrefund'
import { useDict } from '@/utils/dictCache'
import validate from '@/utils/validate'
import UserSelect from '@/components/UserSelect/index.vue'
import OrderSelect from '@/components/OrderSelect/index.vue'
import TableMoreActions from '@/components/TableMoreActions/index.vue'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'
interface Option {
  value: number
  label: string
}

/** 退款类型：1-全额退款 2-部分退款 */
const REFUND_TYPE_OPTIONS: Option[] = [
  { value: 1, label: '全额退款' },
  { value: 2, label: '部分退款' }
]

/** 退款渠道：1-原路退回 2-余额 3-人工转账 */
const REFUND_CHANNEL_OPTIONS: Option[] = [
  { value: 1, label: '原路退回' },
  { value: 2, label: '余额' },
  { value: 3, label: '人工转账' }
]

/** 退款状态走字典 */
const dict = useDict('refund_status')

const labelOf = (options: Option[], value: any) =>
  options.find(item => item.value === Number(value))?.label || '-'
const refundTypeTag = (value: any): TagType => (Number(value) === 2 ? 'warning' : 'primary')

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()
const auditFormRef = ref<FormInstance>()

// 遮罩层
const loading = ref(false)
// 提交中
const submitting = ref(false)
// 审核提交中
const auditSubmitting = ref(false)
// 选中数组
const selectedIds = ref<number[]>([])
// 总条数
const total = ref(0)
// 表格数据
const dataList = ref<any[]>([])
// 弹出层标题
const title = ref('')
// 是否显示弹出层
const open = ref(false)
// 是否显示审核弹窗
const auditOpen = ref(false)
// 申请时间范围（提交前拆成 beginTime / endTime）
const applyTimeRange = ref<[string, string] | null>()

// 查询参数
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  refundNo: undefined as string | undefined,
  orderNo: undefined as string | undefined,
  userId: undefined as number | undefined,
  mobile: undefined as string | undefined,
  status: undefined as number | undefined,
  refundChannel: undefined as number | undefined,
  beginTime: undefined as string | undefined,
  endTime: undefined as string | undefined
})

// 表单默认值（新增与重置共用，避免残留上一条记录的值）
const defaultForm = () => ({
  id: undefined as number | undefined,
  refundNo: '',
  orderId: undefined as number | undefined,
  orderNo: '',
  userId: undefined as number | undefined,
  userName: '',
  mobile: '',
  refundType: undefined as number | undefined,
  refundReason: '',
  refundAmount: 0,
  actualRefundAmount: 0,
  currency: 'CNY',
  status: dict.defaultValue('refund_status'),
  refundChannel: undefined as number | undefined,
  thirdRefundNo: '',
  applyTime: null as string | null,
  refundTime: null as string | null,
  failReason: '',
  remark: ''
})

// 表单参数
const form = reactive<any>(defaultForm())

// 表单校验
const rules = {
  refundType: [{ required: true, message: '请选择退款类型', trigger: 'change' }],
  userId: [{ required: true, message: '请选择申请人', trigger: 'change' }],
  refundAmount: [{ required: true, message: '请输入申请退款金额', trigger: 'blur' }],
  status: [{ required: true, message: '请选择退款状态', trigger: 'change' }]
}

// 审核表单
const auditForm = reactive({
  id: undefined as number | undefined,
  refundNo: '',
  orderNo: '',
  userName: '',
  refundAmount: 0,
  refundReason: '',
  status: 1,
  actualRefundAmount: 0,
  auditRemark: ''
})

const auditRules = {
  status: [{ required: true, message: '请选择审核结果', trigger: 'change' }],
  actualRefundAmount: [{ required: true, message: '请输入实际退款金额', trigger: 'blur' }]
}

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listSysRefundApi(queryParams)
    dataList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
  } finally {
    loading.value = false
  }
}

/** 表单重置 */
const reset = () => {
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

/** 取消按钮 */
const cancel = () => {
  open.value = false
  reset()
}

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.pageNum = 1
  queryParams.beginTime = applyTimeRange.value?.[0]
  queryParams.endTime = applyTimeRange.value?.[1]
  getList()
}

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.userId = undefined
  queryParams.mobile = undefined
  applyTimeRange.value = null
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: any[]) => {
  selectedIds.value = selection.map(item => item.id)
}

/** 行操作 */
const handleActionCommand = (action: any) => {
  const { type, row } = action.command
  if (type === 'audit') {
    handleAudit(row)
  } else if (type === 'edit') {
    handleUpdate(row)
  } else if (type === 'delete') {
    handleDelete(row)
  }
}

/** 选择用户后回填昵称、手机号 */
const handleUserSelect = (user: any) => {
  form.userName = user?.nickname || user?.username || ''
  form.mobile = user?.mobile || ''
}

/** 清空用户 */
const handleUserClear = () => {
  form.userName = ''
  form.mobile = ''
}

/** 选择关联订单后回填订单编号、申请人、手机号、申请金额 */
const handleOrderSelect = (order: any) => {
  if (!order) return
  form.orderNo = order.orderNo || ''
  // 申请人必须与订单一致，选了订单就直接覆盖，避免换单后还留着上一个用户
  form.userId = order.userId
  form.userName = order.userName || ''
  form.mobile = order.mobile || ''
  // 申请金额默认取订单实付金额（按全额退款预填），部分退款再往下改
  if (order.payAmount !== null && order.payAmount !== undefined) {
    form.refundAmount = Number(order.payAmount)
  }
}

/** 清空关联订单 */
const handleOrderClear = () => {
  form.orderId = undefined
  form.orderNo = ''
}

/** 新增按钮操作 */
const handleAdd = () => {
  reset()
  title.value = '添加退款单'
  open.value = true
}

/** 修改按钮操作 */
const handleUpdate = (row: any) => {
  reset()
  detailSysRefundApi(row.id).then(({ data }) => {
    Object.assign(form, data)
    title.value = '修改退款单'
    open.value = true
  })
}

/** 审核按钮操作 */
const handleAudit = (row: any) => {
  auditForm.id = row.id
  auditForm.refundNo = row.refundNo
  auditForm.orderNo = row.orderNo
  auditForm.userName = row.userName
  auditForm.refundAmount = Number(row.refundAmount) || 0
  auditForm.refundReason = row.refundReason
  auditForm.status = 1
  auditForm.actualRefundAmount = Number(row.refundAmount) || 0
  auditForm.auditRemark = ''
  auditFormRef.value?.clearValidate()
  auditOpen.value = true
}

/** 提交审核 */
const submitAudit = async () => {
  if (!auditFormRef.value) return
  const valid = await auditFormRef.value.validate().catch(() => false)
  if (!valid) return

  auditSubmitting.value = true
  try {
    await auditSysRefundApi({
      id: auditForm.id,
      status: auditForm.status,
      auditRemark: auditForm.auditRemark,
      // 拒绝时不传金额，避免写入无效数据
      actualRefundAmount: auditForm.status === 1 ? auditForm.actualRefundAmount : undefined
    })
    ElMessage.success(auditForm.status === 1 ? '已审核通过' : '已拒绝')
    auditOpen.value = false
    getList()
  } catch (error) {
  } finally {
    auditSubmitting.value = false
  }
}

/** 提交按钮 */
const submitForm = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  // 剔除查询用的临时字段和自动维护的时间戳
  const payload: any = { ...form }
  delete payload.ids
  delete payload.beginTime
  delete payload.endTime
  delete payload.createTime
  delete payload.updateTime
  delete payload.auditorName

  submitting.value = true
  try {
    if (form.id !== undefined && form.id !== null) {
      await updateSysRefundApi(payload)
      ElMessage.success('修改成功')
    } else {
      await addSysRefundApi(payload)
      ElMessage.success('新增成功')
    }
    open.value = false
    getList()
  } catch (error) {
  } finally {
    submitting.value = false
  }
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  if (!selectedIds.value.length) return
  ElMessageBox.confirm(`是否确认删除选中的 ${selectedIds.value.length} 条退款单?`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteSysRefundApi(selectedIds.value)
      ElMessage.success('删除成功')
      selectedIds.value = []
      getList()
    } catch (error) {
    }
  }).catch(() => { })
}

/** 删除按钮操作 */
const handleDelete = (row: any) => {
  ElMessageBox.confirm(`是否确认删除退款单【${row.refundNo || row.id}】?`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteSysRefundApi(row.id)
      ElMessage.success('删除成功')
      getList()
    } catch (error) {
    }
  }).catch(() => { })
}

// 分页大小改变
const handleSizeChange = (val: number) => {
  queryParams.pageSize = val
  getList()
}

// 页码改变
const handleCurrentChange = (val: number) => {
  queryParams.pageNum = val
  getList()
}

onMounted(() => {
  getList()
})
</script>
