import type { IResponse } from '@/http/types'
import { getResponseMessage, isSuccessResultCode } from '@/http/tools/enum'
import { useTokenStore } from '@/store/token'
import { getEnvBaseUrl } from '@/utils'

/** 公共上传口（shop-file 那份 FileApiController，路径写死在文件服务上，不在 /api/app 也不在 /api/comm） */
const UPLOAD_IMAGE_PATH = '/api/file/uploadImage'

/**
 * 传一张图，回来的就是能落到那一格的 URL（头像、评论配图共用这一条）。
 *
 * 走的是现成的 POST /api/file/uploadImage —— 收文件、压缩、落哪个存储都在那边，前端这一层不另开一份。
 * source 那一格是后端拿来分目录的（一天一个目录 + 一个来源子目录），传什么由调用方定：头像传 avatar，
 * 评论配图传 comment，和后台上传组件传的同一套值。
 *
 * 只能走 uni.uploadFile（multipart，http.ts 那层包的是 uni.request），所以基准地址和 token 头都要自己拼 ——
 * 拼法和 src/http/interceptor.ts 同一套：H5 开着代理时走代理前缀，其余情况拼环境基准地址。
 */
export function uploadImage(filePath: string, source: string) {
  return new Promise<string>((resolve, reject) => {
    const token = useTokenStore().updateNowTime().validToken
    let uploadUrl = getEnvBaseUrl() + UPLOAD_IMAGE_PATH
    // #ifdef H5
    // H5 开着代理时拼代理前缀（dev 由 vite 转发到后端），否则直接拼环境基准地址 —— 和 interceptor.ts 同一判据
    if (JSON.parse(import.meta.env.VITE_APP_PROXY_ENABLE)) {
      uploadUrl = import.meta.env.VITE_APP_PROXY_PREFIX + UPLOAD_IMAGE_PATH
    }
    // #endif
    uni.uploadFile({
      url: uploadUrl,
      filePath,
      name: 'file',
      formData: { source },
      timeout: 60000, // 图片比一般请求慢，超时单独放宽（同 shop-web 的 uploadAvatar）
      header: token ? { Authorization: token } : {},
      success: (res) => {
        // uploadFile 不判状态码也不解 JSON：回包是 { code, data: url } 那个信封，得自己拆
        if (res.statusCode < 200 || res.statusCode >= 300) {
          return reject(new Error(`图片上传失败(${res.statusCode})`))
        }
        let body: Partial<IResponse<string>>
        try {
          body = JSON.parse(res.data) as Partial<IResponse<string>>
        }
        catch {
          return reject(new Error('图片上传失败'))
        }
        if (!isSuccessResultCode(body?.code as number) || !body?.data) {
          return reject(new Error(getResponseMessage(body, '图片上传失败')))
        }
        resolve(body.data)
      },
      fail: () => reject(new Error('网络错误，换个网络试试')),
    })
  })
}
