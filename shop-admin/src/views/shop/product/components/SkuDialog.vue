<template>
  <!-- 商品页那颗「SKU管理」按钮：管这一件商品名下的规格（添加/修改/删除都在这儿）。
       单价/重量/预警/积分/能不能单卖都在这儿，不在「修改商品」里 —— 那些是「一个规格一个数」的列 -->
  <el-dialog v-model="visible" :title="`SKU管理 - ${product.productName || ''}`" width="1120px" append-to-body
    destroy-on-close>
    <!-- 商品信息只读回显：规格挂在哪件商品下改不了，这一行只用来确认"我开的是哪件商品的规格" -->
    <el-descriptions :column="4" size="small" border class="product-brief">
      <el-descriptions-item label="商品编码">{{ product.productCode || '-' }}</el-descriptions-item>
      <el-descriptions-item label="所属分类">{{ product.categoryName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="上架状态">
        <el-tag :type="product.status === 1 ? 'success' : 'info'">{{ product.status === 1 ? '上架' : '下架' }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="已声明规格">
        {{ declaredSpecIds.length ? `已选 ${declaredSpecIds.length} 项` : '未声明' }}
      </el-descriptions-item>
    </el-descriptions>

    <!-- 改的是下面这张表的内容，按钮按项目口径放在它上面单独一行 -->
    <ButtonGroup class="sku-toolbar">
      <el-button v-permission="['shop:product:addSku']" type="primary" plain icon="Plus"
        @click="handleAdd">添加SKU</el-button>
    </ButtonGroup>

    <el-table v-loading="loading" :data="skuList" show-overflow-tooltip border size="small">
      <el-table-column label="规格图" align="center" width="72" :show-overflow-tooltip="false">
        <template #default="{ row }">
          <!-- 格子里只画组首那一张，点开查看器左右翻完整一组（和商品列表的轮播图列同一画法）；
               这一条规格一张图都没配才沿用商品主图 —— 那也就是 sku_image 为 NULL 的意思，下单快照同口径 -->
          <el-image v-if="row.imageUrls.length" :src="row.imageUrls[0]" :preview-src-list="row.imageUrls"
            preview-teleported fit="cover" class="list-thumb" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="SKU编码" prop="skuCode" align="center"/>
      <el-table-column label="规格" align="center" min-width="120">
        <template #default="{ row }">
          <span>{{ row.specText || '默认规格' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="单价" align="center" width="100">
        <template #default="{ row }">
          <span v-if="row.productPrice != null">￥{{ Number(row.productPrice).toFixed(2) }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <!-- <el-table-column label="单件重量" align="center" width="100">
        <template #default="{ row }">
          <span v-if="row.weight != null">{{ Number(row.weight).toFixed(2) }} kg</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="安全库存" prop="safetyStock" align="center" width="90">
        <template #default="{ row }">
          <span>{{ row.safetyStock ?? 0 }}</span>
        </template>
      </el-table-column> -->
      <el-table-column label="可用库存" prop="availableStock" align="center" width="90">
        <template #default="{ row }">
          <span>{{ row.availableStock ?? 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="成本价" align="center" width="100">
        <template #default="{ row }">
          <span>{{ row.costPrice == null ? '-' : `￥${Number(row.costPrice).toFixed(2)}` }}</span>
        </template>
      </el-table-column>
      <el-table-column label="销量" prop="salesCount" align="center" width="80">
        <template #default="{ row }">
          <span>{{ row.salesCount ?? 0 }}</span>
        </template>
      </el-table-column>
      <!-- <el-table-column label="可销售" align="center" width="80">
        <template #default="{ row }">
          <el-tag :type="row.saleEnable === 1 ? 'success' : 'info'">{{ row.saleEnable === 1 ? '可销售' : '停售' }}</el-tag>
        </template>
      </el-table-column> -->
      <el-table-column label="操作" align="center" width="150" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="['shop:product:updateSku']" type="primary" link icon="Edit"
            @click="handleUpdate(row)">修改</el-button>
          <el-button v-permission="['shop:product:deleteSku']" type="danger" link icon="Delete"
            @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="tip">
      单价、重量、库存预警、积分和「可销售」都跟着具体规格走；可用库存、成本价和销量在这一页只看不能改。
      一件商品至少要留一条规格，最后一条不给删。
    </div>

    <!-- 添加/修改 SKU：嵌套在「SKU管理」上面一层，和商品表单里那颗「新增规格」同一套做法 -->
    <el-dialog v-model="formOpen" :title="skuForm.id ? '修改SKU' : '添加SKU'" width="820px" append-to-body
      destroy-on-close>
      <el-form ref="skuFormRef" :model="skuForm" :rules="skuRules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="SKU编码" prop="skuCode">
              <!-- 编码由后端生成（ShopProductSkuServiceImpl#insert 走 GeneratorNoUtils.getSkuNo），这里只回显 -->
              <el-input v-model="skuForm.skuCode" placeholder="保存后由系统生成" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序" prop="sort">
              <el-input-number v-model="skuForm.sort" :min="0" :step="1" controls-position="right"
                style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="规格" prop="specIds">
              <!-- 和商品表单那一格同一套做法：下拉 + 右边的「新增规格」。
                   在这儿新建的规格会自动并进这件商品的已声明规格（后端 fillFromSpecs 补 product_specs），
                   不用先出去「修改商品」里勾一遍再回来 -->
              <div class="spec-field">
                <el-select v-model="specIds" multiple collapse-tags collapse-tags-tooltip
                  :disabled="!product.categoryId"
                  :placeholder="declaredSpecOptions.length ? '不选就是默认规格（无规格款）' : '还没有可选的规格，点右边新增'">
                  <el-option v-for="item in declaredSpecOptions" :key="item.id" :label="specLabel(item)" :value="item.id" />
                </el-select>
                <el-button v-permission="['shop:spec:add']" type="primary" plain icon="Plus"
                  :disabled="!product.categoryId" @click="openSpecDialog">新增规格</el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单价" prop="productPrice">
              <el-input-number v-model="skuForm.productPrice" :min="0" :precision="2" :step="1"
                controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <!-- 留空 = 没称过：统一下单汇总这一单重量时会跳过它，凑 0 会算出"这单不要重量费"的假结论 -->
            <el-form-item label="单件重量" prop="weight">
              <el-input-number v-model="skuForm.weight" :min="0" :precision="2" :step="0.1" controls-position="right"
                style="width: 100%" placeholder="单位 kg，留空表示未称重" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="规格图" prop="skuImages">
              <!-- 和商品那颗「轮播图」是同一个公共组件：这一档规格自己的一组图，第一张当封面 -->
              <image-list v-model="skuForm.skuImages" :limit="9" source="product" />
              <span class="field-tip">一张都不传就沿用商品主图；第一张同时是这一档的封面（列表缩略图、购物车、单据明细都取它）</span>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="详情图" prop="detailImages">
              <!-- 前台商品详情页「商品详情」那一大块从上往下铺的就是这一组（一般是长图）：
                   只按顺序展示，不参与上面的轮播、也不派生封面，所以这里没有「第一张当封面」那句话说 -->
              <image-list v-model="skuForm.detailImages" :limit="30" source="product"
                placeholder="图片标题（选填）" />
              <span class="field-tip">这一档规格自己的图文详情，一条都不传前台就显示「暂无图文详情」</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">库存与销量</el-divider>
        <!-- 这三个数只给看不给改：可用库存和成本价是 shop_erp_inventory「这条规格 × 这个仓」那一行算出来的
             （库存由入库/出库动作写、成本价加权平均，在商品库存那页改），销量由支付回调按规格累加，
             表单这一格是从列表那行原样带过来的回显。新增时这一条还没入库、也没卖过，整排不出现 -->
        <el-row v-if="skuForm.id" :gutter="20">
          <el-col :span="8">
            <el-form-item label="可用库存" label-width="90px">
              <span class="static-value">{{ skuForm.availableStock ?? 0 }} 件</span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="成本价" label-width="90px">
              <span class="static-value">
                {{ skuForm.costPrice == null ? '未入账' : `￥${Number(skuForm.costPrice).toFixed(2)}` }}
              </span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="销量" label-width="90px">
              <span class="static-value">{{ skuForm.salesCount ?? 0 }} 件</span>
            </el-form-item>
          </el-col>
        </el-row>
        <p v-else class="tip">可用库存、成本价和销量要等这一条规格建好、入过库才会在这里出现。</p>

        <el-divider content-position="left">库存预警</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="安全库存" prop="safetyStock" label-width="90px">
              <el-input-number v-model="skuForm.safetyStock" :min="0" :step="1" controls-position="right"
                style="width: 100%" placeholder="低于此值预警" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最大库存" prop="maxStock" label-width="90px">
              <el-input-number v-model="skuForm.maxStock" :min="0" :step="1" controls-position="right"
                style="width: 100%" placeholder="留空不设上限" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="预警开关" prop="stockWarnEnable" label-width="90px">
              <el-switch v-model="skuForm.stockWarnEnable" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">销售与积分</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <!-- 和商品的上架/下架是两个维度：这一格关掉只是这一条规格下不了单，前台照样看得到这件商品 -->
            <el-form-item label="可销售" prop="saleEnable" label-width="90px">
              <el-switch v-model="skuForm.saleEnable" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="可得积分" prop="gainPoints" label-width="90px">
              <el-input-number v-model="skuForm.gainPoints" :min="0" :step="1" controls-position="right"
                style="width: 100%" placeholder="每件，0 为不发" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="兑换积分" prop="exchangePoints" label-width="90px">
              <el-input-number v-model="skuForm.exchangePoints" :min="0" :step="1" controls-position="right"
                style="width: 100%" placeholder="每件，0 为不可兑" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="formOpen = false">取 消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 新增规格：和商品表单里那颗同名按钮同一张表单，类目固定成这件商品的分类（规格是按分类出候选的） -->
    <el-dialog v-model="specOpen" title="新增规格" width="480px" append-to-body destroy-on-close>
      <el-form ref="specFormRef" :model="specForm" :rules="specRules" label-width="90px">
        <el-form-item label="所属类目">
          <el-input :model-value="product.categoryName" disabled />
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
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  skusByProductApi,
  addShopProductSkuApi,
  updateShopProductSkuApi,
  deleteShopProductSkuApi
} from '@/api/shop/productSku'
import { optionsShopSpecApi, addShopSpecApi } from '@/api/shop/spec'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import ImageList from './ImageList.vue'
import { parseImageJson, toImageUrls } from '@/utils/imageJson'

const props = defineProps<{
  /** 商品列表那一行：只用它的 id/编码/名称/分类/主图/上下架，和 productSpecs（这件商品声明了哪几组规格值） */
  product: any
}>()

const visible = defineModel<boolean>({ required: true })

const loading = ref(false)
const submitLoading = ref(false)
const skuList = ref<any[]>([])

const formOpen = ref(false)
const skuFormRef = ref<FormInstance>()
const skuForm = reactive<any>({})
// 规格下拉选中的 id，提交时拼成逗号串给 specIds（后端会升序去重，{红,L} 和 {L,红} 算同一条）
const specIds = ref<number[]>([])

const skuRules = reactive<FormRules>({
  // 单价是唯一必填项：后端 validate 也是从这一格起判，没价的那条规格进不了交易链
  productPrice: [{ required: true, message: '请填写单价', trigger: 'blur' }]
})

const specLabel = (item: any) => {
  const unit = item.specUnit ? `(${item.specUnit})` : ''
  return item.specValue ? `${item.specName}-${item.specValue}${unit}` : `${item.specName}${unit}`
}

/**
 * 这件商品已声明的规格值 id：「修改商品」里勾的那几串，加上这一弹窗里现建的那几项。
 * 做成响应式的一份而不是直接读 props —— 商品页只在点按钮时才换 product，弹窗里新建的规格并不会回头写
 * shop_product.product_specs 到列表行上（那是提交 SKU 时才由后端补的），本地不记一笔就选不中它。
 */
const declaredSpecIds = ref<number[]>([])

const syncDeclaredSpecIds = () => {
  const raw = props.product?.productSpecs
  declaredSpecIds.value = raw ? String(raw).split(',').filter(Boolean).map(Number) : []
}

// 候选只列这件商品声明过的那几组：前台详情页是按 product_specs 列可选规格的，
// SKU 挂一个没声明的规格值，页面上就点不到它（后端会在提交时把缺的补进商品那一列）
const declaredSpecOptions = ref<any[]>([])

// 新增规格弹窗（表单和商品页那颗同名按钮共用同一份字段口径）
const specOpen = ref(false)
const specSubmitLoading = ref(false)
const specFormRef = ref<FormInstance>()
const specForm = reactive<any>({ specName: '', specValue: '', specUnit: '' })
const specRules = reactive<FormRules>({
  specName: [{ required: true, message: '规格名称不能为空', trigger: 'blur' }]
})

const loadSpecOptions = async () => {
  const ids = declaredSpecIds.value
  const categoryId = props.product?.categoryId
  if (!ids.length || !categoryId) {
    declaredSpecOptions.value = []
    return
  }
  try {
    const { data } = await optionsShopSpecApi(categoryId)
    declaredSpecOptions.value = (data || []).filter((item: any) => ids.includes(item.id))
  } catch (error) {
    declaredSpecOptions.value = []
  }
}

/** 打开新增规格弹窗：规格挂在这一件商品的分类下，所以先把分类定下来 */
const openSpecDialog = () => {
  if (!props.product?.categoryId) {
    ElMessage.warning('这件商品还没有分类，先去修改商品里选一个')
    return
  }
  Object.assign(specForm, { specName: '', specValue: '', specUnit: '' })
  specOpen.value = true
}

/** 建完直接勾上：新规格进已声明那一串、重拉一遍候选，再落到当前这条 SKU 的选中值里 */
const submitSpecForm = async () => {
  const valid = await specFormRef.value?.validate().catch(() => false)
  if (!valid) return
  specSubmitLoading.value = true
  try {
    const { data } = await addShopSpecApi({
      categoryId: props.product?.categoryId,
      specName: specForm.specName,
      specValue: specForm.specValue || undefined,
      specUnit: specForm.specUnit || undefined,
      status: 1
    })
    ElMessage.success('新增规格成功')
    specOpen.value = false
    if (data != null && !declaredSpecIds.value.includes(data)) {
      declaredSpecIds.value.push(data)
    }
    await loadSpecOptions()
    if (data != null && !specIds.value.includes(data)) {
      specIds.value.push(data)
    }
  } catch (error) {
  } finally {
    specSubmitLoading.value = false
  }
}

const getList = async () => {
  if (!props.product?.id) return
  loading.value = true
  try {
    const { data } = await skusByProductApi(props.product.id)
    // sku_images 是 json 列，接口原样给回 JSON 字符串，这里拆成纯地址数组给缩略图和查看器用。
    // 一张规格图都没有（新数据那列是 []、老数据只有 sku_image 那一格）时退回商品主图那一张
    skuList.value = (data || []).map((row: any) => {
      const urls = toImageUrls(parseImageJson(row.skuImages))
      return { ...row, imageUrls: urls.length ? urls : [row.skuImage || row.mainImage].filter(Boolean) }
    })
    // 已声明那一串再并上「这条商品名下真用着的规格」：上一轮在本弹窗里新建的规格已经由后端写进了
    // shop_product.product_specs，但商品列表那一行拿的还是旧数据，不并进来的话下次打开选不到它
    skuList.value.forEach((row: any) => {
      String(row.specIds || '')
        .split(',')
        .filter(Boolean)
        .forEach((text: string) => {
          const id = Number(text)
          if (!Number.isNaN(id) && !declaredSpecIds.value.includes(id)) declaredSpecIds.value.push(id)
        })
    })
  } catch (error) {
    skuList.value = []
  } finally {
    loading.value = false
  }
}

const resetSkuForm = () => {
  Object.assign(skuForm, {
    id: undefined,
    productId: props.product?.id,
    skuCode: undefined,
    skuImages: undefined,
    detailImages: undefined,
    productPrice: undefined,
    weight: undefined,
    safetyStock: 0,
    maxStock: undefined,
    stockWarnEnable: 1,
    gainPoints: 0,
    exchangePoints: 0,
    saleEnable: 1,
    sort: undefined
  })
  specIds.value = []
  skuFormRef.value?.resetFields()
}

const handleAdd = () => {
  resetSkuForm()
  formOpen.value = true
}

const handleUpdate = (row: any) => {
  resetSkuForm()
  Object.assign(skuForm, row)
  specIds.value = row.specIds
    ? String(row.specIds).split(',').filter(Boolean).map(Number)
    : []
  formOpen.value = true
}

const submitForm = () => {
  skuFormRef.value?.validate(async (valid: any) => {
    if (!valid) return
    submitLoading.value = true
    try {
      const payload = { ...skuForm, productId: props.product?.id, specIds: specIds.value.join(',') }
      if (payload.id !== undefined) {
        await updateShopProductSkuApi(payload)
        ElMessage.success('修改成功')
      } else {
        await addShopProductSkuApi(payload)
        ElMessage.success('添加成功')
      }
      formOpen.value = false
      getList()
    } catch (error) {
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDelete = (row: any) => {
  ElMessageBox.confirm(
    `是否确认删除规格“${row.specText || '默认规格'}”?`,
    '警告',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  )
    .then(() => deleteShopProductSkuApi(row.id))
    .then(() => {
      getList()
      ElMessage.success('删除成功')
    })
    .catch(() => { })
}

// 弹窗是 destroy-on-close，但父组件只在点按钮时才换 product，所以每次打开重新捞一遍：
// 上一件商品的规格列表留着不刷的话，改完规格会看不到，删完也会又冒出来
watch(visible, async (open) => {
  if (!open) return
  // 先定「已声明」那一份，捞完（末尾会把各条 SKU 在用的规格并进来）再拉候选 —— 顺序反了候选就是空的
  syncDeclaredSpecIds()
  await getList()
  loadSpecOptions()
})
</script>

<style scoped lang="scss">
.list-thumb {
  width: 44px;
  height: 44px;
  border-radius: 4px;
  cursor: zoom-in;
}

.product-brief {
  margin-bottom: 12px;
}

.sku-toolbar {
  margin-bottom: 8px;
}

.tip {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

/* 规格下拉与"新增规格"按钮并排（同商品表单那一格） */
.spec-field {
  display: flex;
  gap: 8px;
  width: 100%;

  .el-select {
    flex: 1;
    min-width: 0;
  }
}

/* 规格图那一格下面那句说明，和上传框同排会挤，直接换行 */
.field-tip {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

/* 只读回显那一排：跟上面那些输入框同高对齐，字号比控件略小一档 */
.static-value {
  line-height: 32px;
  font-size: 13px;
  color: #303133;
}
</style>
