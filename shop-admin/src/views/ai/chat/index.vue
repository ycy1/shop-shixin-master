<template>
  <div class="app-container chat-layout">
    <!-- 左侧：智能体和会话列表 -->
    <div class="chat-sidebar" :class="{ collapsed: !sidebarOpen }">
      <div class="sidebar-header">
        <h3>AI 对话</h3>
        <el-tooltip content="收起侧栏" placement="bottom" :disabled="!sidebarOpen">
          <el-button text size="small" @click="sidebarOpen = false">
            <el-icon><Fold /></el-icon>
          </el-button>
        </el-tooltip>
      </div>

      <!-- 智能体选择 -->
      <div class="agent-select">
        <el-select v-model="currentAgentId" placeholder="选择智能体" size="large" filterable @change="onAgentChange">
          <el-option v-for="item in agentList" :key="item.id" :label="item.name" :value="item.id">
            <div class="agent-option">
              <el-avatar :size="24" :src="item.avatarUrl || defaultAvatar" />
              <span>{{ item.name }}</span>
            </div>
          </el-option>
        </el-select>
      </div>

      <!-- 会话列表 -->
      <div class="conversation-list">
        <div class="list-header">
          <span>会话列表</span>
          <div class="list-header-actions">
            <el-button v-if="selectedConvIds.length > 0" type="danger" size="small" @click="handleBatchDelete">
              删除({{ selectedConvIds.length }})
            </el-button>
            <el-button text type="primary" size="small" @click="createNewConversation">+ 新建</el-button>
          </div>
        </div>
        <div v-loading="convLoading" class="list-body">
          <el-dropdown
            v-for="conv in conversationList"
            :key="conv.id"
            trigger="contextmenu"
            @command="(cmd: string) => handleContextMenu(cmd, conv)"
          >
            <div
              :class="['conv-item', { active: currentConversationId === conv.id }]"
              @click="switchConversation(conv)"
            >
              <el-checkbox
                :model-value="selectedConvIds.includes(conv.id)"
                @click.stop
                @change="(val: boolean) => toggleSelect(conv.id, val)"
                style="margin-right:6px"
              />
              <span class="conv-title">{{ conv.title }}</span>
              <span class="conv-meta">{{ conv.messageCount || 0 }} 条</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="rename" icon="Edit">修改名称</el-dropdown-item>
                <el-dropdown-item command="delete" icon="Delete" divided style="color:var(--el-color-danger)">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-empty v-if="!convLoading && conversationList.length === 0" description="暂无会话" :image-size="60" />
        </div>
      </div>
    </div>

    <!-- 右侧：聊天区域 -->
    <div class="chat-main">
      <template v-if="currentConversationId">
        <div class="chat-title-bar">
          <el-tooltip v-if="!sidebarOpen" content="展开侧栏" placement="bottom">
            <el-button text size="small" @click="sidebarOpen = true">
              <el-icon><Expand /></el-icon>
            </el-button>
          </el-tooltip>
          <el-input
            v-if="editingTitle"
            ref="titleInputRef"
            v-model="conversationTitle"
            size="small"
            @blur="saveTitle"
            @keydown.enter="saveTitle"
          />
          <span v-else class="chat-title-text" @click="startEdit">{{ conversationTitle }}</span>
        </div>
        <!-- 消息区域 -->
        <div class="messages-container" ref="messagesRef">
          <div v-if="chatMessages.length === 0" class="chat-welcome">
            <el-avatar :size="64" :src="currentAgent?.avatarUrl || defaultAvatar" />
            <h3>{{ currentAgent?.name || 'AI 助手' }}</h3>
            <p>{{ currentAgent?.greetingMessage || '您好！有什么可以帮助您的？' }}</p>
          </div>

          <div v-for="(msg, idx) in chatMessages" :key="idx" :class="['chat-msg', msg.role === 'user' ? 'right' : 'left']">
            <el-avatar v-if="msg.role !== 'user'" :size="36" :src="currentAgent?.avatarUrl || defaultAvatar" />
            <div class="bubble">
              <div class="content" v-html="renderMarkdown(msg.content)"></div>
              <div class="time">{{ msg.createTime }}</div>
            </div>
            <el-avatar v-if="msg.role === 'user'" :size="36" :src="userStore.user.avatar || defaultAvatar" icon="UserFilled" />
          </div>

          <div v-if="isStreaming" class="chat-msg left">
            <el-avatar :size="36" :src="currentAgent?.avatarUrl || defaultAvatar" />
            <div class="bubble streaming">
              <div class="content" v-html="streamingContent"></div><span class="cursor">|</span>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="input-area">
          <div class="cmd-bar">
            <el-autocomplete
              v-model="cmdInput"
              :fetch-suggestions="cmdSuggestions"
              placeholder="输入指令 / ..."
              size="small"
              style="flex:1"
              @keydown.enter="handleCmdSend"
              @select="(v: any) => cmdInput = v.value"
              value-key="label"
            >
              <template #prefix>
                <span style="font-size:12px;color:var(--el-text-color-placeholder)">/</span>
              </template>
              <template #default="{ item }">
                <span>{{ item.label }}</span>
              </template>
            </el-autocomplete>
            <el-button size="small" type="primary" @click="handleCmdSend" :disabled="!cmdInput.trim()">发送指令</el-button>
          </div>
          <div v-if="agentTools.length > 0" class="tool-bar">
            <span style="font-size:12px;color:var(--el-text-color-placeholder);margin-right:6px">工具:</span>
            <el-tag
              v-for="t in agentTools"
              :key="t.name"
              size="small"
              :type="activeTools.includes(t.name) ? 'primary' : 'info'"
              style="cursor:pointer"
              @click="toggleTool(t.name)"
            >
              {{ t.name }}
            </el-tag>
          </div>
          <div class="input-row">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="2"
              placeholder="输入消息，Enter 发送，Shift+Enter 换行"
              @keydown.enter.exact.prevent="sendMessage"
              :disabled="isStreaming"
            />
          </div>
          <div class="input-actions">
            <span class="hint">Enter 发送</span>
            <el-button type="primary" @click="sendMessage" :loading="isStreaming" size="large">
              {{ isStreaming ? '生成中...' : '发送' }}
            </el-button>
          </div>
        </div>
      </template>

      <!-- 未选择智能体 -->
      <div v-else class="no-agent-selected">
        <el-tooltip v-if="!sidebarOpen" content="展开侧栏" placement="bottom">
          <el-button text size="small" @click="sidebarOpen = true" style="position:absolute;left:8px;top:8px">
            <el-icon><Expand /></el-icon>
          </el-button>
        </el-tooltip>
        <el-icon :size="64" color="var(--el-color-primary)"><ChatLineSquare /></el-icon>
        <h2>选择一个智能体开始对话</h2>
        <p>请从左侧选择或创建一个智能体</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAgentListApi } from '@/api/ai/agent'
