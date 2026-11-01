/**
 * 详情页那一档文案：商品详情、门店详情、品牌详情、活动列表/详情和那两组轮播图。
 * （views/product/ProductDetail.vue、views/store/StoreDetail.vue、views/store/StoreCarousel.vue、
 *  views/brand/BrandDetail.vue、views/activity/ActivityList.vue、views/activity/ActivityDetail.vue）
 *
 * 形状标准是中文这份，其余三档各自钉成它的类型（`en/ja/ko: typeof zh`）⇒ 少一格 vue-tsc 当场报错。四档并排放，改一句就在下面几行内。
 * 全站共用的称呼（页头、通用按钮、枚举档位、活动那行日期）在 base.ts / enums.ts，这一档只收本区自己的话。
 *
 * namespace 的名字有两个坑，别撞：
 * ① index.ts 把这份在 baseZh **后面**展开，所以这一档不许再写顶层 `activity` 或 `common` ——
 *    写一次就把 base 那一整格顶掉（活动日期那行 d.activity.between 和 d.common.loading 全站都在用）。
 *    所以活动两块各叫自己那一名：`activityList`、`activityDetail`。
 * ② `product` / `store` / `brand` 这三格 base 只在 d.page.* 和 d.enum.* 底下各挂了一份，顶层是空的，本区用着安全。
 */

