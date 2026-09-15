<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input
            v-model="queryParams.keyword"
            placeholder="请输入用户名/昵称/账号"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="登录方式" prop="loginType">
          <el-select v-model="queryParams.loginType" placeholder="请选择登录方式" clearable>
            <el-option v-for="item in loginTypes" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属部门">
          <DeptSelect
            v-model="queryParams.deptIds"
            placeholder="请选择所属部门"
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
            <el-option label="启用" value="1" />
            <el-option label="禁用" value="0" />
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
            <el-button
              v-permission="['sys:user:add']"
              type="primary"
              icon="Plus"
              @click="handleAdd"
            >新增</el-button>
            <el-button
             v-permission="['sys:user:delete']"
              type="danger"
              icon="Delete"
              :disabled="selectedIds.length === 0"
              @click="handleBatchDelete"
            >批量删除</el-button>
            <el-button
              type="success"
              icon="Upload"
              v-permission="['sys:user:import']"
              @click="openImportDialog"
            >导入</el-button>
            <el-button
              type="warning"
              icon="Download"
              v-permission="['sys:user:export']"
              :loading="exportLoading"
              @click="handleExport"
            >导出</el-button>
          </ButtonGroup>
        </div>
      </template>

      <!-- 数据表格 -->
      <el-table
        v-loading="loading"
        :data="userList"
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection"  width="55" align="center" />
        <el-table-column label="头像"  prop="avatar" align="center">
          <template #default="{ row }">
            <el-image :src="row.avatar" style="width: 40px; height: 40px; border-radius: 5px;" />
          </template>
        </el-table-column>
        <el-table-column label="二维码" prop="qrImg" align="center" width="90">
          <template #default="{ row }">
            <el-image
              v-if="row.qrImg"
              :src="row.qrImg"
              style="width: 40px; height: 40px; border-radius: 4px;"
              :preview-src-list="[row.qrImg]"
              preview-teleported
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="昵称" align="center" prop="nickname" show-overflow-tooltip />
        <el-table-column label="登录方式" align="center" prop="ipLocation" >
          <template #default="{ row }">
            <span v-for="item in loginTypes">
                <el-tag :type="item.style" v-if="row.loginType === item.value">
                  {{ item.label}}
              </el-tag>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="部门" align="center" prop="deptNames" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ (row.deptNames || []).join(' / ') || '-' }}</span>
          </template>
        </el-table-column>
        <!-- <el-table-column label="登录IP" align="center" prop="ip" show-overflow-tooltip /> -->
        <!-- <el-table-column label="登录地址" align="center" prop="ipLocation" show-overflow-tooltip /> -->
        <el-table-column label="地址" width="150" align="center" prop="areaCode" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.areaZh || resolveAreaCode(row.areaCode) || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后登录时间" align="center" prop="lastLoginTime" width="160">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.lastLoginTime, 'YYYY-MM-DD HH') || '-' }}</span>
          </template>
        </el-table-column>
        <!-- <el-table-column label="创建时间" align="center" prop="createTime" width="160" /> -->
        <el-table-column label="操作" align="center" width="280" fixed="right">
          <template #default="scope">
            <TableMoreActions
              :actions="[
                {
                  label: '修改',
                  icon: 'Edit',
                  disabled: !hasPermission('sys:user:update'),
                  command: { type: 'edit', row: scope.row }
                },
                {
                  label: '二维码',
                  type: 'warning',
                  icon: 'Link',
                  disabled: !hasPermission('sys:user:update'),
                  command: { type: 'qr', row: scope.row }
                },
                {
                  label: '重置密码',
                  type: 'info',
                  icon: 'Key',
                  disabled: !hasPermission('sys:user:reset'),
                  command: { type: 'resetPwd', row: scope.row }
                },
                {
                  label: '删除',
                  type: 'danger',
                  icon: 'Delete',
                  disabled: !hasPermission('sys:user:delete'),
                  command: { type: 'delete', row: scope.row }
                }
              ]"
              @command="handleActionCommand"
            />
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页组件 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 30, 50]"
          :total="total"
          :background="true"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 添加或修改用户对话框 -->
    <el-dialog
      :title="dialog.title"
      v-model="dialog.visible"
      width="600px"
      append-to-body
      destroy-on-close
      class="custom-dialog"
    >
      <el-form
        ref="userFormRef"
        :model="userForm"
        :rules="rules"
        label-width="80px"
        class="custom-form"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input 
                v-model="userForm.username" 
                placeholder="请输入用户名" 
                :disabled="dialog.type === 'edit'"
                clearable
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="昵称" prop="nickname">
              <el-input 
                v-model="userForm.nickname" 
                placeholder="请输入昵称"
                clearable 
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="手机号" prop="mobile">
              <el-input 
                v-model="userForm.mobile" 
                placeholder="请输入手机号"
                clearable 
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input 
                v-model="userForm.email" 
                placeholder="请输入邮箱"
                clearable 
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="性别" prop="sex">
              <el-radio-group v-model="userForm.sex">
                <el-radio :value="1">男</el-radio>
                <el-radio :value="2">女</el-radio>
                <el-radio :value="0">保密</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="密码" prop="password" v-if="dialog.type === 'add'">
              <el-input 
                v-model="userForm.password" 
                type="password" 
                placeholder="请输入密码"
                show-password
                clearable
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="角色" prop="roleIds">
          <el-select
            v-model="userForm.roleIds"
            multiple
            placeholder="请选择角色"
            style="width: 100%"
            :disabled="userForm.username === 'admin'"
            clearable
      
          >
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="role.name"
              :value="role.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="部门" prop="deptIds">
          <DeptSelect v-model="userForm.deptIds" style="width: 100%" />
        </el-form-item>

        <el-form-item label="地址" prop="areaCode">
          <el-cascader
            v-model="areaCodeArr"
            :options="colPickerData"
            :props="{ label: 'text', value: 'value', children: 'children' }"
            style="width: 100%"
            clearable
            placeholder="请选择所在地区"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="userForm.status">
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

    <!-- 添加重置密码弹窗 -->
    <el-dialog
      title="重置密码"
      v-model="resetPwdDialog.visible"
      width="500px"
      append-to-body
      destroy-on-close
      class="custom-dialog"
    >
      <el-form
        ref="resetPwdFormRef"
        :model="resetPwdForm"
        :rules="resetPwdRules"
        label-width="100px"
      >
        <el-form-item label="新密码" prop="password">
          <el-input
            v-model="resetPwdForm.password"
            type="password"
            placeholder="请输入新密码"
            show-password
            clearable
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="resetPwdForm.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
            clearable
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="resetPwdDialog.visible = false">取 消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitResetPwd">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 二维码弹窗 -->
    <el-dialog
      title="用户二维码"
      v-model="qrDialog.visible"
      width="360px"
      append-to-body
      destroy-on-close
    >
      <div style="text-align: center;">
        <el-image
          v-if="qrDialog.url"
          :src="qrDialog.url"
          style="width: 240px; height: 240px; border: 1px solid #eee; border-radius: 8px;"
          fit="contain"
        />
        <el-empty v-else description="正在生成…" :image-size="60" />
        <p v-if="qrDialog.nickname" style="color: #666; margin-top: 8px;">{{ qrDialog.nickname }}</p>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="regenerateQr">重新生成</el-button>
          <el-button @click="qrDialog.visible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 导入用户弹窗 -->
    <el-dialog
      title="导入用户"
      v-model="importDialog.visible"
      width="480px"
      append-to-body
      destroy-on-close
      class="custom-dialog"
      @closed="resetImport"
    >
      <el-upload
        ref="importUploadRef"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
        :on-exceed="handleExceed"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击选择文件</em></div>
        <template #tip>
          <div class="el-upload__tip">
            支持 .xlsx / .xls 格式的 Excel 文件，可先「导出用户」获取模板。
          </div>
        </template>
      </el-upload>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="importDialog.visible = false">取 消</el-button>
          <el-button type="primary" :loading="importLoading" :disabled="!importFile" @click="submitImport">开始导入</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getUserListApi,
  createUserApi,
  updateUserApi,
  deleteUserApi,
  resetPasswordApi,
  generateUserQrApi,
  exportUserApi,
  importUserApi
} from '@/api/system/user'
import { saveAs } from 'file-saver'
import { getAllRoleList } from '@/api/system/role'
import { getDictDataByDictTypesApi } from '@/api/system/dict'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import TableMoreActions from '@/components/TableMoreActions/index.vue'
import DeptSelect from '@/components/DeptSelect/index.vue'
import { useUserStore } from '@/store/modules/user'
import validate from '@/utils/validate'

