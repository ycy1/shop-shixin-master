/**
 * 个人中心那五颗弹窗那一档文案：收货地址、订单详情、改密码、签到日历、充值/提现。
 * （views/user/AddressForm.vue、views/user/OrderDetail.vue、views/user/PasswordForm.vue、
 *  views/user/SigninCalendar.vue、views/user/WalletChangeForm.vue）
 *
 * 形状标准是中文这份，其余三档各自钉成它的类型（`en/ja/ko: typeof zh`）⇒ 少一格 vue-tsc 当场报错。四档并排放，改一句就在下面几行内。
 * 全站共用的称呼（页头、通用按钮、枚举档位）在 base.ts / enums.ts，这一档只收本区自己的话。
 */

export const zh = {
  /** views/user/AddressForm.vue：新增/修改收货地址（名字带 Form：地址列表那一块在 trade.ts 的 address） */
  addressForm: {
    addTitle: '添加收货地址',
    editTitle: '修改收货地址',
    /** 新增那一条只说服务端的两件事：只挂自己名下、第一条自动默认 */
    addTip:
      '这里加的地址只挂在你自己账号名下。第一条会自动设为默认；勾了「设为默认」，原来那条默认会被取消（这两条都由服务端保证）。',
    /** 修改是整条覆盖式提交，不提醒一句用户不会知道标签/坐标被抹了 */
    editTip:
      '这里改的只有你自己名下这一条。这一趟是整条覆盖：标签、经纬度这些空着送过去就是清成空，所以改之前那几格已经填的都给你回填了。',
    receiverName: '收货人姓名',
    receiverPhone: '收货人电话',
    province: '省',
    city: '市',
    district: '区/县',
    /** 三级下拉那格空值的提示，三处共用 */
    pleaseSelect: '请选择',
    detail: '详细地址',
    detailPlaceholder: '街道、门牌号（地图选点会自动带过来，可以再改）',
    tag: '标签',
    /** 家 / 公司 / 学校 这三个值是入库的字典值（lib/profile 的 ADDRESS_TAGS），这里只是把它们念一遍 */
    tagPlaceholder: '家 / 公司 / 学校',
    setDefault: '设为默认地址',
    longitude: '经度',
    latitude: '纬度',
    saving: '保存中…',
  },

  /** views/user/OrderDetail.vue：订单详情 */
  order: {
    title: '订单详情',
    orderNo: '订单号',
    status: '订单状态',
    orderType: '订单类型',
    created: '下单时间',
    itemsColumn: '商品行数',
    /** 带数的整句：量和量词都在这颗里，英文语序不用跟着中文排 */
    itemsSummary: '{lines} 行 · {qty} 件',
    store: '所属门店',
    totalAmount: '订单金额',
    deliveryFee: '配送费',
    discountAmount: '优惠金额',
    payAmount: '实付金额',
    payType: '支付方式',
    payTime: '支付时间',
    deliveryNo: '配送单号',
    remark: '买家留言',
    itemsTitle: '商品明细',
    /** 一整盒在库里是盒内每款一行，页面上收成一张卡：卡头那一句（{name} 是盒名，{n} 是款数，{qty} 是件数） */
    boxGroup: '一整盒 · {name}（{n} 款 / 共 {qty} 件）',
    /** 商品被下架/删掉了，行还在单上 */
    productGone: '（商品已经不在了）',
    returnedItems: '已退 {n} 件',
    noItems: '这一单没有商品行',
    footnote: '下单于 {day}，要改单或退货请联系商家。',
    payNow: '立即支付',
  },

  /** views/user/PasswordForm.vue：修改密码 */
  password: {
    title: '修改密码',
    oldPassword: '旧密码',
    /** 最短位数由调用方填进来（和 canSubmit 那一颗判据同一个数） */
    newPassword: '新密码 *（至少 {n} 位）',
    confirmPassword: '确认新密码',
    tooShort: '新密码至少 {n} 位',
    notSame: '两次填的不一样',
    /** 旧密码由服务端比对、这一次会话还登着 —— 两句都是这一颗的形状 */
    tip: '旧密码要由服务端比对，填错了会当场退回「旧密码错误」。改完这一次还登着，不用重新登录，下一次登录才用新密码。',
    submitting: '提交中…',
  },

  /** views/user/SigninCalendar.vue：签到日历 */
  signin: {
    title: '签到记录',
    /** 日历表头：周一开头，和 cells 那颗 leading 的算法同一套 */
    weekDays: ['一', '二', '三', '四', '五', '六', '日'],
    summary: '{month} 签到 {days} 天 / 其中补签 {makeup} 天 / 本月获得 {points} 分 / 最近连续 {continuous} 天',
    signInToday: '今天签到',
    signedToday: '今天已签到',
    signing: '签到中…',
    makeup: '补签',
    makingUp: '补签中…',
    /** 格子上那一行分的回显 */
    dayPoints: '+{n} 分',
    prevMonth: '上一月',
    nextMonth: '下一月',
    backToThisMonth: '回到本月',
    /** 窗口天数由调用方填进来（SELF_MAKEUP_DAYS，和后端同一颗数） */
    makeupTip: '点最近 {n} 天内没签的过去日子即可补签，补签给的分和正常签到一样。',
    successToday: '签到成功：连续第 {n} 天，获得 {points} 分',
    successMakeup: '已补签 {day}，获得 {points} 分',
  },

  /** views/user/WalletChangeForm.vue：充值/提现共用那一颗 */
  wallet: {
    rechargeTitle: '余额充值',
    withdrawTitle: '余额提现',
    amount: '金额（元）',
    amountPlaceholder: '请输入金额，最多两位小数',
    tooPrecise: '金额最多两位小数，请改成两位以内的数。',
    tooMuch: '能提的只有 {usable}，这一笔要提 {picked}（下单占着的那一部分在冻结中，提不走）。',
    rechargeHint: '充进来的钱能用来下单支付，退了货也会退回这里',
    withdrawHint: '当前可用余额 {usable}',
    description: '说明',
    rechargeDescriptionPlaceholder: '例如：活动奖励入账（可以留空）',
    withdrawDescriptionPlaceholder: '例如：提现到银行卡（可以留空）',
    rechargeTip:
      '这一笔加进可用余额，同时记一行「充值」流水；第一次充值时那一行钱包账户也是这一刻建出来的。' +
      '本期充值不走真收款的渠道（微信那条充值单的链还没接），所以动的只是账上的数。',
    withdrawTip:
      '提现是从可用余额里减掉这一笔，同时记一行「提现」流水。下单没付的单子占着的那一部分在冻结中，那一部分提不走；' +
      '账户被冻住时也提不走。本期这一步同样不出真钱，钱是真给出去那一步走线下。',
    submitting: '提交中…',
    confirmRecharge: '确认充值',
    confirmWithdraw: '确认提现',
  },
};

