<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    fullscreen
    class="chat-dialog"
    :append-to-body="false"
    :close-on-click-modal="!isStreaming"
    :close-on-press-escape="!isStreaming"
    destroy-on-close
  >
    <template #header="{ close }">
      <div class="chat-dialog-header">
        <div class="chat-dialog-title">
          <el-input
            v-if="editingTitle"
            ref="titleInputRef"
            v-model="conversationTitle"
            size="small"
            @blur="saveTitle"
            @keydown.enter="saveTitle"
          />
          <span v-else class="title-text" @click="startEdit">{{ conversationTitle }}</span>
        </div>
        <el-button class="chat-dialog-close" text @click="close" :disabled="isStreaming">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
    </template>
    <div class="chat-container" ref="chatContainerRef">
      <!-- 欢迎消息 -->
      <div v-if="messages.length === 0" class="chat-welcome">
        <el-avatar :size="64" :src="agent?.avatarUrl || defaultAvatar" />
        <h3>{{ agent?.name || 'AI 助手' }}</h3>
        <p>{{ greeting || '您好！有什么可以帮助您的？' }}</p>
      </div>

      <!-- 消息列表 -->
      <div v-for="(msg, idx) in messages" :key="idx" :class="['chat-msg', msg.role === 'user' ? 'msg-right' : 'msg-left']">
        <el-avatar v-if="msg.role !== 'user'" :size="36" :src="agent?.avatarUrl || defaultAvatar" />
        <div class="msg-bubble">
          <div class="msg-content" v-html="renderMarkdown(msg.content)"></div>
          <div class="msg-time">{{ msg.time }}</div>
        </div>
        <el-avatar v-if="msg.role === 'user'" :size="36" :src="userStore.user.avatar || defaultAvatar" icon="UserFilled" />
      </div>

      <!-- 流式响应 -->
      <div v-if="isStreaming" class="chat-msg msg-left">
        <el-avatar :size="36" :src="agent?.avatarUrl || defaultAvatar" />
        <div class="msg-bubble streaming">
          <div class="msg-content" v-html="streamingContent"></div><span class="cursor">|</span>
        </div>
      </div>
    </div>

    <div class="chat-input-area">
      <!-- 指令选择器 -->
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
          :rows="3"
          placeholder="输入消息，Enter 发送"
          @keydown.enter.exact.prevent="sendMessage"
          :disabled="isStreaming"
        />
      </div>
      <div class="chat-actions">
        <el-button @click="resetChat" :disabled="isStreaming">重置对话</el-button>
        <el-button type="primary" @click="sendMessage" :loading="isStreaming">
          {{ isStreaming ? '生成中...' : '发送' }}
        </el-button>
        <el-button type="danger" text @click="$emit('update:visible', false)" :disabled="isStreaming">
          关闭对话
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { getChatHistoryApi } from '@/api/ai/chat'
import { createConversationApi, updateConversationApi } from '@/api/ai/conversation'
import { useSSE } from '@/utils/useSSE'
import { renderMarkdown } from '@/utils/markdown'
import { useUserStore } from '@/store/modules/user'
import { getToken } from '@/utils/auth'

const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const userStore = useUserStore()

const props = defineProps<{
  visible: boolean
  agent: any
  greeting?: string
  conversationId?: number
  conversationTitle?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'conversation-change', id: number): void
  (e: 'title-updated'): void
}>()

// ===== 工具 =====
const allTools = ref<any[]>([])
const agentTools = computed(() => {
  if (!props.agent?.tools) return []
  try {
    const names: string[] = JSON.parse(props.agent.tools)
    return allTools.value.filter((t: any) => names.includes(t.name))
  } catch { return [] }
})

const activeTools = ref<string[]>([])

watch(() => props.agent, async (a) => {
  if (a?.tools) {
    try { activeTools.value = JSON.parse(a.tools) } catch { activeTools.value = [] }
  }
}, { immediate: true })

const toggleTool = (name: string) => {
  const idx = activeTools.value.indexOf(name)
  if (idx >= 0) activeTools.value.splice(idx, 1)
  else activeTools.value.push(name)
}

// ===== 指令自动补全 =====
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

// 动态加载工具列表
import { getToolListApi } from '@/api/ai/tool'
getToolListApi().then(res => { allTools.value = res.data || [] }).catch(() => {})


