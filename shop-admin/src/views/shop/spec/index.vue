<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="规格" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="规格名称/规格值" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="所属类目" prop="categoryId" class="category-select">
          <shop-category-select v-model="queryParams.categoryId" :data="categoryOptions" placeholder="全部类目" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
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
          <el-button v-permission="['shop:spec:add']" type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
          <el-button v-permission="['shop:spec:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="规格名称" prop="specName" align="center" min-width="140" />
        <el-table-column label="规格值" prop="specValue" align="center" min-width="140">
          <template #default="{ row }">
            <span>{{ row.specValue || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单位" prop="specUnit" align="center" width="100">
          <template #default="{ row }">
            <span>{{ row.specUnit || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="所属类目" prop="categoryName" align="center" min-width="140">
          <template #default="{ row }">
            <span>{{ row.categoryName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
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
            <el-button v-permission="['shop:spec:update']" type="primary" link icon="Edit"
              @click="handleUpdate(scope.row)">修改</el-button>
            <el-button v-permission="['shop:spec:delete']" type="danger" link icon="Delete"
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
      <el-dialog v-model="open" :title="title" width="560px" append-to-body destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
          <el-form-item label="所属类目" prop="categoryId">
            <shop-category-select v-model="form.categoryId" :data="categoryOptions" placeholder="可不选，空=不限类目"
              style="width: 100%" />
          </el-form-item>
          <el-form-item label="规格名称" prop="specName">
            <el-input v-model="form.specName" placeholder="如：颜色、尺寸、重量" maxlength="64" />
          </el-form-item>
          <el-form-item label="规格值" prop="specValue">
            <el-input v-model="form.specValue" placeholder="如：红色、XL、500" maxlength="64" />
          </el-form-item>
          <el-form-item label="规格单位" prop="specUnit">
            <el-input v-model="form.specUnit" placeholder="如：g、ml、cm，可留空" maxlength="64" />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-radio-group v-model="form.status">
              <el-radio :value="1">启用</el-radio>
              <el-radio :value="0">禁用</el-radio>
            </el-radio-group>
          </el-form-item>
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
  listShopSpecApi,
  detailShopSpecApi,
  addShopSpecApi,
  updateShopSpecApi,
  deleteShopSpecApi
} from '@/api/shop/spec'
import { getCategoryTreeApi } from '@/api/shop/category'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import ShopCategorySelect from '@/components/ShopCategorySelect/index.vue'
import validate from '@/utils/validate'

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
  categoryId: undefined,
  status: undefined
})

// 表单参数
const form = reactive<any>({})

// 分类树选项
const categoryOptions = ref<any[]>([])

const rules = reactive<FormRules>({
  // 类目不必填：留空就是"不限类目"，任何分类下的商品都能选它（口径同品牌）
  specName: [{ required: true, message: '规格名称不能为空', trigger: 'blur' }]
})

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listShopSpecApi(queryParams)
    dataList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
  } finally {
    loading.value = false
  }
}

/** 加载分类树 */
const getCategoryOptions = async () => {
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
    categoryId: undefined,
    specName: undefined,
    specValue: undefined,
    specUnit: undefined,
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

/** 新增按钮操作 */
const handleAdd = () => {
  reset()
  open.value = true
  title.value = '添加商品规格'
}

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailShopSpecApi(row.id)
    Object.assign(form, data)
    open.value = true
    title.value = '修改商品规格'
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
        await updateShopSpecApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopSpecApi(form)
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

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  if (!selectedIds.value.length) return
  ElMessageBox.confirm(`是否确认删除选中的 ${selectedIds.value.length} 条规格?`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => deleteShopSpecApi(selectedIds.value))
    .then(() => {
      getList()
      ElMessage.success('删除成功')
    })
    .catch(() => { })
}

/** 删除按钮操作 */
const handleDelete = (row: any) => {
  ElMessageBox.confirm(`是否确认删除规格“${row.specName}”?`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => deleteShopSpecApi(row.id))
    .then(() => {
      getList()
      ElMessage.success('删除成功')
    })
    .catch(() => { })
}

const handleSizeChange = (val: any) => {
  queryParams.pageSize = val
  getList()
}

const handleCurrentChange = (val: any) => {
  queryParams.pageNum = val
  getList()
}

onMounted(() => {
  getList()
  getCategoryOptions()
})
</script>

<style scoped lang="scss">
/* 参照部门管理：筛选项等分整行，控件宽度统一由 CSS 控制 */
.search-form {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  .category-select{
    align-items: center;
  }

  :deep(.el-form-item) {
    flex: 1 1 0;
    min-width: 0;
    margin-right: 16px;

    &:last-child {
      flex: 0 0 auto;
      margin-right: 0;
    }
  }

  :deep(.el-form-item .el-form-item__content) {
    min-width: 0;
  }

  /* global.scss 里对 .el-input/.el-select 写死了 240px，特异性更高，只能用 !important 覆盖 */
  :deep(.el-form-item .el-input),
  :deep(.el-form-item .el-select),
  :deep(.el-form-item .el-tree-select) {
    width: 100% !important;
  }
}
</style>