const userStore = useUserStore()
const permissions = computed(() => userStore.user.permissions || [])

// 权限检查
const hasPermission = (permission: string): boolean => {
  return permissions.value.includes(permission)
}

// 查询参数
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  status: '',
  loginType: '',
  deptIds: [] as number[]
})

const loading = ref(false)
const total = ref(0)
const userList = ref([])
const queryFormRef = ref<FormInstance>()
const userFormRef = ref<FormInstance>()
const submitLoading = ref(false)

// 选中项数组
const selectedIds = ref<string[]>([])

// 弹窗控制
const dialog = reactive({
  title: '',
  visible: false,
  type: 'add'
})

// 角色选项
const roleOptions = ref<any[]>([])

// 省市区数据
const { colPickerData, resolveAreaCode } = useColPickerData()
const areaCodeArr = ref<string[]>([])

// 表单数据
const userForm = reactive({
  id: undefined,
  username: '',
  nickname: '',
  password: null,
  mobile: '',
  email: '',
  areaCode: '',
  areaZh: '',
  sex: 0,
  status: 1,
  ip: undefined,
  ipLocation: undefined,
  lastLoginTime: undefined,
  createTime: undefined,
  roleIds: [] as number[],
  deptIds: [] as number[]
})

// 表单校验规则
const rules = reactive<FormRules>({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  mobile: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  roleIds: [
    { required: true, message: '请选择角色', trigger: 'change',validator: (rule, value, callback) => {
      if (userForm.username === 'admin') {
        callback()
      } else if (value.length === 0) {
        callback(new Error('请选择角色'))
      } else {
        callback()
      }
    } }
  ],
  sex: [
    { required: true, message: '请选择性别', trigger: 'change' }
  ]
})

