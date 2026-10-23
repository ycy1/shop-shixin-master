# TableMoreActions 表格操作列组件

把表格操作列的多颗按钮智能分组：数量超过 `maxVisible` 时自动把多余的收进「更多」下拉。

## 功能特性

- ✅ 自动分组：按钮数量超过 `maxVisible` 时自动收进「更多」下拉菜单
- ✅ 三态分开：`hidden` 不渲染、`disabled` 渲染但点不动、`tip` 悬浮说明为什么点不动
- ✅ 类型丰富：支持 primary/success/warning/danger/info 等多种类型
- ✅ 图标支持：支持 Element Plus 图标名（字符串形式）
- ✅ 行内 loading：某项 `loading` 时该项禁用，「更多」自己变 loading，免得重复触发

## Props

| 参数 | 说明 | 类型 | 默认值 |
|------|------|------|--------|
| actions | 操作按钮配置数组 | `ActionItem[]` | - |
| maxVisible | 最大直出按钮数（超出则显示更多下拉） | `number` | `3` |

### ActionItem 接口

```typescript
interface ActionItem {
  label: string        // 按钮文本，同时用作 key
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info'  // 按钮类型，默认 primary
  icon?: string        // 图标名称，如 'Edit'
  hidden?: boolean     // 整条不渲染
  disabled?: boolean   // 渲染但置灰、点不动
  tip?: string         // 悬浮提示，一般配 disabled 说明「现在为什么做不了」
  loading?: boolean    // 处理中：本条禁用 + 显示 loading
  command?: any        // 自定义数据，通常放 { type, row }
}
```

## 三个状态字段怎么选

这三个字段各管一件事，别混用：

| 场景 | 写法 | 用户看到 |
|------|------|----------|
| 这个账号没这个权限 | `hidden: !hasPermission('xxx')` | 按钮根本不存在 —— 不给看，也别让人猜 |
| 有权限，但这条数据现在做不了 | `disabled: true, tip: '原因'` | 按钮在、置灰，悬浮说清为什么 |
| 有权限、能做，正在跑 | `loading: true` | 按钮转圈，点第二下不会重复提交 |

`disabled` 一定要配 `tip`：只给一颗点不动的按钮，用户只会以为页面坏了。

## Events

| 事件名 | 说明 | 回调参数 |
|--------|------|----------|
| command | 点击任意按钮（直出的和下拉里的都走这一个） | `(action: ActionItem) => void` |

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
  const { type, row } = action.command
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

### 权限控制

组件收的是 `actions` 数组，`v-permission` 指令用不上（那指令作用在元素上），所以权限在页面里判：

```vue
<template>
  <TableMoreActions :actions="[
    { label: '修改', icon: 'Edit', hidden: !hasPermission('sys:user:update'), command: { type: 'edit', row: scope.row } },
    { label: '重置密码', icon: 'Key', hidden: !hasPermission('sys:user:reset'), command: { type: 'resetPwd', row: scope.row } },
    { label: '删除', type: 'danger', icon: 'Delete', hidden: !hasPermission('sys:user:delete'), command: { type: 'delete', row: scope.row } }
  ]" @command="handleActionCommand" />
</template>

<script setup lang="ts">
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const permissions = computed(() => userStore.user.permissions || [])
const hasPermission = (permission: string): boolean => permissions.value.includes(permission)
</script>
```

### 置灰 + 悬浮解释

业务上现在做不了、但要让人知道有这个功能（也说得清为什么）：

```vue
<template>
  <TableMoreActions :actions="[
    { label: '详情', icon: 'View', command: { type: 'detail', row: scope.row } },
    {
      label: '删除',
      type: 'danger',
      icon: 'Delete',
      disabled: !!deleteTip(scope.row),
      tip: deleteTip(scope.row),
      command: { type: 'delete', row: scope.row }
    }
  ]" @command="handleActionCommand" />
</template>

<script setup lang="ts">
/** 返回空串表示可以删；这一份提示必须和服务端 deleteByIds 那两道闸同一口径 */
const deleteTip = (row: any) => {
  if (Number(row.source) === 1) { return '这是买家已下单的线上订单，不能删除' }
  if (Number(row.totalOut || 0) > 0) { return '已经出过货，不能删除' }
  return ''
}
</script>
```

### 自定义最大可见数

```vue
<TableMoreActions
  :actions="actions"
  :max-visible="2"  <!-- 最多直出 2 颗，其余进「更多」下拉 -->
  @command="handleActionCommand"
/>
```

## 注意事项

1. **图标使用**：`icon` 用字符串形式的图标名，如 `'Edit'`、`'Delete'`、`'View'`。
2. **`disabled` 和 `tip` 一起用**：置灰而不给解释，等于把判断结果藏起来让用户自己试。
3. **权限用 `hidden` 不用 `disabled`**：一个页面里权限不够的操作建议整条不渲染，跟"这条数据暂时不能操作"区分开。
4. **置灰只是前端的提示**：真正的闸在服务端。前端因为快照过期而放行了，服务端还是会拒。
5. **事件只有一个**：`@command` 管所有按钮，直出和下拉里的都走它，不用分别监听。
6. **按钮数量**：建议操作列不超过 5 颗，再多用户体验就不好了。
