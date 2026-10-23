<script lang="ts" setup>
import type { IShopOrderRow } from '@/api/types/shop'
import { payOrder } from '@/api/order'
import { moneyOf, PAY_TYPE_TEXT } from '@/utils/profile'

/**
 * 待支付那一单的支付弹窗（形状照 shop-web 的 components/OrderPayDialog.vue，搬到手机上从下面推出来）。
 *
 * 提交打的是服务端那条支付回调：主单 0→1、还的锁转正、券核销、积分冻结转消耗、出库单和配送单全在那一段里，
 * 页面只把订单自己报回来的那一笔实付递上去 —— 服务端拿它和重算的数比两位小数，对不上整笔拒，这里一律不重算。
 * 微信/支付宝的网关还没接，所以「确认支付」现在等价于把这一单记成已支付，payType 只是落到 sys_order.pay_type 那一格。
 *
 * 余额那一档点不动（照 Web 那条判据）：余额是在**下单**那一刻占钱的（pay_type=4 落单 + 实付从可用余额冻进冻结），
 * 而这一颗面对的是一张已经落下、当初没选余额的单 —— 账上没冻过这一笔，那边结转支付无从结转，
 * 放过去只会把订单记成「余额已付」而钱一分没减。要拿余额付得先取消这一单重新下。
 */

const props = defineProps<{
  modelValue: boolean
  order: IShopOrderRow | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', visible: boolean): void
  (e: 'paid'): void
}>()

const show = computed({
  get: () => props.modelValue,
  set: visible => emit('update:modelValue', visible),
})

/** 档位就是枚举那一份，顺序按 1..4 排（Object 的整型键 JS 本来就按数值升序给） */
const payOptions = Object.entries(PAY_TYPE_TEXT).map(([value, label]) => ({ value: Number(value), label }))

const PAY_TYPE_BALANCE = 4

const payType = ref<number>(payOptions[0]?.value ?? 1)
const submitting = ref(false)
const error = ref('')

/** 每一次推开都当新的一趟：上一趟选的档、那句错误都不带进下一趟 */
watch(show, (visible) => {
  if (visible) {
    payType.value = payOptions[0]?.value ?? 1
    error.value = ''
  }
})

async function submit() {
  if (!props.order || submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await payOrder({
      orderId: props.order.id,
      payType: payType.value,
      payAmount: props.order.payAmount ?? undefined,
    })
    emit('paid')
  }
  catch (err) {
    // 拒掉那句（状态不允许、金额对不上）由 http 层弹 toast，这一格再留一份在弹窗里，免得 toast 走完了看不见
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
        订单支付
      </view>

      <view class="text-[24rpx] text-gray-400">
        {{ order?.orderNo }}
      </view>
      <view class="mt-8rpx text-[40rpx] text-[#d14328] font-medium">
        {{ moneyOf(order?.payAmount ?? order?.totalAmount) }}
      </view>

      <view v-if="error" class="mt-24rpx rounded-lg bg-red-50 px-24rpx py-16rpx text-[26rpx] text-[#d14328]">
        {{ error }}
      </view>

      <view class="mt-32rpx">
        <view
          v-for="opt in payOptions"
          :key="opt.value"
          hover-class="fx-tap"
          class="fx-press mb-16rpx flex items-center justify-between border rounded-lg px-24rpx py-24rpx text-[28rpx]"
          :class="[
            opt.value === payType
              ? 'border-[#d14328] bg-[#fdece8] text-[#d14328]'
              : 'border-gray-100 text-[#333]',
            opt.value === PAY_TYPE_BALANCE ? 'opacity-45' : '',
          ]"
          @click="opt.value === PAY_TYPE_BALANCE || (payType = opt.value)"
        >
          <text>{{ opt.label }}</text>
          <text v-if="opt.value === PAY_TYPE_BALANCE" class="text-[22rpx] text-gray-400">
            本单不可用
          </text>
          <text v-else-if="opt.value === payType" class="text-[26rpx]">✓</text>
        </view>
      </view>

      <view class="mt-8rpx text-[22rpx] text-gray-400 leading-[34rpx]">
        余额支付在下单那一刻就把钱占住了，已经落下的这一单没冻过这一笔，所以这一档点不动；要拿余额付得先取消这一单重新下。
      </view>
      <view class="mt-16rpx text-[22rpx] text-gray-400 leading-[34rpx]">
        本期微信/支付宝的收款渠道还没接上，「确认支付」是把这一单记成已支付并触发后面那几步（还锁、核销券、结转积分、建出库单）。
      </view>

      <view class="mt-40rpx flex gap-24rpx">
        <wd-button plain block size="large" @click="show = false">
          取消
        </wd-button>
        <wd-button block type="error" size="large" :loading="submitting" :disabled="!order" @click="submit">
          {{ submitting ? '支付中…' : '确认支付' }}
        </wd-button>
      </view>
    </view>
  </wd-popup>
</template>