import { getConversationListApi, createConversationApi, updateConversationApi, deleteConversationApi } from '@/api/ai/conversation'
import { getChatHistoryApi } from '@/api/ai/chat'
import { getToolListApi } from '@/api/ai/tool'
import { useSSE } from '@/utils/useSSE'
import { renderMarkdown } from '@/utils/markdown'
import { useUserStore } from '@/store/modules/user'
import { getToken } from '@/utils/auth'

const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const userStore = useUserStore()
const sidebarOpen = ref(true)

// ===== 智能体 =====
const agentList = ref<any[]>([])
const currentAgentId = ref<number | null>(null)
const currentAgent = computed(() => agentList.value.find((a: any) => a.id === currentAgentId.value))

const loadAgents = async () => {
  try {
    const { data } = await getAgentListApi({ pageNum: 1, pageSize: 999, status: 1 })
    agentList.value = data.records || []
  } catch (_) {}
}

const onAgentChange = (agentId: number) => {
  currentConversationId.value = null
  chatMessages.value = []
  loadConversations(agentId)
}

// ===== 会话 =====
const conversationList = ref<any[]>([])
const currentConversationId = ref<number | null>(null)
const selectedConvIds = ref<number[]>([])

const toggleSelect = (id: number, val: boolean) => {
  if (val) selectedConvIds.value.push(id)
  else selectedConvIds.value = selectedConvIds.value.filter(v => v !== id)
}

