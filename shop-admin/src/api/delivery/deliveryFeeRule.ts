import request from '@/utils/request'

/**
 * 配送费规则（/shop/deliveryFeeRule）
 *
 * 这一张是平台级的计价配置：规则建好、启用、排好优先级之后，
 * 买家**统一下单**那一刻，服务端就挑"启用中优先级最高"的那一条把这一单的配送费算死（跟货款一起收，之后改规则不回头）。
 * 弹窗里没有配送费那一格可以填，钱只能靠这里配出来的规则生成。
 */

/**
 * 获取配送费规则列表
 */
export function listShopDeliveryFeeRuleApi(params?: any) {
  return request({
    url: '/shop/deliveryFeeRule/list',
    method: 'get',
    params
  })
}

/**
 * 获取配送费规则详情
 */
export function detailShopDeliveryFeeRuleApi(id: any) {
  return request({
    url: '/shop/deliveryFeeRule/' + id,
    method: 'get'
  })
}

/**
 * 添加配送费规则
 */
export function addShopDeliveryFeeRuleApi(data: any) {
  return request({
    url: '/shop/deliveryFeeRule/add',
    method: 'post',
    data
  })
}

/**
 * 修改配送费规则
 */
export function updateShopDeliveryFeeRuleApi(data: any) {
  return request({
    url: '/shop/deliveryFeeRule/update',
    method: 'put',
    data
  })
}

/**
 * 删除配送费规则
 */
export function deleteShopDeliveryFeeRuleApi(ids: number[] | number) {
  return request({
    url: '/shop/deliveryFeeRule/delete/' + ids,
    method: 'delete'
  })
}
