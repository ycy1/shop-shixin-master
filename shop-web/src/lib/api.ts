import axios, { type AxiosRequestConfig } from 'axios';

/** 这一格只在 import.meta.env.SSR 为真的那一支被读到（浏览器那一支会被打包摇掉）；
 *  装 @types/node 只为拿一个 process 不值当，就按用到的形状钉一下 */
declare const process: { env: Record<string, string | undefined> };

/** 后端统一信封（com.shop.common.Result）：code 200 才算成 */
export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
  extra?: Record<string, unknown>;
}

/**
 * 所有失败都从这个口子出来，调用方只看两颗数：
 * code —— 拿到了信封就是信封里的 code，没拿到就是 HTTP 状态码，连响应都没有（超时/断网）是 0；
 * status —— HTTP 状态码，没收到响应为 null。
 */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly code: number,
    readonly status: number | null,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/** 每次请求都能单独覆盖；这一格是兜底值，从 PUBLIC_API_TIMEOUT 读，没配就用这个 */
const DEFAULT_TIMEOUT = 15000;

/**
 * HTTP 状态码 → 说法。后端信封里的 message 更准，所以这一份只在两种情况兜底：
 * 响应根本不是信封（网关挂了、500 直接抛栈），或者压根没收到响应。
 */
const STATUS_MESSAGE: Record<number, string> = {
  400: '请求参数不对',
  401: '登录已失效，请重新登录',
  403: '没有权限这么操作',
  404: '要访问的东西不存在',
  405: '请求方式不被支持',
  408: '请求超时',
  413: '传的文件太大',
  429: '操作太频繁，稍后再试',
  500: '后端接口连接异常',
  502: '网关异常',
  503: '服务暂时不可用',
  504: '网关超时',
};

declare module 'axios' {
  interface AxiosRequestConfig {
    /** 请求拦截器盖的时间戳，响应拦截器用它算耗时 */
    startedAt?: number;
  }
}

/**
 * 服务端渲染时直连后端（Node 里没有相对路径可走，也吃不到 vite/astro 的 dev proxy），
 * 浏览器里走 PUBLIC_API_BASE：留空就是同源相对路径，dev 由 astro 的 proxy 转 8800。
 *
 * 后端地址在 Node 那一侧**运行时先定**：起进程给的环境变量盖过 build 时烧进产物的那份
 * （换后端不用重新打包）。dev 不用配环境变量 —— vite 的 loadEnv 只把 .env 交给
 * import.meta.env，不会写进 process.env，所以本地照样落到下面那一格。
 */
function pickBaseURL(): string {
  return import.meta.env.SSR
    ? process.env.API_SERVER_BASE ||
        (import.meta.env.API_SERVER_BASE as string) ||
        ''
    : (import.meta.env.PUBLIC_API_BASE as string) || '';
}

function pickTimeout(): number {
  return Number(import.meta.env.PUBLIC_API_TIMEOUT) || DEFAULT_TIMEOUT;
}

const http = axios.create({
  baseURL: pickBaseURL(),
  timeout: pickTimeout(),
  headers: { Accept: 'application/json' },
});

/**
 * token 只存在这一格模块变量里，落 localStorage 由 stores/auth 那份 blob 管（它每次变更都调一次 setAuthToken）。
 * 后端 sa-token 的 token-name 就是 Authorization，且没配 token-prefix，所以原样透传、不加 Bearer。
 */
let authToken: string | null = null;

export function setAuthToken(token: string | null): void {
  authToken = token;
}

