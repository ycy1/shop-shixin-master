import request from '@/utils/request'

// 获取缓存信息
export function getCacheInfoApi() {
  return request({
    url: '/monitor/cache/info',
    method: 'get'
  })
}

// 获取缓存键名列表
export function getCacheKeyListApi(params?: any) {
  return request({
    url: '/monitor/cache/keys',
    method: 'get',
    params
  })
}

// 获取缓存内存信息
export function getCacheMemoryApi() {
  return request({
    url: '/monitor/cache/memory',
    method: 'get'
  })
}

// 清空缓存
export function clearCacheApi() {
  return request({
    url: '/monitor/cache',
    method: 'delete'
  })
}

// 看某个键存的内容（给回来的是库里那串原文，不是对象）
export function getCacheValueApi(key: string) {
  return request({
    url: '/monitor/cache/value',
    method: 'get',
    params: { key }
  })
}

// 改某个键的内容（后端只放行 string 类型的键）
export function updateCacheValueApi(data: { key: string; value: string }) {
  return request({
    url: '/monitor/cache/value',
    method: 'put',
    data
  })
}

// 删某个键
export function deleteCacheKeyApi(key: string) {
  return request({
    url: '/monitor/cache/key',
    method: 'delete',
    params: { key }
  })
} 