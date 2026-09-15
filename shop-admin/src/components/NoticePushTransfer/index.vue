<template>
  <div class="notice-push-transfer">
    <!-- 展示区：按 用户/部门/角色 三个分类展示具体对象 tag（可删除单个），点击弹出穿梭框弹框 -->
    <div class="npt-display" @click="openDialog" title="点击选择发送对象">
      <div v-if="totalSelected" class="npt-display-groups">
        <div class="npt-display-group">
          <span class="npt-group-name">用户</span>
          <el-tag v-for="u in selected.user" :key="u.id" size="small" closable class="npt-display-tag"
            @close="removeDisplayItem('user', u.id)">{{ u.name }}</el-tag>
          <span v-if="!selected.user.length" class="npt-group-zero">（0）</span>
        </div>
        <div class="npt-display-group">
          <span class="npt-group-name">部门</span>
          <el-tag v-for="d in selected.dept" :key="d.id" size="small" closable class="npt-display-tag"
            @close="removeDisplayItem('dept', d.id)">{{ d.name }}</el-tag>
          <span v-if="!selected.dept.length" class="npt-group-zero">（0）</span>
        </div>
        <div class="npt-display-group">
          <span class="npt-group-name">角色</span>
          <el-tag v-for="r in selected.role" :key="r.id" size="small" closable class="npt-display-tag"
            @close="removeDisplayItem('role', r.id)">{{ r.name }}</el-tag>
          <span v-if="!selected.role.length" class="npt-group-zero">（0）</span>
        </div>
      </div>
      <span v-else class="npt-display-empty">点击选择发送对象（空 = 全员可见）</span>
      <el-icon class="npt-display-edit"><Edit /></el-icon>
    </div>

    <!-- 穿梭框弹框：左侧三个 tab 可选对象，右侧为所选对象 -->
    <el-dialog v-model="dialogOpen" title="选择发送对象" width="900px" append-to-body destroy-on-close
      @opened="syncLeftSelection">
      <div class="npt-body">
        <!-- 左侧：三个 tab 的可选对象 -->
        <div class="npt-panel npt-left">
          <el-tabs v-model="activeTab" class="npt-tabs">
            <el-tab-pane label="用户" name="user">
              <div class="npt-search">
                <el-input v-model="userQuery.keyword" placeholder="昵称/用户名" clearable size="small"
                  @keyup.enter="loadUsers(1)" @clear="loadUsers(1)">
                  <template #append>
                    <el-button size="small" @click="loadUsers(1)">搜索</el-button>
                  </template>
                </el-input>
              </div>
              <el-checkbox-group v-model="userChecked" class="npt-list">
                <el-checkbox v-for="u in userList" :key="u.id" :value="u.id" class="npt-item">
                  {{ u.nickname || u.username }}
                </el-checkbox>
              </el-checkbox-group>
              <el-empty v-if="!userList.length" description="暂无用户" :image-size="50" />
              <el-pagination class="npt-page" small background layout="prev, pager, next" :total="userTotal"
                :page-size="userQuery.pageSize" v-model:current-page="userQuery.pageNum" @current-change="loadUsers()" />
            </el-tab-pane>

            <el-tab-pane label="部门" name="dept">
              <div class="npt-search">
                <el-input v-model="deptKeyword" placeholder="部门名称" clearable size="small" />
              </div>
              <div class="npt-list">
                <el-tree ref="deptTreeRef" :data="deptTree" show-checkbox node-key="id" default-expand-all
                  check-strictly :props="{ label: 'name', children: 'children' }" :filter-node-method="filterDeptNode"
                  @check="handleDeptCheck" />
                <el-empty v-if="!deptTree.length" description="暂无部门" :image-size="50" />
              </div>
            </el-tab-pane>

            <el-tab-pane label="角色" name="role">
              <div class="npt-search">
                <el-input v-model="roleKeyword" placeholder="角色名称" clearable size="small" />
              </div>
              <el-checkbox-group v-model="roleChecked" class="npt-list">
                <el-checkbox v-for="r in filteredRoleList" :key="r.id" :value="r.id" class="npt-item">
                  {{ r.name }}
                </el-checkbox>
              </el-checkbox-group>
              <el-empty v-if="!filteredRoleList.length" description="暂无角色" :image-size="50" />
            </el-tab-pane>
          </el-tabs>
          <div class="npt-add">
            <el-button type="primary" size="small" :disabled="leftCheckedCount === 0" @click="addChecked">
              添加至已选
              <el-icon class="el-icon--right"><ArrowRight /></el-icon>
            </el-button>
          </div>
        </div>

        <!-- 右侧：已选对象 -->
        <div class="npt-panel npt-right">
          <div class="npt-right-head">
            <span>已选对象（{{ totalSelected }}）</span>
            <el-button v-if="totalSelected" text type="danger" size="small" @click="clearAll">清空</el-button>
          </div>
          <div class="npt-selected">
            <div v-if="selected.user.length" class="npt-group">
              <div class="npt-group-label">用户</div>
              <div class="npt-group-tags">
                <el-tag v-for="u in selected.user" :key="u.id" closable @close="removeItem('user', u.id)">{{ u.name }}</el-tag>
              </div>
            </div>
            <div v-if="selected.dept.length" class="npt-group">
              <div class="npt-group-label">部门</div>
              <div class="npt-group-tags">
                <el-tag v-for="d in selected.dept" :key="d.id" closable @close="removeItem('dept', d.id)">{{ d.name }}</el-tag>
              </div>
            </div>
            <div v-if="selected.role.length" class="npt-group">
              <div class="npt-group-label">角色</div>
              <div class="npt-group-tags">
                <el-tag v-for="r in selected.role" :key="r.id" closable @close="removeItem('role', r.id)">{{ r.name }}</el-tag>
              </div>
            </div>
            <el-empty v-if="!totalSelected" description="未选择推送对象（默认全员可见）" :image-size="60" />
          </div>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="confirmDialog">确 定</el-button>
          <el-button @click="cancelDialog">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ArrowRight, Edit } from '@element-plus/icons-vue'
