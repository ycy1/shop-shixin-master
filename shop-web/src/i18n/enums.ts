/**
 * 枚举档的称呼：库里存的是数字，页面上要说成人话，四档各一份。
 *
 * 一律用**数组**而不是 { 1: '…' } 那种对象：下标就是库里的码值，`d.enum.orderStatus[row.status]` 直接取，
 * 不用 `String(code)` 绕一层（strictest 档下标取不到值会说 undefined，调用方都带 `||` 兜底，正好）。
 * 码值不从 0 起的那一档，头一格留空串占位，好让下标和码值始终对齐；铺下拉候选时按空串跳过。
 *
 * 顺序照着原来那几张 Record 抄，没改判据；注释里的码值表是库里那一列的口径。
 */

export const zh = {
  enum: {
    /** sys_order.status：0-待支付 1-已支付 2-已完成 3-已取消 4-已退款 5-已关闭 */
    orderStatus: ['待支付', '已支付', '已完成', '已取消', '已退款', '已关闭'],
    /** sys_order.order_type 的标签不在这里了：那一档的定义后台改过一次，写死四份就对不上库里的值，
     *  改成读后端字典（src/lib/dict.ts，GET /api/web/dict/order_type） */
    /** sys_order.pay_type：1 微信 2 支付宝 3 银行卡 4 余额（没付过的单这一格是 NULL，页面按「未支付」说） */
    payType: ['', '微信', '支付宝', '银行卡', '余额'],
    unPaid: '未支付',
    /** shop_order_item.ship_status：0 待发货 1 已发货 2 已退货 3 已出库 4 部分退货 */
    shipStatus: ['待发货', '已发货', '已退货', '已出库', '部分退货'],
    /** shop_activity_signin.sign_type：1 正常签到 2 补签 */
    signType: ['', '正常签到', '补签'],
    signedIn: '已签到',
    /** shop_activity_coupon.coupon_type：1 满减 2 折扣 3 免邮 4 代金 */
    couponType: ['', '满减券', '折扣券', '免邮券', '代金券'],
    coupon: '优惠券',
    /** shop_activity_coupon_user.status：0 未使用 1 已使用 2 已过期 3 已作废 */
    couponUserStatus: ['未使用', '已使用', '已过期', '已作废'],
    /** shop_activity_coupon_user.get_way：1 主动领取 2 系统发放 3 活动赠送 4 新人礼 5 积分兑换 6 徽章发放 */
    getWay: ['', '主动领取', '系统发放', '活动赠送', '新人礼', '积分兑换', '徽章发放'],
    /** shop_activity_points_log.biz_type：1 签到 … 10 管理员调整 */
    pointsBizType: ['', '签到', '购物', '评价', '分享', '邀请', '注册', '兑换', '抵扣', '过期', '管理员调整'],
    /** sys_user.sex：0 保密 1 男 2 女（这三档没有字典，后台用户管理那页也是写死的） */
    sex: ['保密', '男', '女'],
    /** shop_activity_badge_user.source：1 系统发放 2 活动获得 3 消费达标 4 手动发放 5 门店发放 */
    badgeSource: ['', '系统发放', '活动获得', '消费达标', '手动发放', '门店发放'],
    /** shop_activity_badge.badge_type：1 门店 2 活动 3 签到 4 消费 */
    badgeType: ['', '门店', '活动', '签到', '消费'],
    badge: '徽章',
    /** shop_activity_badge_user.status：0 失效 1 有效（两档；库里没有「已过期」那一档，第三格是 Profile.vue 拿 endTime 现比出过期时念的文案，和 status 无关） */
    badgeStatus: ['失效', '有效', '已过期'],
    /** shop_wallet_log.biz_type：1 充值 2 消费 3 退款 4 提现 5 奖励 6 冻结 7 解冻 8 管理员调整 */
    walletBizType: ['', '充值', '消费', '退款', '提现', '奖励', '冻结', '解冻', '管理员调整'],
    /** shop_wallet.status：0 冻结 1 正常 */
    walletStatus: ['冻结', '正常'],
    /** sys_user.account_type：0 平台 1 门店 2 送货员 3 消费者 4 供应商 5 ERP 操作员 */
    accountType: ['平台账号', '门店账号', '送货员账号', '消费者账号', '供应商账号', 'ERP 操作员'],
    /** shop_consumer_favorite.target_type：1 商品 2 门店 3 品牌 4 公园 */
    favoriteTarget: ['', '商品', '门店', '品牌', '公园'],
    /** shop_plaza_message.relation_type：0 无关联 1 商品 2 门店 3 品牌 4 活动（这一档下标从 0 起，第一格不是占位） */
    plazaRelation: ['无关联', '商品', '门店', '品牌', '活动'],
    /** shop_plaza_message.audit_status：0 待审核 1 已发布 2 已拒绝（前台只有作者本人看得到自己那两条） */
    plazaAuditStatus: ['待审核', '已发布', '已拒绝'],
    other: '其他',
    /** shop_store.store_type：1 直营 2 加盟 3 合作 */
    storeType: ['', '直营', '加盟', '合作'],
    /** shop_store.business_status：0 休息中 1 营业中 2 已打烊 */
    businessStatus: ['休息中', '营业中', '已打烊'],
    /** 认不出的码值（后台新加档而前台没跟上）：原来各页自己拼「状态 7」，现在统一这一句 */
    unknown: '未知（{n}）',
    /** 商品那类通用称呼，收藏/足迹的兜底句要用 */
    product: '商品',
    store: '门店',
    brand: '品牌',
  },
};

