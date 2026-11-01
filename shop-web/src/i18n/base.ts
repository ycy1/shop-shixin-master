/**
 * 全站共用的那一档文案：页头页脚、通用按钮、各页标签题、页头那颗登录/注册。
 *
 * 四档并排写：中文这份的形状就是其余三档必须对齐的标准（`en/ja/ko: typeof zh`），
 * 少一格或多一格 vue-tsc 当场报错，不用靠人记。
 * 其余按区域拆的文件（enums / shop / trade / account / ui）同一套形状，最后由 index.ts 拼成一整张表。
 */

export const zh = {
  site: {
    /** 品牌名：英文档没有现成的对应说法，就跟着站内标题一起给 */
    brand: 'Shop 商城',
    /** 页头那颗 logo 是「Shop」上色 + 后面那截不上色，所以后半单独一格 */
    brandTail: '商城',
    home: '首页',
    activity: '活动',
    /** 页头「广场」：前台用户发的动态流，游客也能逛，只有发布/点赞要登录 */
    plaza: '广场',
    /** 页头「潮玩」：专区里面另有一条左侧导航（首页/盲盒/公园），见 layouts/Trendy.astro */
    trendy: '潮玩',
    cart: '购物车',
    search: '搜索',
    /** 页头那颗铃铛（views/auth/UserAuth.vue 的快捷面板） */
    messages: '消息',
    messagesEmpty: '暂时没有消息',
    messagesAll: '查看全部消息',
    /** 铃铛下拉顶上那两颗：措辞和后台同一档（全部已读 / 全部清空），清空这一句要说清「只对你隐藏」 */
    messagesReadAll: '全部已读',
    messagesClearAll: '全部清空',
    messagesClearAllTip: '清空后这些消息只对你隐藏，其他收件人照样能看到，确定清空吗？',
    toggleTheme: '切换昼夜模式',
    backToTop: '回到顶部',
    expandMenu: '展开菜单',
    collapseMenu: '收起菜单',
    language: '切换语言',
    footer: '© {year} Shop 商城',
  },

  common: {
    loading: '加载中…',
    confirm: '确定',
    cancel: '取消',
    close: '关闭',
    save: '保存',
    delete: '删除',
    edit: '修改',
    detail: '详情',
    all: '全部',
    /** 搜索栏旁边那颗：只有关键字输入框的页面用它，页头那一颗在 site.search */
    search: '搜索',
    empty: '这里暂时是空的',
    loadFailed: '加载失败',
    /** 空列占位：后端实体是 NON_NULL，没填的列干脆不在 JSON 里，两头都画这一颗 */
    dash: '—',
    /** 「共 N 枚」这类右上角条数：量词由调用方那一格给（各块不一样，别在这颗里写死） */
    total: '共 {n} {unit}',
    more: '再看 {n} 条',
    yes: '是',
    no: '否',
  },

  /** 各页的浏览器标签题 + SEO 那格 description（在 pages/*.astro 的 frontmatter 里取） */
  page: {
    home: { title: 'Shop 商城', desc: 'Astro + Vue + Flowbite 商城前台' },
    activity: { title: '活动', desc: '正在进行中的促销活动' },
    activityDetail: { title: '活动详情', desc: '活动的内容、图文和跳转' },
    brand: { title: '品牌详情', desc: '品牌的介绍、门店、品牌代言和商品' },
    cart: { title: '我的购物车 - {brand}', desc: '购物车里的规格、数量与合计' },
    checkout: { title: '确认订单 - {brand}', desc: '收货地址、优惠券、积分与提交订单' },
    product: { title: '商品详情', desc: '商品的规格、价格与图文' },
    search: { title: '搜索', titleKw: '搜索 {kw}', desc: '按关键字找商品、门店、品牌' },
    store: { title: '门店详情', desc: '门店的图片、信息和本店商品' },
    comments: { title: '全部评论 - {brand}', desc: '这个对象名下的全部评论', back: '返回详情页' },
    notice: { title: '公告 - {brand}', desc: '公告的全文' },
    plaza: { title: '广场', desc: '大家发的动态，图文都在这一条流里' },
    plazaDetail: { title: '广场消息', desc: '一条广场消息的图文和它名下的评论' },
    /** 潮玩专区三档（左侧导航 首页/盲盒/公园），文案在 trendy.ts */
    trendy: { title: '潮玩专区', desc: '盲盒、潮玩商品和主题公园' },
    trendyBox: { title: '潮玩 · 盲盒', desc: '正在开的盲盒和每台盒的款式' },
    trendyCollection: { title: '潮玩 · 图鉴', desc: '所有潮玩系列、你的收集进度和集齐奖励' },
    trendyPark: { title: '潮玩 · 公园', desc: '营业中的主题公园和名下的票种' },
    user: { title: '个人中心 - {brand}', desc: '账号信息、订单、地址、优惠券、积分与钱包' },
  },

  /** 页头那颗登录/注册（views/auth/UserAuth.vue） */
  auth: {
    entry: '登录 / 注册',
    dialog: '登录 / 注册',
    login: '登录',
    register: '注册',
    loggingIn: '登录中…',
    submitting: '提交中…',
    username: '用户名',
    password: '密码',
    email: '邮箱',
    code: '邮箱验证码',
    sendCode: '获取验证码',
    /** 倒计时那一格：60 → 60s 两边写法一致，留一格是为了以后要改成「60 秒后重发」只动字典 */
    countdown: '{n}s',
    codeSent: '验证码已发到邮箱，1 分钟内有效',
    registered: '注册成功，用刚才的账号密码登录',
    needEmail: '先填邮箱',
    me: '我',
    signedInAria: '进入个人中心，当前登录：{name}',
    /** 页头那颗未读红点的读法：只有数、没有文字，靠这句念出来 */
    unreadAria: '{n} 条未读消息',
  },

  /**
   * 活动那一行的说法：lib/activities.ts 的 activityLabel() / activityTimeText() 读这一格，
   * 首页轮播、活动列表、活动详情、搜索页那个 tab 四处共用（所以放在共用档，不跟某一块页面走）。
   */
  activity: {
    /** 标题和活动名称都没填时那颗兜底名 */
    fallback: '活动 {n}',
    between: '{a} 至 {b}',
    from: '{a} 开始',
    to: '截至 {b}',
  },

  /**
   * 一张券的优惠说成人话：lib/profile.ts 的 couponBenefit() 读这一格。
   * 购物车/结算/个人中心三处都要念同一句，所以放在共用档里，不进任何一区的文件。
   */
  benefit: {
    freeShipping: '免配送费',
    /** 折扣：中文说「9 折」，英文说「10% off」，同一张券两个数 —— 换算在 couponBenefit() 里按语种分一次 */
    discount: '{n}折',
    cap: '（最高抵 {n} 元）',
    threshold: '满 {a} 减 {b}',
    noThreshold: '无门槛减 {n}',
  },
};

