<template>
  <div class="login-container" :class="{ dark: isDark }">
    <!-- 左侧背景区域 -->
    <div class="login-background">
      <div class="background-wrapper">
        <!-- 添加简单的装饰图形 -->
        <div class="animated-background">
          <div class="gradient-circle"></div>
          <div class="geometric-shapes">
            <div class="shape" v-for="n in 5" :key="n"></div>
          </div>
        </div>

        <!-- Logo和标题区域 -->
        <div class="brand-content">
          <div class="logo-wrapper">
            <Logo :size="80" class="floating-logo" :color="logoColor" />
          </div>
          <h1 class="brand-title">{{ settings.title }}</h1>
          <p class="brand-description">
            基于 Vue3 + TypeScript 打造的现代化商城管理系统
          </p>
        </div>
      </div>
    </div>

    <!-- 右侧登录表单区域 -->
    <div class="login-form">
      <div class="form-wrapper">
        <!-- 主题切换按钮 -->
        <div class="theme-switch">
          <el-button class="theme-button" circle @click="toggleTheme">
            <el-icon><component :is="isDark ? 'Sunny' : 'Moon'" /></el-icon>
          </el-button>
        </div>

        <h2 class="welcome-text">欢迎回来</h2>
        <p class="login-tip">请使用您的账号密码登录系统</p>

        <!-- 登录方式切换 -->
        <div class="login-tabs">
          <div
            class="tab-item"
            :class="{ active: loginType === 'account' }"
            @click="loginType = 'account'"
          >
            <el-icon><User /></el-icon>
            账号登录
          </div>
          <div
            class="tab-item"
            :class="{ active: loginType === 'qrcode' }"
            @click="loginType = 'qrcode'"
          >
            <el-icon><component :is="QrCode" /></el-icon>
            扫码登录
          </div>
        </div>

        <!-- 登录表单内容 -->
        <transition name="fade-transform" mode="out-in">
          <el-form
            v-if="loginType === 'account'"
            ref="loginFormRef"
            :model="loginForm"
            :rules="rules"
            @keyup.enter="handleLogin"
          >
            <el-form-item prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                prefix-icon="User"
              />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                prefix-icon="Lock"
                show-password
              />
            </el-form-item>

            <div class="login-options">
              <el-checkbox v-model="rememberMe">记住我</el-checkbox>
              <a href="#" class="forget-password">忘记密码？</a>
            </div>

            <el-button
              :loading="loading"
              type="primary"
              class="login-button"
              @click="handleLogin"
            >
              {{ loading ? "登录中..." : "登录" }}
            </el-button>
          </el-form>

          <div v-else class="qrcode-box">
            <div class="qrcode-wrapper">
              <div class="qrcode-scanner"></div>
              <img v-if="qrCodeUrl" :src="qrCodeUrl" alt="二维码" class="qrcode-img" />
              <transition name="fade">
                <div class="qrcode-mask" v-if="qrMaskState">
                  <el-icon class="expired-icon">
                    <component
                      :is="qrMaskState === 'canceled' ? 'CircleClose' : 'Warning'"
                    />
                  </el-icon>
                  <p>{{ qrMaskState === "canceled" ? "已取消登录" : "二维码已过期" }}</p>
                  <p class="qrcode-mask-tip">
                    {{
                      qrMaskState === "canceled"
                        ? "你在手机上取消了本次登录"
                        : "请刷新后重新扫描"
                    }}
                  </p>
                  <el-button type="primary" @click="refreshQrCode" round>
                    <el-icon><RefreshRight /></el-icon>
                    刷新二维码
                  </el-button>
                </div>
              </transition>
            </div>
            <p class="qrcode-tip">
              <el-icon><Iphone /></el-icon>
              {{
                qrCodeStatus === "scanned"
                  ? "已扫描，请在手机上确认"
                  : "请使用手机扫码登录"
              }}
            </p>
          </div>
        </transition>

        <!-- 社交登录 -->
        <div class="social-login">
          <div class="divider">其他登录方式</div>
          <div class="social-icons">
            <div class="social-icon" @click="handleSocialLogin('wechat')">
              <svg-icon name="wechat" />
            </div>
            <div class="social-icon" @click="handleSocialLogin('qq')">
              <svg-icon name="qq" />
            </div>
            <div class="social-icon" @click="handleSocialLogin('gitee')">
              <svg-icon name="gitee" />
            </div>
          </div>
        </div>
      </div>

      <!-- 滑块验证 -->
      <el-dialog
        title="请拖动滑块完成拼图"
        width="360px"
        v-model="showSliderVerify"
        :close-on-click-modal="false"
        @closed="refresh"
        append-to-body
      >
        <slider-verify
          ref="sliderVerifyRef"
          @success="onSuccess"
          @fail="onFail"
          @again="onAgain"
        />
      </el-dialog>

      <!-- 页脚版权信息 -->
      <footer class="footer">
        <p>Copyright © 2024 Neat-Admin</p>
        <a href="https://beian.miit.gov.cn/" target="_blank"
          >湘ICP备2022002110号-1</a
        >
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import router from "@/router";
import type { FormInstance } from "element-plus";
import { ElMessage } from "element-plus";
import { useUserStore } from "@/store/modules/user";
import { useSettingsStore } from "@/store/modules/settings";
import Logo from "@/layouts/components/Sidebar/Logo.vue";
import settings from "@/config/settings";
import SliderVerify from "./components/SliderVerify.vue";
import {
  getCaptchaSwitchApi,
  getQrLoginCodeApi,
  pollQrLoginApi,
} from "@/api/system/auth";
import { setToken } from "@/utils/auth";

