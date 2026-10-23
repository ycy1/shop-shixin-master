<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="请输入名称/编码/联系人/电话" clearable
            @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="类型" prop="warehouseType">
          <el-select v-model="queryParams.warehouseType" placeholder="全部" clearable>
            <el-option v-for="item in dict.options('warehouse_type')" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联门店" prop="storeId">
          <el-select v-model="queryParams.storeId" placeholder="全部" clearable filterable>
            <el-option v-for="item in storeOptions" :key="item.id" :label="item.storeName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options('warehouse_status')" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
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
          <el-button v-permission="['erp:warehouse:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['erp:warehouse:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <!-- 有库存的仓不给勾（服务端同样拦），批量删除的范围就绕开了删不掉的那几行 -->
        <el-table-column type="selection" width="55" align="center" :selectable="canSelect" />
        <el-table-column label="仓库编码" prop="warehouseCode" align="center" min-width="190" />
        <el-table-column label="仓库名称" prop="warehouseName" align="center" min-width="150" />
        <el-table-column label="类型" align="center" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag('warehouse_type', row.warehouseType)">
              {{ dict.label('warehouse_type', row.warehouseType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联门店" align="center" min-width="130">
          <template #default="{ row }">
            <span>{{ row.storeName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="负责操作员" align="center" min-width="110">
          <template #default="{ row }">
            <span>{{ row.userName || '-' }}</span>
          </template>
        </el-table-column>
       
        <el-table-column label="已用/容量(件)" align="center" min-width="120">
          <template #default="{ row }">
            <span>{{ row.capacityUsed || 0 }} / {{ row.capacity == null ? '不限' : row.capacity }}</span>
          </template>
        </el-table-column>
        
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag('warehouse_status', row.status)">
              {{ dict.label('warehouse_status', row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" align="center" width="170">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.createTime, 'YYYY-MM-DD') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="260" fixed="right">
          <template #default="scope">
            <el-button v-permission="['erp:warehouse:storage']" type="primary" link icon="Box"
              @click="handleStorage(scope.row)">存储详情</el-button>
            <el-button v-permission="['erp:warehouse:update']" type="primary" link icon="Edit"
              @click="handleUpdate(scope.row)">修改</el-button>
            <!-- 仓里还有货就删不掉（服务端同样拦）：按钮置灰，悬浮说清原因。
                 禁用元素不触发鼠标事件，所以 tooltip 的参考对象外面套一层 span -->
            <el-tooltip :disabled="!hasStock(scope.row)" :content="stockTip(scope.row)" placement="top">
              <span class="action-tip">
                <el-button v-permission="['erp:warehouse:delete']" type="danger" link icon="Delete"
                  :disabled="hasStock(scope.row)" @click="handleDelete(scope.row)">删除</el-button>
              </span>
            </el-tooltip>
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
      <el-dialog v-model="open" :title="title" width="720px" append-to-body destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
          <el-divider content-position="left">基本信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="仓库编码" prop="warehouseCode">
                <el-input v-model="form.warehouseCode" placeholder="保存后由系统生成" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="仓库名称" prop="warehouseName">
                <el-input v-model="form.warehouseName" placeholder="请输入仓库名称" maxlength="100" show-word-limit />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="仓库类型" prop="warehouseType">
                <el-select v-model="form.warehouseType" placeholder="请选择类型" style="width: 100%">
                  <el-option v-for="item in dict.options('warehouse_type')" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="关联门店" prop="storeId">
                <!-- 只有门店仓才用得上，主仓/分仓留空是正常状态；已挂靠但门店被停用的，靠列表那列 storeName 显示名字 -->
                <el-select v-model="form.storeId" placeholder="门店仓时选择" clearable filterable style="width: 100%">
                  <el-option v-for="item in storeOptions" :key="item.id" :label="item.storeName" :value="item.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="ERP操作员" prop="userId">
                <!-- 只列 ERP 操作员账号（account_type=5），停用的不列；不选就是还没指派。
                     ERP 操作员账号自己新增时这一格填成本人并禁用（列表按他收窄，指给别人他自己就找不到这个仓了） -->
                <user-select v-model="form.userId" :label="form.userName" only-enabled :account-type="5"
                  :disabled="isErpAccount" style="width: 100%" @select="handleOperatorSelect"
                  @clear="handleOperatorClear" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="联系人" prop="contactName">
                <el-input v-model="form.contactName" placeholder="选填，选操作员后自动带出，可改" maxlength="50" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="联系电话" prop="contactPhone">
                <el-input v-model="form.contactPhone" placeholder="选填" maxlength="20" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="所在地区" prop="areaCode">
                <!-- 级联的 value 就是行政区划代码，选中结果在提交时 join 成 area_code；必填所以不给清除按钮 -->
                <el-cascader v-model="areaArr" :options="colPickerData"
                  :props="{ label: 'text', value: 'value', children: 'children' }" style="width: 100%"
                  placeholder="请选择所在地区" />
              </el-form-item>
            </el-col>
            <el-col :span="18">
              <el-form-item label="经纬度" prop="longitude">
                <div class="pair-field">
                  <el-input-number v-model="form.longitude" :precision="6" :controls="false"
                    placeholder="经度 如 120.150000" style="width: 100%" />
                  <span class="pair-split">-</span>
                  <el-input-number v-model="form.latitude" :precision="6" :controls="false" placeholder="纬度 如 30.250000"
                    style="width: 100%" />
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
                <el-input v-model="form.address" placeholder="选填，街道、门牌号" maxlength="255" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-divider content-position="left">容量与预警</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="仓库容量" prop="capacity">
                <el-input-number v-model="form.capacity" :min="0" :step="100" controls-position="right"
                  :placeholder="capacityPlaceholder" style="width: 100%" @change="handleCapacityChange" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="容量预警值" prop="capacityWarn">
                <el-input-number v-model="form.capacityWarn" :min="0" :step="100" controls-position="right"
                  :placeholder="capacityWarnPlaceholder" style="width: 100%" @change="handleCapacityWarnChange" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="预警开关" prop="warnEnable">
                <el-switch v-model="form.warnEnable" :active-value="1" :inactive-value="0" />
              </el-form-item>
            </el-col>
            <el-col v-if="form.id" :span="12">
              <el-form-item label="已用容量">
                <!-- 服务端维护、表单只读：全站还没有出入库链路，这一列一直是 0，等库存单据来接（见服务层注释） -->
                <el-input :model-value="form.capacityUsed || 0" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态" prop="status">
                <el-radio-group v-model="form.status">
                  <el-radio v-for="item in dict.options('warehouse_status')" :key="item.value" :value="item.value">
                    {{ item.label }}
                  </el-radio>
                </el-radio-group>
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

      <!-- 存储详情：这个仓现在存了什么货，直接查库存表（shop_erp_inventory 按 warehouse_id 筛）。
           只读 —— 数量只能由入库/出库动作改，要调成本价和预警参数去「商品库存」页 -->
      <el-dialog v-model="storageOpen" :title="storageTitle" width="1000px" append-to-body destroy-on-close>
        <el-table :data="storageList" size="small" border v-loading="storageLoading" show-overflow-tooltip
          empty-text="这个仓还没有建库存账（一次入库都没发生过）">
          <el-table-column label="商品名称" align="center" min-width="160">
            <template #default="{ row }">
              <span>{{ row.productName || `已删除规格 #${row.skuId}` }}</span>
            </template>
          </el-table-column>
          <el-table-column label="规格" align="center" min-width="140">
            <template #default="{ row }">
              <span>{{ row.specText || '默认规格' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="商品编码" align="center" min-width="140">
            <template #default="{ row }">
              <span>{{ row.productCode || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="总库存" prop="stock" align="center" width="90" />
          <el-table-column label="锁定" prop="lockedStock" align="center" width="80" />
          <el-table-column label="可用" align="center" width="90">
            <template #default="{ row }">
              <span :class="{ 'stock-warning': isLowStock(row) }">{{ row.availableStock }}</span>
            </template>
          </el-table-column>
          <el-table-column label="成本价" align="center" width="110">
            <template #default="{ row }">
              <span>{{ priceText(row.costPrice) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最后操作人" align="center" min-width="110">
            <template #default="{ row }">
              <span>{{ row.userName || '未记录' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" align="center" width="170">
            <template #default="{ row }">
              <span>{{ validate.formatTime(row.updateTime, 'YYYY-MM-DD') || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-container">
          <el-pagination background size="small" v-model:current-page="storageQuery.pageNum"
            v-model:page-size="storageQuery.pageSize" :page-sizes="[10, 20, 50]" :total="storageTotal"
            layout="total, sizes, prev, pager, next, jumper" @size-change="handleStorageSizeChange"
            @current-change="getStorageList" />
        </div>

        <template #footer>
          <el-button @click="storageOpen = false">关 闭</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listErpWarehouseApi,
  detailErpWarehouseApi,
  addErpWarehouseApi,
  updateErpWarehouseApi,
  deleteErpWarehouseApi
} from '@/api/erp/warehouse'
import { listErpInventoryApi } from '@/api/erp/inventory'
import { optionsShopStoreApi } from '@/api/store/store'
import { getUserDetailApi } from '@/api/system/user'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import ShopMap from '@/components/ShopMap/index.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import validate from '@/utils/validate'
import { useDict } from '@/utils/dictCache'
import { useColPickerData } from '@/composables/useColPickerData'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
/** 账号类型：0平台 1门店 2送货员 3消费者 4供应商 5ERP操作员；ERP 账号只看/只改指派给自己的仓库 */
const isErpAccount = computed(() => userStore.user.accountType === 5)

/**
 * 仓库类型 + 状态字典：warehouse_type / warehouse_status
 */
const dict = useDict('warehouse_type', 'warehouse_status')

/** 门店下拉：只列启用中的门店，停用的靠列表那列 storeName 显示，不让新仓库再挂 */
const storeOptions = ref<any[]>([])

/** 地区数据源：@vant/area-data，级联的 value 就是行政区划代码 */
const { colPickerData, resolveAreaCode } = useColPickerData()
// 库里存的是 area_code（"110000,110100,110101"）+ 冗余的 area_zh，
// 级联选择器要的是数组，join / split 的互转在 reset、回显、提交三处做
const areaArr = ref<string[]>([])

const loading = ref(true)
const submitLoading = ref(false)
const selectedIds = ref<any[]>([])
const total = ref(0)
const dataList = ref<any[]>([])
const title = ref('')
const open = ref(false)

// 存储详情弹窗：这个仓的库存账（只读），warehouseId 是被点的那一行
const storageOpen = ref(false)
const storageLoading = ref(false)
const storageList = ref<any[]>([])
const storageTotal = ref(0)
const storageTitle = ref('')
const storageQuery = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  warehouseId: undefined
})

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// 查询参数
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  warehouseType: undefined,
  storeId: undefined,
  status: undefined
})

// 表单参数
const form = reactive<any>({})

const rules = reactive<FormRules>({
  warehouseName: [{ required: true, message: '仓库名称不能为空', trigger: 'blur' }],
  warehouseType: [{ required: true, message: '请选择仓库类型', trigger: 'change' }],
  // 所在地区必填：收货地址没录坐标时，统一下单就拿这一列（省/市/区任一段）匹仓（selectWarehouseIdByArea）
  areaCode: [{ required: true, validator: validateArea, trigger: 'change' }],
  // 经纬度必填：统一下单按它就近分仓，没坐标的仓进不了距离那一档候选池。
  // 那一格下面挂着经度和纬度两个输入框，所以校验器一次看两个（prop 只挂了 longitude）
  longitude: [{ required: true, validator: validateLocation, trigger: 'blur' }],
  // 容量和预警值填了才比（留空 = 不限 / 不设预警线），下限是仓里现有的货；服务端 validate 同样拦一道
  capacity: [{ validator: validateCapacity, trigger: 'blur' }],
  capacityWarn: [{ validator: validateCapacityWarn, trigger: 'blur' }]
})

/** 当前库存（= 列表那列「已用」，编辑弹窗里也回显）：容量和预警值都不许录到比它小 */
const currentStock = computed(() => Number(form.capacityUsed || 0))

/**
 * 「按容量回填预警值」的两道开关：
 * loadedCapacity 记下打开弹窗时容量那一格的值 —— 回显也算一次 change，不能拿它去覆盖库里存的预警线；
 * capacityWarnEdited 表示这一格已经由人定过了（用户动过，或库里本来就存着一条线），定过就不再自动回填
 */
const loadedCapacity = ref<any>(undefined)
const capacityWarnEdited = ref(false)

/** 容量的 10% 当预警线：还剩这么多件就该补货/腾仓位，四舍五入、至少 1 件 */
const warnOfCapacity = (capacity: any) => Math.max(1, Math.round(Number(capacity) * 0.1))

/** 用户改了容量才回填（el-input-number 的 change 只在用户操作时发，程序性赋值走不到这里） */
const handleCapacityChange = () => {
  if (Number(form.capacity) === Number(loadedCapacity.value)) {
    return
  }
  loadedCapacity.value = form.capacity
  if (capacityWarnEdited.value || form.capacity === null || form.capacity === undefined) {
    return
  }
  form.capacityWarn = warnOfCapacity(form.capacity)
  // 回填是程序改的值，不会触发那一格规则的 blur，红字要自己清
  formRef.value?.clearValidate('capacityWarn')
}

/** 预警值这一格由用户自己填过就不再回填；把它清空等于"我要不设预警线"，回填的开关重新打开 */
const handleCapacityWarnChange = (value: any) => {
  capacityWarnEdited.value = value !== null && value !== undefined
}

/** 容量/预警值那一格的占位符：改已有仓库时把下限写在脸上，省得填小了才知道 */
const capacityPlaceholder = computed(() => (form.id
  ? `留空表示不限，不能小于当前库存 ${currentStock.value} 件`
  : '留空表示不限'))
const capacityWarnPlaceholder = computed(() => (form.id
  ? '留空表示不设预警线，默认取容量的 10%，且不能大于容量'
  : '留空表示不设预警线，填了容量会自动按 10% 回填'))

/** 库存下限：容量和预警值共用这一条 —— 低于仓里已有的货就拦下来（新增的仓还没货，那一格是 0 恒过） */
function stockFloorError(value: any) {
  if (value !== null && value !== undefined && Number(value) < currentStock.value) {
    return `不能小于当前库存 ${currentStock.value} 件`
  }
  return ''
}

function validateCapacity(_rule: any, value: any, callback: any) {
  const error = stockFloorError(value)
  callback(error ? new Error(error) : undefined)
}

/** 预警值多一道：它是「还剩多少空间就该警惕」的那条线，录到比容量还高等于刚建好就天天预警 */
function validateCapacityWarn(_rule: any, value: any, callback: any) {
  const error = stockFloorError(value)
  if (error) {
    callback(new Error(error))
    return
  }
  const capacity = form.capacity
  if (value !== null && value !== undefined && capacity !== null && capacity !== undefined
    && Number(value) > Number(capacity)) {
    callback(new Error(`不能大于仓库容量 ${Number(capacity)} 件`))
    return
  }
  callback()
}

/** 经纬度校验：只填一半等于没填 */
function validateLocation(_rule: any, _value: any, callback: any) {
  if (form.longitude === null || form.longitude === undefined
    || form.latitude === null || form.latitude === undefined) {
    callback(new Error('请填写完整的经纬度，或在地图上选点'))
    return
  }
  callback()
}

/** 所在地区校验：级联绑的是 areaArr（提交才 join 成 form.areaCode），所以看那一串选没选到 */
function validateArea(_rule: any, _value: any, callback: any) {
  if (!areaArr.value || areaArr.value.length === 0) {
    callback(new Error('请选择所在地区'))
    return
  }
  callback()
}

/** 列表那一格：地区优先用库里冗余的 area_zh，兜底才按 area_code 现场解析（老数据可能只有 code） */
const areaOf = (row: any) => {
  return row.areaZh || resolveAreaCode(row.areaCode) || '-'
}

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listErpWarehouseApi(queryParams)
    dataList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
  } finally {
    loading.value = false
  }
}

/** 门店下拉选项 */
const getStoreOptions = async () => {
  try {
    const { data } = await optionsShopStoreApi()
    storeOptions.value = data || []
  } catch (error) {
  }
}

const priceText = (price: any) => (price == null || price === '' ? '-' : `￥${Number(price).toFixed(2)}`)

/** 低于安全库存：口径同「商品库存」页，开关关了或安全库存是 0 都不算 */
const isLowStock = (row: any) => {
  return Number(row.stockWarnEnable) === 1 && Number(row.safetyStock) > 0 && Number(row.stock) < Number(row.safetyStock)
}

/**
 * 仓里还有没有货：看的是「已用/容量」那一格的 capacityUsed（入库/出库末尾按库存表刷出来的）。
 * 有货的仓删不掉 —— 复选框不给勾（挡批量删除）、「删除」按钮置灰带悬浮说明，
 * 服务端 deleteByIds 是现查库存表拦的，这一格只是显示口径，两边偶尔对不上时以接口报错为准
 */
const hasStock = (row: any) => Number(row.capacityUsed || 0) > 0

const stockTip = (row: any) =>
  `这个仓还有库存 ${Number(row.capacityUsed || 0)} 件，不能删除；请先出库或把货调走`

/** 复选框可选条件：和有库存互为反面，删不掉的行不进「批量删除」的范围 */
const canSelect = (row: any) => !hasStock(row)

/** 存储详情列表：按 warehouseId 查库存表这一张账，服务端已经支持这个筛选条件 */
const getStorageList = async () => {
  storageLoading.value = true
  try {
    const { data } = await listErpInventoryApi(storageQuery)
    storageList.value = data.records || []
    storageTotal.value = data.total || 0
  } catch (error) {
  } finally {
    storageLoading.value = false
  }
}

/** 存储详情按钮操作：从第一页重新看这个仓，翻过的页码别留残留 */
const handleStorage = (row: any) => {
  storageTitle.value = `存储详情 - ${row.warehouseName}`
  storageQuery.pageNum = 1
  storageQuery.warehouseId = row.id
  storageList.value = []
  storageTotal.value = 0
  storageOpen.value = true
  getStorageList()
}

const handleStorageSizeChange = () => {
  storageQuery.pageNum = 1
  getStorageList()
}

/** 表单重置 */
const reset = () => {
  Object.assign(form, {
    id: undefined,
    warehouseCode: undefined,
    warehouseName: undefined,
    warehouseType: 1,
    storeId: undefined,
    userId: undefined,
    userName: undefined,
    contactName: undefined,
    contactPhone: undefined,
    areaCode: undefined,
    areaZh: undefined,
    address: undefined,
    longitude: undefined,
    latitude: undefined,
    capacity: undefined,
    capacityWarn: undefined,
    capacityUsed: 0,
    warnEnable: 1,
    status: 1
  })
  formRef.value?.resetFields()
  // 省市区不在 form 里直接编辑，靠这个数组中转，所以 reset 要一起清
  areaArr.value = []
  // 新增是一张白表：容量没填过、预警值也没人定过，「按容量回填」的开关回到初始态
  loadedCapacity.value = undefined
  capacityWarnEdited.value = false
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
  queryParams.warehouseType = undefined
  queryParams.storeId = undefined
  queryParams.status = undefined
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
}

/** 地图选点确认：坐标收进列精度；换了点位老地址就对不上，解析出地址就一起改掉（同供应商管理） */
const handleLocationConfirm = (point: any) => {
  form.longitude = Number(Number(point.longitude).toFixed(6))
  form.latitude = Number(Number(point.latitude).toFixed(6))
  if (point.address) form.address = point.address
  // 选点不会触发那一格的 blur，之前报的"请填写经纬度"得自己抹掉
  formRef.value?.clearValidate('longitude')
}

/**
 * 选中负责的操作员：昵称既给 UserSelect 的输入框回显，也回填到联系人/联系电话那两格
 * （仓库的联系人多数就是负责它的操作员，省一遍输入，口径同供应商页的绑定账号）。
 * 手机号取用户列表里的 mobile，账号没填手机号就留空；换人时以新选的人为准，直接覆盖。
 */
const handleOperatorSelect = (user: any) => {
  const name = user.nickname || user.username
  form.userName = name
  form.contactName = name
  form.contactPhone = user.mobile
}

/** 取消绑定只清回显的名字：联系人/电话是已经录进去的业务数据，不跟着抹 */
const handleOperatorClear = () => {
  form.userName = undefined
}

/**
 * ERP 账号自己新增：操作员填成登录人（这一格禁用了不会再触发 @select，所以联系人得在这里主动带一次）。
 * 手机号不在 /api/auth/info 返回的登录信息里，只能按 id 再查一次用户详情（这个读接口不校验权限，ERP 账号调得动）。
 */
const fillSelfAsOperator = async () => {
  const self = userStore.user as any
  if (!self.id) return
  form.userId = self.id
  form.userName = self.nickname || self.username
  try {
    const { data } = await getUserDetailApi(self.id)
    form.contactName = data.nickname || data.username || form.userName
    form.contactPhone = data.mobile
  } catch (error) {
  }
}

/** 新增按钮操作 */
const handleAdd = async () => {
  reset()
  open.value = true
  title.value = '添加仓库'
  if (isErpAccount.value) await fillSelfAsOperator()
}

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailErpWarehouseApi(row.id)
    Object.assign(form, data)
    // area_code 是 "110000,110100,110101" 这种，split 回级联选择器要的数组
    areaArr.value = (data.areaCode || '').split(',').filter(Boolean)
    // 回显的这两格算"已经定过"：改仓库时别拿容量去覆盖库里那条预警线
    loadedCapacity.value = data.capacity
    capacityWarnEdited.value = data.capacityWarn !== null && data.capacityWarn !== undefined
    open.value = true
    title.value = '修改仓库'
  } catch (error) {
  }
}

/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    // 级联的 [省, 市, 区县] 存成一列 area_code，中文地名顺手解析出来存 area_zh（同供应商管理）；
    // 清空时 area_code 是空串，这几列在实体上是 FieldStrategy.IGNORED，所以写得回库
    form.areaCode = (areaArr.value || []).join(',')
    form.areaZh = resolveAreaCode(form.areaCode)
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateErpWarehouseApi(form)
        ElMessage.success('修改成功')
      } else {
        await addErpWarehouseApi(form)
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
  ElMessageBox.confirm(`确认删除仓库「${row.warehouseName}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteErpWarehouseApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个仓库吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteErpWarehouseApi(selectedIds.value)
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
  getStoreOptions()
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

/* 按钮那一格独占第二行（flex-basis 100% 就会换行）。
   上面 :last-child 把它压成了 0 0 auto，所以这里选择器多带一个 .el-form-item，特异性够、又写在后面才顶得住 */
.search-form :deep(.el-form-item.search-actions) {
  flex: 1 1 100%;
  min-width: 0;
  margin-right: 0;
}

/* 经纬度拆成两格：两个输入等分整行，中间夹一个分隔符（同供应商/门店管理）。
   el-input-number 上写死的 width:100% 会被 flex-basis 0 顶掉，正好让两格等宽 */
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

/* 存储详情里低于安全库存的可用数：标红提醒，口径同「商品库存」页 */
.stock-warning {
  color: var(--el-color-danger);
  font-weight: 600;
}

/* 「删除」外面套了一层 span 给 tooltip 当参考对象（禁用的按钮自己不收鼠标事件），
   EP 的 .el-button + .el-button 间距就断了，补回来 */
.action-tip {
  margin-left: 12px;
}
</style>
