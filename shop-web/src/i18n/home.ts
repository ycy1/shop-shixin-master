/**
 * 首页那一档文案：轮播位、搜索入口、热搜词、门店/品牌那两块、商品卡片。
 * （views/home/Storefront.vue、views/home/ActivityCarousel.vue、views/product/ProductCard.vue、
 *  views/product/ProductCarousel.vue、views/search/SearchBox.vue、views/search/HotWords.vue）
 *
 * 形状标准是中文这份，其余三档各自钉成它的类型（`en/ja/ko: typeof zh`）⇒ 少一格 vue-tsc 当场报错。四档并排放，改一句就在下面几行内。
 * 全站共用的称呼（页头、通用按钮、枚举档位、活动那行日期）在 base.ts / enums.ts，这一档只收本区自己的话。
 */

export const zh = {
  /** 首页那一屏（views/home/Storefront.vue）：轮播位、三档筛选、门店/品牌两块、全部商品那一段 */
  home: {
    carouselTitle: '轮播推荐',
    allProducts: '全部商品',
    /** 两块横条的「全部」那颗 + 空列那一句：整句一格，英文语序和中文不一样，不能拿档位名现拼 */
    allStores: '全部门店',
    allBrands: '全部品牌',
    noStores: '暂无门店',
    noBrands: '暂无品牌',
    /** 门店/品牌行里那一行次要信息缺字段时的兜底话 */
    noAddress: '暂无地址',
    noBrandIntro: '暂无品牌介绍',
    category: '分类',
    clearFilters: '清空筛选',
    /** 「共 N 件」的量词：配 base.ts 的 d.common.total 用（英文那档给复数名词） */
    unitItems: '件',
    retry: '重试',
    /** {msg} 是后端回来的原话，照 rule 原样透传，只把「没打通」这一截翻成四档 */
    productsFetchFailed: '商品接口没打通：{msg}',
    categoryOptionsFailed: '分类候选没加载出来：{msg}',
    listFetchFailed: '这一趟没查到：{msg}',
    /** 列空空得说清是哪一种：筛完没命中 / 压根没上架的商品 */
    nothingInScope: '这个条件下没有商品，换个筛选看看',
    nothingOnSale: '暂时没有上架的商品',
    scrollForMore: '继续下滑加载下一页',
    noMoreProducts: '没有更多商品了',
  },

  /** 商品卡片（views/product/ProductCard.vue）；轮播那一圈（ProductCarousel.vue）自己没有文字，卡片就是这一档 */
  card: {
    noImage: '暂无图片',
    /** 最低价后面那一颗：中文在价后面说「起」，英文跟着金额念不通，所以这边说「and up」 */
    priceFrom: '起',
    pickSpec: '选规格',
  },

  /** 首页活动轮播（views/home/ActivityCarousel.vue）：活动名和图片上的字都是数据，这一档只有控件那几句 */
  carousel: {
    loadFailed: '活动没加载出来：{msg}',
    slideAria: '看第 {n} 条活动',
    prev: '上一条活动',
    next: '下一条活动',
  },

  /** 首页「轮播推荐」那一圈（views/product/ProductCarousel.vue）：卡片上的字都是数据，这一档只有底下那颗 dot 的说辞 */
  recommend: {
    slideAria: '看第 {n} 件推荐商品',
    prev: '上一件推荐商品',
    next: '下一件推荐商品',
  },

  /** 搜索框（views/search/SearchBox.vue）：按钮那颗用 d.site.search */
  searchBox: {
    keyword: '搜索关键字',
    placeholder: '搜商品、门店、品牌',
  },

  /** 热搜那一排（views/search/HotWords.vue）：词本身是数据 */
  hotWords: {
    label: '热搜',
    ariaLabel: '热搜关键字',
  },
};

