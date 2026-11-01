/**
 * 下单区那一档文案：购物车那一页、结算那一页、结算里的地址管理、订单支付弹窗。
 * （views/cart/CartPage.vue、views/checkout/CheckoutPage.vue、
 *  components/AddressManager.vue、components/OrderPayDialog.vue）
 *
 * 形状标准是中文这份，其余三档各自钉成它的类型（`en/ja/ko: typeof zh`）⇒ 少一格 vue-tsc 当场报错。四档并排放，改一句就在下面几行内。
 * 全站共用的称呼（页头、通用按钮、枚举档位、券的优惠那句）在 base.ts / enums.ts，这一档只收本区自己的话。
 *
 * 几条口径，免得下一区又争：
 * - 带数的句子整句进字典，数由调用方用 fill() 填槽（英文语序不一样，半截拼不出来）；
 * - 金额那一格给的是「¥12.00」这一整个串（币种符号仍由页面拼），字典只管句子；
 * - 「已过期」「优惠券」「未支付」这类枚举称呼不在这里另开一格，页面走 d.enum.* + enumLabel()；
 * - 后端回来的错误文字原样透传，不进字典。
 */

export const zh = {
  /** views/cart/CartPage.vue */
  cart: {
    title: '我的购物车',
    /** 标题右边那颗回首页的链接（← 那个箭头是页面给的，不在这一句里） */
    browse: '去逛逛',
    signedOut: '登录后才能看到购物车里的东西，也能在别的设备上接着买。',
    empty: '购物车还是空的。',
    startPicking: '去挑几件',
    selectAll: '全选',
    selectedCount: '已选 {n} 件',
    removeSelected: '删除选中',
    /** 行上那颗勾选框给读屏器念的：商品名 + 规格 */
    selectLineAria: '选中「{name} {spec}」',
    spec: '规格：{n}',
    /** 规格文本那后端可空（无规格的默认SKU），页面自己补的说法 */
    defaultSpec: '默认规格',
    goneProduct: '（这条规格的商品已经没了）',
    unbuyable: '这一行已经买不了了，可以先删掉，或等它回来',
    /** 购物车/结算页那一行的 tip：这一行是抽中入车的，单价按盲盒给的价格算（不是商品原价） */
    boxLine: '盲盒商品',
    /** 端盒那一趟在车里是一整盒一行：这一枚说的是「一整盒」，不是盒里的某一款 */
    endBoxLine: '端盒整盒',
    /** 整盒那一行的标题（后面页面自己拼「 · 盒名」） */
    wholeBox: '一整盒',
    /** 整盒那一行上那颗「展开看盒内有哪几款」 */
    boxPieces: '盒内共 {n} 件',
    /** 加号按到顶时给的那一格 title */
    stockLimitTitle: '这一条规格可用库存只剩 {n} 件',
    summary: '共 {a} 种 · 已选 {b} 件',
    total: '合计',
    goCheckout: '去结算',
    needSelection: '先勾选要买的商品',
    /** 下面三颗是确认框的（删一条和删一批共用一颗框，只是念出来的那句不一样） */
    deleteTitle: '删除购物车商品',
    removeLineConfirm: '从购物车里删掉「{name} {spec}」？',
    removeSelectedConfirm: '从购物车里删掉选中的 {n} 件？',
    /** 商品名读不着时那句里垫的一格 */
    unnamedProduct: '这件商品',
    /** 列表顶上那排筛选：普通商品 / 盲盒（整盒也在盲盒那一档，它的 source_type 也是 1） */
    filterAll: '全部',
    filterNormal: '普通商品',
    filterBox: '盲盒',
    /** 筛了某一档、车里那几件都不属于它时的一句（「购物车还是空的」只在真的空时念） */
    emptyFiltered: '这一档没有商品',
  },

  /** views/checkout/CheckoutPage.vue */
  checkout: {
    title: '确认订单',
    /** 标题右边那颗回头的链接：立即购买进来回商品，从车里来回车 */
    backToProduct: '返回商品',
    backToCart: '回购物车',
    signedOut: '登录后才能下单：地址、优惠券和积分都记在你这个账号上。',

    /** 下单成功那一屏（四句按 paid / 支付失败 / 还没付 分流） */
    placedPaid: '下单成功，已支付',
    placed: '下单成功',
    orderNoLine: '订单号 {no} · 实付',
    paidRecorded: '已按{pay}记下这一笔，接下来就等出库和配送，进度可以在「我的订单」里看。',
    /** {err} 送的是后端那句原文，不翻译 */
    payFailed: '订单已经落下，但这一笔没付成：{err}（可以在「我的订单」里接着付或取消。）',
    awaitingPay: '这一单还在待支付，可以在「我的订单」里接着付或取消。',
    viewOrders: '看我的订单',
    keepBrowsing: '继续逛逛',

    /** 车里一颗没勾 */
    noLines: '这一单没有商品：购物车里还没勾东西。',
    goCart: '去购物车',

    /** 商品清单 */
    goodsHeading: '商品（{a} 种 · {b} 件）',
    /** 捞不着那一档规格时写在 loadError 那一格里的（不是后端那句） */
    skuGone: '这一条规格已经没了，请回详情页重新选',
    lineGone: '（这件商品已经没了）',
    specLine: '规格：{spec} · {price} × {qty}',
    /** 端盒那一趟在车里是一整盒一行：这一格不说规格，说这一盒几件、结几盒、每盒多少钱 */
    boxLine: '盒内共 {n} 件 · {price} × {qty} 盒',
    /** 结算页那一颗：默认只看「一盒几件 × 几盒」，点了才下拉列出盒内每款 */
    boxStyles: '展开看盒内每款',
    defaultSpec: '默认规格',
    offline: '这件商品已下架',
    stockLeft: '可用库存只剩 {n} 件',
    blockedLines: '有 {n} 行买不了，把它们移出这一单才能提交。',
    adjustInCart: '回购物车调整',

    /** 收货地址那一块 */
    addressHeading: '收货地址',
    manageAddresses: '管理地址',
    /** 那句是「文本 + 一颗按钮 + 收标点」，拆成两颗，句号跟着按钮那句走（英文那档不用句号收尾） */
    noAddressLead: '还没有收货地址，',
    addAddressFirst: '先在这里加一条。',
    defaultTag: '默认',
    addressNoCoord: '这条地址既没定坐标也没选所在地区，选它下不了单，上面「管理地址」里点「修改」补一下',

    /** 优惠券那一块（标题和券名垫的那格用 d.enum.coupon，已过期用 d.enum.couponUserStatus） */
    couponSelected: '已选 {a} 张 · 一单最多 {b} 张',
    noCoupons: '名下没有可用的券。券由平台发放，可以在个人中心的「优惠券」里查看。',
    couponThresholdUsable: '满 {n} 可用 · ',
    couponNoThreshold: '无门槛 · ',
    expiredOn: '过期于',
    validUntil: '有效期至',
    /** 券在这单上没抵着钱时那一格报的（免邮券可能一分也没抵着） */
    couponNotUsed: '这一单没用上',
    /** 行下面那三格（券摊到行 / 积分摊到行 / 行实付），数由服务端报，这里只念 */
    lineCoupon: '券抵 {n}',
    linePoints: '分抵 {n}',
    linePaid: '行实付 {n}',

    /** 积分抵扣那一块（「全部」那颗用 d.common.all） */
    pointsHeading: '积分抵扣',
    pointsAvailable: '可用 {n} 分',
    pointsCanDeduct: '可抵 {n}（提交后先冻结，支付成功才真扣走）',
    pointsRateNote: '1 元值多少分由后台配置定，能抵多少钱以试算报回来的数为准',
    pointsSpreadNote:
      '积分按各行「小计 − 券优惠」还剩多少摊到行上，一行最多抵到自己为 0；超出各行承接得住的部分，试算会直接拒。',

    /** 支付方式那一块（候选走 d.enum.payType + enumChoices，余额那一档的说明都在这儿） */
    payMethodHeading: '支付方式',
    balanceAvailable: '可用 {n}',
    walletNoneNote: '还没有钱包账户（一分钱都没进过账），余额支付这一档暂时点不动；等第一笔钱进来就亮了。',
    walletFrozenNote: '钱包账户已被冻结，余额支付用不了，换一种支付方式。',
    balanceShortNote: '余额不够这一单，还差 {n}（余额只整笔付，不支持一部分余额一部分别的方式）。',
    gatewayNote:
      '微信/支付宝的网关还没接：这一格现在只是把你选的方式记到订单上，点「提交并支付」等于走服务端那句支付回调把这一单记成已支付。' +
      '选余额时下单这一刻就先占住这一笔（取消订单整笔退回来），支付成功才真扣走。',

    /** 买家留言 */
    remarkHeading: '买家留言',
    remarkPlaceholder: '有什么要交代给配送的，写在这儿',

    /** 金额那一块 */
    amountHeading: '金额',
    goodsAmount: '商品金额',
    deliveryFee: '配送费',
    payable: '实付',
    computing: '计算中…',
    includesShipping: '（含运费 {n}）',
    pointsUsedParen: '（{n} 分）',
    placeOrder: '提交订单',
    submitting: '提交中…',
    placeAndPay: '提交并支付 · {n}',
    processing: '处理中…',
    amountUpdating: '金额正在更新…',

    /** 提交那颗下面那句：按卡在哪一步给一句人话（每颗都是一个判据，别合） */
    hintNeedLogin: '请先登录',
    hintNoLines: '没有要结算的商品',
    hintBlocked: '有商品已下架或库存不足，先把它移出这一单',
    hintNeedAddress: '请先选一条收货地址',
    hintPreviewFailed: '这一单的钱没算出来，先按上面那句调整',
    hintWalletFrozen: '钱包账户被冻住了，余额支付用不了，换一种支付方式',
    hintBalanceShort: '钱包里还有 {a}，这一单要 {b}，换一种支付方式',
    hintPricing: '正在算这一单的钱…',
  },

  /** components/AddressManager.vue（结算页里那颗地址弹窗；关闭那颗用 d.common.close，修改那颗用 d.common.edit） */
  address: {
    dialogAria: '收货地址管理',
    title: '收货地址',
    add: '添加地址',
    loading: '正在捞你名下的地址…',
    empty: '还没存收货地址：先加一条，填过定位坐标或选过所在地区的才选得上（配送费按它分仓算）。',
    defaultTag: '默认',
    noDetail: '没填详细地址',
    noCoord: '这条既没定坐标也没选地区，选它下不了单，「修改」里点一下地图或选个地区补上',
  },

  /** components/OrderPayDialog.vue（关闭那颗用 d.common.close） */
  pay: {
    dialogAria: '订单支付',
    title: '支付订单',
    balanceNote:
      '余额支付点不动：余额是在下单那一刻就占住的，这一单当初没选它，要拿余额付得先取消这一单、重新下单时选余额。',
    gatewayNote: '微信/支付宝的网关还没接：确认支付就是把这一单记成已支付，选的方式记在订单上。',
    confirm: '确认支付',
    paying: '支付中…',
  },
};

