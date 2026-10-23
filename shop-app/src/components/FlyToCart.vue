<script lang="ts" setup>
import { flights, FLY_MS } from '@/utils/flyToCart'

/**
 * 加购飞行动画那一颗点：外层只管横向位移（匀速），内层只管竖向位移（越落越快），
 * 两条叠出来就是抛物线 —— 一条 transition 同时管两个轴是做不到的，那条曲线得靠两个元素各走各的。
 *
 * 挂在 App.ku.vue 上全局只有一份，谁加购谁往 flights 里塞一趟（见 utils/flyToCart.ts）。
 */
defineOptions({
  name: 'FlyToCart',
})
</script>

<template>
  <view v-if="flights.length" class="fly-layer">
    <view
      v-for="one in flights"
      :key="one.id"
      class="fly-x"
      :style="{
        left: `${one.x}px`,
        top: `${one.y}px`,
        transitionDuration: `${FLY_MS}ms`,
        transform: one.flying ? `translateX(${one.dx}px)` : 'translateX(0)',
      }"
    >
      <view
        class="fly-dot"
        :style="{
          transitionDuration: `${FLY_MS}ms`,
          transform: one.flying ? `translateY(${one.dy}px) scale(0.4)` : 'translateY(0) scale(1)',
        }"
      />
    </view>
  </view>
</template>

<style scoped lang="scss">
/*
 * 整层不吃点击：它在最上面，挡一下就点不动底下的东西。
 * 那两条 var 是给 H5 的：uni-h5 的 boundingClientRect 量出来的是「内容区」坐标（标题栏下面那块），
 * 直接用视口坐标铺层就会整体上移一条标题栏（实测差 44px，点完看着没落到球上）。
 * 小程序里 --window-top 本来就是 0，所以这一句在多端都不用分叉。
 */
.fly-layer {
  position: fixed;
  top: var(--window-top, 0);
  right: 0;
  bottom: var(--window-bottom, 0);
  left: 0;
  z-index: 2000;
  pointer-events: none;
}

.fly-x {
  /* 跟着上面那层走（absolute），别 fixed —— fixed 又按视口算，那条 var 就白加了 */
  position: absolute;
  transition-property: transform;
  transition-timing-function: linear;
}

.fly-dot {
  width: 40rpx;
  height: 40rpx;
  margin: -20rpx 0 0 -20rpx;
  border-radius: 999rpx;
  background-color: #d14328;
  box-shadow: 0 0 16rpx rgba(209, 67, 40, 0.45);
  transition-property: transform;
  /* ease-in 的二次曲线：落得越来越快，配合横向匀速才是抛物线 */
  transition-timing-function: cubic-bezier(0.55, 0.085, 0.68, 0.53);
}
</style>
