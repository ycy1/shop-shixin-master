/**
 * 个人中心那一页的文案：左边那列菜单、五个 tab、八块列表的表头与提示，加上改基础信息那颗弹窗。
 * （views/user/Profile.vue、views/user/AccountForm.vue）
 *
 * 这一页里另五颗弹窗（地址、订单详情、改密码、签到日历、充值提现）在 forms.ts，别往这一档堆。
 * 形状标准是中文这份，其余三档各自钉成它的类型（`en/ja/ko: typeof zh`）⇒ 少一格 vue-tsc 当场报错。四档并排放，改一句就在下面几行内。
 * 全站共用的称呼（页头、通用按钮、枚举档位）在 base.ts / enums.ts，这一档只收本区自己的话。
 */

export const zh = {
  account: {
    /**
     * 右上角「共 N 条」和底下「再看 N 条」的量词：各块不一样，由菜单/tab 那一格带着走
     * （口径同 d.common.total —— 那颗里不写死量词，就是留给这里的）。
     */
    units: {
      order: '单',
      address: '条',
      favorite: '条',
      browse: '件',
      coupon: '张',
      badge: '枚',
      ticket: '张',
      pointLog: '笔',
      walletLog: '笔',
      message: '条',
      plaza: '条',
    },

    /** 页面上零散的那几句：标题、未登录提示、退出登录、成功提示、挂件那两颗按钮 */
    page: {
      title: '个人中心',
      navAria: '个人中心栏目',
      needLogin: '登录后查看个人资料、优惠券、积分、钱包、订单、收货地址、收藏、浏览足迹和签到记录。',
      signOut: '退出登录',
      /** 确认框上那颗按钮写的动词比标题短一档：「退出登录」/「退出」 */
      signOutAction: '退出',
      signOutConfirm: '确定退出「{name}」这个账号？退出后要重新登录才能看订单、地址和签到记录。',
      retry: '重试',
      viewAll: '查看全部',
      /** 底下那颗「再看 N 条」：量词跟着这一格走，所以和 d.common.more 不是同一格 */
      moreUnits: '再看 {n} {unit}',
      profileSaved: '资料已保存。顶部那颗昵称是登录时的快照，重新登录才跟着换。',
      passwordChanged: '密码已改，下一次登录用新的这一个。',
    },

    /** 左边那列菜单 +「个人信息」下面那六个 tab（券和徽章两格用 d.enum.coupon / d.enum.badge） */
    menu: {
      personal: '个人信息',
      orders: '我的订单',
      tickets: '我的票种',
      addresses: '收货地址',
      favorites: '我的收藏',
      browses: '浏览足迹',
      messages: '我的消息',
      base: '基础信息',
      points: '积分',
      wallet: '钱包',
      plaza: '广场消息',
    },

    /** 「个人信息 → 基础信息」那一格：十三排的表头、上面那排按钮、没读到时的空态 */
    info: {
      avatarLabel: '头像',
      zoomAvatar: '放大头像',
      nickname: '昵称',
      username: '账号',
      mobile: '手机号',
      email: '邮箱',
      sex: '性别',
      accountType: '账号类型',
      status: '状态',
      statusEnabled: '启用',
      statusDisabled: '禁用',
      area: '所在地区',
      detail: '详细地址',
      signature: '个性签名',
      roles: '角色',
      createdAt: '注册时间',
      lastLogin: '最后登录时间',
      empty: '没读到账号信息',
      signinRecords: '签到记录',
      changePassword: '修改密码',
    },

    /** 我的订单那一块（状态、详情都另有共用格子：d.enum.orderStatus、d.common.detail） */
    order: {
      kindsPieces: '{kinds} 种 / {pieces} 件',
      pay: '支付',
      empty: '还没有过订单',
    },

    /**
     * 「我的订单」下面那个「我的票种」tab（一行一张发到手上、能进园的主题公园票）。
     * 票种类型 / 入园时段 / 票的状态那三档称呼走后端字典，不在这儿抄一份（见 @/lib/dict）。
     */
    ticket: {
      empty: '还没有拿到过票',
      /** 一行第三句：去哪一家、哪天来玩、约的哪个时段 */
      visit: '{park} · {date} {slot}',
      /** 园区名读不出来时（园区被删了）垫那一格 */
      parkFallback: '园区已下架',
      /** 日期那格为空（脏数据）时的说法 */
      dateFallback: '日期待定',
      holder: '持票人 {name}',
      noHolder: '没填持票人',
      /** 票面图那一颗：点开是一整屏，对着闸机扫的就是它 */
      viewQr: '票面二维码',
      /** 出票时那趟出图失败（票本身照样能用票码核销，所以只是这一格空着） */
      noQr: '票面图没生成',
    },

    /** 收货地址那一块；「编辑」「删除」用 d.common.edit / d.common.delete */
    address: {
      add: '添加地址',
      defaultTag: '默认',
      noDetail: '没填详细地址',
      empty: '还没存收货地址',
      /** 门牌那三格都没填时，确认框里只能念这一句兜底 */
      thisOne: '这条地址',
      removeTitle: '删除收货地址',
      removeConfirm: '确定删掉「{who}」这条收货地址？',
    },

    /** 我的收藏那一块；类型那一格用 d.enum.favoriteTarget */
    favorite: {
      removed: '（已删除）',
      /** target_type 认不出（脏数据）时，兜底那句里顶上的一格 */
      objectFallback: '对象',
      empty: '还没有收藏过东西',
      /** 类型筛选那颗「全部」，以及选了某一档但一条都没有时的那句 */
      filterAll: '全部',
      emptyFiltered: '这一档还没有收藏',
      removeTitle: '删除收藏',
      removeByName: '确定不再收藏「{name}」？',
      removeByType: '确定删掉这条{what}收藏？',
    },

    /** 浏览足迹那一块 */
    browse: {
      gone: '（商品已经不在了）',
      empty: '还没浏览过商品',
    },

    /**
     * 广场消息那一块（左边菜单上和「个人信息」持平的一档）；两档筛选的候选来自 d.enum.plazaAuditStatus / plazaRelation，
     * 两颗按钮用 d.common.*，关键字那格沿用广场列表的搜索提示（搜的是同一批列）。
     */
    plaza: {
      empty: '还没在广场上发过东西',
      /** 一条都没填标题时，确认框里只能念这一句兜底 */
      thisOne: '这条消息',
      removeTitle: '删除广场消息',
      removeConfirm: '确定删掉「{who}」这条广场消息？删了就找不回来，名下收到的赞和评论也跟着没。',
      relationFilter: '关联类型',
      auditFilter: '审核状态',
      clearFilters: '清空筛选',
      emptyFiltered: '这个条件下没有广场消息，换个筛选看看',
    },

    /**
     * 消息中心那一块：三档筛选（全部 / 系统 / 互动，后两档头上顶着自己的未读数）、一行那两句、空态。
     * 正文是服务端写的中文（属于内容不属于界面），这里只铺框架文案；跳转各端自己按 businessType 拼。
     */
    message: {
      filterAll: '全部',
      filterSystem: '系统消息',
      filterInteract: '互动消息',
      markAllRead: '全部已读',
      allReadDone: '所有消息都读过了',
      selectAll: '全选这一页',
      selectedCount: '已选 {count} 条',
      readSelected: '批量已读',
      deleteSelected: '批量删除',
      readSelectedDone: '已把选中的 {count} 条标成已读',
      deleteSelectedConfirm: '删掉选中的 {count} 条？删了只是对你隐藏，别的收件人照样能看到。',
      delete: '删除',
      deleteConfirm: '删掉这条消息？删了只是对你隐藏，别的收件人照样能看到。',
      removed: '已删除',
      unread: '未读',
      read: '已读',
      empty: '还没有收到消息',
      emptyFiltered: '这一档没有消息，换个筛选看看',
      failed: '操作没成功：{error}',
    },

    /** 优惠券那一格：两档筛选的表头、一行一张券上那几句、空态 */
    coupon: {
      statusFilter: '状态',
      typeFilter: '类型',
      clearFilters: '清空筛选',
      nameFallback: '券 {id}',
      validUntil: '{d} 前有效',
      received: '领取于 {d}',
      used: '用于 {d}',
      orderNo: '单号 {no}',
      emptyFiltered: '这个条件下没有券，换个筛选看看',
      empty: '手里还没有券',
    },

    /** 徽章那一格，加上账号卡下面那一排挂件 */
    badge: {
      pendantCount: '徽章 {n} {unit}',
      pendantLoading: '徽章加载中…',
      pendantFailed: '徽章没拉到（{error}）',
      /** 名称没填时的兜底（挂件悬停、列表第一排、放大层的 alt 三处都用它） */
      nameFallback: '徽章 {id}',
      /** 放大层底下那句：徽章文字 */
      captionText: '徽章文字「{text}」',
      validUntil: '{d} 前有效',
      forever: '长期有效',
      received: '获得于 {d}',
      empty: '还没有拿到过徽章',
      /** 每行右边那颗开关：这一枚要不要挂在账号卡下面那排挂件上（改的是 is_show 一列，不动徽章本身） */
      hide: '收起',
      show: '展示',
      allHidden: '徽章都收起来了，点「查看全部」放回来',
      showFailed: '展示状态没改掉：{error}',
    },

    /** 积分那一格：上面那本账的六排、下面流水的两句 */
    points: {
      available: '可用积分',
      totalEarned: '累计获得',
      totalUsed: '累计消耗',
      frozen: '冻结中',
      expiring: '即将过期',
      createdAt: '建账时间',
      emptyAccount: '还没有积分账户（签到、下单、评价都会记分，那时才建这一行账）',
      /** 流水右边那一列的前缀：余额 */
      balanceLabel: '余',
      emptyLogs: '还没有积分流水',
    },

    /** 钱包那一格：toolbar 两颗、上面那本账的八排、下面流水 */
    wallet: {
      withdraw: '提现',
      recharge: '充值',
      available: '可用余额',
      frozen: '冻结中',
      gift: '赠送余额',
      totalRecharge: '累计充值',
      totalConsume: '累计消费',
      totalWithdraw: '累计提现',
      status: '账户状态',
      createdAt: '建账时间',
      emptyAccount: '还没有钱包账户（点上面那颗「充值」充第一笔就会建出这一行账，下单用余额支付、退款退回余额时也会）',
      balanceLabel: '余',
      emptyLogs: '还没有钱包流水',
    },

    /** 「修改」点开的那颗弹窗（views/user/AccountForm.vue）；按钮「取消」「确定」「关闭」用 d.common.* */
    accountForm: {
      dialogTitle: '修改基本信息',
      zoomAvatar: '放大头像',
      avatarAlt: '头像',
      noAvatar: '还没有头像',
      avatarPick: '选一张图',
      avatarChange: '换一张',
      avatarUploading: '上传中…',
      onlyImage: '只能选图片文件',
      nickname: '昵称 *',
      mobile: '手机号',
      mobileHint: '手机号是 11 位、1 开头的',
      email: '邮箱',
      emailHint: '邮箱看着不对，要带 @ 和域名',
      sex: '性别',
      province: '省',
      city: '市',
      district: '区/县',
      selectPlaceholder: '请选择',
      detailLabel: '详细地址',
      detailPlaceholder: '街道、门牌号（地图选点会自动带过来，可以再改）',
      longitude: '经度',
      latitude: '纬度',
      signature: '个性签名',
      saving: '保存中…',
      /** 表单底下那段小字：整段一格，中英文各自的语序都在这一句里排好 */
      note: '这里改的只有你自己账号上这几格：账号、账号类型、状态、角色动不了（服务端只认这一串，id 也是由它钉成登录 id）。空着送过去等于不改，所以这几格改得了、清不掉；头像换了要先看图传上去，顶上一颗和这块的昵称是登录时那份快照，要跟着换得重新登录。',
    },
  },
};