export const en: typeof zh = {
  home: {
    carouselTitle: 'Featured',
    allProducts: 'All products',
    allStores: 'All stores',
    allBrands: 'All brands',
    noStores: 'No stores yet',
    noBrands: 'No brands yet',
    noAddress: 'No address',
    noBrandIntro: 'No brand profile',
    category: 'Category',
    clearFilters: 'Clear filters',
    unitItems: 'items',
    retry: 'Try again',
    productsFetchFailed: 'Product API unreachable: {msg}',
    categoryOptionsFailed: 'Category options failed to load: {msg}',
    listFetchFailed: 'This search failed: {msg}',
    nothingInScope: 'No products match these filters — try different ones',
    nothingOnSale: 'No products on sale right now',
    scrollForMore: 'Keep scrolling to load more',
    noMoreProducts: "That's every product",
  },

  card: {
    noImage: 'No image',
    priceFrom: 'and up',
    /** 定短一点：卡片底下那一排只有一行的高度，英文写「Select options」会换行把整张卡片顶高、图就瘪下去 */
    pickSpec: 'Pick spec',
  },

  carousel: {
    loadFailed: 'Campaigns failed to load: {msg}',
    slideAria: 'Show campaign {n}',
    prev: 'Previous campaign',
    next: 'Next campaign',
  },

  recommend: {
    slideAria: 'Show product {n}',
    prev: 'Previous product',
    next: 'Next product',
  },

  searchBox: {
    keyword: 'Search keyword',
    placeholder: 'Search products, stores, brands',
  },

  hotWords: {
    label: 'Trending',
    ariaLabel: 'Trending searches',
  },
};

export const ja: typeof zh = {
  home: {
    carouselTitle: 'おすすめ',
    allProducts: 'すべての商品',
    allStores: 'すべての店舗',
    allBrands: 'すべてのブランド',
    noStores: '店舗はまだありません',
    noBrands: 'ブランドはまだありません',
    noAddress: '住所なし',
    noBrandIntro: 'ブランド紹介なし',
    category: '分類',
    clearFilters: '条件をクリア',
    unitItems: '件',
    retry: '再試行',
    productsFetchFailed: '商品APIに接続できません：{msg}',
    categoryOptionsFailed: '分類候補を読み込めませんでした：{msg}',
    listFetchFailed: 'この一覧の取得に失敗しました：{msg}',
    nothingInScope: 'この条件に一致する商品はありません。条件を変えてお試しください',
    nothingOnSale: '現在販売中の商品はありません',
    scrollForMore: 'スクロールするとさらに読み込みます',
    noMoreProducts: 'これですべての商品です',
  },

  card: {
    noImage: '画像なし',
    priceFrom: 'から',
    pickSpec: '仕様選択',
  },

  carousel: {
    loadFailed: 'キャンペーンを読み込めませんでした：{msg}',
    slideAria: 'キャンペーン {n} を表示',
    prev: '前のキャンペーン',
    next: '次のキャンペーン',
  },

  recommend: {
    slideAria: '商品 {n} を表示',
    prev: '前の商品',
    next: '次の商品',
  },

  searchBox: {
    keyword: '検索キーワード',
    placeholder: '商品・店舗・ブランドを検索',
  },

  hotWords: {
    label: '人気検索',
    ariaLabel: '人気検索キーワード',
  },
};

export const ko: typeof zh = {
  home: {
    carouselTitle: '추천',
    allProducts: '전체 상품',
    allStores: '전체 매장',
    allBrands: '전체 브랜드',
    noStores: '매장이 아직 없습니다',
    noBrands: '브랜드가 아직 없습니다',
    noAddress: '주소 없음',
    noBrandIntro: '브랜드 소개 없음',
    category: '분류',
    clearFilters: '필터 초기화',
    unitItems: '건',
    retry: '다시 시도',
    productsFetchFailed: '상품 API 연결 실패: {msg}',
    categoryOptionsFailed: '분류 목록 불러오기 실패: {msg}',
    listFetchFailed: '목록 조회 실패: {msg}',
    nothingInScope: '조건에 맞는 상품이 없습니다. 다른 필터로 다시 검색해 보세요',
    nothingOnSale: '현재 판매 중인 상품이 없습니다',
    scrollForMore: '스크롤하면 더 불러옵니다',
    noMoreProducts: '모든 상품을 봤습니다',
  },

  card: {
    noImage: '이미지 없음',
    priceFrom: '부터',
    pickSpec: '사양 선택',
  },

  carousel: {
    loadFailed: '캠페인 불러오기 실패: {msg}',
    slideAria: '캠페인 {n} 보기',
    prev: '이전 캠페인',
    next: '다음 캠페인',
  },

  recommend: {
    slideAria: '상품 {n} 보기',
    prev: '이전 상품',
    next: '다음 상품',
  },

  searchBox: {
    keyword: '검색 키워드',
    placeholder: '상품·매장·브랜드 검색',
  },

  hotWords: {
    label: '인기 검색',
    ariaLabel: '인기 검색어',
  },
};
