<template>
  <div class="menu-container">
    <!-- 搜索表单：这一排只有一个输入框、没有提交按钮，回车会被浏览器隐式提交这个 <form> 刷掉页面，
         所以 el-form 上挡掉 submit 并当成搜索 -->
    <div class="search-wrapper">
      <el-form :model="queryParams" :inline="true" class="search-form" @submit.prevent="handleQuery">
        <el-form-item label="菜单名称" prop="title">
          <el-input v-model="queryParams.title" placeholder="请输入菜单名称" clearable />
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
            <el-button
              v-permission="['sys:menu:add']"
              type="primary"
              icon="Plus"
              @click="handleAdd(null)"
            >新增</el-button>
          </ButtonGroup>
        </div>
      </template>
      <!-- 表格区域（命中那条的下级由服务端一起带出来，默认收起，自己点开看） -->
      <el-table
        v-loading="loading"
        :data="menuList"
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      >
        <el-table-column label="菜单名称" prop="title" show-overflow-tooltip />
        <el-table-column label="图标" align="center" width="60">
          <template #default="{ row }">
            <!-- 图标库 id 和历史 el-icon 名两种格式都由 MenuIcon 分辨，这里不做判断 -->
            <menu-icon :icon="row.icon" />
          </template>
        </el-table-column>
        <el-table-column label="类型" align="center" width="80">
          <template #default="{ row }">
            <el-tag :type="row.type === MenuTypeEnum.CATALOG ? 'primary' : row.type === MenuTypeEnum.MENU ? 'success' : 'info'">
              {{ row.type === MenuTypeEnum.CATALOG ? '目录' : row.type === MenuTypeEnum.MENU ? '菜单' : '按钮' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="路由地址" prop="path" show-overflow-tooltip />
        <el-table-column label="组件路径" prop="component" show-overflow-tooltip />
        <el-table-column label="权限标识" prop="perm" show-overflow-tooltip />
        <el-table-column label="排序" prop="sort" width="60" align="center" />
     
        <el-table-column label="状态" align="center" width="80">
          <template #default="{ row }">
            <el-tag :type="row.hidden === 0 ? 'success' : 'info'">
              {{ row.hidden === 0 ? '显示' : '隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <el-button type="success" link @click="handleAdd(row)" v-permission="['sys:menu:add']">
              <el-icon><Plus /></el-icon>新增
            </el-button>
            <el-button type="primary" link @click="handleEdit(row)" v-permission="['sys:menu:update']">
              <el-icon><Edit /></el-icon>修改
            </el-button>
            <el-button type="danger" link @click="handleDelete(row)" v-permission="['sys:menu:delete']">
              <el-icon><Delete /></el-icon>删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加或修改菜单对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogType === 'add' ? '新增菜单' : '修改菜单'"
      width="700px"
      append-to-body
    >
      <el-form
        ref="menuFormRef"
        :model="menuForm"
        :rules="rules"
        label-width="100px"
      >
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="上级菜单" prop="parentId">
              <el-tree-select
                v-model="menuForm.parentId"
                :data="menuOptions"
                :props="{ label: 'title', value: 'id' }"
                value-key="id"
                placeholder="选择上级菜单"
                check-strictly
                :render-after-expand="false"
                class="flex-grow"
              />
            </el-form-item>
          </el-col>

          <el-col :span="24">
            <el-form-item label="菜单类型" prop="type">
              <el-radio-group v-model="menuForm.type" class="menu-type-group">
                <el-radio-button :value="MenuTypeEnum.CATALOG">目录</el-radio-button>
                <el-radio-button :value="MenuTypeEnum.MENU">菜单</el-radio-button>
                <el-radio-button :value="MenuTypeEnum.BUTTON">按钮</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="菜单名称" prop="title">
              <el-input v-model="menuForm.title" placeholder="请输入菜单名称" />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="排序" prop="sort">
              <el-input-number v-model="menuForm.sort" :min="1" class="flex-grow" />
            </el-form-item>
          </el-col>

          <el-col :span="24" v-if="menuForm.type !== MenuTypeEnum.BUTTON">
            <el-form-item label="图标">
              <!-- 输入框里显示的是图标名称：这一格存的是 sys_icon.id，直接把值给人看就是一串数字 -->
              <el-input :model-value="iconText" placeholder="点击选择图标" readonly clearable @clear="handleIconClear">
                <template #prefix>
                  <menu-icon :icon="menuForm.icon" />
                </template>
                <template #append>
                  <el-button @click="showIconSelect = true">
                    <el-icon><View /></el-icon>选择图标
                  </el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>

          <el-col :span="24" v-if="menuForm.type !== MenuTypeEnum.BUTTON">
            <el-form-item label="路由地址" prop="path">
              <el-input v-model="menuForm.path" placeholder="请输入路由地址" />
            </el-form-item>
          </el-col>

          <el-col :span="24" >
            <el-form-item label="重定向地址" prop="redirect">
              <el-input v-model="menuForm.redirect" placeholder="请输入重定向地址" />
            </el-form-item>
          </el-col>

          <el-col :span="24" v-if="menuForm.type === MenuTypeEnum.MENU">
            <el-form-item label="页面路径" prop="component">
              <el-input v-model="menuForm.component" placeholder="/system/user/index">
                <template #prepend>src/views</template>
                <template #append>.vue</template>
              </el-input>
            </el-form-item>
          </el-col>

          <el-col :span="24" v-if="menuForm.type === MenuTypeEnum.BUTTON">
            <el-form-item label="权限标识" prop="perm">
              <el-input v-model="menuForm.perm" placeholder="请输入权限标识" />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="状态" prop="hidden">
              <el-radio-group v-model="menuForm.hidden">
                <el-radio :value="0">显示</el-radio>
                <el-radio :value="1">隐藏</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="是否外链" prop="isExternal">
              <el-radio-group v-model="menuForm.isExternal">
                <el-radio :value="0">否</el-radio>
                <el-radio :value="1">是</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取 消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 公共图标选择器：菜单这一档默认落在「系统图标」，从商品分类/其它档挑也行 -->
    <shop-icon-select v-model="menuForm.icon" v-model:visible="showIconSelect" :icon-category="1"
      @select="handleIconSelect" />
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import MenuIcon from '@/components/MenuIcon/index.vue'
import ShopIconSelect from '@/components/ShopIconSelect/index.vue'
import { iconRecord } from '@/utils/iconLibrary'
import {
  getMenuListApi,
  createMenuApi,
  updateMenuApi,
  deleteMenuApi,
} from '@/api/system/menu'
import { MenuTypeEnum } from "@/enum/MenuTypeEnum";

const loading = ref(false)
const dialogVisible = ref(false)
const dialogType = ref<'add' | 'edit'>('add')
const menuFormRef = ref<FormInstance>()
const showIconSelect = ref(false)
const submitLoading = ref(false)

// 筛选条件：只按菜单名称模糊查（服务端 LIKE）
const queryParams = reactive({
  title: ''
})

// 表单校验规则
const rules = {
  parentId: [
    { required: true, message: '请选择父级菜单', trigger: 'blur' }
  ],
  title: [
    { required: true, message: '请输入菜单名称', trigger: 'blur' }
  ],
  sort: [
    { required: true, message: '请输入排序', trigger: 'blur' }
  ],
  path: [
    { required: true, message: '请输入路由地址', trigger: 'blur' }
  ],
  component: [
    { required: false, message: '请输入页面路径', trigger: 'blur' }
  ],
  perm: [
    { required: true, message: '请输入权限标识', trigger: 'blur' }
  ],
  hidden: [
    { required: true, message: '请选择状态', trigger: 'blur' }
  ]
}

// 菜单表单对象
const menuForm = reactive<any>({
  id: undefined,
  parentId: 0,
  name: '',
  sort: 0,
  path: '',
  component: '',
  redirect:'',
  type: MenuTypeEnum.CATALOG,
  perms: '',
  icon: '',
  hidden: 0,
  isExternal: 0
})

// 模拟菜单数据
const menuList = ref<any>([])

/**
 * 图标输入框显示的名称：这一格存的是 sys_icon.id（历史数据是 el-icon 名），
 * 给人看 id 没意义。本次弹窗里选中的那条由 handleIconSelect 记住，
 * 回显（打开修改弹窗）时图标库里查得到就用库里的名字，查不到就把原值显示出来。
 */
const selectedIconName = ref('')
const iconText = computed(() => {
  const value = menuForm.icon
  if (!value) return ''
  if (selectedIconName.value) return selectedIconName.value
  const record = /^\d+$/.test(String(value)) ? iconRecord(String(value)) : undefined
  if (record) return record.iconName
  return String(value)
})

const handleIconSelect = (icon: any) => {
  // 选择器回的是数字 id，sys_menu.icon 是 varchar，统一存成字符串（后端实体那一格是 String）
  menuForm.icon = String(icon.id)
  selectedIconName.value = icon.iconName
}

const handleIconClear = () => {
  menuForm.icon = ''
  selectedIconName.value = ''
}

// 菜单树选项
const menuOptions = ref<any>([])

// 查询方法
const getList = async () => {
  loading.value = true
  try {
    const { data } = await getMenuListApi(queryParams.title ? { title: queryParams.title } : {})
    menuList.value = data
    // 「上级菜单」候选要的是完整树：只在没筛选的那一次拉取里覆盖，否则搜一次之后弹窗里就选不到被筛掉的父级了
    if (!queryParams.title) {
      menuOptions.value = [{ id: 0, title: '顶级菜单', children: data }]
    }
  } catch (error) {
  }
  loading.value = false
}

// 提交表单
const submitForm = async () => {
  if (!menuFormRef.value) return
  
  await menuFormRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        const formData = { ...menuForm }

        if (dialogType.value === 'add') {
          await createMenuApi(formData)
          ElMessage.success('新增成功')
        } else {
          await updateMenuApi(formData)
          ElMessage.success('修改成功')
        }
        dialogVisible.value = false
        getList()
      } catch (error) {
      } finally {
        submitLoading.value = false
      }
    }
  })
}

