import request from '@/utils/request'

/**
 * 站点管理 → 广场消息（后台这一侧只有读、审核、改、删；
 * 内容都是前台用户发的，所以没有新增 —— 发布那条写在 /api/comm/shop/plaza/publish，两端共用一份判据）。
 *
 * 列表那条 SQL 不取正文（mediumtext 的富文本，一屏十条拖着它既没地方显示又把响应撑肥），
 * 预览读的是 content_text；正文只在详情返回里有 —— 所以修改弹窗要先走详情那一趟才拿得到正文。
 * 三个计数（点赞/评论/分享）由后端按明细表现算覆盖，这一页只读不回写。
 * 「已删除的那一些」也在这页上：列表带 is_deleted 一列，详情和修改都读得回软删行。
 */

/**
 * 广场消息列表（待审核的那几条也在这一页上；params.isDeleted=1 只看已删除的那些，不填按未删除）
 */
export function listShopPlazaApi(params?: any) {
  return request({
    url: '/sys/plaza/list',
    method: 'get',
    params
  })
}

/**
 * 单条（带正文）：详情弹窗和审核弹窗共用这一条
 */
export function detailShopPlazaApi(id: any) {
  return request({
    url: '/sys/plaza/' + id,
    method: 'get'
  })
}

/**
 * 审核：1 已发布 / 2 已拒绝。
 * 判成 1 且这条从没上过广场时，后端同一趟盖发布时间并给发布者 10 积分（重复点「通过」不再送分）
 */
export function auditShopPlazaApi(data: { id: any; auditStatus: number; auditRemark?: string }) {
  return request({
    url: '/sys/plaza/audit',
    method: 'put',
    data
  })
}

/**
 * 批量审核：把多选框里那几条一次判成同一档、写同一句备注（列表上方那颗「批量审核」）。
 * 走的是单独一条端点而不是给上面那一条递 ids —— 单条读不到要报错（不然是把结论写到了别的行上），
 * 批量读不到（已被别人删掉）是跳过其中一条，两者的处理正好相反，混在一个端点里说不清。
 * 每条的送分判据和单条一模一样：只有第一次判通过才给 10 分，重复点「通过」不再送。
 */
export function auditBatchShopPlazaApi(data: { ids: any[]; auditStatus: number; auditRemark?: string }) {
  return request({
    url: '/sys/plaza/auditBatch',
    method: 'put',
    data
  })
}

/**
 * 修改：后台动 标题 / 广场消息图 / 正文 / 置顶 / 推荐 / 上下架 这六格（正文一改，服务端跟着重算摘要；
 * 广场消息图按「这一次没传就是清空」落库（多个地址用逗号拼在这一列上），所以弹窗要先回填原来那一串，
 * 别让人一提交就把发布人传的图抹了）。
 * 发布人、关联对象和审核那几格不在这一趟里，改了正文也不会重新打回待审核；已删除的那一条同样改得动。
 */
export function updateShopPlazaApi(data: any) {
  return request({
    url: '/sys/plaza/update',
    method: 'put',
    data
  })
}

/**
 * 删除（批量逻辑删除）
 */
export function deleteShopPlazaApi(ids: number[] | number) {
  return request({
    url: '/sys/plaza/delete/' + ids,
    method: 'delete'
  })
}
