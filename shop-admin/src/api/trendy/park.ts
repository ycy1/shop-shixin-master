import request from '@/utils/request'

/**
 * 获取园区列表（keyword 模糊匹配名称/编号，ipId/status 精确匹配）
 */
export function listShopParkApi(params?: any) {
  return request({
    url: '/shop/park/list',
    method: 'get',
    params
  })
}

/**
 * 获取营业中的园区选项（供票种表单、出票表单那格「所属园区」下拉使用，不分页）
 */
export function optionsShopParkApi() {
  return request({
    url: '/shop/park/options',
    method: 'get'
  })
}

/**
 * 获取园区详情
 */
export function detailShopParkApi(id: any) {
  return request({
    url: '/shop/park/' + id,
    method: 'get'
  })
}

/**
 * 添加园区（编号由服务端生成，页面不给填）
 */
export function addShopParkApi(data: any) {
  return request({
    url: '/shop/park/add',
    method: 'post',
    data
  })
}

/**
 * 修改园区
 */
export function updateShopParkApi(data: any) {
  return request({
    url: '/shop/park/update',
    method: 'put',
    data
  })
}

/**
 * 删除园区（名下还有票种时服务端会拦住）
 */
export function deleteShopParkApi(ids: number[] | number) {
  return request({
    url: '/shop/park/delete/' + ids,
    method: 'delete'
  })
}
