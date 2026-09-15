import request from '@/utils/request'

// 获取可用工具列表
export function getToolListApi() {
  return request({
    url: '/sys/tool/list',
    method: 'get'
  })
}
