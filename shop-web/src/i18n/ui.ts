/**
 * 公共控件那一档文案：确认框、遮罩、图片预览、收藏按钮、地图选点、筛选下拉、购物车角标，
 * 加上搜索结果页那一屏（views/search/SearchResults.vue）。
 * （components/ConfirmDialog.vue、ModalOverlay.vue、ImagePreview.vue、FavoriteButton.vue、
 *  MapPicker.vue、FilterSelect.vue、CartBadge.vue）
 *
 * 形状标准是中文这份，其余三档各自钉成它的类型（`en/ja/ko: typeof zh`）⇒ 少一格 vue-tsc 当场报错。四档并排放，改一句就在下面几行内。
 * 全站共用的称呼（页头、通用按钮、枚举档位）在 base.ts / enums.ts，这一档只收本区自己的话。
 * （ModalOverlay.vue 没有一个自己写死的用户可见文字，所以这里没有 modal 这一档。）
 */

export const zh = {
  /** 搜索结果页（views/search/SearchResults.vue）：五个 tab、各自的表头/空提示/「再看 N 个」 */
  search: {
    /** tablist 那颗 aria-label */
    scope: '搜索范围',
    /** 顶上那行结果说明：带关键字时念的就是它 */
    resultFor: '「{kw}」的搜索结果',
    showAllNoKw: '没填关键字，下面就是全部{noun}',
    /** tabpanel 的 aria-label：「商品结果」这种 */
    tabPanelAria: '{label}结果',
    retry: '重试',
    /** 已经捞到几页之后翻页失败那一行：后端那句错误原样跟在后面 */
    nextFailed: '后面这一页没Load出来：{error}',
    noMatch: '没有匹配的{noun}',
    noneYet: '暂时没有{noun}',
    more: '再看 {n} {unit}',
    deliverySupported: '支持配送',
    noAddress: '暂无地址',
    noBrandDesc: '暂无品牌介绍',
    /** 活动那一块投放起止都没填时的兜底一行 */
    activityRunning: '投放中',
    /** 「共 12 件」那个量词由调用方给（base 的规矩），五块各叫各的 */
    unitProduct: '件',
    unitStore: '家',
    unitBrand: '个',
    unitActivity: '条',
    unitPlaza: '条',
    nounProduct: '商品',
    nounStore: '门店',
    nounBrand: '品牌',
    nounActivity: '活动',
    nounPlaza: '广场消息',
  },

  /** 公共确认框（components/ConfirmDialog.vue）：两颗按钮走 d.common.*，这里只收自己那颗 */
  confirm: {
    /** 处理中那句跟着动词走：传「删除」就是「删除中…」 */
    processing: '{text}处理中…',
  },

  /** 图片预览那一层（components/ImagePreview.vue） */
  preview: {
    /** alt 没传时兜底的图注和对话框 aria-label */
    image: '图片预览',
    close: '关闭预览',
    /** 一组图（评论区那种）才有这两颗：只有一张时整对按钮不出现 */
    prev: '上一张',
    next: '下一张',
  },

  /** 收藏那颗（components/FavoriteButton.vue）：只进 title/aria-label，按钮上不说字 */
  favorite: {
    /** 把对象名裹起来再塞进下面三句；英文档自带一个前导空格（中文的「」不用空格） */
    quotedName: '「{name}」',
    loginFirst: '登录后收藏{target}',
    busy: '处理中…',
    remove: '取消收藏{target}',
    add: '收藏{target}',
  },

  /** 地图选点（components/MapPicker.vue）：后端/高德回来的 status、info 一律原样透传，这里只收自己写的话 */
  map: {
    pick: '地图选点',
    repick: '重新选点',
    /** 弹窗的 aria-label 和标题是同一句 */
    dialogAria: '选择位置',
    addressPlaceholder: '输入地址（省市区+街道门牌）回车定位',
    locating: '定位中…',
    /** 自己写的加载失败提示（这条进字典；接口回来的错误消息不进） */
    loadFailed: '地图加载失败，经纬度仍可手动填写',
    geocodeUnavailable: '逆地理服务不可用',
    geocoderMissing: 'Geocoder 插件未加载',
    noAddressReturned: '没有地址返回',
    noResult: '无结果',
    notLoaded: '地图还没加载完成，请稍候',
    notFound: '没找到「{kw}」：{reason}',
    tryDetailed: '地址写详细一点再试',
    resolvingAddress: '地址解析中…',
    detailByHand: '{message}，详细地址自己填',
    existingCoord: '这是表单里现有的坐标，点地图可以换位置',
    clickToPick: '点击地图任意位置落点，可反复改',
  },

  /** 筛选下拉（components/FilterSelect.vue）：候选的 label 由调用方给，这里只收用 props.label 拼的句子 */
  filter: {
    all: '全部{label}',
    filterBy: '按{label}筛选',
    typeToFilter: '输入{label}名称筛选',
    noMatch: '没有匹配{label}',
  },

  /** 购物车角标（components/CartBadge.vue）：只有那颗图链接的 title */
  cartBadge: {
    title: '我的购物车',
  },
};

