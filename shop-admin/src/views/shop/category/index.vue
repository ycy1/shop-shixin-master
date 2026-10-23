<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="search-form">
        <el-form-item label="类目名称" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="请输入类目名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
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
      <template #header>
        <div class="card-header">
          <ButtonGroup>
            <el-button v-permission="['shop:category:add']" type="primary" icon="Plus" @click="handleAdd()">新增</el-button>
          </ButtonGroup>
        </div>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="categoryList" row-key="id" :tree-props="{ children: 'children' }">
        <el-table-column label="类目名称" prop="categoryName" min-width="120" show-overflow-tooltip />
        <el-table-column label="图标" align="center" width="70">
          <template #default="{ row }">
            <shop-icon :type="row.iconType" :value="row.iconValue" :size="24" :alt="row.categoryName" />
          </template>
        </el-table-column>
        <el-table-column label="层级" align="center" width="90">
          <template #default="{ row }">
            <el-tag :type="row.level === 1 ? 'primary' : row.level === 2 ? 'success' : 'info'">
              {{ levelText(row.level) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="排序" prop="sort" align="center" width="90" />
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
        <el-table-column label="操作" align="center"  fixed="right">
          <template #default="scope">
            <!-- 这几个页面一律用 hidden（= 原来 disabled 的行为：整条不给看），置灰 + tip 留给需要解释原因的那几页 -->
            <TableMoreActions :actions="[
              {
                label: '修改',
                icon: 'Edit',
                hidden: !hasPermission('shop:category:update'),
                command: { type: 'edit', row: scope.row }
              },
              {
                label: '添加子类目',
                icon: 'Plus',
                hidden: !hasPermission('shop:category:add') || scope.row.level >= 3,
                command: { type: 'addChild', row: scope.row }
              },
              {
                label: '删除',
                type: 'danger',
                icon: 'Delete',
                hidden: !hasPermission('shop:category:delete'),
                command: { type: 'delete', row: scope.row }
              }
            ]" @command="handleActionCommand" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加或修改类目对话框 -->
    <el-dialog :title="dialog.title" v-model="dialog.visible" width="600px" append-to-body destroy-on-close>
      <el-form ref="categoryFormRef" :model="categoryForm" :rules="rules" label-width="100px">
        <el-form-item label="上级类目" prop="parentId">
          <shop-category-select v-model="categoryForm.parentId" :data="parentOptions" :clearable="false"
            placeholder="选择上级类目" style="width: 100%" />
        </el-form-item>
        <el-form-item label="类目名称" prop="categoryName">
          <el-input v-model="categoryForm.categoryName" placeholder="请输入类目名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <el-input :model-value="categoryForm.iconName" placeholder="点击选择图标" readonly size="large">
            <template #prefix>
              <shop-icon v-if="categoryForm.iconValue" :type="categoryForm.iconType"
                :value="categoryForm.iconValue" :size="24" />
            </template>
            <template #append>
              <el-button @click="showIconSelect = true">选择图标</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="categoryForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="categoryForm.sort" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cancel">取 消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 图标库选择器 -->
    <shop-icon-select v-model="categoryForm.icon" v-model:visible="showIconSelect" @select="handleIconSelect" />
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getCategoryTreeApi,
  createCategoryApi,
  updateCategoryApi,
  deleteCategoryApi
} from '@/api/shop/category'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import TableMoreActions from '@/components/TableMoreActions/index.vue'
import ShopIcon from '@/components/ShopIcon/index.vue'
import ShopIconSelect from '@/components/ShopIconSelect/index.vue'
import ShopCategorySelect from '@/components/ShopCategorySelect/index.vue'
import validate from '@/utils/validate'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const permissions = computed(() => userStore.user.permissions || [])

// 权限检查
const hasPermission = (permission: string): boolean => {
  return permissions.value.includes(permission)
}

const loading = ref(false)
const submitLoading = ref(false)
const categoryList = ref<any[]>([])
const queryFormRef = ref<FormInstance>()
const categoryFormRef = ref<FormInstance>()
const showIconSelect = ref(false)

// 查询参数
const queryParams = reactive({
  keyword: '',
  status: ''
})

// 弹窗控制
const dialog = reactive({
  title: '',
  visible: false,
  type: 'add' as 'add' | 'edit'
})

