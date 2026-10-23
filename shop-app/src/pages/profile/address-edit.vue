<script lang="ts" setup>
import type { IShopAddressForm } from '@/api/types/shop'
import { addMyAddress, getMyAccount, getMyAddress, updateMyAddress } from '@/api/profile'
import { ADDRESS_TAGS, areaColumns, areaZhOf, onAreaColumnChange, parseAreaCode } from '@/composables/useAreaPicker'
import { parseAddressText } from '@/utils/addressParse'
import { geocodeByAddress } from '@/utils/amapGeocode'
import { debounce } from '@/utils/debounce'
import MapPicker from '@/components/MapPicker.vue'

defineOptions({
  name: 'AddressEdit',
})

definePage({
  style: {
    navigationBarTitleText: '收货地址',
  },
})

/**
 * 新增 / 修改一条收货地址（两张页面共用这一份，照 Web 那份 AddressForm.vue 的字段和判据）。
 *
 * 必填就三样：收货人姓名 / 电话 / 详细地址（和表上那三列 NOT NULL 对上），其余都可空。
 * 地区收两列（逗号分隔的行政区划代码 + 空格拼的中文），数据源和后台、Web 是同一份，所以码一模一样。
 * 新增进来先按当前登录账号那几格打个底（见 prefillFromAccount），修改那一趟不碰这个底子。
 * 最上面那一格是粘贴识别：一整条地址粘进来把四格一次填上，判据全在前端（见 utils/addressParse），
 * 用的是级联选择器那同一棵区划树，所以认出来的代码和手动选出来的一模一样。
 *
 * 修改是**整条覆盖**（实体上那几列钉的 IGNORED）：地区 / 标签 / 坐标 不传就被清成空，
 * 所以回填要把列表带回来的每格都摆上 —— 页面上没有的邮编也照样接过来原样送回，免得替用户抹掉。
 * 坐标照 Web 那份走地图选点（H5 是高德、小程序是微信原生选点，见 components/MapPicker），手填那两格留着当退路。
 * 坐标空着时还会拿「所在地区 + 详细地址」去高德自动换一个点（见 utils/amapGeocode，只有 H5 走得起，
 * 小程序和 App 里那份 JSAPI 加载不起来就照旧空着），但不盖掉他自己选过的那一下。
 */

const addressId = ref<number | null>(null)
const receiverName = ref('')
const receiverPhone = ref('')
const areaCodes = ref<string[]>([])
const detail = ref('')
const tag = ref('')
const postalCode = ref('')
const longitude = ref<string | number>('')
const latitude = ref<string | number>('')
const isDefault = ref(false)

const loading = ref(false)
const submitting = ref(false)
const error = ref('')

/** 智能识别那一格：粘进来的原文 + 识别完给他看的一句话 */
const pasteText = ref('')
const pasteHint = ref('')
const pasteWarn = ref(false)

/** 按详细地址找坐标那一趟：给他看的一句话 + 挡住过期回来的那笔 */
const pointHint = ref('')
let pointSeq = 0

const editing = computed(() => addressId.value != null)
const columns = computed(() => areaColumns(areaCodes.value))

/** 坐标填了就得是个范围内的数：下单按这两格就近分仓、算配送费，填歪了会把单派到另一头的仓 */
function badPoint(value: string | number, max: number) {
  const text = String(value ?? '').trim()
  if (!text) {
    return false
  }
  const num = Number(text)
  return !Number.isFinite(num) || Math.abs(num) > max
}
const badLongitude = computed(() => badPoint(longitude.value, 180))
const badLatitude = computed(() => badPoint(latitude.value, 90))
/** 电话和 sys_user.mobile 用同一套（1[3-9] 开头十一位） */
const badPhone = computed(() => !!receiverPhone.value.trim() && !/^1[3-9]\d{9}$/.test(receiverPhone.value.trim()))

const canSubmit = computed(() =>
  !submitting.value
  && !!receiverName.value.trim()
  && !!receiverPhone.value.trim()
  && !badPhone.value
  && !!detail.value.trim()
  && !badLongitude.value
  && !badLatitude.value,
)

/** 标签那三颗是单选：点同一颗取消，这一格本来就可空 */
function pickTag(one: string) {
  tag.value = tag.value === one ? '' : one
}

