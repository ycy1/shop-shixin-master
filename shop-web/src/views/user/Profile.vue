<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { FwbToggle } from 'flowbite-vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import FilterSelect from '@/components/FilterSelect.vue';
import ImagePreview from '@/components/ImagePreview.vue';
import OrderPayDialog from '@/components/OrderPayDialog.vue';
import AccountForm from '@/views/user/AccountForm.vue';
import AddressForm from '@/views/user/AddressForm.vue';
import OrderDetail from '@/views/user/OrderDetail.vue';
import PasswordForm from '@/views/user/PasswordForm.vue';
import PlazaPublishDialog from '@/views/plaza/PlazaPublishDialog.vue';
import SigninCalendar from '@/views/user/SigninCalendar.vue';
import WalletChangeForm from '@/views/user/WalletChangeForm.vue';
import {
  badgeExpired,
  couponBenefit,
  deleteMyAddress,
  deleteMyFavorite,
  fetchMyAccount,
  fetchMyAddresses,
  fetchMyBadges,
  fetchMyBrowses,
  fetchMyCoupons,
  fetchMyFavorites,
  fetchMyOrders,
  fetchMyPointLogs,
  fetchMyPoints,
  fetchMyWallet,
  fetchMyWalletLogs,
  setBadgeShow,
  type AccountInfo,
  type AddressRow,
  type BadgeRow,
  type BrowseRow,
  type CouponRow,
  type FavoriteRow,
  type OrderRow,
  type PointLogRow,
  type PointsAccount,
  type WalletAccount,
  type WalletLogRow,
} from '@/lib/profile';
import {
  deleteNotification,
  fetchMyNotifications,
  fetchUnreadSummary,
  messageTarget,
  readAllNotifications,
  readNotification,
  type NotificationRow,
} from '@/lib/notification';
import {
  PLAZA_AUDIT_PASSED,
  PLAZA_AUDIT_REJECTED,
  deletePlaza,
  fetchMyPlazaPage,
  fetchPlaza,
  plazaLabel,
  type PlazaMessage,
} from '@/lib/plaza';
import { enumFilters, enumLabel, useI18n } from '@/i18n';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();
/** locale 由页面壳递进来：岛里拿不到 Astro 对象（见 @/i18n 顶上那段） */
defineProps<{ locale?: string }>();
const i18n = useI18n();
const { d, fill, href } = i18n;

/** 认不出的码值（后台新加档而前台没跟上）统一走 d.enum.unknown 那一句，不再各块自己拼「状态 7」「业务 null」 */
function unknownCode(code: number | null | undefined) {
  return fill(d.enum.unknown, { n: code ?? d.common.dash });
}

/**
 * 一块列表的形状：已加载到第几页、总页数、行、加载中、错在哪。
 * 八块共用 load()，所以类型只跟着行种类走，面板上那套模板各自写。
 */
interface Block<T> {
  rows: T[];
  page: number;
  pages: number;
  total: number;
  loading: boolean;
  error: string;
}

type SectionKey = 'account' | 'orders' | 'addresses' | 'favorites' | 'browses' | 'messages' | 'plaza';
/** 「个人信息」那一块下面那五个 tab */
type InfoTabKey = 'base' | 'coupon' | 'badge' | 'points' | 'wallet';
/** 一行是什么由那一块自己定，八块合在一条 load() 里读，所以这里收成"一堆行" */
type AnyBlock = Block<unknown>;
type Fetcher = (page: number, size: number) => Promise<{ records: unknown[]; total: number; pages: number }>;

const PAGE_SIZE = 10;

const orders = reactive<Block<OrderRow>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' });
const addresses = reactive<Block<AddressRow>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' });
const favorites = reactive<Block<FavoriteRow>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' });
const browses = reactive<Block<BrowseRow>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' });
const coupons = reactive<Block<CouponRow>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' });
const badges = reactive<Block<BadgeRow>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' });
const pointLogs = reactive<Block<PointLogRow>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' });
const walletLogs = reactive<Block<WalletLogRow>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' });
const messages = reactive<Block<NotificationRow>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' });
const plaza = reactive<Block<PlazaMessage>>({ rows: [], page: 0, pages: 0, total: 0, loading: false, error: '' });

/**
 * 消息中心那一块的状态：
 * - messageCategory：0 不限（那一档 chip 上顶的是 total）、1 系统、2 互动。给 0 时不下 category 这一格，
 *   服务端「不传 = 两档都要」和「传了个别的值 = 拒掉」是两条判据，所以不能拿 null 当默认往上传。
 * - unread：三个未读数由 /unread 那一趟带回来（红点数的是全库，不是当前这一页 —— 页是截过的）。
 * - messageNotice：「全部已读」和批量那两句成没成只能在这儿说，那一块没有别的地方落提示。
 * - selectedIds：多选勾的是哪几条（只在这一串已加载的行里挑；换档整块重拉就清空）。
 */
const messageCategory = ref(0);
const unread = reactive({ total: 0, systemCount: 0, interactCount: 0 });
const messageNotice = ref('');
const selectedIds = ref<number[]>([]);

function fetchMessages(page: number, size: number) {
  return fetchMyNotifications(page, size, messageCategory.value || undefined);
}

async function loadUnreadSummary() {
  try {
    const data = await fetchUnreadSummary();
    unread.total = data.total;
    unread.systemCount = data.systemCount;
    unread.interactCount = data.interactCount;
  } catch {
    // 红点捞不到不影响这一块列表（列表自己会挂红字），这一趟就不吭声
  }
}

/** 换档 = 回第 1 页整块重捞（同券那两档的判据，别在旧那一串上接新页）；串换了，勾掉的跟着清 */
async function refilterMessages() {
  messageNotice.value = '';
  selectedIds.value = [];
  messages.rows.splice(0);
  messages.page = 0;
  messages.pages = 0;
  messages.total = 0;
  await load(messages, fetchMessages);
}

/**
 * 点一行 = 标已读 + 有落地页就跳过去。
 *
 * 跳转链接由前端按 businessType 拼（服务端 link 那一格刻意留空，三端各自的路由形状不一样，见 lib/notification 的 messageTarget）。
 * 公告进详情页、活动进详情页、回复/点赞进「全部评论」那一页并定位到源评论那一条；
 * 签收、徽章、系统提醒本来就没有对应的页面，就地看完（消息正文是完整一句话）就算读过。
 */
async function readMessage(row: NotificationRow) {
  const target = messageTarget(row);
  if (row.isRead !== 1) {
    try {
      await readNotification(row.id);
      row.isRead = 1;
      await loadUnreadSummary();
    } catch (err) {
      // 标已读没成不挡跳转：那条消息本身还是要去看的
      messages.error = fill(d.account.message.failed, { error: (err as Error).message });
    }
  }
  if (target) window.location.href = href(target);
}

/** 三档 chip：value 就是服务端那一格（0 不下），count 只在该档自己未读数不为 0 时才顶出来 —— 「全部」那档不重复报一遍 */
const MESSAGE_FILTERS = computed<{ label: string; value: number; count: number }[]>(() => [
  { label: d.account.message.filterAll, value: 0, count: 0 },
  { label: d.account.message.filterSystem, value: 1, count: unread.systemCount },
  { label: d.account.message.filterInteract, value: 2, count: unread.interactCount },
]);

/** 「全部已读」清的是所有未读、不跟着筛选档走（筛选只用来挑看哪一串），所以只在总数还有未读时才出现 */
async function markMessagesRead() {
  messageNotice.value = '';
  try {
    await readAllNotifications();
    await refilterMessages();
    await loadUnreadSummary();
    messageNotice.value = d.account.message.allReadDone;
  } catch (err) {
    messages.error = fill(d.account.message.failed, { error: (err as Error).message });
  }
}

/**
 * 删一条消息：只是对自己隐藏（receiver 那一行翻 is_deleted），别人的、后台真删都动不到，
 * 所以框里要说清「只是对你隐藏」，不然看着像把这条消息撤回了。
 */
function removeMessage(row: NotificationRow) {
  pending.value = {
    title: d.account.message.delete,
    message: d.account.message.deleteConfirm,
    confirmText: d.common.delete,
    busy: false,
    run: async () => {
      messages.error = '';
      try {
        await deleteNotification(row.id);
        await refilterMessages();
        await loadUnreadSummary();
        messageNotice.value = d.account.message.removed;
      } catch (err) {
        messages.error = (err as Error).message;
      }
    },
  };
}

/**
 * 多选：勾的是当前已加载的那一串行（往下翻页接着勾，换档整块重拉就清空）。
 * 服务端只开了单条那两头（read/{id}、delete/{id}），批量就是把选中的 id 一条条发过去 ——
 * 一次多选撑死一整页（10 条），犯不着为这个再开一条批量端点。
 */
const allMessagesSelected = computed(
  () => messages.rows.length > 0 && messages.rows.every((row) => selectedIds.value.includes(row.id)),
);

function toggleMessageSelect(id: number) {
  const at = selectedIds.value.indexOf(id);
  if (at >= 0) {
    selectedIds.value.splice(at, 1);
  } else {
    selectedIds.value.push(id);
  }
}

function toggleSelectAllMessages() {
  selectedIds.value = allMessagesSelected.value ? [] : messages.rows.map((row) => row.id);
}

/** 「批量已读」只把还没读过的那几条发出去：读过的再发一趟服务端也只是把 read_time 往后挪，白跑 */
async function readSelectedMessages() {
  const rows = messages.rows.filter((row) => selectedIds.value.includes(row.id) && row.isRead !== 1);
  messageNotice.value = '';
  messages.error = '';
  try {
    for (const row of rows) {
      await readNotification(row.id);
      row.isRead = 1;
    }
    selectedIds.value = [];
    await loadUnreadSummary();
    messageNotice.value = fill(d.account.message.readSelectedDone, { count: rows.length });
  } catch (err) {
    messages.error = fill(d.account.message.failed, { error: (err as Error).message });
  }
}

/** 「批量删除」走和单条同一个确认框：删的只是自己名下那几行 receiver，别的收件人照样看得见 */
function removeSelectedMessages() {
  const ids = [...selectedIds.value];
  if (!ids.length) return;
  pending.value = {
    title: d.account.message.delete,
    message: fill(d.account.message.deleteSelectedConfirm, { count: ids.length }),
    confirmText: d.common.delete,
    busy: false,
    run: async () => {
      messages.error = '';
      try {
        for (const id of ids) {
          await deleteNotification(id);
        }
        await refilterMessages();
        await loadUnreadSummary();
        messageNotice.value = d.account.message.removed;
      } catch (err) {
        messages.error = (err as Error).message;
      }
    },
  };
}

/**
 * 优惠券那一格的两档筛选：0 = 不限，形状和首页那三档一样（都吃 FilterSelect）。
 * 状态那档候选的 id 比真值大一格 —— 「未使用」的真值恰好是 0，和下拉里「不限」那颗哨兵撞了（见 enumFilters）。
 * 候选得按语种现算（d.enum 那张表是语种档里的），所以留在这儿是 computed，不在模块顶层。
 */
const couponStatus = ref(0);
const couponType = ref(0);
const couponStatusOptions = computed(() => enumFilters(d.enum.couponUserStatus, 1));
const couponTypeOptions = computed(() => enumFilters(d.enum.couponType));

/** 四种券各一颗图标（描边那套同头像占位那颗，viewBox 24）：满减=票券 折扣=百分号 免邮=货车 代金=钞票 */
const COUPON_TYPE_ICON: Record<number, string> = {
  1: 'M16.5 6v.75m0 3v.75m0 3v.75m0 3V18m-9-5.25h5.25M7.5 15h3M3.375 5.25c-.621 0-1.125.504-1.125 1.125v3.026a2.999 2.999 0 0 1 0 5.198v3.026c0 .621.504 1.125 1.125 1.125h17.25c.621 0 1.125-.504 1.125-1.125v-3.026a2.999 2.999 0 0 1 0-5.198V6.375c0-.621-.504-1.125-1.125-1.125H3.375Z',
  2: 'M9 14.25l6-4.5M4.5 7.5h3.75a.75.75 0 0 0 0-1.5H4.5v-.75a.75.75 0 0 1 1.5 0V6m10.5 6h-3.75a.75.75 0 0 0 0 1.5h3.75v.75a.75.75 0 0 1-1.5 0V15M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z',
  3: 'M8.25 18.75a1.5 1.5 0 0 1-3 0m3 0a1.5 1.5 0 0 0-3 0m3 0h6m-9 0H3.375a1.125 1.125 0 0 1-1.125-1.125V14.25m17.25 4.5a1.5 1.5 0 0 1-3 0m3 0a1.5 1.5 0 0 0-3 0m3 0h1.125c.621 0 1.129-.504 1.09-1.124a17.902 17.902 0 0 0-3.213-9.193 2.056 2.056 0 0 0-1.58-.86H14.25M16.5 18.75h-2.25m0-11.177v-.958c0-.568-.422-1.048-.987-1.106a48.554 48.554 0 0 0-10.026 0 1.106 1.106 0 0 0-.987 1.106v7.635m12-6.677v6.677m0 4.5v-4.5m0 0h-12',
  4: 'M2.25 7.125A1.875 1.875 0 0 1 4.125 5.25h15.75a1.875 1.875 0 0 1 1.875 1.875v9.75a1.875 1.875 0 0 1-1.875 1.875H4.125a1.875 1.875 0 0 1-1.875-1.875v-9.75ZM12 14.25a2.25 2.25 0 1 0 0-4.5 2.25 2.25 0 0 0 0 4.5ZM6 9h.01M18 15h.01',
};
/** 图标那一格的底色跟着类型走；认不出的类型（新加档）灰底 + 那张普通票券，不空着 */
const COUPON_TYPE_TONE: Record<number, string> = {
  1: 'bg-red-50 text-red-600 dark:bg-red-900/40 dark:text-red-300',
  2: 'bg-amber-50 text-amber-600 dark:bg-amber-900/40 dark:text-amber-300',
  3: 'bg-blue-50 text-blue-600 dark:bg-blue-900/40 dark:text-blue-300',
  4: 'bg-green-50 text-green-600 dark:bg-green-900/40 dark:text-green-300',
};
const COUPON_ICON_FALLBACK = 'bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-300';

