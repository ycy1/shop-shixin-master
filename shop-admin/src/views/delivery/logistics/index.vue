<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="请输入名称/编码/简称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="类型" prop="companyType">
          <el-select v-model="queryParams.companyType" placeholder="全部" clearable>
            <el-option v-for="item in dict.options('logistics_company_type')" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="支持服务" prop="supportServices">
          <!-- 多选是「或者」：勾了货到付款和易碎品，支持其中任意一项的公司都会出来 -->
          <el-select v-model="queryParams.supportServices" placeholder="任一支持" multiple collapse-tags
            collapse-tags-tooltip clearable>
            <el-option v-for="item in serviceFlags" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options('logistics_status')" :key="item.value" :label="item.label"
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
          <el-button v-permission="['shop:logistics:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['shop:logistics:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <!-- 表格级开了 show-overflow-tooltip，纯图片列要显式关掉，否则悬停会弹一个空 tooltip -->
        <el-table-column label="LOGO" align="center" width="90" :show-overflow-tooltip="false">
          <template #default="{ row }">
            <el-image v-if="row.logo" :src="row.logo" :preview-src-list="[row.logo]" preview-teleported fit="contain"
              style="width: 50px; height: 50px; border-radius: 4px" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="物流公司编码" prop="companyCode" align="center" min-width="150" />
        <el-table-column label="公司名称" prop="companyName" align="center" min-width="140" />
        
        <el-table-column label="类型" align="center" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag('logistics_company_type', row.companyType)">
              {{ dict.label('logistics_company_type', row.companyType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="客服电话" align="center" min-width="110">
          <template #default="{ row }">
            <span>{{ row.servicePhone || '-' }}</span>
          </template>
        </el-table-column>
        <!-- 三个 0/1 开关并成一列，只画出支持的那几项，否则一行塞不下 -->
        <el-table-column label="支持服务" align="center" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="item in supportedServices(row)" :key="item" size="small" class="service-tag">{{ item
            }}</el-tag>
            <span v-if="supportedServices(row).length === 0">-</span>
          </template>
        </el-table-column>
        
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag('logistics_status', row.status)">
              {{ dict.label('logistics_status', row.status) }}
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
            <el-button v-permission="['shop:logistics:update']" type="primary" link icon="Edit"
              @click="handleUpdate(scope.row)">修改</el-button>
            <el-button v-permission="['shop:logistics:delete']" type="danger" link icon="Delete"
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
              <el-form-item label="物流公司编码" prop="companyCode">
                <el-input v-model="form.companyCode" placeholder="保存后由系统生成" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="公司名称" prop="companyName">
                <el-input v-model="form.companyName" placeholder="请输入物流公司名称" maxlength="100" show-word-limit />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="简称" prop="companyShort">
                <el-input v-model="form.companyShort" placeholder="选填，如 顺丰" maxlength="50" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="类型" prop="companyType">
                <el-select v-model="form.companyType" placeholder="请选择" style="width: 100%">
                  <el-option v-for="item in dict.options('logistics_company_type')" :key="item.value"
                    :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="客服电话" prop="servicePhone">
                <el-input v-model="form.servicePhone" placeholder="选填" maxlength="20" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="官网" prop="website">
                <el-input v-model="form.website" placeholder="https://" maxlength="255" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-divider content-position="left">服务能力</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="货到付款" prop="supportCod">
                <el-radio-group v-model="form.supportCod">
                  <el-radio :value="1">支持</el-radio>
                  <el-radio :value="0">不支持</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="保价" prop="supportInsurance">
                <el-radio-group v-model="form.supportInsurance">
                  <el-radio :value="1">支持</el-radio>
                  <el-radio :value="0">不支持</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="易碎品" prop="supportFragile">
                <el-radio-group v-model="form.supportFragile">
                  <el-radio :value="1">支持</el-radio>
                  <el-radio :value="0">不支持</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="最大承重" prop="maxWeight">
                <el-input-number v-model="form.maxWeight" :min="0" :precision="2" placeholder="单位：kg"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="配送范围" prop="deliveryArea">
                <el-input v-model="form.deliveryArea" type="textarea" :rows="2" placeholder="选填，如 全国，偏远地区除外"
                  maxlength="500" show-word-limit />
              </el-form-item>
            </el-col>
          </el-row>
          <el-divider content-position="left">展示与说明</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="LOGO" prop="logo">
                <upload-image v-model="form.logo" :size="120" source="logistics" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="排序" prop="sort">
                <el-input-number v-model="form.sort" :min="0" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="公司简介" prop="description">
                <el-input v-model="form.description" type="textarea" :rows="3" placeholder="选填" maxlength="500"
                  show-word-limit />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态" prop="status">
                <el-radio-group v-model="form.status">
                  <el-radio v-for="item in dict.options('logistics_status')" :key="item.value" :value="item.value">
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
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listShopLogisticsApi,
  detailShopLogisticsApi,
  addShopLogisticsApi,
  updateShopLogisticsApi,
  deleteShopLogisticsApi
} from '@/api/delivery/logistics'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import UploadImage from '@/components/Upload/Image.vue'
import validate from '@/utils/validate'
import { useDict } from '@/utils/dictCache'

/** 物流公司字典：logistics_company_type = company_type，logistics_status = status */
const dict = useDict('logistics_company_type', 'logistics_status')

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
  companyType: undefined,
  // 多选控件要的是数组，发请求前拼成逗号串（见 getList）
  supportServices: [] as string[],
  status: undefined
})

// 表单参数
const form = reactive<any>({})

const rules = reactive<FormRules>({
  companyName: [{ required: true, message: '物流公司名称不能为空', trigger: 'blur' }]
})

/**
 * 三个 0/1 服务开关：列表并成一列显示、搜索栏按它筛，两处共用这一份。
 * key = 表里的列名（驼峰），value = 传给后端 supportServices 的标识。
 * 布尔列按项目口径不建字典（同 shop_store.is_delivery）。
 */
const serviceFlags = [
  { key: 'supportCod', value: 'cod', label: '货到付款' },
  { key: 'supportInsurance', value: 'insurance', label: '保价' },
  { key: 'supportFragile', value: 'fragile', label: '易碎品' }
]

/** 列表那一格只画支持的那几项 */
const supportedServices = (row: any) => {
  return serviceFlags.filter(item => row[item.key] === 1).map(item => item.label)
}

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    // 数组要拼成逗号串再发：axios 默认把数组序列化成 supportServices[]=xx，后端命令对象接不上
    const { supportServices, ...rest } = queryParams
    const { data } = await listShopLogisticsApi({
      ...rest,
      supportServices: supportServices.length ? supportServices.join(',') : undefined
    })
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
    companyCode: undefined,
    companyName: undefined,
    companyShort: undefined,
    companyType: dict.defaultValue('logistics_company_type', 1),
    servicePhone: undefined,
    website: undefined,
    logo: undefined,
    description: undefined,
    supportCod: 0,
    supportInsurance: 0,
    supportFragile: 0,
    maxWeight: undefined,
    deliveryArea: undefined,
    sort: 0,
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
  queryParams.companyType = undefined
  queryParams.supportServices = []
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
  title.value = '添加物流公司'
}

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailShopLogisticsApi(row.id)
    Object.assign(form, data)
    open.value = true
    title.value = '修改物流公司'
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
        await updateShopLogisticsApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopLogisticsApi(form)
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
  ElMessageBox.confirm(`确认删除物流公司「${row.companyName}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopLogisticsApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个物流公司吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopLogisticsApi(selectedIds.value)
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

.service-tag {
  margin-right: 4px;
}
</style>
