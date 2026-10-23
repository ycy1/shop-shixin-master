<script lang="ts" setup>
import type { IShopWalletAccount } from '@/api/types/shop'
import { moneyOf } from '@/utils/profile'
import { rechargeWallet, withdrawWallet } from '@/api/profile'

/**
 * 充值 / 提现共用的一颗弹窗（形状照 shop-web 的 views/user/WalletChangeForm.vue，只是搬到手机上从下面推出来）。
 *
 * 表单只有两格：这一笔多少钱、为什么（原样写进流水的说明）。账户上那六个数一概不进来 ——
 * 这一本是账，余额只能由充值进、提现出、下单占、退款还这些动作自己加加减减，
 * 页面能直接填余额就等于允许「账上多了 500 块、流水里却什么都没有」。
 * 也没有「给谁充」：comm 那两条把 userId 钉成登录 id，动的永远是自己这本账。
 *
 * 提现那一头多一道当场判据（比可用余额多就不给点）：服务端那条 SQL 压着 balance >= 这一笔，
 * 撞上去整趟回滚，能在页面上先说清就别等人提交完才报错。冻结中那一部分（下单没付的单子占着的）
 * 本来就不在可用余额里，所以这里比的就是 balance 那一格；还没开账时带 null，按 0 元说。
 */

const props = defineProps<{
  modelValue: boolean
  action: 'recharge' | 'withdraw'
  account?: IShopWalletAccount | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', visible: boolean): void
  (e: 'saved'): void
}>()

const show = computed({
  get: () => props.modelValue,
  set: visible => emit('update:modelValue', visible),
})

const isRecharge = computed(() => props.action === 'recharge')
const title = computed(() => (isRecharge.value ? '余额充值' : '余额提现'))
const usable = computed(() => Number(props.account?.balance ?? 0))

/**
 * 金额这格 v-model 绑在数字键盘上，填进去的值可能以 number 回来，所以两种都收、
 * 用的时候一律 String(...) 再 trim（直接 .trim() 撞上 number 会当场抛，提交那颗就永久置灰）。
 */
const amount = ref<string | number>('')
const description = ref('')
const submitting = ref(false)
const error = ref('')

/** 每一次推开都当新的一趟：上一趟填的数、那句错误都不带进下一趟（两颗来回切也一样） */
watch([show, () => props.action], () => {
  if (!show.value) {
    return
  }
  amount.value = ''
  description.value = ''
  error.value = ''
})

/** 填进去的数（空着、或者压根不是个数都是 null） */
const picked = computed(() => {
  const text = String(amount.value).trim()
  if (!text) {
    return null
  }
  const value = Number(text)
  return Number.isFinite(value) ? value : null
})

/** 最多两位小数：库里那一列是 decimal(14,2)，服务端这一条是拒的（不是四舍五入），所以这里先拦住 */
const tooPrecise = computed(() => {
  const value = picked.value
  return value != null && Math.round(value * 100) / 100 !== value
})

/** 提现那一头的超提：填的比可用余额多，服务端那条 SQL 一定改不动 */
const tooMuch = computed(() => !isRecharge.value && picked.value != null && picked.value > usable.value)

const canSubmit = computed(() =>
  !submitting.value && picked.value != null && picked.value > 0 && !tooPrecise.value && !tooMuch.value,
)

/** 底下那句小字说的正是这一笔会怎么动账 */
const hint = computed(() => {
  if (tooPrecise.value) {
    return '金额最多两位小数，请改成两位以内的数。'
  }
  if (tooMuch.value) {
    return `能提的只有 ${moneyOf(usable.value)}，这一笔要提 ${moneyOf(picked.value)}（下单占着的那一部分在冻结中，提不走）。`
  }
  return isRecharge.value
    ? '充进来的钱能用来下单支付，退了货也会退回这里'
    : `当前可用余额 ${moneyOf(usable.value)}`
})

const tip = computed(() => (isRecharge.value
  ? '这一笔加进可用余额，同时记一行「充值」流水；第一次充值时那一行钱包账户也是这一刻建出来的。本期充值不走真收款的渠道（微信那条充值单的链还没接），所以动的只是账上的数。'
  : '提现是从可用余额里减掉这一笔，同时记一行「提现」流水。下单没付的单子占着的那一部分在冻结中，那一部分提不走；账户被冻住时也提不走。本期这一步同样不出真钱，钱是真给出去那一步走线下。'))

async function submit() {
  if (!canSubmit.value) {
    return
  }
  submitting.value = true
  error.value = ''
  const form = { amount: picked.value as number, description: description.value.trim() || undefined }
  try {
    // 回来的就是改完之后那一行账，但由页面自己去重拉：它同时要把流水清回首页，两处口径统一
    await (isRecharge.value ? rechargeWallet(form) : withdrawWallet(form))
    emit('saved')
  }
  catch (err) {
    // 拒掉那句（请先登录、钱不够、账户被冻）由 http 层弹 toast，这一格再留一份在弹窗里，免得toast 走完了看不见
    error.value = (err as Error).message
  }
  finally {
    submitting.value = false
  }
}
</script>

<template>
  <wd-popup v-model="show" position="bottom" safe-area-inset-bottom closable>
    <view class="px-32rpx pb-32rpx pt-8rpx">
      <view class="pb-24rpx pt-16rpx text-[32rpx] text-[#333] font-medium">
        {{ title }}
      </view>

      <view v-if="error" class="mb-24rpx rounded-lg bg-red-50 px-24rpx py-16rpx text-[26rpx] text-[#d14328]">
        {{ error }}
      </view>

      <wd-input v-model="amount" label="金额（元）" type="digit" placeholder="请输入金额，最多两位小数" no-border />
      <view class="py-16rpx text-[24rpx]" :class="tooPrecise || tooMuch ? 'text-[#d14328]' : 'text-gray-400'">
        {{ hint }}
      </view>

      <wd-input v-model="description" label="说明" :maxlength="255" :placeholder="isRecharge ? '例如：活动奖励入账（可以留空）' : '例如：提现到银行卡（可以留空）'" no-border />
      <view class="mt-16rpx text-[22rpx] text-gray-400 leading-[34rpx]">
        {{ tip }}
      </view>

      <view class="mt-40rpx flex gap-24rpx">
        <wd-button plain block size="large" @click="show = false">
          取消
        </wd-button>
        <wd-button block type="error" size="large" :loading="submitting" :disabled="!canSubmit" @click="submit">
          {{ submitting ? '提交中…' : isRecharge ? '确认充值' : '确认提现' }}
        </wd-button>
      </view>
    </view>
  </wd-popup>
</template>
