import request from '@/utils/request'

/**
 * 获取盘点单列表（不带明细；ERP操作员账号只看自己盘的）。
 * 每行除了盘点仓库的名称，还带它现在的状态（盘点中的那家在列表上要说清楚）
 * 和已调整的行数 —— 「调整」「取消」按钮能不能点就看这些数
 */
export function listErpInventoryCheckApi(params?: any) {
  return request({
    url: '/erp/inventoryCheck/list',
    method: 'get',
    params
  })
}

/**
 * 获取盘点单详情（带 items 明细数组；每行带这条规格在盘点仓现在的总库存和可用库存，
 * 所以点开「调整」弹窗就能看出账面数和现在对不对得上、盘亏那几件还动得了动不了）
 */
export function detailErpInventoryCheckApi(id: any) {
  return request({
    url: '/erp/inventoryCheck/' + id,
    method: 'get'
  })
}

/**
 * 盘点明细的规格候选：按盘点仓库取「这个仓登记在册的那些库存行」，一行是一条规格（不是商品）。
 * 和调拨那份的区别是不筛可用库存 —— 账面 0 件的那一行恰恰最该盘（实物在就是盘盈）。
 * warehouseId 必填（没选盘点仓就不该发这个请求），keyword 可为空，一次最多 20 行（后端限的）。
 * 返回的行带 skuId / productId / productName / productCode / specText / stock / lockedStock /
 * availableStock / costPrice —— 选中后 book_quantity 带的就是 stock 那一格
 */
export function optionsErpCheckSkuApi(params: { warehouseId: any; keyword?: string }) {
  return request({
    url: '/erp/inventoryCheck/skuOptions',
    method: 'get',
    params
  })
}

/**
 * 添加盘点单（主表和明细一起提交）。单号、账面数、成本价、差异那几个数都由后端算，
 * 表单只传 warehouseId / checkType / userId / remark 和 items（每行 { skuId }，实盘数可以留到修改时录）。
 * 建完这家仓会被置成 2 盘点中
 */
export function addErpInventoryCheckApi(data: any) {
  return request({
    url: '/erp/inventoryCheck/add',
    method: 'post',
    data
  })
}

/**
 * 修改盘点单（录实盘、改盘点范围）。0 待审核 和 1 盘点中 两档都能进这个弹窗，
 * 但已经调整过的那几行不许动（改了后端会报「已经调整过了」），盘点仓也换不了 ——
 * 这一列后端钉回原值，所以表单上做成只读
 */
export function updateErpInventoryCheckApi(data: any) {
  return request({
    url: '/erp/inventoryCheck/update',
    method: 'put',
    data
  })
}

/**
 * 删除盘点单（明细跟着一起删；只有待审核能删，删完这家仓会被腾出来）
 */
export function deleteErpInventoryCheckApi(ids: number[] | number) {
  return request({
    url: '/erp/inventoryCheck/delete/' + ids,
    method: 'delete'
  })
}

/**
 * 审核盘点单（0 待审核 → 1 盘点中，并补开始时间）。
 * 审核过了才开始录实盘、才允许调整 —— 「调整」是直接动库存的，得先有人认过这张单
 */
export function auditErpInventoryCheckApi(id: any) {
  return request({
    url: '/erp/inventoryCheck/audit/' + id,
    method: 'put'
  })
}

/**
 * 确认调整：只认 id 和 items 里每行的 { id, thisAdjust(本次是否调整), diffReason(差异原因) }。
 * 后端按这一行的 diff_quantity 直接动盘点仓的库存并记流水（正数记 7 盘盈、负数记 8 盘亏、
 * 为 0 的不动账也不记流水，但也算确认过这一笔），然后把行置成 2 已调整。
 * 全部行都到 2 才推主表 2 已完成（补结束时间并腾出仓库），否则停在 1 盘点中。
 * 一行整行一次调平、只能调一次，所以没有"本次调整数"那一格；一张单可以点多次（分几批调）
 */
export function adjustErpInventoryCheckApi(data: any) {
  return request({
    url: '/erp/inventoryCheck/adjust',
    method: 'put',
    data
  })
}

/**
 * 取消盘点单（0/1 → 3 已取消）。这两档只要还没有一行调过账，取消就只是一笔没动的作废 + 把仓库腾出来；
 * 已经调整过库存的单子取消不掉，剩下的规格继续点「调整」调完
 */
export function cancelErpInventoryCheckApi(id: any) {
  return request({
    url: '/erp/inventoryCheck/cancel/' + id,
    method: 'put'
  })
}
