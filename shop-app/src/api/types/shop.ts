/**
 * 商城接口的出入参类型，字段名与后端 VO 逐格对齐（shop-commom 的 com.shop.vo.*）。
 * 只声明 App 这几页真正渲染到的格，后端多给的（productCode/images/subTitle 等）用到再补。
 */

/** MyBatis-Plus 的 IPage 信封，和 shop-web/src/lib/products.ts 那份同形 */
export interface IPageRes<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/** 商品列表那一条：单价在规格那一层，列表只汇总最低价/最高价 */
export interface IShopProduct {
  id: number
  productName: string
  mainImage: string | null
  priceMin: number | null
  priceMax: number | null
  subTitle: string | null
  categoryName: string | null
  brandName: string | null
}

/** 商品详情多出来的那几格：specs/skus 只有详情会填 */
export interface IShopProductSku {
  id: number
  /** 规格按钮上显示的字，默认规格那一格是空的，页面自己补「默认规格」 */
  specText: string | null
  /** 这一档规格的封面 = skuImages 组首那张；没配图时为 null，页面沿用商品主图 */
  skuImage: string | null
  /** 这一档规格的图文详情（「商品详情」那块从上往下铺的就是它），同样是 json 列原样给回的文本 */
  detailImages: string | null
  productPrice: number
  availableStock: number | null
  salesCount: number | null
  gainPoints: number | null
  /** ERP 那边「这一条能不能建销售单」的开关，和前台能不能买是两码事，只读来展示 */
  saleEnable: number | null
}

export interface IShopProductDetail extends IShopProduct {
  productDesc: string | null
  /** MySQL json 列原样给回的文本，形如 [{"title":"","url":""}]，用 parseImageUrls 拆 */
  images: string | null
  skus: IShopProductSku[] | null
  brandId: number | null
  storeName: string | null
}

/** 商品列表入参：分类传一级会把整棵下级一起带上，判据钉在服务端 SQL */
export interface IShopProductQuery {
  keyword?: string
  categoryId?: number
  brandId?: number
  storeId?: number
  carouselEnable?: number
  /** 1 只要文创商品 0 只要门店商品（门店详情页那两档 tab 用的就是这一格） */
  culturalEnable?: number
  pageNum?: number
  pageSize?: number
}

/**
 * 首页轮播那一条（GET /api/app/shop/home/activities）：服务端按「挂首页位 + 投放中 + 配了图」筛过，
 * 所以 image 这一格必不为空；startTime/endTime 那一档接口压根不选，页面别读。
 */
export interface IShopActivity {
  id: number
  activityName: string | null
  title: string | null
  image: string
  /** 字典 link_type：1商品 2分类 4外链 5小程序页面 6无跳转 */
  linkType: number | null
  /** 商品ID / 分类ID / URL / 小程序页面路径，按 linkType 分档，库里存的就是一个字符串 */
  linkValue: string | null
}

/** 搜索结果页「活动」那一 tab 的行（/api/app/shop/activity/list）：同一条 VO，但它不挑图 ⇒ image 可空 */
export type IShopActivityRow = Omit<IShopActivity, 'image'> & {
  image: string | null
  /** 投放起止只是卡片上那一行说法，不是判据（后端只认 status=2 投放中） */
  startTime: string | null
  endTime: string | null
}

/** 活动详情页那一条：全站只有它带 content（后台富文本），渲染走 <rich-text> 的标签白名单 */
export interface IShopActivityDetail {
  id: number
  activityName: string | null
  title: string | null
  image: string | null
  videoUrl: string | null
  content: string | null
  linkType: number | null
  linkValue: string | null
  startTime: string | null
  endTime: string | null
}

/** 公告详情页那一条：全站只有它带 content（后台富文本 HTML），渲染走 <rich-text> 的标签白名单 */
export interface IShopNoticeDetail {
  id: number
  title: string | null
  content: string | null
  createTime: string | null
}

/**
 * 门店：列表和详情共用后端那一份 ShopStoreWebVo，所以类型也只用一个。
 * coverImages 只有详情那条填（门店页那一圈图），列表回来时这几格是 undefined，页面别当它有值。
 */