// 重置密码弹窗控制
const resetPwdDialog = reactive({
  id: undefined,
  visible: false,
  userId: undefined
})

// 重置密码表单
const resetPwdForm = reactive({
  password: '',
  confirmPassword: ''
})

// 重置密码表单校验规则
const resetPwdRules = reactive<FormRules>({
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== resetPwdForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
})

const resetPwdFormRef = ref<FormInstance>()

// 二维码弹窗
const qrDialog = reactive({
  id: undefined,
  visible: false,
  url: '',
  nickname: ''
})

const loginTypes = ref<any>([])

// 导入用户
const importDialog = reactive({ visible: false })
const importUploadRef = ref<any>()
const importFile = ref<any>(null)
const importLoading = ref(false)

// 导出用户 loading
const exportLoading = ref(false)

// 获取用户列表
const getList = async () => {
  loading.value = true
  try {
    const params: any = { ...queryParams }
    if (params.deptIds && params.deptIds.length) {
      params.deptIds = params.deptIds.join(',')
    }
    const { data } = await getUserListApi(params)
    userList.value = data.records
    total.value = data.total
  } catch (error) {
  }
  loading.value = false
}

// 表格选择项变化
const handleSelectionChange = (selection: any[]) => {
  selectedIds.value = selection.map(item => item.id)
}

// 批量删除
const handleBatchDelete = () => {
  if (selectedIds.value.length === 0) return
  
  ElMessageBox.confirm('是否确认批量删除选中的用户?', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteUserApi(selectedIds.value)
      ElMessage.success('批量删除成功')
      getList()
      selectedIds.value = []
    } catch (error) {
    }
  })
}

// 搜索
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置查询
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.deptIds = []
  handleQuery()
}

// 新增用户
const handleAdd = () => {
  dialog.type = 'add'
  dialog.title = '新增用户'
  dialog.visible = true
  userForm.id = undefined
  userForm.username = ''
  userForm.nickname = ''
  userForm.password = null
  userForm.mobile = ''
  userForm.email = ''
  userForm.sex = 0
  userForm.status = 1
  userForm.ip = undefined
  userForm.ipLocation = undefined
  userForm.lastLoginTime = undefined
  userForm.createTime = undefined
  userForm.areaCode = ''
  userForm.areaZh = ''
  userForm.roleIds = []
  userForm.deptIds = []
  areaCodeArr.value = []
}

// 修改用户
const handleUpdate = (row: any) => {
  Object.assign(userForm, row)
  if(row.roles?.length > 0) {
    userForm.roleIds  = row.roles[0].split(',').map((item: string) => parseInt(item))
  }
  userForm.deptIds = row.deptIds || []
  userForm.password = null
  areaCodeArr.value = (row.areaCode || '').split(',').filter(Boolean)
  dialog.type = 'edit'
  dialog.title = '修改用户'
  dialog.visible = true

}

// 提交表单
const submitForm = async () => {
  if (!userFormRef.value) return
  
  await userFormRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        userForm.areaCode = areaCodeArr.value.join(',')
        userForm.areaZh = resolveAreaCode(userForm.areaCode)
        const data = {user: userForm, roleIds: userForm.roleIds, deptIds: userForm.deptIds}
        if (dialog.type === 'add') {
          await createUserApi(data)
          ElMessage.success('新增成功')
        } else {
          await updateUserApi(data)
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

// 删除用户
const handleDelete = (row: any) => {
  ElMessageBox.confirm(`是否确认删除用户"${row.username}"?`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteUserApi(row.id)
      ElMessage.success('删除成功')
      getList()
    } catch (error) {
    }
  })
}