export const zh = {
  /**
   * 本区几块页面共用的一句。
   * 「重试」在门店/品牌/活动三块列表的报错行里都要用，各写一份就是同一句话抄三遍；
   * 但它又不属于 base.ts（那一档已经钉死不许动），所以留在这一区的顶上。
   */
  detailCommon: {
    retry: '重试',
  },

  /** 商品详情页（views/product/ProductDetail.vue）：规格选择、加购、详情/评论两个 tab、放大镜 */
  product: {
    backToList: '回商品列表',
    noImage: '暂无图片',

    /** 名称下面那几行次要信息：标签连着中文自己那个冒号一起走（英文档给半角冒号 + 一个空格），模板只管拼值 */
    brand: '品牌：',
    category: '分类：',
    store: '来源门店：',
    code: '商品编号：',

    /** 带数的三行：已售 / 本规格库存 / 赠送积分，都跟着选中的那一档规格走 */
    sold: '已售 {n}',
    stock: '本规格库存 {n}',
    gainPoints: '购买送 {n} 积分',
    outOfStock: '暂时缺货',

    /** 规格那一排 */
    selectSpec: '选择规格',
    /** 后台没填 spec_text 的那条（无规格的默认SKU），页面补的说法 */
    defaultSpec: '默认规格',
    /** 加购按钮上「还没定下规格」那一档的说话 */
    noSpecAvailable: '暂无可选规格',

    /** 加购那一颗的四种状态 */
    addToCart: '加入购物车',
    loginToAddToCart: '登录后加入购物车',
    adding: '加入中…',
    /** 成功那一句的前半截 + 后半截那颗去结算的链接 */
    addedToCart: '已加入购物车 ·',
    goToCart: '去购物车结算',
    buyNow: '立即购买',

    /** 下面两个 tab、tablist 的 aria-label，和 tab 里那三节的小标题 */
    tabDetail: '商品详情',
    tabComment: '评论',
    infoAria: '商品信息',
    description: '商品描述',
    specs: '商品规格',
    imageDetail: '图文详情',
    /** 详情图那张的 alt：商品名和序号是数据，中间那个称呼跟着语种走 */
    detailImageAlt: '{name} 详情图 {n}',

    noInfo: '暂无商品信息',
  },

  /** 门店详情页 + 那一圈门店轮播图（views/store/StoreDetail.vue、views/store/StoreCarousel.vue） */
  store: {
    backToList: '回门店列表',
    /** 地址/电话/营业时间那三行的标签：同上，连着本语种的冒号一起给（英文档尾上留一个空格） */
    address: '地址：',
    phone: '电话：',
    businessHours: '营业时间：',
    /** 名称下面那枚小徽标 */
    delivery: '支持配送',

    /** 商品那一块的两档 tab + tablist 的 aria-label + 两档各自的空列说法 */
    productsAria: '本店商品',
    storeProducts: '门店商品',
    culturalProducts: '文创商品',
    noProducts: '这家店还没有上架的商品',
    noCulturalProducts: '这家店还没有上架的文创商品',
    /** 「共 N 件」那个量词，跟着 d.common.total 的 {unit} 槽走 */
    countUnit: '件',

    /** 门店那圈平铺轮播（图 + 指示点 + 左右两颗） */
    carousel: {
      imageAlt: '{name} 图 {n}',
      gotoSlide: '看第 {n} 张',
      prev: '上一张',
      next: '下一张',
    },

    /** 底下那一排第三档 tab（2026-10-19 评论从章节搬成 tab，和商品那两档并排）；商品详情页那两档用 d.product.tabComment */
    comments: '门店评价',
  },

  /** 品牌详情页（views/brand/BrandDetail.vue）：头部介绍 → 品牌门店 → 品牌代言 → 品牌商品 */
  brand: {
    backToList: '回品牌列表',
    stores: '品牌门店',
    ambassadors: '品牌代言',
    products: '品牌商品',

    noStores: '这个品牌还没有绑定的门店',
    noProducts: '这个品牌还没有上架的商品',
    /** 门店行没填地址/区域时那一句 */
    noAddress: '暂无地址',

    /** 代言人图那一墙：没填名字的图用的兜底称呼，和放大那颗的 aria-label 共用它 */
    spokespersonImage: '品牌代言图片',
    zoomAria: '放大{name}',

    /** 底下那一排第二档 tab（2026-10-19 评论从章节搬成 tab，和品牌商品并排） */
    comments: '品牌评价',

    countUnit: '件',
  },

  /** 评论区（components/CommentSection.vue）：商品/门店/品牌/广场四处详情页底下那一大块 */
  comment: {
    /** 排序两档（时间 / 点赞）和那一排 tab 的 aria-label；置顶那几条由服务端打头，这两档只排其余的 */
    sortAria: '评论排序',
    sortTime: '最新',
    sortLike: '最热',
    /** 「共 N 条」那个量词，跟着 d.common.total 的 {unit} 槽走 */
    countUnit: '条',
    empty: '还没有评论，来说两句吧',
    /** 详情页那一档只铺最热 5 条：左边这一句顶掉排序那排，底下这颗跳到「全部评论」那一页 */
    hot: '热门评论',
    viewAll: '查看全部 {n} 条评论',

    placeholder: '说说你的想法…',
    limit: '还能写 {n} 字',
    publish: '发表',
    publishing: '发表中…',
    loginToComment: '登录后发表评论',

    /** 表情面板那颗开关和面板本身的 aria-label：emoji 直接进正文，不是一格一格的代码 */
    emoji: '表情',
    emojiAria: '插入表情',
    /** 选图那颗：点开的是系统文件选择器，图先传上去拿回 URL，「发表」那一条只收地址 */
    image: '图片',
    imageUploading: '图片上传中…',
    /** 九宫格底下那句「已选 N / 上限 9 张」 */
    imageCount: '{n}/{max} 张',
    imageRemove: '移除这张图',
    /** 一张评论图都有的 alt；点开看大图那一层的左右两颗用 d.preview.prev / next */
    imageAlt: '评论图片',
    /** 超了张数、传失败了都是这一句级别的话，后端那句原样填进 {msg} */
    imageTooMany: '一条评论最多带 {n} 张图',
    imageFailed: '图片没传上去：{msg}',

    reply: '回复',
    replyingTo: '回复 @{name}',
    cancelReply: '取消',
    /** 点赞那颗的两态：数打在按钮后面，isLike 决定空心还是实心 */
    like: '点赞',
    liked: '已赞',
    /** 只有自己写的那一条才有这颗 */
    delete: '删除',
    deleteTitle: '删除评论',
    deleteConfirm: '删掉后这条评论和它名下的回复都不再显示，确定要删吗？',

    pinned: '置顶',
    anonymous: '匿名用户',
    /** 归属地只到省市，读 sys_comment.ip_source 那一格；访客 IP 本身不给前台 */
    ipLocation: 'IP属地：{place}',

    /** 三条失败话：后端那句原样填进 {msg}，不翻译 */
    loadFailed: '评论没Load出来：{msg}',
    postFailed: '没发出去：{msg}',
    actionFailed: '操作没成功：{msg}',
  },

  /** 活动列表页（views/activity/ActivityList.vue）；标题用 d.site.activity，日期那行用 d.activity.* */
  activityList: {
    subtitle: '正在进行的促销活动，点一张看详情',
    empty: '暂时没有进行中的活动',
    /** 续页失败那一行：后端那句错误原样填进 {msg}，不翻译 */
    moreFailed: '后面这一页没Load出来：{msg}',
    countUnit: '条',
  },

  /** 活动详情页（views/activity/ActivityDetail.vue）：跳转那颗按钮的说法和没内容那一句 */
  activityDetail: {
    /** 报错行里那颗回列表的链接，和页面顶上那颗同义但形状不一样，各留一格 */
    backToList: '回活动列表',
    listLink: '活动列表',
    openLink: '打开活动链接',
    viewCategory: '看这个分类的商品',
    look: '去看看',
    noContent: '这条活动还没写内容',
  },

  /** 公告详情页（views/notice/NoticeDetail.vue）：公告只从消息中心那一条链进来，所以回去的那颗也指消息中心 */
  noticeDetail: {
    backToList: '回消息中心',
    listLink: '消息中心',
    noContent: '这条公告还没写正文',
  },
};

