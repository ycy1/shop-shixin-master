<template>
  <span class="badge-view">
    <!-- 有图标就只显示图标（圆形裁切），右边跟着名称；
         没图标是普通的一行字（文字没了就退到名称），不铺底色也不写白字 ——
         原先那一层底色是 badge_color，这一格已经整条下线，徽章形状只剩「图标」和「那几个字」两种 -->
    <template v-if="icon">
      <el-image class="badge-view-icon" :src="icon" :style="boxStyle" fit="cover"
        :preview-src-list="preview ? [icon] : undefined" :preview-teleported="preview" />
      <span v-if="name" class="badge-view-name">{{ name }}</span>
    </template>
    <span v-else class="badge-view-text">{{ text || name }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  /** 图标 URL（快照列 badge_icon）：有值就只显示这一张图 */
  icon?: string
  /** 徽章文字（快照列 badge_text）：没有图标时铺在这一行上，留空退到名称 */
  text?: string
  /** 徽章名称（快照列 badge_name）：有图标时排在图标右边，没图标且文字也空时当那一行用 */
  name?: string
  /** 图标边长，数字按 px 处理 */
  size?: number | string
  /** 点开看图：列表那两处开，表单预览那一格不开（预览本来就在改这张图的表单里） */
  preview?: boolean
}>(), {
  icon: '',
  text: '',
  name: '',
  size: 44,
  preview: false
})

const boxStyle = computed(() => {
  const size = typeof props.size === 'number' ? `${props.size}px` : props.size
  return { width: size, height: size }
})
</script>

<style scoped lang="scss">
.badge-view {
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
}

/* 圆形靠自身宽高 + border-radius，不跟着表格列宽撑开 */
.badge-view-icon {
  flex: none;
  border-radius: 50%;
  overflow: hidden;
}

.badge-view-name {
  margin-left: 8px;
}

.badge-view-text {
  color: var(--el-text-color-regular);
  word-break: break-all;
}
</style>
