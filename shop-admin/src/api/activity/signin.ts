import request from '@/utils/request'

/**
 * 获取签到列表（一个用户一行：累计签到天数、其中补签次数、当前连续天数、累计获得积分、最近签到日期）
 * params: { keyword 可选（昵称/账号/手机号）, userId 可选, pageNum, pageSize }
 * 那几个天数/积分数是服务端 GROUP BY user_id 现算的，不是库里存的某一格
 */
export function listShopSigninApi(params?: any) {
  return request({
    url: '/activity/signin/list',
    method: 'get',
    params
  })
}

/**
 * 获取某个用户某个月的签到记录（公共组件 SigninCalendar 那一份日历，一次全带不分页，只读）
 * params: { userId 必填, month 'yyyy-MM'（留空按当月）}
 * 只回这个月签过的那几天，没签的日子由日历画成空格子（过去的那些才给补签）
 * 消费者账号登录时服务端把 userId 钉成登录 id，别人账号才认参数里点的名
 */
export function listShopSigninRecordApi(params?: any) {
  return request({
    url: '/activity/signin/record/list',
    method: 'get',
    params
  })
}

/**
 * 后台补签（日历里点某一天）：只收 { userId, signDate: 'yyyy-MM-dd' }
 * 那一天必须早于今天、而且这个人还没签过；签到类型、给的积分、连续天数都由服务端算，表单填不了
 * 返回补下来的那一行（含 points / currentContinuous / signType），拿它报"已补签，获得 X 分"
 */
export function makeupShopSigninApi(data: any) {
  return request({
    url: '/activity/signin/makeup',
    method: 'post',
    data
  })
}

/**
 * 本人补签（日历里点某个没签过的过去日子）：只收 { signDate: 'yyyy-MM-dd' }
 *
 * 和上面那颗后台补签是同一条判据的两端：这一条不压权限，但 userId 由服务端钉成登录 id，
 * 而且只补得到最近 7 天（组件 SigninCalendar 里 SELF_MAKEUP_DAYS 把够不着的格子画成不能点，服务端还会再挡一次）；
 * 要点名给别人补走 /activity/signin/makeup（压 activity:signin:makeup，不限日子）
 *
 * 为什么走 /api/comm：这是"两端同一个动作"的统一接口，开发环境 /api 会被 vite 代理去掉再转给后端，
 * 而后端这个控制器的真实路径就带 /api/comm，所以这里要写成 /api/comm/...，拼完正好落回后端那一条。
 */
export function makeupSelfShopSigninApi(data: any) {
  return request({
    url: '/api/comm/activity/signin/makeup',
    method: 'post',
    data
  })
}

/**
 * 本人签到（「个人信息」页日历上面那一颗）：签今天这一格，没有参数
 *
 * 为什么走 /api/comm 而不是本页其它接口的 /activity/signin/**：这是"两端同一个动作"的统一接口 ——
 * 小程序侧签的也是这一条，给的分、记的流水一模一样。（开发环境 /api 会被 vite 代理去掉再转给后端，
 * 而后端这个控制器的真实路径就带 /api/comm，所以这里要写成 /api/comm/...，拼完正好落回后端那一条。）
 * 返回今天这一行（含 points / currentContinuous / signType），今天已签过会被服务端拒
 */
export function signShopSigninApi() {
  return request({
    url: '/api/comm/activity/signin',
    method: 'post'
  })
}