function couponIconOf(type: number | null) {
  return COUPON_TYPE_ICON[type ?? 0] || COUPON_TYPE_ICON[1];
}

function couponToneOf(type: number | null) {
  return COUPON_TYPE_TONE[type ?? 0] || COUPON_ICON_FALLBACK;
}

/** 带筛选捞券：「点开这一格」和「再看 10 张」都走这一条，所以它挂在下面 INFO_TABS 那一格上 */
function fetchCoupons(page: number, size: number) {
  return fetchMyCoupons(
    page,
    size,
    couponStatus.value ? couponStatus.value - 1 : undefined,
    couponType.value || undefined,
  );
}

/**
 * 换筛选 = 回第 1 页整块重捞（同删地址那一条判据，别在旧那一串上接新页）。
 * 请求途中两档下拉是禁用的 ⇒ 不会两条请求在路上抢着画结果（同首页那三档）。
 */
async function refilterCoupons() {
  coupons.rows.splice(0);
  coupons.page = 0;
  coupons.pages = 0;
  coupons.total = 0;
  await load(coupons, fetchCoupons);
}
watch([couponStatus, couponType], () => void refilterCoupons());

/** 清空只做「改那两格」这一件事，重捞交给上面那个 watch（两格一起改，watch 也只跑一趟） */
function clearCouponFilters() {
  couponStatus.value = 0;
  couponType.value = 0;
}
const couponFiltered = computed(() => couponStatus.value > 0 || couponType.value > 0);

/**
 * 一行一张券上那几句：到哪天有效、哪天领的、用在哪一天、落在哪个单号上。
 * 整句进字典（英文语序是 "Valid until 2026-11-01"，不能拿中文那半截去拼）。
 */
function couponValidUntil(row: CouponRow) {
  return fill(d.account.coupon.validUntil, { d: dayOf(row.expireTime) });
}

function couponReceived(row: CouponRow) {
  return fill(d.account.coupon.received, { d: dayOf(row.receiveTime) });
}

function couponUsed(row: CouponRow) {
  return fill(d.account.coupon.used, { d: dayOf(row.useTime) });
}

function couponOrderNo(row: CouponRow) {
  return fill(d.account.coupon.orderNo, { no: row.orderNo ?? '' });
}

/** 券名没填时的兜底（「券 12」/ "Coupon 12"） */
function couponNameOf(row: CouponRow) {
  return row.couponName || fill(d.account.coupon.nameFallback, { id: row.id });
}

/**
 * 徽章那一格的两句话：这一枚现在还算不算数、标签上说什么。
 * status 只有 1 有效 / 0 失效两档（0 由后台「获取记录」那颗状态开关写，库里没有「已过期」那一档），
 * 名下一枚枚里会掺着「status 还是 1 有效、endTime 却已经过了」的，所以过期是这儿现比的（同上面那张券）。
 */
function badgeValid(row: BadgeRow) {
  return (row.status ?? 1) === 1 && !badgeExpired(row);
}

function badgeStatusText(row: BadgeRow) {
  if (badgeValid(row)) return d.enum.badgeStatus[1];
  if (badgeExpired(row)) return d.enum.badgeStatus[2];
  return enumLabel(d.enum.badgeStatus, row.status, unknownCode(row.status));
}

/** 名称没填时的兜底：列表第一排、挂件的 alt 和悬停 title、放大层那一格都念这一句 */
function badgeNameOf(row: BadgeRow) {
  return row.badgeName || fill(d.account.badge.nameFallback, { id: row.id });
}

/** 挂件悬停那句：名称 · 现在还算不算数 · 哪天拿到的（原来是在模板里拼的一串中文） */
function badgePendantTitle(row: BadgeRow) {
  return [badgeNameOf(row), badgeStatusText(row), badgeReceived(row)].join(' · ');
}

/** 一枚徽章上那句有效期：没写到时间就是长期有效 */
function badgeValidText(row: BadgeRow) {
  return row.endTime ? fill(d.account.badge.validUntil, { d: dayOf(row.endTime) }) : d.account.badge.forever;
}

function badgeReceived(row: BadgeRow) {
  return fill(d.account.badge.received, { d: dayOf(row.startTime) });
}

/** 点开放大层时底下那行说明：只有徽章配了文字才给看 */
function badgeCaptionText(row: BadgeRow) {
  return row.badgeText ? fill(d.account.badge.captionText, { text: row.badgeText }) : '';
}

/** 这一枚要不要挂在账号卡下面那排挂件上：库里那一格只有 0 是收起，读不到也算展示（这一列上线之前发出去的那些） */
function badgeShown(row: BadgeRow) {
  return row.isShow !== 0;
}

/**
 * 顶上那一排挂件用的徽章：读的就是「个人信息 → 徽章」那一份列表的第一页（最新拿到的一枚在前），不另发一个请求。
 * 本人收起的那几枚不挂（每行右边那颗开关改的就是这一格）；下面那一列还是全铺，
 * 不然收起来的没有地方放回来。有效的那几枚挪到前面，过期的灰着留在后面；
 * 上面那句条数报的是 total，所以挂件最多铺第一页那 10 枚。
 */
const headBadges = computed(() =>
  badges.rows.filter(badgeShown).sort((a, b) => Number(badgeValid(b)) - Number(badgeValid(a))),
);

/** 挂杆 + 一圈牌子要不要铺：一枚都没拉回来时留三枚空牌占位（同原来），拉回来了但全被收起就整排收掉 */
const showPendants = computed(() => !badges.rows.length || headBadges.value.length > 0);

/**
 * 收起 / 展示一枚徽章：只动 is_show 那一格，一次只改一枚，busy 记在哪一行上。
 * 改成功就地翻本地那一格 —— 挂件那一排和这一行那颗开关（连它旁边的字）都跟着它走，不重拉整块（重拉会洗掉已经翻出来的后面几页）。
 * 开关本身不回滚：它读的是 :model-value，库里没改成时那一格还是旧值，按下去看着动了、下一次渲染就顶回来。
 * 失败那句不写进 badges.error：那一格有值时整块面板换成红字（见 sectionError），列表就没了，
 * 想再点一次只能刷新页面，所以摆在徽章列表上面单独一行。
 */
const badgeShowBusy = ref<number | null>(null);
const badgeShowError = ref('');

async function toggleBadgeShow(row: BadgeRow, next: boolean) {
  if (badgeShowBusy.value !== null) return;
  badgeShowBusy.value = row.id;
  badgeShowError.value = '';
  const value = next ? 1 : 0;
  try {
    await setBadgeShow(row.id, value);
    row.isShow = value;
  } catch (err) {
    badgeShowError.value = fill(d.account.badge.showFailed, { error: (err as Error).message });
  } finally {
    badgeShowBusy.value = null;
  }
}

/**
 * 「个人信息」那一块里有三格不是列表：账号一行、积分账户一行、钱包账户也是一行，所以它们不进那八块共用的 load()，自己一份形状。
 * loaded 是「拉过没有」那一格（那八块看的是 block.page，这三格没有页可看）。
 */
const account = reactive<{ row: AccountInfo | null; roles: string[]; loaded: boolean; loading: boolean; error: string }>({
  row: null,
  roles: [],
  loaded: false,
  loading: false,
  error: '',
});
const points = reactive<{ row: PointsAccount | null; loaded: boolean; loading: boolean; error: string }>({
  row: null,
  loaded: false,
  loading: false,
  error: '',
});
const wallet = reactive<{ row: WalletAccount | null; loaded: boolean; loading: boolean; error: string }>({
  row: null,
  loaded: false,
  loading: false,
  error: '',
});

/**
 * 左边那列菜单：一次只看一块，谁在菜单上点开来谁。
 * 「没加载过」这句判据要看的是 block.page === 0（空数据也算加载过了，否则每次切回来都重捞一遍）。
 *
 * unit / block / fetch 三格只有那几块列表有 —— 个人信息那一条没有条数可报、也没有下一页可捞（它自己在下面铺 tab）。
 * label / unit 都是按语种现取的格子，所以这张表只能在 setup 里现铺（computed），不能留在模块顶层。
 */
const MENUS = computed<{ key: SectionKey; label: string; unit?: string; block?: AnyBlock; fetch?: Fetcher }[]>(() => [
  { key: 'account', label: d.account.menu.personal },
  { key: 'orders', label: d.account.menu.orders, unit: d.account.units.order, block: orders, fetch: fetchMyOrders },
  { key: 'messages', label: d.account.menu.messages, unit: d.account.units.message, block: messages, fetch: fetchMessages },
  { key: 'addresses', label: d.account.menu.addresses, unit: d.account.units.address, block: addresses, fetch: fetchMyAddresses },
  { key: 'favorites', label: d.account.menu.favorites, unit: d.account.units.favorite, block: favorites, fetch: fetchMyFavorites },
  { key: 'browses', label: d.account.menu.browses, unit: d.account.units.browse, block: browses, fetch: fetchMyBrowses },
  // 广场消息原来是「个人信息」下面最后一格 tab：它能改能删、头上还挂三个筛选项，挤在 tab 里不如单开一档
  { key: 'plaza', label: d.account.menu.plaza, unit: d.account.units.plaza, block: plaza, fetch: fetchPlazaMine },
]);

/**
 * 「个人信息」下面那五个 tab：形状和上面那列菜单一样（点开谁拉谁、条数报在右上角），所以共用 panel 那一格。
 * 积分、钱包那两条的 block 是底下那张流水表，上面那一格账户行没有页可看，自己一份形状（同基础信息那一条）。
 * 券和徽章两格的名字就是枚举档里那两格称呼（d.enum.coupon / d.enum.badge），不另开格子。
 */
const INFO_TABS = computed<{ key: InfoTabKey; label: string; unit?: string; block?: AnyBlock; fetch?: Fetcher }[]>(() => [
  { key: 'base', label: d.account.menu.base },
  { key: 'coupon', label: d.enum.coupon, unit: d.account.units.coupon, block: coupons, fetch: fetchCoupons },
  { key: 'badge', label: d.enum.badge, unit: d.account.units.badge, block: badges, fetch: fetchMyBadges },
  { key: 'points', label: d.account.menu.points, unit: d.account.units.pointLog, block: pointLogs, fetch: fetchMyPointLogs },
  { key: 'wallet', label: d.account.menu.wallet, unit: d.account.units.walletLog, block: walletLogs, fetch: fetchMyWalletLogs },
]);

const active = ref<SectionKey>('account');
const infoTab = ref<InfoTabKey>('base');
/** 这次是往后挪一格还是往前挪（决定右边那卡从哪边移进来）；sliding 只在那段过渡里挂，见 global.css 最后那一条 */
const slideName = ref('tab-next');
const sliding = ref(false);
const current = computed(() => MENUS.value.find((item) => item.key === active.value)!);
/** 现在画的是哪一格：菜单第一格看 tab，其余四格看菜单本身 —— 右上角那句、红字、「再看 N 条」都只认它 */
const panel = computed(() =>
  active.value === 'account' ? INFO_TABS.value.find((item) => item.key === infoTab.value)! : current.value,
);

/** 那一块右上角那句：有列表的那几格报条数，基础信息那一格没有 */
const countText = computed(() =>
  panel.value.unit ? fill(d.common.total, { n: panel.value.block?.total ?? 0, unit: panel.value.unit }) : '',
);
/**
 * 错在哪还是各回各的那一格：没有列表可挂的那三格（基础信息、积分账户、钱包账户）自己带红字。
 * 积分、钱包那两格两路都要看 —— 账户行和流水表各发各的请求，挂了哪个都得说。
 */
const sectionError = computed(() => {
  const listError = panel.value.block?.error ?? '';
  if (active.value !== 'account') return listError;
  if (infoTab.value === 'base') return account.error;
  if (infoTab.value === 'points') return listError || points.error;
  if (infoTab.value === 'wallet') return listError || wallet.error;
  return listError;
});

/** 登录态和头像都读 localStorage，服务端那一趟没有 ⇒ 第一趟两边都画「没登录」，否则撞 hydration */
const ready = ref(false);
const signedIn = computed(() => ready.value && auth.isLoggedIn);
const avatarBroken = ref(false);
const avatar = computed(() => (!avatarBroken.value ? auth.user?.avatar : null));
const nickname = computed(() => auth.user?.nickname || auth.user?.username || d.auth.me);
/** signingOut 期间页面整块压住：token 一清这一列菜单就都变成「请先登录」，留着只会闪一排红 */
const signingOut = ref(false);

