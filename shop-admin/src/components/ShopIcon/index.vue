<template>
  <span class="shop-icon">
    <!-- SVG 只能靠 v-html 塞进 DOM，所以入库前必须先过 sanitizeSvg -->
    <span v-if="type === 1 && safeSvg" class="shop-icon__svg" v-html="safeSvg" :style="boxStyle"></span>
    <img v-else-if="type === 2 && safeUrl" class="shop-icon__img" :src="safeUrl" :alt="alt" :style="boxStyle"/>
    <i v-else-if="type === 3 && safeFontClass" class="shop-icon__font iconfont" :style="boxStyle" :class="safeFontClass"></i>
    <span v-else class="shop-icon__empty">-</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  /** 图标类型：1 SVG 2 图片URL 3 字体类名 */
  type?: number
  /** 图标内容：SVG 源码 / 图片URL / 字体类名 */
  value?: string
  /** 边长，数字按 px 处理 */
  size?: number | string
  alt?: string
}>(), {
  type: 1,
  value: '',
  size: 24,
  alt: ''
})

/**
 * 允许出现的 SVG 标签（按小写比较）。
 * 有意排除：script / foreignObject 能直接执行脚本；image / a 能引外部资源或
 * javascript: 协议；animate 系列和 set 可以挂 onbegin / onend 事件属性。
 */
const SVG_ALLOWED_TAGS = new Set([
  'svg', 'g', 'defs', 'symbol', 'use', 'path', 'rect', 'circle', 'ellipse',
  'line', 'polyline', 'polygon', 'text', 'tspan', 'title', 'desc',
  'lineargradient', 'radialgradient', 'stop', 'clippath', 'mask', 'pattern'
])

const cleanAttrs = (el: Element) => {
  // 遍历副本：removeAttribute 会改动 live 的 attributes 集合
  Array.from(el.attributes).forEach((attr) => {
    const name = attr.name.toLowerCase()
    const value = attr.value.trim()
    if (name.startsWith('on')) {
      el.removeAttribute(attr.name)
      return
    }
    if (name === 'href' || name === 'xlink:href') {
      // 只放行指向文档内 <defs> 的片段引用
      if (!value.startsWith('#')) {
        el.removeAttribute(attr.name)
      }
      return
    }
    if (name === 'style' && /url\s*\(|expression\s*\(|javascript:|@import/i.test(value)) {
      el.removeAttribute(attr.name)
    }
  })
}

/**
 * 白名单清洗 SVG。icon_value 是管理员填的自由文本，直接 v-html 就是存储型 XSS：
 * 存进去一段带 <script> 或 onload= 的 SVG，之后每个打开列表页的人都会中招。
 *
 * 这是够用的最小实现，不是完备方案 —— 如果图标库以后开放给普通用户提交，
 * 换成 DOMPurify（项目目前没有这个依赖）。
 */
const sanitizeSvg = (raw: string): string => {
  const doc = new DOMParser().parseFromString(raw, 'image/svg+xml')
  // XML 解析失败时文档里会有一个 parsererror 节点，这种情况下什么都不输出
  if (doc.getElementsByTagName('parsererror').length > 0) return ''
  const root = doc.documentElement
  if (!root || root.tagName.toLowerCase() !== 'svg') return ''

  const walk = (node: Element) => {
    Array.from(node.children).forEach((child) => {
      if (!SVG_ALLOWED_TAGS.has(child.tagName.toLowerCase())) {
        child.remove()
        return
      }
      cleanAttrs(child)
      walk(child)
    })
  }

  cleanAttrs(root)
  walk(root)

  // 缺 viewBox 时补一个，否则下面用 CSS 拉伸会变形
  if (!root.getAttribute('viewBox')) {
    const w = parseFloat(root.getAttribute('width') || '')
    const h = parseFloat(root.getAttribute('height') || '')
    if (w > 0 && h > 0) root.setAttribute('viewBox', `0 0 ${w} ${h}`)
  }
  // 尺寸统一交给外层容器控制
  root.removeAttribute('width')
  root.removeAttribute('height')

  return new XMLSerializer().serializeToString(root)
}

const safeSvg = computed(() => (props.type === 1 && props.value ? sanitizeSvg(props.value) : ''))

const safeUrl = computed(() => {
  if (props.type !== 2 || !props.value) return ''
  const url = props.value.trim()
  // 无协议的相对路径直接放行；带协议的只认 http(s) 和 data:image
  if (/^[a-z][a-z0-9+.-]*:/i.test(url) && !/^(https?:|data:image\/)/i.test(url)) return ''
  return url
})

const safeFontClass = computed(() => {
  if (props.type !== 3 || !props.value) return ''
  return props.value
    .trim()
    .split(/\s+/)
    .filter((name) => /^[\w-]+$/.test(name))
    .join(' ')
})

const boxStyle = computed(() => {
  const size = typeof props.size === 'number' ? `${props.size}px` : props.size
  return { width: size, height: size, fontSize: size }
})
</script>

<style scoped lang="scss">
.shop-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  line-height: 1;
  vertical-align: middle;
}

.shop-icon__svg,
.shop-icon__img {
  display: block;
  width: 24px;
  height: 24px;
}

/* v-html 出来的节点不带 scoped 属性，必须用 :deep */
.shop-icon__svg :deep(svg) {
  display: block;
  width: 100%;
  height: 100%;
}

.shop-icon__img {
  object-fit: contain;
}

.shop-icon__font {
  // font-family: "iconfont" !important;
  line-height: 1;
}

.shop-icon__empty {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>
