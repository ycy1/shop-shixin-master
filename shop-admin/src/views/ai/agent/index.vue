<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" :inline="true">
        <el-form-item label="名称" prop="name">
          <el-input v-model="queryParams.name" placeholder="请输入智能体名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="queryParams.modelType" placeholder="请选择模型" clearable>
            <el-option label="DeepSeek" value="deepseek" />
            <el-option label="通义千问" value="qwen" />
            <el-option label="Ollama" value="ollama" />
            <el-option label="GPT-4" value="gpt-4" />
            <el-option label="Claude" value="claude" />
          </el-select>
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

    <!-- 操作按钮 -->
    <div class="action-bar">
      <el-button type="primary" icon="Plus" v-permission="['sys:agent:add']" @click="handleAdd">新增智能体</el-button>
      <el-button type="danger" icon="Delete" :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
    </div>

    <!-- 智能体卡片列表 -->
    <div v-loading="loading" class="agent-grid">
      <div v-for="item in tableData" :key="item.id" class="agent-card">
        <div class="card-header">
          <el-avatar :size="56" :src="item.avatarUrl || defaultAvatar">
            <span>{{ item.name?.charAt(0) }}</span>
          </el-avatar>
          <div class="card-info">
            <div class="card-title">
              <span>{{ item.name }}</span>
              <el-tag :type="item.status === 1 ? 'success' : 'danger'" size="small" class="status-tag">
                {{ item.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </div>
            <div class="card-model">
              <el-tag size="small" effect="plain">{{ item.modelType || 'deepseek' }}</el-tag>
              <el-tag v-if="item.configSourceName" size="small" type="warning" effect="plain">{{ item.configSourceName }}</el-tag>
            </div>
          </div>
        </div>

        <div class="card-body">
          <p class="card-desc">{{ item.description || '暂无描述' }}</p>
          <div class="card-stats">
            <div class="stat-item">
              <span class="stat-num">{{ item.totalConversations || 0 }}</span>
              <span class="stat-label">会话数</span>
            </div>
            <div class="stat-item">
              <span class="stat-num">{{ item.totalMessages || 0 }}</span>
              <span class="stat-label">消息数</span>
            </div>
            <div class="stat-item">
              <span class="stat-num">{{ item.temperature || 0.7 }}</span>
              <span class="stat-label">温度</span>
            </div>
          </div>
        </div>

        <div class="card-footer">
          <el-button type="primary" size="small" @click="handleStartChat(item)">
            <el-icon style="margin-right:4px"><ChatDotSquare /></el-icon> 开始对话
          </el-button>
          <el-button v-if="item.configType === 'database' || item.configType === 'document'" size="small" :loading="refreshingId === item.id" @click="handleRefreshKnowledge(item)">
            <el-icon v-if="refreshingId !== item.id" style="margin-right:4px"><Refresh /></el-icon> 更新知识
          </el-button>
          <el-dropdown trigger="click" @command="(cmd: string) => handleAction(cmd, item)">
            <el-button size="small">
              更多 <el-icon><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit" icon="Edit">编辑</el-dropdown-item>
                <el-dropdown-item command="toggle" :icon="item.status === 1 ? 'VideoPause' : 'VideoPlay'">
                  {{ item.status === 1 ? '禁用' : '启用' }}
                </el-dropdown-item>
                <el-dropdown-item command="conversations" icon="ChatLineSquare">查看会话</el-dropdown-item>
                <el-dropdown-item command="delete" icon="Delete" divided style="color:var(--el-color-danger)">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <!-- 空状态 -->
      <el-empty v-if="!loading && tableData.length === 0" description="暂无智能体" />
    </div>

    <!-- 分页 -->
    <div class="pagination-container">
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[12, 24, 36, 48]"
        :total="total"
        :background="true"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog :title="dialog.title" v-model="dialog.visible" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入智能体名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入智能体描述" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="头像" prop="avatarUrl">
          <el-input v-model="form.avatarUrl" placeholder="输入头像URL">
            <template #append>
              <el-avatar :size="28" :src="form.avatarUrl || defaultAvatar" style="display:flex" />
            </template>
          </el-input>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="模型类型" prop="modelType">
              <el-select v-model="form.modelType" placeholder="选择模型">
                <el-option label="DeepSeek" value="deepseek" />
                <el-option label="通义千问" value="qwen" />
                <el-option label="Ollama" value="ollama" />
                <el-option label="GPT-4" value="gpt-4" />
                <el-option label="Claude" value="claude" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="温度" prop="temperature">
              <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" show-input />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="最大Token" prop="maxTokens">
              <el-input-number v-model="form.maxTokens" :min="512" :max="8192" :step="512" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="公开" prop="isPublic">
              <el-switch v-model="form.isPublic" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="前置知识" prop="configType">
              <el-select v-model="form.configType">
                <el-option label="无（手动填写提示词）" value="default" />
                <el-option label="数据库" value="database" />
                <el-option label="文档" value="document" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="form.configType === 'database' || form.configType === 'document'" label="知识源" prop="configId">
          <el-select v-model="form.configId" filterable placeholder="选择已配置的知识源">
            <el-option v-for="item in filteredConfigSources" :key="item.id" :label="item.sourceName" :value="item.id">
              <span>{{ item.sourceName }}</span>
              <span style="float:right;font-size:12px;color:var(--el-text-color-placeholder)">
                {{ item.sourceType === 'database' ? '数据库' : '文档' }}
              </span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="系统提示词" prop="systemPrompt">
          <el-input v-model="form.systemPrompt" type="textarea" :rows="4" placeholder="设定智能体的角色和行为指令..."
            :disabled="form.configType === 'database' || form.configType === 'document'" />
          <div v-if="form.configType === 'database' || form.configType === 'document'" style="font-size:12px;color:var(--el-text-color-placeholder);margin-top:4px">
            已选择知识源，系统提示词将由知识源内容自动填充
          </div>
        </el-form-item>
        <el-form-item label="欢迎语" prop="greetingMessage">
          <el-input v-model="form.greetingMessage" type="textarea" :rows="2" placeholder="设置对话开场白..." />
        </el-form-item>
        <el-form-item label="工具">
          <el-checkbox-group v-model="form.tools">
            <el-checkbox v-for="tool in toolOptions" :key="tool.name" :label="tool.name" border style="margin-right:8px;margin-bottom:4px">
              {{ tool.name }}
            </el-checkbox>
          </el-checkbox-group>
          <div style="font-size:12px;color:var(--el-text-color-placeholder);margin-top:4px">
            选择后 AI 可在对话中调用这些工具
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancel">取 消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 聊天对话框组件 -->
    <ChatDialog
      v-model:visible="chatVisible"
      :agent="chatAgent"
      :greeting="chatAgent?.greetingMessage"
    />
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getAgentListApi, addAgentApi, updateAgentApi, deleteAgentApi, updateAgentStatusApi, refreshAgentKnowledgeApi } from '@/api/ai/agent'
import { getConfigSourceListApi } from '@/api/ai/config-source'
import { getToolListApi } from '@/api/ai/tool'
import ChatDialog from '@/views/ai/conversation/ChatDialog.vue'

const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const userAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

// ===== 智能体列表 =====
const queryParams = reactive({
  pageNum: 1,
  pageSize: 12,
  name: null,
  modelType: null,
  status: null
})
const loading = ref(false)
const total = ref(0)
const tableData = ref<any[]>([])
const selectedIds = ref<number[]>([])
const queryFormRef = ref<FormInstance>()

const getList = async () => {
  loading.value = true
  try {
    const { data } = await getAgentListApi(queryParams)
    tableData.value = data.records
    total.value = data.total
  } catch (_) {}
  loading.value = false
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const resetQuery = () => { queryFormRef.value?.resetFields(); handleQuery() }
const handleSizeChange = (val: number) => { queryParams.pageSize = val; getList() }
const handleCurrentChange = (val: number) => { queryParams.pageNum = val; getList() }

// ===== 新增/编辑对话框 =====
const dialog = reactive({ title: '', visible: false, type: 'add' })
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const toolOptions = ref<any[]>([])

const form = reactive<any>({
  id: undefined, name: '', description: '', avatarUrl: '',
  modelType: 'deepseek', temperature: 0.7, maxTokens: 2048,
  isPublic: 0, configType: 'default', configId: undefined,
  systemPrompt: '', greetingMessage: '', tools: []
})

const rules = reactive<FormRules>({
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
})

const clearForm = () => {
  form.id = undefined; form.name = ''; form.description = ''
  form.avatarUrl = ''; form.modelType = 'deepseek'; form.temperature = 0.7
  form.maxTokens = 2048; form.isPublic = 0; form.configType = 'default'; form.configId = undefined; form.systemPrompt = ''; form.greetingMessage = ''; form.tools = []
}

const handleAdd = () => {
  clearForm(); dialog.type = 'add'
  dialog.title = '新增智能体'; dialog.visible = true
}

const handleEdit = (row: any) => {
  clearForm(); dialog.type = 'edit'
  dialog.title = '编辑智能体'; dialog.visible = true
  setTimeout(() => {
    Object.assign(form, row)
    form.temperature = row.temperature ?? 0.7
    try { form.tools = JSON.parse(row.tools || '[]') } catch { form.tools = [] }
  }, 50)
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      // tools 数组转 JSON 字符串
      const submitData = { ...form, tools: JSON.stringify(form.tools) }
      if (dialog.type === 'add') {
        await addAgentApi(submitData)
        ElMessage.success('新增成功')
      } else {
        await updateAgentApi(submitData)
        ElMessage.success('修改成功')
      }
      dialog.visible = false; getList()
    } catch (_) {} finally { submitLoading.value = false }
  })
}

