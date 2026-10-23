import request from '@/utils/request'

/**
 * 获取入库单列表（不带明细）
 */
export function listErpPurchaseInApi(params?: any) {
  return request({
    url: '/erp/purchaseIn/list',
    method: 'get',
    params
  })
}

/**
 * 获取入库单详情（带 items 明细数组）
 */
export function detailErpPurchaseInApi(id: any) {
  return request({
    url: '/erp/purchaseIn/' + id,
    method: 'get'
  })
}

/**
 * 一张采购单名下的全部入库单，每张连明细（items）一起返回。
 * 采购单页那个「入库单」弹窗用它：列表接口不带明细，逐张再调详情就是 N+1
 */
export function listErpPurchaseInByPurchaseApi(purchaseId: any) {
  return request({
    url: '/erp/purchaseIn/byPurchase/' + purchaseId,
    method: 'get'
  })
}

/**
 * 选采购单时带出的入库明细候选：每行是采购单的明细（商品名/编码、采购数量、已入库数量），
 * 外加 availableQuantity（可入库数量 = 采购数量 − 这张采购单下其它入库单已登记的量）。
 * 改单时要带上 excludeReceiptId，免得把这张单自己已登记的量当成别人占的额度
 */
export function optionsErpPurchaseInItemApi(purchaseId: any, excludeReceiptId?: any) {
  return request({
    url: '/erp/purchaseIn/purchaseItems/' + purchaseId,
    method: 'get',
    params: { excludeReceiptId }
  })
}

/**
 * 添加入库单（主表和明细一起提交；供应商由后端按 purchaseId 回填，表单不用传）
 */
export function addErpPurchaseInApi(data: any) {
  return request({
    url: '/erp/purchaseIn/add',
    method: 'post',
    data
  })
}

/**
 * 修改入库单（明细整单重存：以后端返回的 items 为准增删改，不单独发明细接口）
 */
export function updateErpPurchaseInApi(data: any) {
  return request({
    url: '/erp/purchaseIn/update',
    method: 'put',
    data
  })
}

/**
 * 删除入库单（明细跟着一起删；已经入过货的单子后端会拦住不给删）
 */
export function deleteErpPurchaseInApi(ids: number[] | number) {
  return request({
    url: `/erp/purchaseIn/delete/` + ids,
    method: 'delete'
  })
}

/**
 * 执行入库：只认 id 和 items 里每行的 { id, thisQuantity(本次入库数), price(本次成本价) }。
 * 后端会加库存、刷加权成本价、累加这单和采购单的已入库数量，并按进度把两边状态往前推，
 * 所以状态/入库时间/供应商/仓库这些都不用传
 */
export function receiveErpPurchaseInApi(data: any) {
  return request({
    url: '/erp/purchaseIn/receive',
    method: 'put',
    data
  })
}
