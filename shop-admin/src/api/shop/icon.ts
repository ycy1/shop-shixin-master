import request from '@/utils/request'

/**
 * 获取图标列表
 */
export function listShopIconApi(params?: any) {
  return request({
    url: '/shop/icon/list',
    method: 'get',
    params
  })
}

/**
 * 获取启用状态的图标选项（不分页，供图标选择器使用）
 */
export function optionsShopIconApi(categoryId?: number | string) {
  return request({
    url: '/shop/icon/options',
    method: 'get',
    params: { categoryId }
  })
}

/**
 * 获取图标详情
 */
export function detailShopIconApi(id: any) {
  return request({
    url: '/shop/icon/' + id,
    method: 'get'
  })
}

/**
 * 添加图标
 */
export function addShopIconApi(data: any) {
  return request({
    url: '/shop/icon/add',
    method: 'post',
    data
  })
}

/**
 * 修改图标
 */
export function updateShopIconApi(data: any) {
  return request({
    url: `/shop/icon/update`,
    method: 'put',
    data
  })
}

/**
 * 删除图标
 */
export function deleteShopIconApi(ids: number[] | number) {
  return request({
    url: `/shop/icon/delete/` + ids,
    method: 'delete'
  })
}
