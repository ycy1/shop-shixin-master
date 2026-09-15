import { title } from "process";
import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router";

export const Layout = () => import("@/layouts/index.vue");

// 静态路由
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: "/redirect",
    component: Layout,
    meta: { hidden: true },
    children: [
      {
        path: "/redirect/:path(.*)",
        component: () => import("@/views/redirect/index.vue"),
        meta: { hidden: true }
      }
    ]
  },

  {
    path: "/login",
    component: () => import("@/views/login/index.vue"),
    meta: {hidden: true },
  },

  // 文件中心独立路由：支持在浏览器新窗口/新标签打开（由外链菜单跳转）
  {
    path: "/filecenter/index",
    name: "FileCenter",
    component: () => import("@/views/filecenter/index.vue"),
    meta: { title: "文件中心", hidden: true },
  },

  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/views/error-page/404.vue'),
    meta: { hidden: true },
  },

  {
    path: "/",
    name: "/",
    component: Layout,
    redirect: "/dashboard",
    children: [
      {
        path: "dashboard",
        component: () => import("@/views/dashboard/index.vue"),
        name: "Dashboard",
        meta: {
          title: "仪表盘",
          icon: "Orange",
          affix: true,
          keepAlive: true,
          alwaysShow: false,
        },
      },
    ],
  }
];

/**
 * 创建路由
 */
const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes,
  // 刷新时，滚动条位置还原
  scrollBehavior: () => ({ left: 0, top: 0 }),
});

/**
 * 重置路由
 */
export function resetRouter() {
  router.replace({ path: "/login" });
}

export default router;