export const en: typeof zh = {
  detailCommon: {
    retry: 'Retry',
  },

  product: {
    backToList: 'Back to all products',
    noImage: 'No photo yet',

    brand: 'Brand: ',
    category: 'Category: ',
    store: 'Store: ',
    code: 'Item no.: ',

    sold: '{n} sold',
    stock: '{n} in stock',
    gainPoints: 'Earn {n} points',
    outOfStock: 'Out of stock',

    selectSpec: 'Select variant',
    defaultSpec: 'Standard',
    noSpecAvailable: 'No options available',

    addToCart: 'Add to cart',
    loginToAddToCart: 'Sign in to add to cart',
    adding: 'Adding…',
    addedToCart: 'Added to cart ·',
    goToCart: 'Go to cart',
    buyNow: 'Buy now',

    tabDetail: 'Product details',
    tabComment: 'Reviews',
    infoAria: 'Product information',
    description: 'Description',
    specs: 'Specifications',
    imageDetail: 'Photo details',
    detailImageAlt: '{name} photo {n}',

    noInfo: 'No product details yet',
  },

  store: {
    backToList: 'Back to stores',
    address: 'Address: ',
    phone: 'Phone: ',
    businessHours: 'Hours: ',
    delivery: 'Delivery available',

    productsAria: 'Products in this store',
    storeProducts: 'Store products',
    culturalProducts: 'Creative merch',
    noProducts: 'This store has nothing on sale yet',
    noCulturalProducts: 'This store has no creative merch on sale yet',
    countUnit: 'products',

    carousel: {
      imageAlt: '{name} photo {n}',
      gotoSlide: 'Go to photo {n}',
      prev: 'Previous photo',
      next: 'Next photo',
    },

    comments: 'Store reviews',
  },

  brand: {
    backToList: 'Back to brands',
    stores: 'Brand stores',
    ambassadors: 'Brand ambassadors',
    products: 'Brand products',

    noStores: 'No stores are linked to this brand yet',
    noProducts: 'This brand has nothing on sale yet',
    noAddress: 'No address set',

    spokespersonImage: 'brand ambassador photo',
    zoomAria: 'Zoom in on {name}',

    comments: 'Brand reviews',

    countUnit: 'products',
  },

  comment: {
    sortAria: 'Sort reviews',
    sortTime: 'Latest',
    sortLike: 'Most liked',
    countUnit: 'reviews',
    empty: 'No reviews yet — say the first word',
    hot: 'Popular reviews',
    viewAll: 'See all {n} reviews',

    placeholder: 'Tell us what you think…',
    limit: '{n} characters left',
    publish: 'Post',
    publishing: 'Posting…',
    loginToComment: 'Sign in to post a review',

    emoji: 'Emoji',
    emojiAria: 'Insert emoji',
    image: 'Photo',
    imageUploading: 'Uploading…',
    imageCount: '{n}/{max} photos',
    imageRemove: 'Remove this photo',
    imageAlt: 'Review photo',
    imageTooMany: 'A review can carry at most {n} photos',
    imageFailed: 'Upload failed: {msg}',

    reply: 'Reply',
    replyingTo: 'Replying to @{name}',
    cancelReply: 'Cancel',
    like: 'Like',
    liked: 'Liked',
    delete: 'Delete',
    deleteTitle: 'Delete review',
    deleteConfirm: 'This review and its replies will no longer be shown. Delete it?',

    pinned: 'Pinned',
    anonymous: 'Anonymous',
    ipLocation: 'IP region: {place}',

    loadFailed: 'Could not load the reviews: {msg}',
    postFailed: 'Could not post: {msg}',
    actionFailed: 'That did not work: {msg}',
  },

  activityList: {
    subtitle: 'Current promotions — pick one for the details',
    empty: 'No campaigns are running right now',
    moreFailed: 'Could not load the next page: {msg}',
    countUnit: 'campaigns',
  },

  activityDetail: {
    backToList: 'Back to campaigns',
    listLink: 'Campaign list',
    openLink: 'Open campaign link',
    viewCategory: 'Browse this category',
    look: 'Take a look',
    noContent: 'This campaign has no content yet',
  },

  noticeDetail: {
    backToList: 'Back to messages',
    listLink: 'Messages',
    noContent: 'This notice has no body yet',
  },
};