export const en: typeof zh = {
  addressForm: {
    addTitle: 'Add a delivery address',
    editTitle: 'Edit this address',
    addTip:
      'Addresses added here are only on your own account. The first one becomes the default automatically; tick "Set as default" and the previous default is dropped (the server handles both).',
    editTip:
      'You are only editing your own address. Saving overwrites the whole record, so a blank tag or blank coordinates are stored as empty — everything already set has been filled in for you.',
    receiverName: 'Receiver name',
    receiverPhone: 'Phone number',
    province: 'Province',
    city: 'City',
    district: 'District',
    pleaseSelect: 'Select…',
    detail: 'Full address',
    detailPlaceholder: 'Street, house number (picked from the map, still editable)',
    tag: 'Tag',
    tagPlaceholder: 'Home / Office / School',
    setDefault: 'Set as default address',
    longitude: 'Longitude',
    latitude: 'Latitude',
    saving: 'Saving…',
  },

  order: {
    title: 'Order details',
    orderNo: 'Order no.',
    status: 'Status',
    orderType: 'Order type',
    created: 'Placed',
    itemsColumn: 'Items',
    itemsSummary: '{lines} lines · {qty} units',
    store: 'Store',
    totalAmount: 'Order amount',
    deliveryFee: 'Delivery',
    discountAmount: 'Discount',
    payAmount: 'Paid',
    payType: 'Payment method',
    payTime: 'Paid at',
    deliveryNo: 'Delivery no.',
    remark: 'Message',
    itemsTitle: 'Items in this order',
    boxGroup: 'Whole box · {name} ({n} styles / {qty} items)',
    productGone: 'Product no longer available',
    returnedItems: '{n} returned',
    noItems: 'This order has no items',
    footnote: 'Placed on {day}. Contact the store to change or return this order.',
    payNow: 'Pay now',
  },

  password: {
    title: 'Change password',
    oldPassword: 'Current password',
    newPassword: 'New password * (at least {n} characters)',
    confirmPassword: 'Confirm new password',
    tooShort: 'Use at least {n} characters',
    notSame: 'The two passwords do not match',
    tip: 'Your current password is verified by the server, so a wrong one is rejected on the spot. You stay signed in now — the new password is used the next time you sign in.',
    submitting: 'Submitting…',
  },

  signin: {
    title: 'Check-in history',
    weekDays: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
    summary: '{month}: {days} check-ins ({makeup} made up) · {points} points this month · {continuous} days in a row',
    signInToday: 'Check in today',
    signedToday: 'Checked in today',
    signing: 'Checking in…',
    makeup: 'Make up',
    makingUp: 'Making up…',
    dayPoints: '+{n} pts',
    prevMonth: 'Previous month',
    nextMonth: 'Next month',
    backToThisMonth: 'Back to this month',
    makeupTip:
      'Click any missed day within the last {n} days to make it up — a make-up earns the same points as a normal check-in.',
    successToday: 'Checked in — day {n} of your streak, {points} points earned',
    successMakeup: '{day} made up, {points} points earned',
  },

  wallet: {
    rechargeTitle: 'Top up balance',
    withdrawTitle: 'Withdraw balance',
    amount: 'Amount (¥)',
    amountPlaceholder: 'Enter an amount, up to two decimal places',
    tooPrecise: 'Amounts allow two decimal places at most — please round it.',
    tooMuch:
      'Only {usable} is available and you are withdrawing {picked}. The part held by unpaid orders is frozen and cannot be withdrawn.',
    rechargeHint: 'Balance pays for orders, and refunds come back here',
    withdrawHint: 'Available balance: {usable}',
    description: 'Note',
    rechargeDescriptionPlaceholder: 'e.g. Campaign reward (optional)',
    withdrawDescriptionPlaceholder: 'e.g. Withdrawal to my bank card (optional)',
    rechargeTip:
      'This amount is added to your available balance and logged as a top-up; if it is your first top-up the wallet record is created at the same moment. ' +
      'Top-ups do not go through a real payment channel yet (the WeChat flow is not wired up), so only the book changes.',
    withdrawTip:
      'A withdrawal takes this amount off your available balance and logs it. The part held by unpaid orders is frozen and cannot be withdrawn, ' +
      'and neither can anything while the account is frozen. No money actually leaves here yet — that last step is done offline.',
    submitting: 'Submitting…',
    confirmRecharge: 'Top up',
    confirmWithdraw: 'Withdraw',
  },
};

