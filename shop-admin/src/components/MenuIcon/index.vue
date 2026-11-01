<template>
  <!-- 两种格式都套在 el-icon 里：侧边栏和面包屑的间距、字号都是打在 .el-icon 上的，
       换了图标来源不能把这套排版带丢 -->
  <el-icon v-if="record || legacyName" class="menu-icon">
    <shop-icon v-if="record" :type="record.iconType" :value="record.iconValue" :size="size" :alt="record.iconName" />
    <component v-else :is="legacyName" />
  </el-icon>
  <!-- 查不到才给占位符：图标被删掉或禁用了，这里显示成 - 而不是悄悄什么都不给 -->
  <span v-else-if="missing" class="menu-icon menu-icon--missing">-</span>
</template>

<script setup lang="ts">
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import ShopIcon from '@/components/ShopIcon/index.vue'
import { ensureIcons, iconRecord, iconsReady } from '@/utils/iconLibrary'

const props = withDefaults(defineProps<{
  /** sys_menu.icon 的原值：新的存 sys_icon.id，历史数据存的是 el-icon 组件名 */
  icon?: string | number
  /** 图标库那条按 px/em 定尺寸，默认跟着所在位置的字号走，和 el-icon 那档表现一致 */
  size?: number | string
}>(), {
  icon: '',
  size: '1em'
})

// 菜单图标可能在侧边栏、面包屑、列表里同时出现，图标库只要拉一次
ensureIcons()

/** 纯数字就按图标库 id 解，其它一律当作历史遗留的 el-icon 名 —— 这就是不做数据迁移也能显示的原因 */
const isLibraryId = computed(() => /^\d+$/.test(String(props.icon ?? '').trim()))

const record = computed(() => (isLibraryId.value ? iconRecord(String(props.icon)) : undefined))

/** 老值存得比较随意：'el-icon-setting'、'setting'、'Setting' 都见过，统一归一成注册时的组件名 */
const normalizeName = (raw: string) => {
  const name = raw.trim().replace(/^el-icon-/i, '')
  return name
    .split(/[-_]/)
    .filter((part) => part)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join('')
}

/** 只有真的在 el-icon 注册表里的名字才拿去渲染，否则 Vue 会对未知组件发警告并什么都不画 */
const legacyName = computed(() => {
  if (!props.icon || isLibraryId.value) return ''
  const name = normalizeName(String(props.icon))
  return (ElementPlusIconsVue as Record<string, unknown>)[name] ? name : ''
})

/** 图标库取回来了却查不到这一条，或者老名字在注册表里没有 —— 都算「这个图标没了」 */
const missing = computed(() => {
  if (!props.icon) return false
  if (isLibraryId.value) return iconsReady() && !record.value
  return !legacyName.value
})
</script>

<style scoped lang="scss">
.menu-icon--missing {
  width: auto;
  color: var(--el-text-color-placeholder);
  font-style: normal;
}
</style>
