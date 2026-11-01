<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="名称/编号" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="请输入园区名称/编号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="关联IP" prop="ipId">
          <el-select v-model="queryParams.ipId" placeholder="全部" clearable>
            <el-option v-for="item in ipOptions" :key="item.id" :label="item.ipName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_STATUS)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-card class="box-card">
      <!-- 操作工具栏 -->
      <template #header>
        <ButtonGroup>
          <el-button v-permission="['shop:park:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['shop:park:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格：一行是一家园区，它卖哪些票在「票种管理」那页按园区筛 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <!-- 表格级开了 show-overflow-tooltip，纯图片列要显式关掉，否则悬停会弹一个空 tooltip -->
        <el-table-column label="封面" align="center" width="90" :show-overflow-tooltip="false">
          <template #default="{ row }">
            <el-image v-if="row.coverImage" :src="row.coverImage" :preview-src-list="[row.coverImage]"
              preview-teleported fit="contain" style="width: 50px; height: 50px; border-radius: 4px" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="园区编号" prop="parkNo" align="center" min-width="150" />
        <el-table-column label="园区名称" prop="parkName" align="center" min-width="150" />
        <el-table-column label="关联IP" align="center" min-width="130">
          <template #default="{ row }">
            <span>{{ row.ipName || (row.ipId ? `已删除IP #${row.ipId}` : '-') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="所在城市" align="center" min-width="150">
          <template #default="{ row }">
            <span>{{ areaOf(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="详细地址" align="center" min-width="200">
          <template #default="{ row }">
            <span>{{ row.address || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="营业时间" align="center" width="130">
          <template #default="{ row }">
            <span>{{ row.openTime || '-' }}</span>
          </template>
        </el-table-column>
        <!-- 名下票种数由服务层按当页 id 一次分组数回来（这一格是"这家园区还在卖几种票"，删除前先看它） -->
        <el-table-column label="名下票种" align="center" width="90">
          <template #default="{ row }">
            <span>{{ row.ticketCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_STATUS, row.status)">
              {{ dict.label(DICT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 热门：勾上的排在最前，潮玩专区首页那圈公园轮播取的就是头几家 -->
        <el-table-column label="热门" align="center" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.isHot === 1" type="danger" size="small">热门</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="180" fixed="right">
          <template #default="scope">
            <el-button v-permission="['shop:park:update']" type="primary" link icon="Edit"
              @click="handleUpdate(scope.row)">修改</el-button>
            <el-button v-permission="['shop:park:delete']" type="danger" link icon="Delete"
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
              <el-form-item label="园区编号" prop="parkNo">
                <el-input v-model="form.parkNo" placeholder="保存后由系统生成" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="园区名称" prop="parkName">
                <el-input v-model="form.parkName" placeholder="请输入园区名称" maxlength="100" show-word-limit />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="关联IP" prop="ipId">
                <!-- 口径同门店表单那格潮玩系列：options 只给启用中的IP，不绑就留空。
                     老园区绑着一个后来被停用/删掉的IP时拿名字兜一下，别让下拉空着（同品牌那一档的写法） -->
                <el-select v-model="form.ipId" placeholder="不归属某个IP" clearable filterable style="width: 100%">
                  <el-option v-for="item in ipSelectOptions" :key="item.id" :label="item.ipName" :value="item.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态" prop="status">
                <el-radio-group v-model="form.status">
                  <el-radio v-for="item in dict.options(DICT_STATUS)" :key="item.value" :value="item.value">
                    {{ item.label }}
                  </el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="热门" prop="isHot">
                <el-switch v-model="form.isHot" :active-value="1" :inactive-value="0" />
                <span class="form-tip">勾上的排在最前，首页那圈公园轮播取的就是头几家</span>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="所在地区">
                <!-- 级联的 value 就是行政区划代码，选中结果在提交时 join 成 area_code（口径同门店表单） -->
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
                <el-input v-model="form.address" placeholder="街道、门牌号" maxlength="255" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="营业时间" prop="openTime">
                <!-- open_time 存的就是 "09:00-21:00" 这一列文本，两端各一个时间下拉，
                     拼接/拆分在 reset、回显、提交三处做（口径同门店的营业状态那格） -->
                <div class="pair-field">
                  <el-time-select v-model="hoursStart" start="00:00" end="23:30" step="00:30" clearable
                    placeholder="开始" @change="handleHoursStartChange" />
                  <span class="pair-split">-</span>
                  <el-time-select v-model="hoursEnd" :start="hoursEndStart" end="24:00" step="00:30"
                    :min-time="hoursStart" clearable placeholder="结束" />
                </div>
              </el-form-item>
            </el-col>
          </el-row>
          <el-divider content-position="left">图片与介绍</el-divider>
          <el-form-item label="园区封面" prop="coverImage">
            <upload-image v-model="form.coverImage" :size="120" source="park" />
          </el-form-item>
          <el-form-item label="园区介绍" prop="description">
            <el-input v-model="form.description" type="textarea" :rows="4" placeholder="选填，会显示在小程序那侧的园区详情里"
              maxlength="2000" show-word-limit />
          </el-form-item>
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
  listShopParkApi,
  detailShopParkApi,
  addShopParkApi,
  updateShopParkApi,
  deleteShopParkApi
} from '@/api/trendy/park'
import { optionsShopIpApi } from '@/api/trendy/ip'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import UploadImage from '@/components/Upload/Image.vue'
import ShopMap from '@/components/ShopMap/index.vue'
import { useDict } from '@/utils/dictCache'
import { useColPickerData } from '@/composables/useColPickerData'

const DICT_STATUS = 'park_status'

const dict = useDict(DICT_STATUS)

/** 地区数据源：@vant/area-data，级联的 value 就是行政区划代码 */
const { colPickerData, resolveAreaCode } = useColPickerData()
// 库里存的是 area_code（"110000,110100,110101"）+ 冗余的 area_zh，
// 级联选择器要的是数组，join / split 的互转在 reset、回显、提交三处做
const areaArr = ref<string[]>([])

// open_time 存的是 "09:00-21:00" 这种文本，两端各一个时间下拉
const hoursStart = ref('')
const hoursEnd = ref('')
const hoursEndStart = computed(() => hoursStart.value || '00:00')

/** 换了开始时间就清掉结束时间：留着一个比开始更早的时刻是非法组合，:min-time 只会把它置灰 */
const handleHoursStartChange = () => {
  hoursEnd.value = ''
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
  ipId: undefined,
  status: undefined
})

// 表单参数
const form = reactive<any>({})

const rules = reactive<FormRules>({
  parkName: [{ required: true, message: '园区名称不能为空', trigger: 'blur' }]
})

/** 所在城市优先用库里冗余的 area_zh，兜底才按 area_code 现场解析（老数据可能只有 code） */
const areaOf = (row: any) => row.areaZh || resolveAreaCode(row.areaCode) || '-'

/** IP 候选：口径同门店那页的品牌/系列 —— options 只给启用中的IP */
const ipOptions = ref<any[]>([])
const getIpOptions = async () => {
  try {
    const { data } = await optionsShopIpApi()
    ipOptions.value = data || []
  } catch (error) {
  }
}

/**
 * IP 下拉实际用的候选：绑着停用/已删IP的老园区会选不出来，所以拿名字兜一下别让那一格空着
 *（form.ipName 是详情接口联出来的名字，没这个名字就退回 id，同门店的「已停用品牌 #id」那一档）
 */
const ipSelectOptions = computed(() => {
  const id = form.ipId
  if (!id) return ipOptions.value
  if (ipOptions.value.some((item: any) => String(item.id) === String(id))) return ipOptions.value
  return [{ id, ipName: form.ipName || `已停用IP #${id}` }, ...ipOptions.value]
})

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listShopParkApi(queryParams)
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
    parkNo: undefined,
    parkName: undefined,
    ipId: undefined,
    ipName: undefined,
    areaCode: undefined,
    areaZh: undefined,
    address: undefined,
    longitude: undefined,
    latitude: undefined,
    openTime: undefined,
    coverImage: undefined,
    description: undefined,
    status: Number(dict.defaultValue(DICT_STATUS, 1)),
    isHot: 0
  })
  formRef.value?.resetFields()
  // 省市区不在 form 里直接编辑，靠这个数组中转，所以 reset 要一起清
  areaArr.value = []
  // 营业时间同理，两端是独立的 ref
  hoursStart.value = ''
  hoursEnd.value = ''
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
  queryParams.ipId = undefined
  queryParams.status = undefined
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
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
  open.value = true
  title.value = '添加园区'
}

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailShopParkApi(row.id)
    Object.assign(form, data)
    // area_code 是 "110000,110100,110101" 这种，split 回级联选择器要的数组
    areaArr.value = (data.areaCode || '').split(',').filter(Boolean)
    // open_time 是 "09:00-21:00"，拆成两端时刻回填两个时间下拉
    const [start = '', end = ''] = String(data.openTime || '').split('-')
    hoursStart.value = start.trim()
    hoursEnd.value = end.trim()
    open.value = true
    title.value = '修改园区'
  } catch (error) {
  }
}

/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    // 级联的 [省, 市, 区县] 存成一列 area_code，中文地名顺手解析出来存 area_zh（同门店、用户管理）；
    // 清空时 area_code 是空串，这两列在实体上是 FieldStrategy.IGNORED，所以写得回库
    form.areaCode = (areaArr.value || []).join(',')
    form.areaZh = resolveAreaCode(form.areaCode)
    // 两端都选了才拼成 "09:00-21:00"；只选一头当没填，避免留下半截文本
    form.openTime = hoursStart.value && hoursEnd.value ? `${hoursStart.value}-${hoursEnd.value}` : ''
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateShopParkApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopParkApi(form)
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
  ElMessageBox.confirm(`确认删除园区「${row.parkName}」吗？名下还有票种时删不掉。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopParkApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 家园区吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopParkApi(selectedIds.value)
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
  getIpOptions()
})
</script>

<style scoped lang="scss">
/* 参照门店管理：筛选项等分整行，控件宽度统一由 CSS 控制 */
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

/* 一段拆成两格的字段（营业时间、经纬度）：两个输入等分整行，中间夹一个分隔符 */
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

/* 开关右边那句说明（和盲盒那页同一形状） */
.form-tip {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