const handleBatchDelete = () => {
  if (selectedConvIds.value.length === 0) return
  ElMessageBox.confirm(`确定删除 ${selectedConvIds.value.length} 个会话?`, '警告', {
    type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'
  }).then(async () => {
    await deleteConversationApi(selectedConvIds.value)
    ElMessage.success('批量删除成功')
    selectedConvIds.value = []
    if (currentConversationId.value && !conversationList.value.find(c => c.id === currentConversationId.value)) {
      currentConversationId.value = null
      chatMessages.value = []
    }
    loadConversations(currentAgentId.value!)
  }).catch(() => {})
}
const convLoading = ref(false)

const loadConversations = async (agentId: number) => {
  convLoading.value = true
  try {
    const { data } = await getConversationListApi({ pageNum: 1, pageSize: 50, agentId })
    conversationList.value = data.records || []
  } catch (_) {}
  convLoading.value = false
}

const onSelectAgent = (row: any) => {
  currentAgentId.value = row.id
  loadConversations(row.id)
}

const switchConversation = async (conv: any) => {
  currentConversationId.value = conv.id
  conversationTitle.value = conv.title || '新对话'
  chatMessages.value = []
  try {
    const { data } = await getChatHistoryApi(conv.id)
    chatMessages.value = (data || []).map((m: any) => ({
      ...m,
      createTime: formatTime(m.createTime)
    }))
    scrollToBottom()
  } catch (_) {}
}

const handleContextMenu = (cmd: string, conv: any) => {
  if (cmd === 'rename') {
    currentConversationId.value = conv.id
    conversationTitle.value = conv.title || ''
    editingTitle.value = true
    nextTick(() => titleInputRef.value?.focus())
  } else if (cmd === 'delete') {
    ElMessageBox.confirm(`确定删除会话「${conv.title}」?`, '警告', {
      type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'
    }).then(async () => {
      await deleteConversationApi([conv.id])
      ElMessage.success('删除成功')
      if (currentConversationId.value === conv.id) {
        currentConversationId.value = null
        chatMessages.value = []
      }
      loadConversations(currentAgentId.value!)
    }).catch(() => {})
  }
}

const createNewConversation = async () => {
  if (!currentAgentId.value) {
    ElMessage.warning('请先选择智能体')
    return
  }
  try {
    const { data } = await createConversationApi({ agentId: currentAgentId.value, title: '新对话' })
    conversationList.value.unshift(data)
    switchConversation(data)
  } catch (_) {}
}

// ===== 工具 =====
const allTools = ref<any[]>([])
const agentTools = computed(() => {
  if (!currentAgent.value?.tools) return []
  try {
    const names: string[] = JSON.parse(currentAgent.value.tools)
    return allTools.value.filter((t: any) => names.includes(t.name))
  } catch { return [] }
})
const activeTools = ref<string[]>([])
watch(() => currentAgent.value, (a) => {
  if (a?.tools) { try { activeTools.value = JSON.parse(a.tools) } catch { activeTools.value = [] } }
}, { immediate: true })
const toggleTool = (name: string) => {
  const idx = activeTools.value.indexOf(name)
  if (idx >= 0) activeTools.value.splice(idx, 1)
  else activeTools.value.push(name)
}
getToolListApi().then(res => { allTools.value = res.data || [] }).catch(() => {})

// ===== 聊天 =====
const { start, rendered: streamingHtml } = useSSE()
const chatMessages = ref<any[]>([])
const inputText = ref('')
const isStreaming = ref(false)
const streamingContent = ref('')
const messagesRef = ref<HTMLElement>()
const conversationTitle = ref('')
const editingTitle = ref(false)
const titleInputRef = ref()

const startEdit = () => {
  editingTitle.value = true
  nextTick(() => titleInputRef.value?.focus())
}

const saveTitle = async () => {
  editingTitle.value = false
  if (!currentConversationId.value || !conversationTitle.value.trim()) return
  try {
    await updateConversationApi({ id: currentConversationId.value, title: conversationTitle.value.trim() })
    // 刷新左侧会话列表
    if (currentAgentId.value) loadConversations(currentAgentId.value)
  } catch (_) {}
}

// ===== 指令 =====
const cmdInput = ref('')