import { getUserListApi, getUserDetailApi } from '@/api/system/user'
import { getDeptTreeApi } from '@/api/system/dept'
import { getAllRoleList } from '@/api/system/role'

interface SelectedItem {
  id: number
  name: string
}

const props = defineProps<{ modelValue?: string | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>()

const dialogOpen = ref(false)

const activeTab = ref<'user' | 'dept' | 'role'>('user')
const userChecked = ref<number[]>([])
const roleChecked = ref<number[]>([])

const userList = ref<any[]>([])
const userTotal = ref(0)
const userQuery = reactive({ pageNum: 1, pageSize: 100, keyword: undefined })

const deptTree = ref<any[]>([])
const deptTreeRef = ref()

const roleList = ref<any[]>([])

// 部门/角色 tab 关键词搜索
const deptKeyword = ref('')
const roleKeyword = ref('')

// 部门树过滤：名称包含关键词（el-tree filter-node-method）
const filterDeptNode = (value: string, data: any) => {
  if (!value) return true
  return (data.name || '').includes(value)
}

const filteredRoleList = computed(() => {
  if (!roleKeyword.value) return roleList.value
  const kw = roleKeyword.value.trim()
  if (!kw) return roleList.value
  return roleList.value.filter(r => (r.name || '').includes(kw))
})

// 已选对象：id + 显示名（名从接口加载/补全，缺省回退为 id）
const selected = reactive<{ user: SelectedItem[]; dept: SelectedItem[]; role: SelectedItem[] }>({
  user: [],
  dept: [],
  role: [],
})

// 名称缓存，避免重复请求
const nameMaps = reactive<{ user: Record<number, string>; dept: Record<number, string>; role: Record<number, string> }>({
  user: {},
  dept: {},
  role: {},
})

// 部门树勾选（check-strictly：父子独立勾选，选子不选父；勾选某部门时其下级同步选中）
const deptCheckedIds = ref<number[]>([])

const totalSelected = computed(() => selected.user.length + selected.dept.length + selected.role.length)

const leftCheckedCount = computed(() => {
  if (activeTab.value === 'user') return userChecked.value.length
  if (activeTab.value === 'dept') return deptCheckedIds.value.length
  return roleChecked.value.length
})

// 记录自己 emit 的值，避免父组件回传触发重复初始化
let lastEmitted: string | undefined

// 仅在弹框“确定”时向父级提交；弹框内操作只改内部状态（支持取消回滚）
const emitValue = () => {
  const json = JSON.stringify({
    user: selected.user.map(i => i.id),
    dept: selected.dept.map(i => i.id),
    role: selected.role.map(i => i.id),
  })
  lastEmitted = json
  emit('update:modelValue', json)
}

const parseModel = (val: string | null | undefined) => {
  let obj: any = { user: [], dept: [], role: [] }
  if (typeof val === 'string' && val.trim()) {
    try {
      obj = JSON.parse(val)
    } catch (e) {
      obj = { user: [], dept: [], role: [] }
    }
  }
  selected.user = (obj.user || []).map((id: any) => {
    const num = Number(id)
    return { id: num, name: nameMaps.user[num] ?? String(num) }
  })
  selected.dept = (obj.dept || []).map((id: any) => {
    const num = Number(id)
    return { id: num, name: nameMaps.dept[num] ?? String(num) }
  })
  selected.role = (obj.role || []).map((id: any) => {
    const num = Number(id)
    return { id: num, name: nameMaps.role[num] ?? String(num) }
  })
}

const loadUsers = (page?: number) => {
  if (page) userQuery.pageNum = page
  getUserListApi({
    pageNum: userQuery.pageNum,
    pageSize: userQuery.pageSize,
    keyword: userQuery.keyword || undefined,
  }).then(({ data }) => {
    userList.value = data.records || []
    userTotal.value = data.total || 0
    userList.value.forEach(u => {
      nameMaps.user[Number(u.id)] = u.nickname || u.username || String(u.id)
    })
    // 补全已选用户名字
    selected.user.forEach(su => {
      if (nameMaps.user[su.id]) su.name = nameMaps.user[su.id]
    })
    syncUserChecked()
  })
}

const loadDeptTree = async () => {
  const { data } = await getDeptTreeApi()
  deptTree.value = data || []
  const walk = (nodes: any[]) => {
    nodes.forEach(n => {
      nameMaps.dept[Number(n.id)] = n.name || String(n.id)
      if (n.children?.length) walk(n.children)
    })
  }
  walk(deptTree.value)
  selected.dept.forEach(d => {
    if (nameMaps.dept[d.id]) d.name = nameMaps.dept[d.id]
  })
  nextTick(() => {
    deptTreeRef.value?.setCheckedKeys(selected.dept.map(d => d.id))
  })
}

const loadRoles = async () => {
  const { data } = await getAllRoleList()
  roleList.value = data || []
  roleList.value.forEach(r => {
    nameMaps.role[Number(r.id)] = r.name || String(r.id)
  })
  selected.role.forEach(r => {
    if (nameMaps.role[r.id]) r.name = nameMaps.role[r.id]
  })
}

// 已选用户不在当前页时逐个取详情补全名字
const resolveUserNames = async () => {
  const missing = selected.user.filter(su => !nameMaps.user[su.id])
  for (const su of missing) {
    try {
      const { data } = await getUserDetailApi(su.id)
      const name = data?.nickname || data?.username
      if (name) {
        nameMaps.user[su.id] = name
        su.name = name
      }
    } catch (e) {
      /* 取不到就回退显示 id */
    }
  }
}

const initData = () => {
  loadUsers()
  loadDeptTree()
  loadRoles()
  resolveUserNames()
}

// watch 放在所有函数定义之后：immediate 会在 setup 期间同步执行回调，引用未初始化的函数会抛 TDZ 错误
watch(
  () => props.modelValue,
  (val) => {
    // 由本组件 emit 回传的值，状态已同步，跳过重复初始化
    if (typeof val === 'string' && val === lastEmitted) return
    parseModel(val)
    initData()
  },
  { immediate: true },
)

// 部门关键词变化时过滤树（清空输入即恢复）
watch(deptKeyword, (val) => {
  deptTreeRef.value?.filter(val || '')
})

// 弹框控制：弹框内操作只改内部状态，确定才提交（取消可回滚到上一次确定的值）
const openDialog = () => {
  parseModel(props.modelValue)
  dialogOpen.value = true
}

// 弹框渲染完成后（@opened）左侧同步已选：部门树勾选、角色勾选、当前页用户勾选
const syncLeftSelection = () => {
  deptTreeRef.value?.setCheckedKeys(selected.dept.map(d => d.id))
  roleChecked.value = selected.role.map(r => r.id)
  syncUserChecked()
}

// 已选用户中出现在当前列表页的标记为勾选（分页限制：不在当前页的用户无法展示勾选，可搜索定位）
const syncUserChecked = () => {
  const selectedIds = new Set(selected.user.map(u => u.id))
  userChecked.value = userList.value
    .filter(u => selectedIds.has(Number(u.id)))
    .map(u => Number(u.id))
}

const confirmDialog = () => {
  emitValue()
  dialogOpen.value = false
}

const cancelDialog = () => {
  parseModel(props.modelValue)
  userChecked.value = []
  roleChecked.value = []
  deptCheckedIds.value = selected.dept.map(d => d.id)
  nextTick(() => {
    deptTreeRef.value?.setCheckedKeys(selected.dept.map(d => d.id))
  })
  dialogOpen.value = false
}

// 收集某部门的全部下级部门 id（不含自身）
const collectDeptIds = (node: any): number[] => {
  const ids: number[] = []
  const walk = (n: any) => {
    n.children?.forEach((c: any) => {
      ids.push(Number(c.id))
      walk(c)
    })
  }
  walk(node)
  return ids
}

// check-strictly：父子独立。勾选某部门 → 其所有下级部门同步选中（选父带子）；
// 取消勾选某部门 → 不连带下级；取消勾选子部门 → 不动父部门（选子不选父）
const handleDeptCheck = (data: any, checkedInfo: { checkedKeys: any[] }) => {
  const base = new Set<number>((checkedInfo.checkedKeys as any[]).map(Number))
  // 仅本次操作为“勾选”该部门时，把其全部下级加入目标并同步勾选树
  if (base.has(Number(data.id))) {
    collectDeptIds(data).forEach(id => base.add(id))
  }
  const ids = [...base]
  deptCheckedIds.value = ids
  selected.dept = ids.map(id => ({ id, name: nameMaps.dept[id] ?? String(id) }))
  // setCheckedKeys 触发的是 check-change 而非 check，不会造成递归
  deptTreeRef.value?.setCheckedKeys(ids)
}

const addChecked = () => {
  if (activeTab.value === 'user') {
    userChecked.value.forEach(id => {
      const num = Number(id)
      if (!selected.user.some(u => u.id === num)) {
        selected.user.push({ id: num, name: nameMaps.user[num] ?? String(num) })
      }
    })
    // 不清空勾选：保持与右侧已选同步，再次添加自动去重
  } else if (activeTab.value === 'role') {
    roleChecked.value.forEach(id => {
      const num = Number(id)
      if (!selected.role.some(r => r.id === num)) {
        selected.role.push({ id: num, name: nameMaps.role[num] ?? String(num) })
      }
    })
    // 不清空勾选：保持与右侧已选同步，再次添加自动去重
  } else {
    handleDeptCheck()
  }
}

const removeItem = (type: 'user' | 'dept' | 'role', id: number) => {
  const arr = selected[type]
  const idx = arr.findIndex(i => i.id === id)
  if (idx > -1) arr.splice(idx, 1)
  // 左侧同步反选
  if (type === 'dept') {
    deptCheckedIds.value = selected.dept.map(d => d.id)
    deptTreeRef.value?.setCheckedKeys(deptCheckedIds.value)
  } else if (type === 'user') {
    userChecked.value = userChecked.value.filter(x => Number(x) !== id)
  } else {
    roleChecked.value = roleChecked.value.filter(x => Number(x) !== id)
  }
}

// 展示区删除某个具体对象 tag：先移除并左侧反选，再立即提交（显示区操作直接生效）
const removeDisplayItem = (type: 'user' | 'dept' | 'role', id: number) => {
  removeItem(type, id)
  emitValue()
}

const clearAll = () => {
  selected.user = []
  selected.dept = []
  selected.role = []
  userChecked.value = []
  roleChecked.value = []
  deptCheckedIds.value = []
  deptTreeRef.value?.setCheckedKeys([])
}
</script>

<style scoped lang="scss">
.notice-push-transfer {
  width: 100%;

  // 展示区：多行 tag，点击弹出穿梭框
  .npt-display {
    position: relative;
    display: flex;
    align-items: flex-start;
    gap: 8px;
    min-height: 60px;
    max-height: 140px;
    overflow-y: auto;
    padding: 6px 32px 6px 10px;
    border: 1px solid var(--el-border-color);
    border-radius: var(--el-border-radius-base);
    background-color: var(--el-fill-color-light);
    cursor: pointer;
    transition: border-color 0.2s;

    &:hover {
      border-color: var(--el-color-primary);
    }

    // 三个分类分组，每行：分类名 + 具体对象 tag
    .npt-display-groups {
      display: flex;
      flex-direction: column;
      gap: 6px;
      width: 100%;
    }

    .npt-display-group {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px;
      line-height: 22px;
    }

    .npt-group-name {
      font-size: 12px;
      font-weight: 600;
      color: var(--el-text-color-primary);
      flex-shrink: 0;
    }

    .npt-group-zero {
      font-size: 12px;
      color: var(--el-text-color-placeholder);
    }

    .npt-display-empty {
      color: var(--el-text-color-placeholder);
      font-size: 13px;
      line-height: 26px;
    }

    .npt-display-edit {
      position: absolute;
      top: 50%;
      right: 10px;
      transform: translateY(-50%);
      color: var(--el-text-color-secondary);
    }
  }
}

// 穿梭框弹框主体：左右两栏
.npt-body {
  display: flex;
  gap: 12px;
  width: 100%;

  .npt-panel {
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    padding: 10px;
    min-height: 360px;
  }

  .npt-left {
    flex: 1 1 46%;
    display: flex;
    flex-direction: column;
    gap: 8px;

    .npt-tabs {
      flex: 1;
      overflow: hidden;
    }

    .npt-search {
      margin-bottom: 8px;
    }

    .npt-list {
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-height: 220px;
      max-height: 280px;
      overflow-y: auto;
    }

    .npt-item {
      width: 100%;
      margin-right: 0;
      height: 32px;
      line-height: 32px;
    }

    .npt-page {
      margin-top: 8px;
      justify-content: center;
    }

    .npt-add {
      text-align: right;
    }
  }

  .npt-right {
    flex: 1 1 54%;
    display: flex;
    flex-direction: column;

    .npt-right-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0 2px 8px;
      border-bottom: 1px solid var(--el-border-color-lighter);
      font-weight: 600;
    }

    .npt-selected {
      flex: 1;
      overflow-y: auto;
      margin-top: 8px;
    }

    .npt-group {
      margin-bottom: 10px;

      .npt-group-label {
        font-size: 12px;
        color: var(--el-text-color-secondary);
        margin-bottom: 6px;
        display: block;
      }

      .npt-group-tags {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
      }
    }
  }
}
</style>
