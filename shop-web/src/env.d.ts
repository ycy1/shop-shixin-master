/// <reference types="astro/client" />

/**
 * context.locals / Astro.locals 上那一格语种（由 src/middleware.ts 挂、页面和 Base.astro 读）。
 * astro 把这个口子留给项目自己声明，声明了 astro check 才认 Astro.locals.locale。
 */
declare namespace App {
  interface Locals {
    locale?: import('./i18n').Locale;
  }
}

/**
 * wangEditor 的 vue3 壳：这一包的 package.json 里 exports 只开了 import/require 两档、没写 "types"，
 * 于是 vue-tsc 顺着 exports 找类型就找不到（运行时 vite 解析得到，它不看 types 那一档），
 * 而它自己那份类型是编好的 .vue.d.ts，不在 exports 能走到的路径上。
 * 所以只给 TS 补这一句声明（tsconfig 的 paths 不能用：Astro 会把 paths 同步喂给 vite 的运行时别名，
 * 指过去等于让运行时去 import 一个 .d.ts）。
 */
declare module '@wangeditor/editor-for-vue' {
  import type { DefineComponent } from 'vue';

  export const Editor: DefineComponent<Record<string, unknown>>;
  export const Toolbar: DefineComponent<Record<string, unknown>>;
}
