import request from '@/utils/request'

/**
 * 获取钱包账户列表（联表带出昵称和手机号；可用余额/冻结余额/累计充值/累计消费/累计提现/赠送余额
 * 读的都是库里存的那几格，正常状态在前、可用余额多的排前面）
 * params: { keyword 可选（昵称/账号/手机号）, userId 可选, status 可选, pageNum, pageSize }
 */
export function listShopWalletApi(params?: any) {
  return request({
    url: '/shop/wallet/list',
    method: 'get',
    params
  })
}

/**
 * 获取某个用户的钱包流水（列表页那颗「流水」弹窗，按人分页、最新一笔在前，只读）
 * params: { userId 必填, bizType 可选（1充值 2消费 3退款 4提现 5奖励 6冻结 7解冻 8管理员调整）, pageNum, pageSize }
 * amount 是带符号的（收入为正、支出为负），balance 记的是这一笔之后的余额
 */
export function listShopWalletRecordApi(params?: any) {
  return request({
    url: '/shop/wallet/record/list',
    method: 'get',
    params
  })
}

/**
 * 余额充值 / 提现（「个人信息 → 钱包」那两颗按钮）。
 * data: { amount 必填（元，>0、最多两位小数）, description 选填（原样写进流水的说明） }
 *
 * 为什么走 /api/comm 而不是本页其它接口的 /shop/wallet/**：这是"两端同一个动作"的统一接口 ——
 * 前台「我的钱包」那两颗点的是同一颗后端方法，开发环境 /api 会被 vite 代理去掉再转给后端，
 * 而后端这个控制器的真实路径就带 /api/comm，所以这里要写成 /api/comm/...，拼完正好落回后端那一条。
 *
 * 这一格表单只有金额和说明：账上那几个数不在参数里（余额只能由充值进、提现出、下单占、退款还自己加减），
 * 也没有 userId —— 服务端第一句把它钉成登录 id，动的是登录者自己这本账。
 * 回的是改完之后的那一行账户，但页面还是按列表那一条重拉（两处形状一致，见上面那两条读接口）。
 */
export function rechargeShopWalletApi(data?: any) {
  return request({
    url: '/api/comm/shop/wallet/recharge',
    method: 'post',
    data
  })
}

/** 提现：从可用余额里减这一笔、累计提现加同一笔，同时记一行「提现」流水；冻结中那一部分提不走 */
export function withdrawShopWalletApi(data?: any) {
  return request({
    url: '/api/comm/shop/wallet/withdraw',
    method: 'post',
    data
  })
}
