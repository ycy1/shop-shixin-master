import request from '@/utils/request'

/**
 * 获取菜单列表
 * @param params 筛选条件，目前只有 title（菜单名称模糊匹配，命中的连下级一起返回）。不传就是完整树
 */
export function getMenuListApi(params?: any) {
  return request({
    url: '/sys/menu/tree',
    method: 'get',
    params
  })
}

/**
 * 获取菜单详情
 */
export function getMenuDetailApi(id: string) {
  return request({
    url: `/sys/menu/${id}`,
    method: 'get'
  })
}

/**
 * 新增菜单
 */
export function createMenuApi(data: any) {
  return request({
    url: '/sys/menu',
    method: 'post',
    data
  })
}

/**
 * 修改菜单
 */
export function updateMenuApi(data: any) {
  return request({
    url: `/sys/menu/`,
    method: 'put',
    data
  })
}

/**
 * 删除菜单
 */
export function deleteMenuApi(id: string) {
  return request({
    url: `/sys/menu/${id}`,
    method: 'delete'
  })
}


export function listRoutes() {
  return request({
    url: '/sys/menu',
    method: 'post',
  })
}