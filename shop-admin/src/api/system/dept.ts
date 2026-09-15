import request from '@/utils/request'

/**
 * 获取部门树
 */
export function getDeptTreeApi(params?: any) {
  return request({
    url: '/sys/dept',
    method: 'get',
    params
  })
}

/**
 * 新增部门
 */
export function createDeptApi(data: any) {
  return request({
    url: '/sys/dept',
    method: 'post',
    data
  })
}

/**
 * 修改部门
 */
export function updateDeptApi(data: any) {
  return request({
    url: '/sys/dept',
    method: 'put',
    data
  })
}

/**
 * 删除部门
 */
export function deleteDeptApi(ids: number[] | number) {
  return request({
    url: `/sys/dept/delete/${ids}`,
    method: 'delete'
  })
}

/**
 * 获取部门下的人员列表
 */
export function getDeptUsersApi(deptId: number, params?: any) {
  return request({
    url: `/sys/dept/users/${deptId}`,
    method: 'get',
    params
  })
}

/**
 * 添加部门人员
 */
export function addDeptUsersApi(deptId: number, userIds: number[]) {
  return request({
    url: '/sys/dept/users',
    method: 'post',
    data: {
      deptId,
      userIds
    }
  })
}