/** 三颗弹框：详情只带一个订单 id（0 = 没开），签到日历、改资料、改密码那三颗不要参数 */
const detailId = ref(0);
const signinOpen = ref(false);
/** 地址弹窗：开着与否看 addressOpen，弹的是哪一条看 editingAddress（null = 新增，没现成那条可回填） */
const addressOpen = ref(false);
const editingAddress = ref<AddressRow | null>(null);
/**
 * 改密码：开不开看 passwordOpen，改成功那一句落在「个人信息 → 基础信息」那一格上（框一收就没地方说了）。
 * 不重拉基础信息 —— 这一条只换库里的 password，那十三格里没有它。
 */
const passwordOpen = ref(false);
const accountNotice = ref('');

/**
 * 改基本资料那一颗：弹窗回填读的是这一块已经捞回来的那一行，所以没 row 就不给开（按钮也是这么挂的）。
 * 保存成功要重拉一次这一块 —— 昵称、地区这些正是画在那十三排上的，不拉就还是旧的一套。
 * 顶部那一颗头像/昵称不跟着动：那是登录时的会话快照，重新登录才换（同后台那一页）。
 */
const accountFormOpen = ref(false);

/**
 * 点开看原图的那一层放大：这一页三处头像 + 顶上那一排徽章挂件共用一个 ImagePreview，
 * 所以这三格存的是「要看的那一张 URL」「它是谁」「底下要不要补一句说明」，URL 空串就是没开。
 * 说明只有徽章那一处用（挂件上不再铺徽章文字，点开才给看），头像传空串。
 */
const previewSrc = ref('');
const previewAlt = ref('');
const previewCaption = ref('');

function openPreview(src: string, alt: string, caption = '') {
  previewSrc.value = src;
  previewAlt.value = alt;
  previewCaption.value = caption;
}

/**
 * 账号那一排（含挂件徽章）背后那张封面：站点静态资源，换封面换 `shop-web/public/images/profile-cover.jpg` 这一个文件就行。
 * 写的是根路径而不是 import —— astro 打包时 public 下原样搬到站点根，岛里那条 style 不会被重写，用 import 反而会拿到一张没搬的相对路径。
 */
const COVER_SRC = '/images/profile-cover.jpg';

function openAccountForm() {
  if (!account.row) return;
  accountNotice.value = '';
  accountFormOpen.value = true;
}

async function onAccountSaved() {
  accountFormOpen.value = false;
  await loadAccount();
  accountNotice.value = d.account.page.profileSaved;
}

function openPasswordForm() {
  accountNotice.value = '';
  passwordOpen.value = true;
}

function onPasswordSaved() {
  passwordOpen.value = false;
  accountNotice.value = d.account.page.passwordChanged;
}

/**
 * 正在等确认的那一颗（null = 没开）：这一页三处破坏性动作 —— 退出登录、删地址、删收藏 —— 共用同一个框，
 * 所以「点了确定要干什么」由调用方挂在 run 上带进来。
 * 错误不在这一格：run 自己把服务端那句话写回它那一块现成的红框里（别人的/已删的都回同一句「…不存在」，
 * 那句话直接当提示用），所以框关掉之后报错仍然留在它所属的那一块上面，不另起一个弹窗。
 */
interface PendingAction {
  title: string;
  message: string;
  confirmText: string;
  /** 确定那颗已经按下去了：请求还在路上时两颗按钮都压住，防连点把同一条删两次 */
  busy: boolean;
  run: () => Promise<void>;
}
const pending = ref<PendingAction | null>(null);

/** 点「确定」：跑完就收框 —— 成没成都一样收，失败那句留给 run 写进那一块的红框 */
async function runPending() {
  const action = pending.value;
  if (!action || action.busy) return;
  action.busy = true;
  try {
    await action.run();
  } finally {
    pending.value = null;
  }
}

function openAddressForm(row: AddressRow | null) {
  editingAddress.value = row;
  addressOpen.value = true;
}

/**
 * 存好一条地址：把这一块整块重头拉一次（新增、修改都走这一条）。
 * 「第一条自动设默认」「设默认清掉原来那条」都在服务端，本地那一行的 isDefault 盖不住别人的变化。
 */
async function reloadAddresses() {
  addresses.rows.splice(0);
  addresses.page = 0;
  addresses.pages = 0;
  addresses.total = 0;
  await load(addresses, fetchMyAddresses);
}

function onAddressSaved() {
  addressOpen.value = false;
  return reloadAddresses();
}

/**
 * 待支付那一单从列表上直接付：弹的是结算页那一颗同款支付框（OrderPayDialog），
 * 付成之后整块重拉 —— 状态从 0 待支付翻成 1 已支付是服务端那一趟干的，本地那一行盖不上，
 * 而且列表按状态打头排过，不重拉会出现「点过的排在原位、别的都跟着不动」。
 */
const payTarget = ref<OrderRow | null>(null);

async function reloadOrders() {
  orders.rows.splice(0);
  orders.page = 0;
  orders.pages = 0;
  orders.total = 0;
  await load(orders, fetchMyOrders);
}

async function onOrderPaid() {
  payTarget.value = null;
  await reloadOrders();
}

/** 一行一张单上那句「几种 / 件」：整句一格，英文那档是 "3 variants / 5 items"，不能拿中文那半截拼 */
function orderKindsPieces(row: OrderRow) {
  return fill(d.account.order.kindsPieces, { kinds: row.itemCount ?? 0, pieces: row.totalQuantity ?? 0 });
}

/**
 * 删一条地址：先开确认框（框里念出是哪一条），点「删除」才发。
 * 库里是逻辑删除，但前台没有回收站，对用户就是没了。
 * 删完照新增那条判据整块重拉 —— 删掉的正好是默认那条时服务端不补选，画不画得出「默认」得问库。
 */
function removeAddress(row: AddressRow) {
  const who = [row.receiverName, row.areaZh, row.detail].filter(Boolean).join(' · ') || d.account.address.thisOne;
  pending.value = {
    title: d.account.address.removeTitle,
    message: fill(d.account.address.removeConfirm, { who }),
    confirmText: d.common.delete,
    busy: false,
    run: async () => {
      addresses.error = '';
      try {
        await deleteMyAddress(row.id);
        await reloadAddresses();
      } catch (err) {
        addresses.error = (err as Error).message;
      }
    },
  };
}

/**
 * 广场消息那一档（左边菜单上和「个人信息」持平的一格）：改和删都只认自己发过的那些，归属那一刀压在服务层。
 * editingPlaza 必须是带 content 的那一份 —— 列表那条接口不取正文列，把行直接递进弹窗会开个空编辑器，
 * 所以点「修改」先读一次详情（待审核/被拒的那两条只有作者本人读得回来，正好就是这一档要看的那些）。
 */
const plazaFormOpen = ref(false);
const editingPlaza = ref<PlazaMessage | null>(null);
const plazaNotice = ref('');

/**
 * 这一档的三样筛选：关键字 + 关联类型 + 审核状态（后两档吃 FilterSelect，0 = 不限，同券那两档）。
 * 两档候选的真值里都有 0（关联类型的「无关联」、审核状态的「待审核」），和下拉里「不限」那颗哨兵撞了
 * ⇒ enumFilters 给它们各往上顶一格，发请求前再减回来。
 * 关键字另开一格 input：敲字不捞，回车或点「搜索」才进查询串（同广场列表那一页）。
 */
const plazaKeywordInput = ref('');
const plazaKeyword = ref('');
const plazaRelation = ref(0);
const plazaAudit = ref(0);
const plazaRelationOptions = computed(() => enumFilters(d.enum.plazaRelation, 1));
const plazaAuditOptions = computed(() => enumFilters(d.enum.plazaAuditStatus, 1));
const plazaFiltered = computed(() => plazaKeyword.value !== '' || plazaRelation.value > 0 || plazaAudit.value > 0);

function fetchPlazaMine(page: number, size: number) {
  const query: { keyword?: string; relationType?: number; auditStatus?: number } = {};
  if (plazaKeyword.value) query.keyword = plazaKeyword.value;
  if (plazaRelation.value > 0) query.relationType = plazaRelation.value - 1;
  if (plazaAudit.value > 0) query.auditStatus = plazaAudit.value - 1;
  return fetchMyPlazaPage(page, size, query);
}

/** 换筛选 = 回第 1 页整块重捞（同券那两档，别在旧那一串上接新页）；三格一起改，watch 也只跑一趟 */
async function refilterPlaza() {
  plazaNotice.value = '';
  await reloadPlaza();
}
watch([plazaKeyword, plazaRelation, plazaAudit], () => void refilterPlaza());

function applyPlazaKeyword() {
  const next = plazaKeywordInput.value.trim();
  if (next !== plazaKeyword.value) plazaKeyword.value = next;
}

function clearPlazaFilters() {
  plazaKeywordInput.value = '';
  plazaKeyword.value = '';
  plazaRelation.value = 0;
  plazaAudit.value = 0;
}

async function openPlazaForm(row: PlazaMessage) {
  plazaNotice.value = '';
  plaza.error = '';
  try {
    editingPlaza.value = await fetchPlaza(row.id);
    plazaFormOpen.value = true;
  } catch (err) {
    plaza.error = (err as Error).message;
  }
}

/** 改完整块重拉：审核状态被打回「待审核」是服务端那一趟干的，本地那一行盖不上（同地址那一块的做法） */
async function reloadPlaza() {
  plaza.rows.splice(0);
  plaza.page = 0;
  plaza.pages = 0;
  plaza.total = 0;
  await load(plaza, fetchPlazaMine);
}

function onPlazaSaved() {
  plazaFormOpen.value = false;
  editingPlaza.value = null;
  plazaNotice.value = d.plazaPublish.saved;
  return reloadPlaza();
}

/** 待审核/被拒那两档只在作者自己这一档里看得见，三档各给一种底色，一眼分得开（同广场卡片上那两枚徽标） */
function plazaAuditClass(status: number | null) {
  if (status === PLAZA_AUDIT_PASSED) return 'bg-green-50 text-green-700 dark:bg-green-900/40 dark:text-green-300';
  if (status === PLAZA_AUDIT_REJECTED) return 'bg-red-50 text-red-700 dark:bg-red-900/40 dark:text-red-300';
  return 'bg-amber-50 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300';
}

/**
 * 删一条广场消息：同一个确认框。
 * 库里是逻辑删除，但前台没有回收站，对用户就是没了；名下收到的赞和评论不去回收（后端也不动它们）。
 * 这一条不像地址有「默认互斥」会改到别的行，所以就地摘掉它、总数减一就够。
 */
function removePlaza(row: PlazaMessage) {
  pending.value = {
    title: d.account.plaza.removeTitle,
    message: fill(d.account.plaza.removeConfirm, { who: plazaLabel(row, d.account.plaza.thisOne) }),
    confirmText: d.common.delete,
    busy: false,
    run: async () => {
      plaza.error = '';
      try {
        await deletePlaza(row.id);
        plaza.rows = plaza.rows.filter((one) => one.id !== row.id);
        plaza.total = Math.max(plaza.total - 1, 0);
      } catch (err) {
        plaza.error = (err as Error).message;
      }
    },
  };
}

/**
 * 收藏行能不能点：按 target_type 分叉到各自那一页（商品 / 门店 / 品牌三档前台都有详情页了），
 * targetId 为空（对象已被删）也不给链接。
 */
function favoriteUrl(row: FavoriteRow): string {
  if (!row.targetId) return '';
  if (row.targetType === 1) return href(`/product/${row.targetId}`);
  if (row.targetType === 2) return href(`/store/${row.targetId}`);
  if (row.targetType === 3) return href(`/brand/${row.targetId}`);
  return '';
}

/**
 * 删一条收藏：同一个确认框。
 * 这一条只动这一行，所以就地摘掉它就行，不像地址那样删完要整块重拉（那一路重拉是因为默认互斥会改到别的行）。
 * 总数跟着减一 —— 不然菜单上那句「共 N 条」和底下那颗「再看 10 条」会一直多算这一条。
 */
function removeFavorite(row: FavoriteRow) {
  const what = enumLabel(d.enum.favoriteTarget, row.targetType, d.account.favorite.objectFallback);
  pending.value = {
    title: d.account.favorite.removeTitle,
    message: row.targetName
      ? fill(d.account.favorite.removeByName, { name: row.targetName })
      : fill(d.account.favorite.removeByType, { what }),
    confirmText: d.common.delete,
    busy: false,
    run: async () => {
      favorites.error = '';
      try {
        await deleteMyFavorite(row.id);
        const at = favorites.rows.indexOf(row);
        if (at >= 0) favorites.rows.splice(at, 1);
        favorites.total = Math.max(0, favorites.total - 1);
      } catch (err) {
        favorites.error = (err as Error).message;
      }
    },
  };
}

/** 账号类型这一档本地那张表不要了：枚举档 d.enum.accountType 下标就是码值，兜底统一走 d.enum.unknown 那一句 */
function accountTypeLabel(type: number | null | undefined) {
  return type == null ? '' : enumLabel(d.enum.accountType, type, unknownCode(type));
}

