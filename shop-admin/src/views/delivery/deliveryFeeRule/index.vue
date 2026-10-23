<template>
  <div class="app-container">
    <!--
      配送费规则：平台级的计价配置（不分门店），写法照「物流公司」那一页抄。
      这里配的数只管一件事 —— 买家下单那一刻，服务端挑"启用中优先级最高"的那一条
      把配送费算出来。所以这一页上没有"这一单收了多少"那种数，那在配送单的详情里看。
    -->
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="请输入规则名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="适用类型" prop="deliveryType">
          <el-select v-model="queryParams.deliveryType" placeholder="全部" clearable>
            <el-option v-for="item in dict.options('delivery_type')" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options('delivery_fee_rule_status')" :key="item.value"
              :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item class="search-actions">
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-card class="box-card">
      <!-- 操作工具栏 -->
      <template #header>
        <ButtonGroup>
          <el-button v-permission="['shop:deliveryFeeRule:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['shop:deliveryFeeRule:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="规则名称" prop="ruleName" align="center" min-width="150" />
        <el-table-column label="适用类型" align="center" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag('delivery_type', row.deliveryType)">
              {{ dict.label('delivery_type', row.deliveryType) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 下面四格和新增弹窗里那四个分组一一对应，看列表不用点开修改 -->
        <el-table-column label="基础计费" align="center" min-width="220">
          <template #default="{ row }">
            <span>{{ baseText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="重量计费" align="center" min-width="190">
          <template #default="{ row }">
            <span>{{ weightText(row) }}</span>
          </template>
        </el-table-column>
        <!-- 时段那几档并成一列，只画出真的加了钱的那几项（同「物流公司」的「支持服务」那一列） -->
        <el-table-column label="时段加价" align="center" min-width="200">
          <template #default="{ row }">
            <el-tag v-for="item in surchargeTags(row)" :key="item" size="small" class="rule-tag">{{ item }}</el-tag>
            <span v-if="surchargeTags(row).length === 0">-</span>
          </template>
        </el-table-column>
        <el-table-column label="减免" align="center" min-width="160">
          <template #default="{ row }">
            <el-tag v-for="item in freeTags(row)" :key="item" size="small" type="success" class="rule-tag">{{ item
              }}</el-tag>
            <span v-if="freeTags(row).length === 0">-</span>
          </template>
        </el-table-column>
        <el-table-column label="优先级" prop="priority" align="center" width="90" />
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag('delivery_fee_rule_status', row.status)">
              {{ dict.label('delivery_fee_rule_status', row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" align="center" width="170">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.createTime, 'YYYY-MM-DD') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="150" fixed="right">
          <template #default="scope">
            <el-button v-permission="['shop:deliveryFeeRule:update']" type="primary" link icon="Edit"
              @click="handleUpdate(scope.row)">修改</el-button>
            <el-button v-permission="['shop:deliveryFeeRule:delete']" type="danger" link icon="Delete"
              @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页工具栏 -->
      <div class="pagination-container">
        <el-pagination background v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 30, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>

      <!-- 添加或修改对话框 -->
      <el-dialog v-model="open" :title="title" width="720px" append-to-body destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
          <el-divider content-position="left">基本信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="规则名称" prop="ruleName">
                <el-input v-model="form.ruleName" placeholder="如 3公里内5元" maxlength="100" show-word-limit />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="适用类型" prop="deliveryType">
                <el-select v-model="form.deliveryType" placeholder="请选择" style="width: 100%">
                  <el-option v-for="item in dict.options('delivery_type')" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="优先级" prop="priority">
                <el-input-number v-model="form.priority" :min="0" :step="1" :precision="0" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态" prop="status">
                <el-radio-group v-model="form.status">
                  <el-radio v-for="item in dict.options('delivery_fee_rule_status')" :key="item.value"
                    :value="item.value">
                    {{ item.label }}
                  </el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">基础计费</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="起步配送费" prop="baseFee">
                <el-input-number v-model="form.baseFee" :min="0" :precision="2" placeholder="单位：元"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="起步含里程" prop="baseDistance">
                <el-input-number v-model="form.baseDistance" :min="0" :precision="2" placeholder="单位：km"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="超出每公里" prop="perKmFee">
                <el-input-number v-model="form.perKmFee" :min="0" :precision="2" placeholder="单位：元/km"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">重量计费</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="免费重量" prop="weightFree">
                <el-input-number v-model="form.weightFree" :min="0" :precision="2" placeholder="单位：kg"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="超出每公斤" prop="perKgFee">
                <el-input-number v-model="form.perKgFee" :min="0" :precision="2" placeholder="单位：元/kg"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">时段加价</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="高峰加价" prop="peakFee">
                <el-input-number v-model="form.peakFee" :min="0" :precision="2" placeholder="单位：元"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <!-- 两端都用 el-time-picker：库里的列是 TIME，收发的是 HH:mm:ss；
                   不像门店那个营业时间（el-time-select 整半点）—— 高峰边界经常是 11:30:00 之外自己定的分钟 -->
              <el-form-item label="高峰时段" prop="peakStart">
                <div class="pair-field">
                  <el-time-picker v-model="form.peakStart" value-format="HH:mm:ss" format="HH:mm" placeholder="开始"
                    clearable />
                  <span class="pair-split">-</span>
                  <el-time-picker v-model="form.peakEnd" value-format="HH:mm:ss" format="HH:mm" placeholder="结束"
                    clearable />
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="夜间加价" prop="nightFee">
                <el-input-number v-model="form.nightFee" :min="0" :precision="2" placeholder="单位：元"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="夜间时段" prop="nightStart">
                <div class="pair-field">
                  <el-time-picker v-model="form.nightStart" value-format="HH:mm:ss" format="HH:mm" placeholder="开始"
                    clearable />
                  <span class="pair-split">-</span>
                  <el-time-picker v-model="form.nightEnd" value-format="HH:mm:ss" format="HH:mm" placeholder="结束"
                    clearable />
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="天气加价" prop="weatherFee">
                <el-input-number v-model="form.weatherFee" :min="0" :precision="2" placeholder="单位：元"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">减免</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="满额免配送费" prop="freeDeliveryAmount">
                <el-input-number v-model="form.freeDeliveryAmount" :min="0" :precision="2" placeholder="满多少元，留空不启用"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="近距离免配送费" prop="freeDeliveryDistance">
                <el-input-number v-model="form.freeDeliveryDistance" :min="0" :precision="2"
                  placeholder="多少km以内，留空不启用" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>

          <div class="rule-tip">
            下单建配送单时取的是<strong>启用中优先级最高</strong>的那一条（同优先级取先建的那条），一条规则就够了：
            配送费 = 起步费 + 超里程费 + 超重费 + 命中时段的加价，命中任一减免就是 0 元。
            距离按「发货点 → 收件点」的直线算、重量取配送单上那一格、满额看来源订单的实付金额；
            天气加价这一档本期不参与计算（系统里还没有天气数据来源）。没配任何启用规则时配送费就是 0。
          </div>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="cancel">取 消</el-button>
            <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
          </div>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listShopDeliveryFeeRuleApi,
  detailShopDeliveryFeeRuleApi,
  addShopDeliveryFeeRuleApi,
  updateShopDeliveryFeeRuleApi,
  deleteShopDeliveryFeeRuleApi
} from '@/api/delivery/deliveryFeeRule'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import validate from '@/utils/validate'
import { useDict } from '@/utils/dictCache'

/** 适用类型复用配送单那个字典；状态是这一页自己的 delivery_fee_rule_status */
const dict = useDict('delivery_type', 'delivery_fee_rule_status')

const loading = ref(true)
const submitLoading = ref(false)
const selectedIds = ref<any[]>([])
const total = ref(0)
const dataList = ref<any[]>([])
const title = ref('')
const open = ref(false)

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// 查询参数
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  deliveryType: undefined,
  status: undefined
})

// 表单参数
const form = reactive<any>({})

const rules = reactive<FormRules>({
  ruleName: [{ required: true, message: '规则名称不能为空', trigger: 'blur' }]
})

/** 金额/数量的显示口径同出库单（后端回来的 decimal 是字符串，统一走 Number） */
const money = (value: any) => `￥${Number(value || 0).toFixed(2)}`

/**
 * "21:00:00" 念成 "21:00"：列表那两格放不下秒，而且库里的 TIME 本来就没秒的意义。
 * 空的一侧留空串，调用方自己判。
 */
const hhmm = (value: any) => (value ? String(value).slice(0, 5) : '')

/** 列表那一格：起步多少钱、含多少公里、超出每公里多少 */
const baseText = (row: any) => {
  const perKm = Number(row.perKmFee || 0)
  const include = `含 ${Number(row.baseDistance || 0)} km`
  return perKm > 0
    ? `${money(row.baseFee)}（${include}），超出 ${money(row.perKmFee)}/km`
    : `${money(row.baseFee)}（${include}）`
}

const weightText = (row: any) => {
  const perKg = Number(row.perKgFee || 0)
  if (perKg <= 0) return `${Number(row.weightFree || 0)} kg 内不计重量费`
  return `${Number(row.weightFree || 0)} kg 内不计，超出 ${money(row.perKgFee)}/kg`
}

/** 时段那几档只画真的加了钱的（peakFee 为 0 或没配时段的都不占格子） */
const surchargeTags = (row: any) => {
  const tags: string[] = []
  // 两端成对才念得出来（服务层也是这么拦的），只有一端的话这一档其实不生效，就不显示区间
  const range = (start: any, end: any) => (start && end ? ` ${hhmm(start)}~${hhmm(end)}` : '')
  if (Number(row.peakFee || 0) > 0) {
    tags.push(`高峰 +${money(row.peakFee)}${range(row.peakStart, row.peakEnd)}`)
  }
  if (Number(row.nightFee || 0) > 0) {
    // 夜间经常跨零点（21:00~06:00），这个区间就是字面意思，后端按"起点之后或终点之前"判
    tags.push(`夜间 +${money(row.nightFee)}${range(row.nightStart, row.nightEnd)}`)
  }
  if (Number(row.weatherFee || 0) > 0) {
    tags.push(`天气 +${money(row.weatherFee)}（本期不计）`)
  }
  return tags
}

const freeTags = (row: any) => {
  const tags: string[] = []
  if (row.freeDeliveryAmount != null && row.freeDeliveryAmount !== '') {
    tags.push(`满 ${money(row.freeDeliveryAmount)} 免`)
  }
  if (row.freeDeliveryDistance != null && row.freeDeliveryDistance !== '') {
    tags.push(`${Number(row.freeDeliveryDistance)} km 内免`)
  }
  return tags
}

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listShopDeliveryFeeRuleApi(queryParams)
    dataList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
  } finally {
    loading.value = false
  }
}

/** 表单重置 */
const reset = () => {
  Object.assign(form, {
    id: undefined,
    ruleName: undefined,
    deliveryType: dict.defaultValue('delivery_type', 1),
    baseFee: 0,
    baseDistance: undefined,
    perKmFee: undefined,
    weightFree: undefined,
    perKgFee: undefined,
    peakFee: undefined,
    peakStart: undefined,
    peakEnd: undefined,
    nightFee: undefined,
    nightStart: undefined,
    nightEnd: undefined,
    weatherFee: undefined,
    freeDeliveryAmount: undefined,
    freeDeliveryDistance: undefined,
    priority: 0,
    status: 1
  })
  formRef.value?.resetFields()
}

/** 取消按钮 */
const cancel = () => {
  open.value = false
  reset()
}

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.keyword = undefined
  queryParams.deliveryType = undefined
  queryParams.status = undefined
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
}

/** 新增按钮操作 */
const handleAdd = () => {
  reset()
  open.value = true
  title.value = '添加配送费规则'
}

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailShopDeliveryFeeRuleApi(row.id)
    Object.assign(form, data)
    open.value = true
    title.value = '修改配送费规则'
  } catch (error) {
  }
}

