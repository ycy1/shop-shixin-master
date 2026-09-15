import request from '@/utils/request'

// 获取智能体列表
export function getAgentListApi(params: any) {
  return request({
    url: '/sys/agent/list',
    method: 'get',
    params
  })
}

// 获取智能体详情
export function getAgentDetailApi(id: number) {
  return request({
    url: `/sys/agent/detail/${id}`,
    method: 'get'
  })
}

// 新增智能体
export function addAgentApi(data: any) {
  return request({
    url: '/sys/agent/add',
    method: 'post',
    data
  })
}

// 修改智能体
export function updateAgentApi(data: any) {
  return request({
    url: '/sys/agent/update',
    method: 'put',
    data
  })
}

// 删除智能体
export function deleteAgentApi(ids: number[]) {
  return request({
    url: `/sys/agent/delete/${ids}`,
    method: 'delete'
  })
}

// 修改智能体状态
export function updateAgentStatusApi(id: number, status: number) {
  return request({
    url: '/sys/agent/updateStatus',
    method: 'put',
    params: { id, status }
  })
}

// 刷新智能体知识库
export function refreshAgentKnowledgeApi(id: number) {
  return request({
    url: `/sys/agent/refresh-knowledge/${id}`,
    method: 'put'
  })
}
