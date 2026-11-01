<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="潮玩名称" prop="trendyName">
          <el-input v-model="queryParams.trendyName" placeholder="请输入潮玩名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
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
          <el-button v-permission="['shop:trendy:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['shop:trendy:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="封面" align="center" width="90">
          <template #default="{ row }">
            <el-image v-if="row.coverImage" :src="row.coverImage" :preview-src-list="[row.coverImage]"
              preview-teleported fit="contain" style="width: 50px; height: 50px; border-radius: 4px" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="潮玩名称" prop="trendyName" align="center" min-width="160" />
        <el-table-column label="说明" prop="description" align="center" min-width="260">
          <template #default="{ row }">
            <span>{{ row.description || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="绑定商品" prop="productCount" align="center" width="100">
          <template #default="{ row }">
            <span>{{ row.productCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="集齐奖励" align="center" min-width="170">
          <template #default="{ row }">
            <!-- 类型给标签、值给人看的名字（券名/商品名，服务层按当页一次回填）：只报一串 id 没人认得 -->
            <span v-if="!row.rewardType || !row.rewardValue">-</span>
            <template v-else>
              <el-tag :type="dict.tag(DICT_REWARD, row.rewardType)" size="small">
                {{ dict.label(DICT_REWARD, row.rewardType) }}
              </el-tag>
              <span> {{ row.rewardText || row.rewardValue }}</span>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" align="center" width="170">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.createTime, 'YYYY-MM-DD') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="340" fixed="right">
          <template #default="scope">
            <el-button type="info" link icon="Histogram" @click="handleCollect(scope.row)">集齐进度</el-button>
            <!-- 一件商品都没绑的时候没有可看的，直接把按钮收掉，不给点了弹一句空话（口径同门店那棵「招商信息」） -->
            <el-button v-if="scope.row.productCount" type="info" link icon="Goods"
              @click="handleProducts(scope.row)">绑定商品</el-button>
            <el-button v-permission="['shop:trendy:update']" type="primary" link icon="Edit"
              @click="handleUpdate(scope.row)">修改</el-button>
            <el-button v-permission="['shop:trendy:delete']" type="danger" link icon="Delete"
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
        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="潮玩名称" prop="trendyName">
                <el-input v-model="form.trendyName" placeholder="请输入潮玩名称" maxlength="200" show-word-limit />
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
          <el-row :gutter="20">
            <el-col :span="12">
              <!-- 这一格和「潮玩IP」列表那棵「绑定系列」改的是同一个字段（shop_trendy.ip_id），两个入口谁后保存谁生效。
                   clearable 就是「不归属任何IP」：这一列在服务层标了 IGNORED，清空能落库 -->
              <el-form-item label="所属IP" prop="ipId">
                <el-select v-model="form.ipId" placeholder="不归属任何IP" clearable filterable style="width: 100%">
                  <el-option v-for="item in ipOptions" :key="item.id" :label="item.ipName" :value="item.id" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="潮玩封面" prop="coverImage">
            <upload-image v-model="form.coverImage" :size="120" source="trendy" />
          </el-form-item>
          <el-form-item label="说明" prop="description">
            <el-input v-model="form.description" type="textarea" :rows="4" placeholder="选填" maxlength="500"
              show-word-limit />
          </el-form-item>

          <el-divider content-position="left">集齐奖励</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="奖励类型" prop="rewardType">
                <el-select v-model="form.rewardType" placeholder="请选择" style="width: 100%"
                  @change="handleRewardTypeChange">
                  <el-option v-for="item in dict.options(DICT_REWARD)" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <!-- 三档共用库里那一格（reward_value 是字符串：积分数 / 券ID / 商品ID），所以按类型各换一个控件写它 -->
              <el-form-item v-if="form.rewardType === 1" label="奖励积分" prop="rewardValue">
                <el-input-number v-model="rewardPoints" :min="1" :max="999999" controls-position="right"
                  placeholder="集齐给多少分" style="width: 100%" />
              </el-form-item>
              <el-form-item v-else-if="form.rewardType === 2" label="奖励券" prop="rewardValue">
                <el-select v-model="rewardCouponId" filterable placeholder="选择要发出的优惠券" style="width: 100%">
                  <el-option v-for="item in couponChoices" :key="item.id" :label="item.couponName" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item v-else-if="form.rewardType === 3" label="实物商品" prop="rewardValue">
                <shop-product-select v-model="rewardProductId" :label="rewardProductName" title="选择实物奖品"
                  @select="handleRewardProduct" @clear="handleRewardProductClear" />
              </el-form-item>
            </el-col>
          </el-row>
          <!-- 判据是"这条系列下的规格全抽到过"，跨所有盲盒、空奖不算；发的那一笔落在「集齐领取记录」，一人一条系列只领一次 -->
          <div v-if="form.rewardType === 3" class="tip">
            实物那一档系统发不出货，兑现的是这件商品的一张代金券 —— 先在「优惠券」里给它配一张「指定商品」的券，否则保存会被拦下
          </div>
          <div v-else-if="form.rewardType" class="tip">
            这个人抽齐这条系列下的全部规格时（跨所有盲盒、空奖不算），由定时任务发通知并兑现这一笔，一人一条系列只领一次
          </div>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="cancel">取 消</el-button>
            <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
          </div>
        </template>
      </el-dialog>

      <!-- 绑定商品：这一层只列「已绑到这条系列」的商品，操作栏只给解绑；
           要加绑从头部那颗「绑定」进第二层（未绑过任何系列的候选）。
           两层共用商品列表那条查询（/shop/product/list），只是各带 sectionId / noTrendy 一种筛法 -->
      <el-dialog v-model="productsOpen" width="1100px" append-to-body destroy-on-close>
        <template #header>
          <span class="el-dialog__title">绑定商品{{ productsTitle ? ` - ${productsTitle}` : '' }}</span>
        </template>
        <el-form :model="productQuery" inline class="search-form">
          <el-form-item label="关键字">
            <el-input v-model="productQuery.keyword" placeholder="商品名称/编码" clearable
              @keyup.enter="handleProductQuery" />
          </el-form-item>
          <el-form-item label="分类">
            <shop-category-select v-model="productQuery.categoryId" :data="categoryOptions" placeholder="全部分类" />
          </el-form-item>
          <el-form-item label="品牌">
            <el-select v-model="productQuery.brandId" placeholder="全部品牌" clearable filterable>
              <el-option v-for="item in brandOptions" :key="item.id" :label="item.brandName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="门店">
            <el-select v-model="productQuery.storeId" placeholder="全部门店" clearable filterable>
              <el-option v-for="item in storeOptions" :key="item.id" :label="item.storeName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleProductQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetProductQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <!-- 加绑从这一颗进公共商品弹窗；批量解绑要先在表里勾行（和绑定一样放在列表头顶那一排） -->
        <ButtonGroup class="table-toolbar">
          <el-button type="primary" plain icon="Plus" @click="openPick">绑定</el-button>
          <el-button type="danger" plain icon="Remove" :disabled="mainSelection.length === 0"
            @click="handleBatchUnbind">批量解绑</el-button>
        </ButtonGroup>
        <el-table ref="mainTableRef" v-loading="productsLoading" :data="productList" row-key="id"
          show-overflow-tooltip max-height="420" @selection-change="handleMainSelectionChange">
          <!-- reserve-selection：翻页、换筛选都不掉已勾的行，所以 selection-change 抛的是跨页累积的完整集合 -->
          <el-table-column type="selection" width="45" reserve-selection />
          <el-table-column label="主图" align="center" width="80">
            <template #default="{ row }">
              <el-image v-if="row.mainImage" :src="row.mainImage" :preview-src-list="[row.mainImage]"
                preview-teleported fit="contain" style="width: 44px; height: 44px; border-radius: 4px" />
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="商品名称" prop="productName" align="center" min-width="180" />
          <el-table-column label="商品编码" prop="productCode" align="center" min-width="170" />
          <el-table-column label="分类" align="center" width="110">
            <template #default="{ row }">
              <span>{{ row.categoryName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="品牌" align="center" width="110">
            <template #default="{ row }">
              <span>{{ row.brandName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="门店" align="center" width="120">
            <template #default="{ row }">
              <span>{{ row.storeName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="90" fixed="right">
            <template #default="scope">
              <el-button type="warning" link icon="Remove" :loading="pickSubmitting"
                @click="handleUnbind(scope.row)">解绑</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-container">
          <el-pagination background v-model:current-page="productQuery.pageNum"
            v-model:page-size="productQuery.pageSize" :page-sizes="[10, 20, 30, 50]" :total="productTotal"
            layout="total, sizes, prev, pager, next, jumper" @size-change="handleProductSizeChange"
            @current-change="getProducts" />
        </div>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="productsOpen = false">关 闭</el-button>
          </div>
        </template>
      </el-dialog>

      <!-- 候选那一层直接用公共的商品选择弹窗（多选 + 底部确定）：
           trigger=false 只要它的弹窗、不要那颗输入框，由上面那排「绑定」按钮开；
           query 压一条 noTrendy=true，候选就只剩还没绑过任何系列的商品 -->
      <ShopProductSelect ref="pickRef" v-model="pickIds" multiple :trigger="false" :store-filter="true"
        :query="{ noTrendy: true }" title="选择要绑定的商品" @select="handlePickConfirm" />

      <!-- 集齐进度：这条系列下抽到过规格的人，一人一行（离集齐最近的排最前）。
           两个数都在服务端现数：分子是他名下抽取记录里的规格种类数，分母是这条系列当前的规格总数 ——
           都不存列，存了就要有写入方回头刷它，而现在唯一的写入方是那条定时任务。
           点开某一行铺开这条系列的全部规格，一格上标出他抽到过没有（还缺的就是没抽到的那几格） -->
      <el-dialog v-model="collectOpen" width="1000px" append-to-body destroy-on-close>
        <template #header>
          <span class="el-dialog__title">集齐进度{{ collectTitle ? ` - ${collectTitle}` : '' }}</span>
        </template>
        <el-form :model="collectQuery" inline class="search-form">
          <el-form-item label="用户">
            <el-input v-model="collectQuery.keyword" placeholder="昵称/账号/手机号" clearable
              @keyup.enter="handleCollectQuery" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleCollectQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetCollectQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <el-table v-loading="collectLoading" :data="collectList" row-key="userId" show-overflow-tooltip
          max-height="420" @expand-change="handleCollectExpand">
          <el-table-column type="expand">
            <template #default="{ row }">
              <div v-loading="itemLoading[String(row.userId)]" class="collect-items">
                <p class="collect-summary">
                  这条系列共 {{ row.totalSku }} 格规格，他已抽到 {{ row.collectedSku }} 格{{ collectGapText(row) }}
                </p>
                <div v-for="item in (itemMap[String(row.userId)] || [])" :key="item.skuId" class="collect-item"
                  :class="{ 'is-missing': !item.drawnTime }">
                  <el-image v-if="item.skuImage" :src="item.skuImage" :preview-src-list="[item.skuImage]"
                    preview-teleported fit="cover" class="collect-thumb" />
                  <div v-else class="collect-thumb collect-thumb-empty">无图</div>
                  <p class="collect-name">{{ item.productName }}</p>
                  <p class="collect-spec">{{ item.specText || '默认规格' }}</p>
                  <el-tag :type="item.drawnTime ? 'success' : 'info'" size="small">
                    {{ item.drawnTime ? '已抽到' : '还缺' }}
                  </el-tag>
                  <p v-if="item.drawnTime" class="collect-time">{{ item.drawnTime }}</p>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="用户" align="center" min-width="140">
            <template #default="{ row }">
              <span>{{ row.userName || `用户 #${row.userId}` }}</span>
            </template>
          </el-table-column>
          <el-table-column label="手机号" align="center" width="140">
            <template #default="{ row }">
              <span>{{ row.mobile || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="集齐进度" align="center" width="110">
            <template #default="{ row }">
              <span>{{ row.collectedSku }} / {{ row.totalSku }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" align="center" width="100">
            <template #default="{ row }">
              <!-- 已领 = 领取记录那一行在（分奖励是那条定时任务发的，一人一条系列只一行）；
                   抽齐了却还没领，是任务还没跑到、或这条系列的奖励配得不完整，那一格说清是哪一种 -->
              <el-tag v-if="row.recordId" type="success" size="small">已领奖励</el-tag>
              <el-tag v-else-if="row.totalSku && row.collectedSku >= row.totalSku" type="warning" size="small">
                待发放
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="集齐时间" align="center" width="170">
            <template #default="{ row }">
              <span>{{ row.collectTime || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="兑现说明" align="center" min-width="230">
            <template #default="{ row }">
              <span>{{ row.rewardDesc || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-container">
          <el-pagination background v-model:current-page="collectQuery.pageNum"
            v-model:page-size="collectQuery.pageSize" :page-sizes="[10, 20, 30, 50]" :total="collectTotal"
            layout="total, sizes, prev, pager, next, jumper" @size-change="handleCollectSizeChange"
            @current-change="getCollectList" />
        </div>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="collectOpen = false">关 闭</el-button>
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
  listShopTrendyApi,
  detailShopTrendyApi,
  addShopTrendyApi,
  updateShopTrendyApi,
  deleteShopTrendyApi,
  bindTrendyProductsApi,
  listTrendyCollectApi,
  listTrendyCollectItemsApi
} from '@/api/trendy/trendy'
import { listShopProductApi } from '@/api/shop/product'
import { listShopCouponApi } from '@/api/activity/coupon'
import { getCategoryTreeApi } from '@/api/shop/category'
import { optionsShopBrandApi } from '@/api/shop/brand'
import { optionsShopStoreApi } from '@/api/store/store'
import { optionsShopIpApi } from '@/api/trendy/ip'
import { useDict } from '@/utils/dictCache'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import ShopCategorySelect from '@/components/ShopCategorySelect/index.vue'
import ShopProductSelect from '@/components/ShopProductSelect/index.vue'
import UploadImage from '@/components/Upload/Image.vue'
import validate from '@/utils/validate'

/** 集齐奖励类型那一档的字典（库里存的还是原始数字，只有这一本字典给人看标签） */
const DICT_REWARD = 'trendy_reward_type'
const dict = useDict(DICT_REWARD)

const loading = ref(true)
const submitLoading = ref(false)
const selectedIds = ref<any[]>([])
const total = ref(0)
const dataList = ref<any[]>([])
const title = ref('')
const open = ref(false)

/** 第一层：这条系列名下已绑的商品（只能解绑） */
const productsOpen = ref(false)
const productsLoading = ref(false)
const productsTitle = ref('')
const currentTrendyId = ref<number>()
const productList = ref<any[]>([])
const productTotal = ref(0)
const productQuery = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  categoryId: undefined,
  brandId: undefined,
  storeId: undefined,
  /** 打开面板时钉成当前这条系列（发到后端是商品查询的 sectionId），筛选项里没有它，用户改不掉 */
  sectionId: undefined
})

/** 第二层交给公共的商品选择弹窗（多选 + 底部确定），这里只留它的 ref 和选中值 */
const pickRef = ref<any>()
const pickIds = ref<number[]>([])
const pickSubmitting = ref(false)

/** 第一层勾中的行（批量解绑用）；表格实例留着是为了提交完能清掉勾选 */
const mainTableRef = ref<any>()
const mainSelection = ref<any[]>([])

const handleMainSelectionChange = (rows: any[]) => {
  mainSelection.value = rows
}

/** 提交完（绑定/解绑都算）清掉勾选：行已经换了一批，留着旧勾选会指向不在列表里的行 */
const clearMainSelection = () => {
  mainTableRef.value?.clearSelection()
  mainSelection.value = []
}

/* ---------------- 集齐进度那一弹窗 ---------------- */
const collectOpen = ref(false)
const collectLoading = ref(false)
const collectTitle = ref('')
const collectList = ref<any[]>([])
const collectTotal = ref(0)
const collectQuery = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  /** 打开弹窗时钉成当前这条系列，筛选项里没有它，用户改不掉（口径同绑定商品面板那格 sectionId） */
  trendyId: undefined,
  keyword: undefined
})

/**
 * 展开某一行才现捞那个人的款式明细，按 userId 存着（同一个人这页里只捞一次）。
 * 不给整页十个人预先各发一趟：一屏通常只点开一个，其余那九趟是白跑的（公网库一趟一个来回）。
 */
const itemMap = reactive<Record<string, any[]>>({})
const itemLoading = reactive<Record<string, boolean>>({})

const getCollectList = async () => {
  collectLoading.value = true
  try {
    const { data } = await listTrendyCollectApi(collectQuery)
    collectList.value = data.records || []
    collectTotal.value = data.total || 0
    // 换页、换筛选就把上一批展开的明细丢掉：行已经不是那几行了，留着会点开来张旧格子
    Object.keys(itemMap).forEach(key => delete itemMap[key])
  } catch (error) {
  } finally {
    collectLoading.value = false
  }
}

const handleCollect = (row: any) => {
  collectTitle.value = row.trendyName
  collectQuery.trendyId = row.id
  collectQuery.keyword = undefined
  collectQuery.pageNum = 1
  collectOpen.value = true
  getCollectList()
}

const handleCollectQuery = () => {
  collectQuery.pageNum = 1
  getCollectList()
}

const handleCollectSizeChange = () => {
  collectQuery.pageNum = 1
  getCollectList()
}

const resetCollectQuery = () => {
  collectQuery.keyword = undefined
  handleCollectQuery()
}

/** 展开谁就捞谁（折叠那一趟不做任何事，所以进来先认「这人手上已经有格子了」就回） */
const handleCollectExpand = async (row: any) => {
  const key = String(row.userId)
  if (itemMap[key]) {
    return
  }
  itemLoading[key] = true
  try {
    const { data } = await listTrendyCollectItemsApi({ trendyId: collectQuery.trendyId, userId: row.userId })
    itemMap[key] = data || []
  } catch (error) {
  } finally {
    itemLoading[key] = false
  }
}

/** 展开那一行顶上的一句话：还缺几格就说缺几格，一个都不缺才说抽齐 */
const collectGapText = (row: any) => {
  const gap = (row.totalSku || 0) - (row.collectedSku || 0)
  return gap > 0 ? `，还缺 ${gap} 格` : '，已经抽齐'
}

/** 筛选项的候选：分类树 / 启用中的品牌 / 启用中的门店，都是商品页那三份现成接口 */
const categoryOptions = ref<any[]>([])
const brandOptions = ref<any[]>([])
const storeOptions = ref<any[]>([])
/** 表单那格「所属IP」的候选：启用中的IP，进页面一次拉齐（和上面三排同一次 Promise.all） */
const ipOptions = ref<any[]>([])

const getProducts = async () => {
  productsLoading.value = true
  try {
    const { data } = await listShopProductApi(productQuery)
    productList.value = data.records || []
    productTotal.value = data.total || 0
  } catch (error) {
  } finally {
    productsLoading.value = false
  }
}

/** 打开某一条潮玩的绑定面板：第一层就是它名下那些（排序沿用商品列表那一条：上架在前、sort、id） */
const handleProducts = (row: any) => {
  currentTrendyId.value = row.id
  productsTitle.value = row.trendyName
  productQuery.sectionId = row.id
  clearMainSelection()
  productsOpen.value = true
  handleProductQuery()
}

const handleProductQuery = () => {
  productQuery.pageNum = 1
  getProducts()
}

const handleProductSizeChange = () => {
  productQuery.pageNum = 1
  getProducts()
}

/** 只清筛选项，不动那格 trendyId（它不是给用户改的） */
const resetProductQuery = () => {
  productQuery.keyword = undefined
  productQuery.categoryId = undefined
  productQuery.brandId = undefined
  productQuery.storeId = undefined
  handleProductQuery()
}

/** 头部那排「绑定」：开公共商品弹窗（它自己带 noTrendy=true 的候选） */
const openPick = () => {
  pickIds.value = []
  pickRef.value?.openDialog()
}

/** 批量绑定/解绑都走这一条：trendyId 传当前这条系列，解绑就传 null */
const submitBind = async (productIds: number[], trendyId: number | null) => {
  pickSubmitting.value = true
  try {
    await bindTrendyProductsApi({ productIds, trendyId })
    ElMessage.success(trendyId ? `已绑定 ${productIds.length} 件商品` : `已解绑 ${productIds.length} 件商品`)
    clearMainSelection()
    await getProducts()
    // 外面那一列的计数和那颗按钮的显隐都跟着计数走，所以顺手重拉一次
    getList()
  } catch (error) {
  } finally {
    pickSubmitting.value = false
  }
}

/** 公共弹窗底部「确定」抛上来的整行数组（多选档），直接拿这批 id 绑到当前系列 */
const handlePickConfirm = (rows: any[]) => {
  if (!rows?.length) return
  void submitBind(rows.map((row: any) => row.id), currentTrendyId.value ?? null)
}

const handleUnbind = (row: any) => void submitBind([row.id], null)

/** 批量解绑：勾中的那几行一次撤掉，先确认一句（和列表那棵「批量删除」同形） */
const handleBatchUnbind = () => {
  const ids = mainSelection.value.map((row: any) => row.id);
  ElMessageBox.confirm(`确认解绑选中的 ${ids.length} 件商品吗？（只是解除和这条系列的绑定，商品本身不动）`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    void submitBind(ids, null)
  }).catch(() => { })
}

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// 查询参数
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  trendyName: undefined,
  status: undefined
})

// 表单参数
const form = reactive<any>({})

const rules = reactive<FormRules>({
  trendyName: [{ required: true, message: '潮玩名称不能为空', trigger: 'blur' }],
  rewardType: [{ required: true, message: '请选择集齐奖励类型', trigger: 'change' }],
  rewardValue: [{ validator: validateRewardValue, trigger: 'change' }]
})

/* ---------------- 集齐奖励：三档共用库里那一格 reward_value（一串数字的文本） ---------------- */
/** 券候选：和「徽章管理」那页同一份口径，取第一页那 200 张（券建得晚不在里面的，下面用列表带回来的名字补一项） */
const couponOptions = ref<any[]>([])
/** 实物那一档只把 id 交给公共商品弹窗，名字由父组件留着给它回显（本组件自己不查详情） */
const rewardProductName = ref('')

const getCouponOptions = async () => {
  try {
    const { data } = await listShopCouponApi({ pageNum: 1, pageSize: 200 })
    couponOptions.value = data.records || []
  } catch (error) {
  }
}

/** 库里那一格是字符串，el-input-number 要的是数字，所以这一档只做一个读写翻译 */
const rewardPoints = computed<number | undefined>({
  get: () => (form.rewardType === 1 && form.rewardValue ? Number(form.rewardValue) : undefined),
  set: (value) => {
    form.rewardValue = value === undefined || value === null ? '' : String(value)
  }
})

const rewardCouponId = computed<number | undefined>({
  get: () => (form.rewardType === 2 && form.rewardValue ? Number(form.rewardValue) : undefined),
  set: (value) => {
    form.rewardValue = value ? String(value) : ''
  }
})

const rewardProductId = computed<number | undefined>({
  get: () => (form.rewardType === 3 && form.rewardValue ? Number(form.rewardValue) : undefined),
  set: (value) => {
    form.rewardValue = value ? String(value) : ''
    if (!value) rewardProductName.value = ''
  }
})

/** 下拉是全等匹配，候选里没有那一张时（不在第一页、或者已经被删了）拿列表回填的那个名字补一项，别只显示一串 id */
const couponChoices = computed<any[]>(() => {
  const id = rewardCouponId.value
  if (!id || couponOptions.value.some((item: any) => String(item.id) === String(id))) {
    return couponOptions.value
  }
  return [{ id, couponName: form.rewardText || `券 #${id}` }, ...couponOptions.value]
})

const handleRewardProduct = (product: any) => {
  rewardProductName.value = product?.productName || ''
}

const handleRewardProductClear = () => {
  rewardProductName.value = ''
  form.rewardValue = ''
}

/** 换类型就把上一档留下的那个值清掉：那一格是字符串，留着「3」再切到实物，就变成"发编号 3 的那件商品" */
const handleRewardTypeChange = () => {
  form.rewardValue = ''
  rewardProductName.value = ''
}

/** 三档各说各的空值（同一条规则挂在同一个 prop 上，报错文案按当前类型换） */
function validateRewardValue(_rule: any, _value: any, callback: any) {
  if (!form.rewardType) {
    return callback(new Error('请先选奖励类型'))
  }
  const text = String(form.rewardValue ?? '').trim()
  // 三档落进那一格的都该是一个正整数（积分数 / 券模板 id / 商品 id），服务层那一条也是这么解析的
  if (!/^[1-9]\d*$/.test(text)) {
    return callback(new Error(form.rewardType === 1 ? '请填写集齐给多少积分'
      : form.rewardType === 2 ? '请选择要发出的优惠券' : '请选择实物商品'))
  }
  return callback()
}

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listShopTrendyApi(queryParams)
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
    trendyName: undefined,
    ipId: undefined,
    coverImage: undefined,
    description: undefined,
    status: 1,
    // 类型给字典里 is_default=1 那一档（积分），奖励值留空由必填那条规则拦着，不给一个"看着像配好了"的假值
    rewardType: dict.defaultValue(DICT_REWARD, 1),
    rewardValue: '',
    rewardText: undefined
  })
  rewardProductName.value = ''
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
  queryParams.trendyName = undefined
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
  title.value = '添加潮玩系列'
}

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailShopTrendyApi(row.id)
    Object.assign(form, data)
    // 详情那条查询不回填奖励的名字（那是列表按当页一次查的），所以拿行上现成的名字补两笔：
    // 实物那格要它当商品名回显，券那格要它兜住"这张券不在候选第一页里"的情况
    form.rewardText = row.rewardText
    if (data.rewardType === 3) {
      rewardProductName.value = row.rewardText || ''
    }
    open.value = true
    title.value = '修改潮玩系列'
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
        await updateShopTrendyApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopTrendyApi(form)
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
  ElMessageBox.confirm(`确认删除潮玩系列「${row.trendyName}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopTrendyApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个潮玩系列吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopTrendyApi(selectedIds.value)
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

/** 绑定面板两层筛选项的候选（分类树 / 启用中的品牌 / 启用中的门店）、表单那格「所属IP」和「奖励券」的候选，进页面一次拉齐（口径同商品页） */
/** 一份一个 try：某一趟挂了不该把另外两排筛选项也清空（商品页那三颗候选也是各拉各的） */
const getFilterOptions = async () => {
  const fill = async (p: Promise<any>, put: (v: any[]) => void) => {
    try {
      const { data } = await p
      put(data || [])
    } catch (error) {
    }
  }
  await Promise.all([
    fill(getCategoryTreeApi({}), v => (categoryOptions.value = v)),
    fill(optionsShopBrandApi(), v => (brandOptions.value = v)),
    fill(optionsShopStoreApi(), v => (storeOptions.value = v)),
    fill(optionsShopIpApi(), v => (ipOptions.value = v)),
    getCouponOptions()
  ])
}

onMounted(() => {
  getList()
  getFilterOptions()
})
</script>

<style scoped lang="scss">
/* 集齐奖励那两格下面的一行说明（照商品规格弹窗那一句的样式） */
.tip {
  margin: -6px 0 8px;
  font-size: 12px;
  color: #909399;
}

/* 集齐进度：展开某一行之后铺开的那一排格子（一格一个规格，抽没抽到过看 is-missing） */
.collect-items {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-start;
  padding: 8px 16px 12px;
}

.collect-summary {
  width: 100%;
  margin: 0;
  font-size: 12px;
  color: #909399;
}

.collect-item {
  width: 112px;
  padding: 8px;
  text-align: center;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;

  /* 还缺的那几格压暗一点：这一屏要回答的是"差哪几个"，先让缺的跳出来 */
  &.is-missing {
    background: var(--el-fill-color-light);
    opacity: 0.66;
  }
}

.collect-thumb {
  display: block;
  width: 64px;
  height: 64px;
  margin: 0 auto;
  border-radius: 4px;
}

.collect-thumb-empty {
  line-height: 64px;
  font-size: 12px;
  color: #c0c4cc;
  background: var(--el-fill-color-light);
}

.collect-name {
  margin: 6px 0 0;
  font-size: 12px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.collect-spec {
  margin: 2px 0 6px;
  font-size: 12px;
  color: #909399;
}

.collect-time {
  margin: 4px 0 0;
  font-size: 11px;
  color: #c0c4cc;
}

/* 弹窗里表格上方的那一排操作（绑定）：和筛选项之间留一道缝 */
.table-toolbar {
  margin-bottom: 12px;
}

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
</style>
