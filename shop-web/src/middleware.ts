import { defineMiddleware } from 'astro:middleware';
import { DEFAULT_LOCALE, LOCALES, type Locale } from '@/i18n';

/**
 * 语种前缀是自己剥的（i18n.routing 配成 manual 就是为这一条）。
 *
 * astro.config.mjs 里那格 i18n 还是留着：它管的是各档语言怎么称呼、以及 astro:i18n 那些 helper，
 * 但本机这版 astro（7.3.5）配 pathname-prefix-other-locales 时并不会替 /en/xxx 建出路由 —— 实测一律 404
 * （翻它随包发的产物也对得上：路由表里没有任何按 locale 复制一份 route 的环节，i18n 那段只做「校验和重定向」）。
 * 官方文档给的另一档就是 manual：「你可以自己实现路由逻辑」，所以这里就是那一档。
 *
 * 做法是把 /en/xxx 改写成站内现成的 /xxx 去渲染，再把认出来的语种挂到 locals.locale 上给页面读；
 * 中文是默认档不带前缀（/cart 照旧），这样现成的链接、活动正文里手写的 URL 都不用跟着改。
 */
function localeFromPathname(pathname: string): { locale: Locale; rest: string } {
  const [first, ...tail] = pathname.split('/').filter((segment) => segment !== '');
  const hit = (LOCALES as readonly string[]).includes((first ?? '').toLowerCase()) ? (first as string).toLowerCase() : '';
  if (!hit || hit === DEFAULT_LOCALE) return { locale: DEFAULT_LOCALE, rest: '' };
  return { locale: hit as Locale, rest: '/' + tail.join('/') };
}

/**
 * 给现渲的页面挂一条短缓存，是为了让 astro 的悬浮预加载真省一趟后端。
 *
 * 那套预加载取回来的那份能不能被点下去那一次复用，看的完全是响应上的缓存指令：prefetch 走
 * <link rel="prefetch">，astro 换页时发的又是普通 fetch，两边都没指定 cache 模式，就归浏览器默认那套。
 * 我们这一页是 output: 'server' 现渲的、原先不带 Cache-Control，浏览器存不住 ⇒ 悬浮那一趟白渲，
 * 点下去还得再要一次（dev 日志里能看见同一个 URL 紧挨着出现两回：一次上百毫秒、一次几毫秒）。
 *
 * 只 10 秒，是「后台改了价格/上下架，前台这一页最多旧 10 秒」的上限；再长就得不划算了。
 * private 是刻意的：这份 HTML 可能带着会话信息，别让中间代理替所有用户共用一份。
 */
const PAGE_MAX_AGE = 10;

export const onRequest = defineMiddleware(async (context, next) => {
  const { locale, rest } = localeFromPathname(context.url.pathname);
  context.locals.locale = locale;
  // 带前缀的那一条改成剥掉前缀的路径去渲染，查询串原样带上（/en/search?kw=x 里的 kw 还要留给页面）
  const response = rest ? await next(`${rest}${context.url.search}`) : await next();

  // 只处理 GET 拿到的那份 HTML 页面：接口、图片、_astro 那些静态资源走的是各自的缓存策略，别在这里盖掉
  if (context.request.method !== 'GET') return response;
  if (!response.headers.get('content-type')?.includes('text/html')) return response;

  const headers = new Headers(response.headers);
  headers.set('Cache-Control', `private, max-age=${PAGE_MAX_AGE}`);
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
});