export const en: typeof zh = {
  account: {
    units: {
      order: 'orders',
      address: 'addresses',
      favorite: 'entries',
      browse: 'items',
      coupon: 'coupons',
      badge: 'badges',
      ticket: 'tickets',
      pointLog: 'entries',
      walletLog: 'transactions',
      message: 'messages',
      plaza: 'posts',
    },

    page: {
      title: 'My account',
      navAria: 'Account sections',
      needLogin:
        'Sign in to see your profile, coupons, points, wallet, orders, delivery addresses, favorites, browsing history and check-ins.',
      signOut: 'Sign out',
      signOutAction: 'Sign out',
      signOutConfirm: 'Sign out of “{name}”? You will have to sign in again to see orders, addresses and check-ins.',
      retry: 'Retry',
      viewAll: 'View all',
      moreUnits: 'Show {n} more {unit}',
      profileSaved: 'Profile saved. The name in the header is the snapshot taken at sign-in and only changes when you sign in again.',
      passwordChanged: 'Password changed — use the new one the next time you sign in.',
    },

    menu: {
      personal: 'My info',
      orders: 'My orders',
      tickets: 'My tickets',
      addresses: 'Delivery addresses',
      favorites: 'Favorites',
      browses: 'Recently viewed',
      messages: 'My messages',
      base: 'Profile',
      points: 'Points',
      wallet: 'Wallet',
      plaza: 'Plaza posts',
    },

    info: {
      avatarLabel: 'Avatar',
      zoomAvatar: 'Zoom avatar',
      nickname: 'Nickname',
      username: 'Account',
      mobile: 'Mobile',
      email: 'Email',
      sex: 'Gender',
      accountType: 'Account type',
      status: 'Status',
      statusEnabled: 'Active',
      statusDisabled: 'Disabled',
      area: 'Region',
      detail: 'Address line',
      signature: 'Bio',
      roles: 'Roles',
      createdAt: 'Registered on',
      lastLogin: 'Last sign-in',
      empty: 'No account information found',
      signinRecords: 'Check-ins',
      changePassword: 'Change password',
    },

    order: {
      kindsPieces: '{kinds} variants / {pieces} items',
      pay: 'Pay',
      empty: 'No orders yet',
    },

    /** The “My tickets” tab under My orders: one row = one park ticket in hand. Status/type/slot labels come from the backend dictionary. */
    ticket: {
      empty: 'No tickets yet',
      visit: '{park} · {date} {slot}',
      parkFallback: 'park unavailable',
      dateFallback: 'date TBD',
      holder: 'Bearer {name}',
      noHolder: 'No bearer filled in',
      viewQr: 'Ticket QR code',
      noQr: 'No ticket image',
    },

    address: {
      add: 'Add address',
      defaultTag: 'Default',
      noDetail: 'No street address yet',
      empty: 'No saved addresses',
      thisOne: 'this address',
      removeTitle: 'Delete delivery address',
      removeConfirm: 'Delete this delivery address for “{who}”?',
    },

    favorite: {
      removed: '(removed)',
      objectFallback: 'item',
      empty: 'Nothing favorited yet',
      /** 类型筛选那颗「全部」，以及选了某一档但一条都没有时的那句 */
      filterAll: 'All',
      emptyFiltered: 'Nothing in this type yet',
      removeTitle: 'Remove from favorites',
      removeByName: 'Stop favoriting “{name}”?',
      removeByType: 'Remove this {what} from your favorites?',
    },

    browse: {
      gone: '(this product is gone)',
      empty: 'No browsing history yet',
    },

    plaza: {
      empty: 'You have not posted in the plaza yet',
      thisOne: 'this post',
      removeTitle: 'Delete plaza post',
      removeConfirm: 'Delete {who} from the plaza? It is gone for good, and so are the likes and comments it got.',
      relationFilter: 'Link type',
      auditFilter: 'Review status',
      clearFilters: 'Clear filters',
      emptyFiltered: 'No plaza posts match these filters — try another one',
    },

    message: {
      filterAll: 'All',
      filterSystem: 'System',
      filterInteract: 'Interactions',
      markAllRead: 'Mark all read',
      allReadDone: 'All your messages are now read',
      selectAll: 'Select this page',
      selectedCount: '{count} selected',
      readSelected: 'Mark selected read',
      deleteSelected: 'Delete selected',
      readSelectedDone: 'Marked {count} message(s) as read',
      deleteSelectedConfirm:
        'Delete the {count} selected messages? They only disappear for you — other recipients still see them.',
      delete: 'Delete',
      deleteConfirm: 'Delete this message? It only disappears for you — other recipients still see it.',
      removed: 'Deleted',
      unread: 'Unread',
      read: 'Read',
      empty: 'No messages yet',
      emptyFiltered: 'Nothing in this tab — try another filter',
      failed: 'Could not do that: {error}',
    },

    coupon: {
      statusFilter: 'Status',
      typeFilter: 'Type',
      clearFilters: 'Clear filters',
      nameFallback: 'Coupon {id}',
      validUntil: 'Valid until {d}',
      received: 'Claimed {d}',
      used: 'Used {d}',
      orderNo: 'Order {no}',
      emptyFiltered: 'No coupons match these filters — try another one',
      empty: 'No coupons yet',
    },

    badge: {
      pendantCount: '{n} {unit}',
      pendantLoading: 'Loading badges…',
      pendantFailed: 'Badges did not load ({error})',
      nameFallback: 'Badge {id}',
      captionText: 'Badge wording: “{text}”',
      validUntil: 'Valid until {d}',
      forever: 'Never expires',
      received: 'Earned {d}',
      empty: 'No badges yet',
      hide: 'Hide',
      show: 'Show',
      allHidden: 'All your badges are tucked away — use “View all” to show one again',
      showFailed: 'Could not change the display setting: {error}',
    },

    points: {
      available: 'Available',
      totalEarned: 'Earned',
      totalUsed: 'Used',
      frozen: 'Frozen',
      expiring: 'Expiring soon',
      createdAt: 'Account opened',
      emptyAccount: 'No points account yet — checking in, buying something or leaving a review opens one',
      balanceLabel: 'Bal',
      emptyLogs: 'No points history yet',
    },

    wallet: {
      withdraw: 'Withdraw',
      recharge: 'Top up',
      available: 'Available',
      frozen: 'Frozen',
      gift: 'Gift balance',
      totalRecharge: 'Top-ups',
      totalConsume: 'Spent',
      totalWithdraw: 'Withdrawn',
      status: 'Account status',
      createdAt: 'Account opened',
      emptyAccount: 'No wallet yet — the first top-up opens it (paying with balance or a balance refund opens it too)',
      balanceLabel: 'Bal',
      emptyLogs: 'No wallet history yet',
    },

    accountForm: {
      dialogTitle: 'Edit profile',
      zoomAvatar: 'Zoom avatar',
      avatarAlt: 'Avatar',
      noAvatar: 'No avatar yet',
      avatarPick: 'Choose an image',
      avatarChange: 'Replace',
      avatarUploading: 'Uploading…',
      onlyImage: 'Only image files can be uploaded',
      nickname: 'Nickname *',
      mobile: 'Mobile',
      mobileHint: 'Mobile numbers are 11 digits and start with 1',
      email: 'Email',
      emailHint: 'That email does not look right — it needs an @ and a domain',
      sex: 'Gender',
      province: 'Province',
      city: 'City',
      district: 'District',
      selectPlaceholder: 'Select',
      detailLabel: 'Address line',
      detailPlaceholder: 'Street and house number (picking a point on the map fills this in; you can still edit it)',
      longitude: 'Longitude',
      latitude: 'Latitude',
      signature: 'Bio',
      saving: 'Saving…',
      note:
        'This form only touches the fields on your own account: the login name, account type, status and roles are out of reach (the server accepts exactly this set and pins the id to your session). Leaving a field blank means “keep what is there”, so these can be changed but not cleared. After picking a new avatar, make sure it finished uploading — the header avatar and the nickname here are the snapshot taken at sign-in, and only signing in again refreshes them.',
    },
  },
};