export const en: typeof zh = {
  cart: {
    title: 'My cart',
    browse: 'Browse products',
    signedOut: 'Sign in to see what is in your cart and keep shopping on other devices.',
    empty: 'Your cart is empty.',
    startPicking: 'Start shopping',
    selectAll: 'Select all',
    selectedCount: '{n} selected',
    removeSelected: 'Remove selected',
    selectLineAria: 'Select “{name} {spec}”',
    spec: 'Variant: {n}',
    defaultSpec: 'Default variant',
    goneProduct: '(this variant is no longer listed)',
    unbuyable: 'This line can no longer be bought — remove it, or wait for it to come back.',
    boxLine: 'Blind box item',
    endBoxLine: 'Whole-box draw',
    wholeBox: 'Whole box',
    boxPieces: '{n} items in this box',
    stockLimitTitle: 'Only {n} left in stock for this variant',
    summary: '{a} variants · {b} selected',
    total: 'Total',
    goCheckout: 'Checkout',
    needSelection: 'Tick the items you want first',
    deleteTitle: 'Remove from cart',
    removeLineConfirm: 'Remove “{name} {spec}” from your cart?',
    removeSelectedConfirm: 'Remove the {n} selected items from your cart?',
    unnamedProduct: 'this item',
    /** 列表顶上那排筛选：普通商品 / 盲盒（整盒也在盲盒那一档，它的 source_type 也是 1） */
    filterAll: 'All',
    filterNormal: 'Products',
    filterBox: 'Blind boxes',
    /** 筛了某一档、车里那几件都不属于它时的一句（「购物车还是空的」只在真的空时念） */
    emptyFiltered: 'Nothing in this tab',
  },

  checkout: {
    title: 'Checkout',
    backToProduct: 'Back to product',
    backToCart: 'Back to cart',
    signedOut: 'Sign in to place an order — your addresses, coupons and points all live on your account.',

    placedPaid: 'Order placed and paid',
    placed: 'Order placed',
    orderNoLine: 'Order {no} · Paid',
    paidRecorded: 'Recorded as {pay}. Next comes picking and delivery — follow it under “My orders”.',
    payFailed:
      'The order is placed, but this payment did not go through: {err} (you can pay it or cancel it under “My orders”.)',
    awaitingPay: 'This order is awaiting payment — pay it or cancel it under “My orders”.',
    viewOrders: 'View my orders',
    keepBrowsing: 'Keep browsing',

    noLines: 'Nothing in this order: no cart items are ticked.',
    goCart: 'Go to cart',

    goodsHeading: 'Items ({a} variants · {b} units)',
    skuGone: 'This variant is no longer available — pick it again on the product page.',
    lineGone: '(this item is no longer listed)',
    specLine: 'Variant: {spec} · {price} × {qty}',
    boxLine: '{n} items per box · {price} × {qty} box',
    boxStyles: 'Show what is in the box',
    defaultSpec: 'Default variant',
    offline: 'This product is off sale',
    stockLeft: 'Only {n} left in stock',
    blockedLines: '{n} line(s) cannot be bought — take them out of this order to continue.',
    adjustInCart: 'Adjust in cart',

    addressHeading: 'Delivery address',
    manageAddresses: 'Manage addresses',
    noAddressLead: 'No delivery address yet',
    addAddressFirst: 'add one here.',
    defaultTag: 'Default',
    addressNoCoord: 'This address has neither coordinates nor a region, so you cannot order with it — fix it under “Manage addresses” above.',

    couponSelected: '{a} selected · up to {b} per order',
    noCoupons: 'You have no usable coupons. The platform issues them — check your account for the ones you hold.',
    couponThresholdUsable: 'Minimum spend {n} · ',
    couponNoThreshold: 'No minimum · ',
    expiredOn: 'Expired',
    validUntil: 'Valid until',
    couponNotUsed: 'Not used on this order',
    lineCoupon: 'Coupon {n}',
    linePoints: 'Points {n}',
    linePaid: 'Line total {n}',

    pointsHeading: 'Points',
    pointsAvailable: '{n} points available',
    pointsCanDeduct: 'Up to {n} off (frozen on submit, spent once paid)',
    pointsRateNote: 'The back office sets how many points a yuan is worth; the quote decides what you actually save.',
    pointsSpreadNote:
      'Points spread over the lines by what each line still owes after its coupon, and a line is never taken below zero. ' +
      'More than the lines can absorb is rejected by the quote.',

    payMethodHeading: 'Payment method',
    balanceAvailable: '{n} available',
    walletNoneNote:
      'No wallet yet — nothing has been deposited, so balance payment is unavailable until the first one lands.',
    walletFrozenNote: 'Your wallet is frozen, so balance payment is unavailable — choose another method.',
    balanceShortNote:
      'Your balance is {n} short of this order (balance pays the whole amount and cannot be combined with another method).',
    gatewayNote:
      'The WeChat and Alipay gateways are not wired up yet, so this choice is only recorded on the order and ' +
      '“Pay now” tells the server to mark it paid. Choosing balance locks the amount when the order is placed ' +
      '(cancelling releases it in full) and spends it only once paid.',

    remarkHeading: 'Order notes',
    remarkPlaceholder: 'Anything the delivery should know, put it here',

    amountHeading: 'Totals',
    goodsAmount: 'Items',
    deliveryFee: 'Delivery',
    payable: 'Total due',
    computing: 'Calculating…',
    includesShipping: '(includes {n} delivery)',
    pointsUsedParen: '({n} points)',
    placeOrder: 'Place order',
    submitting: 'Submitting…',
    placeAndPay: 'Pay now · {n}',
    processing: 'Processing…',
    amountUpdating: 'Amounts are updating…',

    hintNeedLogin: 'Sign in first',
    hintNoLines: 'There is nothing to check out',
    hintBlocked: 'Some items are off sale or out of stock — take them out of this order first',
    hintNeedAddress: 'Pick a delivery address first',
    hintPreviewFailed: 'This order could not be priced — follow the message above',
    hintWalletFrozen: 'Your wallet is frozen, so balance payment is unavailable — choose another method',
    hintBalanceShort: 'Your wallet holds {a}, this order needs {b} — choose another method',
    hintPricing: 'Pricing this order…',
  },

  address: {
    dialogAria: 'Delivery addresses',
    title: 'Delivery addresses',
    add: 'Add address',
    loading: 'Loading your addresses…',
    empty:
      'No saved addresses. Add one — an address needs coordinates or at least a region, because the shipping warehouse and the delivery fee come from it.',
    defaultTag: 'Default',
    noDetail: 'No street address filled in',
    noCoord: 'No coordinates and no region on this address, so you cannot order with it — open “Edit” and drop a pin or pick a region.',
  },

  pay: {
    dialogAria: 'Order payment',
    title: 'Pay for this order',
    balanceNote:
      'Balance payment is unavailable here: balance is locked when an order is placed, and this order was not. ' +
      'Cancel it and reorder with balance if you want to pay that way.',
    gatewayNote:
      'The WeChat and Alipay gateways are not wired up yet, so confirming simply marks the order paid and records the method you chose.',
    confirm: 'Confirm payment',
    paying: 'Paying…',
  },
};