export const ja: typeof zh = {
  addressForm: {
    addTitle: '配送先住所を追加',
    editTitle: '住所を編集',
    addTip:
      'ここで追加した住所はあなたのアカウントにのみ登録されます。1件目は自動的にデフォルトになり、「デフォルトに設定」を選ぶと従来のデフォルトが解除されます（どちらもサーバー側で保証）。',
    editTip:
      '編集するのはこの1件のみです。保存はレコード全体を上書きするため、タグや経度・緯度を空で送るとクリアされます。編集前に既に入力されていた項目はすべて再表示されています。',
    receiverName: '受取人姓名',
    receiverPhone: '受取人電話',
    province: '省',
    city: '市',
    district: '区/県',
    pleaseSelect: '選択してください',
    detail: '住所（詳細）',
    detailPlaceholder: '番地・建物名まで（地図で選択、後で編集可）',
    tag: 'タグ',
    tagPlaceholder: '家 / 会社 / 学校',
    setDefault: 'デフォルト住所に設定',
    longitude: '経度',
    latitude: '緯度',
    saving: '保存中…',
  },

  order: {
    title: '注文詳細',
    orderNo: '注文番号',
    status: '注文状態',
    orderType: '注文タイプ',
    created: '注文日時',
    itemsColumn: '商品行数',
    itemsSummary: '{lines}行 · {qty}点',
    store: '所属店舗',
    totalAmount: '注文金額',
    deliveryFee: '配送料',
    discountAmount: '割引金額',
    payAmount: '支払金額',
    payType: '支払い方法',
    payTime: '支払日時',
    deliveryNo: '配送番号',
    remark: '購入者メッセージ',
    itemsTitle: '商品明細',
    boxGroup: '1箱まとめて · {name}（{n}種 / 計{qty}点）',
    productGone: '（商品は存在しません）',
    returnedItems: '{n}点返品済み',
    noItems: 'この注文に商品はありません',
    footnote: '{day}の注文。変更や返品は店舗へご連絡ください。',
    payNow: '今すぐ支払い',
  },

  password: {
    title: 'パスワード変更',
    oldPassword: '現在のパスワード',
    newPassword: '新しいパスワード *（{n}文字以上）',
    confirmPassword: '新しいパスワード（確認）',
    tooShort: '{n}文字以上で入力してください',
    notSame: 'パスワードが一致しません',
    tip: '現在のパスワードはサーバーで照合するため、誤っているとそのままエラーになります。変更後もログイン状態は維持され、新しいパスワードは次のログインから使われます。',
    submitting: '送信中…',
  },

  signin: {
    title: 'チェックイン履歴',
    weekDays: ['月', '火', '水', '木', '金', '土', '日'],
    summary: '{month}：チェックイン{days}日（うち事後チェックイン{makeup}日） · 今月{points}ポイント · 最近連続{continuous}日',
    signInToday: '本日チェックイン',
    signedToday: '本日はチェックイン済み',
    signing: 'チェックイン中…',
    makeup: '事後チェックイン',
    makingUp: '事後チェックイン中…',
    dayPoints: '+{n}ポイント',
    prevMonth: '前月',
    nextMonth: '翌月',
    backToThisMonth: '今月に戻る',
    makeupTip:
      '過去{n}日以内の未チェックイン日をクリックすると事後チェックインできます。獲得ポイントは通常チェックインと同じです。',
    successToday: 'チェックイン成功：連続{n}日目、{points}ポイント獲得',
    successMakeup: '{day}の事後チェックインが完了し、{points}ポイントを獲得',
  },

  wallet: {
    rechargeTitle: '残高チャージ',
    withdrawTitle: '残高出金',
    amount: '金額（元）',
    amountPlaceholder: '金額を入力（小数点以下2桁まで）',
    tooPrecise: '金額は小数点以下2桁までです。2桁以内に変更してください。',
    tooMuch: '出金可能額は{usable}で、今回{picked}を出金しようとしています。未払い注文で確保中の金額は凍結されており出金できません。',
    rechargeHint: '残高は注文支払いに使い、返金はここに戻ります',
    withdrawHint: '利用可能残高 {usable}',
    description: '説明',
    rechargeDescriptionPlaceholder: '例：キャンペーン報酬（空欄可）',
    withdrawDescriptionPlaceholder: '例：銀行カードへ出金（空欄可）',
    rechargeTip:
      'この金額は利用可能残高に加わり、「チャージ」として履歴に記録されます。最初のチャージ時にはこの時点でウォレット口座も作成されます。今回のチャージは実際の入金チャネル（WeChat Payのチャージ導線）はまだ未接続のため、帳簿上の金額のみが変動します。',
    withdrawTip:
      '出金は利用可能残高からこの金額を減らし、「出金」として履歴に記録します。注文したが未払いの注文が確保している金額は凍結されており出金できません。これは口座が凍結されている場合も同じです。今回の出金も実際にはお金は動かず、実際の送金はオフラインで行われます。',
    submitting: '送信中…',
    confirmRecharge: 'チャージする',
    confirmWithdraw: '出金する',
  },
};

