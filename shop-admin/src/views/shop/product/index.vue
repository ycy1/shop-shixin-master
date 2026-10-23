<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="名称/编码" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="商品名称/编码" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="商品分类" prop="categoryId" class="category-select">
          <shop-category-select v-model="queryParams.categoryId" :data="categoryOptions" placeholder="全部分类" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="0" />
          </el-select>
        </el-form-item>
        <div class="search-row">
          <el-form-item label="创建时间">
            <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
              start-placeholder="开始日期" end-placeholder="结束日期" />
          </el-form-item>
          <el-form-item class="search-btns">
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
          <el-button v-permission="['shop:product:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['shop:product:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip
        @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <!-- 表级的 show-overflow-tooltip 会被每一列继承，纯图片列要显式关掉，否则悬停弹一个空 tooltip -->
        <el-table-column label="主图" align="center" width="80" :show-overflow-tooltip="false">
          <template #default="{ row }">
            <el-image v-if="row.mainImage" :src="row.mainImage" :preview-src-list="[row.mainImage]"
              preview-teleported fit="cover" class="list-thumb" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="轮播图" align="center" width="110" :show-overflow-tooltip="false">
          <template #default="{ row }">
            <el-tooltip v-if="row.imageList?.length" :content="`共 ${row.imageList.length} 张`" placement="top">
              <!-- 只画首图，但 preview-src-list 给整组：点开后用查看器的左右箭头翻剩下的 -->
              <el-image :src="row.imageList[0].url" :preview-src-list="row.imageUrls" :initial-index="0"
                preview-teleported fit="cover" class="list-thumb" />
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="商品编码" prop="productCode" align="center" width="150" />
        <el-table-column label="商品名称" prop="productName" align="center" min-width="180" />
        <el-table-column label="所属分类" prop="categoryName" align="center" width="120">
          <template #default="{ row }">
            <span>{{ row.categoryName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="品牌" prop="brandName" align="center" min-width="200">
          <template #default="{ row }">
            <shop-brand-option v-if="row.brandName" :brand-name="row.brandName" :logo="row.brandLogo" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="所属门店" prop="storeName" align="center" min-width="140">
          <template #default="{ row }">
            <span>{{ row.storeName || '-' }}</span>
          </template>
        </el-table-column>
        <!-- 单价在规格那一侧，商品这一行只能给区间：名下只有一条规格时就是那一个数 -->
        <el-table-column label="单价" align="center" width="150">
          <template #default="{ row }">
            <span v-if="row.priceMin != null && row.priceMax != null && Number(row.priceMin) !== Number(row.priceMax)">
              ￥{{ Number(row.priceMin).toFixed(2) }} ~ ￥{{ Number(row.priceMax).toFixed(2) }}
            </span>
            <span v-else-if="row.priceMin != null">￥{{ Number(row.priceMin).toFixed(2) }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <!-- 销量、可用库存、成本价这三格商品这一行不给：它们记在规格（以及规格 × 仓库那一层的账）上，
             汇总成一件商品一个数会骗人。要看就在下面「SKU管理」弹窗里逐条规格看 -->
        <!-- <el-table-column label="排序" prop="sort" align="center" width="80" />
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column> -->
        <el-table-column label="创建时间" prop="createTime" align="center" width="170">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.createTime, 'YYYY-MM-DD') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="250" fixed="right">
          <template #default="scope">
            <!-- 打开这件商品的规格列表：新增/修改/删除 SKU 都在那一个弹窗里，单价/重量/预警/积分都在那几行上，商品表单不收（读接口不验权限，和商品列表同口径） -->
            <el-button type="primary" link icon="Coin" @click="handleSku(scope.row)">SKU管理</el-button>
            <el-button v-permission="['shop:product:update']" type="primary" link icon="Edit"
              :loading="updatingId === scope.row.id" @click="handleUpdate(scope.row)">修改</el-button>
            <el-button v-permission="['shop:product:delete']" type="danger" link icon="Delete"
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

      <!-- 添加或修改对话框：这一份只收「整件商品」的格，单价/重量/预警/积分那些一个规格一个数的，
           在操作列那颗「SKU管理」弹窗里逐条录 -->
      <el-dialog v-model="open" :title="title" width="820px" append-to-body destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="商品编码" prop="productCode">
                <!-- 编码由后端生成（ShopProductServiceImpl#insert 走 GeneratorNoUtils.getProductNo），这里只回显 -->
                <el-input v-model="form.productCode" placeholder="保存后由系统生成" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="商品分类" prop="categoryId">
                <shop-category-select v-model="form.categoryId" :data="categoryOptions" :clearable="false"
                  placeholder="请选择商品分类" style="width: 100%" @change="handleCategoryChange" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="品牌" prop="brandId">
                <el-select v-model="form.brandId" placeholder="不关联品牌" clearable filterable style="width: 100%">
                  <template #label>
                    <shop-brand-option v-if="selectedBrand" :brand-name="selectedBrand.brandName"
                      :logo="selectedBrand.logo" />
                  </template>
                  <el-option v-for="item in brandSelectOptions" :key="item.id" :label="item.brandName"
                    :value="item.id">
                    <shop-brand-option :brand-name="item.brandName" :logo="item.logo" />
                  </el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <!-- 打开这一档就必须选所属门店（文创商品是这家店自己的周边，服务层同样拦一道） -->
            <el-col :span="12">
              <el-form-item label="文创商品" prop="culturalEnable">
                <el-switch v-model="form.culturalEnable" :active-value="1" :inactive-value="0"
                  @change="handleCulturalChange" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="所属门店" prop="storeId">
                <el-select v-model="form.storeId" placeholder="不限定门店" clearable filterable
                  :disabled="storeSelectDisabled" style="width: 100%">
                  <el-option v-for="item in storeSelectOptions" :key="item.id" :label="item.storeName"
                    :value="item.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="排序" prop="sort">
                <el-input-number v-model="form.sort" :min="0" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="上架状态" prop="status">
                <el-radio-group v-model="form.status">
                  <el-radio :value="1">上架</el-radio>
                  <el-radio :value="0">下架</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <!-- 只管首页那个轮播位选不选这件商品（shop_product.carousel_enable）；
                 「这一条规格能不能下单」是另一档，在 SKU 弹窗里那条规格的「可销售」上 -->
            <el-col :span="12">
              <el-form-item label="首页轮播" prop="carouselEnable">
                <el-switch v-model="form.carouselEnable" :active-value="1" :inactive-value="0" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="商品名称" prop="productName">
                <el-input v-model="form.productName" placeholder="请输入商品名称" maxlength="200" show-word-limit />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="副标题" prop="subTitle">
                <el-input v-model="form.subTitle" placeholder="请输入副标题/卖点" maxlength="255" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="商品描述" prop="productDesc">
                <el-input v-model="form.productDesc" type="textarea" :rows="3" placeholder="请输入商品描述"
                  maxlength="255" show-word-limit />
              </el-form-item>
            </el-col>
            <!-- 这一格勾的是「这件商品用哪几组规格值开 SKU」（shop_product.product_specs）；
                 SKU 弹窗的规格下拉也从这一串出候选，在那边新建的规格由后端在提交 SKU 时顺手补进这一列 -->
            <el-col :span="24">
              <el-form-item label="规格" prop="specIds">
                <div class="spec-field">
                  <el-select v-model="specIds" multiple collapse-tags collapse-tags-tooltip placeholder="请选择规格"
                    :disabled="!form.categoryId">
                    <el-option v-for="item in specOptions" :key="item.id" :label="specLabel(item)" :value="item.id" />
                  </el-select>
                  <el-button v-permission="['shop:spec:add']" type="primary" plain icon="Plus"
                    :disabled="!form.categoryId" @click="openSpecDialog">新增规格</el-button>
                </div>
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">图片</el-divider>
          <el-form-item label="主图" prop="mainImage">
            <upload-image v-model="form.mainImage" :size="140" source="product" />
          </el-form-item>
          <el-form-item label="轮播图" prop="images">
            <image-list v-model="form.images" :limit="9" source="product" />
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="cancel">取 消</el-button>
            <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
          </div>
        </template>
      </el-dialog>

      <!-- 新增规格对话框：类目沿用商品表单当前选中的分类，只读展示 -->
      <el-dialog v-model="specOpen" title="新增规格" width="480px" append-to-body destroy-on-close>
        <el-form ref="specFormRef" :model="specForm" :rules="specRules" label-width="90px">
          <el-form-item label="所属类目">
            <el-input :model-value="currentCategoryName" disabled />
          </el-form-item>
          <el-form-item label="规格名称" prop="specName">
            <el-input v-model="specForm.specName" placeholder="如：颜色" maxlength="64" />
          </el-form-item>
          <el-form-item label="规格值" prop="specValue">
            <el-input v-model="specForm.specValue" placeholder="如：红色，可留空" maxlength="64" />
          </el-form-item>
          <el-form-item label="单位" prop="specUnit">
            <el-input v-model="specForm.specUnit" placeholder="如：种 / ml，可留空" maxlength="64" />
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="specOpen = false">取 消</el-button>
            <el-button type="primary" :loading="specSubmitLoading" @click="submitSpecForm">确 定</el-button>
          </div>
        </template>
      </el-dialog>

      <!-- 这件商品名下的规格：单价、重量、库存预警、积分、可销售都在这一张表里逐条录 -->
      <sku-dialog v-model="skuOpen" :product="skuProduct" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listShopProductApi,
  detailShopProductApi,
  addShopProductApi,
  updateShopProductApi,
  deleteShopProductApi
} from '@/api/shop/product'
import { getCategoryTreeApi } from '@/api/shop/category'
import { optionsShopSpecApi, addShopSpecApi } from '@/api/shop/spec'
import { optionsShopBrandApi } from '@/api/shop/brand'
import { optionsShopStoreApi, myShopStoresApi } from '@/api/store/store'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import ShopCategorySelect from '@/components/ShopCategorySelect/index.vue'
import ShopBrandOption from '@/components/ShopBrandOption/index.vue'
import UploadImage from '@/components/Upload/Image.vue'
import ImageList from './components/ImageList.vue'
import SkuDialog from './components/SkuDialog.vue'
import validate from '@/utils/validate'
import { parseImageJson, toImageUrls } from '@/utils/imageJson'

const loading = ref(true)
const submitLoading = ref(false)
const selectedIds = ref<any[]>([])
const total = ref(0)
const dataList = ref<any[]>([])
const title = ref('')
const open = ref(false)

// 操作列那颗「SKU管理」：弹窗只认这一行的 id 和它声明的那几组规格，每次点开重新指定
const skuOpen = ref(false)
const skuProduct = ref<any>({})

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// 查询参数
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  categoryId: undefined,
  status: undefined
})
const dateRange = ref<string[]>([])