export interface IShopStore {
  id: number
  storeName: string
  logo: string | null
  /** 1 直营 2 加盟 3 合作（字典 store_type） */
  storeType: number | null
  /** 1 营业中 0 休息中 2 已打烊（字典 store_business_status）；休息中的店照样能进来看，所以只是徽标 */
  businessStatus: number | null
  /** 1 支持配送 */
  isDelivery: number | null
  /** 省市区中文，和 address 拼一行印 */
  areaZh: string | null
  address: string | null
  contactPhone: string | null
  /** 直接印的文本，如 09:00-22:00 */
  businessHours: string | null
  /** MySQL json 列原样文本 [{"title":"","url":""}]，用 parseImageUrls 拆 */
  coverImages: string | null
}

/** 品牌：同门店，列表和详情共用 ShopBrandWebVo */
export interface IShopBrand {
  id: number
  brandName: string
  logo: string | null
  description: string | null
  /** MySQL json 列原样文本 [{"name":"","url":""}]，只有详情那条填 */
  brandSpokesperson: string | null
}

/** 个人中心「账号」那一条：GET /api/app/shop/profile/account 回的 SysUserProfileVo（password 服务端已清掉） */
export interface IShopAccount {
  sysUser: {
    id: number
    username: string | null
    nickname: string | null
    avatar: string | null
    mobile: string | null
    email: string | null
    signature: string | null
    areaZh: string | null
    address: string | null
    /** 逗号分隔的行政区划代码（省,市,区），和 areaZh 是一对，修改资料那页要拿它回填级联选择器 */
    areaCode?: string | null
    /** GCJ-02；下单按这一格就近分仓，所以修改资料这页也放开手填了 */
    longitude?: number | string | null
    latitude?: number | string | null
    /** 0 平台用户 1 门店 2 送货员 3 消费者 4 供应商 5 ERP 操作员（字典 account_type） */
    accountType: number | null
    /** 0 保密 1 男 2 女（sys_user.sex，这三档没有字典）；实体钉了 NON_NULL，没填的格干脆不在 JSON 里 */
    sex?: number | null
    /** 1 启用 0 禁用 */
    status?: number | null
    createTime?: string | null
    lastLoginTime?: string | null
  } | null
  roles: string[] | null
}

/**
 * 修改基础信息：服务端只认这一串（判据在 ShopAccountWriteServiceImpl#updateProfile，抄后台 /sys/user/myProfile）。
 * 账号 / 账号类型 / 状态 / 角色 / 密码都不在里面，传了也不认；id 由服务端钉成登录 id。
 *
 * 所在地区收两列（area_code 逗号分隔的行政区划代码 + area_zh 拼好的中文），口径同收货地址那两张表；
 * 行政区划代码和后台、Web 用的是同一份 @vant/area-data，所以三处选出来的码、拼出来的中文一模一样。
 * 这几列在库上是 not_empty 策略：空着送上去等于「不改」而不是「清空」，所以这一页改得了、清不掉。
 */
export interface IShopAccountForm {
  nickname: string
  mobile?: string | null
  email?: string | null
  /** 0 保密 1 男 2 女 */
  sex?: number | null
  /** 已经传好的图片 URL（图走 POST /api/file/uploadImage 拿回来），这一条不收文件 */
  avatar?: string | null
  signature?: string | null
  areaCode?: string | null
  areaZh?: string | null
  /** 详细地址（门牌那一段），和 areaZh 是分开的两列 */
  address?: string | null
  /** GCJ-02，下单没选收货地址时按这两格就近分仓 */
  longitude?: number | null
  latitude?: number | null
}

/**
 * 收货地址：GET /api/app/shop/profile/address/list 的一行（后端 ShopAddressVo，比表多了联表的 nickname/username）。
 * 列可空的（地区、标签、邮编、经纬度）没填就是 null；areaCode 是逗号分隔的行政区划代码、和 areaZh 配对。
 */
export interface IShopAddressRow {
  id: number
  receiverName: string
  receiverPhone: string
  areaCode: string | null
  areaZh: string | null
  detail: string
  postalCode: string | null
  /** 后端给的是 BigDecimal，JSON 里是数；页面只读来回填输入框，一律 String() 再用 */
  longitude: number | null
  latitude: number | null
  /** 家 / 公司 / 学校（字典 address_tag，值就是中文） */
  tag: string | null
  /** 1 默认；互斥由服务层保证（第一条强制默认、设默认先清别的） */
  isDefault: number | null
  createTime?: string | null
}

