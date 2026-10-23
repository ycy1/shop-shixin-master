<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="请输入配送单号/出库单号/收件人/手机号" clearable
            @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="送货员" prop="riderId">
          <el-select v-model="queryParams.riderId" placeholder="全部" clearable filterable>
            <el-option v-for="item in riderOptions" :key="item.id" :label="item.riderName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="配送方式" prop="deliveryType">
          <el-select v-model="queryParams.deliveryType" placeholder="全部" clearable>
            <el-option v-for="item in dict.options('delivery_type')" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options('delivery_status')" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <!-- 两个时间范围同一行、各给控件本身的宽度：跟着等分会被拉到整行那么宽 -->
        <div class="search-row">
          <el-form-item label="接单时间">
            <el-date-picker v-model="acceptRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
              start-placeholder="开始日期" end-placeholder="结束日期" />
          </el-form-item>
          <el-form-item label="派单时间">
            <el-date-picker v-model="assignRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
              start-placeholder="开始日期" end-placeholder="结束日期" />
          </el-form-item>
        </div>
        <el-form-item class="search-actions">
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-card class="box-card">
      <!-- 操作工具栏 -->
      <template #header>
        <ButtonGroup>
          <el-button v-permission="['shop:delivery:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['shop:delivery:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="配送单号" prop="deliveryNo" align="center" min-width="150" />
        <el-table-column label="出库单号" prop="outboundNo" align="center" min-width="150" />
        <el-table-column label="送货员" align="center" min-width="110">
          <template #default="{ row }">
            <span>{{ row.riderName || '未派单' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag('delivery_status', row.status)">
              {{ dict.label('delivery_status', row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="配送方式" align="center" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag('delivery_type', row.deliveryType)">
              {{ dict.label('delivery_type', row.deliveryType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="派单方式" align="center" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag('delivery_assign_type', row.assignType)">
              {{ dict.label('delivery_assign_type', row.assignType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="收件人" align="center" min-width="150">
          <template #default="{ row }">
            <span>{{ [row.receiverName, row.receiverPhone].filter(Boolean).join('-') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="配送费" align="center" width="100">
          <template #default="{ row }">
            <span>{{ validate.formatAmount(row.deliveryFee) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="距离" align="center" width="90">
          <template #default="{ row }">
            <span>{{ row.distance ? `${row.distance} km` : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="330" fixed="right">
          <template #default="scope">
            <el-button v-permission="['shop:delivery:detail']" type="primary" link icon="View"
              @click="handleDetail(scope.row)">详情</el-button>
            <!-- 接单：只在还没派出去的那一单上出现（已经指了人的该走修改，不是接单），
                 而且只有送货员本人的账号看得到 —— 服务端那一条统一接口只认"本人来接"。
                 真正的接单逻辑见 handleAccept -->
            <el-button v-if="isRiderAccount && !scope.row.riderId && scope.row.status === 1"
              v-permission="['shop:delivery:take']"
              type="primary" link icon="Select" :loading="acceptingId === scope.row.id"
              @click="handleAccept(scope.row)">接单</el-button>
            <!-- 取货 / 送达：接单后面那两格，状态一格一跳（2 已接单 → 3 配送中 → 4 已送达），
                 判据和后端 advance 里那句 from 完全一致 —— 没走到那一档点了必被拒，所以整条不给看。
                 这两颗不像接单那样只挂在送货员本人身上：后台点它是"替单上那位送货员记这一步"，rider_id 不动 -->
            <el-button v-if="scope.row.status === 2" v-permission="['shop:delivery:take']" type="primary" link
              icon="Box" :loading="actionId === scope.row.id" @click="handlePickup(scope.row)">取货</el-button>
            <el-button v-if="scope.row.status === 3" v-permission="['shop:delivery:take']" type="primary" link
              icon="CircleCheck" :loading="actionId === scope.row.id" @click="handleDelivered(scope.row)">送达</el-button>
            <!-- 取消：路上那四档（0/1/2/3）都能叫停，判据见 canCancel（和后端 cancellable 同口径）。
                 用弹框收一句取消原因（选填，但事后补不回来），顺带把名下已发货的商品行退回「已出库」 -->
            <el-button v-if="canCancel(scope.row)" v-permission="['shop:delivery:take']" type="danger" link
              icon="CircleClose" :loading="actionId === scope.row.id" @click="handleCancel(scope.row)">取消</el-button>
            <el-button v-permission="['shop:delivery:update']" type="primary" link icon="Edit"
              @click="handleUpdate(scope.row)">修改</el-button>
            <el-button v-permission="['shop:delivery:delete']" type="danger" link icon="Delete"
              @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页工具栏 -->
      <div class="pagination-container">
        <el-pagination background v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 30, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>

      <!-- 添加或修改对话框 -->
      <el-dialog v-model="open" :title="title" width="760px" append-to-body destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
          <el-divider content-position="left">关联信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="配送单号" prop="deliveryNo">
                <el-input v-model="form.deliveryNo" placeholder="保存后由系统生成" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="关联出库单" prop="outboundId">
                <!-- 只列「已出库完成」且**还没建过配送单**的那几张（一出库单一配送，建过的选上必被后端挡回来）：
                     不再列出来再置灰 —— 同一件事在别的列表上也是"做不了就不给看"的口径。
                     配送单绑的就是出库单，不再是订单；这一配送做的是哪笔生意，标签上把来源订单号带出来看 -->
                <el-select v-model="form.outboundId" placeholder="请选择出库单" filterable style="width: 100%"
                  @change="handleOutboundChange">
                  <el-option v-for="item in outboundChoices" :key="item.id" :label="outboundLabel(item)"
                    :value="item.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="出库单号" prop="outboundNo">
                <el-input v-model="form.outboundNo" placeholder="选择出库单后自动带出" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="送货员" prop="riderId">
                <!-- 选中送货员会把他的姓名、手机号灌到下面「发货信息」的发货人/发货电话 -->
                <el-select v-model="form.riderId" placeholder="未派单" clearable filterable style="width: 100%"
                  @change="handleRiderChange">
                  <el-option v-for="item in riderOptions" :key="item.id" :label="item.riderName" :value="item.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="配送方式" prop="deliveryType">
                <el-select v-model="form.deliveryType" placeholder="请选择" style="width: 100%">
                  <el-option v-for="item in dict.options('delivery_type')" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="派单方式" prop="assignType">
                <el-select v-model="form.assignType" placeholder="未派单" clearable style="width: 100%">
                  <el-option v-for="item in dict.options('delivery_assign_type')" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态" prop="status">
                <el-select v-model="form.status" placeholder="请选择" style="width: 100%">
                  <el-option v-for="item in dict.options('delivery_status')" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">收件信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="收件人" prop="receiverName">
                <el-input v-model="form.receiverName" placeholder="请输入收件人姓名" maxlength="50" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="收件电话" prop="receiverPhone">
                <el-input v-model="form.receiverPhone" placeholder="请输入收件人电话" maxlength="20" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="收件地址" prop="receiverAddress">
                <el-input v-model="form.receiverAddress" placeholder="请输入收件地址" maxlength="255" />
              </el-form-item>
            </el-col>
            <el-col :span="18">
              <el-form-item label="收件经纬度" prop="receiverLng">
                <div class="pair-field">
                  <el-input-number v-model="form.receiverLng" :precision="6" :controls="false" placeholder="经度"
                    style="width: 100%" @change="refreshEstimate" />
                  <span class="pair-split">-</span>
                  <el-input-number v-model="form.receiverLat" :precision="6" :controls="false" placeholder="纬度"
                    style="width: 100%" @change="refreshEstimate" />
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <!-- 整表 label-width 是 110px，这一格只有 6 格宽，不取消 label 按钮就顶出去了 -->
              <el-form-item label-width="0">
                <shop-map :longitude="form.receiverLng" :latitude="form.receiverLat" title="选择收件位置"
                  @confirm="handleReceiverLocation" />
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 这一组就是"这一单从哪儿发"的全部记录了：配送单上不再有发货门店那一列，
               按出库单生成的那一张会先带上发货仓的联系人/电话/地址/坐标，人工建的就从这里填 -->
          <el-divider content-position="left">发货信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="发货人" prop="senderName">
                <el-input v-model="form.senderName" placeholder="选送货员后自动带出，可改" maxlength="50" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="发货电话" prop="senderPhone">
                <el-input v-model="form.senderPhone" placeholder="选送货员后自动带出，可改" maxlength="20" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="发货地址" prop="senderAddress">
                <el-input v-model="form.senderAddress" placeholder="请输入发货地址（按出库单生成的会带出发货仓的地址）"
                  maxlength="255" />
              </el-form-item>
            </el-col>
            <el-col :span="18">
              <el-form-item label="发货经纬度" prop="senderLng">
                <div class="pair-field">
                  <el-input-number v-model="form.senderLng" :precision="6" :controls="false" placeholder="经度"
                    style="width: 100%" @change="refreshEstimate" />
                  <span class="pair-split">-</span>
                  <el-input-number v-model="form.senderLat" :precision="6" :controls="false" placeholder="纬度"
                    style="width: 100%" @change="refreshEstimate" />
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label-width="0">
                <shop-map :longitude="form.senderLng" :latitude="form.senderLat" title="选择发货位置"
                  @confirm="handleSenderLocation" />
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 距离和时长按「发货点 → 收件点」自动算，不给手填；要改它们就去改上面那两组坐标 -->
          <el-divider content-position="left">配送数据（按两个地点自动算）</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="配送距离" prop="distance">
                <!-- 这两格不是填的：按上面「发货点 → 收件点」算出来，改任一边的坐标（或选点）就跟着重算 -->
                <el-input-number v-model="form.distance" :min="0" :precision="2" disabled placeholder="算不出：两点都要有坐标"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="预计时长" prop="estimatedTime">
                <!-- 这一格存的是分钟，展示按「X天X小时X分钟」念（constants#formatDuration，和三处详情同一口径） -->
                <el-input :model-value="estimatedTimeText" disabled placeholder="按距离折算" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="配送费" prop="deliveryFee">
                <el-input-number v-model="form.deliveryFee" :min="0" :precision="2" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="小费" prop="tipFee">
                <el-input-number v-model="form.tipFee" :min="0" :precision="2" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="重量" prop="weight">
                <el-input-number v-model="form.weight" :min="0" :precision="2" placeholder="单位：kg"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">时间节点</el-divider>
          <el-row :gutter="20">
            <el-col v-for="node in timeNodes" :key="node.prop" :span="12">
              <el-form-item :label="node.label" :prop="node.prop">
                <el-date-picker v-model="form[node.prop]" type="datetime" placeholder="选填"
                  value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">取消与异常</el-divider>
          <el-row :gutter="20">
            <el-col :span="24">
              <el-form-item label="取消原因" prop="cancelReason">
                <el-input v-model="form.cancelReason" type="textarea" :rows="2" maxlength="255" show-word-limit
                  placeholder="状态为已取消时填写" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="异常原因" prop="exceptionReason">
                <el-input v-model="form.exceptionReason" type="textarea" :rows="2" maxlength="255" show-word-limit
                  placeholder="状态为异常时填写" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="cancel">取 消</el-button>
            <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
          </div>
        </template>
      </el-dialog>

      <!-- 详情弹窗：只读，和「商品出库」「我的订单」那两处看的是同一个组件（自己按 id 查全字段） -->
      <delivery-detail v-model:visible="detailOpen" :id="detailId" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listShopDeliveryApi,
  detailShopDeliveryApi,
  addShopDeliveryApi,
  updateShopDeliveryApi,
  deleteShopDeliveryApi,
  acceptShopDeliveryApi,
  pickupShopDeliveryApi,
  deliveredShopDeliveryApi,
  cancelShopDeliveryApi
} from '@/api/delivery/delivery'
import { optionsShopRiderApi } from '@/api/delivery/rider'
import { listErpSaleOutboundApi } from '@/api/erp/saleOutbound'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import DeliveryDetail from '@/components/DeliveryDetail/index.vue'
import { deliveryTimeNodes, estimateByPoints, formatDuration } from '@/components/DeliveryDetail/constants'
import ShopMap from '@/components/ShopMap/index.vue'
import validate from '@/utils/validate'
import { useDict } from '@/utils/dictCache'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()

/**
 * 当前登录账号是不是送货员档（account_type=2，字典 account_type）。
 * 只用来决定「接单」那颗按钮给不给看：服务端那条统一接口只认"本人来接"，
 * 后台账号点它必然被拒（那条要求点名是哪一位送货员，本页没有这一格），所以整条不给看。
 */
const isRiderAccount = computed(() => userStore.user.accountType === 2)

/** 配送相关字典：配送方式 / 派单方式 / 配送状态 */
const dict = useDict('delivery_type', 'delivery_assign_type', 'delivery_status')

/** 六个时间节点是同一套控件，列出来循环渲染，避免复制六遍（详情弹窗那边用的是同一份常量） */
const timeNodes = deliveryTimeNodes

/** 送货员 / 出库单下拉：送货员只列启用中的，停用的靠列表里回填的名字显示 */
const riderOptions = ref<any[]>([])
/** 可配送的出库单：只有已出库完成（status=2）的那几张，货没出满本来就不配送 */
const outboundOptions = ref<any[]>([])

/** 出库单状态（字典 sale_outbound_status）：2 已出库 —— 货没出满的那几张不进候选 */
const OUTBOUND_STATUS_OUT = 2

/**
 * 下拉里说清这是哪张出库单、送的是哪笔订单。
 * 建过配送的那几张根本不在候选里（见 outboundChoices），唯一的例外是改单时自己绑着的那一张 ——
 * 它被滤掉的话下拉框就显示不出当前值了，所以标签上不另加说明。
 */
const outboundLabel = (row: any) => {
  const parts = [row.outboundNo]
  if (row.orderNo) parts.push(`订单 ${row.orderNo}`)
  return parts.join(' · ')
}

/**
 * 下拉里的候选 = 拉回来的已出库单 − 已经建过配送单的那几张（一出库单一配送，deliveryId 由列表 SQL 带回来）。
 * 例外：正在修改的这条自己绑着的那一张要留着，不然一进修改弹窗、当前值就因为不在选项里而显示空。
 * 之所以只取 200 条：这一路是给手工建单挑单用的，新单在前；真要在历史出库单里翻，去「商品出库」那页按单号找。
 */
const outboundChoices = computed(() => outboundOptions.value.filter(
  (item: any) => !item.deliveryId || item.id === form.outboundId))

const getOptions = async () => {
  try {
    const [rider, outbound] = await Promise.all([
      optionsShopRiderApi(),
      listErpSaleOutboundApi({ pageNum: 1, pageSize: 200, status: OUTBOUND_STATUS_OUT })
    ])
    riderOptions.value = rider.data || []
    outboundOptions.value = outbound.data?.records || []
  } catch (error) {
  }
}

/** 时间范围：后端要的是两个整日边界，提交前各自拼成 xxxBeginTime/xxxEndTime */
const acceptRange = ref<string[]>([])
const assignRange = ref<string[]>([])

const loading = ref(true)
const submitLoading = ref(false)
const selectedIds = ref<any[]>([])
const total = ref(0)
const dataList = ref<any[]>([])
const title = ref('')
const open = ref(false)

/** 详情用的是公共组件 DeliveryDetail：它自己按 id 查全字段，这里只管开合和传 id */
const detailOpen = ref(false)
const detailId = ref<number | string>()

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// 查询参数
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  riderId: undefined,
  deliveryType: undefined,
  status: undefined
})

// 表单参数
const form = reactive<any>({})

const rules = reactive<FormRules>({
  outboundId: [{ required: true, message: '请选择关联出库单', trigger: 'change' }],
  receiverName: [{ required: true, message: '收件人姓名不能为空', trigger: 'blur' }],
  receiverPhone: [
    { required: true, message: '收件人电话不能为空', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  receiverAddress: [{ required: true, message: '收件地址不能为空', trigger: 'blur' }]
})

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const params: any = { ...queryParams }
    if (acceptRange.value && acceptRange.value.length === 2) {
      params.acceptBeginTime = `${acceptRange.value[0]} 00:00:00`
      params.acceptEndTime = `${acceptRange.value[1]} 23:59:59`
    }
    if (assignRange.value && assignRange.value.length === 2) {
      params.assignBeginTime = `${assignRange.value[0]} 00:00:00`
      params.assignEndTime = `${assignRange.value[1]} 23:59:59`
    }
    const { data } = await listShopDeliveryApi(params)
    dataList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
  } finally {
    loading.value = false
  }
}

/** 表单重置 */
const reset = () => {
  Object.assign(form, {
    id: undefined,
    deliveryNo: undefined,
    outboundId: undefined,
    outboundNo: '',
    riderId: undefined,
    deliveryType: dict.defaultValue('delivery_type', 1),
    assignType: undefined,
    receiverName: undefined,
    receiverPhone: undefined,
    receiverAddress: undefined,
    receiverLng: undefined,
    receiverLat: undefined,
    senderName: undefined,
    senderPhone: undefined,
    senderAddress: undefined,
    senderLng: undefined,
    senderLat: undefined,
    distance: undefined,
    estimatedTime: undefined,
    deliveryFee: 0,
    tipFee: 0,
    weight: undefined,
    expectTime: undefined,
    assignTime: undefined,
    acceptTime: undefined,
    pickupTime: undefined,
    deliveredTime: undefined,
    cancelTime: undefined,
    status: dict.defaultValue('delivery_status', 0),
    cancelReason: undefined,
    exceptionReason: undefined
  })
  formRef.value?.resetFields()
}

/** 取消按钮 */
const cancel = () => {
  open.value = false
  reset()
}

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.keyword = undefined
  queryParams.riderId = undefined
  queryParams.deliveryType = undefined
  queryParams.status = undefined
  acceptRange.value = []
  assignRange.value = []
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
}

/** 选了出库单就把冗余的出库单号带上（服务端还会按 outboundId 重查一次，这里只为了立刻回显） */
const handleOutboundChange = (outboundId: any) => {
  const outbound = outboundOptions.value.find((item: any) => item.id === outboundId)
  form.outboundNo = outbound ? outbound.outboundNo : ''
}

/**
 * 「配送数据」那两格按「发货点 → 收件点」现算（两点缺任何一边就清空），改坐标 / 地图选点之后都调它。
 * 只是给人看的预览：保存时服务端按同一口径再算一遍并以它为准（ShopDeliveryServiceImpl#fillEstimate）。
 * 预计送达不在这里跟着动 —— 那一格是「时间节点」里人工可以填的，只有新增建单那一刻由系统按时长补。
 */
const refreshEstimate = () => {
  const estimate = estimateByPoints(form.senderLng, form.senderLat, form.receiverLng, form.receiverLat)
  form.distance = estimate ? estimate.distance : undefined
  form.estimatedTime = estimate ? estimate.estimatedTime : undefined
}

/** 预计时长那一格只是把 form.estimatedTime（分钟）换个念法，改的还是同一个值 */
const estimatedTimeText = computed(() => formatDuration(form.estimatedTime))

/**
 * 选中送货员：他本人就是这一单出去发货的人，姓名和手机号盖掉发货信息那两格。
 * 送货员表里存的是 rider_name + phone（不是 riderPhone），options 接口整行带回来，不用另查。
 * 清空送货员不动这两格 —— 建单时人工填的那一组本来就是合理的回退。
 */
const handleRiderChange = (riderId: any) => {
  const rider = riderOptions.value.find((item: any) => item.id === riderId)
  if (!rider) return
  if (rider.riderName) form.senderName = rider.riderName
  if (rider.phone) form.senderPhone = rider.phone
}

/** 地图选点回填（经纬度留 6 位小数）：没挪点时组件抛的 address 是空串，不会冲掉手填地址 */
const handleSenderLocation = (point: any) => {
  form.senderLng = Number(Number(point.longitude).toFixed(6))
  form.senderLat = Number(Number(point.latitude).toFixed(6))
  if (point.address) form.senderAddress = point.address
  refreshEstimate()
}

const handleReceiverLocation = (point: any) => {
  form.receiverLng = Number(Number(point.longitude).toFixed(6))
  form.receiverLat = Number(Number(point.latitude).toFixed(6))
  if (point.address) form.receiverAddress = point.address
  refreshEstimate()
}

/** 详情：列表那一行只带了列表要展示的几列，交给弹窗按 id 再查一次全字段 */
const handleDetail = (row: any) => {
  detailId.value = row.id
  detailOpen.value = true
}

/** 新增按钮操作 */
const handleAdd = () => {
  reset()
  open.value = true
  title.value = '添加配送单'
}

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailShopDeliveryApi(row.id)
    Object.assign(form, data)
    open.value = true
    title.value = '修改配送单'
  } catch (error) {
  }
}

/** 正在接单的那一单 id：按钮上转圈，顺手挡住连点（真正挡并发靠服务端 UPDATE 里那句条件） */
const acceptingId = ref<number | string>()

/**
 * 接单：把这一单揽到自己（送货员）名下，后端顺带按他的交通工具估出预计时长/预计送达，
 * 并把这一单里已经出满的那几行订单明细标成「已发货」。
 *
 * 走的是两端共用的那一条统一接口（/api/comm/shop/delivery/accept，送货员那一端调的也是它）。
 * 这一颗只挂在送货员本人账号上（见 isRiderAccount）：那一条接口只认"本人来接"，
 * 后台账号点它会被拒（要它点名是哪一位送货员，本页没这一格）—— 替人派单走「修改」里那位送货员。
 */
const handleAccept = async (row: any) => {
  try {
    await ElMessageBox.confirm(
      `确认接下配送单「${row.deliveryNo}」？接过来会按你的交通工具估算预计送达时间，这一单里已出库的商品会标成「已发货」`,
      '接单',
      { confirmButtonText: '确认接单', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch (error) {
    return
  }
  acceptingId.value = row.id
  try {
    const { data } = await acceptShopDeliveryApi({ deliveryId: row.id })
    ElMessage.success(
      data?.estimatedTime
        ? `接单成功，预计 ${formatDuration(data.estimatedTime)}后送达`
        : '接单成功（这一单的发货点或收件点没坐标，预计送达时间估不出来）'
    )
    // 接完这一单就归自己了：他从"还没人接"那一批里出去，列表要重新捞一次（顺带能看到估出来的时长）
    getList()
  } catch (error) {
  } finally {
    acceptingId.value = undefined
  }
}

/** 正在取货/送达/取消的那一单 id：同一时刻只有一颗按钮转圈，顺手挡住连点（真正挡并发靠服务端 UPDATE 里那句状态条件） */
const actionId = ref<number | string>()

/**
 * 接单后面那两格的共同实现：只差"从哪一档来、往哪一档去"，校验和写法一模一样，
 * 所以收成这一段（和后端 ShopDeliveryAcceptServiceImpl#advance 同一份思路）。
 *
 * 都走两端共用的那一条统一接口（/api/comm/shop/delivery/pickup、/delivered），入参只有 deliveryId：
 * 这一单派给谁就是谁在跑，送货员账号只能动自己名下的（后端按 rider_id 认），后台账号是替单上那位记这一步。
 * 状态一格一跳，所以两档各挂一颗按钮（status 2 才给取货、3 才给送达），不跨档替人补记。
 */
const runAdvance = async (row: any, step: 'pickup' | 'delivered') => {
  const pickup = step === 'pickup'
  const label = pickup ? '取货' : '送达'
  try {
    await ElMessageBox.confirm(
      pickup
        ? `确认已取到配送单「${row.deliveryNo}」的货、开始配送？这一单会记为「配送中」`
        : `确认配送单「${row.deliveryNo}」已送到收件人手上？这一单会记为「已送达」`,
      label,
      { confirmButtonText: `确认${label}`, cancelButtonText: '再想想', type: 'warning' }
    )
  } catch (error) {
    return
  }
  actionId.value = row.id
  try {
    const { data } = await (pickup ? pickupShopDeliveryApi : deliveredShopDeliveryApi)({ deliveryId: row.id })
    ElMessage.success(`${label}成功，这一单现在是「${dict.label('delivery_status', data?.status)}」`)
    getList()
  } catch (error) {
  } finally {
    actionId.value = undefined
  }
}

const handlePickup = (row: any) => runAdvance(row, 'pickup')

const handleDelivered = (row: any) => runAdvance(row, 'delivered')

/**
 * 还能取消的四档（字典 delivery_status）：0 待派单 / 1 待接单 / 2 已接单 / 3 配送中，
 * 和后端 ShopDeliveryAcceptServiceImpl#cancel 里那个 cancellable 完全一致 —— 4 已送达取消不了
 * （要退货物去「我的订单」提退货），5 已取消再点只会得到一句"这一单已经取消过了"，所以整条不给看。
 * 异常那一档（6）后端也不收，跟着一起挡在外面。
 */
const CANCELLABLE_STATUS = [0, 1, 2, 3]

/**
 * 这一单能不能给当前账号点取消：除了状态那一格，还多一道送货员账号的限制 ——
 * 抢单池里还没派出去的单，送货员账号点取消后端会直接拒（那是后台这页的活），所以那种单不给他看这颗。
 */
const canCancel = (row: any) =>
  CANCELLABLE_STATUS.includes(row.status) && (!isRiderAccount.value || !!row.riderId)

/**
 * 取消配送：这一单不送了 → 5 已取消，记 cancel_time + cancel_reason，
 * 并且把这一单名下「已发货」的订单明细行退回「已出库」（货没送到，那一行还得能再发一次）。
 *
 * 走两端共用的那一条统一接口（/api/comm/shop/delivery/cancel）。取消原因是选填的（不填后端兜一句
 * 「配送单人工取消」），但这里还是问一句：这一格以后是查"为什么没送到"的唯一线索，事后补不回来。
 */
const handleCancel = async (row: any) => {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(
      `确认不再配送「${row.deliveryNo}」？这一单会记为「已取消」，名下已发货的商品行退回「已出库」，库存不动`,
      '取消配送',
      {
        confirmButtonText: '确认取消',
        cancelButtonText: '再想想',
        inputPlaceholder: '取消原因（选填）',
        inputType: 'textarea',
        inputAttributes: { maxlength: 500 }
      }
    )
    reason = (value || '').trim()
  } catch (error) {
    return
  }
  actionId.value = row.id
  try {
    const { data } = await cancelShopDeliveryApi({ deliveryId: row.id, cancelReason: reason || undefined })
    ElMessage.success(
      `已取消，${data?.revertedItemCount ? `名下 ${data.revertedItemCount} 行商品退回「已出库」` : '这一单没有需要退回的商品行'}`
    )
    getList()
  } catch (error) {
  } finally {
    actionId.value = undefined
  }
}

/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateShopDeliveryApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopDeliveryApi(form)
        ElMessage.success('新增成功')
      }
      open.value = false
      getList()
    } catch (error) {
    } finally {
      submitLoading.value = false
    }
  })
}

/** 删除按钮操作 */
const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确认删除配送单「${row.deliveryNo}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopDeliveryApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个配送单吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopDeliveryApi(selectedIds.value)
    ElMessage.success('删除成功')
    selectedIds.value = []
    getList()
  }).catch(() => { })
}

/** 分页 */
const handleSizeChange = () => {
  queryParams.pageNum = 1
  getList()
}
const handleCurrentChange = () => {
  getList()
}

onMounted(() => {
  getList()
  getOptions()
})
</script>

<style scoped lang="scss">
/* 参照部门管理：筛选项等分整行，控件宽度统一由 CSS 控制 */
.search-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;

  :deep(.el-form-item) {
    flex: 1 1 0;
    min-width: 180px;
    margin-right: 16px;

    &:last-child {
      flex: 0 0 auto;
      min-width: 0;
      margin-right: 0;
    }
  }

  :deep(.el-form-item .el-form-item__content) {
    min-width: 0;
  }

  /* global.scss 里对 .el-input/.el-select 写死了 240px，特异性更高，只能用 !important 覆盖 */
  :deep(.el-form-item .el-input),
  :deep(.el-form-item .el-select) {
    width: 100% !important;
  }
}

/* 两个时间范围同一行（照抄商品列表的 .search-row）：整行独占，内部左对齐 */
.search-form :deep(.search-row) {
  flex: 0 0 100%;
  display: flex;
  flex-wrap: nowrap;

  .el-form-item {
    flex: 0 0 auto;
    min-width: 0;
  }

  /* 略宽于 Element Plus daterange 的默认 350px，够放下"开始日期 至 结束日期" */
  .el-date-editor {
    width: 360px !important;
  }
}

/* 按钮那一格独占一行（flex-basis 100% 就会换行）。
   上面 :last-child 把它压成了 0 0 auto，所以这里选择器多带一个 .el-form-item，特异性够、又写在后面才顶得住 */
.search-form :deep(.el-form-item.search-actions) {
  flex: 1 1 100%;
  min-width: 0;
  margin-right: 0;
}

/* 一段拆成两格的字段（经纬度）：两个输入等分整行，中间夹一个分隔符 */
.pair-field {
  display: flex;
  gap: 8px;
  width: 100%;

  :deep(.el-select),
  :deep(.el-input-number) {
    flex: 1;
    min-width: 0;
  }
}

/* 分隔符不参与伸缩，高度对齐输入框、文字居中，颜色压到占位符一档免得抢输入框 */
.pair-split {
  flex: 0 0 auto;
  height: var(--el-component-size, 32px);
  line-height: var(--el-component-size, 32px);
  text-align: center;
  color: var(--el-text-color-placeholder);
  user-select: none;
}
</style>
