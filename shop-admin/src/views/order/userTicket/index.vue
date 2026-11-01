<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="票码/票种名/持票人姓名电话" clearable
            @keyup.enter="handleQuery" />
        </el-form-item>
        <!-- 消费者账号进来时服务端已经把 userId 钉成本人，这一格选了也会被覆盖（所以不给看） -->
        <el-form-item v-if="!isConsumerAccount" label="持票用户" prop="userId">
          <UserSelect v-model="queryParams.userId" :account-type="3" @select="handleQuery" @clear="handleQuery" />
        </el-form-item>
        <el-form-item label="所属园区" prop="parkId">
          <el-select v-model="queryParams.parkId" placeholder="全部" clearable>
            <el-option v-for="item in parkOptions" :key="item.id" :label="item.parkName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="票种类型" prop="ticketType">
          <el-select v-model="queryParams.ticketType" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_STATUS)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <!-- 时间范围卡的是游玩日期（不是出票时间）：这一页问的是"哪天来玩"，不是"哪天买的" -->
        <div class="search-row">
          <el-form-item label="游玩日期" prop="visitRange">
            <el-date-picker v-model="visitRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
              start-placeholder="开始日期" end-placeholder="结束日期" />
          </el-form-item>
          <el-form-item class="search-actions">
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </div>
      </el-form>
    </div>

    <el-card class="box-card">
      <!-- 操作工具栏 -->
      <template #header>
        <ButtonGroup>
          <!-- 这一期只有"后台代客出票"这一条写入路（还没接下单支付），所以新增按钮就是出票 -->
          <el-button v-permission="['shop:userTicket:add']" type="primary" plain icon="Plus"
            @click="handleAdd">代客出票</el-button>
          <el-button v-permission="['shop:userTicket:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格：一行是一张发到用户手上的票。票名/类型/票价都是出票那一刻的快照，
           票种后来改名改价不回刷这些行（口径同营销活动那本用户优惠券） -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="票码" prop="ticketCode" align="center" min-width="170" />
        <!-- 票面二维码是出票那一刻由服务端生成并上传的（ShopParkTicketIssueService）；
             生成失败只记日志、不影响出票，所以这一格为空是有的 —— 那时按票码那一串手输核销照样能进 -->
        <el-table-column label="票面二维码" align="center" width="100" :show-overflow-tooltip="false">
          <template #default="{ row }">
            <el-image v-if="row.qrImg" :src="row.qrImg" :preview-src-list="[row.qrImg]" preview-teleported
              fit="contain" class="qr-thumb" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="持票用户" align="center" min-width="150">
          <template #default="{ row }">
            <span>{{ row.userName || `已删除用户 #${row.userId}` }}</span>
            <span v-if="row.mobile" class="sub-text">（{{ row.mobile }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="园区" align="center" min-width="140">
          <template #default="{ row }">
            <span>{{ row.parkName || (row.parkId ? `已删除园区 #${row.parkId}` : '-') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="票种（快照）" prop="ticketName" align="center" min-width="150" />
        <el-table-column label="类型" align="center" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_TYPE, row.ticketType)">
              {{ dict.label(DICT_TYPE, row.ticketType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="票价" align="right" width="100">
          <template #default="{ row }">{{ priceText(row.price) }}</template>
        </el-table-column>
        <el-table-column label="游玩日期" align="center" width="120">
          <template #default="{ row }">
            <span :class="{ 'is-past': isPast(row.visitDate) }">{{ row.visitDate || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时段" align="center" width="90">
          <template #default="{ row }">{{ dict.label(DICT_SLOT, row.timeSlot) }}</template>
        </el-table-column>
        <el-table-column label="持票人" align="center" min-width="150">
          <template #default="{ row }">
            <span>{{ row.holderName || '-' }}</span>
            <span v-if="row.holderPhone" class="sub-text">（{{ row.holderPhone }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_STATUS, row.status)">
              {{ dict.label(DICT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 核销/退款那一格：没走过那一步就显示 '-'，两个时间点各是各的动作 -->
        <el-table-column label="核销时间" align="center" width="150">
          <template #default="{ row }">{{ shortTime(row.usedTime) }}</template>
        </el-table-column>
        <el-table-column label="退款时间" align="center" width="150">
          <template #default="{ row }">{{ shortTime(row.refundTime) }}</template>
        </el-table-column>
        <el-table-column label="出票时间" align="center" width="150">
          <template #default="{ row }">{{ shortTime(row.createTime) }}</template>
        </el-table-column>
        <!-- 四颗按钮走公共「更多」组件：收成 2 直出 + 下拉（口径同优惠券、盲盒那两页的四颗）。
             「入园核销」不放这一页：核销是闸机/扫码那一侧的动作，后台这本列表只管出票、改票、退钱、补图
             （那颗端点还在 /shop/userTicket/verify/{id}，接扫码那侧时直接调，不用在这页补按钮）。
             「补/重出二维码」不给状态判据：出票时那一趟出图失败（qr_img 那格是 '-'）要能补，
             图已经在了再点也只是按同一个票码换一张新图，扫出来一模一样 -->
        <el-table-column label="操作" align="center" width="230" fixed="right">
          <template #default="{ row }">
            <TableMoreActions :actions="[
              { label: '退款', type: 'warning', icon: 'RefreshLeft', hidden: row.status === 3 || !hasPermission('shop:userTicket:refund'), loading: refundingId === row.id, command: { type: 'refund', row } },
              { label: '修改', icon: 'Edit', hidden: row.status !== 0 || !hasPermission('shop:userTicket:update'), command: { type: 'update', row } },
              { label: row.qrImg ? '重出二维码' : '补出二维码', type: 'info', icon: 'Refresh', hidden: !hasPermission('shop:userTicket:update'), loading: qrRefreshing === row.id, tip: row.qrImg ? '按同一个票码重新生成一张票面图，扫出来还是这一串' : '出票时那趟出图没成，补一张', command: { type: 'qr', row } },
              { label: '删除', type: 'danger', icon: 'Delete', hidden: !hasPermission('shop:userTicket:delete'), command: { type: 'delete', row } }
            ]" @command="handleActionCommand" />
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页工具栏 -->
      <div class="pagination-container">
        <el-pagination background v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 30, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>

      <!--
        代客出票 / 修改一张票。
        出票时只填「给谁、哪张票种、哪天来玩、时段、持票人」：园区和票名/类型/票价由服务端按票种行带成快照，
        票码也由服务端生成，所以这几格在这个弹窗里一个都填不了（修改时只读给看）。
        修改那一趟只放开游玩日期、时段、持票人三格和备注，状态由「入园核销」「退款」两颗按钮推，表单改不动。
      -->
      <el-dialog v-model="open" :title="title" width="720px" append-to-body destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="持票用户" prop="userId">
                <user-select v-model="form.userId" :label="form.userName" :account-type="3" :disabled="!!form.id"
                  placeholder="选择消费者账号" @select="handleHolderSelect" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="所属园区" prop="parkId">
                <!-- 园区在这里只是"筛一下票种候选"的那一手：真正落到票上那格 park_id 由服务端按票种行带 -->
                <el-select v-model="filterParkId" placeholder="先挑园区（也可以直接搜票种）" clearable filterable
                  style="width: 100%" @change="handleParkChange">
                  <el-option v-for="item in parkOptions" :key="item.id" :label="item.parkName" :value="item.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="票种" prop="ticketId">
                <el-select v-model="form.ticketId" placeholder="请选择票种" filterable :disabled="!!form.id"
                  style="width: 100%" @change="handleTicketChange">
                  <el-option v-for="item in ticketChoices" :key="item.id" :label="ticketLabel(item)"
                    :value="item.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="游玩日期" prop="visitDate">
                <el-date-picker v-model="form.visitDate" type="date" value-format="YYYY-MM-DD" placeholder="哪天来玩"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="入园时段" prop="timeSlot">
                <!-- 一张票约的是<b>一个</b>时段（库里这一列是 tinyint），候选由所选票种卖的那几档里出；
                     票种只有「全天」一档时这里就只有这一个选项，且已经替你选好 -->
                <el-select v-model="form.timeSlot" placeholder="请选择" style="width: 100%">
                  <el-option v-for="item in slotChoices" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="票价" prop="price">
                <!-- 快照那一格，只读：出票时由服务端按票种行带，页面改了也不认 -->
                <el-input :model-value="priceText(form.price)" disabled placeholder="选完票种自动带出" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="持票人姓名" prop="holderName">
                <el-input v-model="form.holderName" placeholder="选填，一票一人时填这个人" maxlength="50" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="持票人电话" prop="holderPhone">
                <el-input v-model="form.holderPhone" placeholder="选填" maxlength="20" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="身份证号" prop="holderIdCard">
                <el-input v-model="form.holderIdCard" placeholder="选填，实名入园的票种才要" maxlength="32" />
              </el-form-item>
            </el-col>
            <el-col v-if="form.id" :span="12">
              <el-form-item label="状态">
                <el-tag size="small" :type="dict.tag(DICT_STATUS, form.status)">
                  {{ dict.label(DICT_STATUS, form.status) }}
                </el-tag>
              </el-form-item>
            </el-col>
            <el-col v-if="!form.id" :span="12">
              <el-form-item label="出票张数" prop="quantity">
                <!-- 一次点几张就落几行、每行一个票码（票种上写了每单限购的，服务端按那个数拦）；
                     改票这一路用不上这一格，所以只在新建时出现 -->
                <el-input-number v-model="form.quantity" :min="1" :max="99" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col v-if="form.id" :span="12">
              <el-form-item label="票面二维码">
                <!-- 出票那一刻由服务端生成并上传（图没了也不影响这张票，按票码手输照样能核销） -->
                <el-image v-if="form.qrImg" :src="form.qrImg" :preview-src-list="[form.qrImg]" preview-teleported
                  fit="contain" class="qr-thumb" />
                <span v-else>没生成（可以按票码手输核销）</span>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="备注" prop="remark">
                <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填，比如「电话订票、现场补录」"
                  maxlength="255" show-word-limit />
              </el-form-item>
            </el-col>
          </el-row>
          <div class="tip">
            出票那一刻会把票名、类型、票价按所选票种定格在这一行上，之后票种改了名改了价都不回头刷已经发出去的票。
            <template v-if="form.id">
              <br />票码 {{ form.ticketCode }} 由系统生成、改不了；要换成别的票种只能删了这张重出一张。
            </template>
          </div>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="cancel">取 消</el-button>
            <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
          </div>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listUserTicketApi,
  detailUserTicketApi,
  addUserTicketApi,
  updateUserTicketApi,
  refundUserTicketApi,
  refreshUserTicketQrApi,
  deleteUserTicketApi
} from '@/api/order/userTicket'
import { optionsShopParkApi } from '@/api/trendy/park'
import { optionsShopTicketApi } from '@/api/trendy/parkTicket'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import TableMoreActions from '@/components/TableMoreActions/index.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import dayjs from 'dayjs'
import validate from '@/utils/validate'
import { useDict } from '@/utils/dictCache'
import { useUserStore } from '@/store/modules/user'

const DICT_TYPE = 'ticket_type'
const DICT_SLOT = 'ticket_time_slot'
const DICT_STATUS = 'user_ticket_status'

const dict = useDict(DICT_TYPE, DICT_SLOT, DICT_STATUS)

const userStore = useUserStore()
/** 操作列走 TableMoreActions，权限得在这里自己判（组件没有 v-permission 那套指令，只认 actions 上的 hidden） */
const permissions = computed(() => userStore.user.permissions || [])
const hasPermission = (permission: string): boolean => permissions.value.includes(permission)

/**
 * 当前登录的是否消费者账号（字典 account_type 的 3）：只用来决定显不显持票用户那一格筛选，
 * 真正的数据范围在后端（ShopParkUserTicketServiceImpl 会把 userId 覆盖成本人）。
 */
const isConsumerAccount = computed(() => userStore.user.accountType === 3)

const loading = ref(true)
const submitLoading = ref(false)
const selectedIds = ref<any[]>([])
const total = ref(0)
const dataList = ref<any[]>([])
const title = ref('')
const open = ref(false)

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// 查询参数
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  userId: undefined,
  parkId: undefined,
  ticketType: undefined,
  status: undefined
})
/** 游玩日期区间（含两端），提交时拆成 beginVisitDate / endVisitDate */
const visitRange = ref<string[]>([])

// 表单参数
const form = reactive<any>({})

const rules = reactive<FormRules>({
  userId: [{ required: true, message: '请选择持票用户', trigger: 'change' }],
  ticketId: [{ required: true, message: '请选择票种', trigger: 'change' }],
  visitDate: [{ required: true, message: '请选择游玩日期', trigger: 'change' }]
})

/** 园区候选（筛选和表单里筛票种共用这一份） */
const parkOptions = ref<any[]>([])
const getParkOptions = async () => {
  try {
    const { data } = await optionsShopParkApi()
    parkOptions.value = data || []
  } catch (error) {
  }
}

/** 票种候选：上架中的，按表单里挑的园区收窄 */
const ticketOptions = ref<any[]>([])
const filterParkId = ref<any>(undefined)
const getTicketOptions = async (parkId?: any) => {
  try {
    const { data } = await optionsShopTicketApi(parkId)
    ticketOptions.value = data || []
  } catch (error) {
  }
}

/** 换了园区就把票种候选重查一遍（清掉已选的票种，否则会留下一张别的园区的票在上面） */
const handleParkChange = () => {
  form.ticketId = undefined
  form.price = undefined
  getTicketOptions(filterParkId.value)
}

/** 下拉上那一串：园区 · 票名 · 价格 · 它卖的时段，选的时候就能对上是哪一家的哪种票 */
const ticketLabel = (item: any) => {
  const slots = String(item.timeSlots ?? '').split(',').filter(Boolean).map(Number)
  const slotText = slots.length ? slots.map((slot) => dict.label(DICT_SLOT, slot)).join('/') : ''
  return [item.parkName, item.ticketName, priceText(item.price), slotText].filter(Boolean).join(' · ')
}

/**
 * 票种候选：options 只给上架中的，所以改一张绑着下架票种的老票时会选不出来。
 * 这一行的票名/票价是出票时的快照，拿它顶着显示别让那一格空着（form 里的 ticketId 没动，保存仍是原样）
 */
const ticketChoices = computed(() => {
  const id = form.ticketId
  if (!id) return ticketOptions.value
  if (ticketOptions.value.some((item: any) => String(item.id) === String(id))) return ticketOptions.value
  return [{ id, ticketName: form.ticketName, price: form.price, parkName: form.parkName }, ...ticketOptions.value]
})

/**
 * 时段候选：只出所选票种卖的那几档（那一列在票种上是多选 "1,2" 这种，一张票约的是其中一个）。
 * 还没挑票种、或挑的是列表兜底那一项（读不到票种行）时，把四档都摆出来 —— 服务端那条判据会兜住越界的选择
 */
const slotChoices = computed(() => {
  const picked = ticketOptions.value.find((item: any) => String(item.id) === String(form.ticketId))
  const allowed = String(picked?.timeSlots ?? '').split(',').filter(Boolean).map(Number)
  const options = dict.options(DICT_SLOT)
  return allowed.length ? options.filter((item: any) => allowed.includes(Number(item.value))) : options
})

/** 选中票种：票价带出来给人看一眼，时段按票种那一格收窄（真正落库的票面四格由服务端按票种行覆盖，传了也不认） */
const handleTicketChange = () => {
  const picked = ticketOptions.value.find((item: any) => String(item.id) === String(form.ticketId))
  if (!picked) return
  form.price = picked.price
  const allowed = String(picked.timeSlots ?? '').split(',').filter(Boolean).map(Number)
  // 只卖一档就直接选好（全天票种占绝大多数，省一次点击），卖多档就清掉让人自己挑
  form.timeSlot = allowed.length === 1 ? allowed[0] : undefined
}

/** 选中持票用户：姓名/电话一般是本人，顺手带一次（口径同门店表单选店长） */
const handleHolderSelect = (user: any) => {
  if (!form.holderName) form.holderName = user.nickname || user.username
  if (!form.holderPhone) form.holderPhone = user.mobile
}

/** 金额的显示口径，和各列表页一致（后端返回的 decimal 是字符串） */
const priceText = (price: any) => (price == null || price === '' ? '-' : `￥${Number(price).toFixed(2)}`)

/** 时间列统一到分钟（秒太长不给看），空着就是没走过那一步 */
const shortTime = (value: any) => (value ? validate.formatTime(value, 'YYYY-MM-DD HH:mm') : '-')

/** 游玩日期已经过了：日期那格压个色，一眼看出这一张是该催退款还是该核销（不是判断，只是提示） */
const isPast = (visitDate: any) => !!visitDate && String(visitDate) < dayjs().format('YYYY-MM-DD')

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const params: any = { ...queryParams }
    if (visitRange.value && visitRange.value.length === 2) {
      params.beginVisitDate = visitRange.value[0]
      params.endVisitDate = visitRange.value[1]
    }
    const { data } = await listUserTicketApi(params)
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
    ticketCode: undefined,
    userId: undefined,
    userName: undefined,
    mobile: undefined,
    parkId: undefined,
    parkName: undefined,
    ticketId: undefined,
    ticketName: undefined,
    ticketType: undefined,
    price: undefined,
    visitDate: dayjs().format('YYYY-MM-DD'),
    timeSlot: Number(dict.defaultValue(DICT_SLOT, 0)),
    quantity: 1,
    holderName: undefined,
    holderPhone: undefined,
    holderIdCard: undefined,
    qrImg: undefined,
    status: undefined,
    remark: undefined
  })
  formRef.value?.resetFields()
  // 园区那一格只是筛票种的那一手，不在 form 里，reset 要一起清
  filterParkId.value = undefined
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
  queryParams.userId = undefined
  queryParams.parkId = undefined
  queryParams.ticketType = undefined
  queryParams.status = undefined
  visitRange.value = []
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
}

/** 代客出票 */
const handleAdd = () => {
  reset()
  open.value = true
  title.value = '代客出票'
  filterParkId.value = undefined
  getTicketOptions()
}

/** 修改：只放开游玩日期、时段、持票人那三格和备注（服务端那条判据在，别的状态点不开这颗按钮） */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailUserTicketApi(row.id)
    Object.assign(form, data)
    // 详情走的是 getById，没有联表那三格（持票人昵称/手机号/园区名），从列表这一行上补过来：
    // UserSelect 的回显和票种下拉那条「园区 · 票名 · 价格」的文字都要靠它们才顶得住（票种已下架时更明显）
    form.userName = row.userName
    form.mobile = row.mobile
    form.parkName = row.parkName
    // 园区那一格不参与提交（真正落库的 park_id 是快照），这里只把票种候选收窄到这一家，回显对得上
    filterParkId.value = data.parkId
    getTicketOptions(data.parkId)
    open.value = true
    title.value = '修改这张票'
  } catch (error) {
  }
}

/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateUserTicketApi(form)
        ElMessage.success('修改成功')
      } else {
        await addUserTicketApi(form)
        ElMessage.success('出票成功')
      }
      open.value = false
      getList()
    } catch (error) {
    } finally {
      submitLoading.value = false
    }
  })
}

