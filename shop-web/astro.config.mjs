import { readFileSync } from 'node:fs';
import { defineConfig } from 'astro/config';
import vue from '@astrojs/vue';
import node from '@astrojs/node';
import tailwindcss from '@tailwindcss/vite';

/**
 * 代理目标只认 .env 里的 API_SERVER_BASE —— 服务端渲染那一趟（src/lib/api.ts）读的是同一格，
 * 在这儿再抄一遍地址的话，改端口就会只改对一半。
 */
function readApiTarget() {
  try {
    const raw = readFileSync(new URL('.env', import.meta.url), 'utf8');
    const value = raw.match(/^API_SERVER_BASE=(.+)$/m)?.[1];
    if (value) return value.trim().replace(/^["']|["']$/g, '');
  } catch {
    // .env 还没落地（照着 .env.example 起服务），走下面兜底
  }
  return 'http://127.0.0.1:8800';
}

const API_TARGET = readApiTarget();

export default defineConfig({
  site: 'http://localhost:4321',
  output: 'server',
  adapter: node({ mode: 'standalone' }),
  // 国际化：语种称呼、默认档在这一格定；URL 前缀那一层是自己实现的（见 src/middleware.ts 顶上那段为什么）。
  // locales 决定哪些前缀算「我们支持的语种」，astro:i18n 那组 helper 也读这一格；defaultLocale 是认不出前缀时落回的那一档。
  // routing: 'manual' 会让 astro 关掉它自带的 i18n 中间件，其它 routing 选项（prefixDefaultLocale 等）就不能再配。
  i18n: {
    locales: ['zh', 'en', 'ja', 'ko'],
    defaultLocale: 'zh',
    routing: 'manual',
  },
  // 悬浮预加载：鼠标在链接上停满 80ms 就先把那一页取回来（移开就撤，所以扫过一排不会全发出去），点下去少等一趟。
  // 这一格不开，astro 不会往每页注入 prefetch 那段运行时代码，只有显式写了 data-astro-prefetch 的链接才理。
  prefetch: { prefetchAll: true, defaultStrategy: 'hover' },
  integrations: [
    vue({ appEntrypoint: '/src/pages/_app' }),
  ],
  vite: {
    plugins: [tailwindcss()],
    // 止住「预构建 hash 一路变」：vite 开机那趟扫依赖，只从 html/入口找得到一部分，
    // 剩下这些要等某一页真 import 才「发现新依赖」并重跑预构建 —— @amap 只在点「地图选点」那一刻动态引进来
    // （它顶层摸 window，静态 import 会让 SSR 整页 500），@vant/area-data 只有 /user 那颗岛用（收货地址、改资料两个弹窗）。
    // 一重跑 browserHash 就变，浏览器里那份旧模块图还指着旧 ?v= 的那串 URL，跟着一起 504 Outdated Optimize Dep，
    // 那一页的岛就此没水合：个人中心停在服务端画出来的「请先登录」那一屏，看着就像登录态丢了。
    // 所以开机就把客户端用到的依赖全预构建好（那四颗 astro transitions 虚拟模块是页头 ClientRouter 一加载就要的，
    // 漏了它们等于每趟冷启动的第一页都还会重跑一次预构建）。
    optimizeDeps: {
      include: [
        '@amap/amap-jsapi-loader',
        '@vant/area-data',
        'axios',
        'flowbite-vue',
        'pinia',
        'vue',
        'astro/virtual-modules/transitions-events.js',
        'astro/virtual-modules/transitions-router.js',
        'astro/virtual-modules/transitions-swap-functions.js',
        'astro/virtual-modules/transitions-types.js',
      ],
    },
    server: {
      host: '0.0.0.0',
      port: 4321,
      proxy: {
        '/api': { target: API_TARGET, changeOrigin: true },
      },
    },
  },
});
