import request from '@/utils/request'

/**
 * 获取采购单列表（不带明细）
 */
export function listErpPurchaseApi(params?: any) {
  return request({
    url: '/erp/purchase/list',
    method: 'get',
    params
  })
}

/**
 * 获取采购明细的商品候选：后端按所选供应商在 shop_supplier_product 里登记的供货关系（合作中）收窄，
 * 没建供货关系就是没有候选。supplierId 必填，keyword 可为空。
 * 返回的是供货关系行：商品 id 在 productId 上（id 是这条关系自己的），带 productName/productCode/supplyPrice
 */
export function optionsErpPurchaseProductApi(params: { supplierId: any; keyword?: string }) {
  return request({
    url: '/erp/purchase/productOptions',
    method: 'get',
    params
  })
}

/**
 * 获取采购单详情（带 items 明细数组）
 */
export function detailErpPurchaseApi(id: any) {
  return request({
    url: '/erp/purchase/' + id,
    method: 'get'
  })
}

/**
 * 添加采购单（主表和明细一起提交：data.items 是明细数组）
 */
export function addErpPurchaseApi(data: any) {
  return request({
    url: '/erp/purchase/add',
    method: 'post',
    data
  })
}

/**
 * 修改采购单（明细整单重存：以后端返回的 items 为准增删改，不单独发明细接口）
 */
export function updateErpPurchaseApi(data: any) {
  return request({
    url: `/erp/purchase/update`,
    method: 'put',
    data
  })
}

/**
 * 删除采购单（明细跟着一起删）
 */
export function deleteErpPurchaseApi(ids: number[] | number) {
  return request({
    url: `/erp/purchase/delete/` + ids,
    method: 'delete'
  })
}

/**
 * 取「采购退货」弹窗的草稿（不落库）：返回的是一张还没入库的出库单
 * （outboundSource=3、orderId/orderNo 是这张采购单、status=0 待出库），
 * items 一行一个商品，每行带 receivedQuantity（已入库）/ returnableQuantity（还能退，弹窗里的上限）、
 * quantity=0（本次退货数，由人填）、costPrice（默认按加权采购单价带出，可改）、warehouseId（默认那家仓）。
 * 一件能退的都没有时后端直接报错，弹窗不弹半截的
 */
export function returnDraftErpPurchaseApi(id: any) {
  return request({
    url: '/erp/purchase/' + id + '/returnDraft',
    method: 'get'
  })
}

/**
 * 提交「采购退货」：建一张来源=3 采购退货 的出库单（待出库，仓库在「商品出库」那页点「出库」才真减库存），
 * 返回新建的出库单 id。data 只认 items 每行的 productId/warehouseId/quantity/costPrice 和 remark，
 * 单号、总数、总金额、状态一律后端算。可多次退货：一张采购单名下能有几张这样的单子
 */
export function createReturnErpPurchaseApi(id: any, data: any) {
  return request({
    url: '/erp/purchase/' + id + '/return',
    method: 'post',
    data
  })
}
