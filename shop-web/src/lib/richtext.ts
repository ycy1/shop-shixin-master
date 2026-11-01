/**
 * 后台富文本（WangEditor 存进库的那串 HTML）渲染到前台之前过的一道白名单。
 *
 * 为什么要有这一份：活动详情页那条接口（GET /api/web/shop/activity/{id}）是全站唯一把 content 递到
 * 不要登录的端点上的地方，而 content 是后台表单里那一格自由文本 —— 直接 v-html 就等于
 * 「谁能登进后台的活动管理，谁就能在所有消费者面前执行脚本」。
 *
 * 两道都在才算挡住：后端只把 content 给这一条端点（列表和首页轮播那两条的列清单里根本没有它），
 * 这一份再把标签和属性收到「编辑器本来就会产出的那一些」。
 *
 * 只认白名单、不做黑名单：漏掉一个危险标签（object、svg、math、form…）就是一次注入，
 * 而漏掉一个排版标签只是少点样式。
 */

/** 标签 → 允许留下的属性（style 单独按属性名再收一道，见下面那份）；不在这一份里的标签整个拆掉、只留里面的文字 */
const ALLOWED_TAGS: Record<string, string[]> = {
  p: [],
  br: [],
  hr: [],
  div: ['style'],
  span: ['style'],
  h1: [], h2: [], h3: [], h4: [], h5: [], h6: [],
  strong: [], b: [], em: [], i: [], u: [], s: [], strike: [], sub: [], sup: [],
  blockquote: ['style'],
  pre: [], code: [],
  ul: ['style'], ol: ['style'], li: ['style'],
  a: ['href', 'title', 'target', 'rel'],
  img: ['src', 'alt', 'width', 'height', 'style'],
  table: ['style'], thead: [], tbody: [], tfoot: [], tr: [], td: ['colspan', 'rowspan', 'style'], th: ['colspan', 'rowspan', 'style'],
};

/** style 只留排版这几样：CSS 里的 expression / url(javascript:) 都是历史坑，值再单独挡一道 */
const ALLOWED_STYLE = new Set([
  'color', 'background-color', 'background', 'font-size', 'font-family', 'font-weight', 'font-style',
  'text-align', 'text-decoration', 'line-height', 'text-indent', 'letter-spacing',
  'margin', 'margin-left', 'padding', 'height', 'width', 'max-width',
  'list-style-type', 'border', 'border-collapse', 'border-spacing',
]);

/** 链接和图只放这几种前缀：javascript: / data:text/html 都是能执行东西的 */
const SAFE_URL = /^(https?:|mailto:|tel:)/i;
/** 图片另放 data:image（后台偶尔贴进来一张内联小图），但只认这几种位图 */
const SAFE_IMG = /^(https?:|data:image\/(png|jpe?g|gif|webp);base64,)/i;

/** 这些标签连里面的内容一起删，不是拆壳留字（script 留字会变成一屏乱码，style 留字会漏进页面） */
const DROP_WITH_CONTENT = 'script, style, iframe, object, embed, link, meta, base, form, input, button, textarea, select, option, svg, math';

function cleanStyle(value: string): string {
  return value
    .split(';')
    .map((line) => line.trim())
    .filter(Boolean)
    .filter((line) => {
      const [prop = '', ...rest] = line.split(':');
      const url = rest.join(':');
      // 值里带 url( 的一律不收（挡不住 @import 之外的花样，但 javascript:/data: 都从这条路进来）
      return ALLOWED_STYLE.has(prop.toLowerCase().trim()) && !/url\s*\(/i.test(url);
    })
    .join('; ');
}

/**
 * 交回一段可以放心 v-html 的 HTML。
 *
 * 只在浏览器那一趟调用（DOMParser 是浏览器 API，岛在服务端那一趟不跑这一句）——
 * 活动详情本来就是 onMounted 里捞回来的，捞到了才清洗，天然错开服务端。
 */
export function sanitizeRichText(html: string): string {
  if (!html) return '';
  // 惰性文档：解析出来的 script 不会被执行，拆完再交回字符串
  const doc = new DOMParser().parseFromString(html, 'text/html');
  doc.querySelectorAll(DROP_WITH_CONTENT).forEach((node) => node.remove());

  // 快照一份再走：下面拆壳会把子节点提上来，实时集合边改边遍历会漏掉后半截
  for (const el of [...doc.body.querySelectorAll('*')]) {
    const tag = el.tagName.toLowerCase();
    const allowed = ALLOWED_TAGS[tag];
    if (!allowed) {
      // 不认识的标签只拆这一层壳，里面的文字和已收的标签留着（<font>、<u><span> 这类编辑器历史产物就落在这里）
      el.replaceWith(...el.childNodes);
      continue;
    }
    for (const name of [...el.attributes].map((attr) => attr.name)) {
      if (!allowed.includes(name.toLowerCase())) el.removeAttribute(name);
    }
    const lower = tag;
    if (lower === 'a') {
      const href = el.getAttribute('href') || '';
      if (!SAFE_URL.test(href)) el.removeAttribute('href');
      // 外链新窗口开：astro 的 ClientRouter 只拦同源 <a>，这一条是出站的，但 rel 还是要钉住
      if (el.getAttribute('href')) el.setAttribute('rel', 'noopener noreferrer nofollow');
    }
    if (lower === 'img') {
      const src = el.getAttribute('src') || '';
      if (!SAFE_IMG.test(src)) el.removeAttribute('src');
    }
    if (el.hasAttribute('style')) {
      const cleaned = cleanStyle(el.getAttribute('style') || '');
      if (cleaned) el.setAttribute('style', cleaned);
      else el.removeAttribute('style');
    }
  }

  return doc.body.innerHTML;
}
