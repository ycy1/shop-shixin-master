<script lang="ts" setup>
import { getMyAccount, updateMyAccount, uploadAvatar } from '@/api/profile'
import { areaColumns, areaZhOf, onAreaColumnChange, parseAreaCode } from '@/composables/useAreaPicker'
import MapPicker from '@/components/MapPicker.vue'
import { useUserStore } from '@/store'
import { SEX_TEXT } from '@/utils/profile'

defineOptions({
  name: 'ProfileEdit',
})

definePage({
  style: {
    navigationBarTitleText: '修改资料',
  },
})

/**
 * 基础信息那张表：字段和判据照 Web 端那份修改弹窗（shop-web 的 AccountForm.vue）。
 *
 * 所在地区收两列（area_code 逗号分隔的行政区划代码 + area_zh 拼好的中文），数据源和后台、Web 是同一份
 * @vant/area-data（见 composables/useAreaPicker），所以三处选出来的码一模一样；经纬度照 Web 那颗 MapPicker
 * 走地图选点（H5 高德、小程序微信原生选点，见 components/MapPicker），下面那两个数字输入留着当退路。
 *
 * 全局 field-strategy=not_empty：空着送上去等于「不改」而不是「清空」，所以这一页只改得了、清不掉。
 * 账号、账号类型、状态、角色服务端一概不认，也就不进这张表。
 */

/** 性别那一排的顺序照 Web 那份（男 / 女 / 保密） */
const SEX_OPTIONS = [1, 2, 0]

const nickname = ref('')
const mobile = ref('')
const email = ref('')
const sex = ref(0)
const avatar = ref('')
const signature = ref('')
/** 已选中的行政区划代码（省[,市[,区]]），级联选择器的那一格 */
const areaCodes = ref<string[]>([])
const address = ref('')
/** 这两格挂的是数字键盘，回填的值会被转回 number，所以一律先 String() 再用 */
const longitude = ref<string | number>('')
const latitude = ref<string | number>('')

const loading = ref(true)
const uploading = ref(false)
const submitting = ref(false)
const error = ref('')

/** 打开选择器时停在已选那三级上（列是按已选代码铺的，用户滚的时候由 columnChange 换） */
const columns = computed(() => areaColumns(areaCodes.value))

/** 坐标填了就得是个范围内的数（下单按这两格就近分仓，填歪了会把单派到另一头的仓） */
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

async function load() {
  try {
    const one = (await getMyAccount()).sysUser
    nickname.value = one?.nickname ?? ''
    mobile.value = one?.mobile ?? ''
    email.value = one?.email ?? ''
    sex.value = one?.sex ?? 0
    avatar.value = one?.avatar ?? ''
    signature.value = one?.signature ?? ''
    areaCodes.value = parseAreaCode(one?.areaCode)
    address.value = one?.address ?? ''
    longitude.value = one?.longitude ?? ''
    latitude.value = one?.latitude ?? ''
  }
  catch (err) {
    error.value = (err as Error).message
  }
  finally {
    loading.value = false
  }
}

/** 手机号和后端同一套（1[3-9] 开头十一位，不查重）；邮箱后端不看形状，只在页面这一侧拦 */
const badMobile = computed(() => !!mobile.value && !/^1[3-9]\d{9}$/.test(mobile.value.trim()))
const badEmail = computed(() => !!email.value && !/^[^@\s]+@[^\s@][^\s.@]*\.[^\s@]+$/.test(email.value.trim()))
const canSubmit = computed(() =>
  !submitting.value
  && !uploading.value
  && !!nickname.value.trim()
  && !badMobile.value
  && !badEmail.value
  && !badLongitude.value
  && !badLatitude.value,
)

/**
 * 头像分两步（同 Web 那份、也同后台那个上传组件的顺序）：先 POST /api/file/uploadImage 拿回 URL，
 * URL 跟着这张表一起送 —— 传完不点「保存」什么都不会变。
 */
