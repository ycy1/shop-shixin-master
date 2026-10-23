import request from '@/utils/request'

/**
 * 获取培训列表
 */
export function listShopTrainingApi(params?: any) {
  return request({
    url: '/shop/training/list',
    method: 'get',
    params
  })
}

/**
 * 获取培训详情
 */
export function detailShopTrainingApi(id: any) {
  return request({
    url: '/shop/training/' + id,
    method: 'get'
  })
}

/**
 * 按培训取参训人员列表（列表页那颗「培训人员」弹窗用，一次取全、不翻页）
 */
export function trainingMembersApi(trainingId: any) {
  return request({
    url: '/shop/training/members/' + trainingId,
    method: 'get'
  })
}

/**
 * 新增培训
 */
export function addShopTrainingApi(data: any) {
  return request({
    url: '/shop/training/add',
    method: 'post',
    data
  })
}

/**
 * 修改培训（状态也在这一格改）
 */
export function updateShopTrainingApi(data: any) {
  return request({
    url: '/shop/training/update',
    method: 'put',
    data
  })
}

/**
 * 删除培训
 */
export function deleteShopTrainingApi(ids: number[] | number) {
  return request({
    url: '/shop/training/delete/' + ids,
    method: 'delete'
  })
}

/**
 * 下面三颗走 /api/comm 而不是本页其它接口的 /shop/training/**：报名和签到是"两端同一个动作"的统一接口
 * （门店在自己那一端报、在这后台这一页点也是同一条，ShopStoreCommonController），两边各写一套状态就会分叉。
 * 路径上多出来的那层 /api 是前缀本身：request 的 baseURL（开发环境 /api）会被 vite 代理去掉再转给后端，
 * 而后端这个控制器的真实路径就带 /api/comm，所以这里要写成 /api/comm/...，拼完正好落回后端那一条。
 * 这一路不过 Sa-Token 拦截器，所以没有权限码可挂：资格判的是登录态 + 名下有没有门店（见 signStoresShopTrainingApi）。
 */

/**
 * 可报名门店：当前账号名下能拿来报名的那几家店（门店的绑定账号或店长都算）。
 *
 * 列表页用它决定每一行上那颗「报名」出不出：这一场的所属门店在这份里，才轮得到当前账号报
 * （一场培训归一家门店，2026-09-24 起「总部统一培训」那一档取消了）；空列表就是这一页和当前账号无关
 * （平台/ERP/消费者那些档一家店都不挂），点了也是被后端拒，提前收掉按钮少一次报错。
 * 只拿这一份判"能不能报**这一场**"还不够，还要比行上的 storeId（见那个页面的 canSign）。
 */
export function signStoresShopTrainingApi() {
  return request({
    url: '/api/comm/store/training/sign/stores',
    method: 'get'
  })
}

/**
 * 报名：给当前账号报上一场培训（后端写一行 1 已报名，姓名/电话按登录账号现取快照）。
 *
 * data：{ trainingId }，**没有门店那一格** —— 以哪家门店报名跟着这一场培训的所属门店走，选不了第二家。
 * 后端三道闸：培训得是「报名中」、在报名窗口内、名额没满，再加一道"这一场的门店在你名下"
 * —— 报错文案都是人话，直接透出即可。
 * 返回里带写完现数的 signedCount，主列表那一列「报名」要刷一次。
 */
export function signShopTrainingApi(data: any) {
  return request({
    url: '/api/comm/store/training/sign',
    method: 'post',
    data
  })
}

/**
 * 签到：把当前账号在这一场培训的报名记录翻成 1 已签到，记签到时间。
 *
 * 只传培训 id，**没有 user_id** —— 能签的只有自己报过的那一行，参数能点名别人就等于替别人签到，
 * 所以那颗按钮只出现在"自己已经报上、还没签"的那几行上（列表按登录 id 联报名记录带回
 * selfSignStatus / selfAttendStatus，判据见那个页面的 canAttend）。
 * 后端不看培训是不是「进行中」（状态和开始时间都是后台手动填的，人到场是当场的事实），
 * 只拒已取消那一场和没报名的人。
 */
export function attendShopTrainingApi(trainingId: any) {
  return request({
    url: '/api/comm/store/training/attend/' + trainingId,
    method: 'post'
  })
}

