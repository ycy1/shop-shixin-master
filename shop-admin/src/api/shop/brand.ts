import request from '@/utils/request'

/**
 * 获取品牌列表
 */
export function listShopBrandApi(params?: any) {
  return request({
    url: '/shop/brand/list',
    method: 'get',
    params
  })
}

/**
 * 获取启用状态的品牌选项（不分页，供商品表单下拉使用）
 * 传 categoryId 时返回该分类下的品牌 + 未分类的品牌
 */
export function optionsShopBrandApi(categoryId?: number) {
  return request({
    url: '/shop/brand/options',
    method: 'get',
    params: { categoryId }
  })
}

/**
 * 获取品牌详情
 */
export function detailShopBrandApi(id: any) {
  return request({
    url: '/shop/brand/' + id,
    method: 'get'
  })
}

/**
 * 添加品牌
 */
export function addShopBrandApi(data: any) {
  return request({
    url: '/shop/brand/add',
    method: 'post',
    data
  })
}

/**
 * 修改品牌
 */
export function updateShopBrandApi(data: any) {
  return request({
    url: '/shop/brand/update',
    method: 'put',
    data
  })
}

/**
 * 删除品牌
 */
export function deleteShopBrandApi(ids: number[] | number) {
  return request({
    url: '/shop/brand/delete/' + ids,
    method: 'delete'
  })
}
