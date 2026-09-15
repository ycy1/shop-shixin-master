import { marked } from 'marked'
import hljs from 'highlight.js'

// 配置 marked 使用 highlight.js
marked.setOptions({
  breaks: true,
  gfm: true,
  highlight: function (code: string, lang: string) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang }).value
      } catch (_) { /* fall through */ }
    }
    return hljs.highlightAuto(code).value
  }
})

/**
 * 将 Markdown 文本渲染为 HTML（块级，带 <p> 包裹）
 */
export function renderMarkdown(text: string): string {
  if (!text) return ''
  try {
    // console.log((marked.parse(text) as string))
    return (marked.parse(text) as string)
  } catch (_) {
    return text
  }
}

/**
 * 将 Markdown 文本渲染为 HTML（保留空格和换行，用于流式输出）
 */
export function renderStreamingMarkdown(text: string): string {
  if (!text) return ''
  try {
    return (marked.parse(text) as string)
      .replace(/<\/?p>/g, '')          // 去掉 <p> 包裹
      .replace(/<br>\n?/g, '\n')        // <br> 换回 \n，由 white-space: pre-wrap 控制
  } catch (_) {
    return text
  }
}