/**
 * 识别粘进来的一整条地址：认出来的那一格一律盖过去 —— 这一趟是他主动点的，
 * 比账号打的那个底（甚至比他刚敲的两个字）更该作数；认不出的格子不动，留着手填。
 * 底下那一行把「填了哪几格、哪几格还得自己补」讲明白，不让他对着一张看起来填满了的表单猜。
 */
function handleRecognize() {
  const parsed = parseAddressText(pasteText.value)
  const filled: string[] = []
  const missed: string[] = []
  if (parsed.receiverName) {
    receiverName.value = parsed.receiverName
    filled.push('收货人')
  }
  else {
    missed.push('收货人')
  }
  if (parsed.receiverPhone) {
    receiverPhone.value = parsed.receiverPhone
    filled.push('电话')
  }
  else {
    missed.push('电话')
  }
  if (parsed.areaCodes.length) {
    areaCodes.value = parsed.areaCodes
    filled.push('所在地区')
  }
  else {
    missed.push('所在地区')
  }
  if (parsed.detail) {
    detail.value = parsed.detail
    filled.push('详细地址')
  }
  else {
    missed.push('详细地址')
  }
  pasteWarn.value = missed.length > 0
  pasteHint.value = filled.length
    ? `已填好${filled.join('、')}${missed.length ? `；${missed.join('、')}没认出来，要补一下` : ''}`
    : '这几格都没认出来，照着一格一格填吧'
}

/** 读剪贴板：小程序和 App 里这一颗最省事，H5 里浏览器多半拦读，拦下就让他长按自己粘 */
function handlePasteFromClipboard() {
  uni.getClipboardData({
    success: (res) => {
      if (!res.data) {
        pasteHint.value = '剪贴板是空的'
        pasteWarn.value = true
        return
      }
      pasteText.value = res.data
      handleRecognize()
    },
    fail: () => {
      pasteHint.value = '这一端读不到剪贴板，长按上面那一格粘贴进来'
      pasteWarn.value = true
    },
  })
}

/**
 * 新增那一趟：先把自己账号上的信息摆进表单（口径同后台「选消费者自动回填收货人姓名/电话」，
 * sys_user 本来还收着所在地区、详细地址，这两格一并接过来）。
 * 经纬度故意不接：账号那两位是这个人常驻的位置，一条收货地址点的是另一个地方，
 * 拿它当默认会把单派到另一头的仓 —— 坐标要么他自己地图选点，要么留空。
 * 每一格都只在还空着时才填 —— 异步回来时用户可能已经敲了字，不能把他写的盖掉。
 */
async function prefillFromAccount() {
  try {
    const user = (await getMyAccount())?.sysUser
    if (!user) {
      return
    }
    if (!receiverName.value) {
      receiverName.value = user.nickname || user.username || ''
    }
    if (!receiverPhone.value) {
      receiverPhone.value = user.mobile || ''
    }
    if (!areaCodes.value.length) {
      areaCodes.value = parseAreaCode(user.areaCode)
    }
    if (!detail.value) {
      detail.value = user.address || ''
    }
  }
  catch {
    // 账号那一格读不到就算了，这一趟照样能手填
  }
}

async function loadOne(target: number) {
  loading.value = true
  error.value = ''
  try {
    const row = await getMyAddress(target)
    if (!row) {
      error.value = '这条地址不在了，回去重新挑一条'
      return
    }
    receiverName.value = row.receiverName ?? ''
    receiverPhone.value = row.receiverPhone ?? ''
    areaCodes.value = parseAreaCode(row.areaCode)
    detail.value = row.detail ?? ''
    tag.value = row.tag ?? ''
    postalCode.value = row.postalCode ?? ''
    longitude.value = row.longitude ?? ''
    latitude.value = row.latitude ?? ''
    isDefault.value = row.isDefault === 1
  }
  catch (err) {
    error.value = (err as Error).message
  }
  finally {
    loading.value = false
  }
}

function pointOf(value: string | number) {
  const text = String(value ?? '').trim()
  return text ? Number(text) : null
}

/**
 * 地图选点回来：坐标一定写（用户就是点了那一下），详细地址只在真拿到地址时才覆盖 ——
 * 只是打开看了眼位置不能把用户手写的门牌换成地图给的街道名（口径同 Web 那份 AddressForm）。
 */
function handlePick(point: { longitude: number, latitude: number, address: string }) {
  longitude.value = point.longitude.toFixed(6)
  latitude.value = point.latitude.toFixed(6)
  if (point.address) {
    detail.value = point.address
  }
  // 他自己点的那一下就是准的，那句「按地址找位置」的话不用再挂着
  pointHint.value = ''
}

