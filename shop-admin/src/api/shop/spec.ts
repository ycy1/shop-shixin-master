import request from '@/utils/request'

/**
 * 获取商品规格列表
 */
export function listShopSpecApi(params?: any) {
  return request({
    url: '/shop/spec/list',
    method: 'get',
    params
  })
}

/**
 * 获取启用状态的规格选项（不分页，供商品表单下拉使用）
 */
export function optionsShopSpecApi(categoryId?: number | string) {
  return request({
    url: '/shop/spec/options',
    method: 'get',
    params: { categoryId }
  })
}

/**
 * 获取商品规格详情
 */
export function detailShopSpecApi(id: any) {
  return request({
    url: '/shop/spec/' + id,
    method: 'get'
  })
}

/**
 * 添加商品规格
 */
export function addShopSpecApi(data: any) {
  return request({
    url: '/shop/spec/add',
    method: 'post',
    data
  })
}

/**
 * 修改商品规格
 */
export function updateShopSpecApi(data: any) {
  return request({
    url: `/shop/spec/update`,
    method: 'put',
    data
  })
}

/**
 * 删除商品规格
 */
export function deleteShopSpecApi(ids: number[] | number) {
  return request({
    url: `/shop/spec/delete/` + ids,
    method: 'delete'
  })
}
