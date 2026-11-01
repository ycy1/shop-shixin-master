import request from '@/utils/request'

/**
 * 获取潮玩IP列表
 */
export function listShopIpApi(params?: any) {
  return request({
    url: '/shop/trendyIp/list',
    method: 'get',
    params
  })
}

/**
 * 获取启用状态的潮玩IP选项（不分页，供系列表单「所属IP」下拉使用）
 */
export function optionsShopIpApi() {
  return request({
    url: '/shop/trendyIp/options',
    method: 'get'
  })
}

/**
 * 获取潮玩IP详情
 */
export function detailShopIpApi(id: any) {
  return request({
    url: '/shop/trendyIp/' + id,
    method: 'get'
  })
}

/**
 * 把一批潮玩系列绑到某个IP上（data.ipId 传 null 就是解绑）
 */
export function bindIpTrendiesApi(data: { trendyIds: number[], ipId: number | null }) {
  return request({
    url: '/shop/trendyIp/trendy/bind',
    method: 'put',
    data
  })
}

/**
 * 添加潮玩IP
 */
export function addShopIpApi(data: any) {
  return request({
    url: '/shop/trendyIp/add',
    method: 'post',
    data
  })
}

/**
 * 修改潮玩IP
 */
export function updateShopIpApi(data: any) {
  return request({
    url: '/shop/trendyIp/update',
    method: 'put',
    data
  })
}

/**
 * 删除潮玩IP
 */
export function deleteShopIpApi(ids: number[] | number) {
  return request({
    url: '/shop/trendyIp/delete/' + ids,
    method: 'delete'
  })
}