export const ja: typeof zh = {
  detailCommon: {
    retry: '再試行',
  },

  product: {
    backToList: '商品一覧に戻る',
    noImage: '画像なし',

    brand: 'ブランド：',
    category: 'カテゴリ：',
    store: '来源店舗：',
    code: '商品番号：',

    sold: '販売実績 {n}件',
    stock: 'この仕様の在庫 {n}点',
    gainPoints: '購入で{n}ポイント',
    outOfStock: '在庫切れ',

    selectSpec: '仕様を選択',
    defaultSpec: 'デフォルト仕様',
    noSpecAvailable: '選択可能な仕様がありません',

    addToCart: 'カートに追加',
    loginToAddToCart: 'ログインするとカートに追加できます',
    adding: '追加中…',
    addedToCart: 'カートに追加しました ·',
    goToCart: 'カートへ進む',
    buyNow: '今すぐ購入',

    tabDetail: '商品詳細',
    tabComment: 'レビュー',
    infoAria: '商品情報',
    description: '商品説明',
    specs: '商品スペック',
    imageDetail: '詳細画像',
    detailImageAlt: '{name} 詳細画像 {n}',

    noInfo: '商品情報はありません',
  },

  store: {
    backToList: '店舗一覧に戻る',
    address: '住所：',
    phone: '電話：',
    businessHours: '営業時間：',
    delivery: '配送対応',

    productsAria: 'この店舗の商品',
    storeProducts: '店舗商品',
    culturalProducts: 'クリエイティブ商品',
    noProducts: 'この店舗に販売中の商品はありません',
    noCulturalProducts: 'この店舗に販売中のクリエイティブ商品はありません',
    countUnit: '件',

    carousel: {
      imageAlt: '{name} 画像 {n}',
      gotoSlide: '{n}枚目を見る',
      prev: '前へ',
      next: '次へ',
    },

    comments: '店舗レビュー',
  },

  brand: {
    backToList: 'ブランド一覧に戻る',
    stores: '店舗一覧',
    ambassadors: 'ブランドアンバサダー',
    products: 'ブランド商品',

    noStores: 'このブランドに紐付けられた店舗はありません',
    noProducts: 'このブランドに販売中の商品はありません',
    noAddress: '住所未設定',

    spokespersonImage: 'ブランドアンバサダー画像',
    zoomAria: '{name}を拡大',

    comments: 'ブランドレビュー',

    countUnit: '件',
  },

  comment: {
    sortAria: 'レビューの並び替え',
    sortTime: '新着',
    sortLike: 'いいね順',
    countUnit: '件',
    empty: 'まだレビューがありません。最初の1件を書き込みましょう',
    hot: '人気のレビュー',
    viewAll: 'すべてのレビュー{n}件を見る',

    placeholder: '感想を記入…',
    limit: 'あと{n}文字',
    publish: '投稿',
    publishing: '投稿中…',
    loginToComment: 'ログインしてレビューを投稿',

    emoji: '絵文字',
    emojiAria: '絵文字を挿入',
    image: '写真',
    imageUploading: 'アップロード中…',
    imageCount: '写真 {n}/{max}',
    imageRemove: 'この写真を削除',
    imageAlt: 'レビューの写真',
    imageTooMany: '1件のレビューには最大{n}枚まで添付できます',
    imageFailed: '写真をアップロードできませんでした：{msg}',

    reply: '返信',
    replyingTo: '@{name} への返信',
    cancelReply: 'キャンセル',
    like: 'いいね',
    liked: 'いいね済み',
    delete: '削除',
    deleteTitle: 'レビューを削除',
    deleteConfirm: 'このレビューとその返信は表示されなくなります。削除しますか？',

    pinned: '固定',
    anonymous: '名無し',
    ipLocation: 'IP所在地：{place}',

    loadFailed: 'レビューを読み込めませんでした：{msg}',
    postFailed: '投稿できませんでした：{msg}',
    actionFailed: '操作に失敗しました：{msg}',
  },

  activityList: {
    subtitle: '開催中のキャンペーン、クリックして詳細を見る',
    empty: '現在開催中のキャンペーンはありません',
    moreFailed: '次のページを読み込めませんでした：{msg}',
    countUnit: '件',
  },

  activityDetail: {
    backToList: 'キャンペーン一覧に戻る',
    listLink: 'キャンペーン一覧',
    openLink: 'キャンペーンリンクを開く',
    viewCategory: 'このカテゴリの商品を見る',
    look: '見に行く',
    noContent: 'このキャンペーンにはまだ内容がありません',
  },

  noticeDetail: {
    backToList: 'メッセージへ戻る',
    listLink: 'メッセージ',
    noContent: 'このお知らせにはまだ本文がありません',
  },
};

