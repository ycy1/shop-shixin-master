import request from '@/utils/request'

/**
 * 获取优惠券列表（联表带出适用门店名和负责的运营昵称；发行总量/已发放/可发放量都用库里存的那两格算）
 */
export function listShopCouponApi(params?: any) {
  return request({
    url: '/activity/coupon/list',
    method: 'get',
    params
  })
}

/**
 * 获取一条优惠券详情（修改弹窗和只读详情弹窗共用；范围值已由后端拆成逗号分隔的 scopeIds）
 */
export function detailShopCouponApi(id: any) {
  return request({
    url: '/activity/coupon/' + id,
    method: 'get'
  })
}

/**
 * 获取一张券的发放记录（列表页那颗「发放记录」弹窗，按券分页、最新一条在前，只读）
 * params: { couponId 必填, status 可选（0未使用 1已使用 2已过期 3已作废）, pageNum, pageSize }
 */
export function listShopCouponRecordApi(params?: any) {
  return request({
    url: '/activity/coupon/record/list',
    method: 'get',
    params
  })
}

/**
 * 新增优惠券（券码不传由系统生成；状态一律落 1 未开始，已发放/已使用那两格表单说不动）
 */
export function addShopCouponApi(data: any) {
  return request({
    url: '/activity/coupon/add',
    method: 'post',
    data
  })
}

/**
 * 修改优惠券（状态也在这一次整行提交；发行总量能改，已发放/已使用改不动）
 */
export function updateShopCouponApi(data: any) {
  return request({
    url: '/activity/coupon/update',
    method: 'put',
    data
  })
}

/**
 * 删除优惠券（只删还没发出去过的那几张，这条判据在服务端）
 */
export function deleteShopCouponApi(ids: number[] | number) {
  return request({
    url: '/activity/coupon/delete/' + ids,
    method: 'delete'
  })
}

/**
 * 发放优惠券：给选中的每个用户各发 quantity 张（quantity 不传按一人一张）
 * data: { couponId, userIds: number[], quantity, remark }
 * 面额那几格由后端按模板当场快照，本次占的发行额度 = 人数 × 每人张数，返回实际发出的张数
 */
export function giveShopCouponApi(data: any) {
  return request({
    url: '/activity/coupon/give',
    method: 'post',
    data
  })
}