// 操作命令分发
const handleActionCommand = async (action: any) => {
  const { type, row } = action.command
  switch (type) {
    case 'edit':
      handleUpdate(row)
      break
    case 'qr':
      handleGenerateQr(row)
      break
    case 'resetPwd':
      handleResetPwd(row)
      break
    case 'delete':
      handleDelete(row)
      break
  }
}

// 修改重置密码方法
const handleResetPwd = (row: any) => {
  resetPwdDialog.id = row.id
  resetPwdDialog.visible = true
  resetPwdForm.password = ''
  resetPwdForm.confirmPassword = ''
}

// 生成用户二维码
const handleGenerateQr = async (row: any) => {
  qrDialog.id = row.id
  qrDialog.nickname = row.nickname || row.username || ''
  qrDialog.url = row.qrImg || ''
  qrDialog.visible = true
  if (row.qrImg) {
    // 已有二维码直接展示，可在弹窗内重新生成
    return
  }
  try {
    const { data } = await generateUserQrApi(row.id)
    qrDialog.url = data
    ElMessage.success('二维码生成成功')
    getList()
  } catch (error) {
  }
}

// 重新生成二维码
const regenerateQr = async () => {
  if (!qrDialog.id) return
  qrDialog.url = ''
  try {
    const { data } = await generateUserQrApi(qrDialog.id)
    qrDialog.url = data
    ElMessage.success('二维码已重新生成')
    getList()
  } catch (error) {
  }
}

// 提交重置密码
const submitResetPwd = async () => {
  if (!resetPwdFormRef.value) return
  
  await resetPwdFormRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        await resetPasswordApi({
          id: resetPwdDialog.id,
          password: resetPwdForm.password
        })
        ElMessage.success('重置密码成功')
        resetPwdDialog.visible = false
      } catch (error) {
      } finally {
        submitLoading.value = false
      }
    }
  })
}

// 取消按钮
const cancel = () => {
  dialog.visible = false
  userFormRef.value?.resetFields()
}

// 分页大小改变
const handleSizeChange = (val: number) => {
  queryParams.pageSize = val
  getList()
}

// 页码改变
const handleCurrentChange = (val: number) => {
  queryParams.pageNum = val
  getList()
}

// 获取角色列表
const getRoleOptions = async () => {
  try {
    const { data } = await getAllRoleList()

    roleOptions.value = data
  } catch (error) {
  }
}
const getDicts = async () => {
  try {
    const { data } = await getDictDataByDictTypesApi(['login_type'])
    loginTypes.value = data.login_type.list
  } catch (error) {
  }
}

// 导出用户：勾选时仅导出勾选的行，否则按当前查询条件导出
const handleExport = async () => {
  exportLoading.value = true
  try {
    const params: any = { ...queryParams }
    if (params.deptIds && params.deptIds.length) {
      params.deptIds = params.deptIds.join(',')
    }
    if (selectedIds.value.length > 0) {
      params.ids = selectedIds.value.join(',')
    }
    const blob = await exportUserApi(params)
    // 文件名前缀使用本地时区的年月日时分秒
    const now = new Date()
    const pad = (n: number) => String(n).padStart(2, '0')
    const timestamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
    saveAs(blob, `用户数据_${timestamp}.xlsx`)
    ElMessage.success('导出成功')
  } catch (error) {
  } finally {
    exportLoading.value = false
  }
}

// 打开导入弹窗
const openImportDialog = () => {
  importFile.value = null
  importUploadRef.value?.clearFiles()
  importDialog.visible = true
}

// 选择文件
const handleFileChange = (uploadFile: any) => {
  importFile.value = uploadFile.raw
}

// 移除文件
const handleFileRemove = () => {
  importFile.value = null
}

// 超过数量限制时仅保留最新选择的文件
const handleExceed = (files: any) => {
  importUploadRef.value?.clearFiles()
  const file = files[0]
  importUploadRef.value?.handleStart(file)
  importFile.value = file
}

// 重置导入状态（弹窗关闭时触发）
const resetImport = () => {
  importFile.value = null
  importUploadRef.value?.clearFiles()
}

// 提交导入
const submitImport = async () => {
  if (!importFile.value) return
  importLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', importFile.value)
    const { data } = await importUserApi(formData)
    ElMessage.success(`导入成功，共 ${data} 条数据`)
    importDialog.visible = false
    getList()
  } catch (error) {
  } finally {
    importLoading.value = false
  }
}

// 初始化
onMounted(() => {
  getList()
  getRoleOptions()
  getDicts()
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
  :deep(.el-form-item .el-select),
  :deep(.el-form-item .el-tree-select),
  :deep(.el-form-item .dept-select),
  :deep(.el-form-item .user-select) {
    width: 100% !important;
  }
}
</style>
