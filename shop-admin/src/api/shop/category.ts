import request from '@/utils/request'

/**
 * 获取商品类目树
 */
export function getCategoryTreeApi(params?: any) {
  return request({
    url: '/shop/category',
    method: 'get',
    params
  })
}

/**
 * 新增商品类目
 */
export function createCategoryApi(data: any) {
  return request({
    url: '/shop/category',
    method: 'post',
    data
  })
}

/**
 * 修改商品类目
 */
export function updateCategoryApi(data: any) {
  return request({
    url: '/shop/category',
    method: 'put',
    data
  })
}

/**
 * 删除商品类目
 */
export function deleteCategoryApi(ids: number[] | number) {
  return request({
    url: `/shop/category/delete/${ids}`,
    method: 'delete'
  })
}
