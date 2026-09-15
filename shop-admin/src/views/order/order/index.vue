<template>
  <div class="app-container">

    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form :model="queryParams" ref="queryFormRef" inline>
        <el-form-item label="订单编号" prop="orderNo">
          <el-input v-model="queryParams.orderNo" placeholder="请输入订单编号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="用户" prop="userId">
          <template #label>
            <span>用&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;户</span>
          </template>
          <template #default="scope">
            <UserSelect v-model="queryParams.userId" style="width: 240px" />
          </template>
        </el-form-item>
        <el-form-item label="手机号" prop="mobile">
          <template #label>
            <span>手机号&nbsp;&nbsp;&nbsp;</span>
          </template>
          <el-input v-model="queryParams.mobile" placeholder="请输入手机号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="订单类型" prop="orderType">
          <el-select v-model="queryParams.orderType" placeholder="全部" clearable @change="handleQuery">
            <el-option v-for="item in dict.options('order_type')" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable @change="handleQuery">
            <el-option v-for="item in dict.options('order_status')" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="支付方式" prop="payType">
          <el-select v-model="queryParams.payType" placeholder="全部" clearable @change="handleQuery">
            <el-option v-for="item in dict.options('pay_type')" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="支付时间">
          <el-date-picker v-model="payTimeRange" type="datetimerange" range-separator="至" start-placeholder="开始时间"
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
        <el-table-column label="订单编号" align="center" prop="orderNo" width="200" show-overflow-tooltip />
        <el-table-column label="用户" align="center" prop="userName" width="130" show-overflow-tooltip />
        <el-table-column label="手机号" align="center" prop="mobile" width="130" />
        <el-table-column label="订单类型" align="center" width="100">
          <template #default="scope">
            <el-tag size="small" type="primary">{{ dict.label('order_type', scope.row.orderType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="业务类型" align="center" width="100">
          <template #default="scope">{{ dict.label('order_biz_type', scope.row.bizType) }}</template>
        </el-table-column>
        <el-table-column label="订单状态" align="center" width="100">
          <template #default="scope">
            <el-tag size="small" :type="dict.tag('order_status', scope.row.status)">{{ dict.label('order_status', scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付方式" align="center" width="100">
          <template #default="scope">{{ dict.label('pay_type', scope.row.payType) }}</template>
        </el-table-column>
        <el-table-column label="订单金额" align="right" width="120">
          <template #default="scope">{{ validate.formatAmount(scope.row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="优惠金额" align="right" width="120">
          <template #default="scope">{{ validate.formatAmount(scope.row.discountAmount) }}</template>
        </el-table-column>
        <el-table-column label="实付金额" align="right" width="120">
          <template #default="scope">
            <span style="font-weight: 600; color: var(--el-color-danger)">{{ validate.formatAmount(scope.row.payAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="支付时间" align="center" prop="payTime" width="170">
          <template #default="scope">{{ validate.formatTime(scope.row.payTime, 'YYYY-MM-DD HH') }}</template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="170">
          <template #default="scope">{{ validate.formatTime(scope.row.createTime, 'YYYY-MM-DD HH') }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="150" fixed="right">
          <template #default="scope">
            <TableMoreActions :actions="[
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

      <!-- 添加或修改对话框 -->
      <el-dialog v-model="open" :title="title" width="860px" append-to-body>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
          <el-divider content-position="left">订单信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="订单编号" prop="orderNo">
                <el-input v-model="form.orderNo" placeholder="保存后由系统生成" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="订单类型" prop="orderType">
                <el-select v-model="form.orderType" placeholder="请选择订单类型" style="width: 100%">
                  <el-option v-for="item in dict.options('order_type')" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="业务类型" prop="bizType">
                <el-select v-model="form.bizType" placeholder="请选择业务类型" clearable style="width: 100%">
                  <el-option v-for="item in dict.options('order_biz_type')" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="业务ID" prop="bizId">
                <el-input v-model="form.bizId" placeholder="商品/课程等业务主键" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">用户信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="下单用户" prop="userId">
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
              <el-form-item label="订单总金额" prop="totalAmount">
                <el-input-number v-model="form.totalAmount" :min="0" :precision="2" :step="1"
                  controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="优惠金额" prop="discountAmount">
                <el-input-number v-model="form.discountAmount" :min="0" :precision="2" :step="1"
                  controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="实付金额" prop="payAmount">
                <el-input-number v-model="form.payAmount" :min="0" :precision="2" :step="1" controls-position="right"
                  style="width: 100%" />
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
          </el-row>

          <el-divider content-position="left">支付信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="订单状态" prop="status">
                <el-select v-model="form.status" placeholder="请选择订单状态" style="width: 100%">
                  <el-option v-for="item in dict.options('order_status')" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="支付方式" prop="payType">
                <el-select v-model="form.payType" placeholder="请选择支付方式" clearable style="width: 100%">
                  <el-option v-for="item in dict.options('pay_type')" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="支付时间" prop="payTime">
                <el-date-picker v-model="form.payTime" type="datetime" placeholder="请选择支付时间"
                  value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="过期时间" prop="expireTime">
                <el-date-picker v-model="form.expireTime" type="datetime" placeholder="超时未支付自动关闭"
                  value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="订单备注" prop="remark">
            <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="200" show-word-limit
              placeholder="请输入订单备注" />
          </el-form-item>
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
  listSysOrderApi,
  detailSysOrderApi,
  deleteSysOrderApi,
  addSysOrderApi,
  updateSysOrderApi
} from '@/api/order/sysorder'
import { useDict } from '@/utils/dictCache'
import UserSelect from '@/components/UserSelect/index.vue'
import TableMoreActions from '@/components/TableMoreActions/index.vue'
import validate from '@/utils/validate'

/** 订单相关字典：订单类型 / 业务类型 / 订单状态 / 支付方式 */
const dict = useDict('order_type', 'order_biz_type', 'order_status', 'pay_type')

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// 遮罩层
const loading = ref(false)
// 提交中
const submitting = ref(false)
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
// 支付时间范围（提交前拆成 beginTime / endTime）
const payTimeRange = ref<[string, string] | null>()

// 查询参数
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  orderNo: undefined as string | undefined,
  userId: undefined as number | undefined,
  mobile: undefined as string | undefined,
  orderType: undefined as number | undefined,
  status: undefined as number | undefined,
  payType: undefined as number | undefined,
  beginTime: undefined as string | undefined,
  endTime: undefined as string | undefined
})

// 表单默认值（新增与重置共用，避免残留上一条记录的值）
const defaultForm = () => ({
  id: undefined as number | undefined,
  orderNo: '',
  orderType: dict.defaultValue('order_type'),
  userId: undefined as number | undefined,
  userName: '',
  mobile: '',
  bizId: '',
  bizType: undefined as number | undefined,
  totalAmount: 0,
  payAmount: 0,
  discountAmount: 0,
  currency: 'CNY',
  status: dict.defaultValue('order_status'),
  payType: undefined as number | undefined,
  payTime: null as string | null,
  expireTime: null as string | null,
  remark: ''
})

// 表单参数
const form = reactive<any>(defaultForm())

// 表单校验
const rules = {
  orderType: [{ required: true, message: '请选择订单类型', trigger: 'change' }],
  userId: [{ required: true, message: '请选择下单用户', trigger: 'change' }],
  totalAmount: [{ required: true, message: '请输入订单总金额', trigger: 'blur' }],
  status: [{ required: true, message: '请选择订单状态', trigger: 'change' }]
}

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listSysOrderApi(queryParams)
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
  queryParams.beginTime = payTimeRange.value?.[0]
  queryParams.endTime = payTimeRange.value?.[1]
  getList()
}

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.userId = undefined
  queryParams.mobile = undefined
  payTimeRange.value = null
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: any[]) => {
  selectedIds.value = selection.map(item => item.id)
}

/** 行操作 */
const handleActionCommand = (action: any) => {
  const { type, row } = action.command
  if (type === 'edit') {
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

/** 新增按钮操作 */
const handleAdd = () => {
  reset()
  title.value = '添加订单'
  open.value = true
}

/** 修改按钮操作 */
const handleUpdate = (row: any) => {
  reset()
  detailSysOrderApi(row.id).then(({ data }) => {
    Object.assign(form, data)
    title.value = '修改订单'
    open.value = true
  })
}

/** 提交按钮 */
const submitForm = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  // 剔除查询用的临时字段和时间戳，时间戳交给后端自动填充
  const payload: any = { ...form }
  delete payload.ids
  delete payload.beginTime
  delete payload.endTime
  delete payload.createTime
  delete payload.updateTime

  submitting.value = true
  try {
    if (form.id !== undefined && form.id !== null) {
      await updateSysOrderApi(payload)
      ElMessage.success('修改成功')
    } else {
      await addSysOrderApi(payload)
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
  ElMessageBox.confirm(`是否确认删除选中的 ${selectedIds.value.length} 条订单?`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteSysOrderApi(selectedIds.value)
      ElMessage.success('删除成功')
      selectedIds.value = []
      getList()
    } catch (error) {
    }
  }).catch(() => { })
}

/** 删除按钮操作 */
const handleDelete = (row: any) => {
  ElMessageBox.confirm(`是否确认删除订单【${row.orderNo || row.id}】?`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteSysOrderApi(row.id)
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
