<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="请输入名称/编码/联系人/电话" clearable
            @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options('supplier_status')" :key="item.value" :label="item.label"
              :value="item.value" />
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
          <el-button v-permission="['erp:supplier:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['erp:supplier:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="供应商编码" prop="supplierCode" align="center" min-width="180" />
        <el-table-column label="供应商名称" prop="supplierName" align="center" min-width="160" />
        
        <el-table-column label="绑定账号" align="center" min-width="120">
          <template #default="{ row }">
            <span>{{ row.userName || '-' }}</span>
          </template>
        </el-table-column>
        
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag('supplier_status', row.status)">
              {{ dict.label('supplier_status', row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" align="center" width="170">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.createTime, 'YYYY-MM-DD') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="255" fixed="right">
          <template #default="scope">
            <!-- 供货关系（原「供应商商品」独立页）并进这里：范围就是被点的这一家 -->
            <el-button v-permission="['erp:supplierProduct:list']" type="primary" link icon="Goods"
              @click="handleProducts(scope.row)">供应商品</el-button>
            <el-button v-permission="['erp:supplier:update']" type="primary" link icon="Edit"
              @click="handleUpdate(scope.row)">修改</el-button>
            <el-button v-permission="['erp:supplier:delete']" type="danger" link icon="Delete"
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
              <el-form-item label="供应商编码" prop="supplierCode">
                <el-input v-model="form.supplierCode" placeholder="保存后由系统生成" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="供应商名称" prop="supplierName">
                <el-input v-model="form.supplierName" placeholder="请输入供应商名称" maxlength="100" show-word-limit />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="绑定账号" prop="userId">
                <!-- 供应商账号自己新增时这一格填成本人并禁用（后端 insert 也会钉回登录 id，禁用只是防误操作）；
                     其它账号可自由指定/换绑/清空 -->
                <!-- 绑定账号只能挂到供应商账号上：停用账号不列，别的账号类型也不列 -->
                <user-select v-model="form.userId" :label="form.userName" only-enabled :account-type="4"
                  :disabled="isSupplierAccount" style="width: 100%" @select="handleAccountSelect"
                  @clear="handleAccountClear" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="联系人" prop="contactName">
                <el-input v-model="form.contactName" placeholder="选填" maxlength="50" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="联系电话" prop="contactPhone">
                <el-input v-model="form.contactPhone" placeholder="选填" maxlength="20" />
              </el-form-item>
            </el-col>
            
            <el-col :span="24">
              <el-form-item label="所在地区">
                <!-- 级联的 value 就是行政区划代码，选中结果在提交时 join 成 area_code -->
                <el-cascader v-model="areaArr" :options="colPickerData"
                  :props="{ label: 'text', value: 'value', children: 'children' }" style="width: 100%" clearable
                  placeholder="请选择所在地区" />
              </el-form-item>
            </el-col>
            <el-col :span="18">
              <el-form-item label="经纬度" prop="longitude">
                <div class="pair-field">
                  <el-input-number v-model="form.longitude" :precision="6" :controls="false"
                    placeholder="经度 如 120.150000" style="width: 100%" />
                  <span class="pair-split">-</span>
                  <el-input-number v-model="form.latitude" :precision="6" :controls="false" placeholder="纬度 如 30.250000"
                    style="width: 100%" />
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <!-- 整表 label-width 是 110px，这一格只有 6 格宽，不取消 label 按钮就顶出去了 -->
              <el-form-item label-width="0">
                <shop-map :longitude="form.longitude" :latitude="form.latitude" @confirm="handleLocationConfirm" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="详细地址" prop="address">
                <el-input v-model="form.address" placeholder="选填，街道、门牌号" maxlength="255" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-divider content-position="left">结算信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="开户行" prop="bankName">
                <el-input v-model="form.bankName" placeholder="选填" maxlength="100" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="银行账号" prop="bankAccount">
                <el-input v-model="form.bankAccount" placeholder="选填" maxlength="64" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="税号" prop="taxNo">
                <el-input v-model="form.taxNo" placeholder="选填" maxlength="64" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态" prop="status">
                <el-radio-group v-model="form.status">
                  <el-radio v-for="item in dict.options('supplier_status')" :key="item.value" :value="item.value">
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

      <!-- 供应商品（供货关系）：被点的那一家名下供了哪些商品，进来就是这一家的范围。
           destroy-on-close 是关键：弹窗里那个列表组件在 mounted 时按 supplierId 查一次，
           换了另一家供应商再打开就是重新建、重新查，不会看着上一家的数据 -->
      <el-dialog v-model="productsOpen" :title="productsTitle" width="1200px" append-to-body destroy-on-close>
        <supplier-products v-if="currentSupplier.id" :supplier-id="currentSupplier.id"
          :supplier-name="currentSupplier.supplierName" />
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="productsOpen = false">关 闭</el-button>
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
  listErpSupplierApi,
  detailErpSupplierApi,
  addErpSupplierApi,
  updateErpSupplierApi,
  deleteErpSupplierApi
} from '@/api/erp/supplier'
import { getUserDetailApi } from '@/api/system/user'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import ShopMap from '@/components/ShopMap/index.vue'
import SupplierProducts from './components/SupplierProducts.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import validate from '@/utils/validate'
import { useDict } from '@/utils/dictCache'
import { useColPickerData } from '@/composables/useColPickerData'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
/** 账号类型：0平台 1门店 2送货员 3消费者 4供应商 5ERP操作员；供应商账号只看/只改绑定到自己名下的那条 */
const isSupplierAccount = computed(() => userStore.user.accountType === 4)

/** 供应商状态字典：supplier_status = status */
const dict = useDict('supplier_status')

/** 地区数据源：@vant/area-data，级联的 value 就是行政区划代码 */
const { colPickerData, resolveAreaCode } = useColPickerData()
// 库里存的是 area_code（"110000,110100,110101"）+ 冗余的 area_zh，
// 级联选择器要的是数组，join / split 的互转在 reset、回显、提交三处做
const areaArr = ref<string[]>([])

const loading = ref(true)
const submitLoading = ref(false)
const selectedIds = ref<any[]>([])
const total = ref(0)
const dataList = ref<any[]>([])
const title = ref('')
const open = ref(false)

/** 供应商品弹窗：记住点开的是哪一家（id 给列表当范围，名称只用在标题和删除确认里） */
const productsOpen = ref(false)
const currentSupplier = ref<any>({})
const productsTitle = computed(() =>
  `供应商品${currentSupplier.value.supplierName ? ' - ' + currentSupplier.value.supplierName : ''}`)

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// 查询参数
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  status: undefined
})