// 表单参数
const form = reactive<any>({})
// 规格下拉选项与已选规格 id，提交时拼成逗号分隔字符串写入 product_specs
const specOptions = ref<any[]>([])
const specIds = ref<number[]>([])
// 内嵌的新增规格弹窗
const specOpen = ref(false)
const specSubmitLoading = ref(false)
const specFormRef = ref<FormInstance>()
const specForm = reactive<any>({ specName: '', specValue: '', specUnit: '' })
const specRules = reactive<FormRules>({
  specName: [{ required: true, message: '规格名称不能为空', trigger: 'blur' }]
})

// 分类树选项
const categoryOptions = ref<any[]>([])

// 品牌下拉选项（只取启用中的）
const brandOptions = ref<any[]>([])

// 商品关联的品牌后来被停用时不在启用列表里，el-select 会直接显示裸 id；
// 这里只在展示层把详情接口带回的名字补进去，不改动 brandOptions 本身
const brandSelectOptions = computed(() => {
  const id = form.brandId
  if (!id) return brandOptions.value
  if (brandOptions.value.some((item) => String(item.id) === String(id))) return brandOptions.value
  return [{ id, brandName: form.brandName || `已停用品牌 #${id}`, logo: form.brandLogo }, ...brandOptions.value]
})

// 选中态要连 LOGO 一起显示，而 #label 插槽只回传文字、回传不了选项对象，
// 所以只能自己按 form.brandId 回查选项
const selectedBrand = computed(() => {
  if (!form.brandId) return undefined
  return brandSelectOptions.value.find((item) => String(item.id) === String(form.brandId))
})