export const en: typeof zh = {
  site: {
    brand: 'Shop Store',
    brandTail: 'Store',
    home: 'Home',
    activity: 'Campaigns',
    plaza: 'Plaza',
    trendy: 'Trendy',
    cart: 'Cart',
    search: 'Search',
    messages: 'Messages',
    messagesEmpty: 'No messages yet',
    messagesAll: 'View all messages',
    messagesReadAll: 'Mark all read',
    messagesClearAll: 'Clear all',
    messagesClearAllTip: 'Clearing hides these messages from you only — other recipients still see them. Clear them all?',
    toggleTheme: 'Toggle theme',
    backToTop: 'Back to top',
    expandMenu: 'Open menu',
    collapseMenu: 'Close menu',
    language: 'Change language',
    footer: '© {year} Shop Store',
  },

  common: {
    loading: 'Loading…',
    confirm: 'Confirm',
    cancel: 'Cancel',
    close: 'Close',
    save: 'Save',
    delete: 'Delete',
    edit: 'Edit',
    detail: 'Details',
    all: 'All',
    search: 'Search',
    empty: 'Nothing here yet',
    loadFailed: 'Failed to load',
    dash: '—',
    total: '{n} {unit}',
    more: 'Show {n} more',
    yes: 'Yes',
    no: 'No',
  },

  page: {
    home: { title: 'Shop Store', desc: 'Astro + Vue + Flowbite storefront' },
    activity: { title: 'Campaigns', desc: 'Current promotions and campaigns' },
    activityDetail: { title: 'Campaign detail', desc: 'Campaign content, images and links' },
    brand: { title: 'Brand detail', desc: 'Brand profile, stores, ambassadors and products' },
    cart: { title: 'My cart - {brand}', desc: 'Variants, quantities and totals in your cart' },
    checkout: { title: 'Checkout - {brand}', desc: 'Delivery address, coupons, points and placing the order' },
    product: { title: 'Product detail', desc: 'Variants, pricing and product description' },
    search: { title: 'Search', titleKw: 'Search “{kw}”', desc: 'Find products, stores and brands by keyword' },
    store: { title: 'Store detail', desc: 'Store photos, information and its products' },
    comments: { title: 'All reviews - {brand}', desc: 'Every review posted for this item', back: 'Back to details' },
    notice: { title: 'Notice - {brand}', desc: 'Full text of the notice' },
    plaza: { title: 'Plaza', desc: 'Posts from other shoppers, text and images' },
    plazaDetail: { title: 'Plaza post', desc: 'One plaza post with its images and comments' },
    trendy: { title: 'Trendy toys', desc: 'Blind boxes, trendy products and theme parks' },
    trendyBox: { title: 'Trendy · Blind boxes', desc: 'Boxes running now and the styles inside each' },
    trendyCollection: { title: 'Trendy · Collection', desc: 'Every series, your progress and the completion rewards' },
    trendyPark: { title: 'Trendy · Theme parks', desc: 'Open parks and the tickets they sell' },
    user: { title: 'My account - {brand}', desc: 'Profile, orders, addresses, coupons, points and wallet' },
  },

  auth: {
    entry: 'Sign in / Sign up',
    dialog: 'Sign in / Sign up',
    login: 'Sign in',
    register: 'Sign up',
    loggingIn: 'Signing in…',
    submitting: 'Submitting…',
    username: 'Username',
    password: 'Password',
    email: 'Email',
    code: 'Email code',
    sendCode: 'Send code',
    countdown: '{n}s',
    codeSent: 'Code sent to your email, valid for 1 minute',
    registered: 'Account created — sign in with the username and password you just used',
    needEmail: 'Enter an email first',
    me: 'me',
    signedInAria: 'Open my account, signed in as {name}',
    /** 页头那颗未读红点的读法：只有数、没有文字，靠这句念出来 */
    unreadAria: '{n} unread messages',
  },

  activity: {
    fallback: 'Campaign {n}',
    between: '{a} to {b}',
    from: 'From {a}',
    to: 'Until {b}',
  },

  benefit: {
    freeShipping: 'Free delivery',
    discount: '{n}% off',
    cap: ' (up to {n} off)',
    threshold: '{b} off orders over {a}',
    noThreshold: '{n} off, no minimum',
  },
};