// 表单参数
const form = reactive<any>({})

const rules = reactive<FormRules>({
  supplierName: [{ required: true, message: '供应商名称不能为空', trigger: 'blur' }]
})

/** 列表那一格：地区优先用库里冗余的 area_zh，兜底才按 area_code 现场解析（老数据可能只有 code） */
const areaOf = (row: any) => {
  return row.areaZh || resolveAreaCode(row.areaCode) || '-'
}

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listErpSupplierApi(queryParams)
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
    supplierCode: undefined,
    supplierName: undefined,
    contactName: undefined,
    contactPhone: undefined,
    areaCode: undefined,
    areaZh: undefined,
    address: undefined,
    longitude: undefined,
    latitude: undefined,
    bankName: undefined,
    bankAccount: undefined,
    taxNo: undefined,
    userId: undefined,
    userName: undefined,
    status: 1
  })
  formRef.value?.resetFields()
  // 省市区不在 form 里直接编辑，靠这个数组中转，所以 reset 要一起清
  areaArr.value = []
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
  queryParams.status = undefined
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
}

/** 地图选点确认：坐标收进列精度；换了点位老地址就对不上，解析出地址就一起改掉 */
const handleLocationConfirm = (point: any) => {
  form.longitude = Number(Number(point.longitude).toFixed(6))
  form.latitude = Number(Number(point.latitude).toFixed(6))
  if (point.address) form.address = point.address
}

