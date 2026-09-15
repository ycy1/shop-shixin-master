import request from '@/utils/request'

// 发送消息（非SSE方式，用于历史记录等）
export function sendMessageApi(data: any) {
  return request({
    url: '/ai/send',
    method: 'post',
    data
  })
}

// 获取会话历史消息
export function getChatHistoryApi(conversationId: number) {
  return request({
    url: `/ai/history/${conversationId}`,
    method: 'get'
  })
}