// 请求前：带上 token、盖时间戳
http.interceptors.request.use(
  (config) => {
    if (authToken) {
      config.headers.Authorization = authToken;
    }
    config.startedAt = Date.now();
    if (import.meta.env.DEV) {
      console.debug(`[api] → ${(config.method || 'get').toUpperCase()} ${config.baseURL || ''}${config.url || ''}`);
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// 请求后：拆信封 + 状态码收口，失败一律转成 ApiError 抛出（这一层不弹提示，页面上怎么呈现由调用方定）
// 返回标成 any 是这一层唯一一处放开类型的地方：拦截器交出去的不是 AxiosResponse 而是拆好的 data，
// axios 的类型跟不住，对外的类型由下面 request() 那句断言兜住。
http.interceptors.response.use(
  (response): any => {
    if (import.meta.env.DEV) {
      const cost = response.config.startedAt ? Date.now() - response.config.startedAt : 0;
      console.debug(`[api] ← ${response.status} ${cost}ms ${response.config.url || ''}`);
    }
    const body = response.data as Partial<ApiResult<unknown>> | undefined;
    // 不是信封的响应（比如直接拿二进制）原样给出去，别在这里判空判出个新分支
    if (body && typeof body.code === 'number') {
      if (body.code !== 200) {
        throw new ApiError(body.message || `业务处理失败（code=${body.code}）`, body.code, response.status);
      }
      return body.data;
    }
    return response.data;
  },
  (error) => {
    const status: number | undefined = error.response?.status;
    if (import.meta.env.DEV) {
      console.debug(`[api] ✖ ${status ?? 'no-response'} ${error.config?.url || ''} ${error.message}`);
    }
    if (!error.response) {
      // 超时和断网都归在这里：axios 的 timeout 抛的是 code=ECONNABORTED 的错
      const timeout = error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT';
      throw new ApiError(
        timeout ? `请求超时（超过 ${http.defaults.timeout}ms）` : '网络不通，请检查后端服务',
        0,
        null,
      );
    }
    // 非 2xx 时后端也可能回了信封（全局异常处理器带着 message），那句比状态码表准；
    // 只认 code 是数字的，HTML 错误页别当成业务码读
    const raw = error.response.data as { code?: unknown; message?: unknown } | undefined;
    const envelope = raw && typeof raw.code === 'number' ? (raw as ApiResult<unknown>) : undefined;
    const code = envelope ? envelope.code : (status ?? 0);
    const message = envelope?.message || STATUS_MESSAGE[code] || `请求失败（HTTP ${status}）`;
    throw new ApiError(message, code, status ?? null);
  },
);

/** 单次请求可覆盖的两项；直接借 axios 自己的类型，免得在 headers 那儿对不上它的联合类型 */
export type ApiOptions = Pick<AxiosRequestConfig, 'timeout' | 'headers'>;

function request<T>(config: AxiosRequestConfig): Promise<T> {
  // 拦截器已经把 AxiosResponse 换成了拆好的 data，axios 的类型跟不住这一步（它的返回类型是条件类型，
  // 在泛型上没法收敛），所以这里断言一次：对外 apiGet/apiPost 的返回值就是 T。
  return http.request(config) as Promise<T>;
}

export function apiGet<T>(path: string, params?: Record<string, unknown>, options?: ApiOptions): Promise<T> {
  return request<T>({ ...options, method: 'GET', url: path, params });
}

export function apiPost<T>(path: string, data?: unknown, options?: ApiOptions): Promise<T> {
  return request<T>({ ...options, method: 'POST', url: path, data });
}

/** 更新：整条资源覆盖式提交，后端那侧的 PUT 走 @RequestBody 的 DTO */
export function apiPut<T>(path: string, data?: unknown, options?: ApiOptions): Promise<T> {
  return request<T>({ ...options, method: 'PUT', url: path, data });
}

export function apiDelete<T>(path: string, params?: Record<string, unknown>, options?: ApiOptions): Promise<T> {
  return request<T>({ ...options, method: 'DELETE', url: path, params });
}

/**
 * 文件上传：字段名固定 file（后端现有的收法是 @RequestParam("file") MultipartFile），
 * 其它普通参数放 fields。Content-Type 不手写，交给 axios 带 boundary。
 *
 * 注意后端现成的 /file/** 那一串都钉了 @SaCheckLogin，也不在 /api/** 的免登录名单里 —— 前台够不着。
 * 收文件走的是 FileApiController 那一份（POST /api/file/uploadImage，见 lib/profile 的 uploadAvatar），
 * 它已经在 /api 下面了，不用再另开一颗。
 */
export function apiUpload<T>(
  path: string,
  file: File,
  fields?: Record<string, string>,
  onProgress?: (percent: number) => void,
  options?: ApiOptions,
): Promise<T> {
  const form = new FormData();
  form.append('file', file);
  for (const [key, value] of Object.entries(fields || {})) {
    form.append(key, value);
  }
  return request<T>({
    ...options,
    method: 'POST',
    url: path,
    data: form,
    onUploadProgress: (event) => {
      if (onProgress && event.total) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    },
  });
}