const cancel = () => {
  dialog.visible = false; formRef.value?.resetFields()
}

// ===== 操作 =====
const refreshingId = ref<number | null>(null)

const handleRefreshKnowledge = async (item: any) => {
  refreshingId.value = item.id
  try {
    await refreshAgentKnowledgeApi(item.id)
    ElMessage.success('知识库已更新')
    getList()
  } catch (_) {} finally {
    refreshingId.value = null
  }
}

const handleAction = (cmd: string, row: any) => {
  switch (cmd) {
    case 'edit': handleEdit(row); break
    case 'delete': handleDelete(row); break
    case 'toggle': handleToggleStatus(row); break
    case 'conversations': handleViewConversations(row); break
  }
}

const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确定删除智能体「${row.name}」?`, '警告', {
    type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'
  }).then(async () => {
    await deleteAgentApi([row.id])
    ElMessage.success('删除成功'); getList()
  }).catch(() => {})
}

const handleBatchDelete = () => {
  if (selectedIds.value.length === 0) return
  ElMessageBox.confirm(`确定删除 ${selectedIds.value.length} 个智能体?`, '警告', {
    type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'
  }).then(async () => {
    await deleteAgentApi(selectedIds.value)
    ElMessage.success('批量删除成功'); selectedIds.value = []; getList()
  }).catch(() => {})
}

const handleToggleStatus = async (row: any) => {
  const newStatus = row.status === 1 ? 0 : 1
  await updateAgentStatusApi(row.id, newStatus)
  ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
  getList()
}

// ===== 聊天对话（使用 ChatDialog 组件） =====
const chatVisible = ref(false)
const chatAgent = ref<any>(null)

const handleStartChat = (agent: any) => {
  chatAgent.value = { ...agent, greetingMessage: agent.greetingMessage }
  chatVisible.value = true
}

const router = useRouter()

const handleViewConversations = (agent: any) => {
  router.push({ path: '/ai/ai-conversation', query: { agentId: agent.id } })
}

const configSourceOptions = ref<any[]>([])
const filteredConfigSources = computed(() =>
  configSourceOptions.value.filter((s: any) => s.sourceType === form.configType)
)
const loadConfigSources = async () => {
  try {
    const { data } = await getConfigSourceListApi({ pageNum: 1, pageSize: 999 })
    configSourceOptions.value = data.records || []
  } catch (_) {}
}

const loadTools = async () => {
  try {
    const { data } = await getToolListApi()
    toolOptions.value = data || []
  } catch (_) {}
}

onMounted(() => { getList(); loadConfigSources(); loadTools() })
</script>

<style lang="scss" scoped>
.app-container {
  .action-bar {
    margin-bottom: 16px;
    display: flex;
    gap: 8px;
  }

  .agent-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: 16px;
    min-height: 200px;
  }

  .agent-card {
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-light);
    border-radius: 12px;
    padding: 20px;
    transition: all 0.3s ease;
    display: flex;
    flex-direction: column;

    &:hover {
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
      transform: translateY(-2px);
      border-color: var(--el-color-primary-light-5);
    }

    .card-header {
      display: flex;
      align-items: center;
      gap: 14px;
      margin-bottom: 14px;

      .card-info {
        flex: 1;
        min-width: 0;

        .card-title {
          display: flex;
          align-items: center;
          gap: 8px;
          font-size: 16px;
          font-weight: 600;
          margin-bottom: 4px;

          .status-tag {
            flex-shrink: 0;
          }
        }

        .card-model {
          .el-tag {
            font-size: 11px;
          }
        }
      }
    }

    .card-body {
      flex: 1;
      margin-bottom: 14px;

      .card-desc {
        color: var(--el-text-color-secondary);
        font-size: 13px;
        line-height: 1.6;
        margin: 0 0 12px;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }

      .card-stats {
        display: flex;
        gap: 16px;

        .stat-item {
          display: flex;
          flex-direction: column;
          align-items: center;
          min-width: 60px;

          .stat-num {
            font-size: 18px;
            font-weight: 700;
            color: var(--el-color-primary);
          }
          .stat-label {
            font-size: 12px;
            color: var(--el-text-color-secondary);
            margin-top: 2px;
          }
        }
      }
    }

    .card-footer {
      display: flex;
      gap: 8px;
      padding-top: 14px;
      border-top: 1px solid var(--el-border-color-lighter);
    }
  }

  .pagination-container {
    display: flex;
    justify-content: center;
    margin-top: 20px;
  }
}
:root[data-theme='dark'] {
  .agent-card:hover {
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
  }
}
</style>