export const ja: typeof zh = {
  account: {
    units: {
      order: '件',
      address: '件',
      favorite: '件',
      browse: '件',
      coupon: '枚',
      badge: '個',
      ticket: '枚',
      pointLog: '回',
      walletLog: '回',
      message: '件',
      plaza: '件',
    },

    page: {
      title: 'マイページ',
      navAria: 'マイページ項目',
      needLogin: 'ログインするとプロフィール、クーポン、ポイント、ウォレット、注文、配送先住所、お気に入り、閲覧履歴、チェックイン履歴を確認できます。',
      signOut: 'ログアウト',
      signOutAction: 'ログアウト',
      signOutConfirm: '「{name}」からログアウトしますか？ログアウト後は再ログインしない限り注文、住所、チェックイン履歴は確認できません。',
      retry: 'もう一度',
      viewAll: 'すべて表示',
      moreUnits: 'さらに {n} {unit}',
      profileSaved: 'プロフィールを保存しました。上部のニックネームはログイン時のスナップショットのため、再ログインするまで更新されません。',
      passwordChanged: 'パスワードを変更しました。次回のログインからは新しいパスワードをご利用ください。',
    },

    menu: {
      personal: 'アカウント情報',
      orders: '注文履歴',
      tickets: 'チケット',
      addresses: '配送先住所',
      favorites: 'お気に入り',
      browses: '閲覧履歴',
      messages: 'マイメッセージ',
      base: '基本情報',
      points: 'ポイント',
      wallet: 'ウォレット',
      plaza: '広場の投稿',
    },

    info: {
      avatarLabel: 'アイコン',
      zoomAvatar: 'アイコンを拡大',
      nickname: 'ニックネーム',
      username: 'アカウント名',
      mobile: '携帯電話',
      email: 'メール',
      sex: '性別',
      accountType: 'アカウント種別',
      status: 'ステータス',
      statusEnabled: '有効',
      statusDisabled: '無効',
      area: '所在地',
      detail: '住所（詳細）',
      signature: 'ひとこと',
      roles: '役割',
      createdAt: '登録日',
      lastLogin: '最終ログイン',
      empty: 'アカウント情報を読み込めませんでした',
      signinRecords: 'チェックイン履歴',
      changePassword: 'パスワード変更',
    },

    order: {
      kindsPieces: '{kinds}種 / {pieces}点',
      pay: '支払う',
      empty: '注文はまだありません',
    },

    /** 「注文履歴」の中のもう一枚のタブ（公園チケット）。状態・種類・時間帯のラベルはバックエンドの辞書から。 */
    ticket: {
      empty: 'チケットはまだありません',
      visit: '{park} · {date} {slot}',
      parkFallback: 'パークは廃止',
      dateFallback: '日付未定',
      holder: '利用者 {name}',
      noHolder: '利用者未記入',
      viewQr: 'チケットQRコード',
      noQr: '画像を生成できていません',
    },

    address: {
      add: '住所を追加',
      defaultTag: 'デフォルト',
      noDetail: '住所未入力',
      empty: '登録した住所がありません',
      thisOne: 'この住所',
      removeTitle: '配送先住所を削除',
      removeConfirm: '「{who}」のこの配送先住所を削除しますか？',
    },

    favorite: {
      removed: '（削除済み）',
      objectFallback: '対象',
      empty: 'お気に入りはまだありません',
      /** 类型筛选那颗「全部」，以及选了某一档但一条都没有时的那句 */
      filterAll: 'すべて',
      emptyFiltered: 'この分類のお気に入りはまだありません',
      removeTitle: 'お気に入りを削除',
      removeByName: '「{name}」のお気に入りを解除しますか？',
      removeByType: 'この{what}のお気に入りを削除しますか？',
    },

    browse: {
      gone: '（商品は削除されました）',
      empty: '閲覧履歴はまだありません',
    },

    plaza: {
      empty: '広場への投稿はまだありません',
      thisOne: 'この投稿',
      removeTitle: '広場の投稿を削除',
      removeConfirm: '「{who}」を広場から削除しますか？削除すると戻せず、この投稿に付いたいいねやコメントも消えます。',
      relationFilter: '関連タイプ',
      auditFilter: '審査状態',
      clearFilters: '条件をクリア',
      emptyFiltered: 'この条件に合う広場の投稿はありません。条件を変えてお試しください',
    },

    message: {
      filterAll: 'すべて',
      filterSystem: 'システム',
      filterInteract: 'やりとり',
      markAllRead: 'すべて既読',
      allReadDone: 'すべてのメッセージを既読にしました',
      selectAll: 'このページを全選択',
      selectedCount: '{count} 件選択中',
      readSelected: '選択を既読',
      deleteSelected: '選択を削除',
      readSelectedDone: '選択した {count} 件を既読にしました',
      deleteSelectedConfirm: '選択した {count} 件を削除しますか？自分に対してのみ非表示になり、他の受け取り側には残ります。',
      delete: '削除',
      deleteConfirm: 'このメッセージを削除しますか？自分に対してのみ非表示になり、他の受け取り側には残ります。',
      removed: '削除済み',
      unread: '未読',
      read: '既読',
      empty: '受取メッセージはまだありません',
      emptyFiltered: 'このタブにメッセージはありません。条件を変えてみてください',
      failed: '操作できませんでした：{error}',
    },

    coupon: {
      statusFilter: 'ステータス',
      typeFilter: 'タイプ',
      clearFilters: '条件をクリア',
      nameFallback: 'クーポン {id}',
      validUntil: '{d} まで有効',
      received: '{d}に受け取り',
      used: '{d}に使用',
      orderNo: '注文 {no}',
      emptyFiltered: 'この条件に合うクーポンはありません。条件を変えてお試しください',
      empty: 'クーポンはまだありません',
    },

    badge: {
      pendantCount: 'バッジ {n} {unit}',
      pendantLoading: 'バッジを読み込み中…',
      pendantFailed: 'バッジを取得できませんでした（{error}）',
      nameFallback: 'バッジ {id}',
      captionText: 'バッジの文字「{text}」',
      validUntil: '{d} まで有効',
      forever: '有効期限なし',
      received: '{d}に獲得',
      empty: 'バッジはまだありません',
      hide: '非表示',
      show: '表示',
      allHidden: 'すべてのバッジを非表示にしています。「すべて表示」から元に戻せます',
      showFailed: '表示設定を変更できませんでした：{error}',
    },

    points: {
      available: '利用可能ポイント',
      totalEarned: '累計獲得',
      totalUsed: '累計使用',
      frozen: '凍結中',
      expiring: 'まもなく期限切れ',
      createdAt: '口座作成日',
      emptyAccount: 'ポイント口座はまだありません。チェックイン・注文・レビューでポイントを獲得すると作成されます',
      balanceLabel: '残',
      emptyLogs: 'ポイント履歴はまだありません',
    },

    wallet: {
      withdraw: '出金',
      recharge: 'チャージ',
      available: '利用可能額',
      frozen: '凍結中',
      gift: '贈答残高',
      totalRecharge: '累計チャージ',
      totalConsume: '累計利用',
      totalWithdraw: '累計出金',
      status: '口座ステータス',
      createdAt: '口座作成日',
      emptyAccount: 'ウォレット口座はまだありません。上の「チャージ」で初回に入金すると作成され、残高支払いや残高への返金でも作成されます',
      balanceLabel: '残',
      emptyLogs: '取引履歴はまだありません',
    },

    accountForm: {
      dialogTitle: '基本情報の編集',
      zoomAvatar: 'アイコンを拡大',
      avatarAlt: 'アイコン',
      noAvatar: 'アイコン未設定',
      avatarPick: '画像を選択',
      avatarChange: '差し替え',
      avatarUploading: 'アップロード中…',
      onlyImage: '画像ファイルのみ選択できます',
      nickname: 'ニックネーム *',
      mobile: '携帯電話',
      mobileHint: '携帯電話番号は1で始まる11桁です',
      email: 'メール',
      emailHint: 'メール形式が正しくありません。@ とドメインが必要です',
      sex: '性別',
      province: '省',
      city: '市',
      district: '区/県',
      selectPlaceholder: '選択してください',
      detailLabel: '住所（詳細）',
      detailPlaceholder: '番地・建物名（地図で地点を選ぶと自動入力され、後から編集できます）',
      longitude: '経度',
      latitude: '緯度',
      signature: 'ひとこと',
      saving: '保存中…',
      note:
        'ここで編集できるのはご自身のアカウントのこれらの項目だけで、アカウント名・アカウント種別・ステータス・役割は変更できません（サーバーが受け付けるのはこの範囲のみで、IDもログイン時のIDに固定されます）。空欄で送ると「変更なし」と扱われるため、これらの項目は書き換えられても消去はできません。アイコンを変更したときは画像のアップロード完了をご確認ください。ヘッダーのアイコンとここにあるニックネームはログイン時のスナップショットで、再ログインするまで更新されません。',
    },
  },
};

