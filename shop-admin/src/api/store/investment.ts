import request from '@/utils/request'

/**
 * 获取招商线索列表
 */
export function listShopInvestmentApi(params?: any) {
  return request({
    url: '/shop/investment/list',
    method: 'get',
    params
  })
}

/**
 * 获取招商线索详情
 */
export function detailShopInvestmentApi(id: any) {
  return request({
    url: '/shop/investment/' + id,
    method: 'get'
  })
}

/**
 * 按门店取它来源那条招商线索（门店列表的「招商信息」弹窗用）
 * 关联钉在 shop_store.investment_id 上，所以一家店最多一条；这家店不是招商来的时 data 是 null
 */
export function investmentByStoreApi(storeId: any) {
  return request({
    url: '/shop/investment/byStore/' + storeId,
    method: 'get'
  })
}

/**
 * 招商线索候选（门店表单那格「招商信息」下拉用，不分页）
 * 只返回还没被任何门店占用的那几条；传 storeId 时把这家门店自己已经绑的那条也带回来，修改弹窗才回显得出来
 */
export function optionsShopInvestmentApi(storeId?: number) {
  return request({
    url: '/shop/investment/options',
    method: 'get',
    params: { storeId }
  })
}

/**
 * 新增招商线索
 */
export function addShopInvestmentApi(data: any) {
  return request({
    url: '/shop/investment/add',
    method: 'post',
    data
  })
}

/**
 * 修改招商线索（状态也在这一格改）
 */
export function updateShopInvestmentApi(data: any) {
  return request({
    url: '/shop/investment/update',
    method: 'put',
    data
  })
}

/**
 * 删除招商线索
 */
export function deleteShopInvestmentApi(ids: number[] | number) {
  return request({
    url: '/shop/investment/delete/' + ids,
    method: 'delete'
  })
}