/** 新增/修改收货地址的请求体：id 在 body 里送（不走路径参数，形状同后台那份 update） */
export interface IShopAddressForm {
  id?: number
  receiverName: string
  receiverPhone: string
  areaCode: string
  areaZh: string
  detail: string
  postalCode?: string | null
  longitude: number | null
  latitude: number | null
  tag: string
  isDefault: 0 | 1
}

/** 签到一行 = 签过的那一天（某个月一次给全，没签的日子不给行） */
export interface IShopSigninRow {
  id: number
  /** 后端钉了 @JsonFormat("yyyy-MM-dd")，这一格就是日历上那个串本身 */
  signDate: string
  /** 1 正常签到 2 补签 */
  signType: number | null
  /**
   * 最近连续几天：这张表上不存这一列，服务端从最晚一天往前逐日回数（最多 7 天）现算，
   * 同一个数盖在返回的每一行上，所以取任意一行读它都行。
   */
  currentContinuous: number | null
  /** 这一天给的分（含累计达档那一笔，都并在这里） */
  points: number | null
}

/** 首页「全部商品」那一行筛选的候选：一次给全、已按「父在前、下级紧跟」铺平，level 用来给子级加前缀 */
export interface IShopCategory {
  id: number
  categoryName: string
  level: number | null
}

/** 我的订单一行（一张 sys_order）：itemCount/totalQuantity/storeNames 是明细上现汇总的，列表不带 items */
export interface IShopOrderRow {
  id: number
  orderNo: string
  /** 0 待支付 1 已支付 2 已完成 3 已取消 4 已退款 5 已关闭 */
  status: number | null
  totalAmount: number | null
  payAmount: number | null
  itemCount: number | null
  totalQuantity: number | null
  storeNames: string | null
  createTime: string | null
}

/** 订单详情多出来的那几格 + 名下全部明细行（一次全带，不分页） */
export interface IShopOrderItemRow {
  id: number
  /** 这一行买的是哪条规格；点回详情页要的是下面那个商品 id，两个不是一回事 */
  skuId: number | null
  productId: number | null
  productName: string | null
  image: string | null
  specText: string | null
  price: number | null
  quantity: number | null
  totalAmount: number | null
  /** 0 待发货 1 已发货 2 已退货 3 已出库 4 部分退货 */
  shipStatus: number | null
  storeName: string | null
}

export interface IShopOrderDetail extends IShopOrderRow {
  orderType: number | null
  discountAmount: number | null
  deliveryFee: number | null
  /** 1 微信 2 支付宝 3 银行卡 4 余额；没付过的单这一格是 null */
  payType: number | null
  payTime: string | null
  remark: string | null
  items: IShopOrderItemRow[] | null
}

/**
 * 待支付那一单点「支付」送上去的三格（口径同 shop-web 的 OrderPayDialog）：
 * 走的是服务端那条支付回调，金额发订单自己报回来的那一笔实付 —— 那边拿它和重算的数比两位小数，对不上整笔拒，页面不重算。
 * payAmount 留空就不传（服务端那一格收 null 时按「不比金额」走）。
 */
export interface IShopOrderPayForm {
  orderId: number
  /** 1 微信 2 支付宝 3 银行卡 4 余额；余额这一档在弹窗里点不动（钱是在下单那一刻冻的，这张单当初没冻） */
  payType: number
  payAmount?: number
}

/** 支付回执：主单从 0 翻到 1，顺带把出库单建出来（回调那一段的产物，页面只读这几个数） */
export interface IShopOrderPaid {
  orderId: number
  orderNo: string
  status: number | null
  outboundId: number | null
  outboundNo: string | null
  outboundCreated: boolean | null
}

/** 收藏一行：targetType 1 商品 2 门店 3 品牌，被收藏那一样叫什么由 SQL 联出来（对象删过就是空） */
export interface IShopFavoriteRow {
  id: number
  targetType: number | null
  targetId: number | null
  targetName: string | null
  /** 重新收藏同一个对象会刷新这一格（create_time 留第一次建行的痕，列表不读它） */
  favoriteTime: string | null
}

