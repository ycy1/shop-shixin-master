<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="请输入单号/仓库/操作员/备注" clearable
            @keyup.enter="handleQuery" />
        </el-form-item>
        <!-- 一张盘点单只盘一家仓，所以这一格直接比真列（调拨那页要 OR 两家才拆得开） -->
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="queryParams.warehouseId" placeholder="全部" clearable filterable>
            <el-option v-for="item in warehouseOptions" :key="item.id" :label="item.warehouseName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="盘点类型" prop="checkType">
          <el-select v-model="queryParams.checkType" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_STATUS)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>

        <!-- 时间范围 + 按钮同一行（照抄调拨/采购单那两页的 .search-row）：这一层 wrapper 独占第二行，
             里面两格都不等分、按控件本身的宽度从左排（样式见文件末尾）。筛的是创建时间 -->
        <div class="search-row">
          <el-form-item label="创建时间" prop="createBeginTime">
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
          <el-button v-permission="['erp:inventoryCheck:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['erp:inventoryCheck:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <!-- 复选框只放"删得掉"的那些行（待审核）：批量删除走的是同一个服务端判据 -->
        <el-table-column type="selection" width="55" align="center" :selectable="canDelete" />
        <el-table-column label="盘点单号" prop="checkNo" align="center" min-width="180" />
        <!-- 仓库名和它现在的状态都是列表 SQL 现联表带出来的，改过名不用回头刷单子。
             状态那一格只在"冻得跟这张单无关"时才提示（见 warehouseOdd）：
             2 盘点中本来就是这张单自己冻的，跟着单据状态列走，重复标一遍只会让人以为出了事 -->
        <el-table-column label="盘点仓库" align="center" min-width="160">
          <template #default="{ row }">
            <span>{{ row.warehouseName || `已删除仓库 #${row.warehouseId}` }}</span>
            <el-tag v-if="warehouseOdd(row.warehouseStatus)" class="warehouse-frozen" size="small" type="warning">
              {{ dict.label(DICT_WAREHOUSE, row.warehouseStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="盘点类型" align="center" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_TYPE, row.checkType)">
              {{ dict.label(DICT_TYPE, row.checkType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="盘点SKU" prop="totalSkuCount" align="center" width="90" />
        <!-- 差异那一格：0 就是账实全对上了，标绿；有差异的行还得看「进度」那格调完没有 -->
        <el-table-column label="差异SKU" align="center" width="90">
          <template #default="{ row }">
            <span :class="{ 'diff-warn': Number(row.diffSkuCount || 0) > 0 }">{{ row.diffSkuCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="盘盈" align="center" min-width="140">
          <template #default="{ row }">
            <span class="profit">{{ row.profitQuantity || 0 }} 件</span>
            <span class="amount">{{ priceText(row.profitAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="盘亏" align="center" min-width="140">
          <template #default="{ row }">
            <span class="loss">{{ row.lossQuantity || 0 }} 件</span>
            <span class="amount">{{ priceText(row.lossAmount) }}</span>
          </template>
        </el-table-column>
        <!-- 进度 = 明细里已调整的行数 / 明细行数（列表 SQL 的子查询带的 adjustedCount）：
             相等这张单就自动进 2 已完成，> 0 就不许取消了，两颗按钮的判据都从这一格来 -->
        <el-table-column label="进度" align="center" width="100">
          <template #default="{ row }">
            <span :class="{ 'stock-done': allAdjusted(row) }">{{ row.adjustedCount || 0 }} / {{ row.totalSkuCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_STATUS, row.status)">
              {{ dict.label(DICT_STATUS, row.status) }}
            </el-tag>
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
        <el-table-column label="操作" align="center" width="240" fixed="right">
          <template #default="scope">
            <!-- 按钮超过三颗由 TableMoreActions 收进「更多」下拉（口径同库存调拨那页）。
                 hidden 是整条收掉：权限没过、或者这一档压根做不了这件事（服务端拦的是同一套判据）——
                 批过就改不了也删不了、调完就没得再调、动过账就取消不掉 -->
            <TableMoreActions :actions="[
              { label: '审核', type: 'warning', icon: 'CircleCheck', hidden: !hasPermission('erp:inventoryCheck:audit') || !canAudit(scope.row), loading: auditingId === scope.row.id, command: { type: 'audit', row: scope.row } },
              { label: '调整', type: 'success', icon: 'Sort', hidden: !hasPermission('erp:inventoryCheck:adjust') || !canAdjust(scope.row), command: { type: 'adjust', row: scope.row } },
              { label: '详情', icon: 'View', command: { type: 'detail', row: scope.row } },
              { label: '修改', icon: 'Edit', hidden: !hasPermission('erp:inventoryCheck:update') || !canModify(scope.row), loading: updatingId === scope.row.id, command: { type: 'update', row: scope.row } },
              { label: '取消', type: 'info', icon: 'CircleClose', hidden: !hasPermission('erp:inventoryCheck:cancel') || !canCancel(scope.row), loading: cancellingId === scope.row.id, command: { type: 'cancel', row: scope.row } },
              { label: '删除', type: 'danger', icon: 'Delete', hidden: !hasPermission('erp:inventoryCheck:delete') || !canDelete(scope.row), command: { type: 'delete', row: scope.row } }
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

      <!--
        添加 / 修改盘点单。新增那一步只定"这家仓要盘哪几条规格"（需求②），
        实盘数是修改弹窗里逐行录的（改单在 0 待审核 / 1 盘点中 两档都开放）；
        账面库存和成本价都由后端按盘点仓那条库存行快照，页面上只回显、改不了
      -->
      <el-dialog v-model="open" :title="title" width="1000px" append-to-body destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
          <el-divider content-position="left">基本信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="盘点单号" prop="checkNo">
                <el-input v-model="form.checkNo" placeholder="保存后由系统生成" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="盘点类型" prop="checkType">
                <el-select v-model="form.checkType" placeholder="请选择盘点类型" style="width: 100%">
                  <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="盘点仓库" prop="warehouseId">
                <!-- 盘点仓是这张单的口径线：明细候选就是这家仓的库存行，账面数/成本价也从它那一行带出。
                     改单时这一格禁用 —— 仓在建单那一下就被冻成 2 盘点中了，换仓等于把冻结挪到别人头上，
                     服务端同样钉回原值（要换仓只能取消这张重开） -->
                <el-select v-model="form.warehouseId" :disabled="isEdit" placeholder="请选择盘点仓库" filterable
                  style="width: 100%" @change="handleWarehouseChange">
                  <el-option v-for="item in warehouseCandidates" :key="item.id" :label="item.warehouseName"
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
              <el-form-item label="ERP操作员" prop="userId">
                <!-- 只列 ERP 操作员账号（account_type=5）、停用账号不列；不选就是没记（NULL 存得回去）。
                     ERP 账号自己录单时这一格填成本人并禁用（列表按他收窄，记成别人的话他自己找不到这张单） -->
                <user-select v-model="form.userId" :label="form.userName" only-enabled :account-type="5"
                  :disabled="isErpAccount" style="width: 100%" @select="handleOperatorSelect"
                  @clear="handleOperatorClear" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态" prop="status">
                <!-- 状态不给选：0 是新增的初始档，1 由「审核」推，2 由「调整」按明细进度推，3 由「取消」置 -->
                <el-input :model-value="dict.label(DICT_STATUS, form.status)" placeholder="保存后由系统流转" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="盘点进度">
                <!-- 已调整 / 明细行数：都是服务端算的数，这里只看不动（要往前推走操作列那颗「调整」） -->
                <el-input :model-value="progressText" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="备注" prop="remark">
                <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填，如例行全盘、抽查生鲜区"
                  maxlength="500" show-word-limit />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">盘点明细</el-divider>
          <el-table :data="form.items" size="small" border>
            <!-- 一行是盘点仓里的一条库存行（候选来自 shop_erp_inventory），所以这一格选的是规格不是商品 -->
            <el-table-column label="商品规格" min-width="240">
              <template #default="{ row }">
                <el-tooltip :content="optionTip(row)" placement="top">
                  <el-select v-model="row.skuId" :disabled="!canPickSku" :placeholder="skuPlaceholder" filterable remote
                    :reserve-keyword="false" :remote-method="searchSkus" :loading="skuLoading" style="width: 100%"
                    @change="handleSkuChange(row, $event)">
                    <el-option v-for="item in skuOptions" :key="item.skuId" :label="optionLabel(item)"
                      :value="item.skuId">
                      <div class="product-option">
                        <span class="product-option__name">{{ item.name }}</span>
                        <span class="product-option__spec">{{ item.spec || '默认规格' }}</span>
                        <span class="product-option__code">{{ item.code }}</span>
                      </div>
                    </el-option>
                  </el-select>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="账面库存" width="100" align="center">
              <template #default="{ row }">
                <!-- 不是表单列：后端按盘点仓那一行库存的 stock 快照（建单之后不刷新），这里只回显 -->
                <span>{{ row.bookQuantity == null ? '-' : row.bookQuantity }}</span>
              </template>
            </el-table-column>
            <el-table-column label="实盘库存" width="130" align="center">
              <template #default="{ row }">
                <!-- 留空 = 这一行还没盘到（NULL 存得回去）；已调整的那一行锁死，改了就对不上已经进账的差异 -->
                <el-input-number v-model="row.actualQuantity" :min="0" :controls="false" :value-on-clear="null"
                  :disabled="isAdjusted(row)" placeholder="未盘" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="差异" width="90" align="center">
              <template #default="{ row }">
                <span :class="diffClass(diffOf(row))">{{ diffText(diffOf(row)) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="差异原因" width="130" align="center">
              <template #default="{ row }">
                <!-- 值存的是字符串（"1".."4"），所以候选自己铺、不用 dict.options（它会把数值型 value 转成 number，
                     回显时全等匹配不上，选完显示成裸数字） -->
                <el-select v-model="row.diffReason" :disabled="isAdjusted(row)" placeholder="选填" clearable
                  style="width: 100%">
                  <el-option v-for="item in reasonOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="成本价" width="100" align="center">
              <template #default="{ row }">
                <span>{{ priceText(row.costPrice) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="差异金额" width="110" align="center">
              <template #default="{ row }">
                <span :class="diffClass(diffOf(row))">{{ diffMoneyText(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="行状态" width="90" align="center">
              <template #default="{ row }">
                <!-- 表单里这一格按当前填到哪一步现算：status 要保存完才翻，
                     刚录完实盘还显示「待盘点」会让人以为这一行没算进差异 -->
                <el-tag size="small" :type="dict.tag(DICT_ITEM_STATUS, formItemStatus(row))">
                  {{ dict.label(DICT_ITEM_STATUS, formItemStatus(row)) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ row, $index }">
                <!-- 已经调整过的那一行删不掉（服务端同样拦）：它的差异已经进了库存账 -->
                <el-button type="danger" link icon="Delete" :disabled="isAdjusted(row)" @click="removeItem($index)" />
              </template>
            </el-table-column>
          </el-table>
          <div class="items-footer">
            <el-button type="primary" plain icon="Plus" size="small" :disabled="!canPickSku"
              @click="addItem">添加明细</el-button>
            <span class="items-tip">账面库存与成本价由后端按盘点仓的库存行带出；实盘库存没录的行算「待盘点」，
              差异为 0 也要在「调整」里点一次确认</span>
            <!-- 合计只给录入时看，落库的六个数一律由服务端按明细重算 -->
            <span class="items-total">共 {{ form.items.length }} 行 · 差异 {{ countedDiffSku }} 行 · 盘盈
              {{ countedProfit }} 件 · 盘亏 {{ countedLoss }} 件</span>
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
        确认调整：这一单实际调不调、为什么调，就在这里逐行确认，点一次「调整」= 有差异的那几行直接改盘点仓的库存
        （库存流水跟着记：正差异 7 盘盈、负差异 8 盘亏；差异为 0 的不动账也不记流水，但也算认过这一笔）。
        跟上面的「修改」弹窗是两件事 —— 那边录的是盘点结果（实盘数），这边把它落到账上，
        所以这里不给改实盘数、也不给删行，只有「本次调整」那一勾和「差异原因」一格能动。
        一行整行一次调平（没有"本次调整数"），一张单可以反复点，直到每行都调完（自动进 2 已完成、仓库复位成启用）。
      -->
      <el-dialog v-model="adjustOpen" title="确认调整" width="1000px" append-to-body destroy-on-close>
        <el-form label-width="110px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="盘点单号">
                <el-input :model-value="adjustForm.checkNo" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="盘点仓库">
                <el-input :model-value="adjustForm.warehouseName" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="盘点类型">
                <el-input :model-value="dict.label(DICT_TYPE, adjustForm.checkType)" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="当前状态">
                <el-input :model-value="dict.label(DICT_STATUS, adjustForm.status)" disabled />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">盘点明细</el-divider>
          <el-table :data="adjustForm.items" size="small" border v-loading="adjustLoading">
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
            <el-table-column label="账面" width="80" align="center">
              <template #default="{ row }">
                <span>{{ row.bookQuantity == null ? '-' : row.bookQuantity }}</span>
              </template>
            </el-table-column>
            <el-table-column label="实盘" width="80" align="center">
              <template #default="{ row }">
                <span>{{ row.actualQuantity == null ? '未盘' : row.actualQuantity }}</span>
              </template>
            </el-table-column>
            <el-table-column label="差异" width="80" align="center">
              <template #default="{ row }">
                <span :class="diffClass(row.diffQuantity)">{{ diffText(row.diffQuantity) }}</span>
              </template>
            </el-table-column>
            <!-- 这两格是"这条规格现在这一版"的数：仓从建单起就冻着，账面（bookQuantity）本该等于 currentStock，
                 不相等就是盘点期间有人动过账（或这张单建得太早），先看清再决定调不调。
                 availableStock 还管另一件事：盘亏要减总库存，被下单锁着的那一份还不回来，
                 差得太多就会在后端被拦，所以这一格超了先标红 -->
            <el-table-column label="现在库存" width="100" align="center">
              <template #default="{ row }">
                <span :class="{ 'stock-warn': stockDrifted(row) }">{{ row.currentStock == null ? '-' : row.currentStock }}</span>
              </template>
            </el-table-column>
            <el-table-column label="可用" width="90" align="center">
              <template #default="{ row }">
                <span :class="{ 'stock-warn': lossOverAvailable(row) }">
                  {{ row.availableStock == null ? '-' : row.availableStock }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="差异原因" width="130" align="center">
              <template #default="{ row }">
                <el-select v-model="row.diffReason" :disabled="!canRowAdjust(row)" placeholder="必选" clearable
                  style="width: 100%">
                  <el-option v-for="item in reasonOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="本次调整" width="90" align="center">
              <template #default="{ row }">
                <el-checkbox v-model="row.thisAdjust" :disabled="!canRowAdjust(row)" />
              </template>
            </el-table-column>
            <el-table-column label="行状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="dict.tag(DICT_ITEM_STATUS, row.status)">
                  {{ dict.label(DICT_ITEM_STATUS, row.status) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div class="items-footer">
            <span class="items-tip">这次不调的规格别勾上，留着下次接着调；差异为 0 的行也要勾一次 ——
              无差异同样是"有人认过这一笔"。勾上的行调完就锁死了，一行只能调平一次</span>
            <span class="items-total">本次调整 {{ adjustTotalCount }} 行 · 待确认 {{ adjustRemaining }} 行</span>
          </div>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="adjustOpen = false">取 消</el-button>
            <el-button type="primary" :loading="adjustSubmitting" @click="submitAdjust">确认调整</el-button>
          </div>
        </template>
      </el-dialog>

      <!--
        盘点单详情：只读看这一单的来龙去脉 —— 盘了哪几条规格、各自差几件、差多少钱、什么原因、几个时间。
        已完成 / 已取消 的两张就只能从这里看明细（那两档既改不了也点不开「调整」）。
      -->
      <el-dialog v-model="detailOpen" title="盘点单详情" width="1000px" append-to-body destroy-on-close>
        <el-descriptions title="基本信息" :column="2" border v-loading="detailLoading">
          <el-descriptions-item label="盘点单号">{{ detail.checkNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="盘点类型">
            <el-tag size="small" :type="dict.tag(DICT_TYPE, detail.checkType)">
              {{ dict.label(DICT_TYPE, detail.checkType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="盘点仓库">
            {{ detail.warehouseName || (detail.warehouseId ? `已删除仓库 #${detail.warehouseId}` : '-') }}
            <el-tag v-if="warehouseOdd(detail.warehouseStatus)" class="warehouse-frozen" size="small" type="warning">
              {{ dict.label(DICT_WAREHOUSE, detail.warehouseStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="dict.tag(DICT_STATUS, detail.status)">
              {{ dict.label(DICT_STATUS, detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="操作员">{{ detail.userName || '未指派' }}</el-descriptions-item>
          <el-descriptions-item label="进度">
            <span :class="{ 'stock-done': allAdjusted(detail) }">{{ detail.adjustedCount || 0 }} /
              {{ detail.totalSkuCount || 0 }}</span>
          </el-descriptions-item>
          <!-- 六个数都是服务端按明细算的，这里只是把主表那几列读出来 -->
          <el-descriptions-item label="盘点SKU数">{{ detail.totalSkuCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="差异SKU数">
            <span :class="{ 'diff-warn': Number(detail.diffSkuCount || 0) > 0 }">{{ detail.diffSkuCount || 0 }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="盘盈">{{ detail.profitQuantity || 0 }} 件 / {{ priceText(detail.profitAmount) }}</el-descriptions-item>
          <el-descriptions-item label="盘亏">{{ detail.lossQuantity || 0 }} 件 / {{ priceText(detail.lossAmount) }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">
            {{ validate.formatTime(detail.startTime, 'YYYY-MM-DD') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="结束时间">
            {{ validate.formatTime(detail.endTime, 'YYYY-MM-DD') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ validate.formatTime(detail.createTime, 'YYYY-MM-DD') || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">盘点明细</el-divider>
        <el-table :data="detail.items" size="small" border v-loading="detailLoading">
          <el-table-column label="商品名称" min-width="180">
            <template #default="{ row }">
              <el-tooltip :content="productSpecTip(row)" placement="top">
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
          <el-table-column label="账面库存" width="100" align="center">
            <template #default="{ row }">
              <span>{{ row.bookQuantity == null ? '-' : row.bookQuantity }}</span>
            </template>
          </el-table-column>
          <el-table-column label="实盘库存" width="100" align="center">
            <template #default="{ row }">
              <span>{{ row.actualQuantity == null ? '未盘' : row.actualQuantity }}</span>
            </template>
          </el-table-column>
          <el-table-column label="差异数量" width="100" align="center">
            <template #default="{ row }">
              <span :class="diffClass(row.diffQuantity)">{{ diffText(row.diffQuantity) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="差异原因" width="110" align="center">
            <template #default="{ row }">
              <span>{{ row.diffReason ? dict.label(DICT_REASON, row.diffReason) : '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="成本价" width="100" align="center">
            <template #default="{ row }">
              <span>{{ priceText(row.costPrice) }}</span>
            </template>
          </el-table-column>
          <!-- 差异金额读库里的 diff_amount：它是服务端按 差异数量 × 成本价 算好落库的那个数 -->
          <el-table-column label="差异金额" width="110" align="center">
            <template #default="{ row }">
              <span :class="diffClass(row.diffQuantity)">{{ signedMoneyText(row.diffAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="行状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="dict.tag(DICT_ITEM_STATUS, row.status)">
                {{ dict.label(DICT_ITEM_STATUS, row.status) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <template #footer>
          <el-button @click="detailOpen = false">关 闭</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listErpInventoryCheckApi,
  detailErpInventoryCheckApi,
  optionsErpCheckSkuApi,
  addErpInventoryCheckApi,
  updateErpInventoryCheckApi,
  deleteErpInventoryCheckApi,
  auditErpInventoryCheckApi,
  adjustErpInventoryCheckApi,
  cancelErpInventoryCheckApi
} from '@/api/erp/inventoryCheck'
import { optionsErpWarehouseApi } from '@/api/erp/warehouse'
import { getUserDetailApi } from '@/api/system/user'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import TableMoreActions from '@/components/TableMoreActions/index.vue'
import validate from '@/utils/validate'
import { useDict } from '@/utils/dictCache'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
/** 账号类型：0平台 1门店 2送货员 3消费者 4供应商 5ERP操作员；ERP 账号只看/只改自己盘的这几张单 */
const isErpAccount = computed(() => userStore.user.accountType === 5)

/** 操作列走 TableMoreActions，权限得在这里自己判（组件没有 v-permission 那套指令，只认 hidden/disabled） */
const permissions = computed(() => userStore.user.permissions || [])
const hasPermission = (permission: string): boolean => {
  return permissions.value.includes(permission)
}

/**
 * 盘点类型字典：check_type（1全盘 2抽盘 3循环盘点）
 * 盘点单状态字典：check_status = status（0待审核 1盘点中 2已完成 3已取消）
 * 明细行状态字典：check_item_status（0待盘点 1已盘点 2已调整）
 * 差异原因字典：diff_reason（1正常损耗 2记录错误 3丢失 4其他）
 * 仓库状态字典：warehouse_status = 列表联表带出的 warehouseStatus（不是本单的 status！）
 */
const DICT_TYPE = 'check_type'
const DICT_STATUS = 'check_status'
const DICT_ITEM_STATUS = 'check_item_status'
const DICT_REASON = 'diff_reason'
const DICT_WAREHOUSE = 'warehouse_status'
const dict = useDict(DICT_TYPE, DICT_STATUS, DICT_ITEM_STATUS, DICT_REASON, DICT_WAREHOUSE)

const CHECK_STATUS_PENDING = 0
const CHECK_STATUS_STOCKTAKING = 1
const ITEM_STATUS_PENDING = 0
const ITEM_STATUS_COUNTED = 1
const ITEM_STATUS_ADJUSTED = 2

/**
 * 状态缺失按 0 待审核 算，和后端口径一致（新增时服务端钉的就是 0）
 */
const statusOf = (row: any) => Number(row?.status ?? CHECK_STATUS_PENDING)

/**
 * 能改的是 0 待审核（改盘点范围）和 1 盘点中（录实盘）两档：服务端 update 同一套判据。
 * 2 已完成 / 3 已取消 已经走完了，改明细等于把单据改成和账对不上的样子
 */
const canModify = (row: any) =>
  [CHECK_STATUS_PENDING, CHECK_STATUS_STOCKTAKING].includes(statusOf(row))
/** 「审核」只在待审核这一档出现：1/2/3 早批过了或已经作废 */
const canAudit = (row: any) => statusOf(row) === CHECK_STATUS_PENDING
/**
 * 能删的只有待审核：一进了 1 就可能已经调过账了，删单不会把库存挪回去（要撤下来只能点「取消」）。
 * 复选框的 :selectable 也用它，批量删除撞不到对不上的死账
 */
const canDelete = (row: any) => statusOf(row) === CHECK_STATUS_PENDING
/**
 * 「调整」出现于 1 盘点中（服务端 adjust 同一判据），而且还有没确认的行；
 * 全部行都调完就自动进 2 已完成，这一颗整条收掉（点了也没有可调的行）
 */
const canAdjust = (row: any) => statusOf(row) === CHECK_STATUS_STOCKTAKING && !allAdjusted(row)
/**
 * 「取消」放宽到 0/1 两档，但要求一行都没调过账（进度还是 0 / n）：
 * 只要调过一行库存就退不回去了，那几笔得反向再调一次，是另一张单的活
 */
const canCancel = (row: any) =>
  [CHECK_STATUS_PENDING, CHECK_STATUS_STOCKTAKING].includes(statusOf(row))
  && Number(row.adjustedCount || 0) === 0
/** 整单调完：已调整行数 ≥ 明细行数（两个数都来自列表 SQL，判这一条不用先捞明细） */
const allAdjusted = (row: any) =>
  Number(row.totalSkuCount || 0) > 0 && Number(row.adjustedCount || 0) >= Number(row.totalSkuCount || 0)

/**
 * 仓库的 0 禁用 / 3 调拨中 才在这一格提示：
 * 2 盘点中本来就是这张单自己冻的（跟状态列重复），1 启用是常态，
 * 只有这两档说明"这张单挂着一家账不正常的仓"（服务端只在仓还冻着的时候放行调整）
 */
const WAREHOUSE_ODD_STATUS = [0, 3]
const warehouseOdd = (status: any): boolean =>
  status != null && WAREHOUSE_ODD_STATUS.includes(Number(status))

const loading = ref(true)
const submitLoading = ref(false)
const selectedIds = ref<any[]>([])
const total = ref(0)
const dataList = ref<any[]>([])
const title = ref('')
const open = ref(false)

/** 修改弹窗里盘点仓禁用（换仓服务端不认），新增不禁；用表单有没有 id 判就够 */
const isEdit = computed(() => form.id != null)

/** 审核 / 取消 是就地翻状态，点了才知道行不行；loading 钉在行上免得连点两次 */
const auditingId = ref<number | string>()
const cancellingId = ref<number | string>()
/** 「修改」要点详情接口把明细捞回来，同理 */
const updatingId = ref<number | string>()

/** 确认调整弹窗：跟新增/修改那套状态分开，免得两个弹窗互相踩表单 */
const adjustOpen = ref(false)
const adjustLoading = ref(false)
const adjustSubmitting = ref(false)
const adjustForm = reactive<any>({ items: [] as any[] })

/** 详情弹窗：只读，数据每次点开重新捞一遍（别人可能刚把进度推前了） */
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<any>({})

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// 查询参数：时间筛的是创建时间，所以是 createBeginTime/createEndTime
// （beginTime/endTime 在这一张表上是盘点起止两个真列，同名会把它们当查询条件覆盖掉）
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  warehouseId: undefined,
  checkType: undefined,
  status: undefined
})
const dateRange = ref<string[]>([])

// 表单参数：items 是明细数组，整单一起提交
const form = reactive<any>({ items: [] as any[] })

const rules = reactive<FormRules>({
  warehouseId: [{ required: true, message: '请选择盘点仓库', trigger: 'change' }]
})

/** 金额的显示口径同商品库存、库存调拨那几页（后端返回的 decimal 是字符串，统一走 Number） */
const priceText = (price: any) => (price == null || price === '' ? '￥0.00' : `￥${Number(price).toFixed(2)}`)

/** 差异那一串：正数带 + 号（盘盈）、负数自带 -（盘亏）、没录实盘就是 '-' */
const diffText = (diff: any) => {
  if (diff == null || diff === '') return '-'
  const value = Number(diff)
  return value > 0 ? `+${value}` : `${value}`
}
const diffClass = (diff: any) => {
  const value = Number(diff ?? 0)
  if (value > 0) return 'profit'
  if (value < 0) return 'loss'
  return ''
}
/** 差异金额：带符号的金额，NULL（这条规格还没成本价）就显示 '-'；符号放在货币符前面 */
const signedMoneyText = (amount: any) => {
  if (amount == null || amount === '') return '-'
  const value = Number(amount)
  if (value > 0) return `+￥${value.toFixed(2)}`
  if (value < 0) return `-￥${Math.abs(value).toFixed(2)}`
  return `￥${value.toFixed(2)}`
}

/** 明细行上现算的差异（表单里那一格是录入用的实盘数，落库前服务端也按同一个式子算一遍） */
const diffOf = (row: any) =>
  row.actualQuantity == null || row.actualQuantity === '' ? null
    : Number(row.actualQuantity) - Number(row.bookQuantity || 0)
const diffAmountOf = (row: any) => {
  const diff = diffOf(row)
  return diff == null ? null : Number(row.costPrice || 0) * diff
}
const diffMoneyText = (row: any) => signedMoneyText(diffAmountOf(row))

/** 这一行已经调过账了：实盘数、差异原因、删行三处一律锁死（服务端同样拦） */
const isAdjusted = (row: any) => Number(row?.status ?? 0) === ITEM_STATUS_ADJUSTED

/** 表单里那一格的行状态按当前填到哪一步现算（status 是服务端存的，保存完才翻，直接读会慢一拍） */
const formItemStatus = (row: any) => {
  if (isAdjusted(row)) return ITEM_STATUS_ADJUSTED
  return row.actualQuantity == null || row.actualQuantity === '' ? ITEM_STATUS_PENDING : ITEM_STATUS_COUNTED
}

const countedProfit = computed(() =>
  form.items.reduce((sum: number, row: any) => {
    const diff = diffOf(row)
    return sum + (diff != null && diff > 0 ? diff : 0)
  }, 0)
)
const countedLoss = computed(() =>
  form.items.reduce((sum: number, row: any) => {
    const diff = diffOf(row)
    return sum + (diff != null && diff < 0 ? -diff : 0)
  }, 0)
)
const countedDiffSku = computed(() =>
  form.items.filter((row: any) => {
    const diff = diffOf(row)
    return diff != null && diff !== 0
  }).length
)
/** 弹窗里那一格「盘点进度」的文案（主表两个数，改了要保存才刷） */
const progressText = computed(() =>
  `${form.adjustedCount || 0} / ${form.items.length} 行已调整`
)

const warehouseOptions = ref<any[]>([])

/**
 * 仓库候选：options 接口只列 1 启用的仓（口径同商品入库/库存调拨）。
 * 建单要求这家仓当时是启用的，所以新增那份正好对口；
 * 改单时这一格是禁用的，而它正因为被这张自己冻成 2 盘点中（不在 options 里），
 * 于是把已选那一条按详情带回的名字补回候选，否则那一格只剩一个裸 id
 */
const warehouseCandidates = computed(() => {
  const list = [...warehouseOptions.value]
  const current = form.warehouseId
  if (current && !list.some((item: any) => String(item.id) === String(current))) {
    list.unshift({ id: current, warehouseName: form.warehouseName || `仓库 #${current}` })
  }
  return list
})

/**
 * 规格候选：远程搜索的结果一直往里合并、不清空。
 * el-select 只显示"当前候选里能匹配上的那条"，所以每搜一次就把列表换掉的话，
 * 其它已选行会立刻显示成裸 id —— 盘点明细动辄几十行规格，这个必须避开。
 * 范围由后端按「这家仓登记在册的库存行」收窄（一行是一条规格，账面 0 件的也在）
 */
const skuOptions = ref<any[]>([])
const skuLoading = ref(false)

/** 候选行和明细行都带 skuId，统一成明细那套 key：skuId / name / spec / code / bookQuantity / costPrice */
const mergeSkuOptions = (list: any[]) => {
  const map = new Map(skuOptions.value.map((item: any) => [String(item.skuId), item]))
  list.forEach((item: any) => {
    map.set(String(item.skuId), {
      skuId: item.skuId,
      name: item.productName || `已删除规格 #${item.skuId}`,
      code: item.productCode,
      // 同一件商品有几个规格就有几行，选中后输入框里得靠这一串分出去
      spec: item.specText,
      // 这一条规格在这家仓现在的总库存：选中后就落进 book_quantity（建单那一刻的账面数）
      stock: item.stock,
      // 库存行当前的成本价（加权后的）：回显用，落库由服务端按同一口径带
      costPrice: item.costPrice
    })
  })
  skuOptions.value = [...map.values()]
}

/** 下拉里那条和选中后输入框里那串都用它：商品名 + 规格（无规格的默认那条不补字） */
const optionLabel = (item: any) => `${item.name}${item.spec ? ` ${item.spec}` : ''}`

/** 名称/规格/编码/账面四段拼的悬浮全称（明细行上选择框太窄，靠它看全） */
const optionTip = (item: any) =>
  [item.name, item.spec, item.code, item.stock == null ? null : `账面 ${item.stock} 件`]
    .filter(Boolean)
    .join(' / ')

/**
 * 明细的规格那一格初始是禁用的：要先有盘点仓，挑出来的才是「这家仓在册的那几条规格」
 * （账面数和成本价也是从这个仓的那行库存带的）。编辑回显时已经录好的行照常显示名字
 * （handleUpdate 把明细塞进过候选），只是没仓就没法换挑规格
 */
const canPickSku = computed(() => !!form.warehouseId)

const skuPlaceholder = computed(() =>
  canPickSku.value ? '输入名称/编码/规格搜索' : '请先选择盘点仓库'
)

/** 差异原因的候选：值保持字符串（见模板那一格的注释） */
const reasonOptions = computed(() =>
  dict.list(DICT_REASON).map((item: any) => ({ label: item.label, value: item.value }))
)

/**
 * 选中规格：把这一行的账面数/成本价兜成候选那一行的数（后端落库时按同一口径再带一遍）。
 * 换规格就覆盖 —— 账面数本来就是跟着规格走的，留着上一个规格的数只会误导。
 * el-select 的 change 只在用户操作时触发，编辑回显不会走到这里
 */
const handleSkuChange = (row: any, skuId: any) => {
  const option = skuOptions.value.find((item: any) => String(item.skuId) === String(skuId))
  if (!option) return
  row.bookQuantity = option.stock == null ? undefined : Number(option.stock)
  row.costPrice = option.costPrice == null ? undefined : Number(option.costPrice)
}

const searchSkus = async (keyword: string) => {
  if (!canPickSku.value) return
  skuLoading.value = true
  try {
    const { data } = await optionsErpCheckSkuApi({
      warehouseId: form.warehouseId,
      keyword: keyword || undefined
    })
    mergeSkuOptions(data || [])
  } catch (error) {
  } finally {
    skuLoading.value = false
  }
}

/**
 * 换盘点仓 → 明细整块清回一行空行重录：账面数、成本价和候选范围都是从这个仓那一行库存来的，
 * 留着上一家仓的规格，多半在新仓压根没库存行，提交时被服务端逐行挡掉，等于留一堆半截数据让人猜。
 * el-select 的 change 只在用户操作时触发，编辑弹窗回显不会误清（那一格是禁用的）
 */
const handleWarehouseChange = () => {
  const hadSkus = form.items.some((row: any) => row.skuId)
  form.items = [emptyItem()]
  skuOptions.value = []
  if (form.warehouseId) {
    searchSkus('')
  }
  if (hadSkus) {
    ElMessage.warning('已切换盘点仓库，盘点明细请按该仓的在册规格重新选择')
  }
}

/** 一行空明细：实盘留空（= 还没盘）、成本价/账面数选中规格后由那条库存行兜上 */
const emptyItem = () => ({
  skuId: undefined,
  bookQuantity: undefined,
  actualQuantity: null,
  costPrice: undefined,
  diffReason: undefined,
  status: 0
})

const addItem = () => {
  form.items.push(emptyItem())
}

const removeItem = (index: number) => {
  form.items.splice(index, 1)
}

/** 明细的完整性靠这里挡一遍（后端 validate 还会再挡一遍，这里只为少一次往返） */
const checkItems = () => {
  if (!form.items.length) {
    ElMessage.warning('请至少添加一行盘点明细')
    return false
  }
  const picked = new Set<string>()
  for (let i = 0; i < form.items.length; i++) {
    const row = form.items[i]
    if (!row.skuId) {
      ElMessage.warning(`第 ${i + 1} 行明细没选商品规格`)
      return false
    }
    // 一张盘点单里一条规格只能有一行（表上有 uk_check_sku，服务端同样拦）
    if (picked.has(String(row.skuId))) {
      ElMessage.warning(`第 ${i + 1} 行明细的商品规格和前面的重复了`)
      return false
    }
    picked.add(String(row.skuId))
  }
  return true
}

/** 列表查询 */
const getList = async () => {
  loading.value = true
  try {
    const params: any = { ...queryParams }
    if (dateRange.value && dateRange.value.length === 2) {
      params.createBeginTime = `${dateRange.value[0]} 00:00:00`
      params.createEndTime = `${dateRange.value[1]} 23:59:59`
    }
    const { data } = await listErpInventoryCheckApi(params)
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
    checkNo: undefined,
    warehouseId: undefined,
    warehouseName: undefined,
    warehouseStatus: undefined,
    userId: undefined,
    userName: undefined,
    checkType: 1,
    status: CHECK_STATUS_PENDING,
    adjustedCount: 0,
    remark: undefined,
    // 明细整单提交，新增时先给一行空行让人直接录
    items: [emptyItem()]
  })
  // 候选也清掉：它是按上一家盘点仓查出来的，留着新增时就能挑到这仓压根没有的规格。
  // 编辑是 reset 之后再把手上这几行的规格塞回候选（见 handleUpdate），所以回显不受影响
  skuOptions.value = []
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
  queryParams.warehouseId = undefined
  queryParams.checkType = undefined
  queryParams.status = undefined
  dateRange.value = []
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
}

/** 选操作员只更新回显的名字（列表那一列和弹窗里都读它；服务端认的是 userId） */
const handleOperatorSelect = (user: any) => {
  form.userName = user.nickname || user.username
}

/** 取消指派只清回显的名字 */
const handleOperatorClear = () => {
  form.userName = undefined
}

/**
 * ERP 账号自己新增：操作员填成登录人（那一格禁用了，不会再触发 @select，所以名字得在这里主动带一次）。
 * 昵称不在登录信息那份快照的全部字段里，按 id 再查一次用户详情最稳（这个读接口不校验权限）
 */
const fillSelfAsOperator = async () => {
  const self = userStore.user as any
  if (!self.id) return
  form.userId = self.id
  form.userName = self.nickname || self.username
  try {
    const { data } = await getUserDetailApi(self.id)
    form.userName = data.nickname || data.username || form.userName
  } catch (error) {
  }
}

/** 新增按钮操作 */
const handleAdd = async () => {
  reset()
  open.value = true
  title.value = '添加盘点单'
  if (isErpAccount.value) await fillSelfAsOperator()
}

/** 修改按钮操作：详情接口带 items 回来，规格名/编码/成本价要塞进候选否则选择框显示裸 id */
const handleUpdate = async (row: any) => {
  updatingId.value = row.id
  reset()
  try {
    const { data } = await detailErpInventoryCheckApi(row.id)
    Object.assign(form, data)
    form.items = (data.items || []).map((item: any) => ({
      ...item,
      // 后端给的 decimal 是字符串，统一成 number 让差异金额那一格显示得动
      costPrice: item.costPrice == null ? undefined : Number(item.costPrice)
    }))
    // 明细行上就带着 skuId + 商品名/编码/规格 + 账面数（联表回填的），直接塞进候选
    mergeSkuOptions(form.items)
    open.value = true
    title.value = '修改盘点单'
  } catch (error) {
  } finally {
    updatingId.value = undefined
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
        await updateErpInventoryCheckApi(form)
        ElMessage.success('修改成功')
      } else {
        await addErpInventoryCheckApi(form)
        ElMessage.success('新增成功，盘点仓库已置为盘点中')
      }
      open.value = false
      getList()
    } catch (error) {
    } finally {
      submitLoading.value = false
    }
  })
}

/** 审核：0 待审核 → 1 盘点中，审核过才开始录实盘、才允许调整 */
const handleAudit = (row: any) => {
  ElMessageBox.confirm(
    `确认审核盘点单「${row.checkNo}」吗？审核通过后就开始盘点：可以在「修改」里逐行录实盘、点「调整」把差异落到账上，但这张单就删不掉了。`,
    '提示',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    auditingId.value = row.id
    try {
      await auditErpInventoryCheckApi(row.id)
      ElMessage.success('审核成功，开始盘点')
      getList()
    } catch (error) {
    } finally {
      auditingId.value = undefined
    }
  }).catch(() => { })
}

/** 取消：0/1 → 3 已取消，仓库跟着腾出来（前提是还没有一行调过账） */
const handleCancel = (row: any) => {
  ElMessageBox.confirm(
    `确认取消盘点单「${row.checkNo}」吗？这张单还没有一行调过账，取消只是作废，并把仓库「${row.warehouseName || row.warehouseId}」的状态改回启用。`,
    '提示',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    cancellingId.value = row.id
    try {
      await cancelErpInventoryCheckApi(row.id)
      ElMessage.success('已取消')
      getList()
    } catch (error) {
    } finally {
      cancellingId.value = undefined
    }
  }).catch(() => { })
}

/** 删除按钮操作 */
const handleDelete = (row: any) => {
  ElMessageBox.confirm(
    `确认删除盘点单「${row.checkNo}」吗？明细会一起删掉，仓库也跟着腾出来；审核过的删不了（要撤下来只能点「取消」）。`,
    '提示',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    await deleteErpInventoryCheckApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(
    `确认删除选中的 ${selectedIds.value.length} 张盘点单吗？明细会一起删掉，涉及的仓库也跟着腾出来；审核过的删不了（复选框也不会让你勾上那些）。`,
    '提示',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    await deleteErpInventoryCheckApi(selectedIds.value)
    ElMessage.success('删除成功')
    selectedIds.value = []
    getList()
  }).catch(() => { })
}

const handleActionCommand = (action: any) => {
  const { type, row } = action.command
  switch (type) {
    case 'audit':
      handleAudit(row)
      break
    case 'adjust':
      handleAdjust(row)
      break
    case 'cancel':
      handleCancel(row)
      break
    case 'detail':
      handleDetail(row)
      break
    case 'update':
      handleUpdate(row)
      break
    case 'delete':
      handleDelete(row)
      break
  }
}

/** 明细行那串悬浮用的全称（名字 + 规格 + 编码，长名字被省略号吃掉时靠它看全） */
const productSpecTip = (row: any) =>
  [row.productName || `已删除规格 #${row.skuId}`, row.specText, row.productCode]
    .filter(Boolean)
    .join(' / ')

/**
 * 这一行这次能不能调：只有 1 已盘点 那一档可以 —— 没录实盘的行（0 待盘点）压根不知道差几件，
 * 已经调过的行（2 已调整）一行只能调平一次。弹窗里默认勾上所有盘过的行（一次调完是常态）
 */
const canRowAdjust = (row: any) => Number(row?.status ?? 0) === ITEM_STATUS_COUNTED

/** 账面数和这家仓现在那一版的总库存对不上：只是标红提醒（拦由后端按当时的库存判） */
const stockDrifted = (row: any) =>
  row.currentStock != null && Number(row.currentStock) !== Number(row.bookQuantity || 0)

/** 盘亏的那几件是不是超过了能动的那部分（被下单锁着的那一份还不回来）：同上，只是提醒 */
const lossOverAvailable = (row: any) => {
  const diff = Number(row.diffQuantity || 0)
  return diff < 0 && row.availableStock != null && -diff > Number(row.availableStock)
}

/**
 * 「调整」弹窗：点开时按详情接口把这一单的明细捞回来（账面/实盘/差异/现在库存都在明细上），
 * 每行「本次调整」默认勾上盘过的那几行；没盘的、已调的都不勾（那一格也是禁用的）
 */
const handleAdjust = async (row: any) => {
  Object.assign(adjustForm, {
    id: undefined,
    checkNo: undefined,
    warehouseId: undefined,
    warehouseName: undefined,
    checkType: undefined,
    status: undefined,
    items: []
  })
  adjustOpen.value = true
  adjustLoading.value = true
  try {
    const { data } = await detailErpInventoryCheckApi(row.id)
    Object.assign(adjustForm, {
      id: data.id,
      checkNo: data.checkNo,
      warehouseId: data.warehouseId,
      warehouseName: data.warehouseName,
      checkType: data.checkType,
      status: data.status
    })
    adjustForm.items = (data.items || []).map((item: any) => ({
      id: item.id,
      skuId: item.skuId,
      productName: item.productName,
      productCode: item.productCode,
      specText: item.specText,
      bookQuantity: item.bookQuantity,
      actualQuantity: item.actualQuantity,
      diffQuantity: Number(item.diffQuantity || 0),
      costPrice: item.costPrice == null ? undefined : Number(item.costPrice),
      diffReason: item.diffReason || undefined,
      currentStock: item.currentStock,
      availableStock: item.availableStock,
      status: Number(item.status ?? 0),
      thisAdjust: Number(item.status ?? 0) === ITEM_STATUS_COUNTED
    }))
  } catch (error) {
    adjustOpen.value = false
  } finally {
    adjustLoading.value = false
  }
}

const adjustTotalCount = computed(() =>
  adjustForm.items.filter((row: any) => row.thisAdjust).length
)
const adjustRemaining = computed(() =>
  adjustForm.items.filter((row: any) => !isAdjusted(row)).length
)

/** 差异原因由这一行的下拉直接改 row.diffReason：提交时连同 thisAdjust 一起回后端 */
const checkAdjust = () => {
  if (!adjustForm.items.length) {
    ElMessage.warning('这张盘点单还没有明细，先去「修改」里把商品规格填上')
    return false
  }
  if (adjustTotalCount.value === 0) {
    ElMessage.warning('一行都没勾，这次没有要确认的规格')
    return false
  }
  for (let i = 0; i < adjustForm.items.length; i++) {
    const row = adjustForm.items[i]
    if (!row.thisAdjust) continue
    // 有差异的行必须说清为什么差（后端同样拦）；差异为 0 的行可以留空
    if (Number(row.diffQuantity || 0) !== 0 && !row.diffReason) {
      ElMessage.warning(`第 ${i + 1} 行明细有差异，请先选一个差异原因`)
      return false
    }
  }
  return true
}

const submitAdjust = async () => {
  if (!checkAdjust()) return
  adjustSubmitting.value = true
  try {
    // 只回 id + 每行的 { id, thisAdjust, diffReason }：账面/实盘/差异/进度/时间都由后端算
    await adjustErpInventoryCheckApi({
      id: adjustForm.id,
      items: adjustForm.items.map((row: any) => ({
        id: row.id,
        thisAdjust: !!row.thisAdjust,
        diffReason: row.diffReason || null
      }))
    })
    ElMessage.success('调整成功，盘点仓库的库存与库存流水已更新')
    adjustOpen.value = false
    getList()
  } catch (error) {
  } finally {
    adjustSubmitting.value = false
  }
}

/** 详情：只读，每次重新捞（已调整行数、仓库状态都可能已经被别人推进） */
const handleDetail = async (row: any) => {
  detail.value = {}
  detailOpen.value = true
  detailLoading.value = true
  try {
    const { data } = await detailErpInventoryCheckApi(row.id)
    detail.value = data || {}
  } catch (error) {
  } finally {
    detailLoading.value = false
  }
}

/** 分页 */
const handleSizeChange = () => {
  queryParams.pageNum = 1
  getList()
}
const handleCurrentChange = () => {
  getList()
}

/**
 * 仓库候选：options 接口只列 1 启用的仓，且刻意不按 ERP 账号收窄（见仓库模块），
 * 所以这里拿到的是全部启用的仓 —— 建单要求这家仓当时是启用，这一份正好是同一个口径
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

/* 时间范围那一行（照抄调拨/商品入库那两页的 .search-row）：wrapper 独占整行把日期格顶到第二行，
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

/* 明细行里规格下拉的 名称 + 规格 + 编码（同调拨/商品入库那两页的排法） */
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

/* 仓库那一格的提示标签（禁用中/调拨中）：跟仓库名并排，别把格子撑高 */
.warehouse-frozen {
  margin-left: 6px;
}

/* 盘盈/盘亏两列里的件数和金额：件数带色、金额弱一点，一眼分得开哪边多哪边少 */
.profit {
  color: var(--el-color-success);
  font-weight: 600;
}

.loss {
  color: var(--el-color-danger);
  font-weight: 600;
}

.amount {
  margin-left: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

/* 有差异的规格数：不是 0 就标出来（盘完了才该回到 0） */
.diff-warn {
  color: var(--el-color-warning);
  font-weight: 600;
}

/* 调完的那一行/那一格数字：绿色加粗（同商品入库那页的已入库） */
.stock-done {
  color: var(--el-color-success);
  font-weight: 600;
}

/* 现在库存和账面数对不上、盘亏数盖过可用量的那一格：只是提醒，真正拦不拦由后端按当时的库存判 */
.stock-warn {
  color: var(--el-color-warning);
  font-weight: 600;
}

/* 明细表格下面那排：左边加行按钮，中间说明，右边合计文案 */
.items-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;

  .items-tip {
    flex: 1 1 auto;
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  .items-total {
    flex: 0 0 auto;
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }
}
</style>
