import request from '@/utils/request'

/**
 * 获取园区照片列表（parkId/ipId/sourceType/status/isFeatured 精确匹配，keyword 模糊匹配描述和标签）
 */
export function listParkPhotoApi(params?: any) {
  return request({
    url: '/shop/parkPhoto/list',
    method: 'get',
    params
  })
}

/**
 * 标签候选（Redis 里那一串词表，不是字典也不按当前列表数）
 */
export function tagOptionsParkPhotoApi() {
  return request({
    url: '/shop/parkPhoto/tagOptions',
    method: 'get'
  })
}

/**
 * 获取照片详情
 */
export function detailParkPhotoApi(id: any) {
  return request({
    url: '/shop/parkPhoto/' + id,
    method: 'get'
  })
}

/**
 * 添加照片（所属 IP 由服务端按园区带过来，页面不给填）
 */
export function addParkPhotoApi(data: any) {
  return request({
    url: '/shop/parkPhoto/add',
    method: 'post',
    data
  })
}

/**
 * 修改照片
 */
export function updateParkPhotoApi(data: any) {
  return request({
    url: '/shop/parkPhoto/update',
    method: 'put',
    data
  })
}

/**
 * 审核：把审核状态和审核意见两格一起推过去（通过 1 / 拒绝 2 / 已隐藏 3），服务端顺手给上传人发一条结论消息。
 * 单开一条而不复用「修改」，是因为整行提交会把没带上的人名、描述、意见那些可空列抹成 NULL。
 * auditMsg 留空就是不写意见（服务端归一成 NULL，把上一次那句清掉），所以这里 undefined 干脆不发这一格。
 */
export function auditParkPhotoApi(id: any, status: number, auditMsg?: string) {
  return request({
    url: '/shop/parkPhoto/audit/' + id,
    method: 'put',
    params: auditMsg ? { status, auditMsg } : { status }
  })
}

/**
 * 删除照片
 */
export function deleteParkPhotoApi(ids: number[] | number) {
  return request({
    url: '/shop/parkPhoto/delete/' + ids,
    method: 'delete'
  })
}
