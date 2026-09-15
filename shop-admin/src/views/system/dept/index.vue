<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="请输入部门名称或编码" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="所属部门">
          <DeptSelect v-model="queryParams.parentIds" placeholder="请选择所属部门" style="width: 220px" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 操作按钮区域 -->
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <ButtonGroup>
            <el-button v-permission="['sys:dept:add']" type="primary" icon="Plus" @click="handleAdd()">新增</el-button>
          </ButtonGroup>
        </div>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="deptList" row-key="id" :tree-props="{ children: 'children' }"
        default-expand-all>
        <el-table-column label="部门名称" prop="name" width="200" show-overflow-tooltip />
        <el-table-column label="部门编码" prop="code" align="center" width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.code || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="负责人" prop="leaderName" align="center" width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.leaderName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="人数" align="center" width="90">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="handleMembers(row)">
              {{ row.userCount ?? 0 }}
            </el-link>
          </template>
        </el-table-column>
        <!-- <el-table-column label="排序" prop="sortOrder" align="center" width="100" /> -->
        <el-table-column label="状态" align="center" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" align="center" width="170">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.createTime, 'YYYY-MM-DD') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="280" fixed="right">
          <template #default="scope">
            <TableMoreActions :actions="[
              {
                label: '修改',
                icon: 'Edit',
                disabled: !hasPermission('sys:dept:update'),
                command: { type: 'edit', row: scope.row }
              },
              {
                label: '人员',
                icon: 'User',
                disabled: !hasPermission('sys:dept:user:list'),
                command: { type: 'members', row: scope.row }
              },
              {
                label: '添加子级部门',
                icon: 'Plus',
                disabled: !hasPermission('sys:dept:add'),
                command: { type: 'addChild', row: scope.row }
              },
              {
                label: '删除',
                type: 'danger',
                icon: 'Delete',
                disabled: !hasPermission('sys:dept:delete'),
                command: { type: 'delete', row: scope.row }
              }
            ]" @command="handleActionCommand" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加或修改部门对话框 -->
    <el-dialog :title="dialog.title" v-model="dialog.visible" width="600px" append-to-body destroy-on-close>
      <el-form ref="deptFormRef" :model="deptForm" :rules="rules" label-width="100px">
        <el-form-item label="上级部门" prop="parentId">
          <el-tree-select v-model="deptForm.parentId" :data="parentOptions" :props="{ label: 'name', value: 'id' }"
            value-key="id" placeholder="选择上级部门" check-strictly :render-after-expand="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="部门名称" prop="name">
          <el-input v-model="deptForm.name" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="部门编码" prop="code">
          <el-input v-model="deptForm.code" placeholder="请输入部门编码" disabled />
        </el-form-item>
        <el-form-item label="负责人" prop="leaderId">
          <UserSelect v-model="deptForm.leaderId" :label="deptLeaderName" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="deptForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="deptForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cancel">取 消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 部门人员弹窗 -->
    <el-dialog :title="memberDialog.title" v-model="memberDialog.visible" width="700px" append-to-body destroy-on-close>
      <div class="member-toolbar">
        <el-button v-if="hasPermission('sys:dept:user:add')" type="primary" icon="Plus"
          @click="openAddMemberDialog">添加人员</el-button>
      </div>
      <el-table v-loading="memberLoading" :data="memberList" style="width: 100%">
        <el-table-column label="昵称" prop="nickname" align="center" show-overflow-tooltip />
        <el-table-column label="用户名" prop="username" align="center" show-overflow-tooltip />
        <el-table-column label="手机号" prop="mobile" align="center" width="140">
          <template #default="{ row }">
            <span>{{ row.mobile || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-container">
        <el-pagination v-model:current-page="memberQuery.pageNum" v-model:page-size="memberQuery.pageSize"
          :page-sizes="[10, 20, 30, 50]" :total="memberTotal" :background="true"
          layout="total, sizes, prev, pager, next, jumper" @size-change="getMembers" @current-change="getMembers" />
      </div>
    </el-dialog>

    <!-- 添加部门人员弹窗 -->
    <el-dialog v-model="addMemberDialog.visible" title="添加人员" width="700px" append-to-body destroy-on-close>
      <div class="search-wrapper">
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="关键字">
            <el-input v-model="addUserQuery.keyword" placeholder="请输入用户名/昵称/账号搜索" clearable
              @keyup.enter="getAddUserList" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="getAddUserList">搜索</el-button>
            <el-button icon="Refresh" @click="resetAddUserQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
      <el-table ref="addUserTableRef" v-loading="addUserLoading" :data="addUserList" height="360"
        @row-click="handleAddUserRowClick" @selection-change="handleAddUserSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column label="昵称" prop="nickname" align="center" show-overflow-tooltip />
        <el-table-column label="用户名" prop="username" align="center" show-overflow-tooltip />
        <el-table-column label="手机号" prop="mobile" align="center" width="140">
          <template #default="{ row }">
            <span>{{ row.mobile || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-container">
        <el-pagination v-model:current-page="addUserQuery.pageNum" v-model:page-size="addUserQuery.pageSize"
          :page-sizes="[10, 20, 30, 50]" :total="addUserTotal" :background="true"
          layout="total, sizes, prev, pager, next, jumper" @size-change="getAddUserList"
          @current-change="getAddUserList" />
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="addMemberLoading" @click="handleAddMembers">确 定</el-button>
          <el-button @click="addMemberDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getDeptTreeApi,
  createDeptApi,
  updateDeptApi,
  deleteDeptApi,
  getDeptUsersApi,
  addDeptUsersApi
} from '@/api/system/dept'
import { getUserListApi } from '@/api/system/user'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import DeptSelect from '@/components/DeptSelect/index.vue'
import TableMoreActions from '@/components/TableMoreActions/index.vue'
import validate from '@/utils/validate'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const permissions = computed(() => userStore.user.permissions || [])

// 权限检查
const hasPermission = (permission: string): boolean => {
  return permissions.value.includes(permission)
}

// 查询参数
const queryParams = reactive({
  keyword: '',
  status: '',
  parentIds: [] as number[]
})

const loading = ref(false)
const deptList = ref<any[]>([])
const queryFormRef = ref<FormInstance>()
const deptFormRef = ref<FormInstance>()
const submitLoading = ref(false)

// 弹窗控制
const dialog = reactive({
  title: '',
  visible: false,
  type: 'add' as 'add' | 'edit'
})

// 部门表单
const deptForm = reactive<any>({
  id: undefined,
  parentId: 0,
  name: '',
  code: '',
  leaderId: undefined,
  status: 1,
  sortOrder: 0
})

// 表单校验规则
const rules = reactive<FormRules>({
  name: [
    { required: true, message: '请输入部门名称', trigger: 'blur' }
  ],
  //code: [
  //  { required: true, message: '请输入部门编码', trigger: 'blur' }
  //]
})

// 上级部门选项（含顶级部门）
const parentOptions = ref<any[]>([])

// 负责人名称（编辑回显）
const deptLeaderName = ref('')

// 部门人员弹窗
const memberDialog = reactive({
  visible: false,
  title: '',
  deptId: undefined as number | undefined
})
const memberLoading = ref(false)
const memberList = ref<any[]>([])
const memberTotal = ref(0)
const memberQuery = reactive({
  pageNum: 1,
  pageSize: 10
})

// 添加部门人员弹窗
const addMemberDialog = reactive({
  visible: false
})
const addMemberLoading = ref(false)
const addUserLoading = ref(false)
const addUserTableRef = ref()
const addUserList = ref<any[]>([])
const addUserTotal = ref(0)
const addUserQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  // 关键字：匹配用户名/昵称/账号
  keyword: '',
  // 排除的部门ID：过滤掉已在该部门下的人员
  excludeDeptId: null
})
const selectedAddUsers = ref<any[]>([])

// 打开添加人员弹窗
const openAddMemberDialog = () => {
  selectedAddUsers.value = []
  addUserQuery.pageNum = 1
  addUserQuery.keyword = ''
  // 过滤掉已在该部门下的人员
  addUserQuery.excludeDeptId = memberDialog.deptId
  getAddUserList()
  addMemberDialog.visible = true
}

// 获取可添加的用户列表
const getAddUserList = async () => {
  addUserLoading.value = true
  try {
    const { data } = await getUserListApi(addUserQuery)
    addUserList.value = data.records || []
    addUserTotal.value = data.total || 0
  } catch (error) {
  } finally {
    addUserLoading.value = false
  }
}

// 重置用户搜索
const resetAddUserQuery = () => {
  addUserQuery.keyword = ''
  addUserQuery.pageNum = 1
  // 保持排除当前部门人员
  addUserQuery.excludeDeptId = memberDialog.deptId
  getAddUserList()
}

// 多选变化
const handleAddUserSelectionChange = (rows: any[]) => {
  selectedAddUsers.value = rows
}

// 行点击切换选中（支持点击整行勾选/取消勾选）
const handleAddUserRowClick = (row: any) => {
  addUserTableRef.value?.toggleRowSelection(row)
}

// 确认添加部门人员
const handleAddMembers = async () => {
  if (!selectedAddUsers.value.length) {
    ElMessage.warning('请选择要添加的人员')
    return
  }
  addMemberLoading.value = true
  try {
    await addDeptUsersApi(memberDialog.deptId!, selectedAddUsers.value.map((u) => u.id))
    ElMessage.success('添加成功')
    addMemberDialog.visible = false
    getMembers()
    getList()
  } catch (error) {
  } finally {
    addMemberLoading.value = false
  }
}

// 获取部门树
const getList = async () => {
  loading.value = true
  try {
    const params: any = { ...queryParams }
    if (params.parentIds && params.parentIds.length) {
      params.parentIds = params.parentIds.join(',')
    }
    const { data } = await getDeptTreeApi(params)
    deptList.value = data
  } catch (error) {
  }
  loading.value = false
}

// 构建上级部门选项（编辑时排除自身及其子部门，防止循环引用）
const buildParentOptions = (tree: any[], excludeId?: number) => {
  if (!tree || tree.length === 0) return []
  return tree
    .filter((node) => node.id !== excludeId) // 排除自身及其子部门
    .map((node) => ({
      ...node,
      children: buildParentOptions(node.children, excludeId)
    }))
}

const getParentOptions = async (excludeId?: number) => {
  try {
    const { data } = await getDeptTreeApi({})
    parentOptions.value = [
      { id: 0, name: '顶级部门', children: buildParentOptions(data, excludeId) }
    ]
  } catch (error) {
  }
}

// 新增部门（parentId 用于“添加子级部门”时指定上级）
const handleAdd = (parentId = 0) => {
  dialog.type = 'add'
  dialog.title = parentId ? '新增子部门' : '新增部门'
  Object.assign(deptForm, {
    id: undefined,
    parentId,
    name: '',
    code: '',
    leaderId: undefined,
    status: 1,
    sortOrder: 0
  })
  deptLeaderName.value = ''
  getParentOptions()
  dialog.visible = true
}

// 添加子级部门
const handleAddChild = (row: any) => {
  handleAdd(row.id)
}

// 操作命令分发
const handleActionCommand = async (action: any) => {
  const { type, row } = action.command
  switch (type) {
    case 'edit':
      handleUpdate(row)
      break
    case 'members':
      handleMembers(row)
      break
    case 'addChild':
      handleAddChild(row)
      break
    case 'delete':
      handleDelete(row)
      break
  }
}

// 修改部门
const handleUpdate = (row: any) => {
  dialog.type = 'edit'
  dialog.title = '修改部门'
  Object.assign(deptForm, {
    id: row.id,
    parentId: row.parentId || 0,
    name: row.name,
    code: row.code || '',
    leaderId: row.leaderId,
    status: row.status,
    sortOrder: row.sortOrder || 0
  })
  deptLeaderName.value = row.leaderName || ''
  getParentOptions(row.id)
  dialog.visible = true
}

// 提交表单
const submitForm = async () => {
  if (!deptFormRef.value) return
  await deptFormRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        if (dialog.type === 'add') {
          await createDeptApi(deptForm)
          ElMessage.success('新增成功')
        } else {
          await updateDeptApi(deptForm)
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

// 删除部门
const handleDelete = (row: any) => {
  ElMessageBox.confirm(`是否确认删除部门"${row.name}"?`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteDeptApi(row.id)
      ElMessage.success('删除成功')
      getList()
    } catch (error) {
    }
  }).catch(() => { })
}

// 查看部门人员
const handleMembers = (row: any) => {
  memberDialog.deptId = row.id
  memberDialog.title = `${row.name} - 部门人员`
  memberDialog.visible = true
  memberQuery.pageNum = 1
  getMembers()
}

// 获取部门人员
const getMembers = async () => {
  if (!memberDialog.deptId) return
  memberLoading.value = true
  try {
    const { data } = await getDeptUsersApi(memberDialog.deptId, memberQuery)
    memberList.value = data.records
    memberTotal.value = data.total
  } catch (error) {
  } finally {
    memberLoading.value = false
  }
}

// 搜索
const handleQuery = () => {
  getList()
}

// 重置查询
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.keyword = ''
  queryParams.parentIds = []
  handleQuery()
}

// 取消
const cancel = () => {
  dialog.visible = false
  deptFormRef.value?.resetFields()
}

onMounted(() => {
  getList()
})
</script>

<style scoped lang="scss">
/* 部门人员弹窗工具栏 */
.member-toolbar {
  margin-bottom: 12px;
}

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
  :deep(.el-form-item .el-select),
  :deep(.el-form-item .el-tree-select),
  :deep(.el-form-item .dept-select),
  :deep(.el-form-item .user-select) {
    width: 100% !important;
  }
}
</style>
