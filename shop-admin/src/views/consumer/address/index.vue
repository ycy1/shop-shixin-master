<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="收货人" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="姓名/电话" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <!-- 消费者账号不看这一格：后端已把 userId 钉成本人，选了也会被覆盖（见 isConsumerAccount） -->
        <el-form-item v-if="!isConsumerAccount" label="消费者" prop="userId">
          <UserSelect v-model="queryParams.userId" :account-type="3" @select="handleQuery" @clear="handleQuery" />
        </el-form-item>
        <el-form-item label="标签" prop="tag">
          <el-select v-model="queryParams.tag" placeholder="全部" clearable>
            <el-option v-for="item in dict.options('address_tag')" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
       
        <!-- 时间范围 + 按钮同一行（照抄购物车、商品列表的 .search-row）：这一层 wrapper 独占第二行，
             里面两格都不等分、按控件本身的宽度从左排（样式见文件末尾） -->
        <div class="search-row" :style="{ flex: !isConsumerAccount ? '0 0 100%' : '0' }">
          <el-form-item label="创建时间" prop="dateRange">
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
          <el-button v-permission="['shop:address:add']" type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
          <el-button v-permission="['shop:address:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="消费者" align="center" min-width="120">
          <template #default="{ row }">
            <span>{{ row.nickname || row.username || `已删除用户 #${row.userId}` }}</span>
          </template>
        </el-table-column>
        <el-table-column label="收货人" prop="receiverName" align="center" min-width="100" />
        <el-table-column label="收货电话" prop="receiverPhone" align="center" min-width="120" />
        <el-table-column label="标签" align="center" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.tag" size="small" :type="dict.tag('address_tag', row.tag)">
              {{ dict.label('address_tag', row.tag) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="详细地址" prop="detail" align="center" min-width="180" />
         <el-table-column label="是否默认" align="center" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" size="small" type="success">默认</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" align="center" width="170">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.createTime, 'YYYY-MM-DD') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="150" fixed="right">
          <template #default="scope">
            <el-button v-permission="['shop:address:update']" type="primary" link icon="Edit"
              @click="handleUpdate(scope.row)">修改</el-button>
            <el-button v-permission="['shop:address:delete']" type="danger" link icon="Delete"
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
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="消费者" prop="userId">
                <UserSelect v-model="form.userId" :label="form.nickname" :account-type="3" style="width: 100%"
                  @select="handleConsumerSelect" @clear="handleConsumerClear" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="收货人姓名" prop="receiverName">
                <el-input v-model="form.receiverName" placeholder="请输入收货人姓名" maxlength="50" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="收货人电话" prop="receiverPhone">
                <el-input v-model="form.receiverPhone" placeholder="请输入收货人电话" maxlength="20" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="标签" prop="tag">
                <!-- 字典里有的直接选，没有的允许现场输一个：这一列本来就是自由文本 -->
                <el-select v-model="form.tag" placeholder="选填，如 家/公司/学校" style="width: 100%" clearable filterable
                  allow-create default-first-option>
                  <el-option v-for="item in dict.options('address_tag')" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
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
              <el-form-item label="详细地址" prop="detail">
                <el-input v-model="form.detail" placeholder="街道、门牌号" maxlength="255" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="邮编" prop="postalCode">
                <el-input v-model="form.postalCode" placeholder="选填" maxlength="10" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="是否默认" prop="isDefault">
                <el-radio-group v-model="form.isDefault">
                  <el-radio :value="1">默认地址</el-radio>
                  <el-radio :value="0">否</el-radio>
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
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listShopAddressApi,
  detailShopAddressApi,
  addShopAddressApi,
  updateShopAddressApi,
  deleteShopAddressApi
} from '@/api/consumer/address'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import ShopMap from '@/components/ShopMap/index.vue'
import validate from '@/utils/validate'
import { useDict } from '@/utils/dictCache'
import { useColPickerData } from '@/composables/useColPickerData'
import { useUserStore } from '@/store/modules/user'

/** 标签字典：address_tag，值就是中文本身 */
const dict = useDict('address_tag')