/** 门店下拉选项：普通账号取全部启用中的门店 */
const storeOptions = ref<any[]>([])
/** 当前登录用户担任店长的门店，可能一家也可能多家 */
const myStores = ref<any[]>([])

// 门店用户只在自己名下这几家里挑，别家的一概不列；名下只有一家时没有可挑的，直接禁用
const storeChoices = computed(() => (myStores.value.length ? myStores.value : storeOptions.value))
const storeSelectDisabled = computed(() => myStores.value.length === 1)

// 同品牌：商品绑的门店后来被停用时不在候选里，这里只用详情带回的名字补展示，不动候选本身
const storeSelectOptions = computed(() => {
  const id = form.storeId
  if (!id) return storeChoices.value
  if (storeChoices.value.some((item) => String(item.id) === String(id))) return storeChoices.value
  return [{ id, storeName: form.storeName || `已停用门店 #${id}` }, ...storeChoices.value]
})

const rules = computed<FormRules>(() => ({
  // 商品编码不在这里：由后端生成、输入框是禁用的（物流公司编码同口径），加了必填会让新增永远提交不了
  productName: [{ required: true, message: '商品名称不能为空', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择商品分类', trigger: 'change' }],
  // 文创商品必须挂一家店，普通商品仍然可以「不限定门店」—— 和服务层那一条判据同一口径
  storeId: form.culturalEnable === 1
    ? [{ required: true, message: '文创商品必须选择所属门店', trigger: 'change' }]
    : []
}))

/** 关掉文创这一档，「所属门店」退回非必填，先把它已经挂上的必填提示清掉 */
const handleCulturalChange = (value: any) => {
  if (Number(value) !== 1) formRef.value?.clearValidate(['storeId'])
}

const specLabel = (item: any) => {
  const unit = item.specUnit ? `(${item.specUnit})` : ''
  return item.specValue ? `${item.specName}-${item.specValue}${unit}` : `${item.specName}${unit}`
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
    const { data } = await listShopProductApi(params)
    // images 是 json 列，接口原样给回 JSON 字符串，这里解析成数组供轮播图列渲染
    dataList.value = (data.records || []).map((row: any) => {
      const imageList = parseImageJson(row.images)
      return { ...row, imageList, imageUrls: toImageUrls(imageList) }
    })
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

/** 加载品牌下拉：只取启用中的，传 categoryId 时按"该分类及其上级 + 未分类"过滤 */
const getBrandOptions = async (categoryId?: number) => {
  try {
    const { data } = await optionsShopBrandApi(categoryId)
    brandOptions.value = data || []
  } catch (error) {
  }
}

/** 加载门店下拉：只取启用中的，门店不随分类变化，所以进页面取一次就够 */
const getStoreOptions = async () => {
  try {
    const { data } = await optionsShopStoreApi()
    storeOptions.value = data || []
  } catch (error) {
  }
}

/** 当前登录用户担任店长的门店：一家时下拉被禁用，多家时在这些店里挑 */
const getMyStores = async () => {
  try {
    const { data } = await myShopStoresApi()
    myStores.value = data || []
  } catch (error) {
  }
}

/** 加载指定类目下的启用规格 */
const loadSpecOptions = async (categoryId?: number) => {
  if (!categoryId) {
    specOptions.value = []
    return
  }
  try {
    const { data } = await optionsShopSpecApi(categoryId)
    specOptions.value = data || []
  } catch (error) {
    specOptions.value = []
  }
}

/** 切换分类时重载规格和品牌候选，并剔除不属于新分类的已选项 */
const handleCategoryChange = async (categoryId: number) => {
  await Promise.all([loadSpecOptions(categoryId), getBrandOptions(categoryId)])
  const validIds = new Set(specOptions.value.map((item) => item.id))
  specIds.value = specIds.value.filter((id) => validIds.has(id))
  // 未分类的品牌后端会一并返回，所以落选的就是明确挂在别的分类下，留着只会提交出错位的关联
  if (form.brandId && !brandOptions.value.some((item) => String(item.id) === String(form.brandId))) {
    form.brandId = undefined
  }
}

/** 在类目树里按 id 递归找名称 */
const findCategoryName = (nodes: any[], id: any): string => {
  for (const node of nodes || []) {
    if (node.id === id) return node.categoryName
    const name = findCategoryName(node.children, id)
    if (name) return name
  }
  return ''
}

const currentCategoryName = computed(() => findCategoryName(categoryOptions.value, form.categoryId))

/** 打开新增规格弹窗，类目固定为商品表单当前选中的分类 */
const openSpecDialog = () => {
  if (!form.categoryId) {
    ElMessage.warning('请先选择商品分类')
    return
  }
  Object.assign(specForm, { specName: '', specValue: '', specUnit: '' })
  specOpen.value = true
}

/** 提交新增规格 */
const submitSpecForm = async () => {
  const valid = await specFormRef.value?.validate().catch(() => false)
  if (!valid) return
  specSubmitLoading.value = true
  try {
    const { data } = await addShopSpecApi({
      categoryId: form.categoryId,
      specName: specForm.specName,
      specValue: specForm.specValue || undefined,
      specUnit: specForm.specUnit || undefined,
      status: 1
    })
    ElMessage.success('新增规格成功')
    specOpen.value = false
    await loadSpecOptions(form.categoryId)
    // 新建的规格直接勾上，省得用户再去下拉里找一遍
    if (data != null && !specIds.value.includes(data)) {
      specIds.value.push(data)
    }
  } catch (error) {
  } finally {
    specSubmitLoading.value = false
  }
}

/** 表单重置：这一份只有「整件商品」的格，单价/重量/预警/积分/可销售在 SKU 弹窗里，不在这里 */
const reset = () => {
  Object.assign(form, {
    id: undefined,
    productCode: undefined,
    productName: undefined,
    subTitle: undefined,
    productDesc: undefined,
    categoryId: undefined,
    brandId: undefined,
    storeId: undefined,
    storeName: undefined,
    mainImage: undefined,
    productSpecs: undefined,
    images: undefined,
    sort: 0,
    carouselEnable: 0,
    culturalEnable: 0,
    status: 1
  })
  specIds.value = []
  specOptions.value = []
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
  queryParams.categoryId = undefined
  queryParams.status = undefined
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
  // 还没选分类，品牌候选回到全部
  getBrandOptions()
  // 门店用户的新商品归自己名下的店：先默认第一家（storeName 只给展示兜底用），
  // 只有一家时下拉被禁用，多家时仍可在这里改选
  if (myStores.value.length) {
    form.storeId = myStores.value[0].id
    form.storeName = myStores.value[0].storeName
  }
  open.value = true
  title.value = '添加商品'
}

/** 正在打开修改弹窗的那一行 id：取详情和候选是两次串行请求，按钮上转圈说明"在load不是在卡"，顺手挡住连点 */
const updatingId = ref<number | string>()

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  updatingId.value = row.id
  reset()
  try {
    const { data } = await detailShopProductApi(row.id)
    Object.assign(form, data)
    specIds.value = data.productSpecs
      ? String(data.productSpecs).split(',').filter(Boolean).map(Number)
      : []
    // 只重载候选、不清已选值：历史数据里品牌和分类对不上的商品，编辑别的字段时不该把品牌悄悄清掉
    await Promise.all([loadSpecOptions(data.categoryId), getBrandOptions(data.categoryId)])
    open.value = true
    title.value = '修改商品'
  } catch (error) {
  } finally {
    updatingId.value = undefined
  }
}

/** 「SKU管理」按钮：把这一行整份交给弹窗，它要 id、名称/编码/主图回显，也要 product_specs 决定能选哪几组规格 */
const handleSku = (row: any) => {
  skuProduct.value = row
  skuOpen.value = true
}

/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    submitLoading.value = true
    try {
      const payload = { ...form, productSpecs: specIds.value.join(',') }
      if (form.id !== undefined) {
        await updateShopProductApi(payload)
        ElMessage.success('修改成功')
      } else {
        await addShopProductApi(payload)
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

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  if (!selectedIds.value.length) return
  ElMessageBox.confirm(`是否确认删除选中的 ${selectedIds.value.length} 个商品?`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => deleteShopProductApi(selectedIds.value))
    .then(() => {
      getList()
      ElMessage.success('删除成功')
    })
    .catch(() => { })
}

/** 删除按钮操作 */
const handleDelete = (row: any) => {
  ElMessageBox.confirm(`是否确认删除商品“${row.productName}”?`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => deleteShopProductApi(row.id))
    .then(() => {
      getList()
      ElMessage.success('删除成功')
    })
    .catch(() => { })
}

const handleSizeChange = (val: any) => {
  queryParams.pageSize = val
  getList()
}

const handleCurrentChange = (val: any) => {
  queryParams.pageNum = val
  getList()
}

onMounted(() => {
  getList()
  getCategoryOptions()
  getBrandOptions()
  getStoreOptions()
  getMyStores()
})
</script>

<style scoped lang="scss">
/* 列表里的图片缩略图（主图 / 轮播图）；EP 不给 el-image 设指针样式，cursor 可继承，写在根上就进到 img */
.list-thumb {
  width: 50px;
  height: 50px;
  border-radius: 4px;
  cursor: zoom-in;
}

/* 规格下拉与"新增规格"按钮并排 */
.spec-field {
  display: flex;
  gap: 8px;
  width: 100%;

  .el-select {
    flex: 1;
    min-width: 0;
  }
}

/* 参照部门管理：筛选项等分整行，控件宽度统一由 CSS 控制 */
.search-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  .category-select{
    align-items: center;
  }

  :deep(.el-form-item) {
    flex: 1 1 0;
    min-width: 0;
    margin-right: 16px;
  }

  :deep(.el-form-item .el-form-item__content) {
    min-width: 0;
  }

  /* global.scss 里对 .el-input/.el-select/.el-date-editor 写死了 240px，特异性更高，只能用 !important 覆盖 */
  :deep(.el-form-item .el-input),
  :deep(.el-form-item .el-select),
  :deep(.el-form-item .el-tree-select),
  :deep(.el-form-item .el-date-editor) {
    width: 100% !important;
  }

  /* 时间范围单独一行，且不跟着等分——撑满整行会宽得离谱 */
  .search-row {
    flex: 0 0 100%;
    display: flex;
    flex-wrap: nowrap;

    :deep(.el-form-item) {
      flex: 0 0 auto;
    }

    /* 略宽于 Element Plus daterange 的默认 350px，够放下"开始日期 至 结束日期" */
    :deep(.el-form-item .el-date-editor) {
      width: 360px !important;
    }

    :deep(.el-form-item.search-btns) {
      margin-right: 0;
    }
  }
}
</style>
