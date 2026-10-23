import type {
  ICaptcha,
  IDoubleTokenRes,
  ISingleTokenRes,
  IUpdateInfo,
  IUpdatePassword,
  IUserInfoRes,
} from './types/login'
import { http } from '@/http/http'

/**
 * 登录表单
 */
export interface ILoginForm {
  username: string
  password: string
}

/** 后端 com.shop.dto.user.LoginUserInfo 的形状：一个接口同时把 token 和用户信息带回来 */
interface IShopLoginUser {
  id: number
  username: string
  nickname: string | null
  avatar: string | null
  accountType: number | null
  token: string
}

/** 模板的 token 形状 + 一份用户信息；token store 拿到就直接铺，不必再打一次“当前用户”接口 */
export type IShopLoginRes = ISingleTokenRes & { user: IUserInfoRes }

/** 后端 application.yml 里 sa-token.timeout = 604800（7 天），回包不带有效期，按这一格算 */
const TOKEN_TIMEOUT = 604800

function toShopLoginRes(res: IShopLoginUser): IShopLoginRes {
  return {
    token: res.token,
    expiresIn: TOKEN_TIMEOUT,
    user: {
      userId: res.id,
      username: res.username,
      nickname: res.nickname ?? '',
      avatar: res.avatar ?? undefined,
    },
  }
}

/**
 * 获取验证码
 */
export function getCode() {
  return http.get<ICaptcha>('/auth/getCaptcha')
}

/** 注册表单：形状照 shop-web 的 RegisterForm（后端 com.shop.dto.EmailRegisterDto，昵称可选，不递） */
export interface IRegisterForm {
  username: string
  email: string
  code: string
  password: string
}

/**
 * 邮箱验证码：GET /api/sendEmailCode，type=1 是注册那一档。
 * 这一条会真发一封邮件，邮件服务没通就是后端那句 message（http 层弹出来）
 */
export function sendEmailCode(email: string) {
  return http.get<string>('/api/sendEmailCode', { email, type: 1 })
}

/**
 * 邮箱注册：后端只建账号、不给登录态，所以注册完还是要回登录页输一次
 */
export function register(form: IRegisterForm) {
  return http.post<boolean>('/api/email/register', form)
}

/**
 * 用户登录（账号密码）：后端 /api/auth/login 在放行名单里，小程序里照样输得成，登录页不按平台分叉
 * @param loginForm 登录表单
 */
export function login(loginForm: ILoginForm) {
  return http.post<IShopLoginUser>('/api/auth/login', loginForm).then(toShopLoginRes)
}

/**
 * 刷新token
 * @param refreshToken 刷新token
 */
export function refreshToken(refreshToken: string) {
  return http.post<IDoubleTokenRes>('/auth/refreshToken', { refreshToken })
}

/**
 * 获取用户信息
 * 备注：商城这一端的登录回包已经把用户信息带回来了（见 toShopLoginRes），正常不会打这条；
 * 后端 /api/auth/info 回的是 SysUserVo（带 source 参数、偏后台口径），别拿它当消费者资料页的数据源。
 */
export function getUserInfo() {
  return http.get<IUserInfoRes>('/api/auth/info')
}

/**
 * 退出登录
 */
export function logout() {
  return http.post<void>('/api/auth/logout')
}

/**
 * 修改用户信息
 */
export function updateInfo(data: IUpdateInfo) {
  return http.post('/user/updateInfo', data)
}

/**
 * 修改用户密码
 */
export function updateUserPassword(data: IUpdatePassword) {
  return http.post('/user/updatePassword', data)
}

/**
 * 获取微信登录凭证
 * @returns Promise 包含微信登录凭证(code)
 */
export function getWxCode() {
  return new Promise<UniApp.LoginRes>((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: res => resolve(res),
      fail: err => reject(new Error(err)),
    })
  })
}

/**
 * 微信小程序登录：code 走路径参数，后端 POST /api/wechat/appletLogin/{code} 拿 jscode2session 换 openid，
 * 没有这个 openid 的用户就当场建一个消费者账号（见 AuthServiceImpl#appletLogin）。
 * body 那一份 userInfo 是头像/昵称，小程序现在要用户主动授权才给得到，没有就传空对象，
 * 后端会落一个随机昵称 + 随机头像。
 * 注意这一条只认小程序那一路的 code：App 那一路见下面的 wxAuthLogin；H5 里 uni.login 给的是网页授权 code，
 * 要用公众号那对凭据换 openid（后端没有接 code 的这条口，application-dev.yml 里 wechat.appId/secret 也是空的）。
 */
export function wxLogin(data: { code: string }) {
  return http.post<IShopLoginUser>(`/api/wechat/appletLogin/${data.code}`, {}).then(toShopLoginRes)
}

/** 原生 App 那一路微信授权回来的资料，字段名对齐后端 wechatAuthLogin 读的那四格 */
export interface IWxAuthUserInfo {
  openid: string
  nickname?: string
  avatarUrl?: string
  sex?: number
}

/**
 * App 端微信授权：不像小程序要后端拿 code 去 jscode2session 换，微信 SDK 当场就把 openid 和昵称头像给了前端，
 * 所以这一条只编得进 APP-PLUS（H5 / 小程序里调用直接 reject）。
 * SDK 给的头像字段叫 headimgurl，后端收的是 avatarUrl，在这里换名；sex SDK 声明成字符串，转成数字再递。
 */
export function getWxAuthUserInfo() {
  return new Promise<IWxAuthUserInfo>((resolve, reject) => {
    // #ifdef APP-PLUS
    plus.oauth.getServices((services) => {
      const weixin = services.find(service => service.id === 'weixin')
      if (!weixin) {
        reject(new Error('微信登录未配置，请检查 manifest 里的 oauth'))
        return
      }
      weixin.login((result) => {
        const info = result?.target?.userInfo ?? {}
        const openid = info.openid ?? weixin.authResult?.openid
        if (!openid) {
          reject(new Error('微信授权没有返回 openid'))
          return
        }
        resolve({
          openid,
          nickname: info.nickname,
          avatarUrl: info.headimgurl,
          sex: Number(info.sex) || undefined,
        })
      }, err => reject(new Error(String(err?.msg ?? '微信授权失败'))))
    }, err => reject(new Error(String(err?.msg ?? '获取微信登录服务失败'))))
    // #endif
    // #ifndef APP-PLUS
    reject(new Error('微信 App 登录只支持 App 端'))
    // #endif
  })
}

/**
 * 原生 App 的微信登录：openid 已经在 body 里，后端 AuthServiceImpl#wechatAuthLogin 按它查账号、查不到就当场建一个消费者账号。
 * 注意后端直接信前端递的 openid，没有服务端校验，这一条的安全性靠 App 本身那层。
 */
export function wxAuthLogin(userInfo: IWxAuthUserInfo) {
  return http.post<IShopLoginUser>('/api/wechat/wechatAuthLogin', userInfo).then(toShopLoginRes)
}