function chooseAvatar() {
  if (uploading.value) {
    return
  }
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: async (res) => {
      const path = res.tempFilePaths[0]
      if (!path) {
        return
      }
      uploading.value = true
      error.value = ''
      try {
        avatar.value = await uploadAvatar(path)
      }
      catch (err) {
        error.value = (err as Error).message
      }
      finally {
        uploading.value = false
      }
    },
  })
}

function pointOf(value: string | number) {
  const text = String(value ?? '').trim()
  return text ? Number(text) : null
}

/**
 * 地图选点回来：坐标一定写，详细地址只在真拿到地址时才覆盖 ——
 * 只是打开看了眼位置不能把用户手写的门牌换成地图给的街道名（口径同 Web 那份 AccountForm）。
 */
function handlePick(pickedPoint: { longitude: number, latitude: number, address: string }) {
  longitude.value = pickedPoint.longitude.toFixed(6)
  latitude.value = pickedPoint.latitude.toFixed(6)
  if (pickedPoint.address) {
    address.value = pickedPoint.address
  }
}

async function handleSave() {
  if (!canSubmit.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await updateMyAccount({
      nickname: nickname.value.trim(),
      mobile: mobile.value.trim() || null,
      email: email.value.trim() || null,
      sex: sex.value,
      avatar: avatar.value.trim() || null,
      signature: signature.value.trim() || null,
      // 两列要成对：代码空着就当「不改这一格」，别拼出一条空 area_zh 把已经填好的地区盖掉
      areaCode: areaCodes.value.length ? areaCodes.value.join(',') : null,
      areaZh: areaCodes.value.length ? areaZhOf(areaCodes.value) : null,
      address: address.value.trim() || null,
      longitude: pointOf(longitude.value),
      latitude: pointOf(latitude.value),
    })
    // 「我的」头部那两颗（头像、昵称）读的是 user store 里登录那一刻的快照，重捞一次才跟得上
    await useUserStore().fetchUserInfo().catch(() => undefined)
    uni.showToast({ title: '已保存', icon: 'success' })
    // 让那句 toast 走完再退，个人信息那一页回来会自己重拉一遍
    setTimeout(() => uni.navigateBack(), 800)
  }
  catch (err) {
    error.value = (err as Error).message
  }
  finally {
    submitting.value = false
  }
}

function previewAvatar() {
  if (avatar.value) {
    uni.previewImage({ urls: [avatar.value] })
  }
}

onLoad(load)
</script>

