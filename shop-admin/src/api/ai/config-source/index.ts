import request from '@/utils/request'

// 获取知识源列表
export function getConfigSourceListApi(params: any) {
  return request({
    url: '/sys/config-source/list',
    method: 'get',
    params
  })
}

// 新增知识源
export function addConfigSourceApi(data: any) {
  return request({
    url: '/sys/config-source/add',
    method: 'post',
    data
  })
}

// 修改知识源
export function updateConfigSourceApi(data: any) {
  return request({
    url: '/sys/config-source/update',
    method: 'put',
    data
  })
}

// 删除知识源
export function deleteConfigSourceApi(id: number) {
  return request({
    url: `/sys/config-source/delete/${id}`,
    method: 'delete'
  })
}

// 测试数据库连接
export function testDbConnectionApi(data: any) {
  return request({
    url: '/sys/config-source/test-connection',
    method: 'post',
    data
  })
}
