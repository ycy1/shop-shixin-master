<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="编号/申请人/电话" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="意向类型" prop="intentType">
          <el-select v-model="queryParams.intentType" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_INTENT)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="线索来源" prop="source">
          <el-select v-model="queryParams.source" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_SOURCE)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_STATUS)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>

        <!-- 时间范围 + 按钮同一行（照抄收货地址、购物车的 .search-row）：这一层 wrapper 独占第二行，
             里面两格都不等分、按控件本身的宽度从左排（样式见文件末尾） -->
        <div class="search-row">
          <el-form-item label="签约时间" prop="dateRange">
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
          <el-button v-permission="['shop:investment:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['shop:investment:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="招商编号" prop="investmentNo" align="center" min-width="150" />
        <el-table-column label="申请人" prop="applicantName" align="center" min-width="100" />
        <el-table-column label="状态" align="center" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_STATUS, row.status)">
              {{ dict.label(DICT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="意向类型" align="center" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_INTENT, row.intentType)">
              {{ dict.label(DICT_INTENT, row.intentType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="线索来源" align="center" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_SOURCE, row.source)">
              {{ dict.label(DICT_SOURCE, row.source) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="投资预算" align="center" min-width="110">
          <template #default="{ row }">{{ priceText(row.budgetAmount) || '-' }}</template>
        </el-table-column>
        
        <el-table-column label="负责专员" align="center" min-width="110">
          <template #default="{ row }">
            <span>{{ row.userName || (row.userId ? `已删除用户 #${row.userId}` : '未分配') }}</span>
          </template>
        </el-table-column>
       
        <el-table-column label="签约时间" align="center" width="120">
          <template #default="{ row }">{{ validate.formatTime(row.signTime, 'YYYY-MM-DD') || '-' }}</template>
        </el-table-column>
        
        <el-table-column label="操作" align="center" width="220" fixed="right">
          <template #default="{ row }">
            <!-- 四颗按钮由 TableMoreActions 收成"直出两颗 + 更多"（口径同入库单）。
                 组件收的是 actions 数组、用不上 v-permission 那个指令，权限在 hidden 里自己判；
                 删除只对 status≠0 的单子做不了，那是服务层的判据，这里不预告（不给置灰的按钮）；
                 已经建过门店的整条收掉「创建门店」（行上 storeId 由服务层按 shop_store.investment_no 反查回填，
                 门店表那格有值就算建过），和商品出库那页「建过就换成看结果那颗」同一口径 ——
                 这条不变量服务端也拦（门店服务层 fillInvestmentSource 按编号查重，一家门店一条线索）；
                 2 已取消 / 3 已失效 的线索也整条收掉（判据同门店那格「招商信息」下拉的候选，那边也只放行 0/1）-->
            <TableMoreActions :actions="[
              { label: '详情', icon: 'View', hidden: !hasPermission('shop:investment:detail'), command: { type: 'detail', row } },
              { label: '创建门店', type: 'success', icon: 'Plus', hidden: !hasPermission('shop:store:add') || !!row.storeId || (row.status !== 0 && row.status !== 1), command: { type: 'createStore', row } },
              { label: '修改', icon: 'Edit', hidden: !hasPermission('shop:investment:update'), command: { type: 'update', row } },
              { label: '删除', type: 'danger', icon: 'Delete', hidden: !hasPermission('shop:investment:delete'), command: { type: 'delete', row } }
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
            <el-form-item label="招商编号" prop="investmentNo">
              <el-input v-model="form.investmentNo" placeholder="保存后由系统生成" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责专员" prop="userId">
              <!-- 专员是本平台的招商人员，只列平台账号（account_type=0）；留空表示还没分配 -->
              <user-select v-model="form.userId" :label="form.userName" :account-type="0" only-enabled
                placeholder="选择系统用户" @select="handleUserSelect" @clear="handleUserClear" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="申请人" prop="applicantName">
              <el-input v-model="form.applicantName" placeholder="请输入申请人姓名" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="applicantPhone">
              <el-input v-model="form.applicantPhone" placeholder="请输入申请人电话" maxlength="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="意向类型" prop="intentType">
              <el-select v-model="form.intentType" placeholder="请选择" style="width: 100%">
                <el-option v-for="item in dict.options(DICT_INTENT)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="线索来源" prop="source">
              <el-select v-model="form.source" placeholder="请选择" style="width: 100%">
                <el-option v-for="item in dict.options(DICT_SOURCE)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="投资预算" prop="budgetAmount">
              <el-input-number v-model="form.budgetAmount" :min="0" :precision="2" placeholder="元"
                style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="行业经验" prop="hasExperience">
              <el-radio-group v-model="form.hasExperience">
                <el-radio :value="1">有</el-radio>
                <el-radio :value="0">无</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="签约时间" prop="signTime">
              <el-date-picker v-model="form.signTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="实际签约的时间" style="width: 100%" />
            </el-form-item>
          </el-col>
          <!-- 状态只有修改时才给选：新增一律落成 0 待审核（服务层 normalize 兜底），
               给一个选了的值也会被覆盖，不如不出现 -->
          <el-col v-if="form.id !== undefined" :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择" style="width: 100%">
                <el-option v-for="item in dict.options(DICT_STATUS)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="过期时间" prop="expiryTime">
              <el-date-picker v-model="form.expiryTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="线索有效期截止" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="签约文件" prop="contractFile">
              <div class="pair-field">
                <el-upload :show-file-list="false" :http-request="uploadContract"
                  accept=".pdf,.doc,.docx">
                  <el-button icon="Upload" :loading="uploading">上传</el-button>
                </el-upload>
                <el-link v-if="form.contractFile" type="primary" :href="form.contractFile" target="_blank"
                  class="contract-view">查看</el-link>
                <el-button v-if="form.contractFile" type="danger" link icon="Delete"
                  @click="form.contractFile = undefined">清除</el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="所在地区">
              <!-- 级联的 value 就是行政区划代码，选中结果在提交时 join 成 area_code -->
              <el-cascader v-model="areaArr" :options="colPickerData"
                :props="{ label: 'text', value: 'value', children: 'children' }" style="width: 100%" clearable
                placeholder="请选择所在地区" />
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
              <el-input v-model="form.address" placeholder="街道、门牌号" maxlength="255" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="沟通记录、跟进情况等" maxlength="500"
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

    <!--
      详情：只读看这一条线索的全部登记信息（口径同商品库存、商品入库那两页的详情弹窗），
      要改内容走「修改」，这里不给动。
    -->
    <el-dialog v-model="detailOpen" title="招商详情" width="900px" append-to-body destroy-on-close>
      <el-descriptions :column="2" border v-loading="detailLoading">
        <el-descriptions-item label="招商编号">{{ detail.investmentNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="负责专员">
          {{ detail.userName || (detail.userId ? `已删除用户 #${detail.userId}` : '未分配') }}
        </el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detail.applicantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detail.applicantPhone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="意向类型">
          <el-tag size="small" :type="dict.tag(DICT_INTENT, detail.intentType)">
            {{ dict.label(DICT_INTENT, detail.intentType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="线索来源">
          <el-tag size="small" :type="dict.tag(DICT_SOURCE, detail.source)">
            {{ dict.label(DICT_SOURCE, detail.source) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="投资预算">{{ priceText(detail.budgetAmount) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="行业经验">{{ detail.hasExperience === 1 ? '有' : '无' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="dict.tag(DICT_STATUS, detail.status)">
            {{ dict.label(DICT_STATUS, detail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="签约文件">
          <el-link v-if="detail.contractFile" type="primary" :href="detail.contractFile" target="_blank">
            查看文件
          </el-link>
          <span v-else>未上传</span>
        </el-descriptions-item>
        <el-descriptions-item label="意向地址" :span="2">{{ addressOf(detail) }}</el-descriptions-item>
        <el-descriptions-item label="经纬度" :span="2">
          {{ detail.longitude != null && detail.latitude != null
            ? `${detail.longitude}, ${detail.latitude}` : '未选点' }}
        </el-descriptions-item>
        <el-descriptions-item label="签约时间">{{ detail.signTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="过期时间">{{ detail.expiryTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="修改时间">{{ detail.updateTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
    <!--
      按招商信息创建门店：联系人/电话/地区/地址/坐标都从这条线索带过来（口径同出库单的「生成配送单」——
      先预填草稿、再补上招商记录里没有的那几项提交），门店编码由门店那边自己生成。
      提交走门店的新增接口，所以这一格只受 shop:store:add 控制，建完不改招商记录的状态。
    -->
    <el-dialog v-model="storeOpen" title="创建门店" width="720px" append-to-body destroy-on-close>
      <el-form ref="storeFormRef" :model="storeForm" :rules="storeRules" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="来源线索">
              <span class="source-tip">
                {{ storeSource.investmentNo }} · {{ storeSource.applicantName }}
                {{ storeSource.applicantPhone }}
              </span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="门店编码">
              <el-input placeholder="保存后由系统生成" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="门店名称" prop="storeName">
              <el-input v-model="storeForm.storeName" placeholder="请输入门店名称" maxlength="100" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="门店类型" prop="storeType">
              <el-select v-model="storeForm.storeType" placeholder="请选择" style="width: 100%">
                <el-option v-for="item in dict.options('store_type')" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="店长" prop="managerId">
              <!-- 店长是门店自己的登录账号（account_type=1），留空表示先不挂账号 -->
              <user-select v-model="storeForm.managerId" :label="storeForm.managerName" :account-type="1" only-enabled
                placeholder="选择系统用户" @select="handleManagerSelect" @clear="handleManagerClear" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人" prop="contactName">
              <el-input v-model="storeForm.contactName" placeholder="选填" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="storeForm.contactPhone" placeholder="选填" maxlength="20" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="所在地区">
              <el-cascader v-model="storeAreaArr" :options="colPickerData"
                :props="{ label: 'text', value: 'value', children: 'children' }" style="width: 100%" clearable
                placeholder="请选择所在地区" />
            </el-form-item>
          </el-col>
          <el-col :span="18">
            <el-form-item label="经纬度">
              <div class="pair-field">
                <el-input-number v-model="storeForm.longitude" :precision="6" :controls="false"
                  placeholder="经度 如 120.150000" style="width: 100%" />
                <span class="pair-split">-</span>
                <el-input-number v-model="storeForm.latitude" :precision="6" :controls="false"
                  placeholder="纬度 如 30.250000" style="width: 100%" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label-width="0">
              <shop-map :longitude="storeForm.longitude" :latitude="storeForm.latitude"
                @confirm="handleStoreLocationConfirm" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="详细地址">
              <el-input v-model="storeForm.address" placeholder="街道、门牌号" maxlength="255" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="storeForm.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="storeOpen = false">取 消</el-button>
          <el-button type="primary" :loading="storeSubmitLoading" @click="submitStoreForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listShopInvestmentApi,
  detailShopInvestmentApi,
  addShopInvestmentApi,
  updateShopInvestmentApi,
  deleteShopInvestmentApi
} from '@/api/store/investment'
import { addShopStoreApi } from '@/api/store/store'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import ShopMap from '@/components/ShopMap/index.vue'
import TableMoreActions from '@/components/TableMoreActions/index.vue'
import { uploadApi } from '@/api/file'
import validate from '@/utils/validate'
import { useDict } from '@/utils/dictCache'
import { useUserStore } from '@/store/modules/user'
import { useColPickerData } from '@/composables/useColPickerData'

const userStore = useUserStore()
/** 操作列走 TableMoreActions，权限得在这里自己判（组件没有 v-permission 那套指令，只认 actions 上的 hidden） */
const permissions = computed(() => userStore.user.permissions || [])
const hasPermission = (permission: string): boolean => {
  return permissions.value.includes(permission)
}

const DICT_INTENT = 'invest_intent_type'
const DICT_SOURCE = 'invest_source'
const DICT_STATUS = 'invest_status'

// store_type 是「创建门店」弹窗要用的门店自己的字典，不是本表的枚举列
const dict = useDict(DICT_INTENT, DICT_SOURCE, DICT_STATUS, 'store_type')

/** 地区数据源：@vant/area-data，级联的 value 就是行政区划代码 */
const { colPickerData, resolveAreaCode } = useColPickerData()
// 库里存的是 area_code（"110000,110100,110101"）+ 冗余的 area_zh，
// 级联选择器要的是数组，join / split 的互转在 reset、回显、提交三处做
const areaArr = ref<string[]>([])

const loading = ref(true)
const submitLoading = ref(false)
const uploading = ref(false)
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
  intentType: undefined,
  source: undefined,
  status: undefined
})

// 表单参数
const form = reactive<any>({})

const rules = reactive<FormRules>({
  applicantName: [{ required: true, message: '申请人姓名不能为空', trigger: 'blur' }],
  applicantPhone: [
    { required: true, message: '联系电话不能为空', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  intentType: [{ required: true, message: '请选择意向类型', trigger: 'change' }],
  source: [{ required: true, message: '请选择线索来源', trigger: 'change' }],
  // 两头时间是必填项，判据在服务层也钉了一道（列本身可空，所以后端不靠 DDL 的 NOT NULL）
  signTime: [{ required: true, message: '签约时间不能为空', trigger: 'change' }],
  expiryTime: [{ required: true, message: '过期时间不能为空', trigger: 'change' }]
})

const priceText = (price: any) => (price == null || price === '' ? '' : `￥${Number(price).toFixed(2)}`)

/** 地区优先用库里冗余的 area_zh，兜底才按 area_code 现场解析（老数据可能只有 code） */
const addressOf = (row: any) => {
  const area = row.areaZh || resolveAreaCode(row.areaCode)
  return [area, row.address].filter(Boolean).join(' ') || '-'
}

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const params: any = { ...queryParams }
    if (dateRange.value && dateRange.value.length === 2) {
      params.beginTime = `${dateRange.value[0]} 00:00:00`
      params.endTime = `${dateRange.value[1]} 23:59:59`
    }
    const { data } = await listShopInvestmentApi(params)
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
    investmentNo: undefined,
    userId: undefined,
    userName: undefined,
    applicantName: undefined,
    applicantPhone: undefined,
    intentType: dict.defaultValue(DICT_INTENT, 1),
    source: dict.defaultValue(DICT_SOURCE, 1),
    budgetAmount: undefined,
    hasExperience: 0,
    contractFile: undefined,
    status: 0,
    signTime: undefined,
    expiryTime: undefined,
    areaCode: undefined,
    areaZh: undefined,
    address: undefined,
    longitude: undefined,
    latitude: undefined,
    remark: undefined
  })
  formRef.value?.resetFields()
  // 省市区不在 form 里直接编辑，靠这个数组中转，所以 reset 要一起清
  areaArr.value = []
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
  queryParams.intentType = undefined
  queryParams.source = undefined
  queryParams.status = undefined
  dateRange.value = []
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
}

/** 选中专员：只回填昵称给选择器回显，申请人是外部线索，不跟着带 */
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

/** 签约文件上传：走公共文件上传接口，把返回的地址存进 contract_file 那一列 */
const uploadContract = async (options: any) => {
  const formData = new FormData()
  formData.append('file', options.file)
  uploading.value = true
  try {
    const res: any = await uploadApi(formData, 'investment')
    form.contractFile = res.data
    ElMessage.success('上传成功')
  } catch (error) {
  } finally {
    uploading.value = false
  }
}

/** 新增按钮操作 */
const handleAdd = () => {
  reset()
  open.value = true
  title.value = '添加招商线索'
}

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailShopInvestmentApi(row.id)
    Object.assign(form, data)
    // area_code 是 "110000,110100,110101" 这种，split 回级联选择器要的数组
    areaArr.value = (data.areaCode || '').split(',').filter(Boolean)
    open.value = true
    title.value = '修改招商线索'
  } catch (error) {
  }
}

/** 详情按钮操作 */
const handleDetail = async (row: any) => {
  detail.value = {}
  detailOpen.value = true
  detailLoading.value = true
  try {
    const { data } = await detailShopInvestmentApi(row.id)
    detail.value = data || {}
  } catch (error) {
  } finally {
    detailLoading.value = false
  }
}

/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    // 级联的 [省, 市, 区县] 存成一列 area_code，中文地名顺手解析出来存 area_zh（同门店管理）；
    // 清空时 area_code 是空串，这几列在实体上是 FieldStrategy.IGNORED，所以写得回库
    form.areaCode = (areaArr.value || []).join(',')
    form.areaZh = resolveAreaCode(form.areaCode)
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateShopInvestmentApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopInvestmentApi(form)
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
  ElMessageBox.confirm(`确认删除「${row.applicantName}」这条招商线索吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopInvestmentApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 条招商线索吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopInvestmentApi(selectedIds.value)
    ElMessage.success('删除成功')
    selectedIds.value = []
    getList()
  }).catch(() => { })
}

/**
 * 按招商信息创建门店：这一份草稿只带门店那边要、而招商记录里已有的几项，
 * 门店名称/店长得在这儿补（招商记录里没有）。提交走门店的新增接口。
 */
const storeOpen = ref(false)
const storeSubmitLoading = ref(false)
const storeFormRef = ref<FormInstance>()
// 弹窗自己的地区中转数组，不能复用新增/修改那个 areaArr（两个弹窗可能同时在）
const storeAreaArr = ref<string[]>([])
// 「来源线索」那一行只读展示用，避免把编号/申请人再塞进 storeForm 里污染提交体
const storeSource = ref<any>({})
const storeForm = reactive<any>({})

const storeRules = reactive<FormRules>({
  storeName: [{ required: true, message: '门店名称不能为空', trigger: 'blur' }]
})

const handleCreateStore = (row: any) => {
  Object.assign(storeForm, {
    // 来源线索只带 id：编号由门店服务层按 id 查库里那条覆盖，
    // 建完之后这一格在门店管理的「修改」弹窗里也改得动（换一条或清空都行）
    investmentId: row.id,
    storeName: undefined,
    // 门店类型默认取字典里 is_default=1 那条，和门店管理新增同一口径
    storeType: dict.defaultValue('store_type', 1),
    managerId: undefined,
    managerName: undefined,
    contactName: row.applicantName,
    contactPhone: row.applicantPhone,
    areaCode: row.areaCode,
    areaZh: row.areaZh,
    address: row.address,
    longitude: row.longitude,
    latitude: row.latitude,
    status: 1
  })
  storeSource.value = { investmentNo: row.investmentNo, applicantName: row.applicantName, applicantPhone: row.applicantPhone }
  // 地区同样要 split 回数组才能给级联显示（门店没建时这几列可能是空的）
  storeAreaArr.value = (row.areaCode || '').split(',').filter(Boolean)
  storeFormRef.value?.clearValidate()
  storeOpen.value = true
}

/** 选中店长：只回填昵称给选择器回显，联系人/电话是招商那边带来的，不跟着换人改 */
const handleManagerSelect = (user: any) => {
  storeForm.managerName = user.nickname || user.username
}

const handleManagerClear = () => {
  storeForm.managerName = undefined
}

/** 地图选点：和新增/修改那格一样，坐标收进列精度，挪了点位才改地址 */
const handleStoreLocationConfirm = (point: any) => {
  storeForm.longitude = Number(Number(point.longitude).toFixed(6))
  storeForm.latitude = Number(Number(point.latitude).toFixed(6))
  if (point.address) storeForm.address = point.address
}

const submitStoreForm = () => {
  storeFormRef.value?.validate(async (valid: any) => {
    if (!valid) return
    storeForm.areaCode = (storeAreaArr.value || []).join(',')
    storeForm.areaZh = resolveAreaCode(storeForm.areaCode)
    storeSubmitLoading.value = true
    try {
      await addShopStoreApi(storeForm)
      ElMessage.success('门店创建成功，可到「门店管理」补充图片/营业信息')
      storeOpen.value = false
      // 重查一次：这一行的 storeId 有了值，「创建门店」就该收掉，别让人接着再建一家
      getList()
    } catch (error) {
    } finally {
      storeSubmitLoading.value = false
    }
  })
}

/**
 * 操作列按钮分发（TableMoreActions 只抛一个 command 事件，动作认 action.command.type，同入库单那页）
 */
const handleActionCommand = (action: any) => {
  const { type, row } = action.command
  switch (type) {
    case 'detail':
      handleDetail(row)
      break
    case 'update':
      handleUpdate(row)
      break
    case 'createStore':
      handleCreateStore(row)
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

/* 时间范围那一行（照抄收货地址、购物车的 .search-row）：wrapper 独占整行把日期格顶到第二行，
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

/* 「创建门店」弹窗顶部那行来源提示：只是说明这单从哪条线索来，不抢下面输入框的视觉权重 */
.source-tip {
  color: var(--el-text-color-secondary);
}

/* 一段拆成两格的字段（经纬度、签约文件那一排）：里面各块按自身宽度从左排 */
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

  .contract-view {
    flex: 0 0 auto;
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
