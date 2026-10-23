<template>
  <!--
    配送单详情：只读，自己按 id 查一次全字段（调用方手里通常只有列表那几列）。
    从「商品出库」列表点进来时传的 id 就是列表带回的 deliveryId，一张出库单最多一条配送单。
  -->
  <el-dialog v-model="dialogVisible" :title="title" width="760px" append-to-body destroy-on-close
    @open="handleOpen">
    <div v-loading="loading">
      <el-descriptions title="基本信息" :column="2" border>
        <el-descriptions-item label="配送单号">{{ info.deliveryNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="dict.tag('delivery_status', info.status)">
            {{ dict.label('delivery_status', info.status) }}
          </el-tag>
        </el-descriptions-item>
        <!-- 这一格里可能是订单也可能是出库单（还没换绑的那一条装的是订单），标题跟着值走，见下面的 bindingLabel -->
        <el-descriptions-item :label="bindingLabel">{{ info.outboundNo || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="bindingIdLabel">{{ info.outboundId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="送货员">{{ info.riderName || '未派单' }}</el-descriptions-item>
        <el-descriptions-item label="配送方式">
          <el-tag size="small" :type="dict.tag('delivery_type', info.deliveryType)">
            {{ dict.label('delivery_type', info.deliveryType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="派单方式">
          <el-tag v-if="info.assignType != null" size="small" :type="dict.tag('delivery_assign_type', info.assignType)">
            {{ dict.label('delivery_assign_type', info.assignType) }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-descriptions class="mt-16" title="收件信息" :column="2" border>
        <el-descriptions-item label="收件人">{{ info.receiverName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="收件电话">{{ info.receiverPhone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="收件地址" :span="2">{{ info.receiverAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="收件经纬度" :span="2">{{ coordinate(info.receiverLng, info.receiverLat) }}
        </el-descriptions-item>
      </el-descriptions>

      <el-descriptions class="mt-16" title="发货信息" :column="2" border>
        <el-descriptions-item label="发货人">{{ info.senderName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发货电话">{{ info.senderPhone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发货地址" :span="2">{{ info.senderAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发货经纬度" :span="2">{{ coordinate(info.senderLng, info.senderLat) }}
        </el-descriptions-item>
      </el-descriptions>

      <el-descriptions class="mt-16" title="配送数据" :column="2" border>
        <el-descriptions-item label="配送距离">{{ withUnit(info.distance, 'km') }}</el-descriptions-item>
        <!-- 库里存的是分钟，这一格按「X天X小时X分钟」念给人听（口径见 constants#formatDuration） -->
        <el-descriptions-item label="预计时长">{{ durationOrDash(info.estimatedTime) }}</el-descriptions-item>
        <el-descriptions-item label="配送费">{{ validate.formatAmount(info.deliveryFee) }}</el-descriptions-item>
        <el-descriptions-item label="小费">{{ validate.formatAmount(info.tipFee) }}</el-descriptions-item>
        <el-descriptions-item label="重量">{{ withUnit(info.weight, 'kg') }}</el-descriptions-item>
        <el-descriptions-item label="计价规则">{{ feeRuleText(info) }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions class="mt-16" title="时间节点" :column="2" border>
        <el-descriptions-item v-for="node in timeNodes" :key="node.prop" :label="node.label">
          {{ validate.formatTime(info[node.prop]) }}
        </el-descriptions-item>
      </el-descriptions>

      <el-descriptions class="mt-16" title="取消与异常" :column="1" border>
        <el-descriptions-item label="取消原因">{{ info.cancelReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="异常原因">{{ info.exceptionReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ validate.formatTime(info.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ validate.formatTime(info.updateTime) }}</el-descriptions-item>
      </el-descriptions>
    </div>
    <template #footer>
      <!-- 「商品出库」列表上那颗「生成配送单」进来的时候才多这一颗：看完这一单的发货点/距离/重量再派车 -->
      <el-button v-if="confirmable" type="primary" :loading="confirmLoading" @click="emit('confirm')">确 认</el-button>
      <el-button @click="dialogVisible = false">关 闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { detailShopDeliveryApi } from '@/api/delivery/delivery'
import { deliveryTimeNodes, formatDuration } from './constants'
import { useDict } from '@/utils/dictCache'
import validate from '@/utils/validate'

const props = withDefaults(defineProps<{
  visible: boolean
  /** shop_delivery.id，打开时按它取详情 */
  id?: number | string
  title?: string
  /** 底下多不多那一颗「确认」：「商品出库」列表上那颗「生成配送单」进来的时候才给 */
  confirmable?: boolean
  /** 确认正在路上（按钮转圈，防连点） */
  confirmLoading?: boolean
}>(), {
  id: undefined,
  title: '配送单详情',
  confirmable: false,
  confirmLoading: false
})

const emit = defineEmits<{
  'update:visible': [visible: boolean]
  /** 弹框底下那颗「确认」：把这一条从 0 待派单 推成 1 待接单（动作由调用方做，它才知道自己要点谁） */
  confirm: []
}>()

const dict = useDict('delivery_type', 'delivery_assign_type', 'delivery_status')

const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value)
})

/** 六个时间节点是同一套字段，循环渲染；和配送管理那边新增/修改表单用的是同一份常量 */
const timeNodes = deliveryTimeNodes

const loading = ref(false)
const info = ref<any>({})

/**
 * 关联那一格顶上写的是哪张单，要看值本身：outbound_id / outbound_no 是一槽两用 ——
 * 统一下单建出来的那一条先装订单的那两个值，支付回调才换成出库单的（分辨证据就是号的前缀：
 * 订单号 ORDER_ 打头、出库单号 SALE_OUTBOUND_ 打头）。
 */
const boundToOutbound = computed(() => String(info.value.outboundNo || '').startsWith('SALE_OUTBOUND_'))
const bindingLabel = computed(() => (boundToOutbound.value ? '关联出库单' : '关联订单（还没换绑出库单）'))
const bindingIdLabel = computed(() => (boundToOutbound.value ? '出库单ID' : '订单ID'))

const coordinate = (lng: any, lat: any) => (lng == null || lat == null ? '-' : `${lng}, ${lat}`)

const withUnit = (value: any, suffix: string) => (value == null ? '-' : `${value} ${suffix}`)

const durationOrDash = (minutes: any) => formatDuration(minutes) || '-'

/**
 * 这笔配送费是哪条规则算的（名字由服务端按 fee_rule_id 现查）：
 * 没有 id = 这一单的钱不是规则算的（配送管理里人工填的价）；有 id 查不到名字 = 那条规则后来被删了。
 */
const feeRuleText = (row: any) => row.feeRuleName || (row.feeRuleId ? '规则已删除' : '-')

// 每次打开都重查：配送状态是送货员那边一路推着的，缓存一条就一直是旧样子
const handleOpen = async () => {
  if (!props.id) return
  info.value = {}
  loading.value = true
  try {
    const { data } = await detailShopDeliveryApi(props.id)
    info.value = data || {}
  } catch (error) {
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
/* 分组之间的间距：el-descriptions 自己不带外边距，项目也没有全局 .mt-16 工具类 */
.mt-16 {
  margin-top: 16px;
}
</style>
