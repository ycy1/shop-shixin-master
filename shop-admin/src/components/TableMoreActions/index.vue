<template>
  <div class="table-more-actions">
    <template v-if="shownActions.length <= maxVisible">
      <!-- 全部放得下：逐个直出 -->
      <template v-for="action in shownActions" :key="action.label">
        <el-tooltip v-if="action.tip" :content="action.tip" placement="top" :show-after="200">
          <!-- 禁用的按钮自己不收鼠标事件，所以 tooltip 的参考对象是外面这层 span（flex + gap 排版，间距不受影响） -->
          <span class="action-item">
            <el-button :type="action.type || 'primary'" link :icon="action.icon" :disabled="isDisabled(action)"
              :loading="action.loading" @click="handleCommand(action)">
              {{ action.label }}
            </el-button>
          </span>
        </el-tooltip>
        <el-button v-else :type="action.type || 'primary'" link :icon="action.icon" :disabled="isDisabled(action)"
          :loading="action.loading" @click="handleCommand(action)">
          {{ action.label }}
        </el-button>
      </template>
    </template>

    <template v-else>
      <!-- 放不下：前 (maxVisible - 1) 个直出，其余收进「更多」 -->
      <template v-for="action in inlineActions" :key="action.label">
        <el-tooltip v-if="action.tip" :content="action.tip" placement="top" :show-after="200">
          <span class="action-item">
            <el-button :type="action.type || 'primary'" link :icon="action.icon" :disabled="isDisabled(action)"
              :loading="action.loading" @click="handleCommand(action)">
              {{ action.label }}
            </el-button>
          </span>
        </el-tooltip>
        <el-button v-else :type="action.type || 'primary'" link :icon="action.icon" :disabled="isDisabled(action)"
          :loading="action.loading" @click="handleCommand(action)">
          {{ action.label }}
        </el-button>
      </template>

      <!-- 下拉里有项正在处理中时，「更多」自己变 loading，免得重复触发 -->
      <el-dropdown trigger="click" :disabled="triggerLoading" @command="handleDropdownCommand">
        <el-button type="primary" link :loading="triggerLoading">
          <template v-if="triggerLoading">
            处理中
          </template>
          <template v-else>
            更多
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </template>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="action in moreActions" :key="action.label" :command="action"
              :disabled="isDisabled(action) || action.loading">
              <!-- 同样是禁用语义收不到鼠标事件，带 tip 的那条把内容包一层 span 交给 tooltip 当参考对象。
                   下拉面板是挂到 body 的，所以 placement 用 right（朝上会被面板自己的边界裁掉） -->
              <el-tooltip v-if="action.tip" :content="action.tip" placement="right" :show-after="200">
                <span class="dropdown-item">
                  <el-icon v-if="action.loading" class="dropdown-icon is-loading">
                    <Loading />
                  </el-icon>
                  <el-icon v-else-if="action.icon" class="dropdown-icon">
                    <component :is="action.icon" />
                  </el-icon>
                  {{ action.label }}
                </span>
              </el-tooltip>
              <template v-else>
                <el-icon v-if="action.loading" class="dropdown-icon is-loading">
                  <Loading />
                </el-icon>
                <el-icon v-else-if="action.icon" class="dropdown-icon">
                  <component :is="action.icon" />
                </el-icon>
                {{ action.label }}
              </template>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ArrowDown, Loading } from '@element-plus/icons-vue'

/**
 * 一个 action = 操作列里的一颗按钮。三个状态字段各管各的：
 * hidden 不渲染（权限不足用它 —— 不给看，也别给猜），disabled 渲染但置灰（业务上现在做不了），
 * tip 悬浮说明「为什么做不了」，一般配 disabled 用。
 */
interface ActionItem {
  label: string
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  icon?: string
  /** 整条不渲染 */
  hidden?: boolean
  /** 渲染但置灰、点不动（要配 tip 说清原因，别只给一个点不动的按钮） */
  disabled?: boolean
  /** 悬浮提示：置灰的原因，或任何需要解释一句的操作 */
  tip?: string
  loading?: boolean
  command?: any
  [key: string]: any
}

interface Props {
  actions: ActionItem[]
  maxVisible?: number // 最大直出按钮数，超出部分收进「更多」下拉
}

const props = withDefaults(defineProps<Props>(), { maxVisible: 3 })

const emit = defineEmits<{
  command: [action: ActionItem]
}>()

/** 权限没过的（hidden）先摘掉，剩下的都可能渲染 —— 置灰的那些照样占位，让人知道有这个功能、只是现在不能用 */
const shownActions = computed<ActionItem[]>(() => props.actions.filter(action => !action.hidden))

/** loading 也算点不动：正在处理中的那颗挡住重复提交 */
const isDisabled = (action: ActionItem) => !!action.disabled || !!action.loading

const inlineCount = computed(() => Math.max(1, props.maxVisible - 1))
const inlineActions = computed<ActionItem[]>(() => shownActions.value.slice(0, inlineCount.value))
const moreActions = computed<ActionItem[]>(() => shownActions.value.slice(inlineCount.value))

const triggerLoading = computed(() => shownActions.value.some(action => action.loading))

// 点击和下拉都统一走 command 事件（同部门管理那套分发）
const handleCommand = (action: ActionItem) => {
  if (isDisabled(action)) return
  emit('command', action)
}

const handleDropdownCommand = (action: ActionItem) => {
  if (isDisabled(action)) return
  emit('command', action)
}
</script>

<style lang="scss" scoped>
.table-more-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;

  .el-button {
    padding: 0;
  }

  /* tooltip 的参考对象：inline-flex 让它在 flex + gap 的排版里和旁边的按钮一样占位 */
  .action-item {
    display: inline-flex;
    align-items: center;
  }

  :deep(.el-dropdown) {
    color: var(--el-color-primary);
    cursor: pointer;
  }
}

.dropdown-icon {
  margin-right: 5px;
  font-size: 14px;
}

/* 下拉项里包 tooltip 用的那一层：铺满整项，鼠标扫到哪都算悬浮到这一条 */
.dropdown-item {
  display: inline-flex;
  align-items: center;
  width: 100%;
}
</style>
