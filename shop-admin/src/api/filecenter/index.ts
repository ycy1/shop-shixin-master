import request from '@/utils/request'

/**
 * 文件中心分页列表
 * @param params source: 1我的文件 / 2部门文件 / 3角色文件 / 4回收站
 */
export function getFileCenterPageApi(params?: any) {
  return request({
    url: '/file/center/page',
    method: 'get',
    params
  })
}

/**
 * 上传文件（multipart/form-data）
 */
export function uploadFileCenterApi(data: any) {
  return request({
    url: '/file/center/upload',
    method: 'post',
    headers: { 'Content-Type': 'multipart/form-data' },
    data
  })
}

/**
 * 下载文件（blob）
 */
export function downloadFileCenterApi(id: number | string) {
  return request({
    url: `/file/center/download/${id}`,
    method: 'get',
    responseType: 'blob'
  })
}

/**
 * 移入回收站
 */
export function deleteFileCenterApi(ids: string[] | number) {
  return request({
    url: `/file/center/${ids}`,
    method: 'delete'
  })
}

/**
 * 还原文件
 */
export function restoreFileCenterApi(ids: string[] | number) {
  return request({
    url: `/file/center/restore/${ids}`,
    method: 'put'
  })
}

/**
 * 彻底删除文件
 */
export function deleteFileCenterForeverApi(ids: string[] | number) {
  return request({
    url: `/file/center/forever/${ids}`,
    method: 'delete'
  })
}

/**
 * 设置过期时间（单个/批量）
 */
export function updateFileCenterExpireApi(data: { ids: number[]; expireTime: string }) {
  return request({
    url: '/file/center/expire',
    method: 'put',
    data
  })
}
