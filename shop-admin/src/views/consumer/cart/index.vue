<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="商品" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="商品名称/编码" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <!-- 消费者账号不看这一格：后端已把 userId 钉成本人，选了也会被覆盖（见 isConsumerAccount） -->
        <el-form-item v-if="!isConsumerAccount" label="消费者" prop="userId">
          <UserSelect v-model="queryParams.userId" :account-type="3" @select="handleQuery" @clear="handleQuery" />
        </el-form-item>
        <el-form-item label="商品分类" prop="categoryId" class="category-select">
          <shop-category-select v-model="queryParams.categoryId" :data="categoryOptions" placeholder="全部分类" />
        </el-form-item>
        <el-form-item label="所属门店" prop="storeId">
          <el-select v-model="queryParams.storeId" placeholder="全部" clearable>
            <el-option v-for="item in storeOptions" :key="item.id" :label="item.storeName" :value="item.id" />
          </el-select>
        </el-form-item>

        <!-- 时间范围 + 按钮同一行（照抄商品列表的 .search-row）：这一层 wrapper 独占第二行，
             里面两格都不等分、按控件本身的宽度从左排（样式见文件末尾） -->
        <div class="search-row">
          <el-form-item label="加入购物车" prop="dateRange">
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
          <el-button v-permission="['shop:cart:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['shop:cart:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="消费者" align="center" min-width="130">
          <template #default="{ row }">
            <span>{{ row.nickname || row.username || '-' }}</span>
          </template>
        </el-table-column>
        <!-- 表级的 show-overflow-tooltip 会被每一列继承，纯图片列要显式关掉，否则悬停弹一个空 tooltip -->
        <el-table-column label="商品名称" prop="productName" align="center" min-width="180">
          <template #default="{ row }">
            <span>{{ row.productName || '商品已删除' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="规格" align="center" min-width="140">
          <template #default="{ row }">
            <span>{{ row.specText || '默认规格' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="商品编码" prop="productCode" align="center" min-width="150" />
        <el-table-column label="所属分类" prop="categoryName" align="center" width="120">
          <template #default="{ row }">
            <span>{{ row.categoryName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="品牌" align="center" min-width="160">
          <template #default="{ row }">
            <shop-brand-option v-if="row.brandName" :brand-name="row.brandName" :logo="row.brandLogo" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="所属门店" align="center" min-width="130">
          <template #default="{ row }">
            <span>{{ row.storeName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单价" align="center" width="100">
          <template #default="{ row }">
            <span v-if="row.productPrice != null">￥{{ Number(row.productPrice).toFixed(2) }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" prop="quantity" align="center" width="80" />
        <el-table-column label="小计" align="center" width="110">
          <template #default="{ row }">
            <span v-if="row.productPrice != null">￥{{ (Number(row.productPrice) * (row.quantity || 0)).toFixed(2)
              }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>

        <el-table-column label="加入购物车时间" prop="createTime" align="center" width="170">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.createTime, 'YYYY-MM-DD') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="150" fixed="right">
          <template #default="scope">
            <el-button v-permission="['shop:cart:update']" type="primary" link icon="Edit"
              @click="handleUpdate(scope.row)">修改</el-button>
            <el-button v-permission="['shop:cart:delete']" type="danger" link icon="Delete"
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
          <el-form-item label="商品规格" prop="skuId">
            <ShopProductSkuSelect v-model="form.skuId" :label="skuLabel" placeholder="选择要加购的具体规格"
              style="width: 100%" @select="handleSkuSelect" @clear="handleSkuClear" />
          </el-form-item>
          <!-- 单价/分类/品牌都是回显：后端保存时按 skuId 重新带出（ShopConsumerCartServiceImpl#validate），
               手填也存不进去，摆出来是让人看清这条记录按什么价、归到哪个分类和品牌下 -->
          <el-form-item label="单价">
            <span v-if="form.productPrice != null">{{ priceText(form.productPrice) }}</span>
            <span v-else>-</span>
          </el-form-item>
          <el-form-item label="所属分类">
            <span>{{ form.categoryName || '-' }}</span>
          </el-form-item>
          <el-form-item label="品牌">
            <shop-brand-option v-if="form.brandName" :brand-name="form.brandName" :logo="form.brandLogo" />
            <span v-else>-</span>
          </el-form-item>
          <el-form-item label="数量" prop="quantity">
            <el-input-number v-model="form.quantity" :min="1" :precision="0" style="width: 200px" />
          </el-form-item>
          <el-form-item label="是否选中" prop="selected">
            <el-radio-group v-model="form.selected">
              <el-radio :value="1">选中</el-radio>
              <el-radio :value="0">未选中</el-radio>
            </el-radio-group>
          </el-form-item>
          <!-- 门店不在表单里：同样是后端按商品带的快照，商品不限定门店时这一列是空 -->
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
  listShopCartApi,
  detailShopCartApi,
  addShopCartApi,
  updateShopCartApi,
  deleteShopCartApi
} from '@/api/consumer/cart'
import { getCategoryTreeApi } from '@/api/shop/category'
import { optionsShopStoreApi } from '@/api/store/store'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import ShopCategorySelect from '@/components/ShopCategorySelect/index.vue'
import ShopProductSkuSelect from '@/components/ShopProductSkuSelect/index.vue'
import ShopBrandOption from '@/components/ShopBrandOption/index.vue'
import validate from '@/utils/validate'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
/**
 * 当前登录的是否消费者账号（字典 account_type 的 3）。只用来决定显不显消费者筛选那一格，
 * 真正的数据范围在后端：ShopConsumerCartServiceImpl#selectPage 会把 userId 覆盖成登录 id。
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
  categoryId: undefined,
  storeId: undefined,
  selected: undefined
})
const dateRange = ref<string[]>([])

// 表单参数
const form = reactive<any>({})

const rules = reactive<FormRules>({
  userId: [{ required: true, message: '请选择消费者', trigger: 'change' }],
  skuId: [{ required: true, message: '请选择商品规格', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }]
})

// 分类树选项
const categoryOptions = ref<any[]>([])

// 门店下拉选项（只取启用中的）
const storeOptions = ref<any[]>([])

// 修改回显：详情接口带回的是名称/规格文本，选择器自己不认识 id，所以由这里拼给它
const skuLabel = computed(() =>
  `${form.productName || ''}${form.specText ? ` ${form.specText}` : ''}`.trim())

/** 单价的显示口径同列表那一列 */
const priceText = (price: any) => (price == null || price === '' ? '' : `￥${Number(price).toFixed(2)}`)

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const params: any = { ...queryParams }
    if (dateRange.value && dateRange.value.length === 2) {
      params.beginTime = `${dateRange.value[0]} 00:00:00`
      params.endTime = `${dateRange.value[1]} 23:59:59`
    }
    const { data } = await listShopCartApi(params)
    dataList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
  } finally {
    loading.value = false
  }
}

/** 加载分类树 */
const getCategoryOptions = async () => {
  try {
    const { data } = await getCategoryTreeApi({})
    categoryOptions.value = data || []
  } catch (error) {
  }
}

/** 加载门店下拉：只取启用中的 */
const getStoreOptions = async () => {
  try {
    const { data } = await optionsShopStoreApi()
    storeOptions.value = data || []
  } catch (error) {
  }
}

/** 选中消费者时把昵称带回来，UserSelect 的输入框才知道该显示什么名字 */
const handleConsumerSelect = (user: any) => {
  form.nickname = user?.nickname || user?.username
}

/**
 * 选完规格把单价、商品名、分类/品牌抄到表单上：这几格都只读回显（见弹窗里「单价/所属分类/品牌」）。
 * 提交时这几项传不传都行，后端 ShopCartSaveDto 不收，最终以这条规格所属的商品为准。
 */
const handleSkuSelect = (sku: any) => {
  form.productName = sku.productName
  form.productCode = sku.productCode
  form.specText = sku.specText
  form.productPrice = sku.productPrice
  form.categoryName = sku.categoryName
  form.brandName = sku.brandName
}

/** 换规格前先把手上的回显清掉，免得留着上一个规格的价格和分类 */
const handleSkuClear = () => {
  form.productName = undefined
  form.productCode = undefined
  form.specText = undefined
  form.productPrice = undefined
  form.categoryName = undefined
  form.brandName = undefined
}

/** 表单重置 */
const reset = () => {
  Object.assign(form, {
    id: undefined,
    userId: undefined,
    nickname: undefined,
    skuId: undefined,
    productName: undefined,
    productCode: undefined,
    specText: undefined,
    productPrice: undefined,
    categoryName: undefined,
    brandName: undefined,
    brandLogo: undefined,
    quantity: 1,
    selected: 1
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
  queryParams.userId = undefined
  queryParams.categoryId = undefined
  queryParams.storeId = undefined
  queryParams.selected = undefined
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
  title.value = '添加购物车记录'
}

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailShopCartApi(row.id)
    Object.assign(form, data)
    open.value = true
    title.value = '修改购物车记录'
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
        await updateShopCartApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopCartApi(form)
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
  ElMessageBox.confirm(`确认删除「${row.productName || '该商品'}」这条购物车记录吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopCartApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 条购物车记录吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopCartApi(selectedIds.value)
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
  getCategoryOptions()
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

/* 时间范围那一行（照抄商品、配送列表的 .search-row）：wrapper 独占整行把日期格顶到第二行，
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

.list-thumb {
  width: 50px;
  height: 50px;
  border-radius: 4px;
}
</style>
