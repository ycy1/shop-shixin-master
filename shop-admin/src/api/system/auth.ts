import request from '@/utils/request'

interface LoginParams {
  username: string
  password: string
  captchaCode: string
  captchaKey: string
  rememberMe: boolean
}

// 登录接口
export function loginApi(data: LoginParams) {
  return request({
    url: '/api/auth/login',
    method: 'post',
    data
  })
}

export function logoutApi() {
    return request({
      url: '/api/auth/logout',
      method: 'post',
    })
  }

// 获取用户信息
export function getUserInfoApi() {
  return request({
    url: "/api/auth/info",
    method: "get",
    params: {
      source: "admin"
    }
  })
}

export function getRouters() {
  return request({
    url: '/sys/menu/routers',
    method: 'get'
  })
}

// 获取验证码
export function getCaptchaApi() {
  return request({
    url: '/auth/getCaptcha',
    method: 'get'
  })
}


// 获取验证码开关
export function getCaptchaSwitchApi() {
  return request({
    url: '/sys/config/getConfigByKey/slider_verify_switch',
    method: 'get'
  })
}

// 生成扫码登录二维码（1 分钟有效），返回 { code, qrCodeImage, expireSeconds }
export function getQrLoginCodeApi() {
  return request({
    url: '/api/auth/qrcode/generate',
    method: 'get'
  })
}

// 长轮询扫码登录状态
// timeout 必须显式放宽：request.ts 里的默认超时是 15s，而后端一次长轮询最多挂起 25s，
// 不覆盖的话每次请求都会被客户端提前掐断
export function pollQrLoginApi(code: string) {
  return request({
    url: `/api/auth/qrcode/poll/${code}`,
    method: 'get',
    timeout: 30000
  })
}