<template>
  <div class="brand-option">
    <span class="brand-logo">
      <el-image v-if="logo" :src="logo" fit="contain">
        <!-- LOGO 地址失效时退回首字符，别留个破图 -->
        <template #error>{{ initial }}</template>
      </el-image>
      <span v-else>{{ initial }}</span>
    </span>
    <span class="brand-name">{{ brandName }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  brandName?: string
  /** 品牌列表接口里是 logo，商品联表回填的是 brandLogo，所以拆成具名入参由调用方给 */
  logo?: string
}>(), {
  brandName: '',
  logo: ''
})

// 没有 LOGO 时用首字符占位：英文转大写，中文就是第一个字
const initial = computed(() => (props.brandName || '?').trim().charAt(0).toUpperCase())
</script>

<style scoped lang="scss">
/* el-select-dropdown__item 是固定 34px 行高、非 flex、overflow:hidden 的块，
   所以居中只能做在自己的元素上；text-overflow 也只对父项自己的文字生效，
   长品牌名要在 .brand-name 上单独截断。 */
.brand-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 100%;
  line-height: 20px;
}

.brand-logo {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  overflow: hidden;
  font-size: 12px;
  color: var(--el-color-primary);
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

/* 子组件根元素会带上本文件的 scope 属性，所以不用 :deep */
.brand-logo .el-image {
  width: 100%;
  height: 100%;
}

.brand-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
