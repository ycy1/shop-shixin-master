<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="名称/编号" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="请输入门店名称/编号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="门店类型" prop="storeType">
          <el-select v-model="queryParams.storeType" placeholder="全部" clearable>
            <el-option v-for="item in dict.options('store_type')" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="营业状态" prop="businessStatus">
          <el-select v-model="queryParams.businessStatus" placeholder="全部" clearable>
            <el-option v-for="item in dict.options('store_business_status')" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
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
          <el-button v-permission="['shop:store:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['shop:store:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <!-- 表格级开了 show-overflow-tooltip，纯图片列要显式关掉，否则悬停会弹一个空 tooltip -->
        <el-table-column label="LOGO" align="center" width="90" :show-overflow-tooltip="false">
          <template #default="{ row }">
            <el-image v-if="row.logo" :src="row.logo" :preview-src-list="[row.logo]" preview-teleported fit="contain"
              style="width: 50px; height: 50px; border-radius: 4px" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="门店图片" align="center" width="110" :show-overflow-tooltip="false">
          <template #default="{ row }">
            <el-tooltip v-if="row.covers?.length" :content="`共 ${row.covers.length} 张`" placement="top">
              <!-- 只画首图，但 preview-src-list 给整组：点开后用查看器的左右箭头翻剩下的 -->
              <el-image :src="row.covers[0].url" :preview-src-list="row.coverUrls" :initial-index="0" preview-teleported
                fit="cover" class="cover-thumb" />
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="门店编码" prop="storeCode" align="center" min-width="120" />
        <el-table-column label="门店名称" prop="storeName" align="center" min-width="140" />
        <el-table-column label="类型" align="center" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag('store_type', row.storeType)">
              {{ dict.label('store_type', row.storeType) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 品牌名/LOGO 由服务层按 brand_id 回填（同店长那一格），没绑品牌或被停用删掉后查不到就显示 - -->
        <el-table-column label="绑定品牌" prop="brandName" align="center" min-width="140">
          <template #default="{ row }">
            <shop-brand-option v-if="row.brandName" :brand-name="row.brandName" :logo="row.brandLogo" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column label="店长" align="center" min-width="120">
          <template #default="{ row }">
            <span>{{ row.managerName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="营业状态" align="center" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag('store_business_status', row.businessStatus)">
              {{ dict.label('store_business_status', row.businessStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column label="门店地址" align="center" min-width="220">
          <template #default="{ row }">
            <span>{{ addressOf(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="240" fixed="right">
          <template #default="scope">
            <!-- 只有招商来的门店才有来源线索可看（列表 SQL 已带回 investment_id），
                 没关联的直接把按钮收掉，不给点了弹一句空话；权限沿用招商详情那一行 -->
            <el-button v-if="scope.row.investmentId" v-permission="['shop:investment:detail']"
              type="info" link icon="Document" @click="handleInvestment(scope.row)">招商信息</el-button>
            <el-button v-permission="['shop:store:update']" type="primary" link icon="Edit"
              @click="handleUpdate(scope.row)">修改</el-button>
            <el-button v-permission="['shop:store:delete']" type="danger" link icon="Delete"
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
              <el-form-item label="门店编码" prop="storeCode">
                <el-input v-model="form.storeCode" placeholder="保存后由系统生成" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="门店名称" prop="storeName">
                <el-input v-model="form.storeName" placeholder="请输入门店名称" maxlength="100" show-word-limit />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="门店类型" prop="storeType">
                <el-select v-model="form.storeType" placeholder="请选择" style="width: 100%">
                  <el-option v-for="item in dict.options('store_type')" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
          
            <el-col :span="12">
              <el-form-item label="店长" prop="managerId">
                <!-- 门店用户（自己就是某家店的店长）不给改店长，避免把店转到别人名下 -->
                <!-- 店长就是门店自己的登录账号：只列启用的门店账号（account_type=1），别的类型不列 -->
                <user-select v-model="form.managerId" :label="form.managerName" :disabled="isStoreUser" only-enabled
                  :account-type="1" placeholder="选择系统用户" @select="handleManagerSelect"
                  @clear="handleManagerClear" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="联系人" prop="contactName">
                <el-input v-model="form.contactName" placeholder="选填" maxlength="50" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="联系电话" prop="contactPhone">
                <el-input v-model="form.contactPhone" placeholder="选填" maxlength="20" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="绑定品牌" prop="brandId">
                <!-- 候选同商品表单那一格：启用中的品牌都给（不传分类就是全部），不绑就留空 -->
                <el-select v-model="form.brandId" placeholder="不绑定品牌" clearable filterable style="width: 100%">
                  <el-option v-for="item in brandSelectOptions" :key="item.id" :label="item.brandName"
                    :value="item.id">
                    <shop-brand-option :brand-name="item.brandName" :logo="item.logo" />
                  </el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="招商信息" prop="investmentId">
                <!-- 这家店来源的那条招商线索。候选由服务端按「有没有别的门店占着这条线索」筛过，
                     所以选得到的都是还没建过店的；本店自己已绑那条也在里面，回显得出来 -->
                <el-select v-model="form.investmentId" placeholder="不关联招商线索" clearable filterable
                  style="width: 100%">
                  <el-option v-for="item in investSelectOptions" :key="item.id" :label="investLabel(item)"
                    :value="item.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <!-- <el-col :span="12">
              <el-form-item label="排序" prop="sort">
                <el-input-number v-model="form.sort" :min="0" style="width: 100%" />
              </el-form-item>
            </el-col> -->
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
              <el-form-item label="详细地址" prop="address">
                <el-input v-model="form.address" placeholder="街道、门牌号" maxlength="255" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="营业状态" prop="businessStatus">
                <el-select v-model="form.businessStatus" placeholder="请选择" style="width: 100%">
                  <el-option v-for="item in dict.options('store_business_status')" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="营业时间" prop="businessHours">
                <!-- el-time-select 的下拉项就是整点/半点时刻；两端各存一个 HH:mm，
                     business_hours 里的 "09:00-22:00" 在提交时拼出来 -->
                <div class="pair-field">
                  <el-time-select v-model="hoursStart" start="00:00" end="23:30" step="00:30" clearable
                    placeholder="开始" @change="handleHoursStartChange" />
                  <span class="pair-split">-</span>
                  <el-time-select v-model="hoursEnd" :start="hoursEndStart" end="24:00" step="00:30"
                    :min-time="hoursStart" clearable placeholder="结束" />
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="是否配送" prop="isDelivery">
                <el-radio-group v-model="form.isDelivery">
                  <el-radio :value="1">是</el-radio>
                  <el-radio :value="0">否</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col v-if="form.isDelivery === 1" :span="12">
              <el-form-item label="配送半径" prop="deliveryRadius">
                <el-input-number v-model="form.deliveryRadius" :min="0" placeholder="单位：米" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col v-if="form.isDelivery === 1" :span="12">
              <el-form-item label="起送金额" prop="minDeliveryAmount">
                <el-input-number v-model="form.minDeliveryAmount" :min="0" :precision="2" style="width: 100%" />
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
          <el-divider content-position="left">图片信息</el-divider>
          <el-form-item label="门店LOGO" prop="logo">
            <upload-image v-model="form.logo" :size="120" source="store" />
          </el-form-item>
          <el-form-item label="门店图片" prop="coverImages">
            <!-- 存 [{"title":"","url":""}]，和商品轮播图同一套编辑器 -->
            <image-list v-model="form.coverImages" source="store" label-key="title" placeholder="图片说明" />
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="cancel">取 消</el-button>
            <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
          </div>
        </template>
      </el-dialog>

      <!--
        招商信息：这家店挂的是哪条招商线索 —— 只读看登记内容，要改线索本身去「门店招商」那页的「修改」，
        要换一条线索（或取消关联）在本页的「修改」弹窗里改那一格「招商信息」。
        一家门店只挂一条（关联记在门店行的 investment_id 上），所以这里只可能有一条。
        右上角「打印」把这条线索交给浏览器打印窗口，在那里选「另存为 PDF」就是 PDF。
      -->
      <el-dialog v-model="investOpen" width="900px" append-to-body destroy-on-close>
        <template #header>
          <div class="dialog-head">
            <span class="el-dialog__title">招商信息</span>
            <el-button v-if="invest.investmentNo" type="primary" plain size="small"
              @click="handleInvestmentPrint">打 印</el-button>
          </div>
        </template>
        <el-descriptions :column="2" border v-loading="investLoading">
          <el-descriptions-item label="所属门店">{{ invest.storeName }}</el-descriptions-item>
          <el-descriptions-item label="招商编号">{{ invest.investmentNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="负责专员">
            {{ invest.userName || (invest.userId ? `已删除用户 #${invest.userId}` : '未分配') }}
          </el-descriptions-item>
          <el-descriptions-item label="申请人">{{ invest.applicantName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ invest.applicantPhone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="意向类型">
            <el-tag size="small" :type="dict.tag(DICT_INTENT, invest.intentType)">
              {{ dict.label(DICT_INTENT, invest.intentType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="线索来源">
            <el-tag size="small" :type="dict.tag(DICT_SOURCE, invest.source)">
              {{ dict.label(DICT_SOURCE, invest.source) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="投资预算">{{ priceText(invest.budgetAmount) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="行业经验">{{ invest.hasExperience === 1 ? '有' : '无' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="dict.tag(DICT_STATUS, invest.status)">
              {{ dict.label(DICT_STATUS, invest.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="签约时间">{{ invest.signTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="过期时间">{{ invest.expiryTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="签约文件">
            <el-link v-if="invest.contractFile" type="primary" :href="invest.contractFile" target="_blank">
              查看文件
            </el-link>
            <span v-else>未上传</span>
          </el-descriptions-item>
          <el-descriptions-item label="意向地址" :span="2">{{ addressOf(invest) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ invest.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="登记时间">{{ invest.createTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ invest.updateTime || '-' }}</el-descriptions-item>
        </el-descriptions>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="investOpen = false">关 闭</el-button>
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
  listShopStoreApi,
  detailShopStoreApi,
  myShopStoresApi,
  addShopStoreApi,
  updateShopStoreApi,
  deleteShopStoreApi
} from '@/api/store/store'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import UploadImage from '@/components/Upload/Image.vue'
import ShopMap from '@/components/ShopMap/index.vue'
import ShopBrandOption from '@/components/ShopBrandOption/index.vue'
import { getUserDetailApi } from '@/api/system/user'
import { optionsShopBrandApi } from '@/api/shop/brand'
// 招商信息是另一张表（shop_store_investment）的数据，读接口挂在招商那个控制器下
import { investmentByStoreApi, optionsShopInvestmentApi } from '@/api/store/investment'
import { printInvestments } from '@/utils/print'
import { useUserStore } from '@/store/modules/user'
// 门店图片复用了商品轮播图那套 [{文字, url}] 编辑器；它已经是跨页面共用组件了，
// 后续可移到 src/components/Upload/ 下
import ImageList from '@/views/shop/product/components/ImageList.vue'
import validate from '@/utils/validate'
import { parseImageJson, toImageUrls } from '@/utils/imageJson'
import { useDict } from '@/utils/dictCache'
import { useColPickerData } from '@/composables/useColPickerData'

// 后面三个是「招商信息」弹窗要用的招商那三套枚举，不是本表列
const DICT_INTENT = 'invest_intent_type'
const DICT_SOURCE = 'invest_source'
const DICT_STATUS = 'invest_status'

const dict = useDict('store_type', 'store_business_status', DICT_INTENT, DICT_SOURCE, DICT_STATUS)

const userStore = useUserStore()

/** 地区数据源：@vant/area-data，级联的 value 就是行政区划代码 */
const { colPickerData, resolveAreaCode } = useColPickerData()
// 库里存的是 area_code（"110000,110100,110101"）+ 冗余的 area_zh，
// 级联选择器要的是数组，join / split 的互转在 reset、回显、提交三处做
const areaArr = ref<string[]>([])

// business_hours 存的是 "09:00-22:00" 这种文本，两端各一个时间下拉；
// 和地区一样不直接编辑 form，拼接 / 拆分也在 reset、回显、提交三处做
const hoursStart = ref('')
const hoursEnd = ref('')

// 结束下拉的起始项跟着开始时间走：一打开就停在可选区间的第一项，不用手动往下翻
const hoursEndStart = computed(() => hoursStart.value || '00:00')

/** 换了开始时间就清掉结束时间：留着一个比开始更早的时刻是非法组合，:min-time 只会把它置灰 */
const handleHoursStartChange = () => {
  hoursEnd.value = ''
}

/** 当前登录用户名下的门店；有记录说明这个账号是门店用户（店长），一家或多家都一样 */
const myStores = ref<any[]>([])
const isStoreUser = computed(() => myStores.value.length > 0)

const getMyStores = async () => {
  try {
    const { data } = await myShopStoresApi()
    myStores.value = data || []
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
  storeType: undefined,
  businessStatus: undefined,
  status: undefined
})

// 表单参数
const form = reactive<any>({})

const rules = reactive<FormRules>({
  storeName: [{ required: true, message: '门店名称不能为空', trigger: 'blur' }]
})

/** 地区优先用库里冗余的 area_zh，兜底才按 area_code 现场解析（老数据可能只有 code） */
const addressOf = (row: any) => {
  const area = row.areaZh || resolveAreaCode(row.areaCode)
  return [area, row.address].filter(Boolean).join(' ') || '-'
}

/** 金额口径同各列表页（后端返回的 decimal 是字符串） */
const priceText = (price: any) => (price == null || price === '' ? '' : `￥${Number(price).toFixed(2)}`)

/** 品牌候选：不传分类就是全部启用中的品牌（口径同商品表单那一格） */
const brandOptions = ref<any[]>([])
const getBrandOptions = async () => {
  try {
    const { data } = await optionsShopBrandApi()
    brandOptions.value = data || []
  } catch (error) {
  }
}

/**
 * 品牌下拉实际用的候选：options 只列启用中的，所以绑着停用品牌的老门店会选不出来。
 * 这一格不是本表的名称列、接口也不联表带回品牌名，兜底只能拿 id 顶着显示（同「已删除用户 #id」那一档）。
 */
const brandSelectOptions = computed(() => {
  const id = form.brandId
  if (!id) return brandOptions.value
  if (brandOptions.value.some((item: any) => String(item.id) === String(id))) return brandOptions.value
  return [{ id, brandName: `已停用品牌 #${id}`, logo: '' }, ...brandOptions.value]
})

/** 招商线索候选：每次打开弹窗重查，因为「哪条线索还没建过店」是会被别的门店抢走的 */
const investOptions = ref<any[]>([])
const getInvestOptions = async (storeId?: number) => {
  try {
    const { data } = await optionsShopInvestmentApi(storeId)
    investOptions.value = data || []
  } catch (error) {
  }
}

const investLabel = (item: any) => [item.investmentNo, item.applicantName].filter(Boolean).join(' · ')

/**
 * 招商下拉实际用的候选：本店已绑那条一般都在候选里（options 接口按 storeId 把它留着）。
 * 走到兜底这一档有两种：那条线索被删了，或它的状态后来改成了 2 已取消/3 已失效（候选只放行 0/1）。
 * 门店行上有编号快照，拿它顶一下别让下拉空着 —— form.investmentId 没动，保存仍是原样绑着。
 */
const investSelectOptions = computed(() => {
  const id = form.investmentId
  if (!id) return investOptions.value
  if (investOptions.value.some((item: any) => String(item.id) === String(id))) return investOptions.value
  return [{ id, investmentNo: form.investmentNo || `已删除线索 #${id}`, applicantName: '' }, ...investOptions.value]
})

const investOpen = ref(false)
const investLoading = ref(false)
const invest = ref<any>({})

/**
 * 看这一家店挂的那条招商线索：关联就钉在门店行上（investment_id，本页「修改」弹窗里那一格改得动），
 * 所以一次读接口就够，不用查列表。按钮已经按 row.investmentId 决定给不给看，
 * 这里还拿回来空只可能是那条线索事后被删了 —— 说清楚，别弹一个空表。
 */
const handleInvestment = async (row: any) => {
  invest.value = { storeName: row.storeName }
  investOpen.value = true
  investLoading.value = true
  try {
    const { data } = await investmentByStoreApi(row.id)
    if (!data) {
      ElMessage.warning('这家门店关联的招商线索已经被删除了')
      investOpen.value = false
      return
    }
    // 门店名是这一页的上下文，不是招商表的列，挂到行对象上一格给弹窗和打印器共用
    invest.value = { ...data, storeName: row.storeName }
  } catch (error) {
  } finally {
    investLoading.value = false
  }
}

/** 打印这条招商线索：弹窗里已经捞好的那份直接交给打印器 */
const handleInvestmentPrint = () => {
  printInvestments([invest.value], `招商信息 ${invest.value.investmentNo || ''} - ${invest.value.storeName || ''}`.trim())
}

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listShopStoreApi(queryParams)
    // cover_images 是 json 列，接口原样给回 JSON 字符串，这里解析成数组供图片列渲染
    dataList.value = (data.records || []).map((row: any) => {
      const items = parseImageJson(row.coverImages)
      return { ...row, covers: items, coverUrls: toImageUrls(items) }
    })
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
    storeCode: undefined,
    storeName: undefined,
    storeType: dict.defaultValue('store_type', 1),
    brandId: undefined,
    contactName: undefined,
    contactPhone: undefined,
    areaCode: undefined,
    areaZh: undefined,
    address: undefined,
    longitude: undefined,
    latitude: undefined,
    businessHours: undefined,
    businessStatus: dict.defaultValue('store_business_status', 1),
    isDelivery: 0,
    deliveryRadius: undefined,
    minDeliveryAmount: undefined,
    logo: undefined,
    coverImages: undefined,
    managerId: undefined,
    managerName: undefined,
    // 来源招商这两列表单里改得动，但「修改」是 Object.assign(form, data) 整行灌进来的；
    // reset 不点名清掉的话，改完一家招商来的门店再点「新增」，新门店就白捡上原来那条线索
    investmentId: undefined,
    investmentNo: undefined,
    sort: 0,
    status: 1
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
  queryParams.storeType = undefined
  queryParams.businessStatus = undefined
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

/**
 * 选中店长：昵称回填给店长那格，同时带出联系人/联系电话（店长一般就是联系人本人，
 * 口径同供应商页的绑定账号、收货地址页选消费者）。换人时以新选的人为准，直接覆盖。
 */
const handleManagerSelect = (user: any) => {
  const name = user.nickname || user.username
  form.managerName = name
  form.contactName = name
  form.contactPhone = user.mobile
}

/** 取消选择只清店长的回显：联系人/电话是已经录进去的业务数据，不跟着抹 */
const handleManagerClear = () => {
  form.managerName = undefined
}

/**
 * 门店用户自己新增：店长只能是自己，而那一格是禁用的、不会再触发 @select，所以联系人得在这里主动带一次。
 * 手机号不在 /api/auth/info 返回的登录信息里，只能按 id 再查一次用户详情（这个读接口不校验权限）。
 */
const fillSelfAsManager = async () => {
  const self = userStore.user as any
  if (!self.id) return
  form.managerId = self.id
  form.managerName = self.nickname || self.username
  try {
    const { data } = await getUserDetailApi(self.id)
    form.contactName = data.nickname || data.username || form.managerName
    form.contactPhone = data.mobile
  } catch (error) {
  }
}

/** 新增按钮操作 */
const handleAdd = async () => {
  reset()
  open.value = true
  title.value = '添加门店'
  // 候选每次打开都重查：别的新增/改绑随时可能把某条线索占走
  getInvestOptions()
  if (isStoreUser.value) await fillSelfAsManager()
}

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  reset()
  // 传本店 id：候选要把自己已经绑着那条留着，否则这一格打开是空的
  getInvestOptions(row.id)
  try {
    const { data } = await detailShopStoreApi(row.id)
    Object.assign(form, data)
    // area_code 是 "110000,110100,110101" 这种，split 回级联选择器要的数组
    areaArr.value = (data.areaCode || '').split(',').filter(Boolean)
    // business_hours 是 "09:00-22:00"，拆成两端时刻回填两个时间下拉
    const [start = '', end = ''] = String(data.businessHours || '').split('-')
    hoursStart.value = start.trim()
    hoursEnd.value = end.trim()
    open.value = true
    title.value = '修改门店'
  } catch (error) {
  }
}

/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    // 级联的 [省, 市, 区县] 存成一列 area_code，中文地名顺手解析出来存 area_zh（同用户管理）；
    // 清空时 area_code 是空串，这两列在实体上是 FieldStrategy.IGNORED，所以写得回库
    form.areaCode = (areaArr.value || []).join(',')
    form.areaZh = resolveAreaCode(form.areaCode)
    // 两端都选了才拼成 "09:00-22:00"；只选一头当没填，避免留下半截文本
    // businessHours 在实体上也是 FieldStrategy.IGNORED，所以空串写得回库、能清掉
    form.businessHours = hoursStart.value && hoursEnd.value ? `${hoursStart.value}-${hoursEnd.value}` : ''
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateShopStoreApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopStoreApi(form)
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
  ElMessageBox.confirm(`确认删除门店「${row.storeName}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopStoreApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个门店吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopStoreApi(selectedIds.value)
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
  getMyStores()
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

/* 一段拆成两格的字段（营业时间、经纬度）：两个输入等分整行，中间夹一个分隔符。
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

/* 尺寸和 LOGO 列对齐；cursor 是可继承属性，写在 el-image 根上就能作用到里面的 img */
.cover-thumb {
  width: 50px;
  height: 50px;
  border-radius: 4px;
  cursor: zoom-in;
}
</style>