/** 足迹一行 = 一个商品；browseTime 是最近一次，第一次记录在 createTime */
export interface IShopBrowseRow {
  id: number
  productId: number | null
  productName: string | null
  mainImage: string | null
  productPrice: number | null
  categoryName: string | null
  browseTime: string | null
}

/** 用户券一行：面额那几格是发放时定格的快照，后台改了模板不会串到已发到手上的券 */
export interface IShopCouponRow {
  id: number
  couponName: string | null
  couponCode: string | null
  /** 1 满减 2 折扣 3 免邮 4 代金 */
  couponType: number | null
  /** 满减券/代金券是金额，折扣券是折扣率（0.9 = 九折） */
  faceValue: number | null
  /** 使用门槛，0 = 无门槛 */
  thresholdAmount: number | null
  /** 最高抵扣：折扣券用，null = 不封顶 */
  maxDiscount: number | null
  /** 0 未使用 1 已使用 2 已过期 3 已作废 */
  status: number | null
  /** 1 主动领取 2 系统发放 …（字典 get_way） */
  getWay: number | null
  receiveTime: string | null
  useTime: string | null
  expireTime: string | null
  orderNo: string | null
}

/** 用户徽章一行：名称/文字/图标读的是发放那一刻的快照 */
export interface IShopBadgeRow {
  id: number
  badgeName: string | null
  /** 徽章那几个字（没配图时用；图优先） */
  badgeText: string | null
  badgeIcon: string | null
  /** 1 系统发放 2 活动获得 3 消费达标 4 手动发放 5 门店发放 */
  source: number | null
  /** 拿到那一刻 */
  startTime: string | null
  /** 有效期到，null = 永久 */
  endTime: string | null
  /** 0 失效 1 有效 2 已过期；本期没有定时任务翻这一格，过期要按 endTime 现判 */
  status: number | null
  /** 1 门店 2 活动 3 签到 4 消费（联定义表带出，非本表列） */
  badgeType: number | null
}

/** 积分账户那一行：五个数是存的、读时不做现算；一分没获得过就没有这一行（接口回 null） */
export interface IShopPointsAccount {
  id: number
  balance: number | null
  totalPoints: number | null
  usedPoints: number | null
  frozenPoints: number | null
  expirePoints: number | null
  createTime: string | null
}

/** 积分流水一行：points 带符号（获取为正、消耗为负），balance 是那一笔之后的余额 */
export interface IShopPointsLogRow {
  id: number
  points: number | null
  balance: number | null
  /** 1 签到 2 购物 … 10 管理员调整 */
  bizType: number | null
  bizNo: string | null
  description: string | null
  createTime: string | null
}

/** 钱包账户那一行：几个数都是存的；一分钱没进过账就没有这一行（接口回 null） */
export interface IShopWalletAccount {
  id: number
  /** 可用余额：结算页「余额支付」能用的就是这一格 */
  balance: number | null
  /** 冻结中：下单选余额支付时占住的 */
  frozenBalance: number | null
  giftBalance: number | null
  totalRecharge: number | null
  totalConsume: number | null
  totalWithdraw: number | null
  /** 0 冻结 1 正常 */
  status: number | null
  createTime: string | null
}

/** 钱包流水一行：amount 带符号（收入为正、支出为负），balance 是这一笔之后的可用余额 */
export interface IShopWalletLogRow {
  id: number
  amount: number | null
  balance: number | null
  /** 1 充值 2 消费 3 退款 4 提现 … */
  bizType: number | null
  bizNo: string | null
  description: string | null
  createTime: string | null
}

/**
 * 充值 / 提现那一条表单：服务端只认这两格。
 * 账户上那几个数不在参数里（这一本是账，余额只能由充值进、提现出、下单占、退款还自己加减）；
 * userId 也不在，comm 那两条把它钉成登录 id。
 */
export interface IShopWalletChangeForm {
  /** 元，大于 0、最多两位小数（库里那一列是 decimal(14,2)，多于两位服务端拒、不是四舍五入） */
  amount: number
  /** 选填，原样写进流水的说明；留空由服务端按动作补一句默认的（余额充值 / 余额提现） */
  description?: string
}

