<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="请输入单号/采购单/供应商/仓库/备注" clearable
            @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="供应商" prop="supplierId">
          <el-select v-model="queryParams.supplierId" placeholder="全部" clearable filterable>
            <el-option v-for="item in supplierOptions" :key="item.id" :label="item.supplierName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="入库仓库" prop="warehouseId">
          <el-select v-model="queryParams.warehouseId" placeholder="全部" clearable filterable>
            <el-option v-for="item in warehouseOptions" :key="item.id" :label="item.warehouseName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>

        <!-- 时间范围 + 按钮同一行（照抄采购单、收藏列表的 .search-row）：这一层 wrapper 独占第二行，
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
          <el-button v-permission="['erp:purchaseIn:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['erp:purchaseIn:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" :selectable="canSelect" />
        <el-table-column label="入库单号" prop="receiptNo" align="center" min-width="190" />
        <!-- 来源单据号/仓库/操作人名称都是查询时联表现取的，改过名不用回头刷单子。
             这一列两路共用：入库来源=2 采购入库 是采购单号，1 线上退货 是退款单号（回调建的那几张没有采购单） -->
        <el-table-column label="来源单据" align="center" min-width="180">
          <template #default="{ row }">
            <span>{{ sourceNoOf(row) || '未关联' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="入库来源" align="center" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_SOURCE, row.inboundSource)">
              {{ dict.label(DICT_SOURCE, row.inboundSource) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_TYPE, row.status)">
              {{ dict.label(DICT_TYPE, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="入库仓库" align="center" min-width="150">
          <template #default="{ row }">
            <span>{{ row.warehouseName || `已删除仓库 #${row.warehouseId}` }}</span>
            <!-- 盘点中 / 调拨中 的仓账是冻住的，这一单入不了货：颜色不给成 dict.tag 的默认蓝，
                 这一格要的是"一眼看出异常"，不是回显状态 -->
            <el-tag v-if="warehouseFrozen(row)" class="warehouse-frozen" size="small" type="warning">
              {{ dict.label(DICT_WAREHOUSE, row.warehouseStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="入库总数" prop="totalQuantity" align="center" width="100" />
        <!-- 已入库是明细 received_quantity 现汇总出来的（列表 SQL 里的子查询），入过几次货一目了然 -->
        <el-table-column label="已入库" align="center" width="90">
          <template #default="{ row }">
            <span :class="{ 'stock-done': allReceived(row) }">{{ row.totalReceived || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="入库金额" align="center" width="130">
          <template #default="{ row }">
            <span>{{ priceText(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="180" fixed="right">
          <template #default="scope">
            <!-- 按钮超过三个由 TableMoreActions 收进「更多」下拉（口径同部门管理）。
                 hidden 是不渲染（权限、入满的单子这里只剩「详情」—— 货已经按这张单的明细入进库存了，
                 再改/再删都和库存账对不上，服务端同样拦；入没入满看状态列和「已入库」那一格）；
                 disabled 是渲染但点不动 + 悬浮说原因：这家入库仓正在盘点/调拨，账已冻结，先改回启用才能入库 -->
            <TableMoreActions :actions="[
              { label: '入库', type: 'success', icon: 'Box', hidden: !hasPermission('erp:purchaseIn:receive') || allReceived(scope.row), disabled: warehouseFrozen(scope.row), tip: warehouseFrozen(scope.row) ? frozenTip(scope.row) : undefined, command: { type: 'receive', row: scope.row } },
              { label: '修改', icon: 'Edit', hidden: !hasPermission('erp:purchaseIn:update') || isReceived(scope.row), loading: updatingId === scope.row.id, command: { type: 'update', row: scope.row } },
              { label: '详情', icon: 'View', hidden: !hasPermission('erp:purchaseIn:detail'), command: { type: 'detail', row: scope.row } },
              { label: '删除', type: 'danger', icon: 'Delete', hidden: !hasPermission('erp:purchaseIn:delete') || isReceived(scope.row), command: { type: 'delete', row: scope.row } }
            ]" @command="handleActionCommand" />
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页工具栏 -->
      <div class="pagination-container">
        <el-pagination background v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 30, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>

      <!-- 添加或修改对话框：比采购单那个宽一档，明细多了一列「可入库」，960 会横向滚 -->
      <el-dialog v-model="open" :title="title" width="1080px" append-to-body destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
          <el-divider content-position="left">基本信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="入库单号" prop="receiptNo">
                <el-input v-model="form.receiptNo" placeholder="保存后由系统生成" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="入库来源" prop="inboundSource">
                <!-- 只有新增能选：来源定死这张单挂哪张来源单据（2 采购入库 → 采购单、1 线上退货 → 退款单，
                     由退款回调建），改单时把它换掉等于把单子挪到另一条来源线上，对不上原来的账。
                     它同时管着「采购单」这一格必不必填、明细是从采购单带出还是手工加行 -->
                <el-select v-model="form.inboundSource" :disabled="form.id != null" style="width: 100%"
                  @change="handleSourceChange">
                  <el-option v-for="item in sourceOptions" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12" v-if="isPurchaseSource">
              <el-form-item label="采购单" prop="purchaseId">
                <!-- 候选只列「已审核」的采购单：货全登记进入库单的那张会自动流转成「待入库」，入满的成「已入库」，
                     两张都不会出现在这里（见 getPurchaseOptions）；一单选一张，选了就把它的明细整块带进来（见 handlePurchaseChange） -->
                <el-select v-model="form.purchaseId" placeholder="请选择采购单" filterable style="width: 100%"
                  :loading="purchaseLoading" @change="handlePurchaseChange">
                  <el-option v-for="item in purchaseOptions" :key="item.id" :label="item.purchaseNo"
                    :value="item.id">
                    <div class="product-option">
                      <span class="product-option__name">{{ item.purchaseNo }}</span>
                      <span class="product-option__code">{{ item.supplierName || '-' }}</span>
                    </div>
                  </el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12" v-if="isPurchaseSource">
              <el-form-item label="供应商" prop="supplierName">
                <!-- 不是表单列：主表 supplier_id 由后端按采购单回填，这里只是回显，免得录歪。
                     非采购来源没有供应商这一说（回调那一路 supplier_id 就是 NULL），整格收掉 -->
                <el-input :model-value="form.supplierName" placeholder="选采购单后自动带出" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="入库仓库" prop="warehouseId">
                <!-- 选了采购单就只挑这家门店名下的仓（外加没挂门店的共用仓），见 formWarehouseOptions -->
                <el-select v-model="form.warehouseId"
                  :placeholder="purchaseStoreId ? '请选择仓库（候选按采购单所属门店筛）' : '请选择仓库'"
                  filterable style="width: 100%">
                  <el-option v-for="item in formWarehouseOptions" :key="item.id" :label="item.warehouseName"
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
              <el-form-item label="入库操作人" prop="userId">
                <!-- 只列 ERP 操作员账号（account_type=5）、停用账号不列；不选就是没记（NULL 能存回去）。
                     ERP 账号自己录单时这一格填成本人并禁用（列表按他收窄，记成别人的话他自己找不到这张单） -->
                <user-select v-model="form.userId" :label="form.userName" only-enabled :account-type="5"
                  :disabled="isErpAccount" style="width: 100%" @select="handleOperatorSelect"
                  @clear="handleOperatorClear" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态" prop="status">
                <!-- 状态由「入库」动作按明细进度算（待入库 → 部分入库 → 已入库），表单里只回显不给选 -->
                <el-input :model-value="dict.label(DICT_TYPE, form.status)" placeholder="保存后由系统按入库进度流转"
                  disabled />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="备注" prop="remark">
                <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" maxlength="500"
                  show-word-limit />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">入库明细</el-divider>
          <div class="items-toolbar">
            <!-- 删掉的行后悔了不用重选采购单（那一下会把整块明细覆盖掉），按采购单重新捞一次待入的行 -->
            <el-button v-if="isPurchaseSource" type="primary" plain icon="Plus" size="small"
              :disabled="!form.purchaseId" :loading="purchaseLoading" @click="importItems">导入明细</el-button>
            <!-- 非采购来源没有"从来源单据带明细"这一说，规格自己从商品库里挑（口径同供应商品那页的选择器） -->
            <el-button v-else type="primary" plain icon="Plus" size="small" @click="addItem">添加规格</el-button>
          </div>
          <!-- 采购入库这一路不给"添加行"：能入的规格就是采购单里那几行里还有余量的那些（后端按 sku_id
               对着采购单挡，被别的入库单登记满的规格压根不带进来），所以这一路只能删行（部分入库）和改数量/单价。
               非采购来源那一路没有来源单据可带，规格自己挑（见上面的「添加规格」） -->
          <el-table :data="form.items" size="small" border>
            <el-table-column label="商品规格" min-width="240">
              <template #default="{ row }">
                <shop-product-sku-select v-if="!isPurchaseSource" v-model="row.skuId" :label="skuText(row)"
                  @select="handleItemProductSelect(row, $event)" @clear="handleItemProductClear(row)" />
                <div v-else class="product-option">
                  <span class="product-option__name">{{ row.productName || `已删除规格 #${row.skuId}` }}</span>
                  <span class="product-option__spec">{{ row.specText || '默认规格' }}</span>
                  <span class="product-option__code">{{ row.productCode }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column v-if="isPurchaseSource" label="采购数量" width="100" align="center">
              <template #default="{ row }">
                <span>{{ row.purchaseQuantity == null ? '-' : row.purchaseQuantity }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已入库" width="90" align="center">
              <template #default="{ row }">
                <span>{{ row.receivedQuantity || 0 }}</span>
              </template>
            </el-table-column>
            <!-- 可入库 = 采购数量 − 这张采购单下其它入库单已经登记的量（后端算的，选了采购单那一下才有值） -->
            <el-table-column v-if="isPurchaseSource" label="可入库" width="90" align="center">
              <template #default="{ row }">
                <span :class="{ 'stock-empty': isExhausted(row) }">
                  {{ row.availableQuantity == null ? '-' : row.availableQuantity }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="入库数量" width="130" align="center">
              <template #default="{ row }">
                <!-- 下限是已入库数：这行已经入过货的话，计划数不能改到比实际还小（后端 validate 也会挡）；
                     上限是可入库数，别的入库单占掉的那部分不在这格里填得进去 -->
                <el-input-number v-model="row.quantity" :min="planMin(row)" :max="quantityMax(row)"
                  :disabled="isExhausted(row)" :controls="false" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="单价(成本价)" width="140" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.price" :min="0" :precision="2" :controls="false"
                  :disabled="isExhausted(row)" style="width: 100%" />
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
            <span class="items-tip" v-if="!isPurchaseSource">这一路没有来源采购单：点上面的「添加规格」逐行加，入库数量和单价(成本价)自己填（货入进库存按这一格的单价加权）</span>
            <span class="items-tip" v-else-if="!form.purchaseId">请先在上方选择采购单，明细会自动带出</span>
            <span class="items-tip" v-else>入库数量默认按「可入库」填满；已被别的入库单登记满的规格不会带进来（误删的行点上面的「导入明细」找回，入过货的行不能删）</span>
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
        入库单详情：只读看这一单的来龙去脉 —— 计划多少、实际进了多少、还剩多少。
        要改计划去「修改」，要登记到货去「入库」，这里两样都不给动（口径同商品库存那页的详情）。
        右上角「打印」把这一张单连明细交给浏览器打印窗口，在那里选「另存为 PDF」就是 PDF。
      -->
      <el-dialog v-model="detailOpen" width="900px" append-to-body destroy-on-close>
        <template #header>
          <div class="dialog-head">
            <span class="el-dialog__title">入库单详情</span>
            <el-button v-if="detail.receiptNo" type="primary" plain size="small"
              @click="handleDetailPrint">打 印</el-button>
          </div>
        </template>
        <el-descriptions title="基本信息" :column="2" border v-loading="detailLoading">
          <el-descriptions-item label="入库单号">{{ detail.receiptNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源单据">{{ sourceNoOf(detail) || '未关联' }}</el-descriptions-item>
          <el-descriptions-item label="入库来源">
            <el-tag size="small" :type="dict.tag(DICT_SOURCE, detail.inboundSource)">
              {{ dict.label(DICT_SOURCE, detail.inboundSource) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="供应商">
            {{ detail.supplierName || (detail.supplierId ? `已删除供应商 #${detail.supplierId}` : '-') }}
          </el-descriptions-item>
          <el-descriptions-item label="入库仓库">
            {{ detail.warehouseName || (detail.warehouseId ? `已删除仓库 #${detail.warehouseId}` : '-') }}
            <el-tag v-if="warehouseFrozen(detail)" class="warehouse-frozen" size="small" type="warning">
              {{ dict.label(DICT_WAREHOUSE, detail.warehouseStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="dict.tag(DICT_TYPE, detail.status)">
              {{ dict.label(DICT_TYPE, detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detail.userName || '未记录' }}</el-descriptions-item>
          <!-- 三个数都是服务端按明细算的：计划总数/入库金额来自主表，已入库是明细现汇总的子查询 -->
          <el-descriptions-item label="计划总数">{{ detail.totalQuantity || 0 }}</el-descriptions-item>
          <el-descriptions-item label="已入库">
            <span :class="{ 'stock-done': allReceived(detail) }">{{ detail.totalReceived || 0 }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="待入库">{{ pendingTotal(detail) }}</el-descriptions-item>
          <el-descriptions-item label="入库金额">{{ priceText(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="入库时间">
            {{ validate.formatTime(detail.receiveTime, 'YYYY-MM-DD') || '-' }}
          </el-descriptions-item>
          <!-- 加了「入库来源」之后一共 13 项，这一格不给 span 就会自己占一整行、左边空一格 -->
          <el-descriptions-item label="创建时间" :span="2">
            {{ validate.formatTime(detail.createTime, 'YYYY-MM-DD') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">入库明细</el-divider>
        <el-table :data="detail.items" size="small" border v-loading="detailLoading">
          <el-table-column label="商品名称" min-width="180">
            <template #default="{ row }">
              <el-tooltip :content="productNameTip(row)" placement="top">
                <div class="product-option">
                  <span class="product-option__name">{{ row.productName || `已删除规格 #${row.skuId}` }}</span>
                  <span class="product-option__code">{{ row.productCode }}</span>
                </div>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="规格" align="center" min-width="100">
            <template #default="{ row }">
              <span>{{ row.specText || '默认规格' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="计划入库" width="100" align="center">
            <template #default="{ row }">
              <span>{{ row.quantity || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已入库" width="100" align="center">
            <template #default="{ row }">
              <span :class="{ 'stock-done': remainingOf(row) === 0 }">{{ row.receivedQuantity || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="剩余" width="90" align="center">
            <template #default="{ row }">
              <span>{{ remainingOf(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单价(成本价)" width="130" align="center">
            <template #default="{ row }">
              <span>{{ priceText(row.price) }}</span>
            </template>
          </el-table-column>
          <!-- 小计读库里的 amount：它是服务端按 数量 × 单价 算好落库的那个数，不是这里现乘的 -->
          <el-table-column label="小计" width="120" align="center">
            <template #default="{ row }">
              <span>{{ priceText(row.amount) }}</span>
            </template>
          </el-table-column>
        </el-table>
        <template #footer>
          <el-button @click="detailOpen = false">关 闭</el-button>
        </template>
      </el-dialog>

      <!--
        入库详情：这张单实际到了多少货就在这里逐行填。
        跟上面的「修改」弹窗是两件事——那边改的是计划（打算入多少），这边登记的是实际（这次真入了多少），
        所以这里不给删行、也不给改计划数，只有「本次入库数」和「单价」两格能填。
        提交成功后货才真正进库存表（加权平均成本价），单据和采购单的状态一起流转。
      -->
      <el-dialog v-model="receiveOpen" title="确认入库" width="960px" append-to-body destroy-on-close>
        <el-form label-width="110px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="入库单号">
                <el-input :model-value="receiveForm.receiptNo" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="来源单据">
                <el-input :model-value="sourceNoOf(receiveForm) || '未关联'" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="入库来源">
                <!-- 只回显：来源决定这批货挂的是采购单还是退款单、数量上限由谁管，不是这一单能改的事 -->
                <el-input :model-value="dict.label(DICT_SOURCE, receiveForm.inboundSource)" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="供应商">
                <el-input :model-value="receiveForm.supplierName" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="入库仓库">
                <el-input :model-value="receiveForm.warehouseName" disabled />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">入库明细</el-divider>
          <el-table :data="receiveForm.items" size="small" border v-loading="receiveLoading">
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
            <el-table-column label="计划入库" width="90" align="center">
              <template #default="{ row }">
                <span>{{ row.quantity || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已入库" width="90" align="center">
              <template #default="{ row }">
                <span>{{ row.receivedQuantity || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="剩余" width="90" align="center">
              <template #default="{ row }">
                <span>{{ remainingOf(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="本次入库数" width="130" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.thisQuantity" :min="0" :max="remainingOf(row)" :controls="false"
                  style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="单价(成本价)" width="140" align="center">
              <template #default="{ row }">
                <!-- 默认带计划单价，实际到货价不一样就改这里：入库单明细的 price 和库存的加权成本价都按它算 -->
                <el-input-number v-model="row.price" :min="0" :precision="2" :controls="false" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="小计" width="120" align="center">
              <template #default="{ row }">
                <span>{{ priceText(thisAmount(row)) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="items-footer">
            <span class="items-tip">这次没到的商品把「本次入库数」留 0 即可，剩下的量下次还能继续入</span>
            <span class="items-total">本次数量 {{ receiveTotalQuantity }} · 本次金额
              {{ priceText(receiveTotalAmount) }}</span>
          </div>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="receiveOpen = false">取 消</el-button>
            <el-button type="primary" :loading="receiveSubmitting" @click="submitReceive">确认入库</el-button>
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
  listErpPurchaseInApi,
  detailErpPurchaseInApi,
  optionsErpPurchaseInItemApi,
  addErpPurchaseInApi,
  updateErpPurchaseInApi,
  deleteErpPurchaseInApi,
  receiveErpPurchaseInApi
} from '@/api/erp/purchaseIn'
import { listErpPurchaseApi, detailErpPurchaseApi } from '@/api/erp/purchase'
import { optionsErpSupplierApi } from '@/api/erp/supplier'
import { optionsErpWarehouseApi } from '@/api/erp/warehouse'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import TableMoreActions from '@/components/TableMoreActions/index.vue'
import ShopProductSkuSelect from '@/components/ShopProductSkuSelect/index.vue'
import validate from '@/utils/validate'
import { printReceipts } from '@/utils/print'
import { useDict } from '@/utils/dictCache'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
/** 账号类型：0平台 1门店 2送货员 3消费者 4供应商 5ERP操作员；ERP 账号只看/只改自己操作的那几张单 */
const isErpAccount = computed(() => userStore.user.accountType === 5)

/** 操作列走 TableMoreActions，权限得在这里自己判（组件没有 v-permission 那套指令，只认 disabled） */
const permissions = computed(() => userStore.user.permissions || [])
const hasPermission = (permission: string): boolean => {
  return permissions.value.includes(permission)
}

/**
 * 入库单状态字典：purchase_in_status = status
 * 入库来源字典：inbound_source = inbound_source（1线上退货 2采购入库 3销货退回 4调拨入库 5盘盈入库 6其他，
 * 和出库单那套 sale_outbound_source 是镜像的号：1 线上退货 ↔ 1 线上购买、2 采购入库 ↔ 3 采购退货）
 * 仓库状态字典：warehouse_status = 列表联表带出的 warehouseStatus（不是本单的 status！）
 */
const DICT_TYPE = 'purchase_in_status'
const DICT_SOURCE = 'inbound_source'
const DICT_WAREHOUSE = 'warehouse_status'
const dict = useDict(DICT_TYPE, DICT_SOURCE, DICT_WAREHOUSE)

/**
 * 仓库的 2 盘点中 / 3 调拨中 这两档把账冻住了：仓里的货正在数或者正在挪，
 * 这时候再入货，盘点结果和调拨单都对不上（服务端在库存记账那层拦同样的两档）。
 * 禁用/启用不参与：禁用仓压根进不了下拉，能建出单来的都是当时选得到的仓
 */
const WAREHOUSE_FROZEN_STATUS = [2, 3]
const warehouseFrozen = (row: any): boolean =>
  WAREHOUSE_FROZEN_STATUS.includes(Number(row?.warehouseStatus))
/** 置灰「入库」时要说清冻的是哪家仓、冻在哪一档，以及去哪儿解开 */
const frozenTip = (row: any): string =>
  `仓库「${row?.warehouseName || row?.warehouseId}」${dict.label(DICT_WAREHOUSE, row?.warehouseStatus)}，`
  + '账已冻结，先在「仓库管理」里把它改回启用才能入库'

/** 入库来源：2 采购入库 —— 唯一挂采购单的那一路（供应商和明细都从它带出来） */
const SOURCE_PURCHASE = 2

const loading = ref(true)
const submitLoading = ref(false)
const purchaseLoading = ref(false)
const selectedIds = ref<any[]>([])
const total = ref(0)
const dataList = ref<any[]>([])
const title = ref('')
const open = ref(false)

/** 入库（确认到货）弹窗：跟新增/修改那套状态分开，免得两个弹窗互相踩表单 */
const receiveOpen = ref(false)
const receiveLoading = ref(false)
const receiveSubmitting = ref(false)
const receiveForm = reactive<any>({ items: [] as any[] })

/** 详情弹窗：只读，数据每次点开重新捞一遍（已入库数会被别的操作员推进） */
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
  supplierId: undefined,
  warehouseId: undefined,
  status: undefined
})
const dateRange = ref<string[]>([])

// 表单参数：items 是明细数组，整单一起提交
const form = reactive<any>({ items: [] as any[] })

/**
 * 这一张是不是「2 采购入库」那一路（来源为空按 2 算，后端 validate 同样这么兜底）：
 * 来源决定采购单必不必填、明细是从采购单整块带出还是手工加行
 */
const isPurchaseSource = computed(() =>
  Number(form.inboundSource ?? SOURCE_PURCHASE) === SOURCE_PURCHASE
)

/**
 * 新增只有「2 采购入库」这一档可选：后台建的单必须挂得住一张采购单（服务端 validate 对新建同样挡），
 * 其余来源由各自的回调按来源单据生成（1 线上退货 ← 退款单），手建出来那张单没有来源、也没有额度上限。
 * 改单这一格是禁用的，候选要给全 —— 否则库里 1 线上退货 那张打开会变成空
 */
const sourceOptions = computed(() =>
  form.id == null
    ? dict.options(DICT_SOURCE).filter((item: any) => Number(item.value) === SOURCE_PURCHASE)
    : dict.options(DICT_SOURCE)
)

/**
 * 必填跟着来源走：非采购来源那一格连控件都不渲染（采购单、供应商两格一起收掉），
 * 所以规则也得同步摘掉 —— 否则提交前 validate 会卡在一个页面上根本看不见的字段上
 */
const rules = computed<FormRules>(() => {
  const result: FormRules = {
    warehouseId: [{ required: true, message: '请选择入库仓库', trigger: 'change' }]
  }
  if (isPurchaseSource.value) {
    result.purchaseId = [{ required: true, message: '请选择采购单', trigger: 'change' }]
  }
  return result
})

/** 来源单据号一列两路共用：2 采购入库 是采购单号，1 线上退货 是退款单号（都是后端联表现取的） */
const sourceNoOf = (row: any) => row.purchaseNo || row.refundNo

/** 金额的显示口径同采购单（后端返回的 decimal 是字符串，统一走 Number） */
const priceText = (price: any) => (price == null || price === '' ? '￥0.00' : `￥${Number(price).toFixed(2)}`)

/** 行小计：只是录入时的回显，落库的 amount 由服务端按 数量 × 单价 重算（口径一致，不冲突） */
const rowAmount = (row: any) => (Number(row.quantity || 0) * Number(row.price || 0)).toFixed(2)

const totalQuantity = computed(() =>
  form.items.reduce((sum: number, row: any) => sum + Number(row.quantity || 0), 0)
)
const totalAmount = computed(() =>
  form.items.reduce((sum: number, row: any) => sum + Number(rowAmount(row)), 0)
)

/**
 * 整单是否已入满：totalReceived 是列表/详情 SQL 里按明细现汇总出来的那一列。
 * totalQuantity 为 0（明细被清空）不算入满，这种单子本来就该先去「修改」里把明细填回来。
 */
const allReceived = (row: any) =>
  Number(row.totalQuantity || 0) > 0 && Number(row.totalReceived || 0) >= Number(row.totalQuantity || 0)

/**
 * 状态已是「2 已入库」（字典 purchase_in_status）：这一档的单子不能再改、不能再删，
 * 货已经按它入进库存了。看状态而不是现算的数量，是因为状态列就是这一页对外的口径
 */
const RECEIPT_STATUS_RECEIVED = 2
const isReceived = (row: any) => Number(row.status) === RECEIPT_STATUS_RECEIVED

/** 已入库的单子不给勾，进不了「批量删除」的范围（服务端同样拦） */
const canSelect = (row: any) => !isReceived(row)

/** 整单还剩多少要入 = 计划总数 − 已入库（详情弹窗用；行级别的剩余见 remainingOf） */
const pendingTotal = (row: any) =>
  Math.max(0, Number(row.totalQuantity || 0) - Number(row.totalReceived || 0))

/** 计划入库数下限：已经入过货的行不能把计划改到比实际还小（没入过的可以从 0 起，0 由 checkItems 拦） */
const planMin = (row: any) => Math.max(0, Number(row.receivedQuantity || 0))

/** 明细行还剩多少可入 = 计划 - 已入库（入库弹窗里既是回显也是 el-input-number 的上限） */
const remainingOf = (row: any) =>
  Math.max(0, Number(row.quantity || 0) - Number(row.receivedQuantity || 0))

/**
 * 计划入库数上限：能拿到「可入库数」就按它（后端算的，同 validate 的口径，已经扣掉这张采购单下
 * 其它入库单登记过的量 —— 占完了就是 0，这一格跟着锁在 0，和「可入库」那列显示同一个数），
 * 拿不到可入库数就退回采购数量、再没有就不限
 */
const quantityMax = (row: any) => {
  if (row.availableQuantity != null) return Number(row.availableQuantity)
  return row.purchaseQuantity == null ? undefined : Number(row.purchaseQuantity)
}

/**
 * 可入库被别的入库单占完（0）：这一行的数量和单价都不给填。
 * 走正常回填/导入已经不会出现这种行（`toInboundRows` 把它挡在外面），这里留着是防着后端改了口径
 */
const isExhausted = (row: any) => row.availableQuantity != null && Number(row.availableQuantity) === 0

/** 本次入库的小计：同样只是录入时看，落库的 amount 由服务端按 数量 × 单价 重算 */
const thisAmount = (row: any) => (Number(row.thisQuantity || 0) * Number(row.price || 0)).toFixed(2)

const receiveTotalQuantity = computed(() =>
  receiveForm.items.reduce((sum: number, row: any) => sum + Number(row.thisQuantity || 0), 0)
)
const receiveTotalAmount = computed(() =>
  receiveForm.items.reduce((sum: number, row: any) => sum + Number(thisAmount(row)), 0)
)

const supplierOptions = ref<any[]>([])
const warehouseOptions = ref<any[]>([])
const purchaseOptions = ref<any[]>([])

/**
 * 所选采购单的「所属门店」：不是本单的列，只是拿来收窄下面那格「入库仓库」的候选。
 * 新增时由 handlePurchaseChange 从采购单候选里带，修改回显时由 fillPurchaseQuantity 从采购单详情带
 */
const purchaseStoreId = ref<any>(undefined)

/**
 * 这张采购单能入哪些仓：门店名下的仓 + 没挂门店的共用仓。
 * 返回 null 表示不收窄（非采购来源、或者加 store_id 之前建的老采购单没门店）
 */
const purchaseWarehouseScope = computed<any[] | null>(() => {
  if (!isPurchaseSource.value || !purchaseStoreId.value) return null
  const list = warehouseOptions.value
  const own = list.filter((item: any) => String(item.storeId) === String(purchaseStoreId.value))
  const shared = list.filter((item: any) => item.storeId == null)
  return [...own, ...shared]
})

/**
 * 入库仓库候选。选了采购单就按它的门店收窄（purchaseWarehouseScope）：这家门店名下的仓排在前面，
 * 后面再接没挂门店的共用仓（主仓/分仓的 store_id 是 NULL，本来就给所有门店共用，不能挡掉，见仓库模块）；
 * 非采购来源、以及加 store_id 之前建的老采购单（没门店）都退回全部仓库。
 * 已选中的那一格即便不在范围内也补进候选，否则 el-select 匹配不上就显示裸 id（同销售页那一格）——
 * 换采购单那一下该不该清由 handlePurchaseChange 判，判据用的是上面那份不含兜底的范围
 */
const formWarehouseOptions = computed(() => {
  const scope = purchaseWarehouseScope.value
  if (!scope) return warehouseOptions.value
  if (form.warehouseId && !scope.some((item: any) => String(item.id) === String(form.warehouseId))) {
    const selected = warehouseOptions.value.find((item: any) => String(item.id) === String(form.warehouseId))
    if (selected) return [selected, ...scope]
  }
  return scope
})

/** 采购明细行 → 入库明细行：字段口径见 handlePurchaseChange 上的注释 */
const toInboundItem = (item: any) => ({
  skuId: item.skuId,
  productName: item.productName,
  productCode: item.productCode,
  specText: item.specText,
  // 默认就是可入库数（能被带进来的行可入库一定 > 0，见 toInboundRows）
  quantity: Number(item.availableQuantity || 0),
  price: item.price == null ? undefined : Number(item.price),
  // 这两个都不是明细列：只是挂在这一行上显示 + 当上限用，后端按实体反序列化时会忽略
  purchaseQuantity: Number(item.quantity || 0),
  availableQuantity: Number(item.availableQuantity || 0)
})

/**
 * 采购明细候选 → 表格里的行：只留还有可入库量的那一批。
 * 可入库 = 采购数量 − 这张采购单下**其它**入库单已登记的量，是 0 就说明这个规格已经被别的单子登记满了，
 * 带进来只能看不能改（数量那格锁在 0、提交必被后端挡），所以干脆不展示（2026-09-19 他改的口径：
 * 「导入明细 和选中采购单回填 只回填可入库商品，已有入库单的商品不再展示」）。
 * 「选采购单带出」和「导入明细」两个入口都走这里，保持口径一致
 */
const toInboundRows = (list: any[]) =>
  (list || []).map(toInboundItem).filter((item: any) => Number(item.availableQuantity) > 0)

/**
 * 选采购单 → 整块明细跟着换：规格、单价都从采购单带（能入的规格就是它里面那几行），
 * 入库数量默认「可入库数量」—— 后端按 采购数量 − 这张采购单下其它入库单已登记的量 算出来的，
 * 和提交时 validate 的上限同一口径，所以默认值不会一提交就报超量。
 * 已被别的入库单登记满的规格不会出现在这里（见 toInboundRows）。
 * el-select 的 change 只在用户操作时触发，编辑弹窗回显不会走到这里
 */
const handlePurchaseChange = async (purchaseId: any) => {
  const purchase = purchaseOptions.value.find((item: any) => String(item.id) === String(purchaseId))
  form.supplierId = purchase?.supplierId
  form.supplierName = purchase?.supplierName
  purchaseStoreId.value = purchase?.storeId
  // 仓库候选跟着这张单的门店收窄了，之前挑的那个仓可能已经不在这家门店名下 —— 清空重挑，
  // 别留着一格把货入错仓（候选里为了回显会兜底补上已选项，所以判据问范围本身、不问候选）
  const scope = purchaseWarehouseScope.value
  if (form.warehouseId && scope && !scope.some((item: any) => String(item.id) === String(form.warehouseId))) {
    form.warehouseId = undefined
    ElMessage.warning('已清空所选入库仓库：它不属于这张采购单的门店')
  }
  if (!purchaseId) {
    form.items = []
    return
  }
  purchaseLoading.value = true
  try {
    // form.id 是正在改的那张入库单：它自己已登记的量不该算成别人占的额度
    const { data } = await optionsErpPurchaseInItemApi(purchaseId, form.id)
    form.items = toInboundRows(data)
  } catch (error) {
    form.items = []
  } finally {
    purchaseLoading.value = false
  }
}

/**
 * 「导入明细」：按采购单重新捞一遍明细，只补表格里缺的行（同 skuId 已存在的跳过，
 * 已有行一律不动 —— 否则手填的数量会被冲掉）。捞出来的行和选采购单那一下完全一样：
 * 同样只带可入库 > 0 的规格，已被别的入库单登记满的那些行不展示（见 toInboundRows）。
 */
const importItems = async () => {
  if (!form.purchaseId) {
    ElMessage.warning('请先选择采购单')
    return
  }
  purchaseLoading.value = true
  try {
    const { data } = await optionsErpPurchaseInItemApi(form.purchaseId, form.id)
    const candidates = toInboundRows(data)
    if (candidates.length === 0) {
      ElMessage.warning('这张采购单的规格都已被别的入库单登记满了，没有可入库的商品规格')
      return
    }
    const rows = candidates.filter((item: any) => {
      return !form.items.some((row: any) => String(row.skuId) === String(item.skuId))
    })
    if (rows.length === 0) {
      ElMessage.info('这张采购单可入库的规格已经全在表格里了，没有要补的行')
      return
    }
    form.items.push(...rows)
    ElMessage.success(`已导入 ${rows.length} 行`)
  } catch (error) {
    // 拉取失败就维持现状，别把表格里已经填好的行清掉（报错弹窗由请求拦截器统一给）
  } finally {
    purchaseLoading.value = false
  }
}

/**
 * 换来源（只有新增能换，见表单里那格的 disabled）：非采购入库那一路没有采购单，也就没有
 * "从采购单带明细"和那两道按采购量算的上限，所以把来源单据那几格清掉
 * （后端 validate 同样会把 purchase_id / supplier_id 写成 NULL）。
 * 已经填好的明细行留着 —— 换来源只是这批货不走采购单了，行本身还是那些商品；
 * 但要把 采购数量/可入库 这两个"从采购单带来的上限"一并抹掉，否则数量那格会被它们挡死
 */
const handleSourceChange = (source: any) => {
  if (Number(source) === SOURCE_PURCHASE) return
  form.purchaseId = undefined
  form.purchaseNo = undefined
  form.supplierId = undefined
  form.supplierName = undefined
  // 来源不是采购了就没有"按采购单门店筛仓库"这一说，候选退回全部
  purchaseStoreId.value = undefined
  // 这一格的规则跟着来源摘掉了（见 rules），残留的报错要自己清
  formRef.value?.clearValidate('purchaseId')
  form.items.forEach((row: any) => {
    row.purchaseQuantity = undefined
    row.availableQuantity = undefined
  })
}

/** 非采购来源手工加行：规格自己挑，数量和单价留空由人填（后端的判据只有"数量 > 0、单价不能空"） */
const addItem = () => {
  form.items.push({
    skuId: undefined,
    productName: undefined,
    productCode: undefined,
    specText: undefined,
    quantity: undefined,
    price: undefined,
    receivedQuantity: 0
  })
}

/** 这一行给选择器看的文本：一个商品名下可能有好几条规格，光有商品名分不出挑的是哪一条 */
const skuText = (row: any) => `${row.productName || ''}${row.specText ? ` ${row.specText}` : ''}`.trim()

/**
 * 表格里商品那一格会被截短，而溢出的是格子里那层 span（EP 的列级 show-overflow-tooltip 量的是单元格本身，
 * 量不到也就不弹），所以全名由格子里自己包一层 el-tooltip 给
 */
const productNameText = (row: any) => row.productName || `已删除规格 #${row.skuId}`
const productNameTip = (row: any) => [productNameText(row), row.productCode].filter(Boolean).join(' · ')
const productSpecTip = (row: any) =>
  [productNameText(row), row.specText || '默认规格', row.productCode].filter(Boolean).join(' · ')

/** 规格选择器抛整行回来：名称/编码/规格只为这一行回显用（明细表没这几列，后端按实体反序列化会忽略） */
const handleItemProductSelect = (row: any, sku: any) => {
  row.productName = sku.productName
  row.productCode = sku.productCode
  row.specText = sku.specText
}

const handleItemProductClear = (row: any) => {
  row.productName = undefined
  row.productCode = undefined
  row.specText = undefined
}

/**
 * 删明细行（= 这行不入库了）。已经入过货的行不能删：删掉之后 received_quantity 就没了归属，
 * 货明明进了库存、单据上却查不到来源，后端 validate 也会挡，这里先拦一下。
 */
const removeItem = (index: number) => {
  const row = form.items[index]
  if (Number(row?.receivedQuantity || 0) > 0) {
    ElMessage.warning(`「${skuText(row) || '该规格'}」已入库 ${row.receivedQuantity} 件，不能删掉这行`)
    return
  }
  form.items.splice(index, 1)
}

/** 明细的完整性靠这里挡一遍（后端 validate 还会再挡一遍，这里只为少一次往返） */
const checkItems = () => {
  if (isPurchaseSource.value && !form.purchaseId) {
    ElMessage.warning('请先选择采购单')
    return false
  }
  if (!form.items.length) {
    ElMessage.warning('入库明细不能为空，请至少保留一行商品规格')
    return false
  }
  for (let i = 0; i < form.items.length; i++) {
    const row = form.items[i]
    if (!row.skuId || row.quantity == null || row.price == null) {
      ElMessage.warning(`第 ${i + 1} 行明细的商品规格/数量/单价没填全`)
      return false
    }
    // 可入库被别的入库单占完时默认就是 0，这种行留着也提交不了（后端要求每行数量 > 0）
    if (Number(row.quantity) <= 0) {
      ElMessage.warning(`第 ${i + 1} 行明细的入库数量要大于 0；这行不入库就直接删掉那一行`)
      return false
    }
    if (row.purchaseQuantity != null && Number(row.quantity) > Number(row.purchaseQuantity)) {
      ElMessage.warning(`第 ${i + 1} 行明细的入库数量超过了采购数量 ${row.purchaseQuantity}`)
      return false
    }
    // 可入库数才是真上限：同一张采购单下别的入库单可能已经登记过一部分量（后端 validate 同口径）
    if (row.availableQuantity != null && Number(row.quantity) > Number(row.availableQuantity)) {
      ElMessage.warning(
        `第 ${i + 1} 行明细的可入库数量只有 ${row.availableQuantity}（这张采购单的其余量已被别的入库单登记）`)
      return false
    }
    if (Number(row.quantity) < Number(row.receivedQuantity || 0)) {
      ElMessage.warning(`第 ${i + 1} 行明细已入库 ${row.receivedQuantity} 件，计划入库数不能比它小`)
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
    const { data } = await listErpPurchaseInApi(params)
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
    receiptNo: undefined,
    // 新增默认 2 采购入库（和后端 validate 的兜底值同一个）；编辑时后面 Object.assign(form, data)
    // 会用库里那一格覆盖掉，所以回调建的 1 线上退货 打开修改看到的是"线上退货"
    inboundSource: SOURCE_PURCHASE,
    purchaseId: undefined,
    purchaseNo: undefined,
    supplierId: undefined,
    supplierName: undefined,
    warehouseId: undefined,
    userId: undefined,
    userName: undefined,
    status: 0,
    remark: undefined,
    // 服务端说了算的列一律清掉：编辑过一张单再点新增，reactive 里的残留值会被原样提交
    receiveTime: undefined,
    totalQuantity: undefined,
    totalAmount: undefined,
    // 明细由采购单带出，新增时是空的（不像采购单那样给一行空行手填）
    items: []
  })
  // 收窄「入库仓库」用的那个门店跟着来源单据一起清（它是 ref，不在上面的 form 里）
  purchaseStoreId.value = undefined
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
  queryParams.warehouseId = undefined
  queryParams.status = undefined
  dateRange.value = []
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
}

/** 选操作人只带出回显的名字：入库单没有联系人/电话那两列（口径同仓库的「负责操作员」） */
const handleOperatorSelect = (user: any) => {
  form.userName = user.nickname || user.username
}

const handleOperatorClear = () => {
  form.userName = undefined
}

/** ERP 账号自己录单：操作人填成登录人（那一格禁用了不会再触发 @select，所以名字要在这里带一次） */
const fillSelfAsOperator = () => {
  const self = userStore.user as any
  if (!self.id) return
  form.userId = self.id
  form.userName = self.nickname || self.username
}

/** 新增按钮操作 */
const handleAdd = async () => {
  reset()
  await getPurchaseOptions()
  open.value = true
  title.value = '添加商品入库'
  if (isErpAccount.value) fillSelfAsOperator()
}

/**
 * 操作列按钮分发（TableMoreActions 只抛一个 command 事件，动作认 action.command.type，同部门管理）
 */
const handleActionCommand = (action: any) => {
  const { type, row } = action.command
  switch (type) {
    case 'detail':
      handleDetail(row)
      break
    case 'receive':
      handleReceive(row)
      break
    case 'update':
      handleUpdate(row)
      break
    case 'delete':
      handleDelete(row)
      break
  }
}

/**
 * 详情按钮操作：走列表页已有的那个详情接口（主表联名 + 明细带商品名/编码），
 * 每次都重新捞 —— 已入库数可能刚被别的操作员推进过，拿列表那一行的旧值会看漏。
 */
const handleDetail = async (row: any) => {
  detail.value = {}
  detailOpen.value = true
  detailLoading.value = true
  try {
    const { data } = await detailErpPurchaseInApi(row.id)
    detail.value = data || {}
  } catch (error) {
  } finally {
    detailLoading.value = false
  }
}

/** 打印这一张入库单：弹窗里已经捞好的那份直接交给打印器 */
const handleDetailPrint = () => {
  printReceipts([detail.value])
}

/** 正在打开修改弹窗的那一张单 id：详情、采购单候选、明细的采购数量三段都要等，转圈期间顺手挡住连点 */
const updatingId = ref<number | string>()

/**
 * 修改按钮操作：详情接口带 items 回来。
 * 采购数量那一列是采购单里的数（明细表没存），所以按 purchase_id 再捞一次采购单详情补上，
 * 否则回显里这列全是 '-'，上限也挡不住。
 */
const handleUpdate = async (row: any) => {
  updatingId.value = row.id
  reset()
  try {
    const { data } = await detailErpPurchaseInApi(row.id)
    Object.assign(form, data)
    form.items = data.items || []
    await getPurchaseOptions()
    ensurePurchaseOption(data)
    if (form.purchaseId) {
      await fillPurchaseQuantity(form.items, form.purchaseId)
    }
    open.value = true
    title.value = '修改商品入库'
  } catch (error) {
  } finally {
    updatingId.value = undefined
  }
}

/**
 * 回显时保证下拉里有当前这张采购单：候选只列「已审核」的前 50 张，一张单的货登记满/入完库会流转成
 * 「待入库」「已入库」、从候选里消失，不补上这一条选择框就只能显示裸 id（同采购单页把明细商品塞进候选那次）
 */
const ensurePurchaseOption = (data: any) => {
  if (!data.purchaseId) return
  const exists = purchaseOptions.value.some((item: any) => String(item.id) === String(data.purchaseId))
  if (exists) return
  purchaseOptions.value = [
    {
      id: data.purchaseId,
      purchaseNo: data.purchaseNo,
      supplierId: data.supplierId,
      supplierName: data.supplierName
    },
    ...purchaseOptions.value
  ]
}

/** 按采购单给明细行补 purchaseQuantity（只补已有行，不碰已录的入库数量） */
const fillPurchaseQuantity = async (items: any[], purchaseId: any) => {
  purchaseLoading.value = true
  try {
    const { data } = await detailErpPurchaseApi(purchaseId)
    form.supplierName = data.supplierName
    // 顺便拿这张采购单的门店去收窄「入库仓库」（修改回显时不触发 handlePurchaseChange，得在这里补一次）
    purchaseStoreId.value = data.storeId
    data.items?.forEach((pi: any) => {
      items.forEach((row: any) => {
        if (String(row.skuId) === String(pi.skuId)) {
          row.purchaseQuantity = Number(pi.quantity || 0)
        }
      })
    })
  } catch (error) {
  } finally {
    purchaseLoading.value = false
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
        await updateErpPurchaseInApi(form)
        ElMessage.success('修改成功')
      } else {
        await addErpPurchaseInApi(form)
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

/**
 * 入库按钮操作：拉一次详情拿最新明细（received_quantity 可能刚被别的操作员推进过），
 * 「本次入库数」默认按剩余量填满足数——多数到货就是整批到，少数缺货的人为那格。
 */
const handleReceive = async (row: any) => {
  Object.assign(receiveForm, {
    id: undefined,
    receiptNo: undefined,
    inboundSource: undefined,
    purchaseNo: undefined,
    refundNo: undefined,
    supplierName: undefined,
    warehouseName: undefined,
    items: []
  })
  receiveOpen.value = true
  receiveLoading.value = true
  try {
    const { data } = await detailErpPurchaseInApi(row.id)
    Object.assign(receiveForm, {
      id: data.id,
      receiptNo: data.receiptNo,
      inboundSource: data.inboundSource,
      purchaseNo: data.purchaseNo,
      refundNo: data.refundNo,
      supplierName: data.supplierName,
      warehouseName: data.warehouseName
    })
    receiveForm.items = (data.items || []).map((item: any) => ({
      id: item.id,
      skuId: item.skuId,
      productName: item.productName,
      productCode: item.productCode,
      specText: item.specText,
      quantity: Number(item.quantity || 0),
      receivedQuantity: Number(item.receivedQuantity || 0),
      thisQuantity: remainingOf(item),
      price: item.price == null ? undefined : Number(item.price)
    }))
  } catch (error) {
    receiveOpen.value = false
  } finally {
    receiveLoading.value = false
  }
}

/** 入库弹窗本地的完整性检查（后端 receive 还会按 计划-已入库 和采购数量再挡一遍） */
const checkReceive = () => {
  if (!receiveForm.items.length) {
    ElMessage.warning('这张入库单还没有明细，先去「修改」里把商品规格填上')
    return false
  }
  let planned = 0
  for (let i = 0; i < receiveForm.items.length; i++) {
    const row = receiveForm.items[i]
    const thisTime = Number(row.thisQuantity || 0)
    if (thisTime === 0) continue
    planned += thisTime
    if (thisTime > remainingOf(row)) {
      ElMessage.warning(`第 ${i + 1} 行明细本次入库数超过了剩余的 ${remainingOf(row)} 件`)
      return false
    }
    if (row.price == null) {
      ElMessage.warning(`第 ${i + 1} 行明细要入库，单价(成本价)不能空着`)
      return false
    }
  }
  if (planned === 0) {
    ElMessage.warning('本次入库数全是 0，没有要入的货')
    return false
  }
  return true
}

/** 确认入库：只把 (明细行 id, 本次入库数, 单价) 三样提交上去，其余都是服务端说了算 */
const submitReceive = async () => {
  if (!checkReceive()) return
  receiveSubmitting.value = true
  try {
    await receiveErpPurchaseInApi({
      id: receiveForm.id,
      items: receiveForm.items.map((row: any) => ({
        id: row.id,
        thisQuantity: Number(row.thisQuantity || 0),
        price: row.price
      }))
    })
    ElMessage.success('入库成功')
    receiveOpen.value = false
    getList()
  } catch (error) {
  } finally {
    receiveSubmitting.value = false
  }
}

/** 删除按钮操作 */
const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确认删除入库单「${row.receiptNo}」吗？明细会一起删掉，已经入过货的单子删不了。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteErpPurchaseInApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 张入库单吗？明细会一起删掉。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteErpPurchaseInApi(selectedIds.value)
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

const getWarehouseOptions = async () => {
  try {
    const { data } = await optionsErpWarehouseApi()
    warehouseOptions.value = data || []
  } catch (error) {
  }
}

/** 采购单候选取最新多少张（下拉不分页，靠 filterable 在前端过滤） */
const PURCHASE_OPTION_LIMIT = 50
/** 采购单状态：已审核（字典 purchase_status 那一档） */
const PURCHASE_STATUS_AUDITED = 1

/**
 * 采购单候选：只列「已审核」的。货登记满了的那张会自动流转成「待入库」、已入进仓的成「已入库」，
 * 两张都不再出现在下拉里（服务端 refreshPurchaseStatus 推的状态），所以挑得到的就一定还有货可入；
 * 待审核/已退货的单子本来就不该入库，也不列。
 * 一张单的采购量可以被几张入库单分着登记，入满之前它会一直在；选了以后每行默认按「可入库数」填
 * （见 handlePurchaseChange），提交时服务端按同一口径再挡一遍
 */
const getPurchaseOptions = async () => {
  purchaseLoading.value = true
  try {
    const { data } = await listErpPurchaseApi({
      pageNum: 1,
      pageSize: PURCHASE_OPTION_LIMIT,
      status: PURCHASE_STATUS_AUDITED
    })
    purchaseOptions.value = data.records || []
  } catch (error) {
  } finally {
    purchaseLoading.value = false
  }
}

onMounted(() => {
  getList()
  getSupplierOptions()
  getWarehouseOptions()
  getPurchaseOptions()
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

/* 时间范围那一行（照抄采购单、收藏列表的 .search-row）：wrapper 独占整行把日期格顶到第二行，
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

/* 下拉里的「主文案 + 右侧灰色副文案」那一排（采购单号+供应商、仓库名+编码共用） */
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

  /* 一行是一个具体规格，同一商品名的几行全靠这一串分出去，所以它跟名称同宽、不折到编码那一列去 */
  &__spec {
    flex: 0 0 auto;
    color: var(--el-color-primary);
    font-size: 12px;
  }

  &__code {
    flex: 0 0 auto;
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}

/* 入满的那一行数字：绿色加粗，扫列表时不用点开就知道哪张单还欠货 */
.stock-done {
  color: var(--el-color-success);
  font-weight: 600;
}

/* 可入库已经被别人占完（0）：标红，免得对着一个填不进数的格子猜原因 */
.stock-empty {
  color: var(--el-color-danger);
  font-weight: 600;
}

/* 仓库名后面那枚「盘点中 / 调拨中」：挨着名字，不另起一行 */
.warehouse-frozen {
  margin-left: 6px;
}

/* 明细表格上面那排：导入按钮靠右 */
.items-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

/* 明细表格下面那排：左边提示文案，右边合计 */
.items-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;

  .items-total {
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }

  .items-tip {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}
</style>