// 删除菜单
const handleDelete = (row: any) => {
  ElMessageBox.confirm(
    `确定要删除菜单"${row.title}"吗？`,
    '警告',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await deleteMenuApi(row.id)
      ElMessage.success('删除成功')
      getList()
    } catch (error) {
    }
  }).catch(() => {})
}

// 查询
const handleQuery = () => {
  getList()
}
// 重置筛选条件
const resetQuery = () => {
  queryParams.title = ''
  getList()
}
// 重置表单
const resetForm = () => {
  menuForm.id = undefined
  menuForm.parentId = 0
  menuForm.title = ''
  menuForm.sort = 0
  menuForm.path = ''
  menuForm.redirect = ''
  menuForm.component = ''
  menuForm.type = MenuTypeEnum.CATALOG
  menuForm.perm = ''
  menuForm.icon = ''
  selectedIconName.value = ''
  menuForm.hidden = 0,
  menuForm.isExternal = 0
}

// 新增菜单
const handleAdd = (row?: any) => {
  resetForm()
  if (row) {
    menuForm.parentId = row.id
  }
  dialogType.value = 'add'
  dialogVisible.value = true
}

// 修改菜单
const handleEdit = (row: any) => {
  resetForm()
  dialogType.value = 'edit'
  dialogVisible.value = true
  Object.assign(menuForm, { ...row })
}

// 在组件挂载时获取数据
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