export const ko: typeof zh = {
  account: {
    units: {
      order: '건',
      address: '건',
      favorite: '건',
      browse: '건',
      coupon: '장',
      badge: '개',
      ticket: '장',
      pointLog: '건',
      walletLog: '건',
      message: '건',
      plaza: '건',
    },

    page: {
      title: '마이페이지',
      navAria: '마이페이지 메뉴',
      needLogin: '로그인하시면 프로필, 쿠폰, 포인트, 지갑, 주문 내역, 배송지, 즐겨찾기, 방문 기록, 출석 기록을 확인할 수 있습니다.',
      signOut: '로그아웃',
      signOutAction: '로그아웃',
      signOutConfirm: '"{name}" 계정에서 로그아웃하시겠습니까? 로그아웃 후에는 다시 로그인해야 주문, 주소, 출석 기록을 볼 수 있습니다.',
      retry: '재시도',
      viewAll: '전체 보기',
      moreUnits: '더 보기 {n} {unit}',
      profileSaved: '프로필을 저장했습니다. 상단의 닉네임은 로그인 시점의 스냅샷이므로, 다시 로그인해야 반영됩니다.',
      passwordChanged: '비밀번호를 변경했습니다. 다음 로그인부터 새 비밀번호를 사용해 주세요.',
    },

    menu: {
      personal: '개인 정보',
      orders: '주문 내역',
      tickets: '티켓',
      addresses: '배송지',
      favorites: '즐겨찾기',
      browses: '방문 기록',
      messages: '내 메시지',
      base: '기본 정보',
      points: '포인트',
      wallet: '지갑',
      plaza: '광장 게시물',
    },

    info: {
      avatarLabel: '프로필 사진',
      zoomAvatar: '프로필 사진 확대',
      nickname: '닉네임',
      username: '계정 이름',
      mobile: '휴대폰 번호',
      email: '이메일',
      sex: '성별',
      accountType: '계정 유형',
      status: '상태',
      statusEnabled: '사용',
      statusDisabled: '정지',
      area: '소재지',
      detail: '상세 주소',
      signature: '한 줄 소개',
      roles: '역할',
      createdAt: '가입일',
      lastLogin: '마지막 로그인',
      empty: '계정 정보를 불러올 수 없습니다',
      signinRecords: '출석 기록',
      changePassword: '비밀번호 변경',
    },

    order: {
      kindsPieces: '{kinds}종 / {pieces}건',
      pay: '결제',
      empty: '주문 내역이 없습니다',
    },

    /** 주문 내역 아래의 두 번째 탭(파크 티켓). 상태·종류·시간대 이름은 백엔드 사전에서 가져옵니다. */
    ticket: {
      empty: '받은 티켓이 없습니다',
      visit: '{park} · {date} {slot}',
      parkFallback: '파크 이용 불가',
      dateFallback: '날짜 미정',
      holder: '소지자 {name}',
      noHolder: '소지자 미작성',
      viewQr: '티켓 QR 코드',
      noQr: '이미지 미생성',
    },

    address: {
      add: '주소 추가',
      defaultTag: '기본',
      noDetail: '주소 미입력',
      empty: '저장된 배송지가 없습니다',
      thisOne: '이 주소',
      removeTitle: '배송지 삭제',
      removeConfirm: '"{who}"의 이 배송지를 삭제하시겠습니까?',
    },

    favorite: {
      removed: '(삭제됨)',
      objectFallback: '항목',
      empty: '즐겨찾기한 항목이 없습니다',
      /** 类型筛选那颗「全部」，以及选了某一档但一条都没有时的那句 */
      filterAll: '전체',
      emptyFiltered: '이 분류의 즐겨찾기가 없습니다',
      removeTitle: '즐겨찾기 삭제',
      removeByName: '"{name}"을(를) 즐겨찾기에서 삭제하시겠습니까?',
      removeByType: '이 {what} 즐겨찾기를 삭제하시겠습니까?',
    },

    browse: {
      gone: '(상품이 더 이상 없습니다)',
      empty: '방문 기록이 없습니다',
    },

    plaza: {
      empty: '아직 광장에 올린 게시물이 없습니다',
      thisOne: '이 게시물',
      removeTitle: '광장 게시물 삭제',
      removeConfirm: '「{who}」을 광장에서 삭제할까요? 삭제하면 되돌릴 수 없고 이 게시물에 받은 좋아요와 댓글도 함께 사라집니다.',
      relationFilter: '연관 유형',
      auditFilter: '심사 상태',
      clearFilters: '필터 초기화',
      emptyFiltered: '이 조건에 맞는 광장 게시물이 없습니다. 다른 필터로 다시 시도해 주세요',
    },

    message: {
      filterAll: '전체',
      filterSystem: '시스템',
      filterInteract: '상호',
      markAllRead: '모두 읽음',
      allReadDone: '모든 메시지를 읽음 처리했습니다',
      selectAll: '이 페이지 전체 선택',
      selectedCount: '{count}개 선택됨',
      readSelected: '선택 읽음 처리',
      deleteSelected: '선택 삭제',
      readSelectedDone: '선택한 {count}개를 읽음 처리했습니다',
      deleteSelectedConfirm: '선택한 {count}개를 삭제할까요? 나만 숨겨지고 다른 수신자에게는 그대로 보입니다.',
      delete: '삭제',
      deleteConfirm: '이 메시지를 삭제할까요? 나만 숨겨지고 다른 수신자에게는 그대로 보입니다.',
      removed: '삭제됨',
      unread: '안 읽음',
      read: '읽음',
      empty: '받은 메시지가 없습니다',
      emptyFiltered: '이 탭에는 메시지가 없습니다. 조건을 바꿔 보세요',
      failed: '작업을 완료하지 못했습니다: {error}',
    },

    coupon: {
      statusFilter: '상태',
      typeFilter: '유형',
      clearFilters: '필터 초기화',
      nameFallback: '쿠폰 {id}',
      validUntil: '{d}까지 사용 가능',
      received: '{d}에 수령',
      used: '{d}에 사용',
      orderNo: '주문 {no}',
      emptyFiltered: '이 조건에 맞는 쿠폰이 없습니다. 다른 필터로 다시 시도해 주세요',
      empty: '보유한 쿠폰이 없습니다',
    },

    badge: {
      pendantCount: '배지 {n} {unit}',
      pendantLoading: '배지 불러오는 중…',
      pendantFailed: '배지 불러오기 실패 ({error})',
      nameFallback: '배지 {id}',
      captionText: '배지 문구 "{text}"',
      validUntil: '{d}까지 사용 가능',
      forever: '기간 제한 없음',
      received: '{d}에 획득',
      empty: '획득한 배지가 없습니다',
      hide: '숨기기',
      show: '표시',
      allHidden: '모든 배지를 숨겼습니다. 전체 보기에서 다시 표시할 수 있습니다',
      showFailed: '표시 설정을 바꾸지 못했습니다: {error}',
    },

    points: {
      available: '사용 가능 포인트',
      totalEarned: '총 획득',
      totalUsed: '총 사용',
      frozen: '동결 중',
      expiring: '만료 임박',
      createdAt: '계정 생성일',
      emptyAccount: '포인트 계좌가 없습니다. 출석, 주문, 리뷰로 포인트를 받으면 생성됩니다',
      balanceLabel: '잔',
      emptyLogs: '포인트 사용 내역이 없습니다',
    },

    wallet: {
      withdraw: '출금',
      recharge: '충전',
      available: '사용 가능 잔액',
      frozen: '동결 중',
      gift: '증정 잔액',
      totalRecharge: '총 충전',
      totalConsume: '총 소비',
      totalWithdraw: '총 출금',
      status: '계정 상태',
      createdAt: '계정 생성일',
      emptyAccount: '지갑 계좌가 없습니다. 위의 "충전"으로 처음 입금하면 생성되고, 잔액 결제나 잔액 환불 시에도 생성됩니다',
      balanceLabel: '잔',
      emptyLogs: '지갑 거래 내역이 없습니다',
    },

    accountForm: {
      dialogTitle: '기본 정보 수정',
      zoomAvatar: '프로필 사진 확대',
      avatarAlt: '프로필 사진',
      noAvatar: '프로필 사진 없음',
      avatarPick: '이미지 선택',
      avatarChange: '변경',
      avatarUploading: '업로드 중…',
      onlyImage: '이미지 파일만 선택할 수 있습니다',
      nickname: '닉네임 *',
      mobile: '휴대폰 번호',
      mobileHint: '휴대폰 번호는 1로 시작하는 11자리입니다',
      email: '이메일',
      emailHint: '이메일 형식이 올바르지 않습니다. @와 도메인이 필요합니다',
      sex: '성별',
      province: '성',
      city: '시',
      district: '구/군',
      selectPlaceholder: '선택하세요',
      detailLabel: '상세 주소',
      detailPlaceholder: '도로·번지 (지도에서 위치를 선택하면 자동 입력되며 수정할 수 있습니다)',
      longitude: '경도',
      latitude: '위도',
      signature: '한 줄 소개',
      saving: '저장 중…',
      note:
        '여기서 수정할 수 있는 것은 본인 계정의 이 항목들뿐이며, 계정 이름·계정 유형·상태·역할은 변경할 수 없습니다 (서버가 받는 항목이 이 범위까지이고 ID도 로그인 시점의 ID로 고정됩니다). 빈 칸으로 보내면 변경 없음으로 처리되므로 이 항목들은 바꿀 수는 있어도 비울 수는 없습니다. 프로필 사진을 바꾼 후에는 이미지 업로드가 끝났는지 확인해 주세요. 상단의 사진과 여기의 닉네임은 로그인 시점의 스냅샷이므로, 다시 로그인해야 함께 바뀝니다.',
    },
  },
};
