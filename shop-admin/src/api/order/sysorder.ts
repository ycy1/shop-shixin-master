import request from '@/utils/request'

/**
 * 获取订单主表列表
 */
export function listSysOrderApi(params?: any) {
    return request({
        url: '/sys/sysOrder/list',
        method: 'get',
        params
    })
}

/**
 * 获取订单主表详情
 */
export function detailSysOrderApi(id: any) {
    return request({
        url: '/sys/sysOrder/' + id,
        method: 'get'
    })
}

/**
 * 添加订单主表
 */
export function addSysOrderApi(data: any) {
    return request({
        url: '/sys/sysOrder/add',
        method: 'post',
        data
    })
}

/**
 * 修改订单主表
 */
export function updateSysOrderApi(data: any) {
    return request({
        url: `/sys/sysOrder/update`,
        method: 'put',
        data
    })
}


/**
 * 删除订单主表
 */
export function deleteSysOrderApi(ids: number[] | number) {
    return request({
        url: `/sys/sysOrder/delete/` + ids,
        method: 'delete'
    })
}


