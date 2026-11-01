<script setup lang="ts">
/**
 * 自定义导航栏（一张公共件，页面自己在 definePage 里钉 navigationStyle: 'custom' 后摆到模板顶上）。
 *
 * 形状：状态栏那一截用 --status-bar-height 垫高（H5 上是 0，小程序/App 是实高），下面一行 88rpx：
 * 左右两格等宽（各 88rpx）中间标题才真正居中；左边给了返回，右边这一格如今空着 ——
 * 抽屉那颗挪到 AppSearchBar 那一排（搜索框右边）去了，别在一屏上画两颗。
 *
 * 不画背景（他点名要的）：这一条就顺着页面自己的底色走，所以它是**排在流里**的一块，不是 fixed/sticky ——
 * 透明的东西浮在内容上，滚起来标题会和卡片叠在一起糊成一片。代价是页面往下滚这一条跟着走，
 * 下面那一排搜索是吸顶的，所以入口一直在。
 */
const props = withDefaults(defineProps<{
  /** 标题；一般和 definePage 里的 navigationBarTitleText 同一句 */
  title?: string
  /** 返回那颗：tab 页不画（切 tab 没有可返回的上一跳），详情/表单类页才给 */
  back?: boolean
}>(), {
  title: '',
  back: false,
})

function goBack() {
  uni.navigateBack()
}
</script>

<template>
  <view class="nav-bar">
    <view class="nav-bar__row">
      <view class="nav-bar__side">
        <!-- 返回：小程序/App 上左滑没有系统那一下，得自己给 -->
        <view v-if="props.back" hover-class="fx-tap" class="fx-press nav-bar__btn" @click="goBack">
          <text class="nav-bar__icon">‹</text>
        </view>
      </view>
      <text class="nav-bar__title">{{ props.title }}</text>
      <!-- 右边这格是空的，留着只为和左边等宽、标题居中 -->
      <view class="nav-bar__side" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* rpx 和 --status-bar-height 混在一条 calc 里，Uno 那一套原子类给不出来，这一小块只在这儿手写 */
.nav-bar {
  padding-top: var(--status-bar-height, 0);
}

.nav-bar__row {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 16rpx;
}

/* 左右等宽：不等宽的话标题看着偏，因为中间那格 flex-1 的可用宽度被两边吃得不一样 */
.nav-bar__side {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88rpx;
  flex-shrink: 0;
}

.nav-bar__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-align: center;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 32rpx;
  font-weight: 500;
  color: #333;
}

.nav-bar__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
}

.nav-bar__icon {
  font-size: 44rpx;
  line-height: 44rpx;
  color: #333;
}
</style>