/**
 * 选中绑定账号：昵称既给 UserSelect 的输入框回显，也回填到联系人/联系电话那两格
 * （供应商的联系人多数就是绑定账号本人，省一遍输入，口径同收货地址页选消费者）。
 * 手机号取用户列表里的 mobile，账号没填手机号就留空；换人时以新选的人为准，直接覆盖。
 */
const handleAccountSelect = (user: any) => {
  const name = user.nickname || user.username
  form.userName = name
  form.contactName = name
  form.contactPhone = user.mobile
}

/** 取消绑定只清回显的名字：联系人/电话是已经录进去的业务数据，不跟着抹 */
const handleAccountClear = () => {
  form.userName = undefined
}

/**
 * 供应商账号自己新增：绑定账号填成本人（那一格是禁用的，不会再触发 @select，所以联系人得在这里主动带一次）。
 * 手机号不在 /api/auth/info 返回的登录信息里，只能按 id 再查一次用户详情（这个读接口不校验权限，供应商账号调得动）。
 */
const fillSelfAsAccount = async () => {
  const self = userStore.user as any
  if (!self.id) return
  form.userId = self.id
  form.userName = self.nickname || self.username
  try {
    const { data } = await getUserDetailApi(self.id)
    form.contactName = data.nickname || data.username || form.userName
    form.contactPhone = data.mobile
  } catch (error) {
  }
}

/** 新增按钮操作 */
const handleAdd = async () => {
  reset()
  open.value = true
  title.value = '添加供应商'
  if (isSupplierAccount.value) await fillSelfAsAccount()
}

/** 供应商品按钮操作：列表组件自己按 supplierId 查，这里只负责把是哪一家传进去 */
const handleProducts = (row: any) => {
  currentSupplier.value = { id: row.id, supplierName: row.supplierName }
  productsOpen.value = true
}

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailErpSupplierApi(row.id)
    Object.assign(form, data)
    // area_code 是 "110000,110100,110101" 这种，split 回级联选择器要的数组
    areaArr.value = (data.areaCode || '').split(',').filter(Boolean)
    open.value = true
    title.value = '修改供应商'
  } catch (error) {
  }
}

/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    // 级联的 [省, 市, 区县] 存成一列 area_code，中文地名顺手解析出来存 area_zh（同门店管理）；
    // 清空时 area_code 是空串，这两列在实体上是 FieldStrategy.IGNORED，所以写得回库
    form.areaCode = (areaArr.value || []).join(',')
    form.areaZh = resolveAreaCode(form.areaCode)
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateErpSupplierApi(form)
        ElMessage.success('修改成功')
      } else {
        await addErpSupplierApi(form)
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
  ElMessageBox.confirm(`确认删除供应商「${row.supplierName}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteErpSupplierApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个供应商吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteErpSupplierApi(selectedIds.value)
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

/* 经纬度拆成两格：两个输入等分整行，中间夹一个分隔符（同门店管理）。
   el-input-number 上写死的 width:100% 会被 flex-basis 0 顶掉，正好让两格等宽 */
.pair-field {
  display: flex;
  gap: 8px;
  width: 100%;

  :deep(.el-select),
  :deep(.el-input-number) {
    flex: 1;
    min-width: 0;
  }
}

/* 分隔符不参与伸缩，高度对齐输入框、文字居中，颜色压到占位符一档免得抢输入框 */
.pair-split {
  flex: 0 0 auto;
  height: var(--el-component-size, 32px);
  line-height: var(--el-component-size, 32px);
  text-align: center;
  color: var(--el-text-color-placeholder);
  user-select: none;
}
</style>
