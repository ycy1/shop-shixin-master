# TableMoreActions 表格操作列组件

将表格操作列的多个按钮智能分组，当按钮数量超过设定值时自动显示"更多"下拉菜单。

## 功能特性

- ✅ 自动分组：按钮数量超过 `maxVisible` 时自动隐藏到"更多"下拉菜单
- ✅ 权限控制：支持为每个按钮单独设置权限控制（disabled 状态）
- ✅ 类型丰富：支持 primary/success/warning/danger/info 等多种类型
- ✅ 图标支持：支持 Element Plus 图标
- ✅ 事件完整：提供 click 和 command 事件处理

## Props

| 参数 | 说明 | 类型 | 默认值 |
|------|------|------|--------|
| actions | 操作按钮配置数组 | `ActionItem[]` | - |
| maxVisible | 最大可见按钮数（超出则显示更多下拉） | `number` | `3` |

### ActionItem 接口

```typescript
interface ActionItem {
  label: string        // 按钮文本
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info'  // 按钮类型
  icon?: string        // 图标名称
  disabled?: boolean   // 是否禁用
  command?: any        // 自定义命令数据
}
```

## Events

| 事件名 | 说明 | 回调参数 |
|--------|------|----------|
| click | 点击直接显示的按钮 | `(action: ActionItem) => void` |
| command | 点击下拉菜单中的按钮 | `(action: ActionItem) => void` |

## 使用方法

### 基础用法

```vue
<template>
  <el-table :data="tableData">
    <el-table-column label="操作" width="200">
      <template #default="scope">
        <TableMoreActions
          :actions="[
            { label: '编辑', icon: 'Edit', command: { type: 'edit', row: scope.row } },
            { label: '删除', type: 'danger', icon: 'Delete', command: { type: 'delete', row: scope.row } },
            { label: '查看', icon: 'View', command: { type: 'view', row: scope.row } }
          ]"
          @command="handleActionCommand"
        />
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import TableMoreActions from '@/components/TableMoreActions/index.vue'

const handleActionCommand = (action: any) => {
  const { type, row } = action
  switch (type) {
    case 'edit':
      handleEdit(row)
      break
    case 'delete':
      handleDelete(row)
      break
    case 'view':
      handleView(row)
      break
  }
}
</script>
```

### 带权限控制

```vue
<template>
  <TableMoreActions
    :actions="[
      {
        label: '编辑',
        icon: 'Edit',
        disabled: !hasPermission('sys:user:update'),
        command: { type: 'edit', row: scope.row }
      },
      {
        label: '删除',
        type: 'danger',
        icon: 'Delete',
        disabled: !hasPermission('sys:user:delete'),
        command: { type: 'delete', row: scope.row }
      },
      {
        label: '重置密码',
        icon: 'Refresh',
        disabled: !hasPermission('sys:user:resetPwd'),
        command: { type: 'resetPwd', row: scope.row }
      }
    ]"
    @command="handleActionCommand"
  />
</template>

<script setup lang="ts">
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const permissions = computed(() => userStore.user.permissions || [])

const hasPermission = (permission: string): boolean => {
  return permissions.value.includes(permission)
}
</script>
```

### 自定义最大可见数

```vue
<TableMoreActions
  :actions="actions"
  :max-visible="2"  <!-- 最多显示 2 个按钮，其余放入更多下拉 -->
  @command="handleActionCommand"
/>
```

## 完整示例

```vue
<template>
  <div class="app-container">
    <el-table v-loading="loading" :data="tableData">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="名称" prop="name" />
      <el-table-column label="创建时间" prop="createTime" width="180" />
      
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
                label: '删除',
                type: 'danger',
                icon: 'Delete',
                disabled: !hasPermission('sys:user:delete'),
                command: { type: 'delete', row: scope.row }
              },
              {
                label: '重置密码',
                icon: 'Refresh',
                disabled: !hasPermission('sys:user:resetPwd'),
                command: { type: 'resetPwd', row: scope.row }
              }
            ]"
            @command="handleActionCommand"
          />
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import TableMoreActions from '@/components/TableMoreActions/index.vue'
import { useUserStore } from '@/store/modules/user'
import { deleteUserApi } from '@/api/system/user'

// 权限检查
const userStore = useUserStore()
const permissions = computed(() => userStore.user.permissions || [])

const hasPermission = (permission: string): boolean => {
  return permissions.value.includes(permission)
}

// 处理操作命令
const handleActionCommand = async (action: any) => {
  const { type, row } = action

  switch (type) {
    case 'edit':
      handleUpdate(row)
      break
    case 'delete':
      await handleDelete(row)
      break
    case 'resetPwd':
      handleResetPwd(row)
      break
  }
}

// 删除操作
const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确认删除 "${row.name}" 吗？`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteUserApi(row.id)
    ElMessage.success('删除成功')
  } catch {
    // 用户取消
  }
}
</script>
```

## 注意事项

1. **图标使用**: `icon` 属性使用字符串形式的图标名称，如 `'Edit'`, `'Delete'`, `'View'`
2. **权限控制**: 通过 `disabled` 属性控制按钮是否可点击，建议结合用户权限系统使用
3. **事件选择**: 
   - 直接使用 `@click` 处理直接显示的按钮
   - 使用 `@command` 处理所有按钮（包括下拉菜单中的）
4. **按钮数量**: 建议操作列按钮不超过 5 个，否则用户体验不佳