const CMD_OPTIONS = [
  { value: '/clear', label: '/clear 清除上下文' },
  { value: '/export', label: '/export 导出全部' },
  { value: '/export -n ', label: '/export -n N 导出N条' },
  { value: '/init', label: '/init 重新加载' },
]

const cmdSuggestions = (query: string, cb: (results: any[]) => void) => {
  const q = query.toLowerCase()
  const results = CMD_OPTIONS.filter(o => o.value.toLowerCase().includes(q) || o.label.toLowerCase().includes(q))
  cb(results)
}
const handleCmdSend = async () => {
  const val = cmdInput.value.trim()
  if (!val || !currentConversationId.value) return
  if (!val.startsWith('/')) { ElMessage.warning('指令必须以 / 开头'); return }
  const isClear = val.trim() === '/clear'
  const isInit = val.trim() === '/init'
  cmdInput.value = ''
  const aiUrl = import.meta.env.VITE_APP_BASE_API + '/ai/send'
  chatMessages.value.push({ role: 'user', content: val, createTime: getCurrentTime() })
  isStreaming.value = true
  let exportFileId = ''
  try {
    await start(aiUrl, { body: { conversationId: currentConversationId.value, content: val } },
      (data: string) => {
        if (data.startsWith('EXPORT_OK:')) {
          exportFileId = data.replace('EXPORT_OK:', '')
        } else if (!isClear) {
          chatMessages.value.push({ role: 'assistant', content: data, createTime: getCurrentTime() })
        }
      })
    if (isClear || isInit) {
      const lastMsg = chatMessages.value.length > 0 ? chatMessages.value[chatMessages.value.length - 1] : null
      chatMessages.value = lastMsg ? [lastMsg] : [{ role: 'assistant', content: '✅ 已完成', createTime: getCurrentTime() }]
    }
    if (exportFileId) {
      const downloadUrl = import.meta.env.VITE_APP_BASE_API + '/ai/export-download/' + exportFileId
      const res = await fetch(downloadUrl, { headers: { 'Authorization': getToken() ?? '' } })
      if (res.ok) {
        const blob = await res.blob()
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        const ts = new Date().toISOString().replace(/[^0-9]/g, '').slice(0, 14)
        a.download = `${conversationTitle.value}_${ts}.xlsx`
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        URL.revokeObjectURL(url)
      }
      chatMessages.value.push({ role: 'assistant', content: '✅ 导出成功', createTime: getCurrentTime() })
    }
  } catch (_) {} finally { isStreaming.value = false }
   scrollToBottom()
}

const scrollToBottom = async () => {
  await nextTick()
  const el = messagesRef.value
  if (el) el.scrollTop = el.scrollHeight
  setTimeout(() => {
    if (el) el.scrollTop = el.scrollHeight
  }, 100)
}

const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || !currentConversationId.value) return

  chatMessages.value.push({ role: 'user', content: text, createTime: getCurrentTime() })
  scrollToBottom()
  inputText.value = ''
  isStreaming.value = true
  streamingContent.value = ''

  const aiUrl = import.meta.env.VITE_APP_BASE_API + '/ai/send'
  let fullContent = ''

  try {
    await start(aiUrl, {
      body: { conversationId: currentConversationId.value, content: text, tools: JSON.stringify(activeTools.value) }
    }, (data: string) => {
      if (data === '') {
        fullContent += '\n'
      }else{
        fullContent += data
      }
      streamingContent.value = renderMarkdown(fullContent)
      scrollToBottom()
    })

    if (fullContent) {
      chatMessages.value.push({ role: 'assistant', content: fullContent, createTime: getCurrentTime() })
      // 刷新会话列表
      if (currentAgentId.value) loadConversations(currentAgentId.value)
    }
  } catch (_) {
    if (fullContent) {
      chatMessages.value.push({ role: 'assistant', content: fullContent, createTime: getCurrentTime() })
    }
  } finally {
    isStreaming.value = false
    streamingContent.value = ''
    scrollToBottom()
  }
}

const formatTime = (t: any) => {
  if (!t) return ''
  if (typeof t === 'string') return t.substring(11, 16)
  return `${t.getHours().toString().padStart(2, '0')}:${t.getMinutes().toString().padStart(2, '0')}`
}