/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateShopDeliveryFeeRuleApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopDeliveryFeeRuleApi(form)
        ElMessage.success('新增成功')
      }
      open.value = false
      getList()
    } catch (error) {
    } finally {
      submitLoading.value = false
    }
  })
}

/** 删除按钮操作 */
const handleDelete = (row: any) => {
  ElMessageBox.confirm(
    `确认删除配送费规则「${row.ruleName}」吗？已经生成出来的配送单不受影响（那些单上只存算好的那个价）。`,
    '提示',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    await deleteShopDeliveryFeeRuleApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 条配送费规则吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopDeliveryFeeRuleApi(selectedIds.value)
    ElMessage.success('删除成功')
    selectedIds.value = []
    getList()
  }).catch(() => { })
}

/** 分页 */
const handleSizeChange = () => {
  queryParams.pageNum = 1
  getList()
}
const handleCurrentChange = () => {
  getList()
}

onMounted(() => {
  getList()
})
</script>

<style scoped lang="scss">
/* 参照部门管理：筛选项等分整行，控件宽度统一由 CSS 控制 */
.search-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;

  :deep(.el-form-item) {
    flex: 1 1 0;
    min-width: 180px;
    margin-right: 16px;

    &:last-child {
      flex: 0 0 auto;
      min-width: 0;
      margin-right: 0;
    }
  }

  :deep(.el-form-item .el-form-item__content) {
    min-width: 0;
  }

  /* global.scss 里对 .el-input/.el-select 写死了 240px，特异性更高，只能用 !important 覆盖 */
  :deep(.el-form-item .el-input),
  :deep(.el-form-item .el-select) {
    width: 100% !important;
  }
}

/* 按钮那一格独占第二行（flex-basis 100% 就会换行）。
   上面 :last-child 把它压成了 0 0 auto，所以这里选择器多带一个 .el-form-item，特异性够、又写在后面才顶得住 */
.search-form :deep(.el-form-item.search-actions) {
  flex: 1 1 100%;
  min-width: 0;
  margin-right: 0;
}

/* 一对时刻（高峰/夜间）：同门店那一页的营业时间。el-time-picker 的根节点带 .el-date-editor，
   它在 EP 里有个写死的宽度，要一起压进这一格才不顶出去 */
.pair-field {
  display: flex;
  gap: 8px;
  width: 100%;

  :deep(.el-date-editor) {
    flex: 1 1 0;
    width: 100%;
    min-width: 0;
  }
}

.pair-split {
  line-height: 32px;
  color: var(--el-text-color-secondary);
}

.rule-tag {
  margin-right: 4px;
}

.rule-tip {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}
</style>
