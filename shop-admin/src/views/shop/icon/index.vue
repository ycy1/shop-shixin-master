<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="图标" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="图标名称/编码" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="所属类目" prop="categoryId" class="category-select">
          <shop-category-select v-model="queryParams.categoryId" :data="categoryOptions" placeholder="全部类目" />
        </el-form-item>
        <el-form-item label="类型" prop="iconType">
          <el-select v-model="queryParams.iconType" placeholder="全部" clearable>
            <el-option v-for="item in ICON_TYPES" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
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
        <div class="card-header">
          <div class="card-header-actions">
            <!-- 卡片没有表头，全选框只能放工具栏，管的是当前这一页 -->
            <el-checkbox :model-value="checkAll" :indeterminate="isIndeterminate" @change="handleCheckAll">全选</el-checkbox>
            <ButtonGroup>
              <el-button v-permission="['shop:icon:add']" type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
              <el-button v-permission="['shop:icon:delete']" type="danger" plain icon="Delete"
                :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
            </ButtonGroup>
          </div>
          <span class="card-header-tip">单击卡片选中/取消，右键可修改、复制编码、删除</span>
        </div>
      </template>

      <!-- 图标卡片：5 列，配合每页 20 条正好铺满 4 行 -->
      <div v-loading="loading" class="icon-grid">
        <div v-for="row in dataList" :key="row.id" class="icon-card" :class="{ selected: isSelected(row.id) }"
          @click="toggleSelect(row)" @contextmenu.prevent="openMenu(row, $event)">
          <el-checkbox class="icon-card-check" :model-value="isSelected(row.id)" @click.stop @change="toggleSelect(row)" />
          <div class="icon-card-main">
            <shop-icon :type="row.iconType" :value="row.iconValue" :size="32" :alt="row.iconName" />
          </div>
          <div class="icon-card-name" :title="row.iconName">{{ row.iconName }}</div>
          <div class="icon-card-category" :title="row.categoryName">{{ row.categoryName || '未分类' }}</div>
        </div>
      </div>
      <el-empty v-if="!loading && dataList.length === 0" description="暂无图标" />

      <!-- 分页工具栏：一页就是 5×4=20 张卡片，所以不给改页容量的下拉 -->
      <div class="pagination-container">
        <el-pagination background v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :total="total" layout="total, prev, pager, next, jumper" @current-change="handleCurrentChange" />
      </div>

      <!-- 添加或修改对话框 -->
      <el-dialog v-model="open" :title="title" width="640px" append-to-body destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="图标名称" prop="iconName">
                <el-input v-model="form.iconName" placeholder="如：向左箭头" maxlength="64" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="图标编码" prop="iconCode">
                <el-input v-model="form.iconCode" placeholder="唯一标识，如 icon-arrow-left" maxlength="64" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="所属类目" prop="categoryId">
                <shop-category-select v-model="form.categoryId" :data="categoryOptions" placeholder="可不选"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="排序" prop="sort">
                <el-input-number v-model="form.sort" :min="0" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="图标类型" prop="iconType">
            <el-radio-group v-model="form.iconType" @change="handleTypeChange">
              <el-radio v-for="item in ICON_TYPES" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="图标内容" prop="iconValue">
            <!-- 三种类型的内容格式互不兼容，控件跟着切换 -->
            <el-input v-if="form.iconType === 1" v-model="form.iconValue" type="textarea" :rows="6"
              placeholder="粘贴 SVG 源码，需以 svg 标签开头并带 viewBox 属性" />
            <upload-image v-else-if="form.iconType === 2" v-model="form.iconValue" source="icon" />
            <el-input v-else-if="form.iconType === 3" v-model="form.iconValue"
              placeholder="字体类名，如 iconfont icon-shouye，多个类名用空格分隔" maxlength="255" />
          </el-form-item>
          <el-form-item label="预览">
            <div class="icon-preview">
              <shop-icon :type="form.iconType" :value="form.iconValue" :size="48" :alt="form.iconName" />
            </div>
            <div class="icon-preview-tip">
              SVG 会先做白名单清洗再渲染，script、事件属性、外部引用会被剔除，所以预览和实际显示一致。
            </div>
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-radio-group v-model="form.status">
              <el-radio :value="1">启用</el-radio>
              <el-radio :value="0">禁用</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" maxlength="255"
              show-word-limit />
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

    <!-- 右键操作栏：整页只挂一个，menuRow 记住右键的那张卡片。teleport 到 body 是为了躲开卡片容器的层叠上下文 -->
    <teleport to="body">
      <template v-if="menuVisible">
        <div class="icon-menu-mask" @click="closeMenu" @contextmenu.prevent="closeMenu"></div>
        <ul class="icon-menu" :style="{ left: menuX + 'px', top: menuY + 'px' }">
          <li v-permission="['shop:icon:update']" @click="handleMenuUpdate">
            <el-icon><Edit /></el-icon><span>修改</span>
          </li>
          <li @click="handleMenuCopy">
            <el-icon><DocumentCopy /></el-icon><span>复制图标编码</span>
          </li>
          <li v-permission="['shop:icon:delete']" class="icon-menu-danger" @click="handleMenuDelete">
            <el-icon><Delete /></el-icon><span>删除</span>
          </li>
        </ul>
      </template>
    </teleport>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listShopIconApi,
  detailShopIconApi,
  addShopIconApi,
  updateShopIconApi,
  deleteShopIconApi
} from '@/api/shop/icon'
import { getCategoryTreeApi } from '@/api/shop/category'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import ShopIcon from '@/components/ShopIcon/index.vue'
import ShopCategorySelect from '@/components/ShopCategorySelect/index.vue'
import UploadImage from '@/components/Upload/Image.vue'