// 类目表单，level 由后端根据上级类目推算，不在表单中出现。
// icon 存图标库 id；iconName/iconType/iconValue 是展示用的冗余字段，
// 来自列表接口的联表结果，后端标了 @TableField(exist=false) 不会落库
const categoryForm = reactive<any>({
  id: undefined,
  parentId: 0,
  categoryName: '',
  icon: '',
  iconName: '',
  iconType: undefined,
  iconValue: '',
  status: 1,
  sort: 0
})

// 表单校验规则
const rules = reactive<FormRules>({
  categoryName: [
    { required: true, message: '请输入类目名称', trigger: 'blur' }
  ]
})

// 上级类目选项（含顶级类目）
const parentOptions = ref<any[]>([])

const levelText = (level: number) => {
  return ['一级', '二级', '三级'][level - 1] || `${level}级`
}

// 获取类目树
const getList = async () => {
  loading.value = true
  try {
    const { data } = await getCategoryTreeApi(queryParams)
    categoryList.value = data || []
  } catch (error) {
  } finally {
    loading.value = false
  }
}

// 构建上级类目选项（编辑时排除自身及其子类目，防止循环引用）
const buildParentOptions = (tree: any[], excludeId?: number) => {
  if (!tree || tree.length === 0) return []
  return tree
    .filter((node) => node.id !== excludeId)
    .map((node) => ({
      ...node,
      children: buildParentOptions(node.children, excludeId)
    }))
}

const getParentOptions = async (excludeId?: number) => {
  try {
    const { data } = await getCategoryTreeApi({})
    parentOptions.value = [
      { id: 0, categoryName: '顶级类目', children: buildParentOptions(data, excludeId) }
    ]
  } catch (error) {
  }
}

// 新增类目（parentId 用于“添加子类目”时指定上级）
const handleAdd = (parentId = 0) => {
  dialog.type = 'add'
  dialog.title = parentId ? '新增子类目' : '新增类目'
  Object.assign(categoryForm, {
    id: undefined,
    parentId,
    categoryName: '',
    icon: '',
    iconName: '',
    iconType: undefined,
    iconValue: '',
    status: 1,
    sort: 0
  })
  getParentOptions()
  dialog.visible = true
}

// 修改类目
const handleUpdate = (row: any) => {
  dialog.type = 'edit'
  dialog.title = '修改类目'
  Object.assign(categoryForm, {
    id: row.id,
    parentId: row.parentId || 0,
    categoryName: row.categoryName,
    icon: row.icon || '',
    iconName: row.iconName || '',
    iconType: row.iconType,
    iconValue: row.iconValue || '',
    status: row.status,
    sort: row.sort || 0
  })
  getParentOptions(row.id)
  dialog.visible = true
}

// 选中图标后把展示字段一起写入，避免再发一次详情请求
const handleIconSelect = (icon: any) => {
  categoryForm.iconName = icon.iconName
  categoryForm.iconType = icon.iconType
  categoryForm.iconValue = icon.iconValue
}

// 操作命令分发
const handleActionCommand = (action: any) => {
  const { type, row } = action.command
  switch (type) {
    case 'edit':
      handleUpdate(row)
      break
    case 'addChild':
      handleAdd(row.id)
      break
    case 'delete':
      handleDelete(row)
      break
  }
}

// 提交表单
const submitForm = async () => {
  if (!categoryFormRef.value) return
  await categoryFormRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        const formData = { ...categoryForm }
        if (dialog.type === 'add') {
          await createCategoryApi(formData)
          ElMessage.success('新增成功')
        } else {
          await updateCategoryApi(formData)
          ElMessage.success('修改成功')
        }
        dialog.visible = false
        getList()
      } catch (error) {
      } finally {
        submitLoading.value = false
      }
    }
  })
}

// 删除类目
const handleDelete = (row: any) => {
  ElMessageBox.confirm(`是否确认删除类目“${row.categoryName}”?`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteCategoryApi(row.id)
      ElMessage.success('删除成功')
      getList()
    } catch (error) {
    }
  }).catch(() => { })
}

// 搜索
const handleQuery = () => {
  getList()
}

// 重置查询
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.keyword = ''
  queryParams.status = ''
  handleQuery()
}

// 取消
const cancel = () => {
  dialog.visible = false
  categoryFormRef.value?.resetFields()
}

onMounted(() => {
  getList()
})
</script>

<style scoped lang="scss">
/* 筛选表单保持在一行 */
.search-form {
  display: flex;
  flex-wrap: nowrap;

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

  :deep(.el-form-item .el-input),
  :deep(.el-form-item .el-select) {
    width: 100% !important;
  }
}
</style>
