import request from '@/utils/request'

/**
 * 获取仓库列表
 */
export function listErpWarehouseApi(params?: any) {
  return request({
    url: '/erp/warehouse/list',
    method: 'get',
    params
  })
}

/**
 * 获取启用状态的仓库下拉选项（不分页，给商品入库等表单用；口径同 optionsErpSupplierApi）
 */
export function optionsErpWarehouseApi() {
  return request({
    url: '/erp/warehouse/options',
    method: 'get'
  })
}

/**
 * 获取仓库详情
 */
export function detailErpWarehouseApi(id: any) {
  return request({
    url: '/erp/warehouse/' + id,
    method: 'get'
  })
}

/**
 * 添加仓库
 */
export function addErpWarehouseApi(data: any) {
  return request({
    url: '/erp/warehouse/add',
    method: 'post',
    data
  })
}

/**
 * 修改仓库
 */
export function updateErpWarehouseApi(data: any) {
  return request({
    url: '/erp/warehouse/update',
    method: 'put',
    data
  })
}

/**
 * 删除仓库
 */
export function deleteErpWarehouseApi(ids: number[] | number) {
  return request({
    url: `/erp/warehouse/delete/` + ids,
    method: 'delete'
  })
}