export const ja: typeof zh = {
  site: {
    brand: 'Shop ストア',
    brandTail: 'ストア',
    home: 'ホーム',
    activity: 'キャンペーン',
    plaza: '広場',
    trendy: 'トレンドトイ',
    cart: 'カート',
    search: '検索',
    messages: 'メッセージ',
    messagesEmpty: 'メッセージはまだありません',
    messagesAll: 'メッセージをすべて見る',
    messagesReadAll: 'すべて既読にする',
    messagesClearAll: 'すべて消去',
    messagesClearAllTip: '消去すると自分には見えなくなりますが、他の受信者には引き続き表示されます。すべて消去しますか？',
    toggleTheme: 'テーマ切替',
    backToTop: 'トップへ戻る',
    expandMenu: 'メニューを開く',
    collapseMenu: 'メニューを閉じる',
    language: '言語切替',
    footer: '© {year} Shop ストア',
  },

  common: {
    loading: '読み込み中…',
    confirm: '確定',
    cancel: 'キャンセル',
    close: '閉じる',
    save: '保存',
    delete: '削除',
    edit: '編集',
    detail: '詳細',
    all: 'すべて',
    search: '検索',
    empty: 'まだ何もありません',
    loadFailed: '読み込みに失敗しました',
    dash: '—',
    total: '計 {n} {unit}',
    more: 'さらに {n} 件',
    yes: 'はい',
    no: 'いいえ',
  },

  page: {
    home: { title: 'Shop ストア', desc: 'Astro + Vue + Flowbite のショップフロント' },
    activity: { title: 'キャンペーン', desc: '開催中のプロモーション・キャンペーン' },
    activityDetail: { title: 'キャンペーン詳細', desc: 'キャンペーンの内容、画像とリンク' },
    brand: { title: 'ブランド詳細', desc: 'ブランド紹介、店舗、アンバサダー、商品' },
    cart: { title: 'マイカート - {brand}', desc: 'カート内のスペック、数量、合計' },
    checkout: { title: '注文確認 - {brand}', desc: '配送先住所、クーポン、ポイントと注文確定' },
    product: { title: '商品詳細', desc: '商品のスペック、価格と説明' },
    search: { title: '検索', titleKw: '「{kw}」を検索', desc: 'キーワードで商品・店舗・ブランドを検索' },
    store: { title: '店舗詳細', desc: '店舗の写真、情報、取り扱い商品' },
    comments: { title: 'すべてのレビュー - {brand}', desc: 'この対象に付いたすべてのレビュー', back: '詳細に戻る' },
    notice: { title: 'お知らせ - {brand}', desc: 'お知らせの全文' },
    plaza: { title: '広場', desc: '利用者が投稿した画像とテキストのフィード' },
    plazaDetail: { title: '広場の投稿', desc: '1件の投稿の本文とコメント' },
    trendy: { title: 'トレンドトイ特集', desc: 'ブラインドボックス、トレンド商品、テーマパーク' },
    trendyBox: { title: 'トレンド · ブラインドボックス', desc: '開催中のボックスと各箱のラインナップ' },
    trendyCollection: { title: 'トレンド · 図鑑', desc: '全シリーズと収集進捗、コンプリート報酬' },
    trendyPark: { title: 'トレンド · テーマパーク', desc: '営業中のパークと発売中のチケット' },
    user: { title: 'マイページ - {brand}', desc: 'アカウント情報、注文、住所、クーポン、ポイント、ウォレット' },
  },

  auth: {
    entry: 'ログイン / 新規登録',
    dialog: 'ログイン / 新規登録',
    login: 'ログイン',
    register: '新規登録',
    loggingIn: 'ログイン中…',
    submitting: '送信中…',
    username: 'ユーザー名',
    password: 'パスワード',
    email: 'メール',
    code: 'メール認証コード',
    sendCode: 'コードを取得',
    countdown: '{n}s',
    codeSent: '認証コードをメールに送信しました。有効期限は 1 分です',
    registered: '登録が完了しました。登録したユーザー名とパスワードでログインしてください',
    needEmail: '先にメールを入力してください',
    me: '自分',
    signedInAria: 'マイページへ、ログイン中：{name}',
    unreadAria: '未読 {n} 件',
  },

  activity: {
    fallback: 'キャンペーン {n}',
    between: '{a} ～ {b}',
    from: '{a} から',
    to: '{b} まで',
  },

  benefit: {
    freeShipping: '送料無料',
    discount: '{n}% オフ',
    cap: '（最大{n}元）',
    threshold: '{a}以上で{b}割引',
    noThreshold: '{n}割引（条件なし）',
  },
};