/**
 * 重出 / 补出票面二维码：按这一行现存的票码重新生成一张 PNG、重新上传、覆盖 qr_img。
 * 出票那一步的出图是"失败只记日志、不回滚"（一张卖了钱的票不能因为文件存储抖一下就没了），
 * 所以 qr_img 有可能是空 —— 那一行的按钮标成「补出二维码」。票码不变，扫出来还是同一串。
 * 这一条不咽错误：成了提示、败了把服务端的原话弹出来（点一下总要给人一个结果）。
 */
const qrRefreshing = ref<any>(null)
const handleRefreshQr = async (row: any) => {
  qrRefreshing.value = row.id
  try {
    await refreshUserTicketQrApi(row.id)
    ElMessage.success(row.qrImg ? '二维码已重出' : '二维码已补上')
    getList()
  } catch (error) {
  } finally {
    qrRefreshing.value = null
  }
}

/** 退款：只改状态、不动钱（这一期没有支付链路，钱在线下退） */
const refundingId = ref<any>(null)
const handleRefund = (row: any) => {
  ElMessageBox.confirm(
    `确认给票码「${row.ticketCode}」退款吗？这一期只把状态改成已退款、记下退款时间，钱不在这儿退。`,
    '提示',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    refundingId.value = row.id
    try {
      await refundUserTicketApi(row.id)
      ElMessage.success('已退款')
      getList()
    } finally {
      refundingId.value = null
    }
  }).catch(() => { })
}