const QrCode = markRaw({
  name: "QrCode",
  render() {
    return h(
      "svg",
      {
        viewBox: "0 0 1024 1024",
        width: "1em",
        height: "1em",
        fill: "currentColor",
      },
      [
        h("path", {
          d: "M468 128H160c-17.7 0-32 14.3-32 32v308c0 4.4 3.6 8 8 8h332c4.4 0 8-3.6 8-8V136c0-4.4-3.6-8-8-8zm-56 284H192V192h220v220zm-138-74h56c4.4 0 8-3.6 8-8v-56c0-4.4-3.6-8-8-8h-56c-4.4 0-8 3.6-8 8v56c0 4.4 3.6 8 8 8zm444-140H556c-4.4 0-8 3.6-8 8v332c0 4.4 3.6 8 8 8h276c4.4 0 8-3.6 8-8V160c0-17.7-14.3-32-32-32zm-56 284H556V192h220v220zm-138-74h56c4.4 0 8-3.6 8-8v-56c0-4.4-3.6-8-8-8h-56c-4.4 0-8 3.6-8 8v56c0 4.4 3.6 8 8 8zM192 556v308c0 17.7 14.3 32 32 32h308c4.4 0 8-3.6 8-8V556c0-4.4-3.6-8-8-8H160c-4.4 0-8 3.6-8 8zm56 284V556h220v284H192zm-64-220h56c4.4 0 8-3.6 8-8v-56c0-4.4-3.6-8-8-8h-56c-4.4 0-8 3.6-8 8v56c0 4.4 3.6 8 8 8zm500 220c0 4.4 3.6 8 8 8h108v108c0 4.4 3.6 8 8 8h56c4.4 0 8-3.6 8-8V556c0-4.4-3.6-8-8-8H556c-4.4 0-8 3.6-8 8v332zm64-216h108v108H748V624z",
        }),
      ]
    );
  },
});
const userStore = useUserStore();
const settingsStore = useSettingsStore();
const loginFormRef = ref<FormInstance>();
const loading = ref(false);
const rememberMe = ref(false);
const loginType = ref("account");
const qrCodeUrl = ref("");
// 二维码遮罩：null 不显示 / expired 已过期 / canceled 手机端主动取消。
// 用一个状态而不是两个 boolean，避免"已过期"和"已取消"同时为真
const qrMaskState = ref<"expired" | "canceled" | null>(null);
// loading: 正在取二维码 / waiting: 待扫码 / scanned: 已扫码，等待手机端确认
const qrCodeStatus = ref<"loading" | "waiting" | "scanned">("loading");
// 当前二维码的登录码，长轮询用它作为凭据
let qrCodeCode = "";

const showSliderVerify = ref(false);
const sliderVerifyRef = ref();

const loginForm = reactive({
  username: "admin",
  password: "123456",
  rememberMe: false,
  source: "ADMIN",
  nonceStr: "",
  value: "",
});

const rules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 3, max: 20, message: "长度在 3 到 20 个字符", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, max: 20, message: "长度在 6 到 20 个字符", trigger: "blur" },
  ],
};

const isDark = computed(() => settingsStore.theme === "dark");

const refresh = () => {
  sliderVerifyRef.value?.refresh();
};

const onSuccess = (captcha: any) => {
  loginForm.nonceStr = captcha.nonceStr;
  loginForm.value = captcha.value;

  login();
};

const login = () => {
  loading.value = true;
  userStore
    .login(loginForm)
    .then(() => {
      sliderVerifyRef?.value?.verifySuccessEvent();
      console.log(userStore);
      router.push("/");
      ElMessage.success("登录成功");
    })
    .catch(() => {
      refresh();
    })
    .finally(() => {
      loading.value = false;
    });
};