<template>
  <view class="min-h-screen bg-[#f5f6f7] pb-192rpx pt-24rpx">
    <view v-if="loading" class="py-128rpx text-center text-[26rpx] text-gray-400">
      加载中…
    </view>

    <template v-else>
      <!-- 头像：圆点这一下选图，点开图本身看原图（这两个动作分开摆，免得选完还想看） -->
      <view class="mx-24rpx mb-24rpx flex items-center rounded-xl bg-white px-32rpx py-32rpx">
        <view hover-class="fx-tap" class="fx-press relative shrink-0" @click="chooseAvatar">
          <image
            v-if="avatar"
            :src="avatar"
            mode="aspectFill"
            class="h-[110rpx] w-[110rpx] rounded-full bg-gray-100"
          />
          <view v-else class="h-[110rpx] w-[110rpx] rounded-full bg-gray-100" />
        </view>
        <view class="ml-24rpx min-w-0 flex-1">
          <view class="text-[28rpx] text-[#333]">
            头像
          </view>
          <view class="mt-8rpx text-[24rpx] text-gray-400">
            {{ uploading ? '上传中…' : '选一张图，保存这张表时才落到账号上' }}
          </view>
        </view>
        <text hover-class="fx-tap" class="fx-press shrink-0 text-[26rpx] text-[#d14328]" @click="chooseAvatar">
          {{ uploading ? '上传中' : avatar ? '换一张' : '选一张' }}
        </text>
        <text v-if="avatar" hover-class="fx-tap" class="fx-press ml-24rpx shrink-0 text-[26rpx] text-gray-400" @click="previewAvatar">
          查看
        </text>
      </view>

      <view v-if="error" class="mx-24rpx mb-24rpx rounded-lg bg-red-50 px-32rpx py-16rpx text-[26rpx] text-[#d14328]">
        {{ error }}
      </view>

      <view class="mx-24rpx mb-24rpx rounded-xl bg-white">
        <wd-input v-model="nickname" label="昵称" placeholder="请输入昵称" :maxlength="30" clearable />
        <wd-input v-model="mobile" label="手机号" placeholder="选填" type="number" :maxlength="11" />
        <view v-if="badMobile" class="px-32rpx pb-16rpx text-[22rpx] text-[#d14328]">
          手机号格式不对（1 开头十一位）
        </view>
        <wd-input v-model="email" label="邮箱" placeholder="选填" :maxlength="50" />
        <view v-if="badEmail" class="px-32rpx pb-16rpx text-[22rpx] text-[#d14328]">
          邮箱格式不对
        </view>
        <wd-textarea v-model="signature" label="个性签名" placeholder="选填" :maxlength="255" />
      </view>

      <!-- 所在地区收两列（代码 + 中文），口径同收货地址；坐标走地图选点，同 Web 那颗 MapPicker（手填那两格留着） -->
      <view class="mx-24rpx mb-24rpx rounded-xl bg-white">
        <MapPicker
          :longitude="pointOf(longitude)"
          :latitude="pointOf(latitude)"
          @confirm="handlePick"
        />
        <view class="mx-32rpx h-1px bg-gray-100" />
        <wd-col-picker
          v-model="areaCodes"
          :columns="columns"
          :column-change="onAreaColumnChange"
          label="所在地区"
          placeholder="选填，选到省和市即可"
          title="选择所在地区"
        />
        <wd-input v-model="address" label="详细地址" placeholder="街道、门牌号" :maxlength="255" />
        <wd-input v-model="longitude" label="经度" type="digit" placeholder="选填，如 120.155" />
        <view v-if="badLongitude" class="px-32rpx pb-16rpx text-[22rpx] text-[#d14328]">
          经度要填 -180 到 180 之间的数
        </view>
        <wd-input v-model="latitude" label="纬度" type="digit" placeholder="选填，如 30.274" />
        <view v-if="badLatitude" class="px-32rpx pb-16rpx text-[22rpx] text-[#d14328]">
          纬度要填 -90 到 90 之间的数
        </view>
      </view>

      <view class="mx-24rpx mb-24rpx rounded-xl bg-white px-32rpx py-24rpx">
        <view class="text-[26rpx] text-gray-400">
          性别
        </view>
        <view class="mt-16rpx flex gap-16rpx">
          <view
            v-for="value in SEX_OPTIONS"
            :key="value"
            hover-class="fx-tap"
            class="fx-press rounded-full px-32rpx py-12rpx text-[26rpx]"
            :class="sex === value ? 'bg-[#fdece8] text-[#d14328] font-medium' : 'bg-gray-100 text-gray-600'"
            @click="sex = value"
          >
            {{ SEX_TEXT[value] }}
          </view>
        </view>
      </view>

      <view class="mx-24rpx mb-24rpx text-[22rpx] text-gray-400 leading-[34rpx]">
        这几格改得了、清不掉：空着交上去等于「不改」，不是「清空」（服务端那份 field-strategy 就是这么定的）。
        坐标那一格是下单没选收货地址时拿来就近分仓的，平时收货地址上那两位更准 —— 地址在「地址管理」那一页管。
      </view>

      <view class="fx-bar fixed bottom-0 left-0 right-0 z-10 border-t border-gray-100">
        <view class="px-32rpx py-24rpx">
          <wd-button type="error" size="large" block :loading="submitting" :disabled="!canSubmit" @click="handleSave">
            保存
          </wd-button>
        </view>
      </view>
    </template>

    <!-- 栏子（.fx-bar）比原来高出安全区那一截，页面尾巴也跟着垫同样一截 -->
    <view class="pb-safe" />
  </view>
</template>
