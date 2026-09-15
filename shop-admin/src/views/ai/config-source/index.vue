<template>
  <div class="app-container">
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" :inline="true">
        <el-form-item label="知识源类型" prop="sourceType">
          <el-select v-model="queryParams.sourceType" placeholder="选择类型" clearable>
            <el-option label="数据库" value="database" />
            <el-option label="文档" value="document" />
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
            <el-button type="primary" icon="Plus" @click="handleAdd">新增知识源</el-button>
          </ButtonGroup>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" style="width:100%">
        <el-table-column label="名称" align="center" prop="sourceName" min-width="160" />
        <el-table-column label="类型" align="center" width="100">
          <template #default="{ row }">
            <el-tag :type="row.sourceType === 'database' ? 'primary' : 'success'" size="small">
              {{ row.sourceType === 'database' ? '数据库' : '文档' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="连接/路径" align="center" min-width="300">
          <template #default="{ row }">
            <span v-if="row.sourceType === 'database'">{{ row.jdbcUrl }}</span>
            <span v-else>{{ row.docPath }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="180" />
        <el-table-column label="操作" align="center" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

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

    <!-- 新增/编辑对话框 -->
    <el-dialog :title="dialog.title" v-model="dialog.visible" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="知识源类型" prop="sourceType">
          <el-radio-group v-model="form.sourceType">
            <el-radio value="database">数据库</el-radio>
            <el-radio value="document">文档</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="名称" prop="sourceName">
          <el-input v-model="form.sourceName" placeholder="请输入知识源名称" />
        </el-form-item>
        <el-form-item label="描述" prop="sourceDescription">
          <el-input v-model="form.sourceDescription" type="textarea" :rows="2" placeholder="可选描述" />
        </el-form-item>

        <!-- 数据库配置 -->
        <template v-if="form.sourceType === 'database'">
          <el-form-item label="数据库类型" prop="dbType">
            <el-select v-model="form.dbType">
              <el-option label="MySQL" value="mysql" />
              <el-option label="PostgreSQL" value="postgresql" />
              <el-option label="Oracle" value="oracle" />
            </el-select>
          </el-form-item>
          <el-form-item label="JDBC URL" prop="jdbcUrl">
            <el-input v-model="form.jdbcUrl" placeholder="jdbc:mysql://localhost:3306/db" />
          </el-form-item>
          <el-form-item label="用户名" prop="dbUsername">
            <el-input v-model="form.dbUsername" placeholder="数据库用户名" />
          </el-form-item>
          <el-form-item label="密码" prop="dbPassword">
            <el-input v-model="form.dbPassword" type="password" placeholder="数据库密码" show-password />
          </el-form-item>
          <el-form-item>
            <el-button type="success" :loading="testing" @click="handleTestConnection">
              测试连接
            </el-button>
          </el-form-item>
        </template>

        <!-- 文档配置 -->
        <template v-if="form.sourceType === 'document'">
          <el-form-item label="存储类型" prop="docStorageType">
            <el-select v-model="form.docStorageType">
              <el-option label="本地文件" value="local" />
              <el-option label="URL链接" value="url" />
            </el-select>
          </el-form-item>
          <el-form-item label="文档路径" prop="docPath">
            <div v-if="form.docStorageType === 'local'" style="display:flex;gap:8px;width:100%">
              <el-input v-model="form.docPath" placeholder="本地路径或上传文件" />
              <el-upload :show-file-list="false" :http-request="uploadDoc" accept=".txt,.md,.pdf,.doc,.docx">
                <el-button icon="Upload">上传</el-button>
              </el-upload>
            </div>
            <el-input v-else v-model="form.docPath" placeholder="https://..." />
          </el-form-item>
          <el-form-item label="分块大小" prop="chunkSize">
            <el-input-number v-model="form.chunkSize" :min="100" :max="5000" :step="100" />
            <span style="margin-left:8px;font-size:12px;color:var(--el-text-color-placeholder)">字符数</span>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="cancel">取 消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getConfigSourceListApi, addConfigSourceApi, updateConfigSourceApi, deleteConfigSourceApi, testDbConnectionApi } from '@/api/ai/config-source'
import { uploadApi } from '@/api/file'

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  sourceType: null
})
const loading = ref(false)
const total = ref(0)
const tableData = ref<any[]>([])
const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()
const submitLoading = ref(false)

const dialog = reactive({ title: '', visible: false, type: 'add' })

const form = reactive<any>({
  id: undefined, sourceType: 'database', sourceName: '', sourceDescription: '',
  dbType: 'mysql', jdbcUrl: '', dbUsername: '', dbPassword: '',
  docStorageType: 'local', docPath: '', chunkSize: 500
})

const rules = reactive<FormRules>({
  sourceType: [{ required: true, message: '请选择知识源类型', trigger: 'change' }],
  sourceName: [{ required: true, message: '请输入名称', trigger: 'blur' }]
})

const getList = async () => {
  loading.value = true
  try {
    const { data } = await getConfigSourceListApi(queryParams)
    tableData.value = data.records
    total.value = data.total
  } catch (_) {}
  loading.value = false
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const resetQuery = () => { queryParams.sourceType = null; queryParams.pageNum = 1; getList() }
const handleSizeChange = (val: number) => { queryParams.pageSize = val; getList() }
const handleCurrentChange = (val: number) => { queryParams.pageNum = val; getList() }

const testing = ref(false)

const handleTestConnection = async () => {
  testing.value = true
  try {
    const res = await testDbConnectionApi({
      jdbcUrl: form.jdbcUrl,
      dbUsername: form.dbUsername,
      dbPassword: form.dbPassword
    })
    ElMessage.success(res.message || '连接成功')
  } catch (_) {}
  testing.value = false
}

const uploadDoc = async (options: any) => {
  const formData = new FormData()
  formData.append('file', options.file)
  try {
    const res = await uploadApi(formData, 'config-source')
    form.docPath = res.data || res.data?.url || ''
    ElMessage.success('上传成功')
  } catch (_) {
    ElMessage.error('上传失败')
  }
}

const handleAdd = () => {
  dialog.type = 'add'; dialog.title = '新增知识源'; dialog.visible = true
  Object.assign(form, { id: undefined, sourceType: 'database', sourceName: '', sourceDescription: '',
    dbType: 'mysql', jdbcUrl: '', dbUsername: '', dbPassword: '',
    docStorageType: 'local', docPath: '', chunkSize: 500 })
}

const handleEdit = (row: any) => {
  dialog.type = 'edit'; dialog.title = '修改知识源'; dialog.visible = true
  nextTick(() => Object.assign(form, row))
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (dialog.type === 'add') {
        await addConfigSourceApi(form); ElMessage.success('新增成功')
      } else {
        await updateConfigSourceApi(form); ElMessage.success('修改成功')
      }
      dialog.visible = false; getList()
    } catch (_) {} finally { submitLoading.value = false }
  })
}

const cancel = () => { dialog.visible = false; formRef.value?.resetFields() }

const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确定删除知识源「${row.sourceName}」?`, '警告', {
    type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'
  }).then(async () => {
    await deleteConfigSourceApi(row.id); ElMessage.success('删除成功'); getList()
  }).catch(() => {})
}

onMounted(() => { getList() })
</script>

<style lang="scss" scoped>
.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