const getCurrentTime = () => {
  const d = new Date()
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

onMounted(() => {
  loadAgents()
})
</script>

<style lang="scss" scoped>
.chat-layout {
  display: flex;
  height: calc(100vh - 100px);
  gap: 0;
  overflow: hidden;
  padding: 0 !important;
  position: relative;
  background: var(--el-bg-color);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
}

// 左侧栏
.chat-sidebar {
  width: 280px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  background: var(--el-fill-color-lighter);
  transition: margin-left 0.2s ease, opacity 0.2s ease;

  &.collapsed {
    margin-left: -280px;
    opacity: 0;
  }

  .sidebar-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px;
    border-bottom: 1px solid var(--el-border-color-light);
    h3 { margin: 0; font-size: 16px; }
  }

  .agent-select {
    padding: 12px 16px;
    .el-select { width: 100%; }
    .agent-option {
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }

  .conversation-list {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;

    .list-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 8px;
      padding: 8px 16px;
      font-size: 13px;
      color: var(--el-text-color-secondary);

      .list-header-actions {
        display: flex;
        align-items: center;
        gap: 4px;
      }
    }

    .list-body {
      flex: 1;
      overflow-y: auto;
      padding: 0 8px;

      .el-dropdown {
        display: block;
      }

      .conv-item {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        padding: 6px 12px;
        border-radius: 6px;
        cursor: pointer;
        margin-bottom: 2px;
        transition: background 0.2s;

        &:hover { background: var(--el-color-primary-light-9); }
        &.active { background: var(--el-color-primary-light-8); }

        .conv-title {
          font-size: 13px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          flex: 1;
          min-width: 0;
        }
        .conv-meta {
          font-size: 11px;
          color: var(--el-text-color-placeholder);
          flex-shrink: 0;
        }
      }
    }
  }
}

// 右侧聊天区
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;

  .chat-title-bar {
    display: flex;
    align-items: center;
    padding: 10px 20px;
    border-bottom: 1px solid var(--el-border-color-light);
    background: var(--el-bg-color);
    flex-shrink: 0;
    .chat-title-text {
      font-size: 15px;
      font-weight: 600;
      cursor: pointer;
      &:hover { color: var(--el-color-primary); }
    }
    .el-input { width: 300px; }
  }

  .messages-container {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
    background: var(--el-fill-color-light);

    .chat-welcome {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 60px 20px;
      color: var(--el-text-color-secondary);
      h3 { margin: 12px 0 8px; color: var(--el-text-color-primary); }
      p { font-size: 14px; max-width: 400px; text-align: center; }
    }

    .chat-msg {
      display: flex;
      gap: 10px;
      margin-bottom: 16px;
      align-items: flex-start;

      &.right {
        flex-direction: row-reverse;
        .bubble { background: var(--el-color-primary-light-8); border-radius: 16px 4px 16px 16px; }
      }
      &.left {
        .bubble { background: var(--el-bg-color); border-radius: 4px 16px 16px 16px; border: 1px solid var(--el-border-color-light); }
      }

      .bubble {
        max-width: 75%;
        padding: 12px 16px;
        .content { font-size: 14px; line-height: 1.6; word-break: break-word; p { white-space: pre-wrap; } }
        .time { font-size: 11px; color: var(--el-text-color-placeholder); margin-top: 4px; text-align: right; }
        &.streaming .cursor { animation: blink 1s infinite; }
      }
    }
  }

  .input-area {
    border-top: 1px solid var(--el-border-color-light);
    padding: 12px 16px;
    background: var(--el-bg-color);

    .cmd-bar {
      display: flex;
      gap: 8px;
      margin-bottom: 8px;
    }

    .tool-bar {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 4px;
      margin-bottom: 8px;
    }

    .input-row {
      display: flex;
      gap: 8px;
      align-items: flex-end;
      margin-bottom: 8px;
      .el-textarea { flex: 1; margin-bottom: 0; }
    }

    .input-actions {
      display: flex;
      justify-content: space-between;
      align-items: center;
      .hint { font-size: 12px; color: var(--el-text-color-placeholder); }
    }
  }

  .no-agent-selected {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: var(--el-text-color-secondary);
    h2 { margin: 16px 0 8px; color: var(--el-text-color-primary); }
  }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
</style>