export const en: typeof zh = {
  search: {
    scope: 'Search scope',
    resultFor: 'Search results for “{kw}”',
    showAllNoKw: 'No keyword — showing all {noun}',
    tabPanelAria: '{label} results',
    retry: 'Retry',
    nextFailed: 'Could not load the next page: {error}',
    noMatch: 'No matching {noun}',
    noneYet: 'No {noun} yet',
    more: 'Show {n} more',
    deliverySupported: 'Delivery available',
    noAddress: 'No address yet',
    noBrandDesc: 'No brand description yet',
    activityRunning: 'Live now',
    unitProduct: 'items',
    unitStore: 'stores',
    unitBrand: 'brands',
    unitActivity: 'campaigns',
    unitPlaza: 'posts',
    nounProduct: 'products',
    nounStore: 'stores',
    nounBrand: 'brands',
    nounActivity: 'campaigns',
    nounPlaza: 'plaza posts',
  },

  confirm: {
    processing: '{text}…',
  },

  preview: {
    image: 'Image preview',
    close: 'Close preview',
    prev: 'Previous image',
    next: 'Next image',
  },

  favorite: {
    quotedName: ' “{name}”',
    loginFirst: 'Sign in to favorite{target}',
    busy: 'Working…',
    remove: 'Unfavorite{target}',
    add: 'Favorite{target}',
  },

  map: {
    pick: 'Pick on map',
    repick: 'Re-pick location',
    dialogAria: 'Choose a location',
    addressPlaceholder: 'Enter an address (province/city/street) and press Enter to locate',
    locating: 'Locating…',
    loadFailed: 'Map failed to load — you can still enter the coordinates manually',
    geocodeUnavailable: 'Reverse geocoding unavailable',
    geocoderMissing: 'The geocoder plugin did not load',
    noAddressReturned: 'no address returned',
    noResult: 'no result',
    notLoaded: 'The map is still loading — try again in a moment',
    notFound: 'No match for “{kw}”: {reason}',
    tryDetailed: 'try a more detailed address',
    resolvingAddress: 'resolving address…',
    detailByHand: '{message} — add the detailed address yourself',
    existingCoord: 'These are the saved coordinates — click the map to move the pin',
    clickToPick: 'Click anywhere on the map to drop a pin; you can move it as often as you like',
  },

  filter: {
    all: 'All {label}',
    filterBy: 'Filter by {label}',
    typeToFilter: 'Type a {label} name to filter',
    noMatch: 'No matching {label}',
  },

  cartBadge: {
    title: 'My cart',
  },
};