const accountTypeText = computed(() => accountTypeLabel(auth.user?.accountType));

/** 空列一律画共用档里那颗破折号：undefined 和空串在这页上是同一句话 */
function orDash(value: string | null | undefined) {
  return value || d.common.dash;
}

/**
 * 「个人信息 → 基础信息」那一格的十三排：顺序和文案照后台「消费者管理 → 个人信息」那一格抄，
 * 头像那一格是图，不在这儿（画在这排上面）。空列一律画「—」—— 后端那份实体是 NON_NULL，
 * 没填的列干脆不在 JSON 里，undefined 和空串在这页上是同一句话。
 */
const accountFields = computed<{ label: string; value: string; full?: boolean }[]>(() => {
  const user = account.row;
  if (!user) return [];
  return [
    { label: d.account.info.nickname, value: orDash(user.nickname) },
    { label: d.account.info.username, value: orDash(user.username) },
    { label: d.account.info.mobile, value: orDash(user.mobile) },
    { label: d.account.info.email, value: orDash(user.email) },
    {
      label: d.account.info.sex,
      value:
        user.sex == null ? d.common.dash : enumLabel(d.enum.sex, user.sex, unknownCode(user.sex)),
    },
    { label: d.account.info.accountType, value: accountTypeLabel(user.accountType) || d.common.dash },
    {
      label: d.account.info.status,
      value: user.status == null ? d.common.dash : user.status === 1 ? d.account.info.statusEnabled : d.account.info.statusDisabled,
    },
    { label: d.account.info.area, value: orDash(user.areaZh) },
    { label: d.account.info.detail, value: orDash(user.address) },
    { label: d.account.info.signature, value: orDash(user.signature), full: true },
    { label: d.account.info.roles, value: account.roles.join('、') || d.common.dash },
    { label: d.account.info.createdAt, value: orDash(user.createTime) },
    { label: d.account.info.lastLogin, value: orDash(user.lastLoginTime) },
  ];
});

/**
 * 积分账户那一格的六排：五个数读的都是库里存的那几格（不做现算），顺序和文案照后台「个人信息 → 积分」上面那块。
 * 建账时间只要「哪天」，跟着这一页其余日子那一格走。
 */
const pointCells = computed<{ label: string; value: string; strong?: boolean }[]>(() => {
  const one = points.row;
  if (!one) return [];
  return [
    { label: d.account.points.available, value: pointsOf(one.balance), strong: true },
    { label: d.account.points.totalEarned, value: pointsOf(one.totalPoints) },
    { label: d.account.points.totalUsed, value: pointsOf(one.usedPoints) },
    { label: d.account.points.frozen, value: pointsOf(one.frozenPoints) },
    { label: d.account.points.expiring, value: pointsOf(one.expirePoints) },
    { label: d.account.points.createdAt, value: dayOf(one.createTime) },
  ];
});

/**
 * 钱包账户那一格的八排：那几个数同样是库里存的、读时不做现算，顺序和文案照后台「个人信息 → 钱包」那一块。
 * 账户状态只在被冻住时才多说一句 —— 正常那一档是默认情形，占一整排没信息量。
 */
const walletCells = computed<{ label: string; value: string; strong?: boolean }[]>(() => {
  const one = wallet.row;
  if (!one) return [];
  return [
    { label: d.account.wallet.available, value: moneyOf(one.balance), strong: true },
    { label: d.account.wallet.frozen, value: moneyOf(one.frozenBalance) },
    { label: d.account.wallet.gift, value: moneyOf(one.giftBalance) },
    { label: d.account.wallet.totalRecharge, value: moneyOf(one.totalRecharge) },
    { label: d.account.wallet.totalConsume, value: moneyOf(one.totalConsume) },
    { label: d.account.wallet.totalWithdraw, value: moneyOf(one.totalWithdraw) },
    {
      label: d.account.wallet.status,
      value:
        one.status == null
          ? d.common.dash
          : enumLabel(d.enum.walletStatus, one.status, unknownCode(one.status)),
    },
    { label: d.account.wallet.createdAt, value: dayOf(one.createTime) },
  ];
});

function hasMore(block?: AnyBlock) {
  return !!block && block.page < block.pages;
}
async function load(block: AnyBlock, fetch: Fetcher) {
  if (block.loading) return;
  block.loading = true;
  block.error = '';
  try {
    const next = block.page + 1;
    const data = await fetch(next, PAGE_SIZE);
    block.rows.push(...data.records);
    block.page = next;
    block.pages = data.pages ?? 0;
    block.total = data.total ?? 0;
  } catch (err) {
    block.error = (err as Error).message;
  } finally {
    block.loading = false;
  }
}

/**
 * 基础信息那一格：一次一行，所以它不走上面那条 load()。
 * 菜单上切回来不重捞（这一页只有「修改」这一颗动得到它那几格，保存成功那头自己会调这一条回刷）。
 */
async function loadAccount() {
  if (account.loading) return;
  account.loading = true;
  account.error = '';
  try {
    const data = await fetchMyAccount();
    account.row = data.sysUser ?? null;
    account.roles = data.roles ?? [];
    account.loaded = true;
  } catch (err) {
    account.error = (err as Error).message;
  } finally {
    account.loading = false;
  }
}

/**
 * 积分账户那一格：也是一行，理由同上面那一条。
 * 一分都没获得过时后端回 null —— 那不是错，画成「还没有积分账户」（账户行由第一次积分变动带出来，后台也不建账）。
 */
async function loadPoints() {
  if (points.loading) return;
  points.loading = true;
  points.error = '';
  try {
    points.row = await fetchMyPoints();
    points.loaded = true;
  } catch (err) {
    points.error = (err as Error).message;
  } finally {
    points.loading = false;
  }
}

/**
 * 钱包账户那一格：也是一行，理由同上面那一条。
 * 一分钱都没进过账时后端回 null（这一行账是由第一次余额变动带出来的）—— 那不是错，画成「还没有钱包账户」。
 */
async function loadWallet() {
  if (wallet.loading) return;
  wallet.loading = true;
  wallet.error = '';
  try {
    wallet.row = await fetchMyWallet();
    wallet.loaded = true;
  } catch (err) {
    wallet.error = (err as Error).message;
  } finally {
    wallet.loading = false;
  }
}

/**
 * 充值 / 提现的弹窗：两颗共用一份表单，靠 walletAction 分档（弹窗里的标题、判据、发哪颗请求都跟着它走）。
 * 现成那一行账要带进弹窗：提现得当场比一比可用余额；还没开账时带 null，弹窗里按 0 元说。
 */
const walletFormOpen = ref(false);
const walletAction = ref<'recharge' | 'withdraw'>('recharge');

function openWalletForm(action: 'recharge' | 'withdraw') {
  walletAction.value = action;
  walletFormOpen.value = true;
}

/**
 * 充 / 提成功：上面那几个数正是这一笔改的，重拉一次账户行；流水那块多出的就是刚记下的这一笔，
 * 而它排在最前面，所以整块清回首页重拉（同地址那一块的做法，不这么办会出现「共 N 笔」还是旧的数）。
 * 第一次充值会把那一行账建出来 —— 之前挂的是空态，不重拉就一直空着。
 */
async function onWalletChanged() {
  walletFormOpen.value = false;
  wallet.loaded = false;
  await loadWallet();
  walletLogs.rows.splice(0);
  walletLogs.page = 0;
  walletLogs.pages = 0;
  walletLogs.total = 0;
  await load(walletLogs, fetchMyWalletLogs);
}

/**
 * 把「哪一格」摊成一条序列，好判这次是往后还是往前：个人信息下面那三个 tab 排在最前，其余四条菜单接着往后。
 * 只有左边菜单和上面那排 tab 各自换档时用得上（切这两样才会换右边那一卡的内容）。
 */
function viewRank(section: SectionKey, tab: InfoTabKey) {
  if (section === 'account') return INFO_TABS.value.findIndex((item) => item.key === tab);
  return INFO_TABS.value.length - 1 + MENUS.value.findIndex((item) => item.key === section);
}

/** 换档前先记下方向，<Transition> 才拿得到（key 一改旧的就要开始移了） */
function aimSlide(from: number, to: number) {
  slideName.value = to > from ? 'tab-next' : 'tab-prev';
}

/** tab 切过去才拉那一格（没点开的一概不发请求）；积分、钱包那两格两路一起拉 —— 上面账户行、下面流水第一页 */
function pickInfo(key: InfoTabKey) {
  if (key !== infoTab.value) aimSlide(viewRank(active.value, infoTab.value), viewRank(active.value, key));
  infoTab.value = key;
  if (key === 'base') {
    if (!account.loaded && !account.loading) void loadAccount();
    return;
  }
  if (key === 'points' && !points.loaded && !points.loading) void loadPoints();
  if (key === 'wallet' && !wallet.loaded && !wallet.loading) void loadWallet();
  const item = INFO_TABS.value.find((tab) => tab.key === key)!;
  if (item.block && item.block.page === 0 && !item.block.loading) void load(item.block, item.fetch!);
}

/** 菜单切过去才拉那一块（没点开的一概不发请求）；某一格挂了只红它自己那块，切走再切回来会重试 */
function pick(key: SectionKey) {
  if (key !== active.value) aimSlide(viewRank(active.value, infoTab.value), viewRank(key, infoTab.value));
  active.value = key;
  if (key === 'account') {
    // 停在哪个 tab 就拉哪一格：菜单上切走再切回来不该把三个 tab 都捞一遍
    pickInfo(infoTab.value);
    return;
  }
  const item = MENUS.value.find((menu) => menu.key === key)!;
  if (item.block && item.block.page === 0 && !item.block.loading) void load(item.block, item.fetch!);
  // 消息这一块头上的三档各顶一个未读数，那三个数不在列表那一趟里 ⇒ 每次切过来补捞一次
  if (key === 'messages') void loadUnreadSummary();
}

/** 顶部那一排徽章是入口：点哪一枚都跳到「个人信息 → 徽章」那一格（正停在别的菜单上就先切过来） */
function openBadgeTab() {
  pick('account');
  pickInfo('badge');
}

/**
 * 顶上那一排挂件：点一枚干什么 —— 有图就放大看那张原图（徽章文字跟着在这一层底下给出来），
 * 没图没什么可放大，就跳到那一格，那一格的左边那一格铺的就是它那句文字
 */
function openBadgePendant(row: BadgeRow) {
  if (row.badgeIcon) {
    openPreview(row.badgeIcon, badgeNameOf(row), badgeCaptionText(row));
  } else openBadgeTab();
}

/** 「再看 N 条」那一颗：基础信息、积分账户、钱包账户那三格没有下一页，那一格上 block 一定是有的 */
function loadMore() {
  const item = panel.value;
  if (!item.block || !item.fetch) return;
  void load(item.block, item.fetch);
}

const moreLoading = computed(() => panel.value.block?.loading ?? false);

/**
 * 签到会给分（那一笔分和流水是同一个事务里一起落的），所以日历上签成功要把积分那一格作废掉 ——
 * 不这么做，切到「积分」tab 看到的还是签之前那几个数，而「累计获得 / 可用」正是这一签改动的。
 * 当前正开着那一格就顺手重拉，没开着就只把「拉过没有」那一格抹掉，等切过去再拉。
 */
function onSigninChanged() {
  points.loaded = false;
  pointLogs.rows.splice(0);
  pointLogs.page = 0;
  pointLogs.pages = 0;
  pointLogs.total = 0;
  if (active.value === 'account' && infoTab.value === 'points') pickInfo('points');
}

/** 积分是整数，空格子（脏数据）当 0 说，别让那几个数跳出一片空白（同后台那一页） */
function pointsOf(value: number | null) {
  return String(value ?? 0);
}

/** 流水里的变动数：库里就是带符号存的（获取正、消耗负），补个正号让进出一眼分开 */
function signedPoints(value: number | null) {
  const count = value ?? 0;
  return count > 0 ? `+${count}` : String(count);
}

/** 那一格现在画的是哪一条菜单：三个 tab 只在「个人信息」那一条下面出现 */
const isInfo = computed(() => active.value === 'account');

/**
 * 退出登录也先开确认框（这一页第三处）：点「退出」才发。
 * 请求途中 signingOut 那一格把整页内容压住 —— token 一清那几块列表会各闪一次「请先登录」；
 * 确认框本身挂在那个闸门外面，所以它不会被自己这一次退出闪掉。
 * 服务端那句「退出失败」在这儿接住不用：store 的 finally 已经把本地 token 清了，会话实际已经结束，
 * 不接住的话它只会变成一条控制台里的 unhandled rejection，页面上什么也不说。
 */
function askSignOut() {
  pending.value = {
    title: d.account.page.signOut,
    message: fill(d.account.page.signOutConfirm, { name: nickname.value }),
    confirmText: d.account.page.signOutAction,
    busy: false,
    run: async () => {
      signingOut.value = true;
      try {
        await auth.signOut();
      } catch {
        // 见上面那句：清本地是 store 里 finally 干的，成功失败都一样
      } finally {
        signingOut.value = false;
      }
    },
  };
}

/** 后端给的是 'yyyy-MM-dd HH:mm:ss'，这一页只要「哪天」 */
function dayOf(value: string | null) {
  return value ? value.slice(0, 10) : d.common.dash;
}