/**
 * 操作列按钮分发（TableMoreActions 只抛一个 command 事件，动作认 action.command.type，同优惠券、盲盒那两页）
 */
const handleActionCommand = (action: any) => {
  const { type, row } = action.command
  switch (type) {
    case 'refund':
      handleRefund(row)
      break
    case 'update':
      handleUpdate(row)
      break
    case 'qr':
      handleRefreshQr(row)
      break
    case 'delete':
      handleDelete(row)
      break
  }
}

/** 删除按钮操作 */
const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确认删除票码「${row.ticketCode}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteUserTicketApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 张票吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteUserTicketApi(selectedIds.value)
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
  getParkOptions()
  getTicketOptions()
})
</script>

<style scoped lang="scss">
/* 参照园区管理、我的订单：筛选项等分整行，控件宽度统一由 CSS 控制 */
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

/* 时间范围 + 按钮同一行：这一层 wrapper 独占一行，里面两格都不等分、按控件本身的宽度从左排 */
.search-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;

  :deep(.el-form-item) {
    flex: 0 0 auto;
    margin-right: 16px;
  }
}

/* 手机号、持票人电话那种补充信息压个浅色，别和主信息一样抢眼 */
.sub-text {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

/* 已经过了的游玩日期：不报错、不打折，只是把日期压个色给人一眼看出来 */
.is-past {
  color: var(--el-text-color-secondary);
}

/* 快照那条说明：字号和颜色都压到占位符一档，别抢表单焦点 */
.tip {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-placeholder);
}

/* 票面二维码那一列/那一格的缩略图；点开走 el-image 自带的查看器 */
.qr-thumb {
  width: 46px;
  height: 46px;
  border-radius: 4px;
  cursor: zoom-in;
}
</style>