const { start, stop: stopSSE, rendered: streamingHtml } = useSSE()
const chatContainerRef = ref<HTMLElement>()
const messages = ref<any[]>([])
const inputText = ref('')
const isStreaming = ref(false)
const streamingContent = ref('')
const conversationId = ref<number | null>(null)
const conversationTitle = ref('新对话')
const editingTitle = ref(false)
const titleInputRef = ref()

const startEdit = () => {
  editingTitle.value = true
  nextTick(() => titleInputRef.value?.focus())
}

const saveTitle = async () => {
  editingTitle.value = false
  if (!conversationId.value || !conversationTitle.value.trim()) return
  try {
    await updateConversationApi({ id: conversationId.value, title: conversationTitle.value.trim() })
    emit('title-updated')
  } catch (_) {}
}

// ===== 文件上传 =====

// 弹窗打开时强制设置 DOM 样式
/**
 * (解决：在页面打开时弹窗页元素不存在而导致的css渲染失败问题)
 * 弹窗打开时强制设置 DOM 样式
 * 确保布局不受 CSS 穿透问题影响
 */
watch(() => props.visible, async (val) => {
  if (!val) { stopSSE(); return }
  if (val && props.agent) {
    if (props.conversationId) {
      conversationId.value = props.conversationId
      messages.value = []
      inputText.value = ''
      streamingContent.value = ''
      conversationTitle.value = props.conversationTitle || '新对话'
    } else {
      await initConversation()
      conversationTitle.value = props.conversationTitle || '新对话'
    }
    await nextTick()
    // 直接操作 DOM，确保布局不受 CSS 穿透问题影响
    const dialog = document.querySelector('.chat-dialog') as HTMLElement
    if (dialog) {
      dialog.style.setProperty('margin-top', '0', 'important')
      dialog.style.setProperty('display', 'flex', 'important')
      dialog.style.setProperty('flex-direction', 'column', 'important')
      dialog.style.setProperty('height', '100vh', 'important')
      dialog.style.setProperty('overflow', 'hidden', 'important')
      const header = dialog.querySelector('.el-dialog__header') as HTMLElement
      if (header) {
        header.style.setProperty('padding', '0', 'important')
        header.style.setProperty('flex-shrink', '0', 'important')
      }
      const body = dialog.querySelector('.el-dialog__body') as HTMLElement
      if (body) {
        body.style.setProperty('flex', '1', 'important')
        body.style.setProperty('padding', '0', 'important')
        body.style.setProperty('display', 'flex', 'important')
        body.style.setProperty('flex-direction', 'column', 'important')
        body.style.setProperty('overflow', 'hidden', 'important')
        body.style.setProperty('height', '0', 'important')
      }
    }
  }
})

const scrollToBottom = async () => {
  await nextTick()
  const el = chatContainerRef.value
  if (el) el.scrollTop = el.scrollHeight
  // 等 markdown v-html 渲染完成后再次滚动到最新
  setTimeout(() => {
    if (el) el.scrollTop = el.scrollHeight
  }, 100)
}

const initConversation = async () => {
  messages.value = []
  inputText.value = ''
  streamingContent.value = ''
  try {
    const { data } = await createConversationApi({ agentId: props.agent.id, title: '新对话' })
    conversationId.value = data.id
    emit('conversation-change', data.id)
  } catch (_) {}
}

// ===== 指令 =====
const cmdInput = ref('')
const handleCmdSend = async () => {
  const val = cmdInput.value.trim()
  if (!val || !conversationId.value) return
  if (!val.startsWith('/')) { ElMessage.warning('指令必须以 / 开头'); return }
  const isClear = val.trim() === '/clear'
  const isInit = val.trim() === '/init'
  cmdInput.value = ''
  const aiUrl = import.meta.env.VITE_APP_BASE_API + '/ai/send'
  messages.value.push({ role: 'user', content: val, time: getCurrentTime() })
  isStreaming.value = true
  let exportFileId = ''
  try {
    await start(aiUrl, { body: { conversationId: conversationId.value, content: val } },
      (data: string) => {
        if (data.startsWith('EXPORT_OK:')) {
          exportFileId = data.replace('EXPORT_OK:', '')
        } else if (!isClear) {
          messages.value.push({ role: 'assistant', content: data, time: getCurrentTime() })
        }
      })
    if (isClear || isInit) {
      // 保留最后一条后端返回的消息
      const lastMsg = messages.value.length > 0 ? messages.value[messages.value.length - 1] : null
      messages.value = lastMsg ? [lastMsg] : [{ role: 'assistant', content: '✅ 已完成', time: getCurrentTime() }]
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
      messages.value.push({ role: 'assistant', content: '✅ 导出成功', time: getCurrentTime() })
      scrollToBottom()
    }
  } catch (_) {} finally { isStreaming.value = false }
}

