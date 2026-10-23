<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="收藏对象" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="商品/门店/品牌名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <!-- 消费者账号不看这一格：后端已把 userId 钉成本人，选了也会被覆盖（见 isConsumerAccount） -->
        <el-form-item v-if="!isConsumerAccount" label="消费者" prop="userId">
          <UserSelect v-model="queryParams.userId" :account-type="3" @select="handleQuery" @clear="handleQuery" />
        </el-form-item>
        <el-form-item label="收藏类型" prop="targetType">
          <el-select v-model="queryParams.targetType" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>

        <!-- 时间范围 + 按钮同一行（照抄购物车、商品列表的 .search-row）：这一层 wrapper 独占第二行，
             里面两格都不等分、按控件本身的宽度从左排（样式见文件末尾） -->
        <div class="search-row" :style="{ flex: !isConsumerAccount ? '0 0 100%' : '0' }">
          <el-form-item label="收藏时间" prop="dateRange">
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
          <el-button v-permission="['shop:favorite:add']" type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
          <el-button v-permission="['shop:favorite:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="消费者" align="center" min-width="130">
          <template #default="{ row }">
            <span>{{ row.nickname || row.username || `已删除用户 #${row.userId}` }}</span>
          </template>
        </el-table-column>
        <el-table-column label="收藏类型" prop="targetType" align="center" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_TYPE, row.targetType)">
              {{ dict.label(DICT_TYPE, row.targetType) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 对象名称由后端按类型从商品/门店/品牌三张表里挑出来，对象被删了就是空 -->
        <el-table-column label="收藏对象" align="center" min-width="200">
          <template #default="{ row }">
            <span>{{ row.targetName || `已删除对象 #${row.targetId}` }}</span>
          </template>
        </el-table-column>
        <!-- 收藏时间读 favorite_time 那一格（重新收藏会刷新），不是建行时间 create_time -->
        <el-table-column label="收藏时间" prop="favoriteTime" align="center" width="170">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.favoriteTime, 'YYYY-MM-DD') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="150" fixed="right">
          <template #default="scope">
            <el-button v-permission="['shop:favorite:update']" type="primary" link icon="Edit"
              @click="handleUpdate(scope.row)">修改</el-button>
            <el-button v-permission="['shop:favorite:delete']" type="danger" link icon="Delete"
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
      <el-dialog v-model="open" :title="title" width="640px" append-to-body destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
          <el-form-item label="消费者" prop="userId">
            <UserSelect v-model="form.userId" :label="form.nickname" :account-type="3" style="width: 100%"
              @select="handleConsumerSelect" />
          </el-form-item>
          <el-form-item label="收藏类型" prop="targetType">
            <el-select v-model="form.targetType" placeholder="请选择收藏类型" style="width: 100%"
              @change="handleTypeChange">
              <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
                :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="收藏对象" prop="targetId">
            <el-select v-model="form.targetId" :placeholder="targetPlaceholder" filterable clearable
              :remote="form.targetType === TYPE_PRODUCT" :remote-method="searchProducts" :loading="targetLoading"
              style="width: 100%">
              <el-option v-for="item in targetOptions" :key="item.id" :label="item.name" :value="item.id">
                <!-- 品牌带上 LOGO，其它类型是 名称(+编码/单价) 一行 -->
                <shop-brand-option v-if="item.hasLogo" :brand-name="item.name" :logo="item.logo" />
                <div v-else class="target-option">
                  <span class="target-option__name">{{ item.name }}</span>
                  <span v-if="item.code" class="target-option__code">{{ item.code }}</span>
                  <span v-if="item.priceText" class="target-option__price">{{ item.priceText }}</span>
                </div>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="收藏时间" prop="favoriteTime">
            <el-date-picker v-model="form.favoriteTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="留空按当前时间记" style="width: 100%" />
          </el-form-item>
          <!-- 对象名称不存表：列表和回显都是查询时按 target_type 现联表带出来的 -->
          <!-- 新增等于"收藏上这个对象"：这一人+类型+对象如果已有记录（含已删除的），后端只把收藏时间刷成上面填的
               那一格并把它复活，不会插出第二条，所以这里不提示重复（见 ShopConsumerFavoriteServiceImpl#insert） -->
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
  listShopFavoriteApi,
  detailShopFavoriteApi,
  addShopFavoriteApi,
  updateShopFavoriteApi,
  deleteShopFavoriteApi
} from '@/api/consumer/favorite'
import { listShopProductApi } from '@/api/shop/product'
import { optionsShopBrandApi } from '@/api/shop/brand'
import { optionsShopStoreApi } from '@/api/store/store'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import ShopBrandOption from '@/components/ShopBrandOption/index.vue'
import validate from '@/utils/validate'
import { useDict } from '@/utils/dictCache'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
/**
 * 当前登录的是否消费者账号（字典 account_type 的 3）。只用来决定显不显消费者筛选那一格，
 * 真正的数据范围在后端：ShopConsumerFavoriteServiceImpl#selectPage 会把 userId 覆盖成登录 id。
 */
const isConsumerAccount = computed(() => userStore.user.accountType === 3)

const DICT_TYPE = 'favorite_target_type'
// 收藏类型的三个取值，与字典 favorite_target_type、表注释一致；只用于分支判断，文案一律走字典
const TYPE_PRODUCT = 1
const TYPE_STORE = 2
const TYPE_BRAND = 3

const dict = useDict(DICT_TYPE)

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
  targetType: undefined
})
const dateRange = ref<string[]>([])

