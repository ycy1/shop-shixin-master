import request from '@/utils/request'

/**
 * 获取供应商列表
 */
export function listErpSupplierApi(params?: any) {
  return request({
    url: '/erp/supplier/list',
    method: 'get',
    params
  })
}

/**
 * 获取启用状态的供应商下拉选项（不分页，采购单表单用）
 */
export function optionsErpSupplierApi() {
  return request({
    url: '/erp/supplier/options',
    method: 'get'
  })
}

/**
 * 获取供应商详情
 */
export function detailErpSupplierApi(id: any) {
  return request({
    url: '/erp/supplier/' + id,
    method: 'get'
  })
}

/**
 * 添加供应商
 */
export function addErpSupplierApi(data: any) {
  return request({
    url: '/erp/supplier/add',
    method: 'post',
    data
  })
}

/**
 * 修改供应商
 */
export function updateErpSupplierApi(data: any) {
  return request({
    url: '/erp/supplier/update',
    method: 'put',
    data
  })
}

/**
 * 删除供应商
 */
export function deleteErpSupplierApi(ids: number[] | number) {
  return request({
    url: `/erp/supplier/delete/` + ids,
    method: 'delete'
  })
}