function moneyOf(value: number | null) {
  return value == null ? d.common.dash : `¥${value.toFixed(2)}`;
}

/**
 * 流水里的变动金额：库里带符号存（收入为正、支出为负），补个正号让进出一眼分开。
 * 绝对值再拼符号，免得出现「¥-12.00」和「-¥12.00」两种写法混在这一列里。
 */
function signedMoney(value: number | null) {
  const amount = value ?? 0;
  const text = `¥${Math.abs(amount).toFixed(2)}`;
  return amount > 0 ? `+${text}` : amount < 0 ? `-${text}` : text;
}

/**
 * 页头铃铛面板底部「查看全部消息」带 `?section=messages` 进来：开局就停在菜单上那一格，
 * 不再先捞个人信息/徽章那两趟。认不出的值（手敲的、旧链接）一律当没这格，照默认走。
 */
function initialSection(): SectionKey {
  const wanted = new URLSearchParams(window.location.search).get('section');
  return wanted && wanted !== 'account' && MENUS.value.some((item) => item.key === wanted) ? (wanted as SectionKey) : 'account';
}

onMounted(() => {
  ready.value = true;
  if (!auth.isLoggedIn) return;
  // 只拉进来就看到的那一格（默认个人信息 → 基础信息，深链进来就是深链那格）：另两个 tab、另外四条菜单都等点开
  pick(initialSection());
  // 只有顶部那一排徽章是例外 —— 它不等点开，进来就得有（捞的是同一个 block，等下切到那一格不会重捞）
  if (badges.page === 0 && !badges.loading) void load(badges, fetchMyBadges);
});
</script>