export const ja: typeof zh = {
  cart: {
    title: 'マイカート',
    browse: '商品を見る',
    signedOut: 'ログインするとカートの中身を確認でき、他の端末でも買い続けられます。',
    empty: 'カートは空です。',
    startPicking: '買い物を始める',
    selectAll: 'すべて選択',
    selectedCount: '選択済み {n}件',
    removeSelected: '選択を削除',
    selectLineAria: '「{name} {spec}」を選択',
    spec: 'スペック: {n}',
    defaultSpec: 'デフォルトスペック',
    goneProduct: '（このスペックの販売は終了）',
    unbuyable: 'この商品は現在購入できません。削除するか、再販をお待ちください。',
    boxLine: 'ブラインドボックス商品',
    endBoxLine: 'エンドボックス1箱',
    wholeBox: '1箱まとめて',
    boxPieces: '箱内合計{n}点',
    stockLimitTitle: 'このスペックの在庫は{n}件のみ',
    summary: '{a}種 · {b}件選択',
    total: '合計',
    goCheckout: 'レジへ進む',
    needSelection: '購入する商品を選択してください',
    deleteTitle: 'カートから削除',
    removeLineConfirm: 'カートから「{name} {spec}」を削除しますか？',
    removeSelectedConfirm: '選択した{n}件をカートから削除しますか？',
    unnamedProduct: 'この商品',
    /** 列表顶上那排筛选：普通商品 / 盲盒（整盒也在盲盒那一档，它的 source_type 也是 1） */
    filterAll: 'すべて',
    filterNormal: '通常商品',
    filterBox: 'ブラインドボックス',
    /** 筛了某一档、车里那几件都不属于它时的一句（「购物车还是空的」只在真的空时念） */
    emptyFiltered: 'この分類の商品はありません',
  },

  checkout: {
    title: '注文確認',
    backToProduct: '商品に戻る',
    backToCart: 'カートに戻る',
    signedOut: '注文するにはログインしてください。住所・クーポン・ポイントはすべてアカウントに保存されます。',

    placedPaid: '注文完了・支払い済み',
    placed: '注文完了',
    orderNoLine: '注文番号 {no} · お支払い',
    paidRecorded: '{pay}で支払いを記録しました。次に出荷・配送が進みます。進捗は「マイ注文」でご確認ください。',
    payFailed: '注文は確定しましたが、支払いに失敗しました：{err}（「マイ注文」で再支払いまたはキャンセルできます。）',
    awaitingPay: 'この注文は支払い待ちです。「マイ注文」で支払いまたはキャンセルできます。',
    viewOrders: 'マイ注文を見る',
    keepBrowsing: 'お買い物を続ける',

    noLines: '注文商品がありません：カートで選択された商品がありません。',
    goCart: 'カートへ',

    goodsHeading: '商品（{a}種 · {b}点）',
    skuGone: 'このスペックは選択できません。商品ページで選び直してください。',
    lineGone: '（この商品は終了しました）',
    specLine: 'スペック: {spec} · {price} × {qty}',
    boxLine: '箱内合計{n}点 · {price} × {qty}箱',
    boxStyles: '箱内のラインナップを見る',
    defaultSpec: 'デフォルトスペック',
    offline: 'この商品は販売終了',
    stockLeft: '在庫は{n}件のみ',
    blockedLines: '{n}商品は購入できません。本注文から外すと続行できます。',
    adjustInCart: 'カートで調整',

    addressHeading: '配送先住所',
    manageAddresses: '住所管理',
    noAddressLead: '配送先住所がありません。',
    addAddressFirst: 'ここで追加',
    defaultTag: 'デフォルト',
    addressNoCoord: 'この住所は座標も地域も未設定のため注文に使えません。上の「住所管理」で「編集」により補完してください。',

    couponSelected: '{a}枚選択 · 1注文最大{b}枚',
    noCoupons: '利用可能なクーポンがありません。クーポンはプラットフォームが発行します。マイページの「クーポン」でご確認ください。',
    couponThresholdUsable: '{n}以上で利用可 · ',
    couponNoThreshold: '条件なし · ',
    expiredOn: '期限切れ',
    validUntil: '有効期限',
    couponNotUsed: '本注文では未使用',
    lineCoupon: 'クーポン {n}',
    linePoints: 'ポイント {n}',
    linePaid: 'この行の実払 {n}',

    pointsHeading: 'ポイント利用',
    pointsAvailable: '{n}ポイント利用可',
    pointsCanDeduct: '{n}まで値引き（注文時に凍結、支払い完了後に控除）',
    pointsRateNote: '1元のポイント数は管理画面で設定され、実際の割引額は試算の結果に基づきます。',
    pointsSpreadNote:
      'ポイントは各行の「小計 − クーポン割引」の残額に応じて配分され、各行は0を下回りません。各行が吸収できる額を超えると試算が拒否します。',

    payMethodHeading: '支払い方法',
    balanceAvailable: '利用可能 {n}',
    walletNoneNote: 'ウォレット口座がまだありません（入金実績なし）。最初の入金があるまで残高で支払うは利用できません。',
    walletFrozenNote: 'ウォレットが凍結されているため、残高で支払うは利用できません。他の支払い方法をお選びください。',
    balanceShortNote: '残高が本注文に{n}不足しています（残高は全額支払いのみで、他の方法との併用はできません）。',
    gatewayNote:
      'WeChat PayとAlipayのゲートウェイはまだ未接続です。ここは選択した方法を注文に記録するのみで、「支払いをする」を押すとサーバー側の決済コールバックで本注文を支払い済みとして記録します。残高を選ぶと注文時点でこの金額が確保され（注文キャンセルで全額戻り）、支払い成功後に実際に控除されます。',

    remarkHeading: '購入者メッセージ',
    remarkPlaceholder: '配送への依頼事項をこちらに',

    amountHeading: '金額',
    goodsAmount: '商品金額',
    deliveryFee: '配送料',
    payable: 'お支払い合計',
    computing: '計算中…',
    includesShipping: '（配送料{n}込み）',
    pointsUsedParen: '（{n}ポイント）',
    placeOrder: '注文する',
    submitting: '送信中…',
    placeAndPay: '支払いをする · {n}',
    processing: '処理中…',
    amountUpdating: '金額を更新中…',

    hintNeedLogin: 'ログインしてください',
    hintNoLines: '注文する商品がありません',
    hintBlocked: '販売終了または在庫切れの商品があります。本注文から外してください',
    hintNeedAddress: '配送先住所を選択してください',
    hintPreviewFailed: 'この注文の金額を計算できませんでした。上の案内をご確認ください',
    hintWalletFrozen: 'ウォレットが凍結されているため、残高で支払うは利用できません。他の方法をお選びください',
    hintBalanceShort: '残高は{a}、本注文は{b}が必要です。他の方法をお選びください',
    hintPricing: '金額を計算中…',
  },

  address: {
    dialogAria: '配送先住所管理',
    title: '配送先住所',
    add: '住所を追加',
    loading: '住所を読み込み中…',
    empty: '保存された住所がありません。追加してください。配送料は住所から計算されるため、座標または地域を入力した住所のみ選択できます。',
    defaultTag: 'デフォルト',
    noDetail: '詳細住所未入力',
    noCoord: 'この住所は座標も地域も未設定のため注文に使えません。「住所を編集」で地図から選ぶか地域を指定してください。',
  },

  pay: {
    dialogAria: '注文の支払い',
    title: 'この注文を支払う',
    balanceNote: '残高で支払うは選べません：残高は注文確定時に確保されるため、この注文では使えませんでした。残高で支払うには本注文をキャンセルし、再注文時に残高を選んでください。',
    gatewayNote: 'WeChat PayとAlipayのゲートウェイは未接続のため、確認すると本注文を支払い済みとして記録し、選択した方法を保存します。',
    confirm: '支払いを確定',
    paying: '支払い中…',
  },
};

