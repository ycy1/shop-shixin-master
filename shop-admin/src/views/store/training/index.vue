<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="编号/名称/地点" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="培训类型" prop="trainingType">
          <el-select v-model="queryParams.trainingType" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="培训方式" prop="trainMode">
          <el-select v-model="queryParams.trainMode" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_MODE)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_STATUS)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>

        <!-- 时间范围 + 按钮同一行（照抄门店招商、收货地址的 .search-row）：这一层 wrapper 独占第二行，
             里面两格都不等分、按控件本身的宽度从左排（样式见文件末尾） -->
        <div class="search-row">
          <el-form-item label="培训时间" prop="dateRange">
            <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
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
          <el-button v-permission="['shop:training:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['shop:training:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="培训编号" prop="trainingNo" align="center" min-width="150" />
        <el-table-column label="培训名称" prop="trainingName" align="center" min-width="160" />
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_STATUS, row.status)">
              {{ dict.label(DICT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="培训类型" align="center" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_TYPE, row.trainingType)">
              {{ dict.label(DICT_TYPE, row.trainingType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="培训方式" align="center" width="90">
          <template #default="{ row }">{{ dict.label(DICT_MODE, row.trainMode) }}</template>
        </el-table-column>
        <el-table-column label="所属门店" align="center" min-width="120">
          <template #default="{ row }">
            <!-- 所属门店必填（2026-09-24 起「总部统一培训」那一档取消了），这一格不该再出现空的；
                 真为空只可能是历史行，显示成「未指定」提醒人去「修改」里补上 -->
            <span>{{ row.storeName || (row.storeId ? `已删除门店 #${row.storeId}` : '未指定') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="培训讲师" align="center" min-width="100">
          <template #default="{ row }">
            <span>{{ row.userName || (row.userId ? `已删除用户 #${row.userId}` : '未指定') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="培训时间" align="center" width="150">
          <template #default="{ row }">{{ rangeText(row.startTime, row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="报名" align="center" width="120">
          <template #default="{ row }">{{ row.signCount || 0 }}{{ row.maxCount ? ` / ${row.maxCount}` : ' 人' }}</template>
        </el-table-column>

        <el-table-column label="操作" align="center" width="220" fixed="right">
          <template #default="{ row }">
            <!-- 六颗按钮由 TableMoreActions 收成"直出两颗 + 更多"（口径同门店招商、入库单）。
                 组件收的是 actions 数组、用不上 v-permission 那个指令，权限在 hidden 里自己判；
                 "只有待发布能删"是服务层的判据，这里不预告（不给置灰的按钮）。
                 「报名」「签到」是这一行自己的两颗（2026-09-24 他从「培训人员」弹窗搬到这一列来的）：
                 走的是门店那一端那两条统一接口，没有后台权限码可挂，能不能出现只看行上的状态（见 canSign） -->
            <TableMoreActions :actions="[
              { label: '详情', icon: 'View', hidden: !hasPermission('shop:training:detail'), command: { type: 'detail', row } },
              { label: '报名', icon: 'CirclePlus', hidden: !canSign(row), loading: signLoadingId === row.id, command: { type: 'sign', row } },
              { label: '签到', icon: 'Finished', hidden: !canAttend(row), loading: attendLoadingId === row.id, command: { type: 'attend', row } },
              { label: '培训人员', type: 'success', icon: 'User', hidden: !hasPermission('shop:training:member'), command: { type: 'member', row } },
              { label: '修改', icon: 'Edit', hidden: !hasPermission('shop:training:update'), command: { type: 'update', row } },
              { label: '删除', type: 'danger', icon: 'Delete', hidden: !hasPermission('shop:training:delete'), command: { type: 'delete', row } }
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
    </el-card>

    <!-- 添加或修改对话框 -->
    <el-dialog v-model="open" :title="title" width="720px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="培训编号" prop="trainingNo">
              <el-input v-model="form.trainingNo" placeholder="保存后由系统生成" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="培训名称" prop="trainingName">
              <el-input v-model="form.trainingName" placeholder="请输入培训名称" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="培训类型" prop="trainingType">
              <el-select v-model="form.trainingType" placeholder="请选择" style="width: 100%">
                <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="培训方式" prop="trainMode">
              <el-select v-model="form.trainMode" placeholder="请选择" style="width: 100%">
                <el-option v-for="item in dict.options(DICT_MODE)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属门店" prop="storeId">
              <!-- 必填（2026-09-24 起「总部统一培训」那一档取消了）：不给清除按钮，
                   这一场归哪家店就只那家店的人能报，清空等于谁也都报不进 -->
              <el-select v-model="form.storeId" placeholder="请选择所属门店" filterable style="width: 100%">
                <el-option v-for="item in storeOptions" :key="item.id" :label="item.storeName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="培训讲师" prop="userId">
              <!-- 讲师是门店那边的登录账号（account_type=1），只列启用中的；留空表示还没定人 -->
              <user-select v-model="form.userId" :label="form.userName" :account-type="1" only-enabled
                placeholder="选择系统用户" @select="handleUserSelect" @clear="handleUserClear" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开始时间" prop="startTime">
              <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="培训开始时间" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间" prop="endTime">
              <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="培训结束时间" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="报名开始" prop="signStart">
              <el-date-picker v-model="form.signStart" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="报名开放时间" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="报名截止" prop="signEnd">
              <el-date-picker v-model="form.signEnd" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="报名截止时间" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大参训数" prop="maxCount">
              <el-input-number v-model="form.maxCount" :min="1" :controls="false" placeholder="不填即不限"
                style="width: 100%" />
            </el-form-item>
          </el-col>
          <!-- 状态只有修改时才给选：新增一律落成 0 待发布（服务层 insert 里钉死），
               给一个选了的值也会被覆盖，不如不出现 -->
          <el-col v-if="form.id !== undefined" :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择" style="width: 100%">
                <el-option v-for="item in dict.options(DICT_STATUS)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="线上链接" prop="trainUrl">
              <!-- 线上直播/录播那种才用得上，线下的留着不填也没关系，所以不做校验 -->
              <el-input v-model="form.trainUrl" placeholder="https://（培训方式为线上时填这一格）" maxlength="500" />
            </el-form-item>
          </el-col>
          <el-col :span="18">
            <el-form-item label="经纬度" prop="longitude">
              <div class="pair-field">
                <el-input-number v-model="form.longitude" :precision="6" :controls="false"
                  placeholder="经度 如 120.150000" style="width: 100%" />
                <span class="pair-split">-</span>
                <el-input-number v-model="form.latitude" :precision="6" :controls="false"
                  placeholder="纬度 如 30.250000" style="width: 100%" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <!-- 整表 label-width 是 110px，这一格只有 6 格宽，不取消 label 按钮就顶出去了 -->
            <el-form-item label-width="0">
              <shop-map :longitude="form.longitude" :latitude="form.latitude" @confirm="handleLocationConfirm" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="详细地址" prop="address">
              <el-input v-model="form.address" placeholder="街道、门牌号（线下培训上课地点）" maxlength="255" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="内容简介" prop="content">
              <el-input v-model="form.content" type="textarea" :rows="4" placeholder="培训内容简介、课程安排" maxlength="2000"
                show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="内部备注、通知情况等" maxlength="500"
                show-word-limit />
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

    <!-- 详情：只读看这一场培训的全部登记信息（口径同门店招商那页的详情弹窗），要改内容走「修改」 -->
    <el-dialog v-model="detailOpen" title="培训详情" width="900px" append-to-body destroy-on-close>
      <el-descriptions :column="2" border v-loading="detailLoading">
        <el-descriptions-item label="培训编号">{{ detail.trainingNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="培训名称">{{ detail.trainingName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="dict.tag(DICT_STATUS, detail.status)">
            {{ dict.label(DICT_STATUS, detail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="培训类型">
          <el-tag size="small" :type="dict.tag(DICT_TYPE, detail.trainingType)">
            {{ dict.label(DICT_TYPE, detail.trainingType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="培训方式">{{ dict.label(DICT_MODE, detail.trainMode) }}</el-descriptions-item>
        <el-descriptions-item label="所属门店">
          {{ detail.storeName || (detail.storeId ? `已删除门店 #${detail.storeId}` : '未指定') }}
        </el-descriptions-item>
        <el-descriptions-item label="培训讲师">
          {{ detail.userName || (detail.userId ? `已删除用户 #${detail.userId}` : '未指定') }}
        </el-descriptions-item>
        <el-descriptions-item label="报名人数">
          {{ detail.signCount || 0 }}{{ detail.maxCount ? ` / ${detail.maxCount}` : ' 人' }}
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ detail.startTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ detail.endTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="报名开始">{{ detail.signStart || '-' }}</el-descriptions-item>
        <el-descriptions-item label="报名截止">{{ detail.signEnd || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ detail.publishTime || '未发布' }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ detail.finishTime || '未结束' }}</el-descriptions-item>
        <el-descriptions-item label="培训地点" :span="2">{{ detail.address || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="经纬度" :span="2">
          {{ detail.longitude != null && detail.latitude != null
            ? `${detail.longitude}, ${detail.latitude}` : '未选点' }}
        </el-descriptions-item>
        <el-descriptions-item label="线上链接" :span="2">
          <el-link v-if="detail.trainUrl" type="primary" :href="detail.trainUrl" target="_blank">
            {{ detail.trainUrl }}
          </el-link>
          <span v-else>无</span>
        </el-descriptions-item>
        <el-descriptions-item label="内容简介" :span="2">{{ detail.content || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="修改时间">{{ detail.updateTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!--
      培训人员：这一场培训的报名记录（shop_store_training_member），后台这一侧只读。
      姓名/电话是报名那一刻的快照，所以这里显示的不跟着账号改名；写入走的是门店那一端那两条统一接口
      （报名 POST /api/comm/store/training/sign、签到 POST /api/comm/store/training/attend/{trainingId}，
      都在 shop-api），后台页面上那两颗按钮点的也是同一条 —— 它们在上面的操作列，不在这一屏
      （2026-09-24 他从弹窗里搬过去的）。
    -->
    <el-dialog v-model="memberOpen" :title="memberTitle" width="900px" append-to-body destroy-on-close>
      <el-table v-loading="memberLoading" :data="memberList" show-overflow-tooltip>
        <el-table-column type="index" label="#" width="55" align="center" />
        <el-table-column label="参训人" prop="userName" align="center" min-width="100" />
        <el-table-column label="联系电话" prop="userPhone" align="center" min-width="120">
          <template #default="{ row }">{{ row.userPhone || '-' }}</template>
        </el-table-column>
        <el-table-column label="所属门店" align="center" min-width="120">
          <template #default="{ row }">
            {{ row.storeName || (row.storeId ? `已删除门店 #${row.storeId}` : '-') }}
          </template>
        </el-table-column>
        <el-table-column label="报名状态" align="center" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_SIGN_STATUS, row.signStatus)">
              {{ dict.label(DICT_SIGN_STATUS, row.signStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报名时间" align="center" width="160">
          <template #default="{ row }">{{ row.signTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="出勤状态" align="center" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_ATTEND_STATUS, row.attendStatus)">
              {{ dict.label(DICT_ATTEND_STATUS, row.attendStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="签到时间" align="center" width="160">
          <template #default="{ row }">{{ row.attendTime || '-' }}</template>
        </el-table-column>
        <template #empty>
          <span class="member-empty">{{ memberTrainingName }} 还没有参训人员登记</span>
        </template>
      </el-table>
      <div class="member-tip">共 {{ memberList.length }} 人，已报名 {{ signedCount }} 人</div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="memberOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listShopTrainingApi,
  detailShopTrainingApi,
  trainingMembersApi,
  addShopTrainingApi,
  updateShopTrainingApi,
  deleteShopTrainingApi,
  signStoresShopTrainingApi,
  signShopTrainingApi,
  attendShopTrainingApi
} from '@/api/store/training'
import { optionsShopStoreApi } from '@/api/store/store'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import ShopMap from '@/components/ShopMap/index.vue'
import TableMoreActions from '@/components/TableMoreActions/index.vue'
import validate from '@/utils/validate'
import { useDict } from '@/utils/dictCache'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
/** 操作列走 TableMoreActions，权限得在这里自己判（组件没有 v-permission 那套指令，只认 actions 上的 hidden） */
const permissions = computed(() => userStore.user.permissions || [])
const hasPermission = (permission: string): boolean => {
  return permissions.value.includes(permission)
}

const DICT_TYPE = 'training_type'
const DICT_MODE = 'train_mode'
const DICT_STATUS = 'training_status'
// 这两套是「培训人员」弹窗那两列要用的，本表的行上没有
const DICT_SIGN_STATUS = 'training_sign_status'
const DICT_ATTEND_STATUS = 'training_attend_status'

const dict = useDict(DICT_TYPE, DICT_MODE, DICT_STATUS, DICT_SIGN_STATUS, DICT_ATTEND_STATUS)

const loading = ref(true)
const submitLoading = ref(false)
const selectedIds = ref<any[]>([])
const total = ref(0)
const dataList = ref<any[]>([])
const title = ref('')
const open = ref(false)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<any>({})
const dateRange = ref<string[]>([])

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// 查询参数
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  trainingType: undefined,
  trainMode: undefined,
  status: undefined
})

// 表单参数
const form = reactive<any>({})

const rules = reactive<FormRules>({
  trainingName: [{ required: true, message: '培训名称不能为空', trigger: 'blur' }],
  trainingType: [{ required: true, message: '请选择培训类型', trigger: 'change' }],
  trainMode: [{ required: true, message: '请选择培训方式', trigger: 'change' }],
  // 所属门店必填（服务层同一道闸）：这一格空的场次谁也报不了名
  storeId: [{ required: true, message: '所属门店不能为空', trigger: 'change' }],
  // 两组时间的先后判据在服务层也钉了一道；这里只挡必填的那格
  startTime: [{ required: true, message: '开始时间不能为空', trigger: 'change' }],
  endTime: [{ required: true, message: '结束时间不能为空', trigger: 'change' }]
})

/** 门店候选：表单那一格「所属门店」用（options 返回启用的门店，口径同采购单的所属门店） */
const storeOptions = ref<any[]>([])

const getStoreOptions = async () => {
  try {
    const { data } = await optionsShopStoreApi()
    storeOptions.value = data || []
  } catch (error) {
  }
}

/** 培训时间那一格：开始给全、结束只给到日，一格放不下两个完整时间戳 */
const rangeText = (start: any, end: any) => {
  const s = validate.formatTime(start, 'YYYY-MM-DD HH:mm')
  const e = validate.formatTime(end, 'MM-DD HH:mm')
  return [s, e].filter(Boolean).join(' ~ ') || '-'
}

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const params: any = { ...queryParams }
    if (dateRange.value && dateRange.value.length === 2) {
      // 筛的是 start_time（培训开始时间）；这两个查询字段不叫 beginTime/endTime，
      // 是因为后端实体上 endTime 已经是"培训结束时间"那一列，撞了名
      params.startFrom = `${dateRange.value[0]} 00:00:00`
      params.startTo = `${dateRange.value[1]} 23:59:59`
    }
    const { data } = await listShopTrainingApi(params)
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
    trainingNo: undefined,
    trainingName: undefined,
    content: undefined,
    trainingType: dict.defaultValue(DICT_TYPE, 1),
    trainMode: dict.defaultValue(DICT_MODE, 1),
    storeId: undefined,
    userId: undefined,
    userName: undefined,
    longitude: undefined,
    latitude: undefined,
    address: undefined,
    trainUrl: undefined,
    startTime: undefined,
    endTime: undefined,
    signStart: undefined,
    signEnd: undefined,
    maxCount: undefined,
    signCount: undefined,
    status: 0,
    publishTime: undefined,
    finishTime: undefined,
    remark: undefined
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
  queryParams.trainingType = undefined
  queryParams.trainMode = undefined
  queryParams.status = undefined
  dateRange.value = []
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
}

/** 选中讲师：只回填昵称给选择器回显 */
const handleUserSelect = (user: any) => {
  form.userName = user.nickname || user.username
}

const handleUserClear = () => {
  form.userName = undefined
}

/** 地图选点确认：坐标收进列精度；换了点位老地址就对不上，解析出地址就一起改掉 */
const handleLocationConfirm = (point: any) => {
  form.longitude = Number(Number(point.longitude).toFixed(6))
  form.latitude = Number(Number(point.latitude).toFixed(6))
  if (point.address) form.address = point.address
}

/** 新增按钮操作 */
const handleAdd = () => {
  reset()
  getStoreOptions()
  open.value = true
  title.value = '添加培训'
}

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailShopTrainingApi(row.id)
    // 候选要先把这一行已选的那家门店显示出来，所以打开弹窗前先捞一次
    getStoreOptions()
    Object.assign(form, data)
    open.value = true
    title.value = '修改培训'
  } catch (error) {
  }
}

/** 详情按钮操作 */
const handleDetail = async (row: any) => {
  detail.value = {}
  detailOpen.value = true
  detailLoading.value = true
  try {
    const { data } = await detailShopTrainingApi(row.id)
    detail.value = data || {}
  } catch (error) {
  } finally {
    detailLoading.value = false
  }
}

/**
 * 培训人员：一次取全、不翻页（一场培训的报名数就在最大参训数那一档里，量级很小），
 * 弹窗标题带上培训名，免得开错了不知道看的是哪一期
 */
const memberOpen = ref(false)
const memberLoading = ref(false)
const memberList = ref<any[]>([])
/** 这一屏看的是哪一场：弹窗标题和空列表那句提示都读它的名称 */
const memberTraining = ref<any>({})
const memberTrainingName = computed(() => memberTraining.value.trainingName || '')
const memberTitle = computed(() => `培训人员${memberTrainingName.value ? ' - ' + memberTrainingName.value : ''}`)
const signedCount = computed(() => memberList.value.filter((item: any) => item.signStatus === 1).length)

const fetchMembers = async () => {
  memberLoading.value = true
  try {
    const { data } = await trainingMembersApi(memberTraining.value.id)
    memberList.value = data || []
  } catch (error) {
  } finally {
    memberLoading.value = false
  }
}

const handleMember = async (row: any) => {
  memberList.value = []
  memberTraining.value = row || {}
  memberOpen.value = true
  await fetchMembers()
}

/* ---------------------------- 报名 / 签到（操作列那两颗） ---------------------------- */

/**
 * 名下可用于报名的门店：整个列表共用一次请求（进页面时取一趟就够，一家店不会在翻页中途换主人）。
 * 口径和后端 resolveStore 那一道完全一样（绑定账号 OR 店长），空列表就是这一格和当前账号无关
 * —— 平台/ERP/消费者那些档一家店都不挂，那几行的「报名」按钮全都不出现。
 */
const signStoreList = ref<any[]>([])
/** 处理中的那一行：只有点了那颗按钮的行转圈，别的行照旧能点 */
const signLoadingId = ref<any>(undefined)
const attendLoadingId = ref<any>(undefined)

const loadSignStores = async () => {
  try {
    const { data } = await signStoresShopTrainingApi()
    signStoreList.value = data || []
  } catch (error) {
  }
}

const isMyStore = (storeId: any) => storeId != null
  && signStoreList.value.some((item: any) => Number(item.id) === Number(storeId))

/**
 * 「报名」的显示判据：这一场还在报名中、它所属的那家门店在自己名下（2026-09-24 起所属门店必填，
 * 一场培训就是那一家店的专场，门店不由人挑）、而且自己还没报上（报过又取消了的那行算没报，还能再报）。
 * 报名窗口和名额不在这里判 —— 一个是日历、一个是别人此刻的点击，前端判不出可靠结果，交给后端报错。
 */
const canSign = (row: any) => row.status === 1 && isMyStore(row.storeId) && row.selfSignStatus !== 1

/**
 * 「签到」只出现在自己已经报上、还没签的那几行上。
 * 已取消那几场后端也拒（attend 那一道闸），这里一并收掉按钮，免得点了才知道。
 */
const canAttend = (row: any) => row.status !== 5
  && row.selfSignStatus === 1 && row.selfAttendStatus !== 1

const handleSign = async (row: any) => {
  signLoadingId.value = row.id
  try {
    const { data } = await signShopTrainingApi({ trainingId: row.id })
    const room = data.maxCount == null ? '' : ` / ${data.maxCount}`
    ElMessage.success(`报名成功（${data.storeName}）：已报名 ${data.signedCount}${room} 人`)
    // 整页刷一次：这一行的「报名」那颗要收掉、自己那格状态要出现，「报名」那一列人数也是后端现数的
    getList()
  } catch (error) {
  } finally {
    signLoadingId.value = undefined
  }
}

const handleAttend = async (row: any) => {
  attendLoadingId.value = row.id
  try {
    const { data } = await attendShopTrainingApi(row.id)
    ElMessage.success(`签到成功：${data.attendTime || ''}`)
    getList()
  } catch (error) {
  } finally {
    attendLoadingId.value = undefined
  }
}

/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateShopTrainingApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopTrainingApi(form)
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
  ElMessageBox.confirm(`确认删除「${row.trainingName}」这场培训吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopTrainingApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 场培训吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopTrainingApi(selectedIds.value)
    ElMessage.success('删除成功')
    selectedIds.value = []
    getList()
  }).catch(() => { })
}

/**
 * 操作列按钮分发（TableMoreActions 只抛一个 command 事件，动作认 action.command.type，同门店招商那页）
 */
const handleActionCommand = (action: any) => {
  const { type, row } = action.command
  switch (type) {
    case 'detail':
      handleDetail(row)
      break
    case 'sign':
      handleSign(row)
      break
    case 'attend':
      handleAttend(row)
      break
    case 'member':
      handleMember(row)
      break
    case 'update':
      handleUpdate(row)
      break
    case 'delete':
      handleDelete(row)
      break
  }
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
  // 名下那几家门店只问一次：操作列那几行「报名」要不要出现全指着它比对，列表本身不依赖它
  loadSignStores()
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

/* 时间范围那一行（照抄门店招商、收货地址的 .search-row）：wrapper 独占整行把日期格顶到第二行，
   里面的格子不等分、按控件自身宽度从左排；上面 :deep(.el-form-item) 把格子压成 flex: 1 1 0，
   所以这里要多带一层 .search-row 才顶得住 */
.search-form :deep(.search-row) {
  flex: 0 0 100%;
  display: flex;
  flex-wrap: nowrap;
  align-items: center;

  .el-form-item {
    flex: 0 0 auto;
    min-width: 0;
  }

  /* 日期框不跟着整行撑满：EP 给 .el-date-editor 定的宽度比行内 style 还硬，
     所以 width 之外再压一道 max-width + flex-grow:0。360px 够放"开始日期 至 结束日期"，要调宽窄改同一条规则里的两个 360 */
  .el-date-editor {
    width: 360px !important;
    max-width: 360px !important;
    flex-grow: 0 !important;
  }

  /* 按钮紧贴日期框；想把它顶到行尾，给这格加 margin-left: auto */
  .el-form-item.search-actions {
    margin-right: 0;
  }
}

/* 一段拆成两格的字段（经纬度那一排）：里面各块按自身宽度从左排 */
.pair-field {
  display: flex;
  align-items: center;
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

/* 培训人员弹窗：合计那一行贴着表格，颜色压一档，别和表格里的数字抢视觉 */
.member-tip {
  margin-top: 10px;
  text-align: right;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.member-empty {
  color: var(--el-text-color-secondary);
}
</style>
