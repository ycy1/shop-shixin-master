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

        <!-- 时间范围 + 按钮同一行（照抄购物车、收藏的 .search-row）：这一层 wrapper 独占第二行，
             里面两格都不等分、按控件本身的宽度从左排（样式见文件末尾）。
             筛的是「最近浏览时间」browse_time，不是入库时间 -->
          <el-form-item label="浏览时间" prop="dateRange">
            <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
              start-placeholder="开始日期" end-placeholder="结束日期" />
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
          <el-button v-permission="['shop:browse:add']" type="primary" plain icon="Plus"
            @click="handleAdd">记一次浏览</el-button>
          <el-button v-permission="['shop:browse:delete']" type="danger" plain icon="Delete"
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
        <el-table-column label="商品名称" prop="productName" align="center" min-width="180">
          <template #default="{ row }">
            <span>{{ row.productName || `商品已删除 #${row.productId}` }}</span>
          </template>
        </el-table-column>
        <el-table-column label="商品编码" prop="productCode" align="center" min-width="150">
          <template #default="{ row }">
            <span>{{ row.productCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="所属分类" prop="categoryName" align="center" width="120">
          <template #default="{ row }">
            <span>{{ row.categoryName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单价" align="center" width="100">
          <template #default="{ row }">
            <span v-if="row.productPrice != null">￥{{ Number(row.productPrice).toFixed(2) }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <!-- 两个时间不是一回事：browse_time 会被重复浏览刷新，create_time 是这条足迹第一次记上的时间 -->
        <el-table-column label="最近浏览时间" prop="browseTime" align="center" width="170">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.browseTime, 'YYYY-MM-DD HH:mm') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="首次记录" prop="createTime" align="center" width="130">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.createTime, 'YYYY-MM-DD') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="220" fixed="right">
          <template #default="scope">
            <el-button v-permission="['shop:browse:detail']" type="primary" link icon="View"
              @click="handleDetail(scope.row)">详情</el-button>
            <el-button v-permission="['shop:browse:update']" type="primary" link icon="Edit"
              @click="handleUpdate(scope.row)">修改</el-button>
            <el-button v-permission="['shop:browse:delete']" type="danger" link icon="Delete"
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
          <el-form-item label="商品" prop="productId">
            <el-select v-model="form.productId" placeholder="输入名称/编码搜索商品" filterable remote clearable
              :remote-method="searchProducts" :loading="productLoading" style="width: 100%"
              @change="handleProductChange">
              <el-option v-for="item in productSelectOptions" :key="item.id" :label="item.productName"
                :value="item.id">
                <div class="product-option">
                  <span class="product-option__name">{{ item.productName }}</span>
                  <span class="product-option__code">{{ item.productCode }}</span>
                  <span class="product-option__price">{{ priceText(item.productPrice) }}</span>
                </div>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="浏览时间" prop="browseTime">
            <el-date-picker v-model="form.browseTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="留空按当前时间记" style="width: 100%" />
          </el-form-item>
          <!-- 单价/分类只是回显：本表不存这几列，列表和这里都是查询时按 product_id 现联商品表带出来的 -->
          <el-form-item label="单价">
            <span v-if="form.productPrice != null">{{ priceText(form.productPrice) }}</span>
            <span v-else>-</span>
          </el-form-item>
          <el-form-item label="所属分类">
            <span>{{ form.categoryName || '-' }}</span>
          </el-form-item>
          <!-- 新增等于"记一次浏览"：这个消费者这件商品如果已经有足迹（含已删除的），后端只把浏览时间刷成上面填的值，
               不会插出第二条，所以这里不提示重复（见 ShopConsumerBrowseServiceImpl#insert） -->
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="cancel">取 消</el-button>
            <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
          </div>
        </template>
      </el-dialog>

      <!-- 详情弹窗：只读展示，字段全部来自详情接口（联表带出的商品名/单价/分类在列表那一行也有，
           但按 id 再查一次才不会受列表分页和字段裁剪影响） -->
      <el-dialog v-model="detailOpen" title="浏览足迹详情" width="640px" append-to-body destroy-on-close
        v-loading="detailLoading">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="消费者">
            {{ detail.nickname || detail.username || `已删除用户 #${detail.userId}` }}
          </el-descriptions-item>
          <el-descriptions-item label="消费者ID">{{ detail.userId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="商品名称">{{ detail.productName || `商品已删除 #${detail.productId}` }}</el-descriptions-item>
          <el-descriptions-item label="商品编码">{{ detail.productCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="所属分类">{{ detail.categoryName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="单价">
            {{ detail.productPrice != null ? priceText(detail.productPrice) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="最近浏览时间">{{ validate.formatTime(detail.browseTime) }}</el-descriptions-item>
          <el-descriptions-item label="首次记录">
            {{ validate.formatTime(detail.createTime, 'YYYY-MM-DD') }}
          </el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ validate.formatTime(detail.updateTime) }}</el-descriptions-item>
          <el-descriptions-item label="足迹ID">{{ detail.id ?? '-' }}</el-descriptions-item>
        </el-descriptions>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="detailOpen = false">关 闭</el-button>
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
  listShopBrowseApi,
  detailShopBrowseApi,
  addShopBrowseApi,
  updateShopBrowseApi,
  deleteShopBrowseApi
} from '@/api/consumer/browse'
import { listShopProductApi } from '@/api/shop/product'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import validate from '@/utils/validate'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
/**
 * 当前登录的是否消费者账号（字典 account_type 的 3）。只用来决定显不显消费者筛选那一格，
 * 真正的数据范围在后端：ShopConsumerBrowseServiceImpl#selectPage 会把 userId 覆盖成登录 id。
 */
const isConsumerAccount = computed(() => userStore.user.accountType === 3)

const loading = ref(true)
const submitLoading = ref(false)
const selectedIds = ref<any[]>([])
const total = ref(0)
const dataList = ref<any[]>([])
const title = ref('')
const open = ref(false)

// 详情弹窗（只读）
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<any>({})

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// 查询参数
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  userId: undefined
})
const dateRange = ref<string[]>([])

// 表单参数
const form = reactive<any>({})

const rules = reactive<FormRules>({
  userId: [{ required: true, message: '请选择消费者', trigger: 'change' }],
  productId: [{ required: true, message: '请选择商品', trigger: 'change' }]
})

// 商品下拉：远程按关键字搜（一次只取前 20 条），写法同购物车
const productOptions = ref<any[]>([])
const productLoading = ref(false)

// 修改回显时选中的商品往往不在搜索结果里（已下架、已删除），el-select 会直接显示裸 id，
// 所以只在展示层用详情接口带回的名称补一条，不改动 productOptions 本身
const productSelectOptions = computed(() => {
  const id = form.productId
  if (!id) return productOptions.value
  if (productOptions.value.some((item) => String(item.id) === String(id))) return productOptions.value
  return [
    {
      id,
      productName: form.productName || `商品已删除 #${id}`,
      productCode: form.productCode
    },
    ...productOptions.value
  ]
})

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
    const { data } = await listShopBrowseApi(params)
    dataList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
  } finally {
    loading.value = false
  }
}

/** 商品下拉的远程搜索 */
const searchProducts = async (keyword: string) => {
  productLoading.value = true
  try {
    const { data } = await listShopProductApi({ pageNum: 1, pageSize: 20, keyword: keyword || undefined })
    productOptions.value = data.records || []
  } catch (error) {
  } finally {
    productLoading.value = false
  }
}

/** 选中消费者时把昵称带回来，UserSelect 的输入框才知道该显示什么名字 */
const handleConsumerSelect = (user: any) => {
  form.nickname = user?.nickname || user?.username
}

/**
 * 选完商品把单价、所属分类抄到表单上：两格都是只读回显（本表不存这几列）。
 * 只查 productOptions：回显补的那条合成记录只有名称/编码，没有这些字段，查到它会把手上已有的值清掉。
 */
const handleProductChange = (id: any) => {
  const product = id ? productOptions.value.find((item: any) => String(item.id) === String(id)) : undefined
  form.productPrice = product?.productPrice
  form.categoryName = product?.categoryName
}

/** 表单重置 */
const reset = () => {
  Object.assign(form, {
    id: undefined,
    userId: undefined,
    nickname: undefined,
    productId: undefined,
    productName: undefined,
    productCode: undefined,
    productPrice: undefined,
    categoryName: undefined,
    browseTime: undefined
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
  title.value = '记一次浏览'
  searchProducts('')
}

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailShopBrowseApi(row.id)
    Object.assign(form, data)
    open.value = true
    title.value = '修改浏览足迹'
    searchProducts('')
  } catch (error) {
  }
}

/** 详情：列表那一行只带了列表要展示的几列，进弹窗按 id 再查一次全字段 */
const handleDetail = async (row: any) => {
  detail.value = {}
  detailOpen.value = true
  detailLoading.value = true
  try {
    const { data } = await detailShopBrowseApi(row.id)
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
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateShopBrowseApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopBrowseApi(form)
        ElMessage.success('已记录')
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
  ElMessageBox.confirm(`确认删除「${row.productName || '该商品'}」这条浏览足迹吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopBrowseApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 条浏览足迹吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopBrowseApi(selectedIds.value)
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

/* 时间范围那一行（照抄购物车、收藏的 .search-row）：wrapper 独占整行把日期格顶到第二行，
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

/* 候选项一行：名称在左、编码和单价靠右，写法同购物车的商品下拉 */
.product-option {
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