/** 购物车一行 = 一个规格；id/skuId/quantity/selected 来自实体 ShopConsumerCart */
export interface IShopCartRow {
  id: number
  skuId: number
  quantity: number
  selected: number
  productId: number | null
  productName: string | null
  specText: string | null
  mainImage: string | null
  productPrice: number | null
  availableStock: number | null
  /** 1 可以买 0 商品已下架（灰行、不计入选中和合计） */
  buyable: number | null
}

/** 加购只认 skuId + quantity；改数量/勾选认 id + 那两格（都是局部更新） */
export interface IShopCartSaveForm {
  id?: number
  skuId?: number
  quantity?: number
  selected?: number
}

/** 统一下单入参：userId 不在这一格里（由登录态钉），行只传规格和数量 */
export interface IShopOrderItemForm {
  skuId: number
  quantity: number
  usedPoints?: number
}

export interface IShopOrderForm {
  addressId?: number
  orderType?: number
  remark?: string
  couponIds?: number[]
  payPoints?: number
  payType?: number
  items: IShopOrderItemForm[]
}

/** 下单试算回包：金额全部服务端算，页面只摆不拼（账是平的，见 ShopOrderPreviewVo 的注释） */
export interface IShopOrderPreview {
  itemCount: number
  goodsAmount: number
  deliveryFee: number
  discountAmount: number
  goodsDiscount: number
  shippingDiscount: number
  usedPoints: number
  pointsDiscount: number
  totalAmount: number
  payAmount: number
  /** 逐张券抵了多少，顺序就是请求里 couponIds 的顺序；used=false 的那张不核销、还留在买家手上 */
  coupons: {
    couponId: number
    couponName: string | null
    couponType: number | null
    discountAmount: number
    used: boolean
  }[]
  items: {
    skuId: number
    productName: string
    specText: string | null
    price: number
    quantity: number
    totalAmount: number
    discountAmount: number
    usedPoints: number
    pointsAmount: number
    paidAmount: number
  }[]
}

/**
 * 评论区的一行（后端 CommentListVo，四处详情页两端共用）：
 * 给出去的就是页面上真要看的那几格，访客 IP 本身不给前台，只给归属地。
 */
export interface IShopCommentRow {
  id: number
  /** 顶层评论为 null；回复挂在被回复那一行下面，层数不限（parentId 就是被回复那一条的 id） */
  parentId: number | null
  /** 作者 id，只有对上登录 id 的那几条才画删除那颗 */
  userId: number | null
  nickname: string | null
  avatar: string | null
  /** 回复某个人时才有（顶层这一格是空），页面上那句「回复 @某人」读它 */
  replyNickname: string | null
  /** 纯文本，可含 emoji（就是正文里的普通字符）；配图以 `[图片](https://…)` 内嵌在同一格里，页面拆串后画 */
  content: string
  likeCount: number | null
  /** 1 置顶：两档排序都把它打头，只有后台能改这一格 */
  isStick: number | null
  ipSource: string | null
  createTime: string | null
  /** 这一行我点过赞没：未登录时后端恒给 false */
  isLike: boolean | null
  /** 这一行名下的直接回复（服务端递归 CTE 一趟捞完整棵回复树按 parentId 归好堆带回来，没有就是空数组） */
  children: IShopCommentRow[] | null
}

/** 发表/回复：userId 不在这一格里（由登录态钉）；传 replyToId 就是回复，类型和对象跟着被回复那一条走 */
export interface IShopCommentSaveForm {
  commentType: number
  businessId: number
  content: string
  replyToId?: number
}

/**
 * 我的消息一行（/api/comm/shop/notification/list 带回来的那一格，字段和后台铃铛、shop-web 消息中心同一份 VO）。
 *
 * businessType + businessId 是跳转用的两格：服务端 link 那一格刻意留空，各端自己按档位拼路由（拼法见 utils/notification）。
 * commentType / commentBusinessId 只有互动消息才有 —— 那两档的 businessId 指的是评论本身，
 * 要知道评论挂在哪个商品/门店/品牌/广场消息上只能读服务端联 sys_comment 带回来的这两格。
 */
