import request from '@/utils/request'

/**
 * 获取库存列表（可按商品/仓库筛，lowStockOnly=true 只看低于安全库存的行）
 */
export function listErpInventoryApi(params?: any) {
  return request({
    url: '/erp/inventory/list',
    method: 'get',
    params
  })
}

/**
 * 获取库存详情
 */
export function detailErpInventoryApi(id: any) {
  return request({
    url: '/erp/inventory/' + id,
    method: 'get'
  })
}

/**
 * 修改库存行：服务端只认成本价和三个预警参数，库存数量三列传了也不改
 * （没有新增和删除接口 —— 库存行由入库动作自动建，删一行等于把已入库的数量凭空抹掉）。
 * version 要原样带回读到的那一版：它是乐观锁凭据，中途有人入过货这次更新会被挡下来
 */
export function updateErpInventoryApi(data: any) {
  return request({
    url: '/erp/inventory/update',
    method: 'put',
    data
  })
}

/**
 * 获取库存流水（一行 (商品, 仓库) 账的进出明细，只读）
 *
 * 没有新增/修改/删除接口：流水只能由一次真实的库存变动带出来，这一版只有「入库」会写。
 */
export function listErpInventoryLogApi(params?: any) {
  return request({
    url: '/erp/inventory/log/list',
    method: 'get',
    params
  })
}
