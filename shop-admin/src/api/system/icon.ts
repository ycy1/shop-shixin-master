import request from '@/utils/request'

/**
 * 图标库（sys_icon）
 *
 * 系统级资源：后台菜单、商品类目这些挑图标都走这一套接口。
 */

/**
 * 获取图标列表
 */
export function listIconApi(params?: any) {
  return request({
    url: '/sys/icon/list',
    method: 'get',
    params
  })
}

/**
 * 获取启用状态的图标选项（不分页，供图标选择器使用）
 *
 * @param iconCategory 图标分类：1系统图标 2商品分类 3其他；不传就是全部（选择器的三个 tab 各传一档）
 * @param categoryId   所属商品类目，只有商品分类那一档用得上
 */
export function optionsIconApi(iconCategory?: number | string, categoryId?: number | string) {
  return request({
    url: '/sys/icon/options',
    method: 'get',
    params: { iconCategory, categoryId }
  })
}

/**
 * 获取图标详情
 */
export function detailIconApi(id: any) {
  return request({
    url: '/sys/icon/' + id,
    method: 'get'
  })
}

/**
 * 添加图标
 */
export function addIconApi(data: any) {
  return request({
    url: '/sys/icon/add',
    method: 'post',
    data
  })
}

/**
 * 修改图标
 */
export function updateIconApi(data: any) {
  return request({
    url: `/sys/icon/update`,
    method: 'put',
    data
  })
}

/**
 * 删除图标
 */
export function deleteIconApi(ids: number[] | number) {
  return request({
    url: `/sys/icon/delete/` + ids,
    method: 'delete'
  })
}
