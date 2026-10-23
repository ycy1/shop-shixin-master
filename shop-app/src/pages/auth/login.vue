<script lang="ts" setup>
import { REGISTER_PAGE } from '@/router/config'
import { useTokenStore } from '@/store/token'

definePage({
  style: {
    navigationBarTitleText: '登录',
    // 标题栏刷成品牌红，和下面那条色带接成一片（色带是从标题栏底下开始的）
    navigationBarBackgroundColor: '#d14328',
    navigationBarTextStyle: 'white',
  },
})

const tokenStore = useTokenStore()
const username = ref('')
const password = ref('')
const submitting = ref(false)

function afterLogin() {
  // 登录页是被 toLoginPage 压进来的，成功后回到原来那一页
  uni.navigateBack()
}

/** 注册那一页收着邮箱验证码那一路，注册完它自己会回到这里 */
function toRegister() {
  uni.navigateTo({ url: REGISTER_PAGE })
}

/**
 * 账号密码登录：/api/auth/login 在 SaTokenConfigure 的放行名单里，哪一档都输得成，
 * 所以这一页不按平台分叉，两颗入口同屏给出去。
 */
async function doLogin() {
  if (!username.value || !password.value) {
    uni.showToast({ title: '请输入账号和密码', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await tokenStore.login({ username: username.value, password: password.value })
    afterLogin()
  }
  catch {
    // 失败提示由 http 层和 store 统一给，这里不再叠一层
  }
  finally {
    submitting.value = false
  }
}

/**
 * 微信登录：小程序和 App 两条授权链不一样（小程序给 code 让后端换 openid，App 里 SDK 直接把 openid 给前端），
 * 分叉收在 tokenStore.wxLogin() 里，这一页的按钮哪一档都长同一张样。
 */
async function doWxLogin() {
  submitting.value = true
  try {
    await tokenStore.wxLogin()
    afterLogin()
  }
  catch {
    // 同上
  }
  finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="auth-page">
    <image class="auth-photo" src="/static/images/auth-bg.jpg" mode="aspectFill" />
    <view class="auth-band" />
    <view class="auth-scrim" />
    <view class="relative px-48rpx">
      <view class="pt-96rpx text-center text-[44rpx] text-white font-semibold">
        登录
      </view>
      <view class="mt-16rpx text-center text-[26rpx] text-white opacity-80">
        登录后就能下单、领券
      </view>

      <view class="auth-card mt-64rpx">
        <wd-input v-model="username" label="账号" placeholder="请输入账号" clearable />
        <wd-input v-model="password" label="密码" placeholder="请输入密码" show-password @confirm="doLogin" />
        <view class="mt-64rpx">
          <wd-button type="error" size="large" block :loading="submitting" @click="doLogin">
            登录
          </wd-button>
        </view>

        <view class="mt-64rpx flex items-center">
          <view class="h-1px flex-1 bg-gray-200" />
          <view class="mx-32rpx text-24rpx text-gray-400">
            其他登录方式
          </view>
          <view class="h-1px flex-1 bg-gray-200" />
        </view>
        <view class="mt-48rpx">
          <wd-button type="success" size="large" plain block :loading="submitting" @click="doWxLogin">
            微信登录
          </wd-button>
        </view>
      </view>

      <view class="mt-48rpx pb-48rpx text-center text-[26rpx] text-gray-500">
        还没有账号？<text hover-class="fx-tap" class="fx-press text-[#d14328]" @click="toRegister">去注册</text>
      </view>
    </view>
  </view>
</template>
