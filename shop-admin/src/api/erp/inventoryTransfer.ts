import request from '@/utils/request'

/**
 * 获取调拨单列表（不带明细；ERP操作员账号只看自己负责的）。
 * 每行除了两家仓的名称，还带它们各自的状态（调拨中的那两家要在列表上说清楚），
 * 以及已调出/已调入两个合计数 —— 「调拨」「取消」按钮能不能点就看这些数
 */
export function listErpInventoryTransferApi(params?: any) {
  return request({
    url: '/erp/inventoryTransfer/list',
    method: 'get',
    params
  })
}

/**
 * 获取调拨单详情（带 items 明细数组；每行带调出仓这条规格现在的可用库存，
 * 所以点开「调拨」弹窗就能看出这一行还剩几件能动）
 */
export function detailErpInventoryTransferApi(id: any) {
  return request({
    url: '/erp/inventoryTransfer/' + id,
    method: 'get'
  })
}

/**
 * 调拨明细的规格候选：按调出仓库取「这个仓现在还有可用库存（stock - locked_stock > 0）」的那些库存行，
 * 一行是一条规格（不是商品）。warehouseId 必填（没选调出仓就不该发这个请求），keyword 可为空，
 * 一次最多 20 行（后端限的）。返回的行带 skuId / productId / productName / productCode / specText /
 * availableStock / costPrice
 */
export function optionsErpTransferSkuApi(params: { warehouseId: any; keyword?: string }) {
  return request({
    url: '/erp/inventoryTransfer/skuOptions',
    method: 'get',
    params
  })
}

/**
 * 添加调拨单（主表和明细一起提交）。单号、两家仓的状态、成本价、总数量/总成本都由后端算，
 * 表单只传 fromWarehouseId / toWarehouseId / transferType / userId / reason / remark 和 items
 * （每行 { skuId, quantity }）。建完这两家仓会被置成 3 调拨中
 */
export function addErpInventoryTransferApi(data: any) {
  return request({
    url: '/erp/inventoryTransfer/add',
    method: 'post',
    data
  })
}

/**
 * 修改调拨单（明细整单重存）。只有 0 待审核 能改，而且调出/调入两家仓换不了 ——
 * 这两列后端会钉回原值，改了直接报「不支持换仓库」， so 表单上就把它俩做成只读
 */
export function updateErpInventoryTransferApi(data: any) {
  return request({
    url: '/erp/inventoryTransfer/update',
    method: 'put',
    data
  })
}

/**
 * 删除调拨单（明细跟着一起删；只有待审核能删，删完这两家仓会被腾出来）
 */
export function deleteErpInventoryTransferApi(ids: number[] | number) {
  return request({
    url: '/erp/inventoryTransfer/delete/' + ids,
    method: 'delete'
  })
}

/**
 * 审核调拨单（0 待审核 → 1 已审核）。审核过了才允许开始调拨，
 * 因为「调拨」是直接动两家仓的库存的，得先有人认过这张单
 */
export function auditErpInventoryTransferApi(id: any) {
  return request({
    url: '/erp/inventoryTransfer/audit/' + id,
    method: 'put'
  })
}

/**
 * 执行调拨：只认 id 和 items 里每行的 { id, thisQuantity(本次调拨数) }。
 * 后端在同一个事务里把调出仓减掉、调入仓加上（成本价按调出仓那个数做加权），各记一行流水
 * （6 调拨出库 + 5 调拨入库），再把这一行的 out_quantity/receive_quantity 一起往上累加，
 * 按进度推主表状态：调满 3 已完成（并腾出两家仓）、没调满 2 调出中。
 * 一张单可以点多次，直到调满为止
 */
export function transferErpInventoryTransferApi(data: any) {
  return request({
    url: '/erp/inventoryTransfer/transfer',
    method: 'put',
    data
  })
}

/**
 * 取消调拨单（0/1 → 4 已取消）。这两档一笔库存都没动，取消只是作废 + 把两家仓腾出来；
 * 已经调出过货（2/3）的就取消不掉了
 */
export function cancelErpInventoryTransferApi(id: any) {
  return request({
    url: '/erp/inventoryTransfer/cancel/' + id,
    method: 'put'
  })
}