/**
 * 坐标还空着就拿「所在地区 + 详细地址」去高德换一个点（口径同上，回来的也是 GCJ-02）。
 * 写之前再判一次空 —— 期间他可能已经在地图上点了，不能把人家的点盖掉；
 * seq 挡的是「找坐标的请求还在路上，地址又被改了」那一笔过期结果。
 * 详细地址停手 700ms 才发（一个字一个字敲的时候别每次都打一次高德）。
 */
async function fillPointFromDetail() {
  const zh = areaZhOf(areaCodes.value)
  const query = `${zh.replace(/\s+/g, '')}${detail.value.trim()}`.trim()
  if (!query || String(longitude.value).trim() || String(latitude.value).trim()) {
    return
  }
  const seq = ++pointSeq
  pointHint.value = '坐标空着，正按详细地址找位置…'
  const point = await geocodeByAddress(query, zh.split(' ')[1])
  if (seq !== pointSeq) {
    return
  }
  if (!point) {
    pointHint.value = '这句详细地址在高德上没找到对应位置，要坐标就地图选点或自己填那两格'
    return
  }
  if (String(longitude.value).trim() || String(latitude.value).trim()) {
    // 请求在路上时他已经选过点了，那一下作数
    pointHint.value = ''
    return
  }
  longitude.value = point.longitude.toFixed(6)
  latitude.value = point.latitude.toFixed(6)
  pointHint.value = `已按详细地址找到位置（${longitude.value}, ${latitude.value}），不准就重新选点`
}

const fillPoint = debounce(fillPointFromDetail, 700)

watch([detail, () => areaCodes.value.join(',')], () => {
  fillPoint()
})

async function handleSave() {
  if (!canSubmit.value) {
    return
  }
  submitting.value = true
  error.value = ''
  const target = addressId.value
  const form: IShopAddressForm = {
    receiverName: receiverName.value.trim(),
    receiverPhone: receiverPhone.value.trim(),
    areaCode: areaCodes.value.join(','),
    areaZh: areaZhOf(areaCodes.value),
    detail: detail.value.trim(),
    postalCode: postalCode.value.trim() || null,
    longitude: pointOf(longitude.value),
    latitude: pointOf(latitude.value),
    tag: tag.value.trim(),
    isDefault: isDefault.value ? 1 : 0,
  }
  try {
    // 新增时那一格由服务层判「这个账号的第一条地址直接当默认」，所以照实送 0 也对得上
    await (target ? updateMyAddress({ ...form, id: target }) : addMyAddress(form))
    uni.showToast({ title: '已保存', icon: 'success' })
    // 列表页 onShow 会整块重拉：设默认会改到别的行，就地改会画出两颗默认
    setTimeout(() => uni.navigateBack(), 800)
  }
  catch (err) {
    error.value = (err as Error).message
  }
  finally {
    submitting.value = false
  }
}

onLoad((query) => {
  const target = Number(query?.id)
  if (!target) {
    // 新增：先拿自己账号上的信息打个底，用户在这个底子上改
    void prefillFromAccount()
    return
  }
  addressId.value = target
  uni.setNavigationBarTitle({ title: '修改地址' })
  void loadOne(target)
})
</script>

