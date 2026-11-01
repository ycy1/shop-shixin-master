<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="名称/编号" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="请输入票种名称/编号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="所属园区" prop="parkId">
          <el-select v-model="queryParams.parkId" placeholder="全部" clearable>
            <el-option v-for="item in parkOptions" :key="item.id" :label="item.parkName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="票种类型" prop="ticketType">
          <el-select v-model="queryParams.ticketType" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="0" />
          </el-select>
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
        <ButtonGroup>
          <el-button v-permission="['shop:parkTicket:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['shop:parkTicket:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格：一行是一个票种（一种卖法）；发到用户手上那一张张的票在「订单管理 → 用户票种」那页 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="票种编号" prop="ticketNo" align="center" min-width="160" />
        <el-table-column label="票种名称" prop="ticketName" align="center" min-width="150" />
        <el-table-column label="类型" align="center" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_TYPE, row.ticketType)">
              {{ dict.label(DICT_TYPE, row.ticketType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="所属园区" align="center" min-width="150">
          <template #default="{ row }">
            <span>{{ row.parkName || (row.parkId ? `已删除园区 #${row.parkId}` : '-') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="售价" align="right" width="110">
          <template #default="{ row }">
            <span class="pay-amount">{{ priceText(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="入园时段" align="center" width="150">
          <template #default="{ row }">
            <!-- 这一格是多选（库里 "1,2" 这种），一档一个 tag；只有一档「全天」时就是一个 tag -->
            <template v-if="slotsOf(row.timeSlots).length">
              <el-tag v-for="slot in slotsOf(row.timeSlots)" :key="slot" size="small" class="slot-tag"
                :type="dict.tag(DICT_SLOT, slot)">
                {{ dict.label(DICT_SLOT, slot) }}
              </el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <!-- 这两格留空就是"不限"，列表上别显示成 0（0 张、0 天是另一回事，库里存不出这个值） -->
        <el-table-column label="有效天数" align="center" width="100">
          <template #default="{ row }">{{ row.validDays || '不限' }}</template>
        </el-table-column>
        <el-table-column label="每单限购" align="center" width="100">
          <template #default="{ row }">{{ row.maxPerOrder || '不限' }}</template>
        </el-table-column>
        <!-- 已出票数由服务层按当页票种 id 一次分组数回来（删除前先看这一格，出过票的删不掉） -->
        <el-table-column label="已出票" align="center" width="90">
          <template #default="{ row }">{{ row.soldCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="180" fixed="right">
          <template #default="scope">
            <el-button v-permission="['shop:parkTicket:update']" type="primary" link icon="Edit"
              @click="handleUpdate(scope.row)">修改</el-button>
            <el-button v-permission="['shop:parkTicket:delete']" type="danger" link icon="Delete"
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
          <el-divider content-position="left">票面信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="票种编号" prop="ticketNo">
                <el-input v-model="form.ticketNo" placeholder="保存后由系统生成" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="票种名称" prop="ticketName">
                <el-input v-model="form.ticketName" placeholder="请输入票种名称" maxlength="100" show-word-limit />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="所属园区" prop="parkId">
                <!-- 必填：票种一定属于某一家园区（这一列 NOT NULL，没有"全平台通用票"这一档）；
                     options 只给营业中的园区，老票种绑着已停业园区时拿名字兜一下别让这一格空着 -->
                <el-select v-model="form.parkId" placeholder="请选择所属园区" filterable style="width: 100%">
                  <el-option v-for="item in parkSelectOptions" :key="item.id" :label="item.parkName" :value="item.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="票种类型" prop="ticketType">
                <el-select v-model="form.ticketType" placeholder="请选择" style="width: 100%">
                  <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="售价" prop="price">
                <el-input-number v-model="form.price" :min="0" :precision="2" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="入园时段" prop="timeSlots">
                <!-- 多选：库里那一列是 varchar，存逗号分隔的档位（"1,2"），所以这里用 multiple，
                     提交时 join(',')、回显时 split —— 和门店表单那格地区级联是同一套中转写法（不直接编辑 form） -->
                <el-select v-model="slotArr" multiple placeholder="可入园的时段（不选=全天）" style="width: 100%">
                  <el-option v-for="item in dict.options(DICT_SLOT)" :key="item.value" :label="item.label"
                    :value="item.value" :disabled="isSlotDisabled(item.value)" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="有效天数" prop="validDays">
                <el-input-number v-model="form.validDays" :min="1" :controls="false" placeholder="留空=不限"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="每单限购" prop="maxPerOrder">
                <el-input-number v-model="form.maxPerOrder" :min="1" :controls="false" placeholder="留空=不限"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态" prop="status">
                <el-radio-group v-model="form.status">
                  <el-radio :value="1">上架</el-radio>
                  <el-radio :value="0">下架</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="排序" prop="sort">
                <el-input-number v-model="form.sort" :min="0" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="票种说明" prop="description">
                <el-input v-model="form.description" type="textarea" :rows="3" placeholder="选填，写给买票的人看"
                  maxlength="2000" show-word-limit />
              </el-form-item>
            </el-col>
          </el-row>
          <div class="tip">
            出票那一刻会把名称、类型、票价按这一行定格成快照，之后这里改了名改了价不会回头刷已经发到用户手上的票。
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
  listShopTicketApi,
  detailShopTicketApi,
  addShopTicketApi,
  updateShopTicketApi,
  deleteShopTicketApi
} from '@/api/trendy/parkTicket'
import { optionsShopParkApi } from '@/api/trendy/park'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import { useDict } from '@/utils/dictCache'

const DICT_TYPE = 'ticket_type'
const DICT_SLOT = 'ticket_time_slot'

const dict = useDict(DICT_TYPE, DICT_SLOT)

/** 「全天」那一档（字典 ticket_time_slot 的 0）：它和上午/下午/夜场是包含关系，不并列 */
const SLOT_ALL_DAY = 0

// 时段是多选，库里存的是 "1,2" 这种逗号分隔文本；和门店那页的地区级联同一套写法 ——
// 不直接编辑 form，靠这个数组中转，split / join 在 reset、回显、提交三处做
const slotArr = ref<number[]>([])

/** 拆成一串数字档位（列表那一格一档一个 tag 用）；库里存的是什么就按什么拆 */
const slotsOf = (slots: any) => String(slots ?? '').split(',').filter(Boolean).map(Number)

/**
 * 已经勾了「全天」就把另外三档置灰、已经勾了具体时段就把「全天」置灰：
 * 服务端那条判据也在（ParkTicketSlotUtils），这里是别让人点了才发现互斥
 */
const isSlotDisabled = (value: any) => {
  const picked = slotArr.value.map(Number)
  if (picked.length === 0) return false
  if (picked.includes(SLOT_ALL_DAY)) return Number(value) !== SLOT_ALL_DAY
  return Number(value) === SLOT_ALL_DAY
}

/** 园区候选：只给营业中的（停业的园区不该再挂新票种，口径同商品表单那份门店候选） */
const parkOptions = ref<any[]>([])
const getParkOptions = async () => {
  try {
    const { data } = await optionsShopParkApi()
    parkOptions.value = data || []
  } catch (error) {
  }
}

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
  parkId: undefined,
  ticketType: undefined,
  status: undefined
})

// 表单参数
const form = reactive<any>({})

const rules = reactive<FormRules>({
  ticketName: [{ required: true, message: '票种名称不能为空', trigger: 'blur' }],
  parkId: [{ required: true, message: '请选择所属园区', trigger: 'change' }],
  price: [{ required: true, message: '售价不能为空', trigger: 'blur' }]
})

/** 金额的显示口径，和各列表页一致（后端返回的 decimal 是字符串） */
const priceText = (price: any) => (price == null || price === '' ? '-' : `￥${Number(price).toFixed(2)}`)

/**
 * 园区下拉实际用的候选：options 只列营业中的，所以绑着停业/已删园区的老票种会选不出来。
 * 列表行上有 parkName（服务层回填的那一格），拿它顶一下别让这一格空着（同门店的「已停用品牌 #id」那一档）
 */
const parkSelectOptions = computed(() => {
  const id = form.parkId
  if (!id) return parkOptions.value
  if (parkOptions.value.some((item: any) => String(item.id) === String(id))) return parkOptions.value
  return [{ id, parkName: form.parkName || `已停业园区 #${id}` }, ...parkOptions.value]
})

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listShopTicketApi(queryParams)
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
    ticketNo: undefined,
    ticketName: undefined,
    parkId: undefined,
    parkName: undefined,
    ticketType: Number(dict.defaultValue(DICT_TYPE, 1)),
    price: undefined,
    timeSlots: undefined,
    validDays: undefined,
    maxPerOrder: undefined,
    description: undefined,
    status: 1,
    sort: 0
  })
  formRef.value?.resetFields()
  // 时段那一格靠 slotArr 中转（多选 ↔ 逗号文本），reset 要一起清回默认的全天
  slotArr.value = [SLOT_ALL_DAY]
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
  queryParams.parkId = undefined
  queryParams.ticketType = undefined
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
  title.value = '添加票种'
}

/** 修改按钮操作：每次点开重新捞，列表那行的价格/名称可能已经被别人改过 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailShopTicketApi(row.id)
    Object.assign(form, data)
    // 库里那一列是 "1,2" 这种，split 回多选下拉要的数组
    slotArr.value = slotsOf(data.timeSlots)
    open.value = true
    title.value = '修改票种'
  } catch (error) {
  }
}

/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    // 多选的档位拼回逗号分隔文本（一个都没挑就当全天，和建表默认一致）；
    // "全天不与其他三档并列"由服务端 ParkTicketSlotUtils 判，这里已经把互斥的那几项置灰了
    form.timeSlots = slotArr.value.length ? slotArr.value.join(',') : String(SLOT_ALL_DAY)
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateShopTicketApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopTicketApi(form)
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
  ElMessageBox.confirm(`确认删除票种「${row.ticketName}」吗？已经出出去过票的删不掉。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopTicketApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个票种吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopTicketApi(selectedIds.value)
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
  getParkOptions()
})
</script>

<style scoped lang="scss">
/* 参照园区管理、门店管理：筛选项等分整行，控件宽度统一由 CSS 控制 */
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

/* 快照那条说明：字号和颜色都压到占位符一档，别抢表单焦点 */
.tip {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-placeholder);
}

/* 实付一档的金额用强调色，和左边的原价拉开（口径同订单那几页） */
.pay-amount {
  color: var(--el-color-primary);
}

/* 入园时段那一格是多选，一档一个 tag，挨着放别贴死 */
.slot-tag {
  margin: 0 2px;
}
</style>
