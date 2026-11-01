<script setup lang="ts">
import { useSideMenuEntry } from '@/hooks/useSideMenu'

/**
 * 顶部那一排：搜索入口 + 右侧栏那颗。tab 四页（首页/广场/购物车/我的）都挂在同一片位置，形状只在这儿维护一份。
 *
 * 搜索框是入口不是输入框：点一下就跳搜索页，词在那里打（首页原来那句注释的意思，四页同一口径）。
 * 那颗 ≡ 开的是布局那一层的抽屉 —— 开关只能靠 inject 拿「这一页那一份」，而页面自己的 setup 里 inject 拿不到
 * （布局是页面的子组件，slot 内容归页面渲染），所以它必须待在组件实例里，不能搬回页面的那一排上。
 *
 * 吸顶 + 毛玻璃沿用首页那一套：滚动时这一排留在顶上，标题那条跟着划走。
 * 样式刻意不 scoped：下面要伸手进 wd-search 这个自定义组件里摘它的死白底，小程序里 scoped 跨组件边界不一定命中。
 */
const menu = useSideMenuEntry()

function toSearch() {
  uni.navigateTo({ url: '/pages/search/search' })
}
</script>

<template>
  <view class="app-search-bar z-10">
    <view class="flex items-center">
      <!-- disabled 是为了让点击冒泡出来（wd-search 只在 disabled 时抛 click），这一格只是个入口 -->
      <wd-search
        class="min-w-0 flex-1"
        disabled
        placeholder="搜索商品 / 门店 / 品牌 / 活动"
        hide-cancel
        @click="toSearch()"
      />
      <view hover-class="fx-tap" class="fx-press app-search-bar__menu" @click="menu?.open()">
        <text class="app-search-bar__icon">≡</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss">
.app-search-bar {
  position: sticky;
  /* H5 顶上那条 44px 标题栏是盖在内容之上的，粘到 top:0 会钻进它底下（实测滚起来后整条 y=0）；
     --window-top 就是那条的高度，小程序/App 上这个变量不存在 ⇒ 回退成 0 */
  top: var(--window-top, 0);
  /* 毛玻璃：和底部那条 tabbar 同一套做法（见 src/tabbar/index.vue）。老安卓 webview 不认 backdrop-filter，
     那一头就只剩 75% 的白底，不看糊也照样读得清 */
  background-color: rgba(255, 255, 255, 0.75);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  backdrop-filter: saturate(180%) blur(20px);
}

/* wd-search 那层底色是写死的 #fff，它没留 CSS 变量，不摘掉就把上面这层毛玻璃整个盖住了。
   里面那颗灰底输入条（.wd-search__block）留着，不然这一格就不像个搜索框了 */
.app-search-bar .wd-search {
  background: transparent;
}

/* 搜索框右边那颗抽屉入口：不给底，和自绘导航栏里那颗同一个样子（那颗也是一行光字） */
.app-search-bar__menu {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  margin-right: 8rpx;
}

.app-search-bar__icon {
  font-size: 40rpx;
  line-height: 40rpx;
  color: #333;
}
</style>