<template>
  <div class="space-y-6">
    <h1 class="text-2xl font-semibold">{{ d.account.page.title }}</h1>

    <!-- 没登录这一页就没内容：给一颗开登录窗的按钮（头部那颗岛认 data-open-login） -->
    <div
      v-if="!signedIn"
      class="rounded-xl border border-gray-200 bg-white p-8 text-center dark:border-gray-700 dark:bg-gray-800"
    >
      <p class="text-gray-600 dark:text-gray-300">
        {{ d.account.page.needLogin }}
      </p>
      <button
        type="button"
        data-open-login
        class="mt-4 rounded-lg bg-blue-600 px-5 py-2 text-sm font-medium text-white hover:bg-blue-700"
      >
        {{ d.auth.entry }}
      </button>
    </div>

    <template v-else-if="!signingOut">
      <!-- 账号那一排：退出登录挂在这儿（它管的不是某一列表格）；签到那一颗搬进「个人信息 → 基础信息」那一格 -->
      <section class="relative overflow-hidden rounded-xl border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800">
        <!-- 封面：整张卡铺一层实景（图是站点静态资源，见上面 COVER_SRC），上面再压一层往左的底色渐变 ——
             压住的那一段左边放字、下面挂挂件，右边那一段山留在外面当封面；
             落在封面上的文字一律走 .on-cover（白字 + 差值混合），底下浅就显深、底下深就显浅，
             所以压不压这层渐变都读得清，也不用再给每一处配一套 dark: 色 -->
        <div
          aria-hidden="true"
          class="pointer-events-none absolute inset-0 bg-cover bg-center"
          :style="{ backgroundImage: `url(${COVER_SRC})` }"
        ></div>
        <div
          aria-hidden="true"
          class="pointer-events-none absolute inset-0 bg-gradient-to-r from-white from-35% to-transparent to-80% dark:from-gray-800"
        ></div>

        <div class="relative flex flex-wrap items-center gap-4 p-5">
          <!-- 头像点开是原图（那一颗只是缩略圆，图本身可能比这一格大得多）；没图就还是那颗占位 svg，不给挂一个点开空白的按钮 -->
          <button
            v-if="avatar"
            type="button"
            :aria-label="d.account.info.zoomAvatar"
            class="h-14 w-14 shrink-0 cursor-zoom-in overflow-hidden rounded-full border border-gray-200 dark:border-gray-600"
            @click="openPreview(avatar || '', d.account.info.avatarLabel)"
          >
            <img :src="avatar" :alt="nickname" class="h-full w-full object-cover" @error="avatarBroken = true" />
          </button>
          <span
            v-else
            class="flex h-14 w-14 shrink-0 items-center justify-center overflow-hidden rounded-full border border-gray-200 text-gray-400 dark:border-gray-600"
          >
            <svg class="h-8 w-8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
              <circle cx="12" cy="8" r="4" />
              <path stroke-linecap="round" d="M4.5 20a7.5 7.5 0 0 1 15 0" />
            </svg>
          </span>
          <div class="min-w-0 flex-1">
            <p class="on-cover text-lg font-medium">{{ nickname }}</p>
            <p class="on-cover text-sm">
              {{ d.account.info.username }} {{ auth.user?.username || d.common.dash }}
              <span v-if="accountTypeText"> · {{ accountTypeText }}</span>
            </p>
          </div>
          <button
            type="button"
            class="rounded-lg border border-gray-300 px-4 py-2 text-sm hover:bg-gray-50 dark:border-gray-600 dark:hover:bg-gray-700"
            @click="askSignOut"
          >
            <span class="on-cover">{{ d.account.page.signOut }}</span>
          </button>
        </div>

        <!-- 名下一枚一枚徽章挂成挂件：读的还是「个人信息 → 徽章」那一份的第一页（不另发请求），
             一根挂杆横铺在卡片上，每枚是一根绳 + 一枚圆牌，左右各歪一点像挂着晃（这一歪和悬停摆都在文件末尾那份样式里）；
             圆牌只铺图，下面不铺文字（名称只在悬停的 title 里，点开那枚才给看：有图的在放大层底下那一行，没图的跳到那一格，那一格左边就是它的文字），
             过期的灰着排在有效的那几枚后面；正在拉的时候这一排先以空牌占位（跟账号那一排一起出现，
             免得数据回来那一刻卡片往下撑一下），一枚都没有时整块收掉，但拉失败了要留着那句提示（见下面的重试） -->
        <div v-if="badges.rows.length || badges.loading || badges.error" class="relative px-5 pb-5">
          <div class="flex items-baseline justify-between gap-3">
            <span v-if="headBadges.length" class="on-cover text-xs">
              {{ fill(d.account.badge.pendantCount, { n: badges.total, unit: d.account.units.badge }) }}
            </span>
            <!-- 全被收起来了：这一排没牌子可挂，但这一句要留着，不然看着像整排坏了（放回来点右边「查看全部」进徽章那一格） -->
            <span v-else-if="badges.rows.length" class="on-cover text-xs">{{ d.account.badge.allHidden }}</span>
            <span v-else-if="badges.loading" class="on-cover text-xs">{{ d.account.badge.pendantLoading }}</span>
            <span v-else class="on-cover text-xs">{{ fill(d.account.badge.pendantFailed, { error: badges.error }) }}</span>
            <button
              v-if="badges.rows.length"
              type="button"
              class="on-cover shrink-0 text-xs hover:underline"
              @click="openBadgeTab"
            >
              {{ d.account.page.viewAll }}
            </button>
            <!-- 这一排拉失败时不能整块蒸发：那样除了刷新没有第二条路（徽章那一格也在这一份数据上） -->
            <button
              v-else-if="badges.error"
              type="button"
              class="on-cover shrink-0 text-xs hover:underline"
              @click="load(badges, fetchMyBadges)"
            >
              {{ d.account.page.retry }}
            </button>
          </div>
          <!-- 往右挂：封面露出来的那一段在卡片右边（左边那一条被遮罩压回底色好放字），所以挂件一排收在右沿。
               杆整根横铺在这一排上面（一根到底），每枚只有绳以下会摆，所以绳的头顶始终落在杆上。
               showPendants：一枚都没拉回来时这两段还要留（空牌占位），拉回来了但全被收起就整排收掉 -->
          <div v-if="showPendants" class="rod mt-2" aria-hidden="true"></div>
          <ul v-if="showPendants" class="flex flex-wrap items-start justify-end gap-y-3">
            <li v-for="row in headBadges" :key="row.id" class="pendant w-16">
              <button
                type="button"
                class="badge-body flex w-full flex-col items-center gap-1 pb-1 text-center"
                :class="row.badgeIcon ? 'cursor-zoom-in' : 'cursor-pointer'"
                :title="badgePendantTitle(row)"
                @click="openBadgePendant(row)"
              >
                <span class="rope" aria-hidden="true"></span>
                <span
                  class="flex h-11 w-11 items-center justify-center overflow-hidden rounded-full border-2 border-white bg-white shadow ring-1 ring-gray-200 dark:border-gray-600 dark:bg-gray-700 dark:ring-gray-500"
                >
                  <img
                    v-if="row.badgeIcon"
                    :src="row.badgeIcon"
                    :alt="badgeNameOf(row)"
                    class="h-full w-full object-cover"
                    :class="badgeValid(row) ? '' : 'opacity-40 grayscale'"
                  />
                  <!-- 老徽章没配图（图标改成必填之前发出去的那些）：圆牌上铺一枚描边奖章当占位符，
                       描边那套同头像占位那颗（viewBox 24）；不往上面写字，点开还是跳徽章那一格 -->
                  <svg
                    v-else
                    class="h-6 w-6 text-gray-400 dark:text-gray-500"
                    :class="badgeValid(row) ? '' : 'opacity-40'"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.8"
                    aria-hidden="true"
                  >
                    <circle cx="12" cy="9" r="5.25" />
                    <path stroke-linecap="round" stroke-linejoin="round" d="M9.2 13.7 7.8 21l4.2-2.3 4.2 2.3-1.4-7.3" />
                  </svg>
                </span>
              </button>
            </li>
            <!-- 还没拉回来：先挂三枚空牌占住这一排的位置，挂绳、圆牌两段都按真挂件的尺寸铺，
                 数据回来只是把里面换成真的，卡片不再往下撑一下（拉失败时这三枚灰着留着，上面那句提示和重试在） -->
            <template v-if="!badges.rows.length">
              <li
                v-for="n in 3"
                :key="`pending-${n}`"
                class="pendant is-pending w-16"
                :class="{ 'is-loading': badges.loading }"
                aria-hidden="true"
              >
                <div class="badge-body flex w-full flex-col items-center gap-1 pb-1 opacity-50">
                  <span class="rope" aria-hidden="true"></span>
                  <span
                    class="h-11 w-11 rounded-full border-2 border-white bg-gray-200 shadow ring-1 ring-gray-200 dark:border-gray-600 dark:bg-gray-700 dark:ring-gray-500"
                  ></span>
                </div>
              </li>
            </template>
          </ul>
        </div>
      </section>

      <div class="flex flex-col gap-6 lg:flex-row" :class="{ 'overflow-x-clip': sliding }">
        <!-- 菜单：一次只看一块，右边那一列跟着这一格换 -->
        <nav
          class="shrink-0 lg:w-44"
          :aria-label="d.account.page.navAria"
        >
          <ul class="flex gap-1 overflow-x-auto lg:flex-col lg:gap-0 lg:space-y-1 lg:rounded-xl lg:border lg:border-gray-200 lg:bg-white lg:p-1 dark:lg:border-gray-700 dark:lg:bg-gray-800">
            <li v-for="menu in MENUS" :key="menu.key" class="shrink-0">
              <button
                type="button"
                class="w-full whitespace-nowrap rounded-lg px-3 py-2 text-left text-sm transition-colors"
                :class="
                  active === menu.key
                    ? 'bg-blue-50 font-medium text-blue-700 dark:bg-blue-900/40 dark:text-blue-300'
                    : 'text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-700'
                "
                :aria-current="active === menu.key ? 'page' : undefined"
                @click="pick(menu.key)"
              >
                {{ menu.label }}
                <!-- 只有消息这一条头上顶红点数：它说的不是「这一格有几行」而是「还有几条没读」，别的那几条没有这个概念 -->
                <span
                  v-if="menu.key === 'messages' && unread.total"
                  class="ml-1 inline-block rounded-full bg-red-500 px-1.5 text-xs leading-5 text-white"
                  >{{ unread.total }}</span
                >
              </button>
            </li>
          </ul>
        </nav>

        <!-- 切左边那列菜单 = 整张卡换掉：卡跟着 active 换 key ⇒ Vue 认成换了一个元素，<Transition> 把旧那卡左右移走完事再挂新的
             （方向由 pick 换档前先记进 slideName，CSS 在 global.css 最后那一条）。
             sliding 只在这段过渡里挂 overflow-x-clip：28px 的位移会探出容器，不裁就闪一条横向滚动条 ——
             上面那行裁的是「卡自己移」，卡这一层裁的是「卡里面那块内容移」（它不出卡的圆角边框）。 -->
        <Transition :name="slideName" mode="out-in" @before-leave="sliding = true" @after-enter="sliding = false">
          <section
            :key="active"
            class="min-w-0 flex-1 rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-700 dark:bg-gray-800"
            :class="{ 'overflow-x-clip': sliding }"
          >
            <div class="mb-3 flex items-baseline justify-between">
              <h2 class="font-medium">{{ current.label }}</h2>
              <span v-if="countText" class="text-sm text-gray-500 dark:text-gray-400">{{ countText }}</span>
            </div>
            <!-- 个人信息那一块的五个 tab：底下这几格都在同一个账号上，只是看的角度不同（资料 / 券 / 徽章 / 分 / 钱） -->
            <div v-if="isInfo" class="mb-4 flex gap-1 border-b border-gray-200 dark:border-gray-700">
              <button
                v-for="tab in INFO_TABS"
                :key="tab.key"
                type="button"
                class="-mb-px border-b-2 px-3 py-2 text-sm transition-colors"
                :class="
                  infoTab === tab.key
                    ? 'border-blue-600 font-medium text-blue-700 dark:border-blue-400 dark:text-blue-300'
                    : 'border-transparent text-gray-600 hover:border-gray-300 hover:text-gray-800 dark:text-gray-300 dark:hover:border-gray-600 dark:hover:text-gray-100'
                "
                :aria-current="infoTab === tab.key ? 'page' : undefined"
                @click="pickInfo(tab.key)"
              >
                {{ tab.label }}
              </button>
            </div>
            <!-- 切上面那排 tab 只换下面这一块：卡的边框、标题、那排 tab 都留在原地，这一块跟着 infoTab 换 key ⇒ 左右移进移出。
                 另外四条菜单没有 tab（infoTab 一直是进来的那一档）⇒ 这一路不会自己动起来，切它们动的是整张卡。 -->
            <Transition :name="slideName" mode="out-in" @before-leave="sliding = true" @after-enter="sliding = false">
              <div :key="infoTab">
                <!-- 基础信息那一格的 toolbar：签到、改资料、改密码都是这个账号自己的动作，挂在它下面那一排（同收货地址那颗「添加地址」的位置） -->
                <div v-if="isInfo && infoTab === 'base'" class="mb-3">
                  <div class="flex justify-end gap-2">
                    <button
                      type="button"
                      class="rounded-lg border border-gray-300 px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
                      @click="signinOpen = true"
                    >
                      {{ d.account.info.signinRecords }}
                    </button>
                    <button
                      type="button"
                      class="rounded-lg border border-gray-300 px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
                      @click="openPasswordForm"
                    >
                      {{ d.account.info.changePassword }}
                    </button>
                    <!-- 回填读的是这一块捞回来的那一行，没 row 点开就是个空表，所以这一颗跟着数据走 -->
                    <button
                      v-if="account.row"
                      type="button"
                      class="rounded-lg bg-blue-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-blue-700"
                      @click="openAccountForm"
                    >
                      {{ d.common.edit }}
                    </button>
                  </div>
                  <!-- 改成功这一句只能在这儿说：那一个框收掉之后页面上没别的地方，不吭声用户不知道有没有改成 -->
                  <p v-if="accountNotice" class="mt-2 text-right text-sm text-green-600 dark:text-green-400">
                    {{ accountNotice }}
                  </p>
                </div>
                <!-- 收货地址那一块的 toolbar：往这一块加东西的那颗挂在表格上面（添加地址只有这一路是「加」，删除都是逐行那颗） -->
                <div v-if="active === 'addresses'" class="mb-3 flex justify-end">
                  <button
                    type="button"
                    class="rounded-lg bg-blue-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-blue-700"
                    @click="openAddressForm(null)"
                  >
                    {{ d.account.address.add }}
                  </button>
                </div>
                <!-- 钱包那一格的 toolbar：钱进钱出的两颗挂在那本账上面。
                     两颗都不跟着数据走：还没开账时充值正是把那一行账建出来的动作，提现点开了弹窗会说清能提的是 0 元。 -->
                <div v-if="isInfo && infoTab === 'wallet'" class="mb-3 flex justify-end gap-2">
                  <button
                    type="button"
                    class="rounded-lg border border-gray-300 px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
                    @click="openWalletForm('withdraw')"
                  >
                    {{ d.account.wallet.withdraw }}
                  </button>
                  <button
                    type="button"
                    class="rounded-lg bg-blue-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-blue-700"
                    @click="openWalletForm('recharge')"
                  >
                    {{ d.account.wallet.recharge }}
                  </button>
                </div>

                <!-- 消息那一块的 toolbar：三档筛选（各档头上顶着自己的未读数）+「全部已读」挂在列表上面。
                     筛选只挑看哪一串；「全部已读」一次清掉所有未读、不跟着筛选档走，下面那一排多选按钮管的才是勾上的那几条。 -->
                <div v-if="active === 'messages'" class="mb-3 flex flex-wrap items-center gap-2">
                  <button
                    v-for="filter in MESSAGE_FILTERS"
                    :key="filter.value"
                    type="button"
                    :disabled="messages.loading"
                    class="rounded-full border px-3 py-1 text-sm transition-colors disabled:opacity-60"
                    :class="
                      messageCategory === filter.value
                        ? 'border-blue-600 bg-blue-50 font-medium text-blue-700 dark:border-blue-400 dark:bg-blue-900/40 dark:text-blue-300'
                        : 'border-gray-300 text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700'
                    "
                    @click="messageCategory = filter.value, refilterMessages()"
                  >
                    {{ filter.label }}
                    <span v-if="filter.count" class="ml-1 text-xs">{{ filter.count }}</span>
                  </button>
                  <button
                    v-if="unread.total"
                    type="button"
                    :disabled="messages.loading"
                    class="ml-auto text-sm text-blue-600 hover:underline disabled:opacity-60 dark:text-blue-400"
                    @click="markMessagesRead"
                  >
                    {{ d.account.message.markAllRead }}
                  </button>
                </div>
                <!-- 多选那一排：勾了才有的两颗批量按钮，管的只是当前已加载的这些行 -->
                <div v-if="active === 'messages' && messages.rows.length" class="mb-3 flex flex-wrap items-center gap-2">
                  <label class="flex items-center gap-1.5 text-sm text-gray-600 dark:text-gray-300">
                    <input
                      type="checkbox"
                      class="h-4 w-4 accent-blue-600"
                      :checked="allMessagesSelected"
                      @change="toggleSelectAllMessages"
                    />
                    {{ d.account.message.selectAll }}
                  </label>
                  <span v-if="selectedIds.length" class="text-xs text-gray-500 dark:text-gray-400">
                    {{ fill(d.account.message.selectedCount, { count: selectedIds.length }) }}
                  </span>
                  <button
                    type="button"
                    :disabled="!selectedIds.length || messages.loading"
                    class="rounded-lg border border-gray-300 px-3 py-1 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-60 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
                    @click="readSelectedMessages"
                  >
                    {{ d.account.message.readSelected }}
                  </button>
                  <button
                    type="button"
                    :disabled="!selectedIds.length || messages.loading"
                    class="rounded-lg border border-red-300 px-3 py-1 text-sm text-red-600 hover:bg-red-50 disabled:opacity-60 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-900/30"
                    @click="removeSelectedMessages"
                  >
                    {{ d.account.message.deleteSelected }}
                  </button>
                </div>
                <!-- 「全部已读」「已删除」这两句只能在这儿说：动作做完列表就重拉了，没别的地方留提示 -->
                <p v-if="active === 'messages' && messageNotice" class="mb-3 text-right text-sm text-green-600 dark:text-green-400">
                  {{ messageNotice }}
                </p>

                <p
                  v-if="sectionError"
                  class="mb-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300"
                >
                  {{ sectionError }}
                </p>

                <!-- 个人信息那一块的五格：基础信息 / 优惠券 / 徽章 / 积分 / 钱包 -->
                <template v-else-if="isInfo">
                  <!-- 基础信息：一个账号一行，两列排下来（形状照后台那一格的 el-descriptions :column="2"） -->
                  <template v-if="infoTab === 'base'">
                    <dl v-if="account.row" class="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">
                      <div>
                        <dt class="text-xs text-gray-500 dark:text-gray-400">{{ d.account.info.avatarLabel }}</dt>
                        <dd class="mt-1">
                          <button
                            v-if="account.row.avatar"
                            type="button"
                            :aria-label="d.account.info.zoomAvatar"
                            class="h-14 w-14 cursor-zoom-in overflow-hidden rounded-full border border-gray-200 dark:border-gray-600"
                            @click="openPreview(account.row.avatar || '', d.account.info.avatarLabel)"
                          >
                            <img :src="account.row.avatar" :alt="account.row.nickname || d.account.info.avatarLabel" class="h-full w-full object-cover" />
                          </button>
                          <span
                            v-else
                            class="flex h-14 w-14 items-center justify-center overflow-hidden rounded-full border border-gray-200 text-gray-400 dark:border-gray-600"
                          >
                            <svg class="h-8 w-8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
                              <circle cx="12" cy="8" r="4" />
                              <path stroke-linecap="round" d="M4.5 20a7.5 7.5 0 0 1 15 0" />
                            </svg>
                          </span>
                        </dd>
                      </div>
                      <div v-for="field in accountFields" :key="field.label" :class="field.full ? 'sm:col-span-2' : ''">
                        <dt class="text-xs text-gray-500 dark:text-gray-400">{{ field.label }}</dt>
                        <dd class="mt-1 text-sm">{{ field.value }}</dd>
                      </div>
                    </dl>
                    <p v-else class="text-sm text-gray-500 dark:text-gray-400">
                      {{ account.loading ? d.common.loading : d.account.info.empty }}
                    </p>
                  </template>

                  <!-- 优惠券：两档筛选摆在券上面（它们换的就是底下这一串），一行一张券。
                       面额那几格读的是发放时定格的快照，后台改了模板也不会串到已发到手上的券 -->
                  <template v-else-if="infoTab === 'coupon'">
                    <div class="mb-3 flex flex-wrap items-center gap-2">
                      <FilterSelect
                        v-model="couponStatus"
                        :options="couponStatusOptions"
                        :label="d.account.coupon.statusFilter"
                        :disabled="coupons.loading"
                      />
                      <FilterSelect
                        v-model="couponType"
                        :options="couponTypeOptions"
                        :label="d.account.coupon.typeFilter"
                        :disabled="coupons.loading"
                      />
                      <button
                        v-if="couponFiltered"
                        type="button"
                        :disabled="coupons.loading"
                        class="text-sm text-blue-600 hover:underline disabled:opacity-60 dark:text-blue-400"
                        @click="clearCouponFilters"
                      >
                        {{ d.account.coupon.clearFilters }}
                      </button>
                    </div>

                    <ul class="divide-y divide-gray-100 dark:divide-gray-700">
                      <li v-for="row in coupons.rows" :key="row.id" class="flex items-start gap-3 py-3">
                        <!-- 图标只表意（读屏当装饰），类型那三个字在最后一行照说 -->
                        <span
                          class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg"
                          :class="couponToneOf(row.couponType)"
                          aria-hidden="true"
                        >
                          <svg
                            class="h-6 w-6"
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            stroke-width="1.8"
                            stroke-linecap="round"
                            stroke-linejoin="round"
                          >
                            <path :d="couponIconOf(row.couponType)" />
                          </svg>
                        </span>
                        <div class="min-w-0 flex-1">
                          <div class="flex items-baseline justify-between gap-3 text-sm">
                            <span class="min-w-0 truncate font-medium">{{ couponNameOf(row) }}</span>
                            <span
                              class="shrink-0 rounded px-2 py-0.5 text-xs"
                              :class="
                                row.status === 0
                                  ? 'bg-green-50 text-green-700 dark:bg-green-900/40 dark:text-green-300'
                                  : 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
                              "
                            >
                              {{ enumLabel(d.enum.couponUserStatus, row.status ?? 0, unknownCode(row.status)) }}
                            </span>
                          </div>
                          <div class="mt-1 flex items-baseline justify-between gap-3">
                            <span class="truncate text-sm text-red-500 dark:text-red-400">{{ couponBenefit(i18n, row) }}</span>
                            <span v-if="row.expireTime" class="shrink-0 text-xs text-gray-500 dark:text-gray-400">
                              {{ couponValidUntil(row) }}
                            </span>
                          </div>
                          <p class="mt-1 truncate text-xs text-gray-500 dark:text-gray-400">
                            {{ enumLabel(d.enum.couponType, row.couponType, d.enum.coupon) }} · {{ couponReceived(row) }}
                            <template v-if="row.getWay != null"> · {{ enumLabel(d.enum.getWay, row.getWay, unknownCode(row.getWay)) }}</template>
                            <template v-if="row.useTime"> · {{ couponUsed(row) }}</template>
                            <template v-if="row.orderNo"> · {{ couponOrderNo(row) }}</template>
                          </p>
                        </div>
                      </li>
                      <li v-if="!coupons.rows.length && !coupons.loading" class="py-3 text-sm text-gray-500 dark:text-gray-400">
                        {{ couponFiltered ? d.account.coupon.emptyFiltered : d.account.coupon.empty }}
                      </li>
                    </ul>
                  </template>

                  <!-- 徽章：一枚一行（形状照上面优惠券那一格）。有图标就只铺图标（圆形裁切），
                       没配图是一行普通文字，不压底色；名称/文字/图标读的都是发放那一刻定格的快照，
                       后台后来改了徽章不会串到已经拿到手的这一枚上。
                       每行右边那颗开关改的是 is_show 一列 —— 只有账号卡下面那一排挂件读它，
                       这一列本身全铺，收起来的也留在里面，不然没有地方放回来 -->
                  <template v-else-if="infoTab === 'badge'">
                    <p v-if="badgeShowError" class="mb-2 text-sm text-red-600 dark:text-red-400">
                      {{ badgeShowError }}
                    </p>
                    <ul class="divide-y divide-gray-100 dark:divide-gray-700">
                      <li v-for="row in badges.rows" :key="row.id" class="flex items-start gap-3 py-3">
                        <img
                          v-if="row.badgeIcon"
                          :src="row.badgeIcon"
                          :alt="badgeNameOf(row)"
                          class="h-10 w-10 shrink-0 rounded-full object-cover"
                        />
                        <span v-else class="w-10 shrink-0 break-all text-center text-xs leading-tight">
                          {{ row.badgeText || row.badgeName }}
                        </span>
                        <div class="min-w-0 flex-1">
                          <div class="flex items-baseline justify-between gap-3 text-sm">
                            <span class="min-w-0 truncate font-medium">{{ badgeNameOf(row) }}</span>
                            <span
                              class="shrink-0 rounded px-2 py-0.5 text-xs"
                              :class="
                                badgeValid(row)
                                  ? 'bg-green-50 text-green-700 dark:bg-green-900/40 dark:text-green-300'
                                  : 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
                              "
                            >
                              {{ badgeStatusText(row) }}
                            </span>
                          </div>
                          <p class="mt-1 truncate text-xs text-gray-500 dark:text-gray-400">
                            {{ enumLabel(d.enum.badgeType, row.badgeType, d.enum.badge) }} · {{ badgeReceived(row) }}
                            <template v-if="row.source != null"> · {{ enumLabel(d.enum.badgeSource, row.source, unknownCode(row.source)) }}</template>
                            · {{ badgeValidText(row) }}
                          </p>
                        </div>
                        <!-- 这一枚要不要挂在账号卡下面那一排：开关开着（is_show=1）就挂，关掉就不挂。
                             只管 is_show 一列，名称/形状/有效期都不碰；改失败那句摆在列表上面。
                             这颗用 flowbite-vue 的 FwbToggle，不自己拼 div：它那一套 peer-checked: 工具类是
                             组件自带、扫描结果一直在样式表里（见 global.css 那两行 @source），
                             自己新写的几颗（translate-x-4 / ring-blue-600 这些）在客户端换页进来的那趟里查不到，
                             开关就会塌成一片没底色的东西，得整页刷新才回来 -->
                        <FwbToggle
                          reverse
                          size="sm"
                          :model-value="badgeShown(row)"
                          :label="badgeShown(row) ? d.account.badge.show : d.account.badge.hide"
                          :disabled="badgeShowBusy !== null"
                          wrapper-class="ml-auto shrink-0 self-center"
                          @update:model-value="toggleBadgeShow(row, $event as boolean)"
                        />
                      </li>
                      <li v-if="!badges.rows.length && !badges.loading" class="py-3 text-sm text-gray-500 dark:text-gray-400">
                        {{ d.account.badge.empty }}
                      </li>
                    </ul>
                  </template>

                  <!-- 积分：上面一块是那本账的五个数（库里存的），下面一张表是这本账的进出明细（同后台那一格的形状） -->
                  <template v-else-if="infoTab === 'points'">
                    <dl
                      v-if="points.row"
                      class="mb-4 grid grid-cols-2 gap-x-6 gap-y-3 border-b border-gray-100 pb-4 sm:grid-cols-3 dark:border-gray-700"
                    >
                      <div v-for="cell in pointCells" :key="cell.label">
                        <dt class="text-xs text-gray-500 dark:text-gray-400">{{ cell.label }}</dt>
                        <dd class="mt-1 text-sm" :class="cell.strong ? 'text-lg font-semibold text-red-500 dark:text-red-400' : ''">
                          {{ cell.value }}
                        </dd>
                      </div>
                    </dl>
                    <!-- 账户行是由积分动作建出来的，一条都没获得过就没有这一行，不是数据丢了 -->
                    <p v-else class="mb-4 text-sm text-gray-500 dark:text-gray-400">
                      {{ points.loading ? d.common.loading : d.account.points.emptyAccount }}
                    </p>

                    <ul class="divide-y divide-gray-100 dark:divide-gray-700">
                      <li v-for="row in pointLogs.rows" :key="row.id" class="flex items-center gap-3 py-3">
                        <span class="min-w-0 flex-1">
                          <span class="block truncate text-sm">{{ enumLabel(d.enum.pointsBizType, row.bizType ?? 0, unknownCode(row.bizType)) }}</span>
                          <span class="block truncate text-xs text-gray-500 dark:text-gray-400">
                            {{ dayOf(row.createTime) }}
                            <template v-if="row.description"> · {{ row.description }}</template>
                            <template v-if="row.bizNo"> · {{ row.bizNo }}</template>
                          </span>
                        </span>
                        <span
                          class="shrink-0 text-sm"
                          :class="(row.points ?? 0) < 0 ? 'text-gray-500 dark:text-gray-400' : 'text-red-500 dark:text-red-400'"
                        >
                          {{ signedPoints(row.points) }}
                        </span>
                        <span class="w-16 shrink-0 text-right text-xs text-gray-500 dark:text-gray-400">
                          {{ d.account.points.balanceLabel }} {{ pointsOf(row.balance) }}
                        </span>
                      </li>
                      <li v-if="!pointLogs.rows.length && !pointLogs.loading" class="py-3 text-sm text-gray-500 dark:text-gray-400">
                        {{ d.account.points.emptyLogs }}
                      </li>
                    </ul>
                  </template>

                  <!-- 钱包：上面一块是那本账的几个数（同样是库里存的，不做现算），下面一张表是余额的进出明细
                       （形状照上面积分那一格，也照后台「个人信息 → 钱包」那一块） -->
                  <template v-else>
                    <dl
                      v-if="wallet.row"
                      class="mb-4 grid grid-cols-2 gap-x-6 gap-y-3 border-b border-gray-100 pb-4 sm:grid-cols-3 dark:border-gray-700"
                    >
                      <div v-for="cell in walletCells" :key="cell.label">
                        <dt class="text-xs text-gray-500 dark:text-gray-400">{{ cell.label }}</dt>
                        <dd class="mt-1 text-sm" :class="cell.strong ? 'text-lg font-semibold text-red-500 dark:text-red-400' : ''">
                          {{ cell.value }}
                        </dd>
                      </div>
                    </dl>
                    <!-- 这一行账是由余额变动带出来的（第一次充值、下单用余额付、退款退回余额），没动过就没有这一行，不是数据丢了 -->
                    <p v-else class="mb-4 text-sm text-gray-500 dark:text-gray-400">
                      {{ wallet.loading ? d.common.loading : d.account.wallet.emptyAccount }}
                    </p>

                    <ul class="divide-y divide-gray-100 dark:divide-gray-700">
                      <li v-for="row in walletLogs.rows" :key="row.id" class="flex items-center gap-3 py-3">
                        <span class="min-w-0 flex-1">
                          <span class="block truncate text-sm">{{ enumLabel(d.enum.walletBizType, row.bizType ?? 0, unknownCode(row.bizType)) }}</span>
                          <span class="block truncate text-xs text-gray-500 dark:text-gray-400">
                            {{ dayOf(row.createTime) }}
                            <template v-if="row.description"> · {{ row.description }}</template>
                            <template v-if="row.bizNo"> · {{ row.bizNo }}</template>
                          </span>
                        </span>
                        <span
                          class="shrink-0 text-sm"
                          :class="(row.amount ?? 0) < 0 ? 'text-gray-500 dark:text-gray-400' : 'text-red-500 dark:text-red-400'"
                        >
                          {{ signedMoney(row.amount) }}
                        </span>
                        <span class="w-24 shrink-0 text-right text-xs text-gray-500 dark:text-gray-400">
                          {{ d.account.wallet.balanceLabel }} {{ moneyOf(row.balance) }}
                        </span>
                      </li>
                      <li v-if="!walletLogs.rows.length && !walletLogs.loading" class="py-3 text-sm text-gray-500 dark:text-gray-400">
                        {{ d.account.wallet.emptyLogs }}
                      </li>
                    </ul>
                  </template>
                </template>

                <!-- 我的订单：一行一张单，多一颗「详情」看名下全部商品行 -->
                <ul v-else-if="active === 'orders'" class="divide-y divide-gray-100 dark:divide-gray-700">
                  <li v-for="row in orders.rows" :key="row.id" class="py-3">
                    <div class="flex items-baseline justify-between gap-3 text-sm">
                      <span class="truncate font-mono text-gray-500 dark:text-gray-400">{{ row.orderNo }}</span>
                      <span class="shrink-0 rounded bg-blue-50 px-2 py-0.5 text-xs text-blue-700 dark:bg-blue-900/40 dark:text-blue-300">
                        {{ enumLabel(d.enum.orderStatus, row.status, unknownCode(row.status)) }}
                      </span>
                    </div>
                    <div class="mt-1 flex items-baseline justify-between gap-3">
                      <span class="text-sm text-gray-500 dark:text-gray-400">
                        {{ dayOf(row.createTime) }} · {{ orderKindsPieces(row) }}
                        <template v-if="row.storeNames"> · {{ row.storeNames }}</template>
                      </span>
                      <span class="flex shrink-0 items-baseline gap-3">
                        <span class="text-sm font-medium text-red-500 dark:text-red-400">{{ moneyOf(row.totalAmount) }}</span>
                        <button
                          v-if="row.status === 0"
                          type="button"
                          class="rounded-lg bg-blue-600 px-3 py-1 text-sm font-medium text-white hover:bg-blue-700"
                          @click="payTarget = row"
                        >
                          {{ d.account.order.pay }}
                        </button>
                        <button
                          type="button"
                          class="text-sm text-blue-600 hover:underline dark:text-blue-400"
                          @click="detailId = row.id"
                        >
                          {{ d.common.detail }}
                        </button>
                      </span>
                    </div>
                  </li>
                  <li v-if="!orders.rows.length && !orders.loading" class="py-3 text-sm text-gray-500 dark:text-gray-400">
                    {{ d.account.order.empty }}
                  </li>
                </ul>

                <!-- 收货地址 -->
                <ul v-else-if="active === 'addresses'" class="divide-y divide-gray-100 dark:divide-gray-700">
                  <li v-for="row in addresses.rows" :key="row.id" class="py-3">
                    <div class="flex items-baseline gap-2 text-sm">
                      <span class="font-medium">{{ row.receiverName || '—' }}</span>
                      <span class="text-gray-500 dark:text-gray-400">{{ row.receiverPhone || '—' }}</span>
                      <span v-if="row.isDefault === 1" class="rounded bg-green-50 px-1.5 text-xs text-green-700 dark:bg-green-900/40 dark:text-green-300">
                        {{ d.account.address.defaultTag }}
                      </span>
                      <span v-if="row.tag" class="rounded bg-gray-100 px-1.5 text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-300">
                        {{ row.tag }}
                      </span>
                      <button
                        type="button"
                        class="ml-auto shrink-0 text-sm text-blue-600 hover:underline dark:text-blue-400"
                        @click="openAddressForm(row)"
                      >
                        {{ d.common.edit }}
                      </button>
                      <button
                        type="button"
                        class="shrink-0 text-sm text-gray-500 hover:text-red-600 hover:underline dark:text-gray-400 dark:hover:text-red-400"
                        @click="removeAddress(row)"
                      >
                        {{ d.common.delete }}
                      </button>
                    </div>
                    <p class="mt-1 text-sm text-gray-600 dark:text-gray-300">
                      {{ [row.areaZh, row.detail].filter(Boolean).join(' ') || d.account.address.noDetail }}
                    </p>
                  </li>
                  <li v-if="!addresses.rows.length && !addresses.loading" class="py-3 text-sm text-gray-500 dark:text-gray-400">
                    {{ d.account.address.empty }}
                  </li>
                </ul>

                <!-- 我的收藏：只有「删」这一颗（前台没有「收藏」那颗按钮，收藏行都由后台录），逐行删 -->
                <ul v-else-if="active === 'favorites'" class="divide-y divide-gray-100 dark:divide-gray-700">
                  <!-- 不能用 justify-between：那样空白会平均分到「名称↔时间」和「时间↔删除」两段，
                       名字长的行时间被挤得靠左、名字短的行靠右，上下几行看就是一条斜线。
                       改成名称 flex-1 把空白全吃掉，时间和「删除」两格 shrink-0 钉死在右边，时间这一列就齐了。
                       （items-center 而不是 items-baseline：名称那格带 truncate = overflow:hidden，这种 flex 项拿不到文字基线） -->
                  <li v-for="row in favorites.rows" :key="row.id" class="flex items-center gap-3 py-3">
                    <span class="min-w-0 flex-1 truncate text-sm">
                      <span class="mr-2 rounded bg-gray-100 px-1.5 text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-300">
                        {{ enumLabel(d.enum.favoriteTarget, row.targetType, d.enum.other) }}
                      </span>
                      <!-- 商品/门店各有详情页（门店那条 2026-10-02 才起），品牌留个名 -->
                      <a
                        v-if="favoriteUrl(row)"
                        :href="favoriteUrl(row)"
                        class="text-blue-600 hover:underline dark:text-blue-400"
                      >{{ row.targetName || d.account.favorite.removed }}</a>
                      <template v-else>{{ row.targetName || d.account.favorite.removed }}</template>
                    </span>
                    <span class="shrink-0 text-xs text-gray-500 dark:text-gray-400">{{ dayOf(row.favoriteTime) }}</span>
                    <button
                      type="button"
                      class="shrink-0 text-sm text-gray-500 hover:text-red-600 hover:underline dark:text-gray-400 dark:hover:text-red-400"
                      @click="removeFavorite(row)"
                    >
                      {{ d.common.delete }}
                    </button>
                  </li>
                  <li v-if="!favorites.rows.length && !favorites.loading" class="py-3 text-sm text-gray-500 dark:text-gray-400">
                    {{ d.account.favorite.empty }}
                  </li>
                </ul>

                <!-- 消息：一行一条，最左边那颗勾给批量操作用、未读的往右一格挂红点；点标题那段标已读并按 businessType 跳过去，右边留「删除」。
                     正文是服务端写好的那句中文（属于内容不属于界面，不翻），这里只做排版 -->
                <ul v-else-if="active === 'messages'" class="divide-y divide-gray-100 dark:divide-gray-700">
                  <li v-for="row in messages.rows" :key="row.id" class="flex items-start gap-3 py-3">
                    <input
                      type="checkbox"
                      class="mt-1 h-4 w-4 shrink-0 accent-blue-600"
                      :checked="selectedIds.includes(row.id)"
                      @change="toggleMessageSelect(row.id)"
                    />
                    <span
                      class="mt-1.5 h-2 w-2 shrink-0 rounded-full"
                      :class="row.isRead === 1 ? '' : 'bg-red-500'"
                      :aria-label="row.isRead === 1 ? d.account.message.read : d.account.message.unread"
                    ></span>
                    <span
                      class="min-w-0 flex-1 cursor-pointer"
                      :class="{ 'opacity-70': row.isRead === 1 }"
                      @click="readMessage(row)"
                    >
                      <span class="flex flex-wrap items-baseline gap-2">
                        <span class="rounded bg-gray-100 px-1.5 text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-300">
                          {{ row.category === 2 ? d.account.message.filterInteract : d.account.message.filterSystem }}
                        </span>
                        <span
                          class="text-sm"
                          :class="row.isRead === 1 ? 'text-gray-600 dark:text-gray-300' : 'font-medium'"
                          >{{ row.title || d.common.dash }}</span
                        >
                      </span>
                      <span class="mt-1 block break-words text-sm text-gray-600 dark:text-gray-300">{{ row.message }}</span>
                      <span class="mt-1 block text-xs text-gray-500 dark:text-gray-400">
                        {{ dayOf(row.createTime)
                        }}<template v-if="row.fromNickname"> · {{ row.fromNickname }}</template>
                      </span>
                    </span>
                    <button
                      type="button"
                      class="shrink-0 text-sm text-gray-500 hover:text-red-600 hover:underline dark:text-gray-400 dark:hover:text-red-400"
                      @click="removeMessage(row)"
                    >
                      {{ d.common.delete }}
                    </button>
                  </li>
                  <li v-if="!messages.rows.length && !messages.loading" class="py-3 text-sm text-gray-500 dark:text-gray-400">
                    {{ messageCategory ? d.account.message.emptyFiltered : d.account.message.empty }}
                  </li>
                </ul>

                <!-- 我的广场消息：这一档读的是 my/list，不拦审核状态 ⇒ 待审核的和被拒的都在这一列上（广场那一页找不到它们）。
                     「修改」开的就是广场列表右下角那颗同款弹窗（给了 post 就是编辑态），「删除」走这一页共用的确认框。 -->
                <template v-else-if="active === 'plaza'">
                  <!-- 三样筛选摆在列表上面（它们换的就是底下这一串）：关键字敲完回车或点「搜索」才捞，两档下拉一改就捞 -->
                  <div class="mb-3 flex flex-wrap items-center gap-2">
                    <input
                      v-model="plazaKeywordInput"
                      type="search"
                      :placeholder="d.plazaList.keywordPlaceholder"
                      :aria-label="d.plazaList.keywordPlaceholder"
                      class="w-48 rounded-lg border border-gray-300 px-3 py-1.5 text-sm outline-none focus:border-blue-500 dark:border-gray-600 dark:bg-gray-900"
                      @keyup.enter="applyPlazaKeyword"
                    />
                    <button
                      type="button"
                      :disabled="plaza.loading"
                      class="rounded-lg bg-blue-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-60"
                      @click="applyPlazaKeyword"
                    >
                      {{ d.common.search }}
                    </button>
                    <FilterSelect v-model="plazaRelation" :options="plazaRelationOptions" :label="d.account.plaza.relationFilter" :disabled="plaza.loading" />
                    <FilterSelect v-model="plazaAudit" :options="plazaAuditOptions" :label="d.account.plaza.auditFilter" :disabled="plaza.loading" />
                    <button
                      v-if="plazaFiltered"
                      type="button"
                      :disabled="plaza.loading"
                      class="text-sm text-blue-600 hover:underline disabled:opacity-60 dark:text-blue-400"
                      @click="clearPlazaFilters"
                    >
                      {{ d.account.plaza.clearFilters }}
                    </button>
                  </div>
                  <p v-if="plazaNotice" class="mb-3 text-right text-sm text-green-600 dark:text-green-400">
                    {{ plazaNotice }}
                  </p>
                  <ul class="divide-y divide-gray-100 dark:divide-gray-700">
                    <li v-for="row in plaza.rows" :key="row.id" class="py-3">
                      <div class="flex items-center gap-2 text-sm">
                        <span class="shrink-0 rounded px-1.5 py-0.5 text-xs" :class="plazaAuditClass(row.auditStatus)">
                          {{ enumLabel(d.enum.plazaAuditStatus, row.auditStatus ?? 0, unknownCode(row.auditStatus)) }}
                        </span>
                        <a
                          :href="href(`/plaza/${row.id}`)"
                          class="min-w-0 flex-1 truncate text-blue-600 hover:underline dark:text-blue-400"
                          >{{ plazaLabel(row, d.plazaList.noTitle) }}</a
                        >
                        <button
                          type="button"
                          class="shrink-0 text-sm text-blue-600 hover:underline dark:text-blue-400"
                          @click="openPlazaForm(row)"
                        >
                          {{ d.common.edit }}
                        </button>
                        <button
                          type="button"
                          class="shrink-0 text-sm text-gray-500 hover:text-red-600 hover:underline dark:text-gray-400 dark:hover:text-red-400"
                          @click="removePlaza(row)"
                        >
                          {{ d.common.delete }}
                        </button>
                      </div>
                      <p class="mt-1 text-sm text-gray-600 dark:text-gray-300">
                        {{ row.contentText?.replace(/\s+/g, ' ') || d.plazaDetail.noContent }}
                      </p>
                      <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                        {{ dayOf(row.createTime ?? null) }}
                        <template v-if="row.relationName"> · {{ fill(d.plazaDetail.relation, { name: row.relationName }) }}</template>
                        <template v-if="row.auditRemark"> · {{ fill(d.plazaDetail.auditRemark, { msg: row.auditRemark }) }}</template>
                      </p>
                    </li>
                    <li v-if="!plaza.rows.length && !plaza.loading" class="py-3 text-sm text-gray-500 dark:text-gray-400">
                      {{ plazaFiltered ? d.account.plaza.emptyFiltered : d.account.plaza.empty }}
                    </li>
                  </ul>
                </template>

                <!-- 浏览足迹 -->
                <ul v-else class="divide-y divide-gray-100 dark:divide-gray-700">
                  <li v-for="row in browses.rows" :key="row.id" class="flex items-center gap-3 py-3">
                    <span class="h-10 w-10 shrink-0 overflow-hidden rounded bg-gray-100 dark:bg-gray-700">
                      <img v-if="row.mainImage" :src="row.mainImage" :alt="row.productName || ''" class="h-full w-full object-cover" />
                    </span>
                    <span class="min-w-0 flex-1">
                      <a
                        v-if="row.productId"
                        :href="href(`/product/${row.productId}`)"
                        class="block truncate text-sm text-blue-600 hover:underline dark:text-blue-400"
                      >{{ row.productName || d.account.browse.gone }}</a>
                      <span class="block truncate text-xs text-gray-500 dark:text-gray-400">
                        {{ dayOf(row.browseTime) }}<template v-if="row.categoryName"> · {{ row.categoryName }}</template>
                      </span>
                    </span>
                    <span class="shrink-0 text-sm text-red-500 dark:text-red-400">{{ moneyOf(row.productPrice) }}</span>
                  </li>
                  <li v-if="!browses.rows.length && !browses.loading" class="py-3 text-sm text-gray-500 dark:text-gray-400">
                    {{ d.account.browse.empty }}
                  </li>
                </ul>

                <button
                  v-if="!sectionError && hasMore(panel.block)"
                  type="button"
                  class="mt-2 w-full rounded-lg border border-gray-300 py-2 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-60 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
                  :disabled="moreLoading"
                  @click="loadMore"
                >
                  {{ moreLoading ? d.common.loading : fill(d.account.page.moreUnits, { n: PAGE_SIZE, unit: panel.unit ?? '' }) }}
                </button>
              </div>
            </Transition>
          </section>
        </Transition>
      </div>

      <OrderDetail v-if="detailId" :id="detailId" @paid="reloadOrders" @close="detailId = 0" />
      <OrderPayDialog
        v-if="payTarget"
        :order-id="payTarget.id"
        :order-no="payTarget.orderNo"
        :pay-amount="payTarget.payAmount"
        @paid="onOrderPaid"
        @close="payTarget = null"
      />
      <SigninCalendar v-if="signinOpen" @close="signinOpen = false" @changed="onSigninChanged" />
      <PasswordForm v-if="passwordOpen" @saved="onPasswordSaved" @close="passwordOpen = false" />
      <AccountForm v-if="accountFormOpen" :account="account.row" @saved="onAccountSaved" @close="accountFormOpen = false" />
      <ImagePreview
        v-if="previewSrc"
        :src="previewSrc"
        :alt="previewAlt"
        :caption="previewCaption"
        @close="previewSrc = ''"
      />
      <AddressForm v-if="addressOpen" :address="editingAddress" @saved="onAddressSaved" @close="addressOpen = false" />
      <WalletChangeForm
        v-if="walletFormOpen"
        :action="walletAction"
        :account="wallet.row"
        @saved="onWalletChanged"
        @close="walletFormOpen = false"
      />
      <!-- 改成自己发的那一条：广场列表右下角那颗弹窗（给了 post 就是编辑态）；发布成功那一路在这页用不上，不接 -->
      <PlazaPublishDialog v-if="plazaFormOpen" :post="editingPlaza" @saved="onPlazaSaved" @close="plazaFormOpen = false" />
    </template>

    <!-- 确认框挂在「登录后那一整块」那个闸门外面：退出登录这一颗自己会把 signingOut 掰成真，
         挂在里面它就会在请求途中被自己闪掉，连报错都没地方看 -->
    <ConfirmDialog
      v-if="pending"
      :title="pending.title"
      :message="pending.message"
      :confirm-text="pending.confirmText"
      :busy="pending.busy"
      @confirm="runPending"
      @cancel="pending = null"
    />
  </div>