export const en: typeof zh = {
  enum: {
    orderStatus: ['Awaiting payment', 'Paid', 'Completed', 'Cancelled', 'Refunded', 'Closed'],
    unPaid: 'Not paid',
    payType: ['', 'WeChat', 'Alipay', 'Bank card', 'Balance'],
    shipStatus: ['Not shipped', 'Shipped', 'Returned', 'Out of stock', 'Partly returned'],
    signType: ['', 'Check-in', 'Make-up'],
    signedIn: 'Checked in',
    couponType: ['', 'Amount off', 'Percentage off', 'Free shipping', 'Voucher'],
    coupon: 'Coupon',
    couponUserStatus: ['Unused', 'Used', 'Expired', 'Void'],
    getWay: ['', 'Claimed', 'Issued', 'Campaign gift', 'Welcome gift', 'Points exchange', 'Badge reward'],
    pointsBizType: ['', 'Check-in', 'Purchase', 'Review', 'Share', 'Invite', 'Sign-up', 'Exchange', 'Redemption', 'Expired', 'Manual adjustment'],
    sex: ['Not specified', 'Male', 'Female'],
    badgeSource: ['', 'Issued', 'Campaign', 'Spending goal', 'Manual', 'Store'],
    badgeType: ['', 'Store', 'Campaign', 'Check-in', 'Spending'],
    badge: 'Badge',
    badgeStatus: ['Inactive', 'Active', 'Expired'],
    walletBizType: ['', 'Top-up', 'Payment', 'Refund', 'Withdrawal', 'Reward', 'Frozen', 'Unfrozen', 'Manual adjustment'],
    walletStatus: ['Frozen', 'Normal'],
    accountType: ['Platform', 'Store', 'Rider', 'Consumer', 'Supplier', 'ERP operator'],
    favoriteTarget: ['', 'Product', 'Store', 'Brand', 'Park'],
    plazaRelation: ['None', 'Product', 'Store', 'Brand', 'Campaign'],
    plazaAuditStatus: ['Pending review', 'Published', 'Rejected'],
    other: 'Other',
    storeType: ['', 'Direct', 'Franchise', 'Partner'],
    businessStatus: ['Closed for now', 'Open', 'Closed today'],
    unknown: 'Unknown ({n})',
    product: 'Product',
    store: 'Store',
    brand: 'Brand',
  },
};

