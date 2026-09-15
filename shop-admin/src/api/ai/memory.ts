import request from '@/utils/request'

// 获取消息记录列表
export function getMemoryListApi(params: any) {
  return request({
    url: '/ai/memory/list',
    method: 'get',
    params
  })
}

// 删除消息记录
export function deleteMemoryApi(id: number) {
  return request({
    url: `/ai/memory/delete/${id}`,
    method: 'delete'
  })
}
