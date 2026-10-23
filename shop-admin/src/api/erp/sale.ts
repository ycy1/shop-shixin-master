import request from '@/utils/request'

/**
 * 获取销售单列表（不带明细；ERP操作员账号只看自己负责的）
 */
export function listErpSaleApi(params?: any) {
  return request({
    url: '/erp/sale/list',
    method: 'get',
    params
  })
}

/**
 * 获取销售明细的商品候选：后端按「商品属于这家门店（shop_product.store_id）且这一条规格标了可销售
 * （shop_product_sku.sale_enable=1）」收窄，storeId 必填，keyword 可为空；一行就是一个规格（不是商品）。
 * 传了 warehouseId 再加一档 —— 这个仓里有**这一条规格**的可用库存（stock - locked_stock > 0）才回来。
 * 返回的行是商品 + 规格拼出来的（id / skuId / productName / specText / productPrice），
 * 明细那行落的是 skuId，单价那一格默认带 productPrice
 */
export function optionsErpSaleProductApi(params: { storeId: any; warehouseId?: any; keyword?: string }) {
  return request({
    url: '/erp/sale/productOptions',
    method: 'get',
    params
  })
}

/**
 * 获取销售单详情（带 items 明细数组）
 */
export function detailErpSaleApi(id: any) {
  return request({
    url: '/erp/sale/' + id,
    method: 'get'
  })
}

/**
 * 添加销售单（主表和明细一起提交：data.items 是明细数组）
 */
export function addErpSaleApi(data: any) {
  return request({
    url: '/erp/sale/add',
    method: 'post',
    data
  })
}

/**
 * 修改销售单（明细整单重存；「审核」「作废」都是在这里选状态）。
 * 只有待审核的还能改，审核过就整单冻结
 */
export function updateErpSaleApi(data: any) {
  return request({
    url: '/erp/sale/update',
    method: 'put',
    data
  })
}

/**
 * 删除销售单（明细跟着一起删；只有待审核 / 已取消 的删得掉）
 */
export function deleteErpSaleApi(ids: number[] | number) {
  return request({
    url: '/erp/sale/delete/' + ids,
    method: 'delete'
  })
}
