<template>
  <template v-if="isLeafOrDashboard">
    <!-- 叶子菜单：点击统一走 handleMenuClick（外链新窗口打开 / 内部路由跳转） -->
    <el-menu-item :index="resolvePath(route.path)" @click="handleMenuClick(route)">
      <el-icon v-if="route.meta?.icon">
        <component :is="route.meta.icon" />
      </el-icon>
      <template #title>{{ route.meta?.title }}</template>
    </el-menu-item>
  </template>
  <template v-else>
    <el-sub-menu :index="resolvePath(route.path)">
      <template #title>
        <el-icon v-if="route.meta?.icon">
          <component :is="route.meta.icon" />
        </el-icon>
        <span>{{ route.meta?.title }}</span>
      </template>
      <template v-for="child in route.children" :key="child.path">
        <menu-item
          v-if="!child.meta?.hidden"
          :route="child"
          :base-path="resolvePath(route.path)"
        />
      </template>
    </el-sub-menu>
  </template>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import validate from '@/utils/validate'

const props = defineProps<{
  route: any
  basePath: string
}>()

const currentRoute = useRoute()
const router = useRouter()

const isLeafOrDashboard = computed(() => {
  return !props.route.children ||
         props.route.children.length === 0 ||
         props.route.path === '/dashboard'
})

// 外链菜单：按 meta.isExternal 判断（兼容 URL 前缀外链）
const isExternalRoute = computed(() => {
  return props.route.meta?.isExternal === true || validate.isExternal(props.route.path)
})

// 菜单点击：接收完整的 route 对象；外链新窗口打开，内部路由跳转
const handleMenuClick = (menuRoute: any) => {
  const index = resolvePath(menuRoute.path)
  if (isExternalRoute.value) {
    window.open(index, '_blank')
    return
  }
  if (currentRoute.path !== index) {
    router.push(index)
  }
}

const resolvePath = (routePath: string) => {
  // 如果是外部链接，直接返回
  if (validate.isExternal(routePath)) {
    return routePath
  }

  // 如果是根路径，直接返回
  if (routePath === '/') {
    return routePath
  }

  // 如果是绝对路径（以/开头），直接返回
  if (routePath.startsWith('/')) {
    return routePath
  }

  // 如果是仪表盘路径，特殊处理
  if (routePath === 'dashboard') {
    return '/dashboard'
  }

  // 处理相对路径
  const path = props.basePath === '/'
    ? `/${routePath}`
    : `${props.basePath}/${routePath}`

  // 规范化路径，去除多余的斜杠
  return path.replace(/\/+/g, '/')
}
</script>
