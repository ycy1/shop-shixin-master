<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="请输入单号/门店/备注" clearable
            @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="销售门店" prop="storeId">
          <el-select v-model="queryParams.storeId" placeholder="全部" clearable filterable>
            <el-option v-for="item in storeOptions" :key="item.id" :label="item.storeName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>

        <!-- 时间范围 + 按钮同一行（照抄采购单那一页的 .search-row）：这一层 wrapper 独占第二行，
             里面两格都不等分、按控件本身的宽度从左排（样式见文件末尾） -->
        <div class="search-row">
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
          <el-button v-permission="['erp:sale:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['erp:sale:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <!-- 复选框只放"删得掉"的那些行（待审核 / 已取消）：批量删除走的是同一个服务端判据 -->
        <el-table-column type="selection" width="55" align="center" :selectable="canDelete" />
        <el-table-column label="销售单号" prop="saleNo" align="center" min-width="180" />
        <!-- 门店/仓库/操作员名称都是查询时联表现取的，改名字不用回头刷单子 -->
        <el-table-column label="销售门店" align="center" min-width="160">
          <template #default="{ row }">
            <span>{{ row.storeName || `已删除门店 #${row.storeId}` }}</span>
          </template>
        </el-table-column>
        <el-table-column label="总数量" prop="totalQuantity" align="center" width="100" />
        <el-table-column label="销售金额" align="center" width="130">
          <template #default="{ row }">
            <span>{{ priceText(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_TYPE, row.status)">
              {{ dict.label(DICT_TYPE, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预计发货" align="center" width="120">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.expectDate, 'YYYY-MM-DD') || '-' }}</span>
          </template>
        </el-table-column>
        <!-- 建单时可以不填（列注释写的就是"出库时确定"）：那一页建出库单时如果各行同为一个仓会顺手补上 -->
        <el-table-column label="发货仓库" align="center" min-width="140">
          <template #default="{ row }">
            <span>{{ row.warehouseName || '出库时确定' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="ERP操作员" align="center" min-width="120">
          <template #default="{ row }">
            <span>{{ row.userName || '未指派' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" align="center" width="170">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.createTime, 'YYYY-MM-DD') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="250" fixed="right">
          <template #default="scope">
            <!-- 出库单：这一张销售单名下的出库单都在这里，点行首箭头展开看那张单出了哪些商品。
                 待审核（0）/ 已取消（4）不给这个入口：前者还没批下来建不了出库单，后者名下必然没有
                 （形状照采购单页那颗「入库单」） -->
            <el-button v-if="canViewOutbound(scope.row)" v-permission="['erp:sale:outbound']" type="primary" link
              icon="Document" @click="handleOutbound(scope.row)">出库单</el-button>
            <!-- 只有待审核的单子能改（服务端同样拦）：「审核」就是在这里把状态选成已审核，
                 选完这张单就冻结了，要撤下来只能选「已取消」作废，之后既改不动也出不了货 -->
            <el-button v-if="canModify(scope.row)" v-permission="['erp:sale:update']" type="primary" link
              icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
            <!-- 删除比修改宽一档：已取消的那些也要删得掉（这两档名下必然没有出库单） -->
            <el-button v-if="canDelete(scope.row)" v-permission="['erp:sale:delete']" type="danger" link
              icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
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
      <el-dialog v-model="open" :title="title" width="960px" append-to-body destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
          <el-divider content-position="left">基本信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="销售单号" prop="saleNo">
                <el-input v-model="form.saleNo" placeholder="保存后由系统生成" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="销售门店" prop="storeId">
                <!-- 门店是这张单的口径线：明细候选按「属于这家门店 + 可销售 + 所选发货仓库有货」查，「发货仓库」那一格也只看这家门店名下的仓 -->
                <el-select v-model="form.storeId" placeholder="请选择销售门店" filterable style="width: 100%"
                  @change="handleStoreChange">
                  <el-option v-for="item in storeOptions" :key="item.id" :label="item.storeName" :value="item.id">
                    <div class="product-option">
                      <span class="product-option__name">{{ item.storeName }}</span>
                      <span class="product-option__code">{{ item.storeCode }}</span>
                    </div>
                  </el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="ERP操作员" prop="userId">
                <!-- ERP 账号自己新增时这一格填成本人并禁用（后端 insert 也会钉回登录 id，禁用只是防误操作）；
                     其它账号可自由指定/换绑/清空（不选就是还没指派）。联系人/电话按选中的操作员带出 -->
                <!-- 只列 ERP 操作员账号（account_type=5）；停用账号不列，别的账号类型也不列 -->
                <user-select v-model="form.userId" :label="form.userName" only-enabled :account-type="5"
                  :disabled="isErpAccount" style="width: 100%" @select="handleOperatorSelect"
                  @clear="handleOperatorClear" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="联系人" prop="contactName">
                <el-input v-model="form.contactName" placeholder="选填，选操作员后自动带出，可改" maxlength="50" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="联系电话" prop="contactPhone">
                <el-input v-model="form.contactPhone" placeholder="选填，选操作员后自动带出，可改" maxlength="20" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="发货仓库" prop="warehouseId">
                <!-- 仓库是挂在门店名下的，所以这一格的候选跟着上面的门店走（见 storeWarehouseOptions）；
                     这一格也是明细的门槛：不选仓不给挑商品，选了才按「该仓有可用库存 + 可销售」查候选（见 searchProducts） -->
                <el-select v-model="form.warehouseId"
                  :disabled="!form.storeId"
                  :placeholder="form.storeId ? '请选择发货仓库（明细按该仓有货的商品筛）' : '请先选择销售门店'"
                  clearable filterable style="width: 100%" @change="handleWarehouseChange">
                  <el-option v-for="item in storeWarehouseOptions" :key="item.id" :label="item.warehouseName"
                    :value="item.id">
                    <div class="product-option">
                      <span class="product-option__name">{{ item.warehouseName }}</span>
                      <span class="product-option__code">{{ item.warehouseCode }}</span>
                    </div>
                  </el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态" prop="status">
                <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
                  <el-option v-for="item in formStatusOptions" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="预计发货" prop="expectDate">
                <el-date-picker v-model="form.expectDate" type="date" value-format="YYYY-MM-DD" placeholder="可不选"
                  style="width: 100%" />
              </el-form-item>
            </el-col>

            <el-col :span="24">
              <el-form-item label="备注" prop="remark">
                <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" maxlength="500"
                  show-word-limit />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">销售明细</el-divider>
          <el-table :data="form.items" size="small" border>
            <!-- 一行是一个具体规格（候选来自 shop_product_sku），所以这一格选的是规格不是商品 -->
            <el-table-column label="商品规格" min-width="260">
              <template #default="{ row }">
                <el-select v-model="row.skuId" :disabled="!canPickProduct"
                  :placeholder="productPlaceholder" filterable remote
                  :reserve-keyword="false" :remote-method="searchProducts" :loading="productLoading"
                  style="width: 100%" @change="handleProductChange(row, $event)">
                  <el-option v-for="item in productOptions" :key="item.skuId" :label="optionLabel(item)"
                    :value="item.skuId">
                    <div class="product-option">
                      <span class="product-option__name">{{ item.name }}</span>
                      <span class="product-option__spec">{{ item.spec || '默认规格' }}</span>
                      <span class="product-option__code">{{ item.code }}</span>
                    </div>
                  </el-option>
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="销售数量" width="130" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="1" :controls="false" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="已出库" width="110" align="center">
              <template #default="{ row }">
                <!-- 只读回显：这一列只能由「商品出库」的出库动作累加，手填等于改出库进度（服务端也不认请求体里的值） -->
                <span :class="{ 'stock-done': row.shippedQuantity > 0 && row.shippedQuantity >= Number(row.quantity || 0) }">{{ row.shippedQuantity || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="销售单价" width="140" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.price" :min="0" :precision="2" :controls="false" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="小计" width="120" align="center">
              <template #default="{ row }">
                <span>{{ priceText(rowAmount(row)) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ $index }">
                <el-button type="danger" link icon="Delete" @click="removeItem($index)" />
              </template>
            </el-table-column>
          </el-table>
          <div class="items-footer">
            <el-button type="primary" plain icon="Plus" size="small" @click="addItem">添加明细</el-button>
            <!-- 合计只给录入时看，落库的 total_amount / total_quantity 一律由服务端按明细重算 -->
            <span class="items-total">共 {{ form.items.length }} 行 · 数量 {{ totalQuantity }} · 金额
              {{ priceText(totalAmount) }}</span>
          </div>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="cancel">取 消</el-button>
            <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
          </div>
        </template>
      </el-dialog>

      <!--
        这张销售单名下的出库单：只看不动，要登记出库/真正扣账去「商品出库」那页（形状照采购单那页的「入库单」）。
        右上角「打印」把这几张单连明细交给浏览器打印窗口（在那里选「另存为 PDF」就是 PDF）。
        明细由后端跟着出库单一次给全（见 listErpSaleOutboundBySaleApi），所以点行首箭头只是把
        已经拿到手的那几行摊开，不再逐张捞。隔色只看外层那一张表（见 .outbound-table 那段）：
        展开的商品明细自己不再斑马，整块底色跟着它所属那一行走（那一行是白就是白、是蓝就是蓝）。
        宽度比「入库单」那张多 140px：展开的明细多一列「出库仓库」（出库单的仓记在行上，不在主表），
        内层那些定宽列是死的，弹窗不补这一档， squeeze 就全落在商品那一格上，长名字直接变省略号
      -->
      <el-dialog v-model="outboundOpen" width="1180px" append-to-body destroy-on-close>
        <template #header>
          <div class="dialog-head">
            <span class="el-dialog__title">销售单关联的出库单</span>
            <el-button v-if="outboundList.length > 0" type="primary" plain size="small"
              @click="handleOutboundPrint">打 印</el-button>
          </div>
        </template>
        <div class="outbound-head">
          <span>销售单 {{ outboundSale.saleNo || '-' }}</span>
          <span>销售门店 {{ outboundSale.storeName || '-' }}</span>
          <span>共 {{ outboundList.length }} 张出库单</span>
        </div>
        <el-table class="outbound-table" :data="outboundList" size="small" border stripe row-key="id"
          v-loading="outboundLoading">
          <el-table-column type="expand">
            <template #default="{ row, $index }">
              <!-- 展开区跟着它所属那一行的底色走：EP 的条纹只打在数据行上（下标奇数那条），展开行自己不带，
                   所以这里按同一个下标补一次，见 .outbound-items-wrap -->
              <div class="outbound-items-wrap" :class="{ 'is-striped': $index % 2 === 1 }">
                <el-table class="outbound-items" size="small" border :data="row.items || []"
                  empty-text="这张出库单没有明细">
                  <el-table-column label="商品" min-width="270" show-overflow-tooltip>
                    <template #default="{ row: item }">
                      <div class="product-option">
                        <span class="product-option__name">{{ item.productName || `已删除规格 #${item.skuId}` }}</span>
                        <span class="product-option__spec">{{ item.specText || '默认规格' }}</span>
                        <span class="product-option__code">{{ item.productCode }}</span>
                      </div>
                    </template>
                  </el-table-column>
                  <!-- 仓库在明细上不在主表：一张单的几行可能从几家仓分别发（所以外层那一列没有"出库仓库"） -->
                  <el-table-column label="出库仓库" align="center" min-width="140">
                    <template #default="{ row: item }">
                      <span>{{ item.warehouseName || `已删除仓库 #${item.warehouseId}` }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="计划出库" prop="quantity" width="100" align="center" />
                  <el-table-column label="已出库" width="100" align="center">
                    <template #default="{ row: item }">
                      <span :class="{ 'stock-done': remainingOf(item) === 0 }">{{ item.outboundQuantity || 0 }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="剩余" width="90" align="center">
                    <template #default="{ row: item }">
                      <span>{{ remainingOf(item) }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="单价(成本价)" width="130" align="center">
                    <template #default="{ row: item }">
                      <span>{{ priceText(item.costPrice) }}</span>
                    </template>
                  </el-table-column>
                  <!-- 小计读库里的 amount：它是服务端按 数量 × 成本价 算好落库的那个数，不是这里现乘的 -->
                  <el-table-column label="小计" width="120" align="center">
                    <template #default="{ row: item }">
                      <span>{{ priceText(item.amount) }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="出库单号" prop="outboundNo" align="center" min-width="150" show-overflow-tooltip />
          <el-table-column label="状态" align="center" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="dict.tag(DICT_OUTBOUND_STATUS, row.status)">
                {{ dict.label(DICT_OUTBOUND_STATUS, row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="计划总数" prop="totalQuantity" align="center" width="90" />
          <el-table-column label="已出库" prop="totalOut" align="center" width="80" />
          <el-table-column label="出库金额" align="center" width="110">
            <template #default="{ row }">
              <span>{{ priceText(row.totalAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作人" align="center" min-width="100">
            <template #default="{ row }">
              <span>{{ row.userName || '未记录' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="出库时间" align="center" width="110">
            <template #default="{ row }">
              <span>{{ validate.formatTime(row.outboundTime, 'YYYY-MM-DD') || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!outboundLoading && outboundList.length === 0" class="outbound-empty">
          这张销售单还没有建过出库单，去「商品出库」点「新建」选这张单即可
        </div>
        <template #footer>
          <el-button @click="outboundOpen = false">关 闭</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listErpSaleApi,
  detailErpSaleApi,
  addErpSaleApi,
  updateErpSaleApi,
  deleteErpSaleApi,
  optionsErpSaleProductApi
} from '@/api/erp/sale'
import { listErpSaleOutboundBySaleApi } from '@/api/erp/saleOutbound'
import { optionsShopStoreApi } from '@/api/store/store'
import { optionsErpWarehouseApi } from '@/api/erp/warehouse'
import { getUserDetailApi } from '@/api/system/user'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import validate from '@/utils/validate'
import { printOutbounds } from '@/utils/print'
import { useDict } from '@/utils/dictCache'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
/** 账号类型：0平台 1门店 2送货员 3消费者 4供应商 5ERP操作员；ERP 账号只看/只改指派给自己那几张单 */
const isErpAccount = computed(() => userStore.user.accountType === 5)

/**
 * 销售单状态字典：sale_status = status（0待审核 1已审核 2待出库 3已出库 4已取消）。
 * 2/3 不由这一页选：出库单把每行登记满/出满后服务端按进度回写，
 * 见 ShopErpSaleOutboundServiceImpl#refreshSaleStatus
 */
const DICT_TYPE = 'sale_status'
/** 出库单自己的状态字典（「出库单」弹窗那一列要用）：0待出库 1部分出库 2已出库，和销售单的 2 待出库是两码事 */
const DICT_OUTBOUND_STATUS = 'sale_outbound_status'
const SALE_STATUS_PENDING = 0
/** 已取消：待审核时作废。这一档改不动了，但还删得掉（名下必然没有出库单） */
const SALE_STATUS_CANCELLED = 4
/**
 * 只有待审核这一档还能改（服务端 update 同样拦）：审核过就等于对客户承诺过了，
 * 名下出库单是照着这张单的明细建的。status 缺失按待审核算，和后端口径一致
 */
const canModify = (row: any) => Number(row.status ?? SALE_STATUS_PENDING) === SALE_STATUS_PENDING
/** 能删的比能改的宽一档：待审核 和 已取消（服务端 deleteByIds 同一道判据） */
const canDelete = (row: any) => canModify(row) || Number(row.status) === SALE_STATUS_CANCELLED
/**
 * 「出库单」入口：待审核的还没批、建不了出库单，已取消的名下必然没有（服务端 insert 只放行 1/2 两档），
 * 所以这两档整条收掉不置灰；1 已审核 / 2 待出库 / 3 已出库 都能点，看的是"出到哪一步了"
 */
const canViewOutbound = (row: any) =>
  ![SALE_STATUS_PENDING, SALE_STATUS_CANCELLED].includes(Number(row.status))
/**
 * 表单里那一格「状态」的候选：2 待出库 / 3 已出库 是出库那条链推出来的，手工选了等于伪造进度，
 * 所以下拉里根本不给它们（列表和查询仍然按全字典渲染标签）
 */
const formStatusValues = [SALE_STATUS_PENDING, 1, SALE_STATUS_CANCELLED]
/** 出库来源字典：这一列列表上没有，但「打印」出来的单头上要写，所以一并加载（见 utils/print） */
const dict = useDict(DICT_TYPE, DICT_OUTBOUND_STATUS, 'sale_outbound_source')
const formStatusOptions = computed(() =>
  dict.options(DICT_TYPE).filter((item: any) => formStatusValues.includes(Number(item.value)))
)

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
  storeId: undefined,
  status: undefined
})
const dateRange = ref<string[]>([])

// 表单参数：items 是明细数组，整单一起提交
const form = reactive<any>({ items: [] as any[] })

const rules = reactive<FormRules>({
  storeId: [{ required: true, message: '请选择销售门店', trigger: 'change' }]
})

/** 金额的显示口径同购物车、商品列表（后端返回的 decimal 是字符串，统一走 Number） */
const priceText = (price: any) => (price == null || price === '' ? '￥0.00' : `￥${Number(price).toFixed(2)}`)

/** 行小计：只是录入时的回显，落库的 amount 由服务端按 数量 × 单价 重算（口径一致，不冲突） */
const rowAmount = (row: any) => (Number(row.quantity || 0) * Number(row.price || 0)).toFixed(2)

const totalQuantity = computed(() =>
  form.items.reduce((sum: number, row: any) => sum + Number(row.quantity || 0), 0)
)
const totalAmount = computed(() =>
  form.items.reduce((sum: number, row: any) => sum + Number(rowAmount(row)), 0)
)

const storeOptions = ref<any[]>([])
const warehouseOptions = ref<any[]>([])

/**
 * 「发货仓库」那一格的候选：跟着上面的门店走 —— 这家门店名下的仓排在前面，
 * 后面接上没挂门店的那几家（主仓/分仓是共用的，store_id 为空，见仓库实体那一列的注释，
 * 一律排除掉的话共用仓就没法发了，就近分仓那条链也把没挂门店的仓当候选）。
 * options 接口返回的是仓库整行（含 storeId），所以这一档在前端切就行，不用新接口。
 * 没选门店时这一档没有意义（门店是这张单的口径线），下拉先禁用
 */
const storeWarehouseOptions = computed(() => {
  if (!form.storeId) return []
  const list = warehouseOptions.value
  const own = list.filter((item: any) => String(item.storeId) === String(form.storeId))
  const shared = list.filter((item: any) => item.storeId == null)
  const options = [...own, ...shared]
  // 改单回显兜底：这一档收紧之前录的单子，原来那个仓可能既不属于这家门店也非共用仓，
  // 掉出候选那一格就只剩一个裸 id（同明细商品候选只合并不清空的理由），所以把它补回第一档
  if (form.warehouseId && !options.some((item: any) => String(item.id) === String(form.warehouseId))) {
    const selected = list.find((item: any) => String(item.id) === String(form.warehouseId))
    if (selected) options.unshift(selected)
  }
  return options
})

/**
 * 商品候选：远程搜索的结果一直往里合并、不清空。
 * el-select 只显示"当前候选里能匹配上的那条"，所以每搜一次就把列表换掉的话，
 * 其它已选行会立刻显示成裸 id —— 明细里同时有五六行商品，这个必须避开。
 * 范围由后端按「属于这家门店 + 这一条规格标了可销售」收窄（一行就是一个规格），选了发货仓库再加一档「那个仓有这一条规格的可用库存」（见 searchProducts）
 */
const productOptions = ref<any[]>([])
const productLoading = ref(false)

/** 候选行和明细行都带 skuId（候选那一格 id 是 SPU、skuId 才是这一行选的规格），统一成明细那套 key：skuId / name / spec / code / price */
const mergeProductOptions = (list: any[]) => {
  const map = new Map(productOptions.value.map((item: any) => [String(item.skuId), item]))
  list.forEach((item: any) => {
    map.set(String(item.skuId), {
      skuId: item.skuId,
      name: item.productName || `已删除规格 #${item.skuId}`,
      code: item.productCode,
      // 同一件商品供了几个规格就有几行，选中后输入框里得靠这一串分出去
      spec: item.specText,
      // 这条规格的售价，选规格时用来兜这一行的销售单价（改得了：这一单可以按别的价成交）
      price: item.productPrice
    })
  })
  productOptions.value = [...map.values()]
}

/** 下拉里那条和选中后输入框里那串都用它：商品名 + 规格（无规格的默认那条不补字） */
const optionLabel = (item: any) => `${item.name}${item.spec ? ` ${item.spec}` : ''}`

/**
 * 选中规格：把这一行的销售单价兜成这条规格的售价；已经录过的不动（单价填过就不覆盖），
 * 免得挑错商品把自己录的数冲掉。
 * el-select 的 change 只在用户操作时触发，编辑回显不会走到这里
 */
const handleProductChange = (row: any, skuId: any) => {
  const option = productOptions.value.find((item: any) => String(item.skuId) === String(skuId))
  if (!option) return
  if (row.price == null || row.price === '') {
    row.price = option.price == null ? undefined : Number(option.price)
  }
}

/**
 * 明细的商品那一格初始是禁用的：要先有门店、再有发货仓库，挑出来的商品才是「这家门店可销售的 +
 * 这个仓有可用库存」那一份。编辑回显时已经录好的行照常显示名字（handleUpdate 把明细塞进过候选），
 * 只是不换出仓库就没法改挑商品
 */
const canPickProduct = computed(() => !!(form.storeId && form.warehouseId))

const productPlaceholder = computed(() => {
  if (!form.storeId) return '请先选择销售门店'
  if (!form.warehouseId) return '请先选择发货仓库'
  return '输入名称/编码/规格搜索'
})

const searchProducts = async (keyword: string) => {
  if (!canPickProduct.value) return
  productLoading.value = true
  try {
    const { data } = await optionsErpSaleProductApi({
      storeId: form.storeId,
      // 后端 EXISTS 到那个仓的可用库存（stock - locked_stock > 0）
      warehouseId: form.warehouseId,
      keyword: keyword || undefined
    })
    mergeProductOptions(data || [])
  } catch (error) {
  } finally {
    productLoading.value = false
  }
}

/**
 * 换发货仓库 → 候选范围整个换掉（换成"那个仓有没有货"），所以重拉一次。
 * 已选行不清：换仓只是换个发货口，那几行的商品/数量/单价还是这张单要卖的数，
 * 而且候选是合并进去的、掉出范围也照样显示名字；真有货没货要到出库那一步才挡得实
 */
const handleWarehouseChange = () => {
  const hadProducts = form.items.some((row: any) => row.skuId)
  searchProducts('')
  if (hadProducts && form.warehouseId) {
    ElMessage.info('已按该仓库的可用库存过滤商品候选，请核对已选商品是否有货')
  }
}

/** 一行空明细：数量 1、已出库 0、单价留空，选中规格后单价会被这条规格的售价兜一次（见 handleProductChange） */
const emptyItem = () => ({ skuId: undefined, quantity: 1, shippedQuantity: 0, price: undefined })

const addItem = () => {
  form.items.push(emptyItem())
}

/**
 * 换门店 → 商品候选范围跟着变（后端按「商品属于这家门店」收窄），原来选的商品多半不在新范围里，
 * 所以整块清回一行空行重录，不留一堆选不中、提交又被后端挡掉的半截数据（后端 validate 会逐行复核归属）。
 * 发货仓库也一起清掉：那一格的候选是跟着门店走的（这家门店名下的仓 + 没挂门店的共用仓），
 * 留着上一家门店的仓，下拉里已经选不中它了
 * el-select 的 change 只在用户操作时触发，编辑弹窗回显不会误清
 */
const handleStoreChange = () => {
  const hadProducts = form.items.some((row: any) => row.skuId)
  form.warehouseId = undefined
  form.items = [emptyItem()]
  productOptions.value = []
  if (hadProducts) {
    ElMessage.warning('已切换门店，销售明细请按该门店的商品范围重新选择')
  }
}

const removeItem = (index: number) => {
  form.items.splice(index, 1)
}

/** 明细的完整性靠这里挡一遍（后端 validate 还会再挡一遍，这里只为少一次往返） */
const checkItems = () => {
  if (!form.items.length) {
    ElMessage.warning('请至少添加一行销售明细')
    return false
  }
  for (let i = 0; i < form.items.length; i++) {
    const row = form.items[i]
    if (!row.skuId || !row.quantity || row.price == null) {
      ElMessage.warning(`第 ${i + 1} 行明细的商品规格/数量/单价没填全`)
      return false
    }
  }
  return true
}

/** 列表查询 */
const getList = async () => {
  loading.value = true
  try {
    const params: any = { ...queryParams }
    if (dateRange.value && dateRange.value.length === 2) {
      params.beginTime = `${dateRange.value[0]} 00:00:00`
      params.endTime = `${dateRange.value[1]} 23:59:59`
    }
    const { data } = await listErpSaleApi(params)
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
    saleNo: undefined,
    storeId: undefined,
    warehouseId: undefined,
    userId: undefined,
    userName: undefined,
    status: SALE_STATUS_PENDING,
    expectDate: undefined,
    contactName: undefined,
    contactPhone: undefined,
    remark: undefined,
    // 明细整单提交，新增时先给一行空行让人直接录
    items: [emptyItem()]
  })
  // 候选也清掉：它是按上一个门店的商品查出来的，留着新增时就能挑到范围外的商品。
  // 编辑是 reset 之后再把手上这几行的商品塞回来（见 handleUpdate），所以回显不受影响
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
  queryParams.storeId = undefined
  queryParams.status = undefined
  dateRange.value = []
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
}

/**
 * 选操作员带出联系人和联系电话（口径同采购单：这一单就是他在跟）。
 * 换人时以新选的人为准，直接覆盖；手机号取用户详情里的 mobile，账号没填手机号就留空。
 */
const handleOperatorSelect = (user: any) => {
  const name = user.nickname || user.username
  form.userName = name
  form.contactName = name
  form.contactPhone = user.mobile
}

/** 取消指派只清回显的名字：联系人/电话是已经录进去的业务数据，不跟着抹（同采购单） */
const handleOperatorClear = () => {
  form.userName = undefined
}

/**
 * ERP 账号自己新增：操作员填成登录人（那一格禁用了，不会再触发 @select，所以联系人得在这里主动带一次）。
 * 手机号不在 /api/auth/info 返回的登录信息里，只能按 id 再查一次用户详情（这个读接口不校验权限，ERP 账号调得动）。
 */
const fillSelfAsOperator = async () => {
  const self = userStore.user as any
  if (!self.id) return
  form.userId = self.id
  form.userName = self.nickname || self.username
  try {
    const { data } = await getUserDetailApi(self.id)
    form.contactName = data.nickname || data.username || form.userName
    form.contactPhone = data.mobile
  } catch (error) {
  }
}

/** 新增按钮操作 */
const handleAdd = async () => {
  reset()
  open.value = true
  title.value = '添加销售单'
  if (isErpAccount.value) await fillSelfAsOperator()
}

/** 修改按钮操作：详情接口带 items 回来，商品名/编码要塞进候选否则选择框显示裸 id */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailErpSaleApi(row.id)
    Object.assign(form, data)
    form.items = data.items || []
    // 明细行上就带着 skuId + 商品名/编码/规格（联表回填的），直接塞进候选，否则选择框显示裸 id
    mergeProductOptions(form.items)
    open.value = true
    title.value = '修改销售单'
  } catch (error) {
  }
}

/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    if (!checkItems()) return
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateErpSaleApi(form)
        ElMessage.success('修改成功')
      } else {
        await addErpSaleApi(form)
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
  ElMessageBox.confirm(`确认删除销售单「${row.saleNo}」吗？明细会一起删掉；审核过的删不了（要撤下来只能先在待审核时作废）。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteErpSaleApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 张销售单吗？明细会一起删掉；审核过的删不了（复选框也不会让你勾上那些）。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteErpSaleApi(selectedIds.value)
    ElMessage.success('删除成功')
    selectedIds.value = []
    getList()
  }).catch(() => { })
}

/**
 * 「出库单」弹窗：只看这一张销售单名下的出库单（只读；要登记出库、真正扣账去「商品出库」那页操作）。
 * 一次把这几张单连各自的明细捞回来：一张销售单分几次出库而已，撑死几张，不做分页
 */
const outboundOpen = ref(false)
const outboundLoading = ref(false)
const outboundList = ref<any[]>([])
const outboundSale = ref<any>({})

const handleOutbound = async (row: any) => {
  outboundSale.value = row
  outboundList.value = []
  outboundOpen.value = true
  outboundLoading.value = true
  try {
    const { data } = await listErpSaleOutboundBySaleApi(row.id)
    outboundList.value = data || []
  } catch (error) {
  } finally {
    outboundLoading.value = false
  }
}

/** 打印这一张销售单名下的出库单：直接把弹窗里已经捞到的那几张交给打印器，不再捞一遍 */
const handleOutboundPrint = () => {
  printOutbounds(outboundList.value, `销售单 ${outboundSale.value.saleNo} 的出库单`)
}

/** 明细行还剩多少没出 = 计划出库 − 已出库（口径同商品出库那页） */
const remainingOf = (row: any) =>
  Math.max(0, Number(row.quantity || 0) - Number(row.outboundQuantity || 0))

/** 分页 */
const handleSizeChange = () => {
  queryParams.pageNum = 1
  getList()
}
const handleCurrentChange = () => {
  getList()
}

const getStoreOptions = async () => {
  try {
    const { data } = await optionsShopStoreApi()
    storeOptions.value = data || []
  } catch (error) {
  }
}

/**
 * 仓库候选：主表那一格「发货仓库」用。options 接口刻意不按 ERP 账号收窄（见仓库模块），
 * 所以这里拿到的是全部启用的仓
 */
const getWarehouseOptions = async () => {
  try {
    const { data } = await optionsErpWarehouseApi()
    warehouseOptions.value = data || []
  } catch (error) {
  }
}

onMounted(() => {
  getList()
  getStoreOptions()
  getWarehouseOptions()
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

/* 时间范围那一行（照抄采购单那一页的 .search-row）：wrapper 独占整行把日期格顶到第二行，
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
     所以 width 之外再压一道 max-width + flex-grow:0 */
  .el-date-editor {
    width: 360px !important;
    max-width: 360px !important;
    flex-grow: 0 !important;
  }

  /* 按钮紧贴日期框 */
  .el-form-item.search-actions {
    margin-right: 0;
  }
}

/* 明细行里商品下拉的名称 + 编码（同采购单那一页的排法） */
.product-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  &__name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__code {
    flex: 0 0 auto;
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  /* 一行是一个具体规格，同一商品名的几行全靠这一串分出去，所以它跟名称同宽、不折到编码那一列去 */
  &__spec {
    flex: 0 0 auto;
    color: var(--el-color-primary);
    font-size: 12px;
  }
}

/* 「出库单」弹窗：抬头那排说明文字 */
.outbound-head {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

/*
 * 「出库单」弹窗的隔色：只有外层那一张表斑马（同采购单那页的 .receipt-table）。
 * EP 的条纹就是 --el-fill-color-lighter（默认 #fafafa，在这种带 border 的小表格里等于没色），所以换成蓝，
 * hover 再压一档不然划过去看不出动静。展开的商品明细不再自己区分：整张内层表的底色改成透明
 * （EP 的表根、tr、th 各有一个 bg 变量，三个都要清，不然还是一块白），
 * --el-table-expanded-cell-bg-color 也留透明 —— 色由 .outbound-items-wrap 那层铺，
 * 才能跟着所属那一行（EP 展开区在 hover 时会把单元格刷成透明，色涂在单元格上会闪）
 */
.outbound-table {
  --el-fill-color-lighter: var(--el-color-primary-light-8);
  --el-table-row-hover-bg-color: var(--el-color-primary-light-7);
  --el-table-expanded-cell-bg-color: transparent;

  /* 内边距挪到包装层，色块才铺得满整个展开区（EP 默认给展开单元格留了 20px / 50px） */
  :deep(.el-table__expanded-cell) {
    padding: 0;
  }

  .outbound-items {
    --el-table-bg-color: transparent;
    --el-table-tr-bg-color: transparent;
    --el-table-header-bg-color: transparent;
  }
}

.outbound-items-wrap {
  padding: 20px 50px;

  /* 条纹按数据行下标算（奇数那条才有），展开行不带这个类，所以由这里按同一个下标补一次。
     颜色直接取外层那个条纹变量，两边不会走偏 */
  &.is-striped {
    background: var(--el-fill-color-lighter);
  }
}

/* 一张出库单都没有时的说明 */
.outbound-empty {
  margin-top: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

/* 出满的那一行数字：绿色加粗（同商品入库那页的已入库） */
.stock-done {
  color: var(--el-color-success);
  font-weight: 600;
}

/* 明细表格下面那排：左边加行按钮，右边合计文案 */
.items-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;

  .items-tip {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  .items-total {
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }
}
</style>