/* 滑动验证失败*/
const onFail = (msg: string) => {
  refresh();
};
/* 滑动验证异常*/
const onAgain = () => {
  ElMessage.error("滑动操作异常，请重试");
};

const toggleTheme = () => {
  const newTheme = isDark.value ? "light" : "dark";
  settingsStore.saveSettings({ theme: newTheme });
};

const handleLogin = async () => {
  loginFormRef.value?.validate((flag) => {
    getCaptchaSwitchApi().then((res) => {
      if (!res.data || res.data.configValue === "Y") {
        showSliderVerify.value = true;
      } else {
        login();
      }
    });
  });
};

const handleSocialLogin = (type: string) => {
  ElMessage.success(type + "登录测试");
};

/**
 * 用"代"来作废上一轮的异步流程。
 * 光靠一个 boolean 是不够的：刷新时把它置回 false，上一轮那个还在飞的请求回来后
 * 会误以为自己是有效的，于是和新的一轮同时轮询。
 */
let qrEpoch = 0;
let qrCountdownTimer: number | undefined;

/** 作废当前二维码的所有异步流程（长轮询 + 倒计时） */
const stopQrLogin = () => {
  qrEpoch += 1;
  if (qrCountdownTimer) {
    clearInterval(qrCountdownTimer);
    qrCountdownTimer = undefined;
  }
};

const startQrCountdown = (epoch: number, seconds: number) => {
  let remain = seconds;
  qrCountdownTimer = window.setInterval(() => {
    remain -= 1;
    if (remain > 0 || epoch !== qrEpoch) return;
    // 后端那边 key 已经过期了，不用等轮询返回，直接展示过期遮罩
    stopQrLogin();
    qrMaskState.value = "expired";
  }, 1000);
};

/**
 * 长轮询：一次返回后再发下一次，而不是定时空转。
 * 后端每次最多挂起 25 秒，所以 pollQrLoginApi 的 axios 超时设成了 30 秒。
 */
const pollQrCode = async (epoch: number) => {
  while (epoch === qrEpoch) {
    let state: any;
    try {
      const res = await pollQrLoginApi(qrCodeCode);
      state = res.data;
    } catch (e) {
      if (epoch !== qrEpoch) return;
      // 网络抖动，等一下重试，别把整个登录流程打断
      await new Promise((resolve) => setTimeout(resolve, 1500));
      continue;
    }
    if (epoch !== qrEpoch) return;

    if (state?.state === "CONFIRMED" && state.loginUserInfo?.token) {
      stopQrLogin();
      setToken(state.loginUserInfo.token);
      // toast 里带上用户名。用户信息和动态路由交给路由守卫去拉，
      // 和账号密码登录走同一条路
      const { username, nickname } = state.loginUserInfo;
      const who = nickname || username;
      ElMessage.success(who ? `登录成功，欢迎回来 ${who}` : "登录成功");
      router.push("/");
      return;
    }

    if (state?.state === "EXPIRED" || state?.state === "CANCELED") {
      stopQrLogin();
      // 手机端主动取消 ≠ 二维码过期，两种提示要分开
      qrMaskState.value = state.state === "CANCELED" ? "canceled" : "expired";
      return;
    }

    if (state?.state === "SCANNED") {
      qrCodeStatus.value = "scanned";
    }
    // WAITING（本轮挂起超时）和 SCANNED 都继续下一轮
  }
};

const refreshQrCode = async () => {
  stopQrLogin();
  const epoch = qrEpoch;

  qrMaskState.value = null;
  qrCodeStatus.value = "loading";
  qrCodeUrl.value = "";

  try {
    const res = await getQrLoginCodeApi();
    // 请求返回时组件可能已经卸载，或者用户已经点了刷新
    if (epoch !== qrEpoch) return;

    qrCodeCode = res.data.code;
    qrCodeUrl.value = res.data.qrCodeImage;
    qrCodeStatus.value = "waiting";

    startQrCountdown(epoch, res.data.expireSeconds || 60); // 二维码过期时间默认 60 秒
    pollQrCode(epoch);
  } catch (e) {
    if (epoch !== qrEpoch) return;
    qrMaskState.value = "expired";
  }
};

watch(loginType, (newVal) => {
  if (newVal === "qrcode") {
    refreshQrCode();
  } else {
    stopQrLogin();
  }
});

onUnmounted(() => {
  stopQrLogin();
});