export interface IShopNotificationRow {
  id: number
  /** 1 系统消息（公告 / 活动 / 培训 / 签收 / 徽章）、2 互动消息（回复 / 评价 / 点赞） */
  category: number | null
  /** 业务档（notification_business_type）：notice / activity / training / receive / badge / comment / like / system */
  businessType: string | null
  businessId: number | null
  /** 服务端填的跳转目标（现状八档全留空，留着认：哪天后台手工填了一条就该照它跳） */
  link: string | null
  /** 源评论的类型：1 商品 2 门店 3 品牌 4 广场 */
  commentType: number | null
  /** 那条评论挂在哪个商品/门店/品牌/广场消息上 */
  commentBusinessId: number | null
  title: string | null
  /** 服务端写好的那句正文（属于内容不属于界面，不翻） */
  message: string | null
  fromNickname: string | null
  /** 0 未读 1 已读：读的是当前登录人自己那一行 receiver */
  isRead: number | null
  createTime: string | null
}

/** 未读汇总：total 给红点，两档各给 tab 上那个数 */
export interface IShopNotificationUnread {
  total: number
  systemCount: number
  interactCount: number
}

/**
 * 广场消息一行（回的是后端实体 ShopPlazaMessage，字段与 shop-web 的 src/lib/plaza 那一份逐格相同）。
 *
 * 列表那条 SQL 不取 content，所以正文只有详情页那一趟读得到；三个计数由服务端读明细现算，前端只念不回写。
 * contentText 是服务端从正文剥标签算出来的摘要。
 * selfLiked 只有登录时才带回这一格（未登录是 undefined），userName / relationName 是联表带出的非本表列。
 */
export interface IShopPlazaMessage {
  id: number
  userId: number | null
  /** 发布者昵称：账号删了联不上就是空，页面按 id 兜底 */
  userName: string | null
  /** 关联类型：0 无关联 1 商品 2 门店 3 品牌 4 活动（字典 plaza_relation_type） */
  relationType: number | null
  relationId: number | null
  /** 关联对象名称：对象被删了就是空 */
  relationName: string | null
  /** 标题可空，正文才是必填 */
  title: string | null
  /** 富文本正文（HTML）：只有详情那一条带 */
  content?: string | null
  contentText: string | null
  /** 广场消息图：发布人自己上传的一组图，几个地址用英文逗号拼在这一列上（可空，服务端不会从正文抓图来补）；卡片配图取第一张，详情页在正文下方整组铺开 */
  plazaImage: string | null
  isTop: number | null
  isRecommend: number | null
  likeCount: number | null
  commentCount: number | null
  shareCount: number | null
  /** 审核状态：0 待审核 1 已发布 2 已拒绝（字典 plaza_audit_status） */
  auditStatus: number | null
  /** 判「拒绝」时后台写给发布者看的那一句，前台只有作者本人那一趟读得到 */
  auditRemark: string | null
  /** 1 正常 2 已隐藏（字典 plaza_status） */
  status: number | null
  /** 审核通过那一刻由服务端盖的时间；没通过就是空 */
  publishTime: string | null
  createTime?: string | null
  selfLiked?: boolean | null
}

/**
 * 发布广场消息：只有这五格由前端给（userId 由登录态钉、audit_status 一律落 0 待审核）。
 * 关联对象两格要一起给：只填类型不填 id 服务端会拒；类型给 0（无关联）时 id 那一格不用管。
 * 广场消息图不传就是「这条没配图」—— 服务端每次都照表单写这一格，所以修改时必须把已有的那一串带回去。
 */
export interface IShopPlazaPublishForm {
  title?: string
  content: string
  /** 广场消息图：几个地址用英文逗号拼成一串；每张先走 /api/file/uploadImage 传上去，把回来的地址按顺序拼进来（最多 9 张，判据在服务端） */
  plazaImage?: string
  /** 关联类型：0 无关联 1 商品 2 门店 3 品牌 4 活动（字典 plaza_relation_type） */
  relationType?: number
  relationId?: number
}

/** 改自己发的那一条：形状同发布，多一个 id（只能改自己的，改完服务端打回待审核） */
export interface IShopPlazaUpdateForm extends IShopPlazaPublishForm {
  id: number
}
