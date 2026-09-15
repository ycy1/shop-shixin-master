import request from '@/utils/request'

// 获取会话列表
export function getConversationListApi(params: any) {
  return request({
    url: '/ai/conversation/list',
    method: 'get',
    params
  })
}

// 获取会话详情
export function getConversationDetailApi(id: number) {
  return request({
    url: `/ai/conversation/detail/${id}`,
    method: 'get'
  })
}

// 新建会话
export function createConversationApi(data: any) {
  return request({
    url: '/ai/conversation/create',
    method: 'post',
    data
  })
}

// 修改会话
export function updateConversationApi(data: any) {
  return request({
    url: '/ai/conversation/update',
    method: 'put',
    data
  })
}

// 删除会话
export function deleteConversationApi(ids: number[]) {
  return request({
    url: `/ai/conversation/delete/${ids}`,
    method: 'delete'
  })
}