// 添加 logo 颜色计算
const logoColor = computed(() => {
  return isDark.value ? "#4ecdc4" : "#ff6b6b";
});
</script>

<style lang="scss" scoped>
.login-container {
  display: flex;
  min-height: 100vh;
  background: var(--el-bg-color);
}

/* 左侧背景区域样式 */
.login-background {
  flex: 1;
  position: relative;
  background: linear-gradient(135deg, #e3f2fd 0%, #bbdefb 100%);
  overflow: hidden;

  .background-wrapper {
    position: relative;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;

    /* 添加动态背景效果 */
    &::before {
      content: "";
      position: absolute;
      inset: -50%;
      background-image: repeating-linear-gradient(
          0deg,
          transparent,
          transparent 2px,
          rgba(33, 150, 243, 0.1) 3px,
          rgba(33, 150, 243, 0.1) 3px
        ),
        repeating-linear-gradient(
          90deg,
          transparent,
          transparent 2px,
          rgba(33, 150, 243, 0.1) 3px,
          rgba(33, 150, 243, 0.1) 3px
        );
      background-size: 30px 30px;
      transform: perspective(500px) rotateX(60deg);
      animation: matrixMove 20s linear infinite;
    }

    &::after {
      content: "";
      position: absolute;
      inset: 0;
      background: radial-gradient(
          circle at 30% 30%,
          rgba(33, 150, 243, 0.3) 0%,
          transparent 50%
        ),
        radial-gradient(
          circle at 70% 70%,
          rgba(3, 169, 244, 0.3) 0%,
          transparent 50%
        );
      filter: blur(30px);
      animation: glowPulse 8s ease-in-out infinite alternate;
    }
  }

  /* 品牌内容样式优化 */
  .brand-content {
    position: relative;
    z-index: 1;
    text-align: center;
    padding: 40px;

    .logo-wrapper {
      margin-bottom: 30px;
      position: relative;
      &::before {
        content: "";
        position: absolute;
        inset: -30px;
        border: 2px solid rgba(33, 150, 243, 0.3);
        border-radius: 50%;
        animation: rotateBorder 10s linear infinite;
      }

      &::after {
        content: "";
        position: absolute;
        inset: -15px;
        background: radial-gradient(
          circle,
          rgba(33, 150, 243, 0.4),
          transparent 70%
        );
        filter: blur(15px);
        animation: glowPulse 4s ease-in-out infinite;
      }
    }

    .brand-title {
      font-size: 36px;
      font-weight: bold;
      color: #1565c0;
      margin-bottom: 16px;
      text-shadow: 0 0 10px rgba(33, 150, 243, 0.5);
    }

    .brand-description {
      font-size: 16px;
      color: #1976d2;
      max-width: 400px;
      margin: 0 auto;
      line-height: 1.6;
    }
  }
}

/* 移除之前的动画相关样式 */
.animated-background,
.gradient-circle,
.geometric-shapes {
  display: none;
}

/* 右侧登录表单区域样式 */
.login-form {
  width: 500px;
  padding: 40px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background: var(--el-bg-color);
  position: relative;
  box-shadow: -10px 0 20px rgba(0, 0, 0, 0.05);

  .form-wrapper {
    max-width: 400px;
    margin: 0 auto;
    width: 100%;
  }

  /* 登录方式切换 */
  .login-tabs {
    display: flex;
    margin-bottom: 30px;
    background: var(--el-fill-color-light);
    padding: 5px;
    border-radius: 12px;
    position: relative;

    .tab-item {
      flex: 1;
      padding: 12px;
      text-align: center;
      cursor: pointer;
      border-radius: 8px;
      transition: all 0.3s;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      color: var(--el-text-color-secondary);

      &.active {
        background: var(--el-bg-color);
        color: var(--el-color-primary);
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      }

      .el-icon {
        font-size: 18px;
      }
    }
  }

  /* 表单项样式 */
  :deep(.el-form-item) {
    margin-bottom: 24px;

    .el-input__wrapper {
      padding: 0 15px;
      height: 44px;
      border-radius: 8px;
      background: var(--el-fill-color-light);
      border: 2px solid transparent;
      transition: all 0.3s;

      &:hover,
      &.is-focus {
        border-color: var(--el-color-primary);
        background: var(--el-bg-color);
      }

      .el-input__inner {
        font-size: 14px;
      }
    }
  }

  /* 登录按钮 */
  .login-button {
    width: 100%;
    height: 44px;
    border-radius: 8px;
    font-size: 16px;
    font-weight: 500;
    margin-top: 10px;
    background: linear-gradient(45deg, #ff6b6b, #4ecdc4);
    border: none;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 5px 15px rgba(255, 107, 107, 0.3);
    }
  }

  /* 二维码登录样式 */
  .qrcode-box {
    text-align: center;
    padding: 30px 0;

    .qrcode-wrapper {
      width: 200px;
      height: 200px;
      margin: 0 auto;
      padding: 15px;
      background: white;
      border-radius: 12px;
      position: relative;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);

      .qrcode-scanner {
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 2px;
        background: var(--el-color-primary);
        animation: scan 2s linear infinite;
      }

      .qrcode-img {
        width: 100%;
        height: 100%;
        object-fit: contain;
      }

      .qrcode-mask {
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: rgba(255, 255, 255, 0.9);
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 12px;
        border-radius: 12px;
      }

      .qrcode-mask-tip {
        font-size: 12px;
        color: var(--el-text-color-secondary);
        margin: 0;
      }
    }

    .qrcode-tip {
      margin-top: 20px;
      color: var(--el-text-color-secondary);
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
    }
  }

  /* 社交登录样式 */
  .social-login {
    margin-top: 40px;

    .divider {
      display: flex;
      align-items: center;
      color: var(--el-text-color-secondary);
      font-size: 14px;
      margin-bottom: 20px;

      &::before,
      &::after {
        content: "";
        flex: 1;
        height: 1px;
        background: var(--el-border-color);
        margin: 0 16px;
      }
    }

    .social-icons {
      display: flex;
      justify-content: center;
      gap: 20px;

      .social-icon {
        width: 40px;
        height: 40px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        transition: all 0.3s;
        background: var(--el-fill-color-light);
        color: var(--el-text-color-regular);

        &:hover {
          transform: translateY(-2px);
          background: var(--el-color-primary-light-9);
          color: var(--el-color-primary);
        }
      }
    }
  }

  /* 页脚样式 */
  .footer {
    text-align: center;
    color: var(--el-text-color-secondary);
    font-size: 14px;

    a {
      color: inherit;
      text-decoration: none;

      &:hover {
        color: var(--el-color-primary);
      }
    }
  }
}

