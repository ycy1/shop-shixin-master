<script lang="ts" setup>
import type { IRegisterForm } from '@/api/login'
import { register, sendEmailCode } from '@/api/login'
import { LOGIN_PAGE } from '@/router/config'

definePage({
  style: {
    navigationBarTitleText: '注册',
    // 同登录页：标题栏刷成品牌红，接住下面那条色带
    navigationBarBackgroundColor: '#d14328',
    navigationBarTextStyle: 'white',
  },
})

/**
 * 邮箱注册，四格照 shop-web 的注册表单（账号 / 邮箱 + 验证码 / 密码），四格都填了才给提交。
 * 后端只建账号不给登录态，所以注册成功是回登录页，而不是直接进商城。
 */

const form = ref<IRegisterForm>({ username: '', email: '', code: '', password: '' })
const submitting = ref(false)
const sending = ref(false)
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const canSubmit = computed(() => !!(form.value.username && form.value.email && form.value.code && form.value.password))

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})

async function sendCode() {
  if (sending.value || countdown.value > 0) {
    return
  }
  if (!form.value.email) {
    uni.showToast({ title: '请先填邮箱', icon: 'none' })
    return
  }
  sending.value = true
  try {
    await sendEmailCode(form.value.email)
    uni.showToast({ title: '验证码已发到邮箱', icon: 'none' })
    startCountdown()
  }
  catch {
    // 邮件服务不通就是后端那句 message，http 层已经弹了
  }
  finally {
    sending.value = false
  }
}

/** 60s 内不让再发一封（口径同 Web 那一版），走完自己收表 */
function startCountdown() {
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

async function doRegister() {
  if (!canSubmit.value || submitting.value) {
    return
  }
  submitting.value = true
  try {
    await register({ ...form.value })
    uni.showToast({ title: '注册成功，请登录' })
    toLogin()
  }
  catch {
    // 同上，失败那句由 http 层给
  }
  finally {
    submitting.value = false
  }
}

/** 从登录页点进来的就退回去（登录页那一格还留着已输的账号），别的来路就压一张登录页 */
function toLogin() {
  const pages = getCurrentPages()
  const prev = pages[pages.length - 2]
  if (prev && `/${prev.route}` === LOGIN_PAGE) {
    uni.navigateBack()
  }
  else {
    uni.redirectTo({ url: LOGIN_PAGE })
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
        注册
      </view>
      <view class="mt-16rpx text-center text-[26rpx] text-white opacity-80">
        用邮箱开一个账号
      </view>

      <view class="auth-card mt-64rpx">
        <wd-input v-model="form.username" label="账号" placeholder="请设置账号" clearable />
        <wd-input v-model="form.email" label="邮箱" placeholder="请输入邮箱">
          <template #suffix>
            <text
              hover-class="fx-tap"
              class="fx-press text-[26rpx]"
              :class="countdown > 0 ? 'text-gray-400' : 'text-[#d14328]'"
              @click="sendCode"
            >
              {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
            </text>
          </template>
        </wd-input>
        <wd-input v-model="form.code" label="验证码" placeholder="请输入邮箱验证码" clearable />
        <wd-input v-model="form.password" label="密码" placeholder="请输入密码" show-password @confirm="doRegister" />

        <view class="mt-64rpx">
          <wd-button type="error" size="large" block :disabled="!canSubmit" :loading="submitting" @click="doRegister">
            注册
          </wd-button>
        </view>
      </view>

      <view class="mt-48rpx pb-48rpx text-center text-[26rpx] text-gray-500">
        已有账号？<text hover-class="fx-tap" class="fx-press text-[#d14328]" @click="toLogin">去登录</text>
      </view>
    </view>
  </view>
</template>