export const ja: typeof zh = {
  enum: {
    orderStatus: ['支払い待ち', '支払い済み', '完了', 'キャンセル済み', '返金済み', '終了'],
    unPaid: '未払い',
    payType: ['', 'WeChat Pay', 'Alipay', '銀行カード', '残高'],
    shipStatus: ['発送待ち', '発送済み', '返品済み', '出庫済み', '一部返品'],
    signType: ['', 'チェックイン', '事後チェックイン'],
    signedIn: 'チェックイン済み',
    couponType: ['', '条件付き割引クーポン', '割引率クーポン', '送料無料クーポン', '金額割引クーポン'],
    coupon: 'クーポン',
    couponUserStatus: ['未使用', '使用済み', '期限切れ', '無効'],
    getWay: ['', '自分で受け取る', 'システム発行', 'キャンペーン特典', '新規特典', 'ポイント交換', 'バッジ発行'],
    pointsBizType: ['', 'チェックイン', 'ショッピング', 'レビュー', '共有', '招待', '会員登録', '交換', '充当', '期限切れ', '管理者調整'],
    sex: ['未設定', '男性', '女性'],
    badgeSource: ['', 'システム発行', 'キャンペーン獲得', '購入達成', '手動発行', '店舗発行'],
    badgeType: ['', '店舗', 'キャンペーン', 'チェックイン', '購入'],
    badge: 'バッジ',
    badgeStatus: ['失効', '有効', '期限切れ'],
    walletBizType: ['', 'チャージ', '消費', '返金', '出金', '報酬', '凍結', '凍結解除', '管理者調整'],
    walletStatus: ['凍結', '正常'],
    accountType: ['プラットフォームアカウント', '店舗アカウント', '配送担当', '会員', 'サプライヤー', 'ERP 担当'],
    favoriteTarget: ['', '商品', '店舗', 'ブランド', '公園'],
    plazaRelation: ['関連なし', '商品', '店舗', 'ブランド', 'キャンペーン'],
    plazaAuditStatus: ['審査中', '公開済み', '差し戻し'],
    other: 'その他',
    storeType: ['', '直営', 'フランチャイズ', '提携'],
    businessStatus: ['準備中', '営業中', '閉店中'],
    unknown: '不明（{n}）',
    product: '商品',
    store: '店舗',
    brand: 'ブランド',
  },
};

export const ko: typeof zh = {
  enum: {
    orderStatus: ['결제 대기', '결제 완료', '완료', '취소됨', '환불됨', '종료'],
    unPaid: '미결제',
    payType: ['', '위챗페이', '알리페이', '은행 카드', '잔액'],
    shipStatus: ['배송 대기', '배송됨', '반품됨', '출고됨', '부분 반품'],
    signType: ['', '출석', '미출석 체크인'],
    signedIn: '출석 완료',
    couponType: ['', '조건부 할인 쿠폰', '할인율 쿠폰', '배송비 무료 쿠폰', '정액 할인 쿠폰'],
    coupon: '쿠폰',
    couponUserStatus: ['미사용', '사용됨', '만료됨', '폐기됨'],
    getWay: ['', '직접 수령', '시스템 발급', '캠페인 지급', '신규 회원 선물', '포인트 교환', '배지 발급'],
    pointsBizType: ['', '출석', '쇼핑', '리뷰', '공유', '초대', '가입', '교환', '사용', '만료', '관리자 조정'],
    sex: ['미설정', '남성', '여성'],
    badgeSource: ['', '시스템 발급', '캠페인 획득', '구매 달성', '수동 발급', '매장 발급'],
    badgeType: ['', '매장', '캠페인', '출석', '구매'],
    badge: '배지',
    badgeStatus: ['무효', '유효', '기간 만료'],
    walletBizType: ['', '충전', '소비', '환불', '출금', '보상', '동결', '동결 해제', '관리자 조정'],
    walletStatus: ['동결', '정상'],
    accountType: ['플랫폼 계정', '매장 계정', '배송 담당자', '소비자', '공급업체', 'ERP 담당자'],
    favoriteTarget: ['', '상품', '매장', '브랜드', '공원'],
    plazaRelation: ['없음', '상품', '매장', '브랜드', '캠페인'],
    plazaAuditStatus: ['심사 대기', '게시됨', '반려됨'],
    other: '기타',
    storeType: ['', '직영', '가맹', '제휴'],
    businessStatus: ['준비 중', '영업 중', '마감'],
    unknown: '알 수 없음({n})',
    product: '상품',
    store: '매장',
    brand: '브랜드',
  },
};