export const ja: typeof zh = {
  search: {
    scope: '検索範囲',
    resultFor: '「{kw}」の検索結果',
    showAllNoKw: 'キーワード未入力、すべての{noun}を表示しています',
    tabPanelAria: '{label}結果',
    retry: '再試行',
    nextFailed: '次のページを読み込めませんでした：{error}',
    noMatch: '一致する{noun}がありません',
    noneYet: '{noun}はまだありません',
    more: 'さらに{n} {unit}を表示',
    deliverySupported: '配送対応',
    noAddress: '住所なし',
    noBrandDesc: 'ブランド紹介なし',
    activityRunning: '開催中',
    unitProduct: '件',
    unitStore: '店',
    unitBrand: '件',
    unitActivity: '件',
    unitPlaza: '件',
    nounProduct: '商品',
    nounStore: '店舗',
    nounBrand: 'ブランド',
    nounActivity: 'キャンペーン',
    nounPlaza: '広場の投稿',
  },

  confirm: {
    processing: '{text}中…',
  },

  preview: {
    image: '画像プレビュー',
    close: 'プレビューを閉じる',
    prev: '前の画像',
    next: '次の画像',
  },

  favorite: {
    quotedName: '「{name}」',
    loginFirst: 'ログインすると{target}をお気に入りに追加できます',
    busy: '処理中…',
    remove: '{target}のお気に入りを解除',
    add: '{target}をお気に入りに追加',
  },

  map: {
    pick: '地図で選択',
    repick: '位置を再選択',
    dialogAria: '位置を選択',
    addressPlaceholder: '住所を入力（都道府県＋市区町村＋番地）してEnter',
    locating: '位置特定中…',
    loadFailed: '地図の読み込みに失敗しました。座標は手動で入力できます',
    geocodeUnavailable: '逆地理コード変換を利用できません',
    geocoderMissing: 'Geocoderプラグインが読み込まれていません',
    noAddressReturned: '住所未取得',
    noResult: '結果なし',
    notLoaded: '地図の読み込み中です。しばらくしてからもう一度お試しください',
    notFound: '「{kw}」が見つかりませんでした：{reason}',
    tryDetailed: '住所をもう少し詳しく入力してください',
    resolvingAddress: '住所解析中…',
    detailByHand: '{message}、詳細住所は手動でご入力ください',
    existingCoord: 'フォーム内の既存座標です。地図をクリックして位置を変更できます',
    clickToPick: '地図上の任意の地点をクリックして選択、何度でも変更できます',
  },

  filter: {
    all: 'すべての{label}',
    filterBy: '{label}で絞り込み',
    typeToFilter: '{label}名を入力して絞り込み',
    noMatch: '一致する{label}がありません',
  },

  cartBadge: {
    title: 'マイカート',
  },
};

export const ko: typeof zh = {
  search: {
    scope: '검색 범위',
    resultFor: '“{kw}” 검색 결과',
    showAllNoKw: '키워드 없음 — 모든 {noun} 표시',
    tabPanelAria: '{label} 결과',
    retry: '재시도',
    nextFailed: '다음 페이지를 불러오지 못했습니다: {error}',
    noMatch: '일치하는 {noun} 없음',
    noneYet: '아직 {noun} 없음',
    more: '{n} {unit} 더 보기',
    deliverySupported: '배송 가능',
    noAddress: '주소 없음',
    noBrandDesc: '브랜드 소개 없음',
    activityRunning: '진행 중',
    unitProduct: '건',
    unitStore: '곳',
    unitBrand: '건',
    unitActivity: '건',
    unitPlaza: '건',
    nounProduct: '상품',
    nounStore: '매장',
    nounBrand: '브랜드',
    nounActivity: '캠페인',
    nounPlaza: '광장 게시물',
  },

  confirm: {
    processing: '{text} 중…',
  },

  preview: {
    image: '이미지 미리보기',
    close: '미리보기 닫기',
    prev: '이전 이미지',
    next: '다음 이미지',
  },

  favorite: {
    quotedName: '“{name}”',
    loginFirst: '로그인 후 {target} 즐겨찾기 추가',
    busy: '처리 중…',
    remove: '{target} 즐겨찾기 해제',
    add: '{target} 즐겨찾기 추가',
  },

  map: {
    pick: '지도에서 선택',
    repick: '위치 다시 선택',
    dialogAria: '위치 선택',
    addressPlaceholder: '주소 입력 후 Enter (시·도 + 시·군·구 + 번지)',
    locating: '위치 확인 중…',
    loadFailed: '지도를 불러오지 못했습니다. 좌표는 직접 입력할 수 있습니다',
    geocodeUnavailable: '역지오코딩을 사용할 수 없습니다',
    geocoderMissing: 'Geocoder 플러그인이 로드되지 않았습니다',
    noAddressReturned: '주소 반환 없음',
    noResult: '결과 없음',
    notLoaded: '지도를 불러오는 중입니다. 잠시 후 다시 시도해 주세요',
    notFound: '“{kw}”와 일치하는 결과 없음: {reason}',
    tryDetailed: '주소를 더 자세히 입력해 보세요',
    resolvingAddress: '주소 해석 중…',
    detailByHand: '{message}, 상세 주소는 직접 입력해 주세요',
    existingCoord: '저장된 좌표입니다. 지도를 클릭해 위치를 바꿀 수 있습니다',
    clickToPick: '지도의 아무곳이나 클릭해 선택, 여러 번 다시 놓을 수 있습니다',
  },

  filter: {
    all: '전체 {label}',
    filterBy: '{label}로 필터',
    typeToFilter: '{label} 이름을 입력해 필터',
    noMatch: '일치하는 {label} 없음',
  },

  cartBadge: {
    title: '내 장바구니',
  },
};
