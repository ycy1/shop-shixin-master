<script setup lang="ts">
import SideMenu from '@/components/SideMenu.vue'
import { SIDE_MENU_KEY } from '@/hooks/useSideMenu'

/**
 * 全站这一层管三件事：任意页面左滑开右侧栏、右上角那颗入口、以及给页面里的导航栏递一个开关。
 *
 * 判据给得窄（横向 >=70px、纵向 <=40px、时长 <600ms 才算一次左滑）：手指在页面上随便一划不该开抽屉，
 * 得是「明显的横扫」。时长那一条同时兜住了「touchstart 在别处、touchend 落在这里」那种错配。
 *
 * 页面里真正要横滑的地方（图轮播、横向 chip 条）在自家容器上记一笔 `markSwipeZone`，这一层读到就不算开抽屉
 * —— 不能在那上面挂 @touchstart.stop：小程序会编译成 catchtouch*，微信的 tap 就合成不出来，那一片点击整个失效。
 *
 * 抽屉每页一份实例：点条目本来就要跳页，跨页留着开合状态没有意义；未读数和签到状态是开抽屉那一趟现捞的。
 * 这一层不再画悬浮那颗（详情页只要左滑，摆在内容上还会压住页面右上角的东西）；看得见的入口归搜索那一排右边（AppSearchBar）。
 */
const menuOpen = ref(false)

provide(SIDE_MENU_KEY, { open: () => (menuOpen.value = true) })

let startX = 0
let startY = 0
let startAt = 0

function onTouchStart(e: TouchEvent) {
  // 横滑区起的手势：连起点都不记，免得 touchend 落在别处时被拿去做差
  if (takeSwipeZone()) {
    return
  }
  const one = e.touches[0]
  if (!one) {
    return
  }
  startX = one.clientX
  startY = one.clientY
  startAt = Date.now()
}

function onTouchEnd(e: TouchEvent) {
  // 抽屉开着的时候里面的滑动不算：那已经是「在抽屉上」的动作，该由它自己管
  if (menuOpen.value) {
    return
  }
  if (takeSwipeZone()) {
    return
  }
  const one = e.changedTouches[0]
  if (!one || Date.now() - startAt > 600) {
    return
  }
  if (one.clientX - startX <= -70 && Math.abs(one.clientY - startY) <= 40) {
    menuOpen.value = true
  }
}
</script>

<template>
  <view @touchstart="onTouchStart" @touchend="onTouchEnd">
    <slot />

    <SideMenu v-model="menuOpen" />
  </view>
</template>