const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || !conversationId.value) return

  messages.value.push({ role: 'user', content: text, time: getCurrentTime() })
  scrollToBottom()
  inputText.value = ''
  isStreaming.value = true
  streamingContent.value = ''

  const aiUrl = import.meta.env.VITE_APP_BASE_API + '/ai/send'
  let fullContent = ''

  try {
    await start(aiUrl, {
      body: { conversationId: conversationId.value, content: text, tools: JSON.stringify(activeTools.value) }
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
      messages.value.push({ role: 'assistant', content: fullContent, time: getCurrentTime() })
    }
  } catch (_) {
    if (fullContent) {
      messages.value.push({ role: 'assistant', content: fullContent, time: getCurrentTime() })
    }
  } finally {
    isStreaming.value = false
    streamingContent.value = ''
    scrollToBottom()
  }
}

const resetChat = () => {
  messages.value = []
  streamingContent.value = ''
  inputText.value = ''
}

const getCurrentTime = () => {
  const d = new Date()
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

// 对外暴露加载历史消息的方法
const loadHistory = async (convId: number) => {
  conversationId.value = convId
  messages.value = []
  try {
    const { data } = await getChatHistoryApi(convId)
    messages.value = (data || []).map((m: any) => ({
      ...m,
      time: m.createTime?.substring(11, 16) || ''
    }))
    scrollToBottom()
  } catch (_) {}
}

defineExpose({ loadHistory })
</script>

<style lang="scss" scoped>
/* chat-dialog 本身就是 el-dialog 元素，直接写样式 */
.chat-dialog {
  margin-top: 0 !important;
  display: flex !important;
  flex-direction: column !important;
  height: 100vh !important;
  overflow: hidden !important;
}

.chat-dialog :deep(.el-dialog__header) {
  padding: 0;
  display: flex;
  flex-direction: column;
}

.chat-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--el-border-color-light);
  position: sticky;
  top: 0;
  z-index: 10;
  background: var(--el-bg-color);
}

.chat-dialog-title {
  font-size: 16px;
  font-weight: 600;
  .title-text {
    cursor: pointer;
    &:hover { color: var(--el-color-primary); }
  }
  .el-input {
    width: 260px;
  }
}

.chat-dialog-close {
  font-size: 18px;
}

.chat-dialog :deep(.el-dialog__body) {
  flex: 1 !important;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
  overflow: hidden !important;
  height: 0 !important;
}

.chat-container {
  flex: 1 !important;
  overflow-y: auto !important;
  padding: 20px !important;
  background: var(--el-fill-color-light) !important;

  .chat-welcome {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 60px 20px;
    color: var(--el-text-color-secondary);
    h3 { margin: 16px 0 8px; font-size: 20px; color: var(--el-text-color-primary); }
    p { font-size: 14px; text-align: center; max-width: 400px; }
  }

  .chat-msg {
    display: flex;
    gap: 10px;
    margin-bottom: 16px;
    align-items: flex-start;

    &.msg-right {
      flex-direction: row-reverse;
      .msg-bubble {
        background: var(--el-color-primary-light-8);
        border-radius: 16px 4px 16px 16px;
      }
    }
    &.msg-left {
      .msg-bubble {
        background: var(--el-bg-color);
        border-radius: 4px 16px 16px 16px;
        border: 1px solid var(--el-border-color-light);
      }
    }
  }

  .msg-bubble {
    max-width: 75%;
    padding: 12px 16px;
    .msg-content {
      font-size: 14px;
      line-height: 1.6;
      word-break: break-word;
      p { white-space: pre-wrap; }
    }
    .msg-time {
      font-size: 11px;
      color: var(--el-text-color-placeholder);
      margin-top: 4px;
      text-align: right;
    }
    &.streaming .cursor {
      animation: blink 1s infinite;
    }
  }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.chat-input-area {
  flex-shrink: 0;
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

  .uploaded-files {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    margin-bottom: 8px;
  }

  .input-row {
    display: flex;
    gap: 8px;
    align-items: flex-end;
    .el-textarea { flex: 1; }
  }

  .el-textarea { margin-bottom: 8px; }
  .chat-actions { display: flex; justify-content: flex-end; gap: 8px; }
}
</style>