<template>
  <view class="min-h-screen bg-[#f5f6f7] pb-192rpx pt-24rpx">
    <view v-if="loading" class="py-128rpx text-center text-[26rpx] text-gray-400">
      加载中…
    </view>

    <template v-else>
      <view v-if="error" class="mx-24rpx mb-24rpx rounded-lg bg-red-50 px-32rpx py-16rpx text-[26rpx] text-[#d14328]">
        {{ error }}
      </view>

      <view class="mx-24rpx mb-24rpx rounded-xl bg-white px-32rpx py-24rpx">
        <view class="text-[26rpx] text-gray-400">
          粘贴一整条地址，自动识别下面四格
        </view>
        <view class="mt-16rpx rounded-lg bg-gray-100">
          <wd-textarea
            v-model="pasteText"
            :no-border="true"
            :auto-height="true"
            :maxlength="255"
            placeholder="如：张三 13800001111 广东省深圳市福田区福华三路88号"
          />
        </view>
        <view class="mt-16rpx flex gap-16rpx">
          <wd-button size="small" type="error" :disabled="!pasteText.trim()" @click="handleRecognize">
            识别
          </wd-button>
          <wd-button size="small" plain @click="handlePasteFromClipboard">
            从剪贴板粘贴
          </wd-button>
        </view>
        <view v-if="pasteHint" class="mt-12rpx text-[22rpx]" :class="pasteWarn ? 'text-[#e6a23c]' : 'text-gray-400'">
          {{ pasteHint }}
        </view>
      </view>

      <view class="mx-24rpx mb-24rpx rounded-xl bg-white">
        <wd-input v-model="receiverName" label="收货人" placeholder="请输入姓名" :maxlength="30" clearable />
        <wd-input v-model="receiverPhone" label="手机号" placeholder="收货人电话" type="number" :maxlength="11" />
        <view v-if="badPhone" class="px-32rpx pb-16rpx text-[22rpx] text-[#d14328]">
          电话格式不对（1 开头十一位）
        </view>
        <wd-col-picker
          v-model="areaCodes"
          :columns="columns"
          :column-change="onAreaColumnChange"
          label="所在地区"
          placeholder="选填，选到省和市即可"
          title="选择所在地区"
        />
        <wd-input v-model="detail" label="详细地址" placeholder="街道、门牌号" :maxlength="255" />
      </view>

      <view class="mx-24rpx mb-24rpx rounded-xl bg-white px-32rpx py-24rpx">
        <view class="text-[26rpx] text-gray-400">
          标签
        </view>
        <view class="mt-16rpx flex gap-16rpx">
          <view
            v-for="one in ADDRESS_TAGS"
            :key="one"
            hover-class="fx-tap"
            class="fx-press rounded-full px-32rpx py-12rpx text-[26rpx]"
            :class="tag === one ? 'bg-[#fdece8] text-[#d14328] font-medium' : 'bg-gray-100 text-gray-600'"
            @click="pickTag(one)"
          >
            {{ one }}
          </view>
        </view>
      </view>

      <view class="mx-24rpx mb-24rpx rounded-xl bg-white">
        <MapPicker
          :longitude="pointOf(longitude)"
          :latitude="pointOf(latitude)"
          @confirm="handlePick"
        />
        <view class="mx-32rpx h-1px bg-gray-100" />
        <wd-input v-model="longitude" label="经度" type="digit" placeholder="选填，如 120.155" />
        <view v-if="badLongitude" class="px-32rpx pb-16rpx text-[22rpx] text-[#d14328]">
          经度要填 -180 到 180 之间的数
        </view>
        <wd-input v-model="latitude" label="纬度" type="digit" placeholder="选填，如 30.274" />
        <view v-if="badLatitude" class="px-32rpx pb-16rpx text-[22rpx] text-[#d14328]">
          纬度要填 -90 到 90 之间的数
        </view>
        <view v-if="pointHint" class="px-32rpx pb-24rpx text-[22rpx] text-gray-400">
          {{ pointHint }}
        </view>
      </view>

      <view class="mx-24rpx mb-24rpx flex items-center rounded-xl bg-white px-32rpx py-24rpx">
        <view class="min-w-0 flex-1">
          <view class="text-[28rpx] text-[#333]">
            设为默认
          </view>
          <view class="mt-8rpx text-[24rpx] text-gray-400">
            结算页会先选中这一条；这个账号的第一条地址本来就会当默认
          </view>
        </view>
        <wd-switch v-model="isDefault" />
      </view>

      <view class="mx-24rpx mb-24rpx text-[22rpx] text-gray-400 leading-[34rpx]">
        {{ editing
          ? '修改是整条交上去的：地区、标签、坐标这几格空着填就等于清掉（不是「不改」），所以别的地方改过这一条，进来都会先摆成现在库里的样子。'
          : '收货人、电话、详细地址三格必填；坐标空着会按详细地址自动找一个点，也可以地图选点或自己填，下单就按它就近分仓、算配送费。' }}
      </view>
    </template>

    <view class="fx-bar fixed bottom-0 left-0 right-0 z-10 border-t border-gray-100">
      <view class="px-32rpx py-24rpx">
        <wd-button type="error" size="large" block :loading="submitting" :disabled="!canSubmit" @click="handleSave">
          保存
        </wd-button>
      </view>
    </view>

    <!-- 栏子（.fx-bar）比原来高出安全区那一截，页面尾巴也跟着垫同样一截 -->
    <view class="pb-safe" />
  </view>
</template>
