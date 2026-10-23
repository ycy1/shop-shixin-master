<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="请输入商品名称/编码/规格/仓库名称" clearable
            @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="queryParams.warehouseId" placeholder="全部" clearable filterable>
            <el-option v-for="item in warehouseOptions" :key="item.id" :label="item.warehouseName" :value="item.id">
              <div class="product-option">
                <span class="product-option__name">{{ item.warehouseName }}</span>
                <span class="product-option__code">{{ item.warehouseCode }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="商品" prop="productId">
          <shop-product-select v-model="queryParams.productId" :label="queryProductName" placeholder="全部商品"
            @select="handleProductSelect" @clear="handleProductClear" />
        </el-form-item>
        <el-form-item class="search-actions">
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>

        <!-- 库存行是按 (规格, 仓库) 唯一的一张账，一次入库就是一次增量，所以这里没有新增/删除按钮：
             数量列只能由入库动作改，勾上「只看低于安全库存」当补货清单用 -->
        <div class="search-row">
          <el-form-item label-width="0">
            <el-checkbox v-model="queryParams.lowStockOnly" @change="handleQuery">只看低于安全库存</el-checkbox>
          </el-form-item>
        </div>
      </el-form>
    </div>

    <el-card class="box-card">
      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="商品名称" align="center" min-width="160">
          <template #default="{ row }">
            <span>{{ row.productName || `已删除规格 #${row.skuId}` }}</span>
          </template>
        </el-table-column>
        <el-table-column label="规格" align="center" min-width="140">
          <template #default="{ row }">
            <span>{{ row.specText || '默认规格' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="商品编码" align="center" min-width="140">
          <template #default="{ row }">
            <span>{{ row.productCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="仓库" align="center" min-width="140">
          <template #default="{ row }">
            <span>{{ row.warehouseName || `已删除仓库 #${row.warehouseId}` }}</span>
          </template>
        </el-table-column>
        <el-table-column label="总库存" prop="stock" align="center" width="90" />
        <!-- <el-table-column label="锁定" prop="lockedStock" align="center" width="80" /> -->
        <el-table-column label="可用" align="center" width="90">
          <template #default="{ row }">
            <span :class="{ 'stock-warning': isLowStock(row) }">{{ row.availableStock }}</span>
          </template>
        </el-table-column>
        <!-- <el-table-column label="在途" prop="inTransitStock" align="center" width="80" /> -->
        <el-table-column label="成本价" align="center" width="110">
          <template #default="{ row }">
            <span>{{ priceText(row.costPrice) }}</span>
          </template>
        </el-table-column>
        <!-- <el-table-column label="安全库存" prop="safetyStock" align="center" width="100" />
        <el-table-column label="最大库存" align="center" width="100">
          <template #default="{ row }">
            <span>{{ row.maxStock == null ? '不限' : row.maxStock }}</span>
          </template>
        </el-table-column>
        <el-table-column label="预警" align="center" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.stockWarnEnable === 1 ? 'success' : 'info'">
              {{ row.stockWarnEnable === 1 ? '开启' : '关闭' }}
            </el-tag>
          </template>
        </el-table-column> -->
        <el-table-column label="最后操作人" align="center" min-width="110">
          <template #default="{ row }">
            <span>{{ row.userName || '未记录' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" align="center" width="170">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.updateTime, 'YYYY-MM-DD') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="250" fixed="right">
          <template #default="scope">
            <el-button v-permission="['erp:inventory:detail']" type="primary" link icon="View"
              @click="handleDetail(scope.row)">详情</el-button>
            <el-button v-permission="['erp:inventory:update']" type="primary" link icon="Edit"
              @click="handleUpdate(scope.row)">修改</el-button>
            <!-- 流水：这一行账的进出明细，只能由单据动作写出来，所以只有查 -->
            <el-button v-permission="['erp:inventory:log']" type="primary" link icon="Document"
              @click="handleLog(scope.row)">库存日志</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页工具栏 -->
      <div class="pagination-container">
        <el-pagination background v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 30, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>

      <!-- 库存详情：全站唯一的只读口径，成本价怎么来的、三个数量列各是什么都在这里说清楚 -->
      <el-dialog v-model="detailOpen" title="库存详情" width="760px" append-to-body destroy-on-close>
        <el-descriptions title="库存数量" :column="2" border v-loading="detailLoading">
          <el-descriptions-item label="商品名称">{{ detail.productName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="规格">{{ detail.specText || '默认规格' }}</el-descriptions-item>
          <el-descriptions-item label="商品编码">{{ detail.productCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="仓库">{{ detail.warehouseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="成本价">{{ priceText(detail.costPrice) }}</el-descriptions-item>
          <el-descriptions-item label="总库存">{{ detail.stock }}</el-descriptions-item>
          <el-descriptions-item label="锁定库存">{{ detail.lockedStock }}</el-descriptions-item>
          <el-descriptions-item label="可用库存">{{ detail.availableStock }}</el-descriptions-item>
          <el-descriptions-item label="在途库存">{{ detail.inTransitStock }}</el-descriptions-item>
        </el-descriptions>
        <el-descriptions title="预警参数" :column="2" border class="detail-block">
          <el-descriptions-item label="安全库存">{{ detail.safetyStock }}</el-descriptions-item>
          <el-descriptions-item label="最大库存">{{ detail.maxStock == null ? '不限' : detail.maxStock }}</el-descriptions-item>
          <el-descriptions-item label="预警开关">{{ detail.stockWarnEnable === 1 ? '开启' : '关闭' }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag size="small" :type="isLowStock(detail) ? 'danger' : 'success'">
              {{ isLowStock(detail) ? '低于安全库存' : '正常' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-descriptions title="操作留痕" :column="2" border class="detail-block">
          <el-descriptions-item label="最后操作人">{{ detail.userName || '未记录' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">
            {{ validate.formatTime(detail.updateTime) || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="建账时间">
            {{ validate.formatTime(detail.createTime) || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="库存版本">{{ detail.version }}</el-descriptions-item>
        </el-descriptions>
        <template #footer>
          <el-button @click="detailOpen = false">关 闭</el-button>
        </template>
      </el-dialog>

      <!-- 添加或修改对话框：只有成本价和三个预警参数能改 -->
      <el-dialog v-model="open" :title="title" width="720px" append-to-body destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
          <el-divider content-position="left">库存数量（只读）</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="商品规格">
                <el-input :model-value="`${form.productName || '-'}（${form.specText || '默认规格'}）`" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="仓库">
                <el-input :model-value="form.warehouseName" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="总库存">
                <el-input :model-value="form.stock" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="锁定库存">
                <el-input :model-value="form.lockedStock" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="可用库存">
                <el-input :model-value="form.availableStock" disabled />
              </el-form-item>
            </el-col>
          </el-row>
          <div class="items-footer">
            <span class="items-tip">库存数量只能由「商品入库」的入库动作改，这里改不了</span>
          </div>

          <el-divider content-position="left">成本与预警</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="成本价" prop="costPrice">
                <!-- 入库时按加权平均自动算，清空表示还没有成本价（从没入过货或者被抹掉了） -->
                <el-input-number v-model="form.costPrice" :min="0" :precision="2" :step="1" controls-position="right"
                  placeholder="留空表示未定价" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="安全库存" prop="safetyStock">
                <el-input-number v-model="form.safetyStock" :min="0" :step="10" controls-position="right"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="最大库存" prop="maxStock">
                <el-input-number v-model="form.maxStock" :min="0" :step="100" controls-position="right"
                  placeholder="留空表示不限" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="预警开关" prop="stockWarnEnable">
                <el-switch v-model="form.stockWarnEnable" :active-value="1" :inactive-value="0" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="cancel">取 消</el-button>
            <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
          </div>
        </template>
      </el-dialog>

      <!-- 库存日志（流水）：这一行 (规格, 仓库) 账的进出明细，只读。
           一行 = 一次库存变动，变动前后的库存连起来就是这条账的来龙去脉，
           所以这里不放新增/修改/删除按钮 —— 手工插一条流水会让它对不上账本 -->
      <el-dialog v-model="logOpen" :title="logTitle" width="980px" append-to-body destroy-on-close>
        <el-table :data="logList" size="small" border v-loading="logLoading" show-overflow-tooltip
          empty-text="这条库存还没有流水">
          <el-table-column label="时间" align="center" width="160">
            <template #default="{ row }">
              <span>{{ validate.formatTime(row.createTime) || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="变动类型" align="center" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="dict.tag(CHANGE_TYPE_DICT, row.changeType)">
                {{ dict.label(CHANGE_TYPE_DICT, row.changeType) }}
              </el-tag>
            </template>
          </el-table-column>
          <!-- 带符号：入库 +N 绿色、出库 -N 红色（这一版只有入库） -->
          <el-table-column label="变动数量" align="center" width="100">
            <template #default="{ row }">
              <span :class="Number(row.changeQuantity) < 0 ? 'count-out' : 'count-in'">
                {{ quantityText(row.changeQuantity) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="变动前" prop="beforeStock" align="center" width="90" />
          <el-table-column label="变动后" prop="afterStock" align="center" width="90" />
          <el-table-column label="关联单号" align="center" min-width="160">
            <template #default="{ row }">
              <span>{{ row.bizNo || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作人" align="center" min-width="110">
            <template #default="{ row }">
              <span>{{ row.userName || '未记录' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="备注" align="center" min-width="120">
            <template #default="{ row }">
              <span>{{ row.remark || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-container">
          <el-pagination background size="small" v-model:current-page="logQuery.pageNum"
            v-model:page-size="logQuery.pageSize" :page-sizes="[10, 20, 50]" :total="logTotal"
            layout="total, sizes, prev, pager, next, jumper" @size-change="handleLogSizeChange"
            @current-change="getLogList" />
        </div>

        <template #footer>
          <el-button @click="logOpen = false">关 闭</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listErpInventoryApi,
  detailErpInventoryApi,
  updateErpInventoryApi,
  listErpInventoryLogApi
} from '@/api/erp/inventory'
import { optionsErpWarehouseApi } from '@/api/erp/warehouse'
import ShopProductSelect from '@/components/ShopProductSelect/index.vue'
import { useDict } from '@/utils/dictCache'
import validate from '@/utils/validate'

const loading = ref(true)
const submitLoading = ref(false)
const detailLoading = ref(false)
const total = ref(0)
const dataList = ref<any[]>([])
const detail = ref<any>({})
const title = ref('')
const open = ref(false)
const detailOpen = ref(false)

/** 变动类型是枚举列，标签一律走字典，不在这页里写死中文名 */
const CHANGE_TYPE_DICT = 'inventory_change_type'
const dict = useDict(CHANGE_TYPE_DICT)

// 库存日志（流水）弹窗：只读，按被点的那一行账查
const logOpen = ref(false)
const logLoading = ref(false)
const logList = ref<any[]>([])
const logTotal = ref(0)
const logTitle = ref('库存日志')

// 流水的筛选条件是固定的：(规格, 仓库) 就是那一行账的身份，页面上不给改
const logQuery = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  skuId: undefined,
  warehouseId: undefined
})

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// 查询参数：lowStockOnly 是纯筛选项（不是表里的列），服务端按 stock < safety_stock 挡
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  warehouseId: undefined,
  productId: undefined,
  lowStockOnly: false
})

/** 商品那一格是弹窗选择器，名称得自己留着回显（同购物车/收藏的口径） */
const queryProductName = ref('')

// 表单参数：只有下面四列写得回去，数量三列是拿来看的
const form = reactive<any>({})

const rules = reactive<FormRules>({
  safetyStock: [{ required: true, message: '安全库存不能为空', trigger: 'blur' }]
})

const priceText = (price: any) => (price == null || price === '' ? '-' : `￥${Number(price).toFixed(2)}`)

/** 流水里的变动数量：库里就是带符号存的（入库正、出库负），这里补个正号让进出一眼能分开 */
const quantityText = (quantity: any) => {
  const count = Number(quantity)
  return count > 0 ? `+${count}` : `${count}`
}

/** 低于安全库存：开关关了或者安全库存是 0 都不算，口径跟服务端的 lowStockOnly 筛选一致 */
const isLowStock = (row: any) => {
  return Number(row.stockWarnEnable) === 1 && Number(row.safetyStock) > 0 && Number(row.stock) < Number(row.safetyStock)
}

const warehouseOptions = ref<any[]>([])

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listErpInventoryApi(queryParams)
    dataList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
  } finally {
    loading.value = false
  }
}

/** 仓库下拉：库存列表这里连停用仓库的账都还在，但候选只列启用中的（同商品入库，免得按停用仓去筛出新仓） */
const getWarehouseOptions = async () => {
  try {
    const { data } = await optionsErpWarehouseApi()
    warehouseOptions.value = data || []
  } catch (error) {
  }
}

/** 表单重置 */
const reset = () => {
  Object.assign(form, {
    id: undefined,
    productId: undefined,
    productName: undefined,
    warehouseId: undefined,
    warehouseName: undefined,
    stock: undefined,
    lockedStock: undefined,
    availableStock: undefined,
    costPrice: undefined,
    safetyStock: 0,
    maxStock: undefined,
    stockWarnEnable: 1,
    // 乐观锁凭据：由列表/详情带回来，本地不该留上一次的残留值
    version: undefined
  })
  formRef.value?.resetFields()
}

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
  queryParams.warehouseId = undefined
  queryParams.productId = undefined
  queryParams.lowStockOnly = false
  queryProductName.value = ''
  handleQuery()
}

const handleProductSelect = (product: any) => {
  queryProductName.value = product.productName
  handleQuery()
}

const handleProductClear = () => {
  queryProductName.value = ''
  handleQuery()
}

/** 详情按钮操作 */
const handleDetail = async (row: any) => {
  detailOpen.value = true
  detailLoading.value = true
  try {
    const { data } = await detailErpInventoryApi(row.id)
    detail.value = data || {}
  } catch (error) {
  } finally {
    detailLoading.value = false
  }
}

/**
 * 库存日志按钮操作：把弹窗定位到被点的那一行账（规格 + 仓库），从最新一条流水看起。
 * 标题里带上规格和仓库名，是为了翻页/滚动时也知道看的是哪一行账
 */
const handleLog = (row: any) => {
  logTitle.value = '库存日志 - '
    + `${row.productName || `规格 #${row.skuId}`}（${row.specText || '默认规格'}）`
    + ` / ${row.warehouseName || `仓库 #${row.warehouseId}`}`
  logQuery.pageNum = 1
  logQuery.skuId = row.skuId
  logQuery.warehouseId = row.warehouseId
  logList.value = []
  logTotal.value = 0
  logOpen.value = true
  getLogList()
}

/** 流水列表：只读，条件固定是这一行账 */
const getLogList = async () => {
  logLoading.value = true
  try {
    const { data } = await listErpInventoryLogApi(logQuery)
    logList.value = data.records || []
    logTotal.value = data.total || 0
  } catch (error) {
  } finally {
    logLoading.value = false
  }
}

const handleLogSizeChange = () => {
  logQuery.pageNum = 1
  getLogList()
}

/**
 * 修改按钮操作：照其它页的惯例先捞一次详情再填表。
 * 这一页尤其要捞：表单要把 version（乐观锁凭据）原样带回去，列表那一行可能已经是几分钟前的了，
 * 拿旧 version 提交会被服务端挡成"库存刚被别人改过"。
 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailErpInventoryApi(row.id)
    Object.assign(form, data)
    open.value = true
    title.value = '修改库存参数'
  } catch (error) {
  }
}

/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    submitLoading.value = true
    try {
      await updateErpInventoryApi({
        id: form.id,
        costPrice: form.costPrice,
        safetyStock: form.safetyStock,
        maxStock: form.maxStock,
        stockWarnEnable: form.stockWarnEnable,
        // 乐观锁：带的是打开弹窗时读到的那一版，中途有人入过货这次就会失败并提示刷新
        version: form.version
      })
      ElMessage.success('修改成功')
      open.value = false
      getList()
    } catch (error) {
    } finally {
      submitLoading.value = false
    }
  })
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
  getWarehouseOptions()
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

/* 勾选那一格独占一行（wrapper  flex-basis 100% 就换行，同商品入库的 .search-row） */
.search-form :deep(.search-row) {
  flex: 0 0 100%;
  display: flex;
  align-items: center;

  .el-form-item {
    flex: 0 0 auto;
    min-width: 0;
    margin-right: 0;
  }
}

/* 仓库下拉里的「名称 + 右侧灰色编码」那一排（同商品入库） */
.product-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  &__name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__code {
    flex: 0 0 auto;
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}

/* 只读数量格下面那排提示 */
.items-footer {
  margin: 0 0 4px 110px;

  .items-tip {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}

/* 详情弹窗里三组描述之间的间距 */
.detail-block {
  margin-top: 20px;
}

.stock-warning {
  color: var(--el-color-danger);
  font-weight: 600;
}

/* 流水里的变动数量：入库（正）绿、出库（负）红，和变动类型那列的标签颜色是两套东西，别混着看 */
.count-in {
  color: var(--el-color-success);
  font-weight: 600;
}

.count-out {
  color: var(--el-color-danger);
  font-weight: 600;
}
</style>