/** 地区数据源：@vant/area-data，级联的 value 就是行政区划代码 */
const { colPickerData, resolveAreaCode } = useColPickerData()
// 库里存的是 area_code（"110000,110100,110101"）+ 冗余的 area_zh，
// 级联选择器要的是数组，join / split 的互转在 reset、回显、提交三处做
const areaArr = ref<string[]>([])

const userStore = useUserStore()
/**
 * 当前登录的是否消费者账号（字典 account_type 的 3）。只用来决定显不显消费者筛选那一格，
 * 真正的数据范围在后端：ShopConsumerAddressServiceImpl#selectPage 会把 userId 覆盖成登录 id。
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
  tag: undefined,
  isDefault: undefined
})

const dateRange = ref<string[]>([])

// 表单参数
const form = reactive<any>({})

const rules = reactive<FormRules>({
  userId: [{ required: true, message: '请选择消费者', trigger: 'change' }],
  receiverName: [{ required: true, message: '收货人姓名不能为空', trigger: 'blur' }],
  receiverPhone: [{ required: true, message: '收货人电话不能为空', trigger: 'blur' }],
  detail: [{ required: true, message: '详细地址不能为空', trigger: 'blur' }]
})

const areaOf = (row: any) => {
  const area = row.areaZh || resolveAreaCode(row.areaCode)
  return area || '-'
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
    const { data } = await listShopAddressApi(params)
    dataList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
  } finally {
    loading.value = false
  }
}

/**
 * 选中消费者：昵称既给 UserSelect 的输入框回显，也回填到收货人姓名/电话那两格
 * （收货人多数就是本人，省一遍输入）。手机号取用户列表里的 mobile，
 * 账号没填手机号就留空，让必填校验去提示；换人的时候以新选的人为准，直接覆盖。
 */
const handleConsumerSelect = (user: any) => {
  const name = user.nickname || user.username
  form.nickname = name
  form.receiverName = name
  form.receiverPhone = user.mobile
}

const handleConsumerClear = () => {
  form.nickname = undefined
}

/** 表单重置 */
const reset = () => {
  Object.assign(form, {
    id: undefined,
    userId: undefined,
    nickname: undefined,
    receiverName: undefined,
    receiverPhone: undefined,
    areaCode: undefined,
    areaZh: undefined,
    detail: undefined,
    postalCode: undefined,
    longitude: undefined,
    latitude: undefined,
    tag: undefined,
    isDefault: 0
  })
  areaArr.value = []
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
  queryParams.userId = undefined
  queryParams.tag = undefined
  queryParams.isDefault = undefined
  dateRange.value = []
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
  if (point.address) form.detail = point.address
}

/** 新增按钮操作 */
const handleAdd = () => {
  reset()
  open.value = true
  title.value = '添加收货地址'
}

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailShopAddressApi(row.id)
    Object.assign(form, data)
    // area_code 是 "110000,110100,110101" 这种，split 回级联选择器要的数组
    areaArr.value = (data.areaCode || '').split(',').filter(Boolean)
    open.value = true
    title.value = '修改收货地址'
  } catch (error) {
  }
}

/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    // 级联的 [省, 市, 区县] 存成一列 area_code，中文地名顺手解析出来存 area_zh（同门店、用户管理）；
    // 清空时 area_code 是空串，这几列在实体上是 FieldStrategy.IGNORED，所以写得回库
    form.areaCode = (areaArr.value || []).join(',')
    form.areaZh = resolveAreaCode(form.areaCode)
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateShopAddressApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopAddressApi(form)
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
  ElMessageBox.confirm(`确认删除「${row.receiverName}」的收货地址吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopAddressApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 条收货地址吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopAddressApi(selectedIds.value)
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

/* 时间范围那一行（照抄购物车、商品列表的 .search-row）：wrapper 独占整行把日期格顶到第二行，
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

/* 经纬度两个输入框并成一格 */
.pair-field {
  display: flex;
  align-items: center;
  width: 100%;

  .pair-split {
    padding: 0 8px;
    color: var(--el-text-color-secondary);
  }
}
</style>