/* 动画相关 */
@keyframes scan {
  0% {
    top: 0;
  }
  50% {
    top: calc(100% - 2px);
  }
  100% {
    top: 0;
  }
}

@keyframes float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-20px);
  }
}

@keyframes rotate {
  0% {
    transform: rotate(0);
  }
  100% {
    transform: rotate(360deg);
  }
}

/* 主题切换按钮样式 */
.theme-switch {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 10;

  .theme-button {
    width: 40px;
    height: 40px;
    background: rgba(255, 255, 255, 0.1);
    backdrop-filter: blur(10px);
    border: none;
    color: var(--el-text-color-primary);
    transition: all 0.3s;

    &:hover {
      background: rgba(255, 255, 255, 0.2);
      transform: translateY(-2px);
    }
  }
}

/* 登录选项样式 */
.login-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 20px 0;

  .el-checkbox {
    color: var(--el-text-color-regular);
  }

  .forget-password {
    color: var(--el-text-color-regular);
    text-decoration: none;
    font-size: 14px;
    transition: all 0.3s;

    &:hover {
      color: var(--el-color-primary);
    }
  }
}

/* 深色模式适配 */
.dark {
  .login-form {
    background: var(--el-bg-color-overlay);

    .login-tabs {
      background: rgba(255, 255, 255, 0.05);

      .tab-item.active {
        background: rgba(0, 0, 0, 0.3);
      }
    }

    :deep(.el-input__wrapper) {
      background: rgba(0, 0, 0, 0.2);

      &:hover,
      &.is-focus {
        background: rgba(0, 0, 0, 0.3);
      }
    }

    .qrcode-wrapper {
      background: var(--el-bg-color);
    }

    .social-icon {
      background: rgba(255, 255, 255, 0.05);

      &:hover {
        background: rgba(255, 255, 255, 0.1);
      }
    }
  }

  .theme-switch {
    .theme-button {
      background: rgba(0, 0, 0, 0.2);

      &:hover {
        background: rgba(0, 0, 0, 0.3);
      }
    }
  }

  .logo-wrapper {
    :deep(svg path) {
      fill: #2196f3;
    }
  }
}

/* 更新动画 */
@keyframes matrixMove {
  0% {
    background-position: 0 0;
  }
  100% {
    background-position: 0 30px;
  }
}

@keyframes glowPulse {
  0%,
  100% {
    opacity: 0.3;
    transform: scale(0.95);
  }
  50% {
    opacity: 0.8;
    transform: scale(1.05);
  }
}

@keyframes rotateBorder {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