</template>

<style scoped>
/* 铺在封面背景上的文字统一走这一颗：白字 + 差值混合，等于「跟底下反着来」——
   压在浅色遮罩上显黑、压在深色遮罩或实景暗部上显白，所以切昼夜不用换一套色，也不用给每处补 dark:。
   只吃 color，尺寸/粗细照旧用工具类；写在 @layer 外面才压得住 Tailwind 那套 utilities */
.on-cover {
  color: #fff;
  mix-blend-mode: difference;
}

/* 挂件那一排的杆、绳、那一歪和悬停摆，全写在这一份里：
   rotate-2 / origin-top / animate-pulse 这几颗工具类
   在这个 island 里压根没被生成（页面样式表里查不到），挂在 class 上不生效，所以不跟扫描较劲。
   杆和绳那两色也钉死：这一排永远挂在那张浅米色封面上，跟着昼夜换色会在浅色上看不见 */
.pendant {
  transform-origin: top center;
  rotate: -2deg;
}

.pendant:nth-child(even) {
  rotate: 2deg;
}

/* 悬停只摆徽章本体（绳+圆牌+吊牌绕绳头顶摆两下再停住），杆整根不动。
   抬整枚会把绳从杆上摘下来，所以不用 translate */
.badge-body {
  transform-origin: top center;
}

.pendant:hover .badge-body {
  animation: pendant-swing 0.9s ease-out;
}

@keyframes pendant-swing {
  0% {
    rotate: 0deg;
  }

  25% {
    rotate: 5deg;
  }

  50% {
    rotate: -3.5deg;
  }

  75% {
    rotate: 2deg;
  }

  100% {
    rotate: 0deg;
  }
}

.rod {
  display: block;
  height: 3px;
  border-radius: 9999px;
  background-color: #a8a29e;
}

.rope {
  display: block;
  width: 1.5px;
  height: 16px;
  background-color: #78716c;
}

.pendant:hover .rope {
  background-color: #44403c;
}

.pendant.is-loading {
  animation: pendant-pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

@keyframes pendant-pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.4;
  }
}
</style>