const ICON_TYPES = [
  { value: 1, label: 'SVG' },
  { value: 2, label: '图片URL' },
  { value: 3, label: '字体类名' }
]

const loading = ref(true)
const submitLoading = ref(false)
const selectedIds = ref<any[]>([])
const total = ref(0)
const dataList = ref<any[]>([])
const title = ref('')
const open = ref(false)

// 右键操作栏
const menuVisible = ref(false)
const menuX = ref(0)
const menuY = ref(0)
const menuRow = ref<any>(null)

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// 查询参数
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 20,
  keyword: undefined,
  categoryId: undefined,
  iconType: undefined,
  status: undefined
})

// 表单参数
const form = reactive<any>({})

// 分类树选项
const categoryOptions = ref<any[]>([])

const rules = reactive<FormRules>({
  iconName: [{ required: true, message: '图标名称不能为空', trigger: 'blur' }],
  iconCode: [{ required: true, message: '图标编码不能为空', trigger: 'blur' }],
  iconType: [{ required: true, message: '请选择图标类型', trigger: 'change' }],
  iconValue: [{ required: true, message: '图标内容不能为空', trigger: 'blur' }]
})

/** 查询列表 */
const getList = async () => {
  closeMenu()
  selectedIds.value = []
  loading.value = true
  try {
    const { data } = await listShopIconApi(queryParams)
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

/** 切换类型时清空内容：SVG 源码当图片 URL 提交必然渲染不出来 */
const handleTypeChange = () => {
  form.iconValue = ''
}

/** 表单重置 */
const reset = () => {
  Object.assign(form, {
    id: undefined,
    iconName: undefined,
    iconCode: undefined,
    iconType: 1,
    iconValue: undefined,
    categoryId: undefined,
    sort: 0,
    status: 1,
    remark: undefined
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
  queryParams.iconType = undefined
  queryParams.status = undefined
  handleQuery()
}

/** 选中态：id 只存一份在 selectedIds 里，卡片和全选框都从它反推，免得两处状态对不上 */
const isSelected = (id: any) => selectedIds.value.some((item) => String(item) === String(id))

const toggleSelect = (row: any) => {
  const index = selectedIds.value.findIndex((item) => String(item) === String(row.id))
  if (index > -1) {
    selectedIds.value.splice(index, 1)
  } else {
    selectedIds.value.push(row.id)
  }
}

const checkAll = computed(() => dataList.value.length > 0 && selectedIds.value.length === dataList.value.length)
const isIndeterminate = computed(() => selectedIds.value.length > 0 && !checkAll.value)

/** 全选只覆盖当前这一页：翻页会清空选中 */
const handleCheckAll = (checked: any) => {
  selectedIds.value = checked ? dataList.value.map((item) => item.id) : []
}

/** 右键卡片：菜单贴着鼠标位置弹，靠右/靠下时往回收，免得被视口裁掉 */
const openMenu = (row: any, event: MouseEvent) => {
  menuRow.value = row
  menuX.value = Math.min(event.clientX, window.innerWidth - 168)
  menuY.value = Math.min(event.clientY, window.innerHeight - 128)
  menuVisible.value = true
}

const closeMenu = () => {
  menuVisible.value = false
}

/** 菜单项点击：先把记住的那一行取到局部变量，再关菜单 */
const handleMenuUpdate = async () => {
  const row = menuRow.value
  closeMenu()
  await handleUpdate(row)
}

const handleMenuDelete = () => {
  const row = menuRow.value
  closeMenu()
  handleDelete(row)
}

const handleMenuCopy = async () => {
  const code = menuRow.value.iconCode
  closeMenu()
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success('图标编码已复制')
  } catch (error) {
  }
}

/** 新增按钮操作 */
const handleAdd = () => {
  reset()
  open.value = true
  title.value = '添加图标'
}

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailShopIconApi(row.id)
    Object.assign(form, data)
    open.value = true
    title.value = '修改图标'
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
        await updateShopIconApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopIconApi(form)
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

/** 删除操作 */
const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确认删除图标「${row.iconName}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopIconApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除：deleteShopIconApi 收 id 数组，选中态靠 getList 清 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个图标吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopIconApi(selectedIds.value)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 翻页 */
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
    /* 四个筛选项加按钮，窄屏时靠 min-width 触发换行而不是挤成一团 */
    min-width: 220px;
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

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.card-header-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.icon-grid {
  display: grid;
  /* 5 列 × 每页 20 条 = 4 行，不做自适应，窄屏靠卡片内部省略号撑 */
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  min-height: 200px;
}

.icon-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  cursor: pointer;
  /* 整卡可点，连点选别把名称拖蓝 */
  user-select: none;
  transition: all 0.2s;

  &:hover {
    border-color: var(--el-color-primary);
    box-shadow: var(--el-box-shadow-light);
  }

  &.selected {
    border-color: var(--el-color-primary);
    background-color: var(--el-color-primary-light-9);
  }
}

.icon-card-check {
  position: absolute;
  top: 6px;
  left: 8px;
}

.icon-card-main {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 64px;
  margin-bottom: 10px;
}

.icon-card-name,
.icon-card-category {
  max-width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  text-align: center;
}

.icon-card-name {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.icon-card-category {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 遮罩吃掉菜单外的点击，层级压在菜单下面 */
.icon-menu-mask {
  position: fixed;
  inset: 0;
  z-index: 3000;
}

.icon-menu {
  position: fixed;
  z-index: 3001;
  min-width: 160px;
  margin: 0;
  padding: 5px 0;
  list-style: none;
  font-size: 14px;
  background-color: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  box-shadow: var(--el-box-shadow-light);

  li {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 16px;
    color: var(--el-text-color-regular);
    cursor: pointer;

    &:hover {
      background-color: var(--el-fill-color-light);
      color: var(--el-color-primary);
    }
  }

  .icon-menu-danger:hover {
    color: var(--el-color-danger);
  }
}

.icon-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88px;
  height: 88px;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
}

.icon-preview-tip {
  margin-left: 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
</style>