export const ko: typeof zh = {
  detailCommon: {
    retry: '재시도',
  },

  product: {
    backToList: '상품 목록으로',
    noImage: '이미지 없음',

    brand: '브랜드: ',
    category: '분류: ',
    store: '매장: ',
    code: '상품 번호: ',

    sold: '판매 {n}건',
    stock: '해당 사양 재고 {n}개',
    gainPoints: '구매 시 {n}포인트',
    outOfStock: '품절',

    selectSpec: '사양 선택',
    defaultSpec: '기본 사양',
    noSpecAvailable: '선택 가능한 사양이 없습니다',

    addToCart: '장바구니에 담기',
    loginToAddToCart: '로그인 후 장바구니에 담을 수 있습니다',
    adding: '추가 중…',
    addedToCart: '장바구니에 담았습니다 ·',
    goToCart: '장바구니로 이동',
    buyNow: '바로 구매',

    tabDetail: '상품 상세',
    tabComment: '리뷰',
    infoAria: '상품 정보',
    description: '상품 설명',
    specs: '상품 사양',
    imageDetail: '상세 이미지',
    detailImageAlt: '{name} 상세 이미지 {n}',

    noInfo: '상품 정보가 없습니다',
  },

  store: {
    backToList: '매장 목록으로',
    address: '주소: ',
    phone: '전화: ',
    businessHours: '영업 시간: ',
    delivery: '배송 가능',

    productsAria: '이 매장 상품',
    storeProducts: '매장 상품',
    culturalProducts: '문화 창작 상품',
    noProducts: '이 매장에 판매 중인 상품이 없습니다',
    noCulturalProducts: '이 매장에 판매 중인 문화 창작 상품이 없습니다',
    countUnit: '건',

    carousel: {
      imageAlt: '{name} 이미지 {n}',
      gotoSlide: '{n}번째 이미지 보기',
      prev: '이전',
      next: '다음',
    },

    comments: '매장 리뷰',
  },

  brand: {
    backToList: '브랜드 목록으로',
    stores: '브랜드 매장',
    ambassadors: '브랜드 앰배서더',
    products: '브랜드 상품',

    noStores: '이 브랜드에 연결된 매장이 없습니다',
    noProducts: '이 브랜드에 판매 중인 상품이 없습니다',
    noAddress: '주소 없음',

    spokespersonImage: '브랜드 앰배서더 이미지',
    zoomAria: '{name} 확대',

    comments: '브랜드 리뷰',

    countUnit: '건',
  },

  comment: {
    sortAria: '리뷰 정렬',
    sortTime: '최신',
    sortLike: '좋아요순',
    countUnit: '건',
    empty: '아직 리뷰가 없습니다. 첫 리뷰를 남겨 주세요',
    hot: '인기 리뷰',
    viewAll: '모든 리뷰 {n}건 보기',

    placeholder: '후기를 적어 주세요…',
    limit: '입력 가능 {n}자',
    publish: '게시',
    publishing: '게시 중…',
    loginToComment: '로그인 후 리뷰를 게시할 수 있습니다',

    emoji: '이모지',
    emojiAria: '이모지 삽입',
    image: '사진',
    imageUploading: '업로드 중…',
    imageCount: '사진 {n}/{max}',
    imageRemove: '이 사진 삭제',
    imageAlt: '리뷰 사진',
    imageTooMany: '리뷰 한 개에 최대 {n}장까지 첨부할 수 있습니다',
    imageFailed: '사진을 업로드하지 못했습니다: {msg}',

    reply: '답글',
    replyingTo: '@{name} 님에게 답글',
    cancelReply: '취소',
    like: '좋아요',
    liked: '좋아요함',
    delete: '삭제',
    deleteTitle: '리뷰 삭제',
    deleteConfirm: '이 리뷰와 답글이 더 이상 표시되지 않습니다. 삭제할까요?',

    pinned: '고정',
    anonymous: '익명',
    ipLocation: 'IP 지역: {place}',

    loadFailed: '리뷰를 불러오지 못했습니다: {msg}',
    postFailed: '게시하지 못했습니다: {msg}',
    actionFailed: '작업에 실패했습니다: {msg}',
  },

  activityList: {
    subtitle: '진행 중인 캠페인, 눌러서 상세 보기',
    empty: '현재 진행 중인 캠페인이 없습니다',
    moreFailed: '다음 페이지를 불러오지 못했습니다: {msg}',
    countUnit: '건',
  },

  activityDetail: {
    backToList: '캠페인 목록으로',
    listLink: '캠페인 목록',
    openLink: '캠페인 링크 열기',
    viewCategory: '이 분류의 상품 보기',
    look: '둘러보기',
    noContent: '이 캠페인에는 아직 내용이 없습니다',
  },

  noticeDetail: {
    backToList: '메시지로 돌아가기',
    listLink: '메시지',
    noContent: '이 공지에는 아직 본문이 없습니다',
  },
};
