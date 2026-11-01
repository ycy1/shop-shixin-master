<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="请输入单号/原因/备注/仓库/操作员" clearable
            @keyup.enter="handleQuery" />
        </el-form-item>
        <!-- 一张调拨单有两家仓，所以这一格是"哪一头命中都算"（后端 SQL 里是 from OR to），
             拆成两个下拉反而让人纠结该填哪一边 -->
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="queryParams.warehouseId" placeholder="全部" clearable filterable>
            <el-option v-for="item in warehouseOptions" :key="item.id" :label="item.warehouseName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="调拨类型" prop="transferType">
          <el-select v-model="queryParams.transferType" placeholder="全部" clearable>
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

        <!-- 时间范围 + 按钮同一行（照抄采购单/商品入库那两页的 .search-row）：这一层 wrapper 独占第二行，
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
          <el-button v-permission="['erp:inventoryTransfer:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['erp:inventoryTransfer:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <!-- 复选框只放"删得掉"的那些行（待审核）：批量删除走的是同一个服务端判据 -->
        <el-table-column type="selection" width="55" align="center" :selectable="canDelete" />
        <el-table-column label="调拨单号" prop="transferNo" align="center" min-width="180" />
        <!-- 两家仓：名称和它们各自的状态都是列表 SQL 现联表带出来的，改过名不用回头刷单子。
             状态那一格只在"冻得跟这张单无关"时才提示（见 warehouseOdd），
             调拨中(3) 本来就是自己这张单冻的、跟着状态列走，重复标一遍只会让人以为出了事 -->
        <el-table-column label="调出仓库" align="center" min-width="160">
          <template #default="{ row }">
            <span>{{ row.fromWarehouseName || `已删除仓库 #${row.fromWarehouseId}` }}</span>
            <el-tag v-if="warehouseOdd(row.fromWarehouseStatus)" class="warehouse-frozen" size="small" type="warning">
              {{ dict.label(DICT_WAREHOUSE, row.fromWarehouseStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="调入仓库" align="center" min-width="160">
          <template #default="{ row }">
            <span>{{ row.toWarehouseName || `已删除仓库 #${row.toWarehouseId}` }}</span>
            <el-tag v-if="warehouseOdd(row.toWarehouseStatus)" class="warehouse-frozen" size="small" type="warning">
              {{ dict.label(DICT_WAREHOUSE, row.toWarehouseStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="总数量" prop="totalQuantity" align="center" width="90" />
        <!-- 已调出是明细 out_quantity 现汇总出来的（列表 SQL 里的子查询）：调了几次、还剩多少一眼看出，
             「调拨」按钮还能不能点也看这两个数 -->
        <el-table-column label="已调出" align="center" width="100">
          <template #default="{ row }">
            <span :class="{ 'stock-done': allMoved(row) }">{{ row.totalOut || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="调拨成本" align="center" width="130">
          <template #default="{ row }">
            <span>{{ priceText(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="调拨类型" align="center" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_TYPE, row.transferType)">
              {{ dict.label(DICT_TYPE, row.transferType) }}
            </el-tag>
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
            <!-- 按钮超过三颗由 TableMoreActions 收进「更多」下拉（口径同商品入库那页）。
                 hidden 是整条收掉：权限没过、或者这一档压根做不了这件事（服务端拦的是同一套判据，
                 这里只是不让人点了才知道）—— 已审核过就改不了也删不了、调满就没得再调、
                 动过货就取消不掉，留一颗点不动的按钮反而更绕。
                 disabled 只留给"现在不行但等下可能行"的那种 -->
            <TableMoreActions :actions="[
              { label: '审核', type: 'warning', icon: 'CircleCheck', hidden: !hasPermission('erp:inventoryTransfer:audit') || !canAudit(scope.row), loading: auditingId === scope.row.id, command: { type: 'audit', row: scope.row } },
              { label: '调拨', type: 'success', icon: 'Sort', hidden: !hasPermission('erp:inventoryTransfer:transfer') || !canTransfer(scope.row), command: { type: 'transfer', row: scope.row } },
              { label: '详情', icon: 'View', command: { type: 'detail', row: scope.row } },
              { label: '修改', icon: 'Edit', hidden: !hasPermission('erp:inventoryTransfer:update') || !canModify(scope.row), loading: updatingId === scope.row.id, command: { type: 'update', row: scope.row } },
              { label: '取消', type: 'info', icon: 'CircleClose', hidden: !hasPermission('erp:inventoryTransfer:cancel') || !canCancel(scope.row), loading: cancellingId === scope.row.id, command: { type: 'cancel', row: scope.row } },
              { label: '删除', type: 'danger', icon: 'Delete', hidden: !hasPermission('erp:inventoryTransfer:delete') || !canDelete(scope.row), command: { type: 'delete', row: scope.row } }
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

      <!-- 添加或修改对话框 -->
      <el-dialog v-model="open" :title="title" width="960px" append-to-body destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
          <el-divider content-position="left">基本信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="调拨单号" prop="transferNo">
                <el-input v-model="form.transferNo" placeholder="保存后由系统生成" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="调拨类型" prop="transferType">
                <el-select v-model="form.transferType" placeholder="请选择调拨类型" style="width: 100%">
                  <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="调出仓库" prop="fromWarehouseId">
                <!-- 调出仓是这张单的口径线：明细候选按「这个仓还有可用库存」查，成本价也从它那一行库存带出。
                     改单时这一格禁用 —— 两家仓在建单那一下就被冻成 3 调拨中了，换仓等于把冻结挪到别人头上，
                     服务端同样钉回原值（要换仓只能取消这张重开一张） -->
                <el-select v-model="form.fromWarehouseId" :disabled="isEdit" placeholder="请选择调出仓库" filterable
                  style="width: 100%" @change="handleFromWarehouseChange">
                  <el-option v-for="item in fromWarehouseOptions" :key="item.id" :label="item.warehouseName"
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
              <el-form-item label="调入仓库" prop="toWarehouseId">
                <!-- 候选里已经把「调出仓」那一家摘掉了（后端 validate 同样拦"不能是同一家"），
                     所以这里选不到和上面重复的那一格 -->
                <el-select v-model="form.toWarehouseId" :disabled="isEdit" placeholder="请选择调入仓库" filterable
                  style="width: 100%">
                  <el-option v-for="item in toWarehouseOptions" :key="item.id" :label="item.warehouseName"
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
                <!-- 状态不给选：0 是新增的初始档，1 由「审核」推，2/3 由「调拨」按明细进度推，4 由「取消」置 -->
                <el-input :model-value="dict.label(DICT_STATUS, form.status)" placeholder="保存后由系统流转" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="调拨原因" prop="reason">
                <el-input v-model="form.reason" type="textarea" :rows="2" placeholder="选填，如缺货补仓、门店间调配"
                  maxlength="500" show-word-limit />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="备注" prop="remark">
                <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" maxlength="500"
                  show-word-limit />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">调拨明细</el-divider>
          <el-table :data="form.items" size="small" border>
            <!-- 一行是调出仓里的一条库存行（候选来自 shop_erp_inventory），所以这一格选的是规格不是商品 -->
            <el-table-column label="商品规格" min-width="260">
              <template #default="{ row }">
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
              </template>
            </el-table-column>
            <!-- 可用库存只是挑的时候看到的快照：真正能不能挪、能挪几件，由「调拨」那一下按当时的库存数算
                 （锁定那份是下单占着的，压根不在可调范围里） -->
            <el-table-column label="调出仓可用" width="110" align="center">
              <template #default="{ row }">
                <span>{{ row.availableStock == null ? '-' : row.availableStock }}</span>
              </template>
            </el-table-column>
            <el-table-column label="调拨数量" width="130" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="1" :controls="false" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="成本价" width="120" align="center">
              <template #default="{ row }">
                <!-- 不是表单列：后端按调出仓那一行库存的当前成本价带出（改不了，落库也以它为准），
                     这里回显只为看得到这一行的口径 -->
                <span>{{ priceText(row.costPrice) }}</span>
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
            <el-button type="primary" plain icon="Plus" size="small" :disabled="!canPickSku" @click="addItem">添加明细</el-button>
            <span class="items-tip">成本价由后端按调出仓的库存带出，这里只回显；调拨数量想分几次调都行，剩下没调的随时接着调</span>
            <!-- 合计只给录入时看，落库的 total_amount / total_quantity 一律由服务端按明细重算 -->
            <span class="items-total">共 {{ form.items.length }} 行 · 数量 {{ totalQuantity }} · 成本
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
        确认调拨：这一单实际挪多少货就在这里逐行填，点一次「调拨」= 调出仓减、调入仓加，两边同一笔账一次记完
        （库存流水跟着两行：6 调拨出库 + 5 调拨入库）。跟上面的「修改」弹窗是两件事 ——
        那边改的是计划（打算调多少，只有待审核能改），这边登记的是实际（这次真挪了几件），
        所以这里不给删行、也不给改计划数，只有「本次调拨数」一格能填。
        一张单可以反复点，直到每行都调满（状态自动进 3 已完成，两家仓跟着复位成启用）。
      -->
      <el-dialog v-model="transferOpen" title="确认调拨" width="960px" append-to-body destroy-on-close>
        <el-form label-width="110px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="调拨单号">
                <el-input :model-value="transferForm.transferNo" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="调拨类型">
                <el-input :model-value="dict.label(DICT_TYPE, transferForm.transferType)" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="调出仓库">
                <el-input :model-value="transferForm.fromWarehouseName" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="调入仓库">
                <el-input :model-value="transferForm.toWarehouseName" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="调拨原因">
                <el-input :model-value="transferForm.reason" placeholder="未填写" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="当前状态">
                <el-input :model-value="dict.label(DICT_STATUS, transferForm.status)" disabled />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">调拨明细</el-divider>
          <el-table :data="transferForm.items" size="small" border v-loading="transferLoading">
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
            <el-table-column label="计划调拨" width="90" align="center">
              <template #default="{ row }">
                <span>{{ row.quantity || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已调出" width="90" align="center">
              <template #default="{ row }">
                <span>{{ row.outQuantity || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="剩余" width="90" align="center">
              <template #default="{ row }">
                <span>{{ remainingOf(row) }}</span>
              </template>
            </el-table-column>
            <!-- 这一格是"调出仓现在还能挪几件"的快照：锁定那份是下单占着的，动不了。
                 填超了这里不拦（数字可能已经被别人推进过），到后端按当时的库存实打实判 -->
            <el-table-column label="调出仓可用" width="110" align="center">
              <template #default="{ row }">
                <span :class="{ 'stock-warn': overAvailable(row) }">
                  {{ row.availableStock == null ? '-' : row.availableStock }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="本次调拨数" width="130" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.thisQuantity" :min="0" :max="remainingOf(row)" :controls="false"
                  style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="成本价" width="120" align="center">
              <template #default="{ row }">
                <!-- 建单时从调出仓带出来的那个数：调入仓做加权平均用的也是它，两边对得上账 -->
                <span>{{ priceText(row.costPrice) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="items-footer">
            <span class="items-tip">这次不挪的商品把「本次调拨数」留 0 即可，剩下没调满的下次还能接着调；调满的那一行会自动把两家仓的账挪过去</span>
            <span class="items-total">本次数量 {{ transferTotalQuantity }}</span>
          </div>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="transferOpen = false">取 消</el-button>
            <el-button type="primary" :loading="transferSubmitting" @click="submitTransfer">确认调拨</el-button>
          </div>
        </template>
      </el-dialog>

      <!--
        调拨单详情：只读看这一单的来龙去脉 —— 计划多少、已挪多少、两边各进出了几件、三个时间。
        已完成 / 已取消 的两张就只能从这里看明细（那两档既改不了也点不开「调拨」）。
      -->
      <el-dialog v-model="detailOpen" title="调拨单详情" width="960px" append-to-body destroy-on-close>
        <el-descriptions title="基本信息" :column="2" border v-loading="detailLoading">
          <el-descriptions-item label="调拨单号">{{ detail.transferNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="调拨类型">
            <el-tag size="small" :type="dict.tag(DICT_TYPE, detail.transferType)">
              {{ dict.label(DICT_TYPE, detail.transferType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="调出仓库">
            {{ detail.fromWarehouseName || (detail.fromWarehouseId ? `已删除仓库 #${detail.fromWarehouseId}` : '-') }}
            <el-tag v-if="warehouseOdd(detail.fromWarehouseStatus)" class="warehouse-frozen" size="small" type="warning">
              {{ dict.label(DICT_WAREHOUSE, detail.fromWarehouseStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="调入仓库">
            {{ detail.toWarehouseName || (detail.toWarehouseId ? `已删除仓库 #${detail.toWarehouseId}` : '-') }}
            <el-tag v-if="warehouseOdd(detail.toWarehouseStatus)" class="warehouse-frozen" size="small" type="warning">
              {{ dict.label(DICT_WAREHOUSE, detail.toWarehouseStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="dict.tag(DICT_STATUS, detail.status)">
              {{ dict.label(DICT_STATUS, detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="操作员">{{ detail.userName || '未指派' }}</el-descriptions-item>
          <!-- 三个数都是服务端算的：计划总数/调拨成本来自主表，已调出是明细现汇总的子查询 -->
          <el-descriptions-item label="计划总数">{{ detail.totalQuantity || 0 }}</el-descriptions-item>
          <el-descriptions-item label="已调出">
            <span :class="{ 'stock-done': allMoved(detail) }">{{ detail.totalOut || 0 }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="待调出">{{ pendingTotal(detail) }}</el-descriptions-item>
          <el-descriptions-item label="调拨成本">{{ priceText(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">
            {{ validate.formatTime(detail.auditTime, 'YYYY-MM-DD') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="调出时间">
            {{ validate.formatTime(detail.outTime, 'YYYY-MM-DD') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="完成时间">
            {{ validate.formatTime(detail.receiveTime, 'YYYY-MM-DD') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ validate.formatTime(detail.createTime, 'YYYY-MM-DD') || '-' }}</el-descriptions-item>
          <el-descriptions-item label="调拨原因" :span="2">{{ detail.reason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">调拨明细</el-divider>
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
          <el-table-column label="计划调拨" width="100" align="center">
            <template #default="{ row }">
              <span>{{ row.quantity || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已调出" width="90" align="center">
            <template #default="{ row }">
              <span :class="{ 'stock-done': remainingOf(row) === 0 }">{{ row.outQuantity || 0 }}</span>
            </template>
          </el-table-column>
          <!-- 已调入和已调出在两侧一次记完，所以这两个数必然相等；摆出来是让人自己核对账平不平 -->
          <el-table-column label="已调入" width="90" align="center">
            <template #default="{ row }">
              <span>{{ row.receiveQuantity || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="剩余" width="90" align="center">
            <template #default="{ row }">
              <span>{{ remainingOf(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="成本价" width="120" align="center">
            <template #default="{ row }">
              <span>{{ priceText(row.costPrice) }}</span>
            </template>
          </el-table-column>
          <!-- 小计读库里的 amount：它是服务端按 数量 × 成本价 算好落库的那个数，不是这里现乘的 -->
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
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listErpInventoryTransferApi,
  detailErpInventoryTransferApi,
  optionsErpTransferSkuApi,
  addErpInventoryTransferApi,
  updateErpInventoryTransferApi,
  deleteErpInventoryTransferApi,
  auditErpInventoryTransferApi,
  transferErpInventoryTransferApi,
  cancelErpInventoryTransferApi
} from '@/api/erp/inventoryTransfer'
import { optionsErpWarehouseApi } from '@/api/erp/warehouse'
import { getUserDetailApi } from '@/api/system/user'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import TableMoreActions from '@/components/TableMoreActions/index.vue'
import validate from '@/utils/validate'
import { useDict } from '@/utils/dictCache'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
/** 账号类型：0平台 1门店 2送货员 3消费者 4供应商 5ERP操作员；ERP 账号只看/只改自己操作的那几张单 */
const isErpAccount = computed(() => userStore.user.accountType === 5)

/** 操作列走 TableMoreActions，权限得在这里自己判（组件没有 v-permission 那套指令，只认 hidden/disabled） */
const permissions = computed(() => userStore.user.permissions || [])
const hasPermission = (permission: string): boolean => {
  return permissions.value.includes(permission)
}

/**
 * 调拨类型字典：transfer_type（1普通 2紧急 3门店间）
 * 调拨单状态字典：transfer_status = status（0待审核 1已审核 2调出中 3已完成 4已取消）
 * 仓库状态字典：warehouse_status = 列表联表带出的 fromWarehouseStatus / toWarehouseStatus
 * （不是本单的 status！）
 */
const DICT_TYPE = 'transfer_type'
const DICT_STATUS = 'transfer_status'
const DICT_WAREHOUSE = 'warehouse_status'
const dict = useDict(DICT_TYPE, DICT_STATUS, DICT_WAREHOUSE)

const TRANSFER_STATUS_PENDING = 0
const TRANSFER_STATUS_AUDITED = 1
/** 调出中：已经调出过至少一次、还没调满 —— 这一档货已经开始在两家仓之间挪了 */
const TRANSFER_STATUS_OUTING = 2
/**
 * 状态缺失按 0 待审核 算，和后端口径一致（新增时服务端钉的就是 0）
 */
const statusOf = (row: any) => Number(row?.status ?? TRANSFER_STATUS_PENDING)

/** 只有待审核这一档还能改（服务端 update 同样拦）：一进 1 就批下来了，接着还可能已经挪过货 */
const canModify = (row: any) => statusOf(row) === TRANSFER_STATUS_PENDING
/** 能删的和能改的同一档：动了货的单子删掉，库存账和单据就对不上了 */
const canDelete = canModify
/** 「审核」只在待审核这一档出现：1/2/3 早批过了，4 已经作废 */
const canAudit = (row: any) => statusOf(row) === TRANSFER_STATUS_PENDING
/**
 * 「取消」放宽到 0/1：这两档都还是一笔库存没动，取消只是翻状态 + 把两家仓腾出来。
 * 2 调出中 / 3 已完成 不许取消 —— 货已经在两家仓之间挪过了，回退得反向再调一次，那是另一张单的活
 */
const canCancel = (row: any) =>
  [TRANSFER_STATUS_PENDING, TRANSFER_STATUS_AUDITED].includes(statusOf(row))
/**
 * 「调拨」出现于 1 已审核 / 2 调出中（服务端 transfer 同一套判据），且还有没调满的行；
 * 调满之后自动进 3 已完成，这一颗就整条收掉（点了也没有可挪的量）
 */
const canTransfer = (row: any) =>
  [TRANSFER_STATUS_AUDITED, TRANSFER_STATUS_OUTING].includes(statusOf(row)) && !allMoved(row)

/**
 * 仓库的 0 禁用 / 2 盘点中 才在这一格提示：
 * 3 调拨中本来就是这张单自己冻的（跟状态列重复），1 启用是常态，
 * 只有这两档说明"这张单还挂着一家账不正常的仓"（服务端只在仓行还在时放行调拨）
 */
const WAREHOUSE_ODD_STATUS = [0, 2]
const warehouseOdd = (status: any): boolean =>
  status != null && WAREHOUSE_ODD_STATUS.includes(Number(status))

const loading = ref(true)
const submitLoading = ref(false)
const selectedIds = ref<any[]>([])
const total = ref(0)
const dataList = ref<any[]>([])
const title = ref('')
const open = ref(false)

/** 修改弹窗里两家仓禁用（换仓服务端不认），新增不禁；用表单有没有 id 判就够 */
const isEdit = computed(() => form.id != null)

/** 审核 / 取消 是就地翻状态，点了才知道行不行；loading 钉在行上免得连点两次 */
const auditingId = ref<number | string>()
const cancellingId = ref<number | string>()
/** 「修改」要点详情接口把明细捞回来，同理 */
const updatingId = ref<number | string>()

/** 确认调拨弹窗：跟新增/修改那套状态分开，免得两个弹窗互相踩表单 */
const transferOpen = ref(false)
const transferLoading = ref(false)
const transferSubmitting = ref(false)
const transferForm = reactive<any>({ items: [] as any[] })

/** 详情弹窗：只读，数据每次点开重新捞一遍（别的操作员可能刚把进度推前了） */
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
  warehouseId: undefined,
  transferType: undefined,
  status: undefined
})
const dateRange = ref<string[]>([])

// 表单参数：items 是明细数组，整单一起提交
const form = reactive<any>({ items: [] as any[] })

const rules = reactive<FormRules>({
  fromWarehouseId: [{ required: true, message: '请选择调出仓库', trigger: 'change' }],
  toWarehouseId: [{ required: true, message: '请选择调入仓库', trigger: 'change' }]
})

/** 金额的显示口径同商品入库、商品库存那几页（后端返回的 decimal 是字符串，统一走 Number） */
const priceText = (price: any) => (price == null || price === '' ? '￥0.00' : `￥${Number(price).toFixed(2)}`)

/** 行小计：只是录入时的回显，落库的 amount 由服务端按 数量 × 成本价 重算（口径一致，不冲突） */
const rowAmount = (row: any) => (Number(row.quantity || 0) * Number(row.costPrice || 0)).toFixed(2)

const totalQuantity = computed(() =>
  form.items.reduce((sum: number, row: any) => sum + Number(row.quantity || 0), 0)
)
const totalAmount = computed(() =>
  form.items.reduce((sum: number, row: any) => sum + Number(rowAmount(row)), 0)
)

/** 这一行还剩多少要调 = 计划 − 已调出（调拨弹窗里既是回显也是本次调拨数的上限） */
const remainingOf = (row: any) =>
  Math.max(0, Number(row.quantity || 0) - Number(row.outQuantity || 0))

/** 整单还剩多少要调 = 计划总数 − 已调出（详情弹窗用） */
const pendingTotal = (row: any) =>
  Math.max(0, Number(row.totalQuantity || 0) - Number(row.totalOut || 0))

/**
 * 整单调满：已调出 ≥ 计划总数。两个数都来自列表 SQL（total_quantity 是主表列、
 * total_out 是明细现汇总的子查询），所以列表上判这一条不用先捞明细
 */
const allMoved = (row: any) =>
  Number(row.totalQuantity || 0) > 0 && Number(row.totalOut || 0) >= Number(row.totalQuantity || 0)

/** 本次调拨数是不是超过了调出仓现在能挪的量：只是标红提醒，拦由后端拦（这个数是快照） */
const overAvailable = (row: any) =>
  row.availableStock != null && Number(row.thisQuantity || 0) > Number(row.availableStock)

const warehouseOptions = ref<any[]>([])

/**
 * 两家仓各自的候选：options 接口只列 1 启用的仓（口径同商品入库），
 * 再把"对面那一家"摘掉 —— 调出/调入不能是同一家，选得到才会在提交前撞后端那道拦。
 * 改单时这两个下拉都是禁用的，而这两家仓正是被这张单自己冻成 3 调拨中（所以不在 options 里），
 * 于是把已选那一条按详情带回的名字补回候选，否则那一格只剩一个裸 id
 */
const warehouseCandidates = (side: 'from' | 'to') => {
  const list = [...warehouseOptions.value]
  const other = side === 'from' ? form.toWarehouseId : form.fromWarehouseId
  const filtered = other ? list.filter((item: any) => String(item.id) !== String(other)) : list
  const current = side === 'from' ? form.fromWarehouseId : form.toWarehouseId
  if (current && !filtered.some((item: any) => String(item.id) === String(current))) {
    const name = side === 'from' ? form.fromWarehouseName : form.toWarehouseName
    filtered.unshift({ id: current, warehouseName: name || `仓库 #${current}` })
  }
  return filtered
}
const fromWarehouseOptions = computed(() => warehouseCandidates('from'))
const toWarehouseOptions = computed(() => warehouseCandidates('to'))

/**
 * 规格候选：远程搜索的结果一直往里合并、不清空。
 * el-select 只显示"当前候选里能匹配上的那条"，所以每搜一次就把列表换掉的话，
 * 其它已选行会立刻显示成裸 id —— 明细里同时有五六行规格，这个必须避开。
 * 范围由后端按「这家调出仓还有可用库存（stock - locked_stock > 0）」收窄（一行是一条库存行）
 */
const skuOptions = ref<any[]>([])
const skuLoading = ref(false)

/** 候选行和明细行都带 skuId，统一成明细那套 key：skuId / name / spec / code / availableStock / costPrice */
const mergeSkuOptions = (list: any[]) => {
  const map = new Map(skuOptions.value.map((item: any) => [String(item.skuId), item]))
  list.forEach((item: any) => {
    map.set(String(item.skuId), {
      skuId: item.skuId,
      name: item.productName || `已删除规格 #${item.skuId}`,
      code: item.productCode,
      // 同一件商品有几个规格就有几行，选中后输入框里得靠这一串分出去
      spec: item.specText,
      // 这一条规格在调出仓现在的可用库存：只是挑的时候看一眼
      availableStock: item.availableStock,
      // 这一行库存当前的成本价（加权后的）：回显用，落库由服务端按同一口径带
      costPrice: item.costPrice
    })
  })
  skuOptions.value = [...map.values()]
}

/** 下拉里那条和选中后输入框里那串都用它：商品名 + 规格（无规格的默认那条不补字） */
const optionLabel = (item: any) => `${item.name}${item.spec ? ` ${item.spec}` : ''}`

/**
 * 明细的规格那一格初始是禁用的：要先有调出仓，挑出来的才是「这个仓还有可用库存」那一份
 * （成本价也是从这个仓的那行库存带的）。编辑回显时已经录好的行照常显示名字
 * （handleUpdate 把明细塞进过候选），只是没仓就没法换挑规格
 */
const canPickSku = computed(() => !!form.fromWarehouseId)

const skuPlaceholder = computed(() =>
  canPickSku.value ? '输入名称/编码/规格搜索' : '请先选择调出仓库'
)

/**
 * 选中规格：把这一行的成本价/可用库存兜成候选那一行的数。
 * 已经录过数量的不动（换规格才覆盖），免得挑错商品把自己录的数冲掉。
 * el-select 的 change 只在用户操作时触发，编辑回显不会走到这里
 */
const handleSkuChange = (row: any, skuId: any) => {
  const option = skuOptions.value.find((item: any) => String(item.skuId) === String(skuId))
  if (!option) return
  row.costPrice = option.costPrice == null ? undefined : Number(option.costPrice)
  row.availableStock = option.availableStock
}

const searchSkus = async (keyword: string) => {
  if (!canPickSku.value) return
  skuLoading.value = true
  try {
    const { data } = await optionsErpTransferSkuApi({
      warehouseId: form.fromWarehouseId,
      keyword: keyword || undefined
    })
    mergeSkuOptions(data || [])
  } catch (error) {
  } finally {
    skuLoading.value = false
  }
}

/**
 * 换调出仓 → 明细整块清回一行空行重录：候选范围（哪些规格有货）和成本价都是从这个仓那一行库存来的，
 * 留着上一家仓的规格，多半在新仓压根没库存，提交时被服务端逐行挡掉，等于留一堆半截数据让人猜。
 * 已选行有货也一样清 —— 这里判不准，不如一次说清
 * el-select 的 change 只在用户操作时触发，编辑弹窗回显不会误清（那两家仓是禁用的）
 */
const handleFromWarehouseChange = () => {
  const hadSkus = form.items.some((row: any) => row.skuId)
  form.items = [emptyItem()]
  skuOptions.value = []
  if (form.fromWarehouseId) {
    searchSkus('')
  }
  if (hadSkus) {
    ElMessage.warning('已切换调出仓库，调拨明细请按该仓的可用库存重新选择')
  }
}

/** 一行空明细：数量 1、成本价留空（选中规格后由那条库存行兜上） */
const emptyItem = () => ({ skuId: undefined, quantity: 1, costPrice: undefined, availableStock: undefined })

const addItem = () => {
  form.items.push(emptyItem())
}

const removeItem = (index: number) => {
  form.items.splice(index, 1)
}

/** 明细的完整性靠这里挡一遍（后端 validate 还会再挡一遍，这里只为少一次往返） */
const checkItems = () => {
  if (!form.items.length) {
    ElMessage.warning('请至少添加一行调拨明细')
    return false
  }
  for (let i = 0; i < form.items.length; i++) {
    const row = form.items[i]
    if (!row.skuId || !row.quantity) {
      ElMessage.warning(`第 ${i + 1} 行明细的商品规格/调拨数量没填全`)
      return false
    }
  }
  if (form.fromWarehouseId && String(form.fromWarehouseId) === String(form.toWarehouseId)) {
    ElMessage.warning('调出仓库和调入仓库不能是同一家')
    return false
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
    const { data } = await listErpInventoryTransferApi(params)
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
    transferNo: undefined,
    fromWarehouseId: undefined,
    toWarehouseId: undefined,
    fromWarehouseName: undefined,
    toWarehouseName: undefined,
    userId: undefined,
    userName: undefined,
    transferType: 1,
    status: TRANSFER_STATUS_PENDING,
    reason: undefined,
    remark: undefined,
    // 明细整单提交，新增时先给一行空行让人直接录
    items: [emptyItem()]
  })
  // 候选也清掉：它是按上一家调出仓查出来的，留着新增时就能挑到有货范围外的规格。
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
  queryParams.transferType = undefined
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
  title.value = '添加调拨单'
  if (isErpAccount.value) await fillSelfAsOperator()
}

/** 修改按钮操作：详情接口带 items 回来，规格名/编码/成本价要塞进候选否则选择框显示裸 id */
const handleUpdate = async (row: any) => {
  updatingId.value = row.id
  reset()
  try {
    const { data } = await detailErpInventoryTransferApi(row.id)
    Object.assign(form, data)
    form.items = data.items || []
    // 明细行上就带着 skuId + 商品名/编码/规格 + 该仓可用库存（联表回填的），直接塞进候选
    mergeSkuOptions(form.items)
    open.value = true
    title.value = '修改调拨单'
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
        await updateErpInventoryTransferApi(form)
        ElMessage.success('修改成功')
      } else {
        await addErpInventoryTransferApi(form)
        ElMessage.success('新增成功，两家仓库已置为调拨中')
      }
      open.value = false
      getList()
    } catch (error) {
    } finally {
      submitLoading.value = false
    }
  })
}

/** 审核：0 待审核 → 1 已审核，审核过才开始调拨 */
const handleAudit = (row: any) => {
  ElMessageBox.confirm(
    `确认审核调拨单「${row.transferNo}」吗？审核通过后就能开始调拨（点「调拨」直接挪两家仓的库存），但这张单就改不了也删不掉了。`,
    '提示',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    auditingId.value = row.id
    try {
      await auditErpInventoryTransferApi(row.id)
      ElMessage.success('审核成功')
      getList()
    } catch (error) {
    } finally {
      auditingId.value = undefined
    }
  }).catch(() => { })
}

/** 取消：0/1 → 4 已取消，两家仓跟着腾出来 */
const handleCancel = (row: any) => {
  ElMessageBox.confirm(
    `确认取消调拨单「${row.transferNo}」吗？这张单还没调出过货，取消只是作废，并把两家仓库的状态改回启用。`,
    '提示',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    cancellingId.value = row.id
    try {
      await cancelErpInventoryTransferApi(row.id)
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
    `确认删除调拨单「${row.transferNo}」吗？明细会一起删掉，两家仓库也跟着腾出来；审核过的删不了（要撤下来只能点「取消」）。`,
    '提示',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    await deleteErpInventoryTransferApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(
    `确认删除选中的 ${selectedIds.value.length} 张调拨单吗？明细会一起删掉，涉及的两家仓库也跟着腾出来；审核过的删不了（复选框也不会让你勾上那些）。`,
    '提示',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    await deleteErpInventoryTransferApi(selectedIds.value)
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
    case 'transfer':
      handleTransfer(row)
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
 * 「调拨」弹窗：点开时按详情接口把这一单的明细捞回来（只读的那几列都在明细上），
 * 每行「本次调拨数」默认按剩余填满 —— 一次调完是常态，不想调的商品把这格改成 0 就行
 */
const handleTransfer = async (row: any) => {
  Object.assign(transferForm, {
    id: undefined,
    transferNo: undefined,
    fromWarehouseId: undefined,
    toWarehouseId: undefined,
    fromWarehouseName: undefined,
    toWarehouseName: undefined,
    transferType: undefined,
    status: undefined,
    reason: undefined,
    items: []
  })
  transferOpen.value = true
  transferLoading.value = true
  try {
    const { data } = await detailErpInventoryTransferApi(row.id)
    Object.assign(transferForm, {
      id: data.id,
      transferNo: data.transferNo,
      fromWarehouseId: data.fromWarehouseId,
      toWarehouseId: data.toWarehouseId,
      fromWarehouseName: data.fromWarehouseName,
      toWarehouseName: data.toWarehouseName,
      transferType: data.transferType,
      status: data.status,
      reason: data.reason
    })
    transferForm.items = (data.items || []).map((item: any) => ({
      id: item.id,
      skuId: item.skuId,
      productName: item.productName,
      productCode: item.productCode,
      specText: item.specText,
      quantity: Number(item.quantity || 0),
      outQuantity: Number(item.outQuantity || 0),
      availableStock: item.availableStock,
      costPrice: item.costPrice == null ? undefined : Number(item.costPrice),
      thisQuantity: remainingOf(item)
    }))
  } catch (error) {
    transferOpen.value = false
  } finally {
    transferLoading.value = false
  }
}

const transferTotalQuantity = computed(() =>
  transferForm.items.reduce((sum: number, row: any) => sum + Number(row.thisQuantity || 0), 0)
)

/** 提交前挡一遍：至少要有一行要调，且不能超剩余（el-input-number 的 max 已经压过一道，这里兜手输） */
const checkTransfer = () => {
  if (!transferForm.items.length) {
    ElMessage.warning('这张调拨单还没有明细，先去「修改」里把商品规格填上')
    return false
  }
  let planned = 0
  for (let i = 0; i < transferForm.items.length; i++) {
    const row = transferForm.items[i]
    const thisTime = Number(row.thisQuantity || 0)
    if (thisTime === 0) continue
    planned += thisTime
    if (thisTime > remainingOf(row)) {
      ElMessage.warning(`第 ${i + 1} 行明细本次调拨数超过了剩余的 ${remainingOf(row)} 件`)
      return false
    }
  }
  if (planned === 0) {
    ElMessage.warning('本次调拨数全是 0，没有要挪的货')
    return false
  }
  return true
}

const submitTransfer = async () => {
  if (!checkTransfer()) return
  transferSubmitting.value = true
  try {
    // 只回 id + 每行的 { id, thisQuantity }：状态、时间、两家仓的账、成本价、进度都由后端算
    await transferErpInventoryTransferApi({
      id: transferForm.id,
      items: transferForm.items.map((row: any) => ({
        id: row.id,
        thisQuantity: Number(row.thisQuantity || 0)
      }))
    })
    ElMessage.success('调拨成功，两家仓库的库存已更新')
    transferOpen.value = false
    getList()
  } catch (error) {
  } finally {
    transferSubmitting.value = false
  }
}

/** 详情：只读，每次重新捞（已调出数、两家仓状态都可能已经被别人推进） */
const handleDetail = async (row: any) => {
  detail.value = {}
  detailOpen.value = true
  detailLoading.value = true
  try {
    const { data } = await detailErpInventoryTransferApi(row.id)
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
 * 所以这里拿到的是全部启用的仓 —— 建单要求两家仓当时都是启用，这一份正好是同一个口径
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

/* 时间范围那一行（照抄采购单/商品入库那两页的 .search-row）：wrapper 独占整行把日期格顶到第二行，
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

/* 明细行里规格下拉的 名称 + 规格 + 编码（同销售单/商品入库那两页的排法） */
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

/* 仓库那一格的提示标签（禁用中/盘点中）：跟仓库名并排，别把格子撑高 */
.warehouse-frozen {
  margin-left: 6px;
}

/* 调满的那一行/那一格数字：绿色加粗（同商品入库那页的已入库） */
.stock-done {
  color: var(--el-color-success);
  font-weight: 600;
}

/* 本次调拨数已经盖过调出仓可用量的那一格：只是提醒，真正拦不拦由后端按当时的库存判 */
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
