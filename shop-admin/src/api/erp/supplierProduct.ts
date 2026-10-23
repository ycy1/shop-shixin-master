import request from '@/utils/request'

/**
 * 获取供货关系列表（供应商账号后端只返回自己那家的）
 */
export function listErpSupplierProductApi(params?: any) {
  return request({
    url: '/erp/supplierProduct/list',
    method: 'get',
    params
  })
}

/**
 * 获取供货关系详情（服务端额外按 sku_id 带出规格文本，修改弹窗那格的回显靠它）
 */
export function detailErpSupplierProductApi(id: any) {
  return request({
    url: '/erp/supplierProduct/' + id,
    method: 'get'
  })
}

/**
 * 添加供货关系（一行 = 一个供应商供某个规格；商品名称/所属分类由服务端按规格所属商品回填，前端传了也不认）
 */
export function addErpSupplierProductApi(data: any) {
  return request({
    url: '/erp/supplierProduct/add',
    method: 'post',
    data
  })
}

/**
 * 修改供货关系（供应商和规格改不了——它两定死了一条关系，只改供货价/编码/起订量/周期/默认/状态）
 */
export function updateErpSupplierProductApi(data: any) {
  return request({
    url: '/erp/supplierProduct/update',
    method: 'put',
    data
  })
}

/**
 * 删除供货关系
 */
export function deleteErpSupplierProductApi(ids: number[] | number) {
  return request({
    url: `/erp/supplierProduct/delete/` + ids,
    method: 'delete'
  })
}
