import request from '@/utils/request'

/**
 * 获取潮玩系列列表
 */
export function listShopTrendyApi(params?: any) {
  return request({
    url: '/shop/trendy/list',
    method: 'get',
    params
  })
}

/**
 * 获取启用状态的潮玩系列选项（不分页，供商品/品牌/门店表单下拉使用）
 */
export function optionsShopTrendyApi() {
  return request({
    url: '/shop/trendy/options',
    method: 'get'
  })
}

/**
 * 获取潮玩系列详情
 */
export function detailShopTrendyApi(id: any) {
  return request({
    url: '/shop/trendy/' + id,
    method: 'get'
  })
}

/**
 * 某条潮玩系列的集齐进度（列表上那颗「集齐进度」弹窗，分页）
 *
 * params: trendyId（必填）/ keyword（按人的昵称、账号或手机号筛，可空）+ 分页两参
 */
export function listTrendyCollectApi(params?: any) {
  return request({
    url: '/shop/trendy/collect/progress',
    method: 'get',
    params
  })
}

/**
 * 进度列表里点开某一个人：这条系列下的全部规格，一格上标出他抽到过没有（不分页，一次全带）
 */
export function listTrendyCollectItemsApi(params: { trendyId: any, userId: any }) {
  return request({
    url: '/shop/trendy/collect/items',
    method: 'get',
    params
  })
}

/**
 * 把一批商品绑到某个潮玩系列上（data.trendyId 传 null 就是解绑）
 */
export function bindTrendyProductsApi(data: { productIds: number[], trendyId: number | null }) {
  return request({
    url: '/shop/trendy/product/bind',
    method: 'put',
    data
  })
}

/**
 * 添加潮玩系列
 */
export function addShopTrendyApi(data: any) {
  return request({
    url: '/shop/trendy/add',
    method: 'post',
    data
  })
}

/**
 * 修改潮玩系列
 */
export function updateShopTrendyApi(data: any) {
  return request({
    url: '/shop/trendy/update',
    method: 'put',
    data
  })
}

/**
 * 删除潮玩系列
 */
export function deleteShopTrendyApi(ids: number[] | number) {
  return request({
    url: '/shop/trendy/delete/' + ids,
    method: 'delete'
  })
}
