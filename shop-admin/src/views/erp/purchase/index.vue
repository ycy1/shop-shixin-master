<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="请输入单号/供应商/备注" clearable
            @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="供应商" prop="supplierId">
          <el-select v-model="queryParams.supplierId" placeholder="全部" clearable filterable>
            <el-option v-for="item in supplierOptions" :key="item.id" :label="item.supplierName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>

        <!-- 时间范围 + 按钮同一行（照抄收藏、购物车列表的 .search-row）：这一层 wrapper 独占第二行，
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
          <el-button v-permission="['erp:purchase:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['erp:purchase:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" :selectable="canModify" />
        <el-table-column label="采购单号" prop="purchaseNo" align="center" min-width="180" />
        <!-- 供应商/操作员名称都是查询时联表现取的，供应商改名不用回头刷单子 -->
        <el-table-column label="供应商" align="center" min-width="160">
          <template #default="{ row }">
            <span>{{ row.supplierName || `已删除供应商 #${row.supplierId}` }}</span>
          </template>
        </el-table-column>
        <el-table-column label="所属门店" align="center" min-width="160">
          <template #default="{ row }">
            <!-- 入库单那一页的「入库仓库」候选按它收窄，所以列上要先看得见是哪家门店。
                 加列之前建的老单没这个信息，显示 '-'（那几张单在入库页仍按全部仓库挑） -->
            <span>{{ row.storeName || (row.storeId ? '已删除门店 #' + row.storeId : '-') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="总数量" prop="totalQuantity" align="center" width="100" />
        <el-table-column label="采购金额" align="center" width="130">
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
        <el-table-column label="预计到货" align="center" width="120">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.expectDate, 'YYYY-MM-DD') || '-' }}</span>
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
        <el-table-column label="操作" align="center" width="280" fixed="right">
          <template #default="scope">
            <!-- 入库单：这一张采购单名下的入库单都在这里，点行首箭头展开看那张单入了哪些商品。
                 待审核（0）的单子不给这个入口：还没批下来的单建不了入库单 -->
            <el-button v-if="scope.row.status !== PURCHASE_STATUS_PENDING"
              v-permission="['erp:purchase:receipt']" type="primary" link icon="Document"
              @click="handleReceipt(scope.row)">入库单</el-button>
            <!-- 采购退货：退的是这张单当初进来的货，所以入口挂在行上而不是另开一页。
                 可多次退：额度是「已入库 − 已登记退货」，弹窗只把还没退完的商品列出来，
                 一件都没入过货时后端会直接报错，不弹半截的单子 -->
            <el-button v-if="scope.row.status !== PURCHASE_STATUS_PENDING"
              v-permission="['erp:purchase:return']" type="warning" link icon="RefreshLeft"
              @click="handleReturn(scope.row)">采购退货</el-button>
            <!-- 只有待审核的单子能改/能删（服务端同样拦）：「审核」就是在这里把状态选成已审核，
                 选完这张单就冻结了，要动只能再开一张 -->
            <el-button v-if="canModify(scope.row)" v-permission="['erp:purchase:update']" type="primary" link
              icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
            <el-button v-if="canModify(scope.row)" v-permission="['erp:purchase:delete']" type="danger" link
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
              <el-form-item label="采购单号" prop="purchaseNo">
                <el-input v-model="form.purchaseNo" placeholder="保存后由系统生成" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="供应商" prop="supplierId">
                <el-select v-model="form.supplierId" placeholder="请选择供应商" filterable style="width: 100%"
                  @change="handleSupplierChange">
                  <el-option v-for="item in supplierOptions" :key="item.id" :label="item.supplierName"
                    :value="item.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="所属门店" prop="storeId">
                <!-- 这批货进的是哪家门店的仓：建入库单时「入库仓库」的候选按它收窄，所以录单时必填 -->
                <el-select v-model="form.storeId" placeholder="请选择所属门店" filterable style="width: 100%">
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
              <el-form-item label="状态" prop="status">
                <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
                  <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="预计到货" prop="expectDate">
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

          <el-divider content-position="left">采购明细</el-divider>
          <el-table :data="form.items" size="small" border>
            <!-- 一行是一个具体规格（候选来自供货关系，那表记的就是 sku_id），所以这一格选的是规格不是商品 -->
            <el-table-column label="商品规格" min-width="260">
              <template #default="{ row }">
                <el-select v-model="row.skuId" :disabled="!form.supplierId"
                  :placeholder="form.supplierId ? '输入名称/编码/规格搜索' : '请先选择供应商'" filterable remote
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
            <el-table-column label="采购数量" width="130" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="1" :controls="false" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="已入库" width="110" align="center">
              <template #default="{ row }">
                <!-- 只读回显：这一列只能由「商品入库」的入库动作累加，手填等于改入库进度（服务端也不认请求体里的值） -->
                <span>{{ row.receivedQuantity || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="采购单价" width="140" align="center">
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
        这张采购单名下的入库单：只看不动，要登记到货去「商品入库」那页。右上角「打印」把这几张单
        连明细交给浏览器打印窗口（在那里选「另存为 PDF」就是 PDF）。
        明细由后端跟着入库单一次给全（见 listErpPurchaseInByPurchaseApi），所以点行首箭头只是把
        已经拿到手的那几行摊开，不再逐张捞。隔色只看外层那一张表（见 .receipt-table 那段）：
        展开的商品明细自己不再斑马，整块底色跟着它所属那一行走（那一行是白就是白、是蓝就是蓝）
      -->
      <el-dialog v-model="receiptOpen" width="1040px" append-to-body destroy-on-close>
        <template #header>
          <div class="dialog-head">
            <span class="el-dialog__title">采购单关联的入库单</span>
            <el-button v-if="receiptList.length > 0" type="primary" plain size="small"
              @click="handleReceiptPrint">打 印</el-button>
          </div>
        </template>
        <div class="receipt-head">
          <span>采购单 {{ receiptPurchase.purchaseNo || '-' }}</span>
          <span>供应商 {{ receiptPurchase.supplierName || '-' }}</span>
          <span>共 {{ receiptList.length }} 张入库单</span>
        </div>
        <el-table class="receipt-table" :data="receiptList" size="small" border stripe row-key="id"
          v-loading="receiptLoading">
          <el-table-column type="expand">
            <template #default="{ row, $index }">
              <!-- 展开区跟着它所属那一行的底色走：EP 的条纹只打在数据行上（下标奇数那条），展开行自己不带，
                   所以这里按同一个下标补一次，见 .receipt-items-wrap -->
              <div class="receipt-items-wrap" :class="{ 'is-striped': $index % 2 === 1 }">
                <el-table class="receipt-items" size="small" border :data="row.items || []"
                  empty-text="这张入库单没有明细">
                  <!-- 这一格里溢出的是内层那个 span 而不是单元格本身，EP 的 show-overflow-tooltip
                       量不到、不弹，所以全称由这里自己包一层 tooltip -->
                  <el-table-column label="商品名称" min-width="180">
                    <template #default="{ row: item }">
                      <el-tooltip :content="productNameTip(item)" placement="top">
                        <div class="product-option">
                          <span class="product-option__name">{{ item.productName || `已删除规格 #${item.skuId}` }}</span>
                          <span class="product-option__code">{{ item.productCode }}</span>
                        </div>
                      </el-tooltip>
                    </template>
                  </el-table-column>
                  <el-table-column label="规格" width="100" align="center">
                    <template #default="{ row: item }">
                      <span>{{ item.specText || '默认规格' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="计划入库" prop="quantity" width="100" align="center" />
                  <el-table-column label="已入库" width="100" align="center">
                    <template #default="{ row: item }">
                      <span :class="{ 'stock-done': remainingOf(item) === 0 }">{{ item.receivedQuantity || 0 }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="剩余" width="90" align="center">
                    <template #default="{ row: item }">
                      <span>{{ remainingOf(item) }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="单价(成本价)" width="130" align="center">
                    <template #default="{ row: item }">
                      <span>{{ priceText(item.price) }}</span>
                    </template>
                  </el-table-column>
                  <!-- 小计读库里的 amount：它是服务端按 数量 × 单价 算好落库的那个数，不是这里现乘的 -->
                  <el-table-column label="小计" width="120" align="center">
                    <template #default="{ row: item }">
                      <span>{{ priceText(item.amount) }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="入库单号" prop="receiptNo" align="center" min-width="150" show-overflow-tooltip />
          <el-table-column label="入库仓库" align="center" min-width="140">
            <template #default="{ row }">
              <span>{{ row.warehouseName || `已删除仓库 #${row.warehouseId}` }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" align="center" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="dict.tag(RECEIPT_DICT_TYPE, row.status)">
                {{ dict.label(RECEIPT_DICT_TYPE, row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="计划总数" prop="totalQuantity" align="center" width="90" />
          <el-table-column label="已入库" prop="totalReceived" align="center" width="80" />
          <el-table-column label="入库金额" align="center" width="110">
            <template #default="{ row }">
              <span>{{ priceText(row.totalAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作人" align="center" min-width="100">
            <template #default="{ row }">
              <span>{{ row.userName || '未记录' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="入库时间" align="center" width="110">
            <template #default="{ row }">
              <span>{{ validate.formatTime(row.receiveTime, 'YYYY-MM-DD') || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!receiptLoading && receiptList.length === 0" class="receipt-empty">
          这张采购单还没有建过入库单，去「商品入库」新增一张即可
        </div>
        <template #footer>
          <el-button @click="receiptOpen = false">关 闭</el-button>
        </template>
      </el-dialog>

      <!--
        采购退货：从这张单退多少货回供应商，形状照抄「商品入库」的「确认入库」弹窗——
        不给加行、删行（商品是后端按采购单汇总好的），只有「本次退货数」「退货仓库」「单价」三格能填。
        这里只是登记：提交后建一张来源=3 采购退货 的出库单（0 待出库），货要等仓库在「商品出库」那页
        点「出库」才真离仓、才减库存。没退完的量下次还能再退，所以这一张可以反复开
      -->
      <el-dialog v-model="returnOpen" title="采购退货" width="1040px" append-to-body destroy-on-close>
        <el-form label-width="110px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="采购单号">
                <el-input :model-value="returnForm.purchaseNo" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="供应商">
                <el-input :model-value="returnForm.supplierName" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="退货原因">
                <el-input v-model="returnForm.remark" type="textarea" :rows="2"
                  placeholder="选填，会记在生成的出库单备注上" maxlength="500" show-word-limit />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">退货明细</el-divider>
          <el-table :data="returnForm.items" size="small" border v-loading="returnLoading">
            <el-table-column label="商品规格" min-width="220">
              <template #default="{ row }">
                <el-tooltip :content="productSpecTip(row)" placement="top">
                  <div class="product-option">
                    <span class="product-option__name">{{ row.productName || `已删除规格 #${row.skuId}` }}</span>
                    <span class="product-option__spec">{{ row.specText || '默认规格' }}</span>
                    <span class="product-option__code">{{ row.productCode }}</span>
                  </div>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="已入库" width="90" align="center">
              <template #default="{ row }">
                <span>{{ row.receivedQuantity || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="还能退" width="90" align="center">
              <template #default="{ row }">
                <!-- 上限就是这一格的数（已入库 − 这张单名下退货单已登记的量）；退满的商品后端不列，所以这一列恒 > 0 -->
                <span>{{ row.returnableQuantity || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="本次退货数" width="130" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="0" :max="Number(row.returnableQuantity || 0)"
                  :controls="false" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="退回仓库" width="200" align="center">
              <template #default="{ row }">
                <!-- 默认带出这个商品当初主要从哪家仓入的货，可以改；仓里够不够货不在这里判，
                     真出库那一步按 (商品, 仓库) 扣账，不够会明确说是哪一行 -->
                <el-select v-model="row.warehouseId" placeholder="请选择仓库" filterable style="width: 100%">
                  <el-option v-for="item in warehouseOptions" :key="item.id" :label="item.warehouseName"
                    :value="item.id">
                    <div class="product-option">
                      <span class="product-option__name">{{ item.warehouseName }}</span>
                      <span class="product-option__code">{{ item.warehouseCode }}</span>
                    </div>
                  </el-option>
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="单价(采购价)" width="140" align="center">
              <template #default="{ row }">
                <!-- 默认带这个商品在这张单里的加权采购单价：退回给供应商按进价结，不按库存的加权成本 -->
                <el-input-number v-model="row.costPrice" :min="0" :precision="2" :controls="false"
                  style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="小计" width="120" align="center">
              <template #default="{ row }">
                <span>{{ priceText(returnRowAmount(row)) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="items-footer">
            <span class="items-tip">这次不退的商品把「本次退货数」留 0 即可，剩下的量下次还能再退</span>
            <span class="items-total">本次退货数量 {{ returnTotalQuantity }} · 金额
              {{ priceText(returnTotalAmount) }}</span>
          </div>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="returnOpen = false">取 消</el-button>
            <el-button type="primary" :loading="returnSubmitting" @click="submitReturn">确认退货</el-button>
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
  listErpPurchaseApi,
  detailErpPurchaseApi,
  addErpPurchaseApi,
  updateErpPurchaseApi,
  deleteErpPurchaseApi,
  optionsErpPurchaseProductApi,
  returnDraftErpPurchaseApi,
  createReturnErpPurchaseApi
} from '@/api/erp/purchase'
import { optionsErpSupplierApi } from '@/api/erp/supplier'
import { optionsShopStoreApi } from '@/api/store/store'
import { optionsErpWarehouseApi } from '@/api/erp/warehouse'
import { listErpPurchaseInByPurchaseApi } from '@/api/erp/purchaseIn'
import { getUserDetailApi } from '@/api/system/user'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import validate from '@/utils/validate'
import { printReceipts } from '@/utils/print'
import { useDict } from '@/utils/dictCache'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
/** 账号类型：0平台 1门店 2送货员 3消费者 4供应商 5ERP操作员；ERP 账号只看/只改指派给自己那几张单 */
const isErpAccount = computed(() => userStore.user.accountType === 5)

/**
 * 采购单状态字典：purchase_status = status（0待审核 1已审核 4待入库 2已入库 3已退货）。
 * 4/2 不由这一页选：入库单登记满/入满后服务端按进度回写，见 ShopErpPurchaseInServiceImpl#refreshPurchaseStatus
 */
const DICT_TYPE = 'purchase_status'
/** 待审核：这一档的单子还没批，列表上不给「入库单」入口（也建不了入库单） */
const PURCHASE_STATUS_PENDING = 0
/**
 * 也只有这一档还能改、还能删：审核过就整单冻结（服务端 update / deleteByIds 同样拦，
 * 批量删除因此也靠它挡在勾选那一步）。status 缺失按待审核算，和后端口径一致
 */
const canModify = (row: any) => Number(row.status ?? PURCHASE_STATUS_PENDING) === PURCHASE_STATUS_PENDING
/** 入库单自己的状态字典（「入库单」弹窗那一列要用）：0待入库 1部分入库 2已入库，和采购单的 4 待入库是两码事 */
const RECEIPT_DICT_TYPE = 'purchase_in_status'
/** 入库来源字典：这一列列表上没有，但「打印」出来的单头上要写，所以一并加载（见 utils/print） */
const dict = useDict(DICT_TYPE, RECEIPT_DICT_TYPE, 'inbound_source')

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
  supplierId: undefined,
  status: undefined
})
const dateRange = ref<string[]>([])

// 表单参数：items 是明细数组，整单一起提交
const form = reactive<any>({ items: [] as any[] })

const rules = reactive<FormRules>({
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  storeId: [{ required: true, message: '请选择所属门店', trigger: 'change' }]
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

const supplierOptions = ref<any[]>([])

/** 门店候选：主表那一格「所属门店」用（口径同销售页的销售门店，options 返回启用的门店） */
const storeOptions = ref<any[]>([])

const getStoreOptions = async () => {
  try {
    const { data } = await optionsShopStoreApi()
    storeOptions.value = data || []
  } catch (error) {
  }
}

/**
 * 商品候选：远程搜索的结果一直往里合并、不清空。
 * el-select 只显示"当前候选里能匹配上的那条"，所以每搜一次就把列表换掉的话，
 * 其它已选行会立刻显示成裸 id —— 明细里同时有五六行商品，这个必须避开。
 * 范围由后端按供应商在 shop_supplier_product 里「合作中」的供货关系收窄（见 searchProducts），
 * 一行是一条具体规格，所以键是 skuId
 */
const productOptions = ref<any[]>([])
const productLoading = ref(false)

const mergeProductOptions = (list: any[]) => {
  const map = new Map(productOptions.value.map((item: any) => [String(item.skuId), item]))
  list.forEach((item: any) => map.set(String(item.skuId), {
    skuId: item.skuId,
    name: item.productName || `已删除规格 #${item.skuId}`,
    code: item.productCode,
    // 同一件商品供了几个规格就有几行，选中后输入框里得靠这一串分出去
    spec: item.specText,
    // 关系行上带的供货价和最小起订量，选商品时用来兜这一行的单价/数量
    supplyPrice: item.supplyPrice,
    minOrderQty: item.minOrderQty
  }))
  productOptions.value = [...map.values()]
}

/** 下拉里那条和选中后输入框里那串都用它：商品名 + 规格（无规格的默认那条不补字） */
const optionLabel = (item: any) => `${item.name}${item.spec ? ` ${item.spec}` : ''}`

/**
 * 选中规格：把这条供货关系的参数先填进这一行（数量按最小起订量、单价按供货价，都能改）；
 * 已经录过的不动——单价填过就不覆盖，数量只在还是空行默认值 1 时才顶掉它，
 * 免得挑错商品把自己录的数冲掉。
 * el-select 的 change 只在用户操作时触发，编辑回显不会走到这里
 */
const handleProductChange = (row: any, skuId: any) => {
  const option = productOptions.value.find((item: any) => String(item.skuId) === String(skuId))
  if (!option) return
  if (row.price == null || row.price === '') {
    row.price = option.supplyPrice == null ? undefined : Number(option.supplyPrice)
  }
  if (row.quantity == null || Number(row.quantity) === 1) {
    row.quantity = Math.max(1, Number(option.minOrderQty || 1))
  }
}

const searchProducts = async (keyword: string) => {
  if (!form.supplierId) return
  productLoading.value = true
  try {
    const { data } = await optionsErpPurchaseProductApi({
      supplierId: form.supplierId,
      keyword: keyword || undefined
    })
    mergeProductOptions(data || [])
  } catch (error) {
  } finally {
    productLoading.value = false
  }
}

/** 一行空明细：数量 1、已入库 0、单价留空，选中规格后数量和单价会被供货关系里的参数兜一次（见 handleProductChange） */
const emptyItem = () => ({ skuId: undefined, quantity: 1, receivedQuantity: 0, price: undefined })

const addItem = () => {
  form.items.push(emptyItem())
}

/**
 * 换供应商 → 商品候选范围跟着变（后端按这家供应商在 shop_supplier_product 里「合作中」的供货关系收窄），
 * 原来选的商品多半不在新范围里，所以整块清回一行空行重录，不留一堆选不中、提交又被后端挡掉的半截数据。
 * el-select 的 change 只在用户操作时触发，编辑弹窗回显不会误清
 */
const handleSupplierChange = () => {
  const hadProducts = form.items.some((row: any) => row.skuId)
  form.items = [emptyItem()]
  productOptions.value = []
  if (hadProducts) {
    ElMessage.warning('已切换供应商，采购明细请按该供应商的商品范围重新选择')
  }
}

const removeItem = (index: number) => {
  form.items.splice(index, 1)
}

/** 明细的完整性靠这里挡一遍（后端 validate 还会再挡一遍，这里只为少一次往返） */
const checkItems = () => {
  if (!form.items.length) {
    ElMessage.warning('请至少添加一行采购明细')
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
    const { data } = await listErpPurchaseApi(params)
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
    purchaseNo: undefined,
    supplierId: undefined,
    storeId: undefined,
    userId: undefined,
    userName: undefined,
    status: 0,
    expectDate: undefined,
    contactName: undefined,
    contactPhone: undefined,
    remark: undefined,
    // 明细整单提交，新增时先给一行空行让人直接录
    items: [emptyItem()]
  })
  // 候选也清掉：它是按上一个供应商的供货关系查出来的，留着新增时就能挑到范围外的商品。
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
  queryParams.supplierId = undefined
  queryParams.status = undefined
  dateRange.value = []
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
}

/**
 * 选操作员带出联系人和联系电话（口径同供应商页的绑定账号：这一单就是他在跟）。
 * 换人时以新选的人为准，直接覆盖；手机号取用户列表里的 mobile，账号没填手机号就留空。
 */
const handleOperatorSelect = (user: any) => {
  const name = user.nickname || user.username
  form.userName = name
  form.contactName = name
  form.contactPhone = user.mobile
}

/** 取消指派只清回显的名字：联系人/电话是已经录进去的业务数据，不跟着抹（同供应商页） */
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
  title.value = '添加采购单'
  if (isErpAccount.value) await fillSelfAsOperator()
}

/** 修改按钮操作：详情接口带 items 回来，商品名/编码/规格要塞进候选否则选择框显示裸 id */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailErpPurchaseApi(row.id)
    Object.assign(form, data)
    form.items = data.items || []
    // 明细行上就带着 skuId + 商品名/编码/规格（联表回填的），直接塞进候选，否则选择框显示裸 id
    mergeProductOptions(form.items)
    open.value = true
    title.value = '修改采购单'
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
        await updateErpPurchaseApi(form)
        ElMessage.success('修改成功')
      } else {
        await addErpPurchaseApi(form)
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
  ElMessageBox.confirm(`确认删除采购单「${row.purchaseNo}」吗？明细会一起删掉；已经建过入库单的要先去「商品入库」删掉那些单。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteErpPurchaseApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 明细行还剩多少没入 = 计划 − 已入库（口径同商品入库那页） */
const remainingOf = (row: any) =>
  Math.max(0, Number(row.quantity || 0) - Number(row.receivedQuantity || 0))

/**
 * 商品那一格被截短时要看全名：格子里溢出的是内层那个 span，EP 的列级 show-overflow-tooltip
 * 量的是单元格本身、量不到，所以 tooltip 由格子里自己包一层 el-tooltip 给（列上那个属性要摘掉，留着只是骗人）
 */
const productNameText = (row: any) => row.productName || `已删除规格 #${row.skuId}`
const productNameTip = (row: any) => [productNameText(row), row.productCode].filter(Boolean).join(' · ')
const productSpecTip = (row: any) =>
  [productNameText(row), row.specText || '默认规格', row.productCode].filter(Boolean).join(' · ')

/**
 * 「入库单」弹窗：只看这一张采购单名下的入库单（只读；要登记到货去「商品入库」那页操作）。
 * 一次把这几张单连各自的明细捞回来：一张采购单分几次入库而已，撑死几张，不做分页
 */
const receiptOpen = ref(false)
const receiptLoading = ref(false)
const receiptList = ref<any[]>([])
const receiptPurchase = ref<any>({})

const handleReceipt = async (row: any) => {
  receiptPurchase.value = row
  receiptList.value = []
  receiptOpen.value = true
  receiptLoading.value = true
  try {
    const { data } = await listErpPurchaseInByPurchaseApi(row.id)
    receiptList.value = data || []
  } catch (error) {
  } finally {
    receiptLoading.value = false
  }
}

/** 打印这一张采购单名下的入库单：直接把弹窗里已经捞到的那几张交给打印器，不再捞一遍 */
const handleReceiptPrint = () => {
  printReceipts(receiptList.value, `采购单 ${receiptPurchase.value.purchaseNo} 的入库单`)
}

/**
 * 仓库候选：「采购退货」弹窗里那一列要用。options 接口刻意不按 ERP 账号收窄（见仓库模块），
 * 所以这里拿到的是全部启用的仓，弹窗里默认带出的那家仓一定在里面
 */
const warehouseOptions = ref<any[]>([])

const getWarehouseOptions = async () => {
  try {
    const { data } = await optionsErpWarehouseApi()
    warehouseOptions.value = data || []
  } catch (error) {
  }
}

/**
 * 「采购退货」弹窗：草稿由后端按这张单汇总好带回来（一行一个商品，带 已入库 / 还能退 / 默认仓库 / 默认单价），
 * 这里只在草稿上填「本次退货数」「退回仓库」「单价」。提交后建的是 0 待出库 的单子，货还没离仓。
 * returnForm 用库里那份单据的形状（quantity=本次退货数、costPrice=单价），提交时才组请求体
 */
const returnOpen = ref(false)
const returnLoading = ref(false)
const returnSubmitting = ref(false)
const returnForm = reactive<any>({
  purchaseId: undefined,
  purchaseNo: undefined,
  supplierName: undefined,
  remark: undefined,
  items: [] as any[]
})

/** 行小计：退货这一路的单价列叫 costPrice（出库明细的字段名），和采购明细的 price 不是同一个 key */
const returnRowAmount = (row: any) =>
  (Number(row.quantity || 0) * Number(row.costPrice || 0)).toFixed(2)

const returnTotalQuantity = computed(() =>
  returnForm.items.reduce((sum: number, row: any) => sum + Number(row.quantity || 0), 0)
)
const returnTotalAmount = computed(() =>
  returnForm.items.reduce((sum: number, row: any) => sum + Number(returnRowAmount(row)), 0)
)

const handleReturn = async (row: any) => {
  Object.assign(returnForm, {
    purchaseId: row.id,
    purchaseNo: row.purchaseNo,
    supplierName: row.supplierName,
    remark: undefined,
    items: []
  })
  returnOpen.value = true
  returnLoading.value = true
  try {
    const { data } = await returnDraftErpPurchaseApi(row.id)
    returnForm.purchaseNo = data.orderNo || row.purchaseNo
    returnForm.supplierName = data.supplierName || row.supplierName
    returnForm.items = (data.items || []).map((item: any) => ({
      skuId: item.skuId,
      productName: item.productName,
      productCode: item.productCode,
      // 一条规格一行，商品名会重复出现，靠这一串分出去
      specText: item.specText,
      receivedQuantity: Number(item.receivedQuantity || 0),
      returnableQuantity: Number(item.returnableQuantity || 0),
      // 本次退多少由人填（草稿给 0），仓库和单价是后端按采购/入库记录带出的默认值，都能改
      quantity: 0,
      warehouseId: item.warehouseId,
      costPrice: item.costPrice == null ? undefined : Number(item.costPrice)
    }))
  } catch (error) {
    // 一件能退的都没有（后端直接报错）就别留一张空弹窗
    returnOpen.value = false
  } finally {
    returnLoading.value = false
  }
}

/** 提交前的完整性检查（后端 createPurchaseReturn 按额度还会再挡一遍，这里只为少一次往返） */
const checkReturn = () => {
  const picked = returnForm.items.filter((row: any) => Number(row.quantity || 0) > 0)
  if (!picked.length) {
    ElMessage.warning('请至少填写一件商品的退货数量')
    return false
  }
  for (const row of picked) {
    if (!row.warehouseId) {
      ElMessage.warning(`请填写「${row.productName || ''}${row.specText ? ` ${row.specText}` : ''}」退回哪个仓库`)
      return false
    }
  }
  return true
}

const submitReturn = async () => {
  if (!checkReturn()) return
  returnSubmitting.value = true
  try {
    // 只提交这四列 + 备注：单号、总数、总金额、状态一律后端按明细算（退 0 的行不提交）
    await createReturnErpPurchaseApi(returnForm.purchaseId, {
      remark: returnForm.remark,
      items: returnForm.items
        .filter((row: any) => Number(row.quantity || 0) > 0)
        .map((row: any) => ({
          skuId: row.skuId,
          warehouseId: row.warehouseId,
          quantity: Number(row.quantity || 0),
          costPrice: row.costPrice
        }))
    })
    ElMessage.success('退货单已创建，去「商品出库」确认出库后货才离仓')
    returnOpen.value = false
    getList()
  } catch (error) {
  } finally {
    returnSubmitting.value = false
  }
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 张采购单吗？明细会一起删掉；已经建过入库单的要先去「商品入库」删掉那些单。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteErpPurchaseApi(selectedIds.value)
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

const getSupplierOptions = async () => {
  try {
    const { data } = await optionsErpSupplierApi()
    supplierOptions.value = data || []
  } catch (error) {
  }
}

onMounted(() => {
  getList()
  getSupplierOptions()
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

/* 时间范围那一行（照抄收藏、购物车列表的 .search-row）：wrapper 独占整行把日期格顶到第二行，
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

/* 明细行里商品下拉的名称 + 编码（同商品下拉在购物车里的排法） */
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

/* 「入库单」弹窗：抬头那排说明文字 */
.receipt-head {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

/*
 * 「入库单」弹窗的隔色：只有外层那一张表斑马。EP 的条纹就是 --el-fill-color-lighter（默认 #fafafa，
 * 在这种带 border 的小表格里等于没色），所以换成蓝，hover 再压一档不然划过去看不出动静。
 * 展开的商品明细不再自己区分：整张内层表的底色改成透明（EP 的表根、tr、th 各有一个 bg 变量，三个都要清，
 * 不然还是一块白），--el-table-expanded-cell-bg-color 也留透明 —— 色由 .receipt-items-wrap 那层铺，
 * 才能跟着所属那一行（EP 展开区在 hover 时会把单元格刷成透明，色涂在单元格上会闪）
 */
.receipt-table {
  --el-fill-color-lighter: var(--el-color-primary-light-8);
  --el-table-row-hover-bg-color: var(--el-color-primary-light-7);
  --el-table-expanded-cell-bg-color: transparent;

  /* 内边距挪到包装层，色块才铺得满整个展开区（EP 默认给展开单元格留了 20px / 50px） */
  :deep(.el-table__expanded-cell) {
    padding: 0;
  }

  .receipt-items {
    --el-table-bg-color: transparent;
    --el-table-tr-bg-color: transparent;
    --el-table-header-bg-color: transparent;
  }
}

.receipt-items-wrap {
  padding: 20px 50px;

  /* 条纹按数据行下标算（奇数那条才有），展开行不带这个类，所以由这里按同一个下标补一次。
     颜色直接取外层那个条纹变量，两边不会走偏 */
  &.is-striped {
    background: var(--el-fill-color-lighter);
  }
}

/* 一张入库单都没有时的说明 */
.receipt-empty {
  margin-top: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

/* 入满的那一行数字：绿色加粗（同商品入库那页） */
.stock-done {
  color: var(--el-color-success);
  font-weight: 600;
}

/* 明细表格下面那排：左边提示/加行按钮，右边合计文案 */
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
