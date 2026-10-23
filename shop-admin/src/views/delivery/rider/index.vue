<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="请输入姓名/编码/手机号" clearable
            @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="物流公司" prop="logisticsCompanyId">
          <el-select v-model="queryParams.logisticsCompanyId" placeholder="全部" clearable filterable>
            <el-option v-for="item in logisticsOptions" :key="item.id" :label="item.companyName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="交通工具" prop="vehicleType">
          <el-select v-model="queryParams.vehicleType" placeholder="全部" clearable>
            <el-option v-for="item in dict.options('rider_vehicle_type')" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="在线状态" prop="onlineStatus">
          <el-select v-model="queryParams.onlineStatus" placeholder="全部" clearable>
            <el-option v-for="item in dict.options('rider_online_status')" :key="item.value" :label="item.label"
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
          <el-button v-permission="['shop:rider:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['shop:rider:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <!-- 表格级开了 show-overflow-tooltip，纯图片列要显式关掉，否则悬停会弹一个空 tooltip -->
        <el-table-column label="头像" align="center" width="80" :show-overflow-tooltip="false">
          <template #default="{ row }">
            <el-image v-if="row.avatar" :src="row.avatar" :preview-src-list="[row.avatar]" preview-teleported
              fit="cover" style="width: 40px; height: 40px; border-radius: 50%" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="送货员编码" prop="riderCode" align="center" min-width="140" />
        <el-table-column label="送货员姓名" prop="riderName" align="center" min-width="100" />
        <el-table-column label="手机号" prop="phone" align="center" min-width="120" />
        <el-table-column label="常驻地点" align="center" min-width="180">
          <template #default="{ row }">
            <!-- 没填常驻地点的送货员是正常状态，只是他那份「离我最近」的配送列表排不出距离 -->
            <span>{{ row.residentAddress || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="所属物流公司" align="center" min-width="140">
          <template #default="{ row }">
            <span>{{ row.logisticsCompanyName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="送货员账号" align="center" min-width="110">
          <template #default="{ row }">
            <span>{{ row.userName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="在线状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag('rider_online_status', row.onlineStatus)">
              {{ dict.label('rider_online_status', row.onlineStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="工作状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag('rider_work_status', row.workStatus)">
              {{ dict.label('rider_work_status', row.workStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column label="操作" align="center" width="150" fixed="right">
          <template #default="scope">
            <el-button v-permission="['shop:rider:update']" type="primary" link icon="Edit"
              @click="handleUpdate(scope.row)">修改</el-button>
            <el-button v-permission="['shop:rider:delete']" type="danger" link icon="Delete"
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
      <el-dialog v-model="open" :title="title" width="720px" append-to-body destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
          <el-divider content-position="left">基本信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="送货员编码" prop="riderCode">
                <el-input v-model="form.riderCode" placeholder="保存后由系统生成" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="送货员姓名" prop="riderName">
                <el-input v-model="form.riderName" placeholder="请输入送货员姓名" maxlength="50" show-word-limit />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="手机号" prop="phone">
                <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="20" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="送货员账号" prop="userId">
                <!-- 送货员自己的登录账号，可空（未开通 App 账号）；只列启用的送货员账号（account_type=2），别的类型不列 -->
                <user-select v-model="form.userId" :label="form.userName" only-enabled :account-type="2"
                  placeholder="选择系统用户" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="所属物流公司" prop="logisticsCompanyId">
                <!-- 可空：不挂靠物流公司的送货员是正常状态，清空要能存回去（服务层给了 FieldStrategy.IGNORED） -->
                <el-select v-model="form.logisticsCompanyId" placeholder="可不选" clearable filterable
                  style="width: 100%">
                  <el-option v-for="item in logisticsOptions" :key="item.id" :label="item.companyName"
                    :value="item.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="交通工具" prop="vehicleType">
                <el-select v-model="form.vehicleType" placeholder="请选择" style="width: 100%">
                  <el-option v-for="item in dict.options('rider_vehicle_type')" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="车牌号" prop="vehicleNo">
                <el-input v-model="form.vehicleNo" placeholder="选填" maxlength="32" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="最大接单数" prop="maxOrderCount">
                <el-input-number v-model="form.maxOrderCount" :min="1" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="服务城市" prop="serviceCity">
                <el-input v-model="form.serviceCity" placeholder="选填，如 杭州市" maxlength="50" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="服务区域" prop="serviceArea">
                <el-input v-model="form.serviceArea" placeholder="多个区域用逗号分隔" maxlength="255" />
              </el-form-item>
            </el-col>
            <!-- 常驻地点：删了门店归属之后，这是"这个人常在哪儿"的唯一坐标 ——
                 送货员账号那份配送列表按「送货点离这里最近」排的就是它，所以经纬度要成对（后端 validate 会挡只填一半） -->
            <el-col :span="24">
              <el-form-item label="常驻地点" prop="residentAddress">
                <el-input v-model="form.residentAddress" placeholder="请输入常驻地点，或右侧地图选点" maxlength="255" />
              </el-form-item>
            </el-col>
            <el-col :span="18">
              <el-form-item label="常驻经纬度" prop="residentLng">
                <div class="pair-field">
                  <el-input-number v-model="form.residentLng" :precision="6" :controls="false" placeholder="经度"
                    style="width: 100%" />
                  <span class="pair-split">-</span>
                  <el-input-number v-model="form.residentLat" :precision="6" :controls="false" placeholder="纬度"
                    style="width: 100%" />
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <!-- 整表 label-width 是 110px，这一格只有 6 格宽，不取消 label 按钮就顶出去了 -->
              <el-form-item label-width="0">
                <shop-map :longitude="form.residentLng" :latitude="form.residentLat" title="选择常驻位置"
                  @confirm="handleResidentLocation" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="在线状态" prop="onlineStatus">
                <el-select v-model="form.onlineStatus" placeholder="请选择" style="width: 100%">
                  <el-option v-for="item in dict.options('rider_online_status')" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="工作状态" prop="workStatus">
                <el-select v-model="form.workStatus" placeholder="请选择" style="width: 100%">
                  <el-option v-for="item in dict.options('rider_work_status')" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态" prop="status">
                <el-radio-group v-model="form.status">
                  <el-radio :value="1">启用</el-radio>
                  <el-radio :value="0">禁用</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>
          <el-divider content-position="left">头像与证件</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="身份证号" prop="idCard">
                <el-input v-model="form.idCard" placeholder="选填" maxlength="32" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="健康证到期日" prop="healthCertExpire">
                <el-date-picker v-model="form.healthCertExpire" type="datetime" placeholder="选填"
                  value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="头像" prop="avatar">
                <upload-image v-model="form.avatar" :size="120" source="rider" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="身份证正面" prop="idCardFront">
                <upload-image v-model="form.idCardFront" :size="120" source="rider" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="身份证反面" prop="idCardBack">
                <upload-image v-model="form.idCardBack" :size="120" source="rider" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="健康证图片" prop="healthCert">
                <upload-image v-model="form.healthCert" :size="120" source="rider" />
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
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listShopRiderApi,
  detailShopRiderApi,
  addShopRiderApi,
  updateShopRiderApi,
  deleteShopRiderApi
} from '@/api/delivery/rider'
import { optionsShopLogisticsApi } from '@/api/delivery/logistics'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import UploadImage from '@/components/Upload/Image.vue'
import ShopMap from '@/components/ShopMap/index.vue'
import { useDict } from '@/utils/dictCache'

/** 送货员相关字典：交通工具 / 在线状态 / 工作状态 / 启用状态 */
const dict = useDict('rider_vehicle_type', 'rider_online_status', 'rider_work_status', 'rider_status')

/** 物流公司下拉：只列启用中的，已挂靠但被停用的靠列表列显示 */
const logisticsOptions = ref<any[]>([])

const getLogisticsOptions = async () => {
  try {
    const { data } = await optionsShopLogisticsApi()
    logisticsOptions.value = data || []
  } catch (error) {
  }
}

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
  logisticsCompanyId: undefined,
  vehicleType: undefined,
  onlineStatus: undefined,
  status: undefined
})

// 表单参数
const form = reactive<any>({})

const rules = reactive<FormRules>({
  riderName: [{ required: true, message: '送货员姓名不能为空', trigger: 'blur' }],
  phone: [
    { required: true, message: '手机号不能为空', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ]
})

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listShopRiderApi(queryParams)
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
    riderCode: undefined,
    userId: undefined,
    userName: '',
    riderName: undefined,
    phone: undefined,
    avatar: undefined,
    logisticsCompanyId: undefined,
    logisticsCompanyName: '',
    idCard: undefined,
    idCardFront: undefined,
    idCardBack: undefined,
    healthCert: undefined,
    healthCertExpire: undefined,
    vehicleType: dict.defaultValue('rider_vehicle_type', 1),
    vehicleNo: undefined,
    serviceCity: undefined,
    serviceArea: undefined,
    residentAddress: undefined,
    residentLng: undefined,
    residentLat: undefined,
    maxOrderCount: 5,
    onlineStatus: dict.defaultValue('rider_online_status', 0),
    workStatus: dict.defaultValue('rider_work_status', 0),
    status: 1
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
  queryParams.logisticsCompanyId = undefined
  queryParams.vehicleType = undefined
  queryParams.onlineStatus = undefined
  queryParams.status = undefined
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
}

/** 新增按钮操作 */
const handleAdd = () => {
  reset()
  open.value = true
  title.value = '添加送货员'
}

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailShopRiderApi(row.id)
    // userName / logisticsCompanyName 是接口按 id 回填的显示字段，一起进 form 给选择器回显用
    Object.assign(form, data)
    open.value = true
    title.value = '修改送货员'
  } catch (error) {
  }
}

/**
 * 地图选点回填常驻经纬度（留 6 位小数）：没挪点时组件抛的 address 是空串，不会冲掉手填的地点文本。
 * 反过来手改经纬度不回填地址 —— 那两格只是给人看的备注，就近排序读的是坐标。
 */
const handleResidentLocation = (point: any) => {
  form.residentLng = Number(Number(point.longitude).toFixed(6))
  form.residentLat = Number(Number(point.latitude).toFixed(6))
  if (point.address) form.residentAddress = point.address
}

/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateShopRiderApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopRiderApi(form)
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
  ElMessageBox.confirm(`确认删除送货员「${row.riderName}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopRiderApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个送货员吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopRiderApi(selectedIds.value)
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
  getLogisticsOptions()
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

/* 经度 - 纬度 并排（和配送管理、仓库那几个表单同一套写法）：两个输入框等分，中间的分隔符不跟着伸缩 */
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
