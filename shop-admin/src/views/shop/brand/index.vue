<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="品牌名称" prop="brandName">
          <el-input v-model="queryParams.brandName" placeholder="请输入品牌名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="所属分类" prop="categoryId" class="category-select">
          <shop-category-select v-model="queryParams.categoryId" :data="categoryOptions" placeholder="全部分类" />
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
          <el-button v-permission="['shop:brand:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['shop:brand:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="LOGO" align="center" width="90">
          <template #default="{ row }">
            <el-image v-if="row.logo" :src="row.logo" :preview-src-list="[row.logo]" preview-teleported fit="contain"
              style="width: 50px; height: 50px; border-radius: 4px" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <!-- 表格级开了 show-overflow-tooltip，纯图片列要显式关掉，否则悬停会弹一个空 tooltip -->
        <el-table-column label="代言人" align="center" width="110" :show-overflow-tooltip="false">
          <template #default="{ row }">
            <el-tooltip v-if="row.spokespersons?.length" :content="`共 ${row.spokespersons.length} 张`" placement="top">
              <!-- 只画首图，但 preview-src-list 给整组：点开后用查看器的左右箭头翻剩下的 -->
              <el-image :src="row.spokespersons[0].url" :preview-src-list="row.spokespersonUrls" :initial-index="0"
                preview-teleported fit="cover" class="spokesperson-thumb" />
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="品牌名称" prop="brandName" align="center" min-width="140" />
        <el-table-column label="所属分类" prop="categoryId" align="center" min-width="120">
          <template #default="{ row }">
            <span>{{ categoryNameMap[row.categoryId] || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="联系方式" prop="contact" align="center" min-width="150">
          <template #default="{ row }">
            <span>{{ row.contact || '-' }}</span>
          </template>
        </el-table-column>
        <!-- <el-table-column label="品牌介绍" prop="description" align="center" min-width="220">
          <template #default="{ row }">
            <span>{{ row.description || '-' }}</span>
          </template>
        </el-table-column> -->
        <el-table-column label="排序" prop="sort" align="center" width="80" />
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
            <el-button v-permission="['shop:brand:update']" type="primary" link icon="Edit"
              @click="handleUpdate(scope.row)">修改</el-button>
            <el-button v-permission="['shop:brand:delete']" type="danger" link icon="Delete"
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
      <el-dialog v-model="open" :title="title" width="640px" append-to-body destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="品牌名称" prop="brandName">
                <el-input v-model="form.brandName" placeholder="请输入品牌名称" maxlength="50" show-word-limit />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="排序" prop="sort">
                <el-input-number v-model="form.sort" :min="0" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="所属分类" prop="categoryId">
            <shop-category-select v-model="form.categoryId" :data="categoryOptions" placeholder="可不选"
              style="width: 100%" />
          </el-form-item>
          <el-form-item label="品牌LOGO" prop="logo">
            <upload-image v-model="form.logo" :size="120" source="brand" />
          </el-form-item>
          <el-form-item label="代言人" prop="brandSpokesperson">
            <!-- 存 [{"name":"","url":""}]，所以那行文字用 name 键 -->
            <image-list v-model="form.brandSpokesperson" :limit="6" source="brand" label-key="name"
              placeholder="代言人姓名" />
          </el-form-item>
          <el-form-item label="联系方式" prop="contact">
            <el-input v-model="form.contact" placeholder="电话 / 微信 / 邮箱，选填" maxlength="100" />
          </el-form-item>
          <el-form-item label="品牌介绍" prop="description">
            <el-input v-model="form.description" type="textarea" :rows="4" placeholder="选填" maxlength="500"
              show-word-limit />
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
  listShopBrandApi,
  detailShopBrandApi,
  addShopBrandApi,
  updateShopBrandApi,
  deleteShopBrandApi
} from '@/api/shop/brand'
import { getCategoryTreeApi } from '@/api/shop/category'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import ShopCategorySelect from '@/components/ShopCategorySelect/index.vue'
import UploadImage from '@/components/Upload/Image.vue'
// 代言人图片复用了商品轮播图那套 [{文字, url}] 编辑器，靠 label-key 切换键名；
// 它已经是跨页面共用组件了，后续可移到 src/components/Upload/ 下
import ImageList from '@/views/shop/product/components/ImageList.vue'
import validate from '@/utils/validate'
import { parseImageJson, toImageUrls } from '@/utils/imageJson'

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
  brandName: undefined,
  categoryId: undefined,
  status: undefined
})

// 表单参数
const form = reactive<any>({})

// 分类树选项
const categoryOptions = ref<any[]>([])

// 品牌分页走的是 BaseMapper，没有联表回写分类名，所以列表用本地这层映射显示
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

const rules = reactive<FormRules>({
  brandName: [{ required: true, message: '品牌名称不能为空', trigger: 'blur' }]
})

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listShopBrandApi(queryParams)
    // brand_spokesperson 是 json 列，接口原样给回 JSON 字符串，这里解析成数组供图片列渲染
    dataList.value = (data.records || []).map((row: any) => {
      const items = parseImageJson(row.brandSpokesperson)
      return { ...row, spokespersons: items, spokespersonUrls: toImageUrls(items) }
    })
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
    brandName: undefined,
    logo: undefined,
    brandSpokesperson: undefined,
    description: undefined,
    contact: undefined,
    categoryId: undefined,
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
  queryParams.brandName = undefined
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
  title.value = '添加品牌'
}

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailShopBrandApi(row.id)
    Object.assign(form, data)
    open.value = true
    title.value = '修改品牌'
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
        await updateShopBrandApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopBrandApi(form)
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
  ElMessageBox.confirm(`确认删除品牌「${row.brandName}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopBrandApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个品牌吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopBrandApi(selectedIds.value)
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
  getCategoryOptions()
})
</script>

<style scoped lang="scss">
/* 参照部门管理：筛选项等分整行，控件宽度统一由 CSS 控制 */
.search-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  .category-select{
    align-items: center;
  }

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
  :deep(.el-form-item .el-select),
  :deep(.el-form-item .el-tree-select) {
    width: 100% !important;
  }
}

/* 尺寸和 LOGO 列对齐；cursor 是可继承属性，写在 el-image 根上就能作用到里面的 img */
.spokesperson-thumb {
  width: 50px;
  height: 50px;
  border-radius: 4px;
  cursor: zoom-in;
}
</style>
