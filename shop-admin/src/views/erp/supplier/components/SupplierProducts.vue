<template>
  <div class="supplier-products">
    <!-- 筛选：供应商已经被外面的那一行钉住了，所以这里没有供应商那一格 -->
    <el-form ref="queryFormRef" :model="queryParams" inline class="filter-form">
      <el-form-item label="关键字" prop="keyword">
        <el-input v-model="queryParams.keyword" placeholder="商品名称/编码/规格/供货编码" clearable
          @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="商品分类" prop="categoryId">
        <shop-category-select v-model="queryParams.categoryId" :data="categoryOptions" placeholder="全部分类" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable>
          <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
            :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 操作按钮放在表格上方（口径同独立页那版） -->
    <ButtonGroup>
      <el-button v-permission="['erp:supplierProduct:add']" type="primary" plain icon="Plus"
        @click="handleAdd">新增</el-button>
      <el-button v-permission="['erp:supplierProduct:delete']" type="danger" plain icon="Delete"
        :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
    </ButtonGroup>
    <br/>
    <el-table v-loading="loading" :data="dataList" border show-overflow-tooltip
      @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="45" align="center" />
      <!-- 商品名称是本表冗余的快照（改名不回刷），商品编码和规格是联表现取的，所以商品删过之后名字还在 -->
      <el-table-column label="商品名称" prop="productName" align="center" min-width="160" />
      <!-- 一行是一条供货关系，而关系挂在规格上：同一商品的几个规格就是好几行，光看商品名分不出哪行 -->
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
      <el-table-column label="所属分类" prop="categoryId" align="center" min-width="110">
        <template #default="{ row }">
          <span>{{ categoryNameMap[row.categoryId] || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="供货价" align="center" width="100">
        <template #default="{ row }">
          <span>{{ priceText(row.supplyPrice) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="供货编码" prop="supplyCode" align="center" min-width="130">
        <template #default="{ row }">
          <span>{{ row.supplyCode || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="最小起订量" prop="minOrderQty" align="center" width="95" />
      <el-table-column label="供货周期" align="center" width="90">
        <template #default="{ row }">
          <span>{{ row.leadTime == null ? '-' : `${row.leadTime} 天` }}</span>
        </template>
      </el-table-column>
      <el-table-column label="默认" align="center" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.isDefault === 1" size="small" type="success">默认</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="85">
        <template #default="{ row }">
          <el-tag size="small" :type="dict.tag(DICT_TYPE, row.status)">
            {{ dict.label(DICT_TYPE, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" align="center" width="110">
        <template #default="{ row }">
          <span>{{ validate.formatTime(row.createTime, 'YYYY-MM-DD') || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="150" fixed="right">
        <template #default="scope">
          <el-button v-permission="['erp:supplierProduct:update']" type="primary" link icon="Edit"
            @click="handleUpdate(scope.row)">修改</el-button>
          <el-button v-permission="['erp:supplierProduct:delete']" type="danger" link icon="Delete"
            @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-container">
      <el-pagination background v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 30, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange" @current-change="handleCurrentChange" />
    </div>

    <!-- 添加或修改对话框 -->
    <el-dialog v-model="open" :title="title" width="720px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="供应商">
              <!-- 供应商就是外面点进来的那一家，改不了：(供应商, 规格) 定死了一条关系的身份，
                   要换供应商得关掉这个弹窗去别的供应商名下重建（后端 update 也会把这两列钉回原值） -->
              <el-input :value="supplierName || `供应商 #${supplierId}`" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品规格" prop="skuId">
              <!-- 规格选择器（公共组件）：弹窗里按关键字/分类筛出具体那一行规格，带单价和可用库存。
                   它不像 el-select 会自己触发 change 校验，所以选完手动清这一格的报错。
                   修改时禁用：这一格和上面那家供应商一起构成身份 -->
              <shop-product-sku-select v-model="form.skuId" :label="skuLabel" :disabled="isEdit"
                @select="formRef?.clearValidate('skuId')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供货价" prop="supplyPrice">
              <el-input-number v-model="form.supplyPrice" :min="0" :precision="2" :controls="false"
                placeholder="可空，还没谈定" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供货编码" prop="supplyCode">
              <el-input v-model="form.supplyCode" placeholder="选填，供应商自己的货号" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最小起订量" prop="minOrderQty">
              <el-input-number v-model="form.minOrderQty" :min="1" :controls="false" placeholder="默认 1"
                style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供货周期" prop="leadTime">
              <el-input-number v-model="form.leadTime" :min="0" :controls="false" placeholder="选填，单位天"
                style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认供应商" prop="isDefault">
              <!-- 同一规格下最多一行是默认，互斥由后端保证（设成默认会把别的行改回 0），口径同收货地址的默认地址 -->
              <el-radio-group v-model="form.isDefault">
                <el-radio :value="1">默认</el-radio>
                <el-radio :value="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="item in dict.options(DICT_TYPE)" :key="item.value" :value="item.value">
                  {{ item.label }}
                </el-radio>
              </el-radio-group>
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
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listErpSupplierProductApi,
  detailErpSupplierProductApi,
  addErpSupplierProductApi,
  updateErpSupplierProductApi,
  deleteErpSupplierProductApi
} from '@/api/erp/supplierProduct'
import { getCategoryTreeApi } from '@/api/shop/category'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import ShopCategorySelect from '@/components/ShopCategorySelect/index.vue'
import ShopProductSkuSelect from '@/components/ShopProductSkuSelect/index.vue'
import validate from '@/utils/validate'
import { useDict } from '@/utils/dictCache'

/**
 * 供应商商品（供货关系）列表，挂在供应商管理页的「供应商品」弹窗里。
 *
 * 由外层的供应商钉住范围：这一家名下有哪些规格供货，就在这里看和改，
 * 所以原来独立页上的「供应商」筛选项、那一列、表单里选供应商那三处都拿掉了。
 * 数据范围还是服务端说了算：供应商账号登录时接口只返回他自己名下的那几家（见后端 selectPage）。
 */
const props = defineProps<{
  /** 当前看的是哪家供应商（shop_erp_supplier.id），列表和新增都按它过滤 */
  supplierId: number | string
  /** 供应商名称，只用于标题和确认文案，不参与查询 */
  supplierName?: string
}>()

/** 合作状态字典：supplier_product_status = status */
const DICT_TYPE = 'supplier_product_status'
const dict = useDict(DICT_TYPE)

const loading = ref(true)
const submitLoading = ref(false)
const selectedIds = ref<any[]>([])
const total = ref(0)
const dataList = ref<any[]>([])
const title = ref('')
const open = ref(false)

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// 查询参数：supplierId 不在这里，它来自 props，提交时再并进去（免得被重置按钮清掉）
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  categoryId: undefined,
  status: undefined
})

// 表单参数：id 有值就是修改（规格那一格跟着禁用）
const form = reactive<any>({})
const isEdit = computed(() => form.id !== undefined)

// 修改回显：选择器自己只认 id，所以把详情带回的商品名快照 + 规格文本拼给它
const skuLabel = computed(() =>
  `${form.productName || ''}${form.specText ? ` ${form.specText}` : ''}`.trim())

const rules = reactive<FormRules>({
  skuId: [{ required: true, message: '请选择商品规格', trigger: 'change' }]
})

/** 金额的显示口径同采购单、商品列表（后端返回的 decimal 是字符串，统一走 Number） */
const priceText = (price: any) => (price == null || price === '' ? '-' : `￥${Number(price).toFixed(2)}`)

// 分类树：筛选项和列表「所属分类」那格的名称映射都靠它（分页 XML 不联 shop_category）
const categoryOptions = ref<any[]>([])

const categoryNameMap = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {}
  const walk = (nodes: any[]) => {
    for (const node of nodes || []) {
      map[node.id] = node.categoryName
      if (node.children?.length) walk(node.children)
    }
  }
  walk(categoryOptions.value)
  return map
})

/** 查询列表：条件里 always 带上 supplierId，只看这一家名下的供货关系 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listErpSupplierProductApi({ ...queryParams, supplierId: props.supplierId })
    dataList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
  } finally {
    loading.value = false
  }
}

/** 分类树只用来把 categoryId 翻成名字，一个弹窗生命周期里查一次就够 */
const getOptions = async () => {
  try {
    const { data } = await getCategoryTreeApi({})
    categoryOptions.value = data || []
  } catch (error) {
  }
}

/** 表单重置 */
const reset = () => {
  Object.assign(form, {
    id: undefined,
    skuId: undefined,
    productName: undefined,
    specText: undefined,
    supplyPrice: undefined,
    supplyCode: undefined,
    minOrderQty: undefined,
    leadTime: undefined,
    isDefault: 0,
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
  queryParams.categoryId = undefined
  queryParams.status = undefined
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
}

/** 新增按钮操作：供应商已经是 props 那一家，不用再选 */
const handleAdd = () => {
  reset()
  open.value = true
  title.value = '添加供应商品'
}

/** 修改按钮操作：规格那一格是禁用的，回显靠详情带的商品名 + 规格文本拼成 label */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailErpSupplierProductApi(row.id)
    Object.assign(form, data)
    open.value = true
    title.value = '修改供应商品'
  } catch (error) {
  }
}

/** 提交按钮：supplierId 在这里补，新增时就是当前这家；修改时后端本来也会钉回原值 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateErpSupplierProductApi(form)
        ElMessage.success('修改成功')
      } else {
        await addErpSupplierProductApi({ ...form, supplierId: props.supplierId })
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
    `确认删除「${props.supplierName || '该供应商'}」供货「${row.productName}${row.specText ? ` ${row.specText}` : ''}」的关系吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteErpSupplierProductApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 条供货关系吗？删掉后采购单就选不到这些规格了。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteErpSupplierProductApi(selectedIds.value)
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
  getOptions()
})
</script>

<style scoped lang="scss">
/* 弹窗里的筛选项：宽度写死比列表页的等分布局好使，格与格之间靠 el-form inline 的默认间距 */
.filter-form {
  margin-bottom: 4px;

  :deep(.el-form-item) {
    margin-bottom: 12px;
  }

  :deep(.el-input),
  :deep(.el-select) {
    width: 200px;
  }
}
</style>