export const ko: typeof zh = {
  site: {
    brand: 'Shop 스토어',
    brandTail: '스토어',
    home: '홈',
    activity: '캠페인',
    plaza: '광장',
    trendy: '트렌디 피규어',
    cart: '장바구니',
    search: '검색',
    messages: '메시지',
    messagesEmpty: '메시지가 없습니다',
    messagesAll: '메시지 모두 보기',
    messagesReadAll: '모두 읽음 처리',
    messagesClearAll: '모두 비우기',
    messagesClearAllTip: '비우면 나에게만 숨겨지고 다른 수신자에게는 그대로 보입니다. 모두 비울까요?',
    toggleTheme: '테마 전환',
    backToTop: '맨 위로',
    expandMenu: '메뉴 열기',
    collapseMenu: '메뉴 닫기',
    language: '언어 전환',
    footer: '© {year} Shop 스토어',
  },

  common: {
    loading: '로딩 중…',
    confirm: '확인',
    cancel: '취소',
    close: '닫기',
    save: '저장',
    delete: '삭제',
    edit: '수정',
    detail: '상세',
    all: '전체',
    search: '검색',
    empty: '아직 내용이 없습니다',
    loadFailed: '불러오기 실패',
    dash: '—',
    total: '총 {n} {unit}',
    more: '더 보기 {n}건',
    yes: '예',
    no: '아니요',
  },

  page: {
    home: { title: 'Shop 스토어', desc: 'Astro + Vue + Flowbite 쇼핑몰 프런트' },
    activity: { title: '캠페인', desc: '진행 중인 프로모션 및 캠페인' },
    activityDetail: { title: '캠페인 상세', desc: '캠페인 내용, 이미지 및 링크' },
    brand: { title: '브랜드 상세', desc: '브랜드 소개, 매장, 앰배서더, 상품' },
    cart: { title: '내 장바구니 - {brand}', desc: '장바구니의 사양, 수량, 합계' },
    checkout: { title: '주문 확인 - {brand}', desc: '배송지, 쿠폰, 포인트 및 주문 제출' },
    product: { title: '상품 상세', desc: '상품 사양, 가격 및 설명' },
    search: { title: '검색', titleKw: '“{kw}” 검색', desc: '키워드로 상품·매장·브랜드 검색' },
    store: { title: '매장 상세', desc: '매장 사진, 정보, 매장 상품' },
    comments: { title: '모든 리뷰 - {brand}', desc: '이 대상에 등록된 모든 리뷰', back: '상세로 돌아가기' },
    notice: { title: '공지 - {brand}', desc: '공지 전문' },
    plaza: { title: '광장', desc: '다른 구매자들이 올린 글과 사진' },
    plazaDetail: { title: '광장 게시물', desc: '게시물 하나의 본문과 댓글' },
    trendy: { title: '트렌디 피규어관', desc: '블라인드 박스, 트렌디 상품, 테마 파크' },
    trendyBox: { title: '트렌디 · 블라인드 박스', desc: '진행 중인 박스와 각 박스의 라인업' },
    trendyCollection: { title: '트렌디 · 도감', desc: '모든 시리즈와 수집 진행도, 완결 보상' },
    trendyPark: { title: '트렌디 · 테마 파크', desc: '영업 중인 파크와 판매 중인 티켓' },
    user: { title: '마이페이지 - {brand}', desc: '계정 정보, 주문, 주소, 쿠폰, 포인트, 지갑' },
  },

  auth: {
    entry: '로그인 / 회원가입',
    dialog: '로그인 / 회원가입',
    login: '로그인',
    register: '회원가입',
    loggingIn: '로그인 중…',
    submitting: '제출 중…',
    username: '사용자 이름',
    password: '비밀번호',
    email: '이메일',
    code: '이메일 인증 코드',
    sendCode: '코드 받기',
    countdown: '{n}s',
    codeSent: '인증 코드가 이메일로 발송되었습니다. 유효 기간은 1분입니다',
    registered: '가입이 완료되었습니다. 방금 등록한 계정과 비밀번호로 로그인해 주세요',
    needEmail: '이메일을 먼저 입력해 주세요',
    me: '나',
    signedInAria: '마이페이지 열기, 로그인 계정: {name}',
    unreadAria: '읽지 않은 메시지 {n}건',
  },

  activity: {
    fallback: '캠페인 {n}',
    between: '{a} ~ {b}',
    from: '{a} 시작',
    to: '{b} 종료',
  },

  benefit: {
    freeShipping: '배송비 무료',
    discount: '{n}% 할인',
    cap: '(최대 {n}위안)',
    threshold: '{a}위안 이상 {b}위안 할인',
    noThreshold: '조건 없이 {n}위안 할인',
  },
};