export const ko: typeof zh = {
  cart: {
    title: '내 장바구니',
    browse: '상품 보기',
    signedOut: '로그인하면 장바구니 내용을 확인할 수 있고 다른 기기에서도 이어서 구매할 수 있습니다.',
    empty: '장바구니가 비어 있습니다.',
    startPicking: '쇼핑 시작하기',
    selectAll: '전체 선택',
    selectedCount: '{n}건 선택됨',
    removeSelected: '선택 삭제',
    selectLineAria: '"{name} {spec}" 선택',
    spec: '사양: {n}',
    defaultSpec: '기본 사양',
    goneProduct: '(이 사양은 판매 종료)',
    unbuyable: '이 상품은 현재 구매할 수 없습니다. 삭제하거나 재출고를 기다려 주세요.',
    boxLine: '블라인드 박스 상품',
    endBoxLine: '엔박스 1박스',
    wholeBox: '1박스 전체',
    boxPieces: '박스 총 {n}개',
    stockLimitTitle: '이 사양의 재고 {n}개 남음',
    summary: '{a}종 · {b}건 선택',
    total: '합계',
    goCheckout: '결제로 이동',
    needSelection: '구매할 상품을 선택해 주세요',
    deleteTitle: '장바구니에서 삭제',
    removeLineConfirm: '장바구니에서 "{name} {spec}"을(를) 삭제할까요?',
    removeSelectedConfirm: '선택한 {n}건을 장바구니에서 삭제할까요?',
    unnamedProduct: '이 상품',
    /** 列表顶上那排筛选：普通商品 / 盲盒（整盒也在盲盒那一档，它的 source_type 也是 1） */
    filterAll: '전체',
    filterNormal: '일반 상품',
    filterBox: '블라인드 박스',
    /** 筛了某一档、车里那几件都不属于它时的一句（「购物车还是空的」只在真的空时念） */
    emptyFiltered: '이 분류의 상품이 없습니다',
  },

  checkout: {
    title: '주문 확인',
    backToProduct: '상품으로 돌아가기',
    backToCart: '장바구니로 돌아가기',
    signedOut: '주문하려면 로그인하세요. 주소, 쿠폰, 포인트는 모두 계정에 저장됩니다.',

    placedPaid: '주문 완료 · 결제 완료',
    placed: '주문 완료',
    orderNoLine: '주문번호 {no} · 최종 결제',
    paidRecorded: '{pay}(으)로 결제가 기록되었습니다. 다음 단계는 출고·배송이며 진행 상황은 "주문 내역"에서 확인하실 수 있습니다.',
    payFailed: '주문은 접수되었으나 결제에 실패했습니다: {err} ("주문 내역"에서 다시 결제하거나 취소할 수 있습니다).',
    awaitingPay: '이 주문은 결제 대기입니다. "주문 내역"에서 결제하거나 취소할 수 있습니다.',
    viewOrders: '주문 내역 보기',
    keepBrowsing: '쇼핑 계속하기',

    noLines: '주문 상품이 없습니다: 장바구니에서 선택된 상품이 없습니다.',
    goCart: '장바구니로',

    goodsHeading: '상품 ({a}종 · {b}건)',
    skuGone: '이 사양은 선택할 수 없습니다. 상품 페이지에서 다시 선택해 주세요.',
    lineGone: '(이 상품은 판매 종료)',
    specLine: '사양: {spec} · {price} × {qty}',
    boxLine: '박스 총 {n}개 · {price} × {qty}박스',
    boxStyles: '박스 구성 보기',
    defaultSpec: '기본 사양',
    offline: '이 상품 판매 종료',
    stockLeft: '재고 {n}개 남음',
    blockedLines: '{n}개 상품은 구매할 수 없습니다. 주문에서 제외하면 계속할 수 있습니다.',
    adjustInCart: '장바구니에서 조정',

    addressHeading: '배송지 주소',
    manageAddresses: '주소 관리',
    noAddressLead: '배송지 주소가 없습니다.',
    addAddressFirst: '여기서 추가',
    defaultTag: '기본',
    addressNoCoord: '이 주소에는 좌표와 지역이 없어 주문에 사용할 수 없습니다. 위의 "주소 관리"에서 "수정"으로 보완해 주세요.',

    couponSelected: '{a}개 선택 · 주문당 최대 {b}개',
    noCoupons: '사용 가능한 쿠폰이 없습니다. 쿠폰은 플랫폼에서 발급하며 내 정보의 "쿠폰"에서 확인할 수 있습니다.',
    couponThresholdUsable: '{n} 이상 사용 가능 · ',
    couponNoThreshold: '조건 없음 · ',
    expiredOn: '만료됨',
    validUntil: '유효 기간',
    couponNotUsed: '이 주문에서 미사용',
    lineCoupon: '쿠폰 {n}',
    linePoints: '포인트 {n}',
    linePaid: '이 행 최종 결제 {n}',

    pointsHeading: '포인트 사용',
    pointsAvailable: '{n}포인트 사용 가능',
    pointsCanDeduct: '{n}까지 할인 (주문 시 동결, 결제 완료 후 차감)',
    pointsRateNote: '1위안당 포인트는 관리자에서 설정되며, 실제 할인 금액은 산정 결과에 따릅니다.',
    pointsSpreadNote:
      '포인트는 각 행의 "소계 − 쿠폰 할인" 잔액에 따라 배분되며, 각 행은 0 이하로 내려가지 않습니다. 행이 감당하는 금액을 초과하면 산정에서 거부됩니다.',

    payMethodHeading: '결제 수단',
    balanceAvailable: '사용 가능 {n}',
    walletNoneNote: '지갑 계좌가 아직 없습니다(입금 내역 없음). 첫 입금이 들어올 때까지 잔액 결제를 사용할 수 없습니다.',
    walletFrozenNote: '지갑이 동결되어 잔액 결제를 사용할 수 없습니다. 다른 결제 수단을 선택해 주세요.',
    balanceShortNote: '잔액이 이 주문에 {n} 부족합니다(잔액은 전액 결제만 가능하며 다른 수단과 혼용할 수 없습니다).',
    gatewayNote:
      'WeChat Pay와 Alipay 게이트웨이는 아직 연동되지 않았습니다. 여기서는 선택한 수단을 주문에 기록만 하며, "결제하기"를 누르면 서버의 결제 콜백으로 이 주문을 결제 완료로 기록합니다. 잔액을 선택하면 주문 시점에 금액이 선점되며(주문 취소 시 전액 반환), 결제 성공 후에 실제로 차감됩니다.',

    remarkHeading: '구매자 메시지',
    remarkPlaceholder: '배송 관련 요청 사항을 여기에 입력하세요',

    amountHeading: '금액',
    goodsAmount: '상품 금액',
    deliveryFee: '배송비',
    payable: '최종 결제 금액',
    computing: '계산 중…',
    includesShipping: '(배송비 {n} 포함)',
    pointsUsedParen: '({n}포인트)',
    placeOrder: '주문하기',
    submitting: '제출 중…',
    placeAndPay: '결제하기 · {n}',
    processing: '처리 중…',
    amountUpdating: '금액 업데이트 중…',

    hintNeedLogin: '로그인하세요',
    hintNoLines: '주문할 상품이 없습니다',
    hintBlocked: '판매 종료 또는 재고 소진 상품이 있습니다. 주문에서 제외해 주세요',
    hintNeedAddress: '배송지 주소를 선택해 주세요',
    hintPreviewFailed: '이 주문의 금액을 계산하지 못했습니다. 위 안내를 확인해 주세요',
    hintWalletFrozen: '지갑이 동결되어 잔액 결제를 사용할 수 없습니다. 다른 수단을 선택해 주세요',
    hintBalanceShort: '잔액은 {a}이고 이 주문에는 {b}이(가) 필요합니다. 다른 수단을 선택해 주세요',
    hintPricing: '금액 계산 중…',
  },

  address: {
    dialogAria: '배송지 주소 관리',
    title: '배송지 주소',
    add: '주소 추가',
    loading: '주소를 불러오는 중…',
    empty: '저장된 주소가 없습니다. 추가해 주세요. 배송비는 주소로 산정되므로 좌표 또는 지역이 입력된 주소만 선택할 수 있습니다.',
    defaultTag: '기본',
    noDetail: '상세 주소 미입력',
    noCoord: '이 주소에는 좌표와 지역이 없어 주문에 사용할 수 없습니다. "주소 수정"에서 지도로 선택하거나 지역을 지정해 주세요.',
  },

  pay: {
    dialogAria: '주문 결제',
    title: '이 주문 결제',
    balanceNote: '잔액 결제는 사용할 수 없습니다: 잔액은 주문 시점에 선점되므로 이 주문에서는 사용할 수 없었습니다. 잔액으로 결제하려면 이 주문을 취소하고 다시 주문할 때 잔액을 선택해 주세요.',
    gatewayNote: 'WeChat Pay와 Alipay 게이트웨이가 미연동이므로, 확인하면 이 주문을 결제 완료로 기록하고 선택한 수단을 저장합니다.',
    confirm: '결제 확인',
    paying: '결제 중…',
  },
};