// 表单参数
const form = reactive<any>({})

const rules = reactive<FormRules>({
  userId: [{ required: true, message: '请选择消费者', trigger: 'change' }],
  targetType: [{ required: true, message: '请选择收藏类型', trigger: 'change' }],
  targetId: [{ required: true, message: '请选择收藏对象', trigger: 'change' }]
})

// 商品要远程按关键字搜（一次只取前 20 条），门店/品牌一次拉全量启用项
const productOptions = ref<any[]>([])
const storeOptions = ref<any[]>([])
const brandOptions = ref<any[]>([])
const targetLoading = ref(false)

/** 单价的显示口径同购物车、商品列表 */
const priceText = (price: any) => (price == null || price === '' ? '' : `￥${Number(price).toFixed(2)}`)

/**
 * 三种类型统一成一个候选列表，选择器只留一个：门店/品牌没有单价、商品没有 LOGO，
 * 所以按存在的字段给（hasLogo 决定这一行用不用 shop-brand-option 渲染）
 */
const targetOptions = computed(() => {
  let list: any[] = []
  if (form.targetType === TYPE_STORE) {
    list = storeOptions.value.map((item: any) => ({ id: item.id, name: item.storeName }))
  } else if (form.targetType === TYPE_BRAND) {
    list = brandOptions.value.map((item: any) => ({
      id: item.id,
      name: item.brandName,
      logo: item.logo || '',
      hasLogo: true
    }))
  } else if (form.targetType === TYPE_PRODUCT) {
    list = productOptions.value.map((item: any) => ({
      id: item.id,
      name: item.productName,
      code: item.productCode,
      priceText: priceText(item.productPrice)
    }))
  }
  // 修改回显时选中的对象往往不在候选里（门店/品牌已停用、商品已删除），
  // el-select 会直接显示裸 id，所以只在展示层用详情接口带回的名称补一条
  const id = form.targetId
  if (id && !list.some((item) => String(item.id) === String(id))) {
    return [{ id, name: form.targetName || `已删除对象 #${id}` }, ...list]
  }
  return list
})

const targetPlaceholder = computed(() => {
  if (form.targetType === TYPE_PRODUCT) return '输入名称/编码搜索商品'
  if (form.targetType === TYPE_STORE) return '请选择门店'
  if (form.targetType === TYPE_BRAND) return '请选择品牌'
  return '请先选择收藏类型'
})

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const params: any = { ...queryParams }
    if (dateRange.value && dateRange.value.length === 2) {
      params.beginTime = `${dateRange.value[0]} 00:00:00`
      params.endTime = `${dateRange.value[1]} 23:59:59`
    }
    const { data } = await listShopFavoriteApi(params)
    dataList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
  } finally {
    loading.value = false
  }
}

/** 门店、品牌下拉：只取启用中的 */
const getStoreOptions = async () => {
  try {
    const { data } = await optionsShopStoreApi()
    storeOptions.value = data || []
  } catch (error) {
  }
}
const getBrandOptions = async () => {
  try {
    const { data } = await optionsShopBrandApi()
    brandOptions.value = data || []
  } catch (error) {
  }
}

/** 商品候选的远程搜索 */
const searchProducts = async (keyword: string) => {
  targetLoading.value = true
  try {
    const { data } = await listShopProductApi({ pageNum: 1, pageSize: 20, keyword: keyword || undefined })
    productOptions.value = data.records || []
  } catch (error) {
  } finally {
    targetLoading.value = false
  }
}

/** 选中消费者时把昵称带回来，UserSelect 的输入框才知道该显示什么名字 */
const handleConsumerSelect = (user: any) => {
  form.nickname = user?.nickname || user?.username
}

/** 换类型等于换了一批候选，之前选的对象肯定不属于新类型，直接清掉 */
const handleTypeChange = () => {
  form.targetId = undefined
  form.targetName = undefined
  if (form.targetType === TYPE_PRODUCT && !productOptions.value.length) {
    searchProducts('')
  }
}

/** 表单重置 */
const reset = () => {
  Object.assign(form, {
    id: undefined,
    userId: undefined,
    nickname: undefined,
    targetType: TYPE_PRODUCT,
    targetId: undefined,
    targetName: undefined,
    favoriteTime: undefined
  })
  productOptions.value = []
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
  queryParams.targetType = undefined
  dateRange.value = []
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
  title.value = '添加收藏记录'
  searchProducts('')
}

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailShopFavoriteApi(row.id)
    Object.assign(form, data)
    open.value = true
    title.value = '修改收藏记录'
    if (form.targetType === TYPE_PRODUCT) {
      searchProducts('')
    }
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
        await updateShopFavoriteApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopFavoriteApi(form)
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
  ElMessageBox.confirm(`确认删除「${row.targetName || '该对象'}」这条收藏吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopFavoriteApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 条收藏记录吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopFavoriteApi(selectedIds.value)
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
  getBrandOptions()
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
  // flex: 0 0 100%;
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

/* 候选项一行：名称在左、编码和单价靠右，写法同购物车的商品下拉 */
.target-option {
  display: flex;
  align-items: center;
  gap: 8px;
  line-height: 20px;

  &__name {
    flex: 0 1 auto;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__code {
    flex: 0 0 auto;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  /* 单价推到行尾：候选项这一行是 flex */
  &__price {
    flex: 0 0 auto;
    margin-left: auto;
    padding-left: 8px;
  }
}
</style>
