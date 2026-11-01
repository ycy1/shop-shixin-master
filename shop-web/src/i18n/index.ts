import { getCurrentInstance, inject, provide } from 'vue';
import { en as accountEn, ja as accountJa, ko as accountKo, zh as accountZh } from './account';
import { en as baseEn, ja as baseJa, ko as baseKo, zh as baseZh } from './base';
import { en as detailEn, ja as detailJa, ko as detailKo, zh as detailZh } from './detail';
import { en as enumsEn, ja as enumsJa, ko as enumsKo, zh as enumsZh } from './enums';
import { en as formsEn, ja as formsJa, ko as formsKo, zh as formsZh } from './forms';
import { en as homeEn, ja as homeJa, ko as homeKo, zh as homeZh } from './home';
import { en as plazaEn, ja as plazaJa, ko as plazaKo, zh as plazaZh } from './plaza';
import { en as trendyEn, ja as trendyJa, ko as trendyKo, zh as trendyZh } from './trendy';
import { en as tradeEn, ja as tradeJa, ko as tradeKo, zh as tradeZh } from './trade';
import { en as uiEn, ja as uiJa, ko as uiKo, zh as uiZh } from './ui';

/**
 * 语种怎么定下来的（这一格是整套东西里最不显然的，写清免得下次又踩）：
 *
 * astro.config.mjs 里钉了 routing:'manual' —— 这版 astro（7.3.5）实测不会替未加前缀的页面铺出 /en/xxx 路由，
 * 所以前缀那一层是自己在 src/middleware.ts 里剥的：认出 /en 就 rewrite 回原路径，语种挂在 context.locals.locale。
 * 页面壳（pages/*.astro）读 Astro.locals.locale 拿到 d，页头链接由 localeHref() 加前缀。
 *
 * 但岛（client:load 那些 Vue 组件）是 astro 单独渲染的一颗颗小程序，它拿不到 Astro 对象，
 * 所以页面要把 locale 当一颗普通 prop 递给每一颗岛，岛里 useI18n() 再 provide 给自己的子组件。
 * 为什么不只靠「客户端读 <html lang>」：岛在服务端那一趟也要渲染文案，
 * 那种合只在浏览器里认的取法会让服务端画中文、客户端画英文 ⇒ 整页文案报 Hydration mismatch（和 localStorage 那个坑同一类）。
 * 读 <html lang> 只当最后一道兜底，给忘了传 locale 的岛用：页面不至于整块空文字，退回中文那一档。
 */

/** 语种档位要和 astro.config.mjs 的 i18n.locales 一致；这两处各写一半的话，切语言会拼出不存在的 URL */
export const LOCALES = ['zh', 'en', 'ja', 'ko'] as const;
export type Locale = (typeof LOCALES)[number];
export const DEFAULT_LOCALE: Locale = 'zh';

/**
 * 一张表由几档拼成：base（页头/通用/各页标题/登录）+ enums（枚举称呼）+ 各区自己那一份。
 * 分区是刻意的 —— 全塞一处会滚成几千行，改一句要往下翻半天；各区那份四档并排，缺格照样当场报错。
 */
export const zh = { ...baseZh, ...enumsZh, ...homeZh, ...detailZh, ...plazaZh, ...trendyZh, ...tradeZh, ...accountZh, ...formsZh, ...uiZh };

/** 中文拼出来的形状就是其余三档必须对齐的标准：哪一区漏了格子，那一句当场对不上 */
export type Dict = typeof zh;
const en: Dict = { ...baseEn, ...enumsEn, ...homeEn, ...detailEn, ...plazaEn, ...trendyEn, ...tradeEn, ...accountEn, ...formsEn, ...uiEn };
const ja: Dict = { ...baseJa, ...enumsJa, ...homeJa, ...detailJa, ...plazaJa, ...trendyJa, ...tradeJa, ...accountJa, ...formsJa, ...uiJa };
const ko: Dict = { ...baseKo, ...enumsKo, ...homeKo, ...detailKo, ...plazaKo, ...trendyKo, ...tradeKo, ...accountKo, ...formsKo, ...uiKo };

const DICTS: Record<Locale, Dict> = { zh, en, ja, ko };

/** <html lang> 上要写完整语言标签：单写 zh 浏览器不一定认得出是简体，字体回退会挑走繁体那一套 */
export const HTML_LANG: Record<Locale, string> = { zh: 'zh-CN', en: 'en', ja: 'ja-JP', ko: 'ko-KR' };

/** 语言按钮上那几格：语种名一律按它自己的写法摆（中文档也写「English」），这是各家站点通用的规矩，不进文案表 */
export const LANG_NAMES: Record<Locale, string> = { zh: '中文', en: 'English', ja: '日本語', ko: '한국어' };

/** 岛与岛之间不共享组件实例，语种沿一棵树往下传只能靠 provide/inject 这一格 */
const LOCALE_KEY = Symbol('shop-locale');

