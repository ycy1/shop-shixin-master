import request from '@/utils/request'

/**
 * 获取退款表列表
 */
export function listSysRefundApi(params?: any) {
    return request({
        url: '/sys/sysRefund/list',
        method: 'get',
        params
    })
}

/**
 * 获取退款表详情
 */
export function detailSysRefundApi(id: any) {
    return request({
        url: '/sys/sysRefund/' + id,
        method: 'get'
    })
}

/**
 * 添加退款表
 */
export function addSysRefundApi(data: any) {
    return request({
        url: '/sys/sysRefund/add',
        method: 'post',
        data
    })
}

/**
 * 修改退款表
 */
export function updateSysRefundApi(data: any) {
    return request({
        url: `/sys/sysRefund/update`,
        method: 'put',
        data
    })
}


/**
 * 审核退款：status 直接传审核完要落的那一档 —— 3-退款中（通过）/ 2-审核拒绝（拒绝）
 */
export function auditSysRefundApi(data: any) {
    return request({
        url: '/sys/sysRefund/audit',
        method: 'put',
        data
    })
}


/**
 * 删除退款表
 */
export function deleteSysRefundApi(ids: number[] | number) {
    return request({
        url: `/sys/sysRefund/delete/` + ids,
        method: 'delete'
    })
}


