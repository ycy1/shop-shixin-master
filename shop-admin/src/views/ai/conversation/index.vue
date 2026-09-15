<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" :inline="true">
        <el-form-item label="智能体" prop="agentId">
          <el-select v-model="queryParams.agentId" placeholder="选择智能体" clearable filterable>
            <el-option v-for="item in agentOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 操作按钮 -->
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <ButtonGroup>
            <el-button type="danger" icon="Delete" :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
          </ButtonGroup>
        </div>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="tableData" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="会话标题" align="center" prop="title" min-width="200" show-overflow-tooltip />
        <el-table-column label="智能体" align="center" prop="agentName" min-width="120" />
        <el-table-column label="摘要" align="center" prop="summary" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="summary-text">{{ row.summary || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="消息数" align="center" prop="messageCount" width="90" />
        <el-table-column label="状态" align="center" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : row.status === 0 ? 'info' : 'warning'" size="small">
              {{ row.status === 1 ? '活跃' : row.status === 0 ? '已结束' : '归档' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后活跃" align="center" prop="lastActiveAt" width="180" />
        <el-table-column label="创建时间" align="center" prop="createTime" width="180" />
        <el-table-column label="操作" align="center" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleChat(row)">
              <el-icon style="margin-right:3px"><ChatDotSquare /></el-icon>对话
            </el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
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

    <!-- 聊天对话框组件 -->
    <ChatDialog
      v-model:visible="chatVisible"
      :agent="currentAgent"
      :conversation-id="currentConversationId"
      :conversation-title="currentConversationTitle"
      @title-updated="getList"
      ref="chatDialogRef"
    />
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { getConversationListApi, deleteConversationApi } from '@/api/ai/conversation'
import { getAgentListApi, getAgentDetailApi } from '@/api/ai/agent'
import { useRoute } from 'vue-router'
import ChatDialog from '@/views/ai/conversation/ChatDialog.vue'

const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const route = useRoute()

// ===== 智能体列表 =====
const agentOptions = ref<any[]>([])
const getAgentOptions = async () => {
  try {
    const { data } = await getAgentListApi({ pageNum: 1, pageSize: 999 })
    agentOptions.value = data.records
  } catch (_) {}
}

// ===== 会话列表 =====
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  agentId: route.query.agentId ? Number(route.query.agentId) : null as number | null
})
const loading = ref(false)
const total = ref(0)
const tableData = ref<any[]>([])
const selectedIds = ref<number[]>([])
const queryFormRef = ref()
const currentAgent = ref<any>(null)

const getList = async () => {
  loading.value = true
  try {
    const { data } = await getConversationListApi(queryParams)
    tableData.value = data.records
    total.value = data.total
  } catch (_) {}
  loading.value = false
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const resetQuery = () => {
  queryParams.agentId = null
  queryParams.pageNum = 1
  getList()
}
const handleSizeChange = (val: number) => { queryParams.pageSize = val; getList() }
const handleCurrentChange = (val: number) => { queryParams.pageNum = val; getList() }
const handleSelectionChange = (selection: any[]) => { selectedIds.value = selection.map((item: any) => item.id) }

const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确定删除会话「${row.title}」?`, '警告', {
    type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'
  }).then(async () => {
    await deleteConversationApi([row.id])
    ElMessage.success('删除成功'); getList()
  }).catch(() => {})
}

const handleBatchDelete = () => {
  if (selectedIds.value.length === 0) return
  ElMessageBox.confirm(`确定删除 ${selectedIds.value.length} 个会话?`, '警告', {
    type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'
  }).then(async () => {
    await deleteConversationApi(selectedIds.value)
    ElMessage.success('批量删除成功'); selectedIds.value = []; getList()
  }).catch(() => {})
}

// ===== 聊天对话框（使用 ChatDialog 组件） =====
const chatVisible = ref(false)
const chatDialogRef = ref()
const currentConversationId = ref<number | null>(null)
const currentConversationTitle = ref('')

const handleChat = async (row: any) => {
  currentConversationId.value = row.id
  currentConversationTitle.value = row.title || '新对话'
  // 加载完整智能体信息（含 tools）
  currentAgent.value = { id: row.agentId, name: row.agentName, avatarUrl: row.agentAvatar }
  try {
    const { data } = await getAgentDetailApi(row.agentId)
    if (data) Object.assign(currentAgent.value, data)
  } catch (_) {}
  chatVisible.value = true
  // 等组件渲染完成后加载历史消息
  await nextTick()
  if (chatDialogRef.value?.loadHistory) {
    chatDialogRef.value.loadHistory(row.id)
  }
}

onMounted(() => {
  getAgentOptions()
  getList()
})
</script>

<style lang="scss" scoped>
.app-container {
  .summary-text {
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }
  .pagination-container {
    display: flex;
    justify-content: center;
    margin-top: 20px;
  }
}

</style>
