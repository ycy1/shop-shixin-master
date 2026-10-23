import { apiGet, apiPost } from './api';

/** AuthController 的 LoginUserInfo：token 只在登录那一趟带回来，之后靠它换 /api/auth/info */
export interface LoginUser {
  id: number;
  username: string | null;
  nickname: string | null;
  avatar: string | null;
  sex: number | null;
  accountType: number | null;
  token?: string | null;
}

export interface LoginForm {
  username: string;
  password: string;
}

/**
 * 前台不做滑块，但 LoginDTO 上 nonceStr/value 是 @NotBlank，不填那句校验先拦下，
 * 轮不到后面的开关。滑块开关是 N 时 AuthServiceImpl 整个跳过 checkImageCode，
 * 这两格没人比对，给个非空占位就行。
 */
const CAPTCHA_SKIPPED = { nonceStr: 'skip', value: '0' };

export interface RegisterForm {
  email: string;
  code: string;
  username: string;
  password: string;
}

/** source 后端只用来挡演示账号（PC 不让用 test 登录门户），这边固定 PC */
export function login(form: LoginForm): Promise<LoginUser> {
  return apiPost<LoginUser>('/api/auth/login', { ...form, ...CAPTCHA_SKIPPED, source: 'PC' });
}

export function fetchLoginUser(): Promise<LoginUser> {
  return apiGet<LoginUser>('/api/auth/info', { source: 'PC' });
}

export function logout(): Promise<void> {
  return apiPost<void>('/api/auth/logout');
}

/** type=1 注册；这一条会真发一封邮件，邮件服务没通就是后端那句 message */
export function sendEmailCode(email: string): Promise<string> {
  return apiGet<string>('/api/sendEmailCode', { email, type: 1 });
}

export function register(form: RegisterForm): Promise<boolean> {
  return apiPost<boolean>('/api/email/register', form);
}