export const ko: typeof zh = {
  addressForm: {
    addTitle: '배송지 주소 추가',
    editTitle: '주소 수정',
    addTip:
      '여기서 추가한 주소는 본인 계정에만 등록됩니다. 첫 주소는 자동으로 기본으로 설정되며, "기본으로 설정"을 선택하면 기존 기본 주소가 해제됩니다(둘 다 서버에서 보장).',
    editTip:
      '수정하는 주소는 이 한 건뿐입니다. 저장은 레코드 전체를 덮어쓰므로, 태그와 경도·위도를 비워 두면 초기화됩니다. 수정 전에 이미 입력되어 있던 항목은 모두 다시 채워져 있습니다.',
    receiverName: '수령인 이름',
    receiverPhone: '수령인 연락처',
    province: '성',
    city: '시',
    district: '구/군',
    pleaseSelect: '선택하세요',
    detail: '상세 주소',
    detailPlaceholder: '도로명·건물명까지(지도에서 선택 후 수정 가능)',
    tag: '태그',
    tagPlaceholder: '집 / 회사 / 학교',
    setDefault: '기본 주소로 설정',
    longitude: '경도',
    latitude: '위도',
    saving: '저장 중…',
  },

  order: {
    title: '주문 상세',
    orderNo: '주문번호',
    status: '주문 상태',
    orderType: '주문 유형',
    created: '주문 시간',
    itemsColumn: '상품 행',
    itemsSummary: '{lines}행 · {qty}건',
    store: '소속 매장',
    totalAmount: '주문 금액',
    deliveryFee: '배송비',
    discountAmount: '할인 금액',
    payAmount: '결제 금액',
    payType: '결제 수단',
    payTime: '결제 시간',
    deliveryNo: '배송 번호',
    remark: '구매자 메시지',
    itemsTitle: '상품 상세',
    boxGroup: '1박스 · {name} ({n}종 / 총 {qty}개)',
    productGone: '(상품이 더 이상 없음)',
    returnedItems: '{n}건 반품됨',
    noItems: '이 주문에는 상품이 없습니다',
    footnote: '{day} 주문입니다. 변경이나 반품은 매장에 문의해 주세요.',
    payNow: '지금 결제',
  },

  password: {
    title: '비밀번호 변경',
    oldPassword: '현재 비밀번호',
    newPassword: '새 비밀번호 *({n}자 이상)',
    confirmPassword: '새 비밀번호 확인',
    tooShort: '{n}자 이상 입력해 주세요',
    notSame: '비밀번호가 일치하지 않습니다',
    tip: '현재 비밀번호는 서버에서 확인하므로 잘못 입력하면 바로 오류가 표시됩니다. 변경 후에도 로그인이 유지되며, 새 비밀번호는 다음 로그인부터 사용됩니다.',
    submitting: '제출 중…',
  },

  signin: {
    title: '출석 기록',
    weekDays: ['월', '화', '수', '목', '금', '토', '일'],
    summary: '{month}: 출석 {days}일 (미출석 체크인 {makeup}일) · 이번 달 {points}포인트 · 최근 {continuous}일 연속',
    signInToday: '오늘 출석',
    signedToday: '오늘 출석 완료',
    signing: '출석 중…',
    makeup: '미출석 체크인',
    makingUp: '미출석 체크인 중…',
    dayPoints: '+{n}포인트',
    prevMonth: '이전 달',
    nextMonth: '다음 달',
    backToThisMonth: '이번 달로',
    makeupTip:
      '최근 {n}일 이내 미출석 날짜를 클릭하면 미출석 체크인할 수 있으며, 획득 포인트는 일반 출석과 동일합니다.',
    successToday: '출석 성공: {n}일 연속, {points}포인트 획득',
    successMakeup: '{day} 미출석 체크인 완료, {points}포인트 획득',
  },

  wallet: {
    rechargeTitle: '잔액 충전',
    withdrawTitle: '잔액 출금',
    amount: '금액(위안)',
    amountPlaceholder: '금액 입력, 소수점 둘째 자리까지',
    tooPrecise: '금액은 소수점 둘째 자리까지 입력할 수 있습니다. 둘째 자리 이내로 수정해 주세요.',
    tooMuch: '출금 가능 금액은 {usable}이고 이번 출금은 {picked}입니다. 미결제 주문에 선점된 금액은 동결되어 출금할 수 없습니다.',
    rechargeHint: '잔액은 주문 결제에 사용하며 환불은 여기로 반환됩니다',
    withdrawHint: '사용 가능 잔액 {usable}',
    description: '설명',
    rechargeDescriptionPlaceholder: '예: 이벤트 보상 (공란 가능)',
    withdrawDescriptionPlaceholder: '예: 은행 카드로 출금 (공란 가능)',
    rechargeTip:
      '이 금액은 사용 가능 잔액에 더해 "충전" 내역으로 기록되며, 첫 충전 시 이 시점에 지갑 계좌가 함께 생성됩니다. 이번 충전은 실제 입금 채널(WeChat Pay 충전 연동)이 아직 미연동이므로 장부상의 금액만 변동됩니다.',
    withdrawTip:
      '출금은 사용 가능 잔액에서 이 금액을 빼고 "출금" 내역으로 기록합니다. 주문했으나 미결제인 주문이 선점한 금액은 동결되어 출금할 수 없으며, 계좌가 동결된 경우도 출금할 수 없습니다. 이번 출금도 실제 자금은 이동하지 않고, 실제 송금은 오프라인으로 처리됩니다.',
    submitting: '제출 중…',
    confirmRecharge: '충전하기',
    confirmWithdraw: '출금하기',
  },
};