/** 认识的语言标签都收下（en-US、zh-TW、zh_CN…），认不出的一律当默认档 */
export function normalizeLocale(value: unknown): Locale {
  const raw = typeof value === 'string' ? value.toLowerCase().replace(/[_\s]/g, '-') : '';
  if (raw.startsWith('en')) return 'en';
  if (raw.startsWith('zh') || raw === 'cn' || raw === 'zh-cn') return 'zh';
  if (raw.startsWith('ja')) return 'ja';
  if (raw.startsWith('ko')) return 'ko';
  return DEFAULT_LOCALE;
}

/**
 * 把「你要 {n} 件」这类带槽的句子填上数。
 * 槽名写死不铺 i18n 库那套复数规则：本项目里带数的句子就那几颗数量/金额，槽位由每档自己排，
 * 真要到「1 item / 2 items」那一步再引机制。
 */
export function fill(template: string, params: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (whole, name: string) => {
    const value = params[name];
    return value === undefined ? whole : String(value);
  });
}

/**
 * 枚举档位取称呼：下标就是库里那个码值，认不出（后台新加档而前台没跟上）就回这一句兜底。
 * 原来各页自己拼「状态 7」，现在统一走这一颗，四档都有话说。
 */
export function enumLabel(list: readonly string[], code: number | null | undefined, fallback = ''): string {
  if (code == null || code < 0) return fallback;
  return list[code] || fallback;
}

/** 下拉候选：把枚举表按码值升铺成一串 { value, label }，空串那格（码值不从 0 起的占位）跳过 */
export function enumChoices(list: readonly string[], from = 1): { value: number; label: string }[] {
  const out: { value: number; label: string }[] = [];
  for (let code = from; code < list.length; code += 1) {
    const label = list[code];
    if (label) out.push({ value: code, label });
  }
  return out;
}

/**
 * 站内链接按语种加前缀：中文是默认档不带前缀（前缀那一层由 middleware 自己剥，见这一格顶上那段），
 * 其余三档在路径前面挂 /en、/ja、/ko。
 *
 * astro 官方那颗同名的 helper（astro:i18n 的 localizeHref / getRelativeLocaleUrl）只能读 Astro 对象，
 * 岛里（客户端那一侧）拿不到，所以全站共用这一颗：两头都认它，就不会出现「页头的链接对了、卡片里的链接漏了前缀」。
 * 传进来的路径带着 ?q= 或 # 也无所谓 —— 前缀只加在最前面，后面那串原样跟着走。
 */
export function localeHref(locale: Locale, path: string): string {
  if (locale === DEFAULT_LOCALE) return path;
  return `/${locale}${path.startsWith('/') ? '' : '/'}${path}`;
}

export interface I18n {
  locale: Locale;
  /** 文案表，模板里写 d.cart.title 这样直接取 */
  d: Dict;
  fill: typeof fill;
  /** 站内链接加语种前缀：:href="href(`/product/${id}`)" */
  href: (path: string) => string;
}

/** .astro 的 frontmatter 用这一颗：把 Astro.currentLocale 交给它，拿到 d */
export function useDict(value: unknown): I18n {
  const locale = normalizeLocale(value);
  return { locale, d: DICTS[locale], fill, href: (path) => localeHref(locale, path) };
}

/** 岛里的组件（含岛的子组件）用这一颗 */
export function useI18n(): I18n {
  const instance = getCurrentInstance();
  const injected = inject<string>(LOCALE_KEY, '');
  const props = instance?.props as Record<string, unknown> | undefined;
  const attrs = instance?.attrs as Record<string, unknown> | undefined;
  const fromDocument = typeof document === 'undefined' ? '' : document.documentElement.lang;

  const i18n = useDict(injected || props?.locale || attrs?.locale || fromDocument);
  // 这一趟把自己认到的语种钉在棵上，下面整棵子树（弹窗里再嵌的那些也算）就不用各读一遍
  provide(LOCALE_KEY, i18n.locale);
  return i18n;
}

/** 筛选下拉的候选（FilterSelect 吃的是 { id, label }）：能整体挪一格 —— 真值 0 那一档和「不限」那颗哨兵撞，调用方发请求前减回去 */
export function enumFilters(list: readonly string[], shift = 0, from = 0): { id: number; label: string }[] {
  const out: { id: number; label: string }[] = [];
  for (let code = from; code < list.length; code += 1) {
    const label = list[code];
    if (label) out.push({ id: code + shift, label });
  }
  return out;
}

/**
 * 把带语种前缀的路径还原成不带前缀的那一份，切语言时拿它去拼另一个语种的 URL。
 * 只看头一段：/en/cart → /cart，/cart → /cart，而 /product/en 这种（en 是商品 id）不会被误剥。
 * 出来的这份始终带领头那道 / —— 拼进 href 就当绝对路径用，少一道 / 会变成相对链接（/en/search 上切中文得 search）。
 */
export function stripLocalePrefix(pathname: string): string {
  const segments = pathname.split('/').filter((segment) => segment !== '');
  const first = segments[0]?.toLowerCase();
  if (first && (LOCALES as readonly string[]).includes(first)) segments.shift();
  return `/${segments.join('/')}`;
}
