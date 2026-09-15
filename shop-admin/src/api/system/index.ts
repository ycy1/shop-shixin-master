import request from '@/utils/request'

/**
 * 获取dashboard数据
 */
export function getDashboardDataApi() {
  return request({
    url: '/sys/dashboard',
    method: 'get'
  })
}

