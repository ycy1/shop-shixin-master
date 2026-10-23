import request from '@/utils/request'

/**
 * 获取物流公司列表
 */
export function listShopLogisticsApi(params?: any) {
  return request({
    url: '/shop/logistics/list',
    method: 'get',
    params
  })
}

/**
 * 获取启用状态的物流公司选项（不分页，供表单下拉使用）
 */
export function optionsShopLogisticsApi() {
  return request({
    url: '/shop/logistics/options',
    method: 'get'
  })
}

/**
 * 获取物流公司详情
 */
export function detailShopLogisticsApi(id: any) {
  return request({
    url: '/shop/logistics/' + id,
    method: 'get'
  })
}

/**
 * 添加物流公司（编码由服务端生成，新增时不传）
 */
export function addShopLogisticsApi(data: any) {
  return request({
    url: '/shop/logistics/add',
    method: 'post',
    data
  })
}

/**
 * 修改物流公司
 */
export function updateShopLogisticsApi(data: any) {
  return request({
    url: '/shop/logistics/update',
    method: 'put',
    data
  })
}

/**
 * 删除物流公司
 */
export function deleteShopLogisticsApi(ids: number[] | number) {
  return request({
    url: '/shop/logistics/delete/' + ids,
    method: 'delete'
  })
}
