<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="请输入出库单号/订单号/采购单号/下单人/操作人/备注" clearable
            @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="出库来源" prop="outboundSource">
          <el-select v-model="queryParams.outboundSource" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_SOURCE)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>

        <!-- 时间范围 + 按钮同一行（照抄商品入库的 .search-row）：这一层 wrapper 独占第二行，
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
          <el-button v-permission="['erp:saleOutbound:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新建</el-button>
          <el-button v-permission="['erp:saleOutbound:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" :selectable="canSelect" />
        <el-table-column label="出库单号" prop="outboundNo" align="center" min-width="190" />
        <!-- 来源单据号/供应商名/操作人名称都是查询时联表现取的，改过名不用回头刷单子。
             order_id / order_no 这一对是按「出库来源」分指三张表的（1 线上购买 = 订单号，
             2 销售出库 = 销售单号，3 采购退货 = 采购单号），所以这一列的表头不能写死成"关联订单"；
             4 调拨出库 / 5 报损出库 / 6 其他 是后台手工建的，压根没有来源单据（这两列就是 NULL） -->
        <el-table-column label="来源单据" align="center" min-width="180">
          <template #default="{ row }">
            <span>{{ sourceNoOf(row) }}</span>
          </template>
        </el-table-column>
        <!-- 同一列三路共用：线上那一路是下单的买家，采购退货那一路是收货的供应商（后端按来源分别联出来）；
             销售出库那一路两头都没有（货是按后台开的销售单出的），这一格就是 - -->
        <el-table-column label="下单人 / 供应商" align="center" min-width="130">
          <template #default="{ row }">
            <span>{{ (isOnlineOrder(row) ? row.buyerName : row.supplierName) || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="订单状态" align="center" width="100">
          <template #default="{ row }">
            <!-- 只有来源=1 这一路有订单可看；采购退货那一路 order_id 指的是采购单，这一格就是 - -->
            <el-tag v-if="isOnlineOrder(row)" size="small" :type="dict.tag(DICT_ORDER_STATUS, row.orderStatus)">
              {{ dict.label(DICT_ORDER_STATUS, row.orderStatus) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="出库来源" align="center" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_SOURCE, row.outboundSource)">
              {{ dict.label(DICT_SOURCE, row.outboundSource) }}
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
        <el-table-column label="计划出库" prop="totalQuantity" align="center" width="90" />
        <!-- 已出库是明细 outbound_quantity 现汇总出来的（列表 SQL 里的子查询），出过几次货一目了然 -->
        <el-table-column label="已出库" align="center" width="90">
          <template #default="{ row }">
            <span :class="{ 'stock-done': allOut(row) }">{{ row.totalOut || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="出库金额" align="center" width="130">
          <template #default="{ row }">
            <span>{{ priceText(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="出库操作人" align="center" min-width="110">
          <template #default="{ row }">
            <span>{{ row.userName || '未记录' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="240" fixed="right">
          <template #default="scope">
            <!-- 权限没过用 hidden（整条不给看）；删不掉、出库仓冻着账都用 disabled + tip：按钮还在、点不动，悬浮说清是哪条原因
                 （买家已下单 / 已经出过货 / 发货仓正在盘点或调拨），口径同服务端 deleteByIds 那两道闸和库存记账那一层。
                 配送单不由这一颗建了：统一下单那一刻就建好（重量/距离/配送费在那一次定死，买家付的钱里含着它），
                 支付回调把它换绑到这张出库单上。所以这里两颗是「生成配送单」和「配送单」：
                 · 「生成配送单」只在还停在 0 待派单 时给看（deliveryStatus === 0）：点开是这一单那条配送单的详情，
                   弹框里的「确认」才真正把状态推成 1 待接单、进送货员的抢单池 ——
                   先在弹框里把发货点/收件点/距离/重量看一遍再派车，不看清楚就派车等于蒙着派；
                   一确认这颗就自己消失（状态不再是 0），换成「配送单」去看那一条 ——
                   同一颗按钮既出现过又置灰，等于让操作员去猜为什么点不动；
                 · 「配送单」绑过就给看（判据是列表 SQL 带回的 deliveryId），但还停在 0 待派单 的那几条不给看：
                   那一会儿「生成配送单」点开的是同一张详情，两颗同一时刻只亮一颗（口径同上一版"建过就整条不给看"）。
                 没出满（allOut 为假）时两颗都不给看：一批货分几次出，前几次出完就派车等于让送货员跑两趟。
                 来源不是 1 线上购买 的两颗都不给看：采购退货那一路货是退回供应商的，没有收件人也没有派送 -->
            <TableMoreActions :actions="[
              { label: '出库', type: 'success', icon: 'Sell', hidden: !hasPermission('erp:saleOutbound:outbound') || allOut(scope.row), disabled: frozenRows(scope.row) > 0, tip: frozenRows(scope.row) > 0 ? frozenOutTip(scope.row) : undefined, command: { type: 'outbound', row: scope.row } },
              { label: '生成配送单', icon: 'Van', hidden: !hasPermission('erp:saleOutbound:delivery') || !isOnlineOrder(scope.row) || !allOut(scope.row) || !scope.row.deliveryId || scope.row.deliveryStatus !== 0, tip: `货已出齐：先看配送单「${scope.row.deliveryNo}」，在弹框里点「确认」把它推成「待接单」，送货员才看得见这一趟`, command: { type: 'createDelivery', row: scope.row } },
              { label: '配送单', icon: 'Document', hidden: !hasPermission('erp:saleOutbound:detail') || !isOnlineOrder(scope.row) || !scope.row.deliveryId || scope.row.deliveryStatus === 0, tip: scope.row.deliveryNo ? `查看配送单「${scope.row.deliveryNo}」` : '查看这条配送单', command: { type: 'deliveryDetail', row: scope.row } },
              { label: '详情', icon: 'View', hidden: !hasPermission('erp:saleOutbound:detail'), command: { type: 'detail', row: scope.row } },
              { label: '删除', type: 'danger', icon: 'Delete', hidden: !hasPermission('erp:saleOutbound:delete'), disabled: !!deleteTip(scope.row), tip: deleteTip(scope.row), command: { type: 'delete', row: scope.row } }
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
        出库单详情：只读看这一单的来龙去脉 —— 计划多少、实际出了多少、还剩多少。
        要登记出货点列表上的「出库」，这里不给动（口径同商品入库那页的详情）。
        右上角「打印」把这一张单连明细交给浏览器打印窗口，在那里选「另存为 PDF」就是 PDF。
      -->
      <el-dialog v-model="detailOpen" width="900px" append-to-body destroy-on-close>
        <template #header>
          <div class="dialog-head">
            <span class="el-dialog__title">出库单详情</span>
            <el-button v-if="detail.outboundNo" type="primary" plain size="small"
              @click="handleDetailPrint">打 印</el-button>
          </div>
        </template>
        <el-descriptions title="基本信息" :column="2" border v-loading="detailLoading">
          <el-descriptions-item label="出库单号">{{ detail.outboundNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源单据">{{ sourceNoOf(detail) }}</el-descriptions-item>
          <el-descriptions-item label="出库来源">
            <el-tag size="small" :type="dict.tag(DICT_SOURCE, detail.outboundSource)">
              {{ dict.label(DICT_SOURCE, detail.outboundSource) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="dict.tag(DICT_TYPE, detail.status)">
              {{ dict.label(DICT_TYPE, detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <!-- 只有来源=1 这一路联得到订单；采购退货那一路 order_id 指的是采购单 -->
            <el-tag v-if="isOnlineOrder(detail)" size="small" :type="dict.tag(DICT_ORDER_STATUS, detail.orderStatus)">
              {{ dict.label(DICT_ORDER_STATUS, detail.orderStatus) }}
            </el-tag>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="下单人 / 供应商">
            {{ (isOnlineOrder(detail) ? detail.buyerName : detail.supplierName) || '-' }}
          </el-descriptions-item>
          <!-- 三个数都是服务端按明细算的：计划总数/出库金额来自主表，已出库是明细现汇总的子查询 -->
          <el-descriptions-item label="计划总数">{{ detail.totalQuantity || 0 }}</el-descriptions-item>
          <el-descriptions-item label="已出库">
            <span :class="{ 'stock-done': allOut(detail) }">{{ detail.totalOut || 0 }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="待出库">{{ pendingTotal(detail) }}</el-descriptions-item>
          <el-descriptions-item label="出库金额">{{ priceText(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="出库操作人">{{ detail.userName || '未记录' }}</el-descriptions-item>
          <el-descriptions-item label="出库时间">
            {{ validate.formatTime(detail.outboundTime, 'YYYY-MM-DD') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ validate.formatTime(detail.createTime, 'YYYY-MM-DD') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">出库明细</el-divider>
        <el-table :data="detail.items" size="small" border v-loading="detailLoading" class = "product-item">
          <el-table-column label="商品" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="product-option">
                <span class="product-option__name">{{ row.productName || `已删除规格 #${row.skuId}` }}</span>
                <span class="product-option__code">{{ row.productCode }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="规格" align="center" min-width="110">
            <template #default="{ row }">
              <span>{{ row.specText || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="发货仓库" align="center" min-width="130">
            <template #default="{ row }">
              <span>{{ row.warehouseName || `已删除仓库 #${row.warehouseId}` }}</span>
              <!-- 盘点中 / 调拨中 的仓账已冻结，这一行出不了货（列表上「出库」也按它置灰） -->
              <el-tag v-if="warehouseFrozen(row)" class="warehouse-frozen" size="small" type="warning">
                {{ dict.label(DICT_WAREHOUSE, row.warehouseStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="计划出库" width="90" align="center">
            <template #default="{ row }">
              <span>{{ row.quantity || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已出库" width="90" align="center">
            <template #default="{ row }">
              <span :class="{ 'stock-done': remainingOf(row) === 0 }">{{ row.outboundQuantity || 0 }}</span>
            </template>
          </el-table-column>
          <!-- <el-table-column label="剩余" width="90" align="center">
            <template #default="{ row }">
              <span>{{ remainingOf(row) }}</span>
            </template>
          </el-table-column> -->
          <el-table-column label="成本价" width="110" align="center">
            <template #default="{ row }">
              <span>{{ row.costPrice == null ? '-' : priceText(row.costPrice) }}</span>
            </template>
          </el-table-column>
          <!-- 小计读库里的 amount：它是服务端按 计划数 × 成本价 算好落库的那个数，不是这里现乘的 -->
          <el-table-column label="小计" width="110" align="center">
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
        这一单那条配送单（统一下单时建的、支付回调换绑到这张出库单上）：内容两边共用，都由组件按 id 自己查。
        · 「配送单」那颗点开只看；
        · 「生成配送单」那颗点开的是同一份东西，只是底下多一颗「确认」—— 发货点/收件点/距离/重量先看一遍，
          看完再点确认把它派出去（0 待派单 → 1 待接单）。
      -->
      <delivery-detail v-model:visible="deliveryDetailOpen" :id="deliveryDetailId" :title="deliveryDetailTitle"
        :confirmable="deliveryConfirmable" :confirm-loading="deliveryConfirming" @confirm="handleDeliveryConfirm" />

      <!--
        出库：这张单实际发多少货就在这里逐行填。
        一张单可以分几次出（没发的行把「本次出库数」留 0，剩下的量下次还能继续出），
        所以这里不给删行、也不给改计划数，只有「本次出库数」和（非线上订单时）「出库成本价」两格能填。
        提交成功后货才真从该仓库存里减掉、下单锁住的可用量还回去、库存流水记一行，
        单据和来源订单的状态一起流转。
      -->
      <el-dialog v-model="outOpen" title="确认出库" width="1080px" append-to-body destroy-on-close>
        <el-form label-width="110px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="出库单号">
                <el-input :model-value="outForm.outboundNo" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="来源单据">
                <el-input :model-value="outForm.orderNo || '未关联'" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="出库来源">
                <!-- 只回显：来源决定成本价能不能填、要不要归还下单锁定的库存，不是这一单能改的事 -->
                <el-input :model-value="dict.label(DICT_SOURCE, outForm.outboundSource)" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="下单人 / 供应商">
                <el-input :model-value="(isOnlineSource ? outForm.buyerName : outForm.supplierName) || '-'" disabled />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">出库明细</el-divider>
          <el-table :data="outForm.items" size="small" border v-loading="outLoading" class = "product-item">
            <el-table-column label="商品" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="product-option">
                  <span class="product-option__name">{{ row.productName || `已删除规格 #${row.skuId}` }}</span>
                  <span class="product-option__code">{{ row.productCode }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="规格" align="center" min-width="100">
              <template #default="{ row }">
                <span>{{ row.specText || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="发货仓库" align="center" min-width="120">
              <template #default="{ row }">
                <span>{{ row.warehouseName || `已删除仓库 #${row.warehouseId}` }}</span>
                <!-- 这一格是「本次出库数」填不进去的原因：仓在盘点或调拨，账冻着（服务端同样拦） -->
                <el-tag v-if="warehouseFrozen(row)" class="warehouse-frozen" size="small" type="warning">
                  {{ dict.label(DICT_WAREHOUSE, row.warehouseStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="计划出库" width="90" align="center">
              <template #default="{ row }">
                <span>{{ row.quantity || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已出库" width="90" align="center">
              <template #default="{ row }">
                <span>{{ row.outboundQuantity || 0 }}</span>
              </template>
            </el-table-column>
            <!-- <el-table-column label="剩余" width="90" align="center">
              <template #default="{ row }">
                <span>{{ remainingOf(row) }}</span>
              </template>
            </el-table-column> -->
            <!-- 这一列只在「非线上订单」时出现：线上订单要出的这批货本来就是锁在这一仓的那一份
                 （支付回调改派过仓时锁定会跟着挪过来），「可用库存」已经把它扣掉了，
                 显示出来只会是一个让人不敢填的 0（口径见 maxThis） -->
            <el-table-column v-if="!isOnlineSource" label="该仓可用" width="100" align="center">
              <template #default="{ row }">
                <span :class="{ 'stock-empty': Number(maxOf(row)) === 0 }">{{ row.availableStock ?? 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="本次出库数" width="130" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.thisQuantity" :min="0" :max="maxOf(row)" :controls="false"
                  :disabled="Number(maxOf(row)) === 0" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="出库成本价" width="140" align="center">
              <template #default="{ row }">
                <!-- 线上订单：成本价在支付回调时就按这一行订单明细的实付金额摊好了，这一格禁用（传了也不认）；
                     其它来源由操作员填，出库时按「计划数 × 成本价」结转小计 -->
                <el-input-number v-model="row.costPrice" :min="0" :precision="2" :controls="false"
                  :disabled="isOnlineSource" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="小计" width="120" align="center">
              <template #default="{ row }">
                <!-- 线上订单这一格就是库里那行的 amount（回调定的实付金额，出库不会改它）；
                     非线上是现乘的，成本价一改立刻跟着变 -->
                <span>{{ priceText(rowAmount(row)) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="items-footer">
            <span class="items-tip">{{ outTip }}</span>
            <span class="items-total">共 {{ outForm.items.length }} 行 · 本次数量 {{ outTotalQuantity }} · 计划金额
              {{ priceText(outTotalAmount) }}</span>
          </div>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="outOpen = false">取 消</el-button>
            <el-button type="primary" :loading="outSubmitting" @click="submitOutbound">确认出库</el-button>
          </div>
        </template>
      </el-dialog>

      <!--
        新建出库单：来源给四档（默认 2 销售出库）—— 1 线上购买由支付回调按订单明细自动建、
        3 采购退货由采购单页那颗「采购退货」建，那两档不在下拉里（后端 insert 同样只认这四档）。
        形状照抄商品入库那页的「新增」：2 那一路选一张已审核的销售单 → 明细整块按它带进来，
        只有「本次出库数」「出库仓库」「成本价」三格能填、不给加行；4/5/6 那三路没有来源单据，
        规格自己逐行挑（同入库单的非采购来源那一路）。
        这里只是登记：提交后建的是 0 待出库 的单子，货要在这页点「出库」才真离仓、才减库存。
        一张销售单可以被几张出库单分着登记，出满之前它一直在下拉里（额度 = 销售量 − 名下已登记量）
      -->
      <el-dialog v-model="addOpen" title="新建出库单" width="1080px" append-to-body destroy-on-close>
        <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="110px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="出库单号">
                <el-input v-model="addForm.outboundNo" placeholder="保存后由系统生成" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="出库来源" prop="outboundSource">
                <!-- 它管着下面整块：选 2 才有「销售单」那一格、明细从销售单带出并且受额度约束；
                     选 4/5/6 就是手工加行，建出来的单子没有来源单据（order_id/order_no 为 NULL） -->
                <el-select v-model="addForm.outboundSource" style="width: 100%" @change="handleSourceChange">
                  <el-option v-for="item in formSourceOptions" :key="item.value" :label="item.label"
                    :value="Number(item.value)" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12" v-if="isSaleSource">
              <el-form-item label="销售单" prop="orderId">
                <!-- 候选只列「已审核」的销售单：每行都被名下出库单登记满的那张会自动流转成「待出库」、
                     出满的成「已出库」，两张都不再出现在下拉里（服务端 refreshSaleStatus 推的状态），
                     所以挑得到的就一定还有货没登记。一单选一张，选了就把它的明细整块带进来 -->
                <el-select v-model="addForm.orderId" placeholder="请选择销售单" filterable style="width: 100%"
                  :loading="saleLoading" @change="handleSaleChange">
                  <el-option v-for="item in saleOptions" :key="item.id" :label="item.saleNo" :value="item.id">
                    <div class="product-option">
                      <span class="product-option__name">{{ item.saleNo }}</span>
                      <span class="product-option__code">{{ item.storeName || '-' }}</span>
                    </div>
                  </el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12" v-if="isSaleSource">
              <el-form-item label="销售门店">
                <el-input :model-value="addForm.storeName" placeholder="选销售单后自动带出" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="备注" prop="remark">
                <el-input v-model="addForm.remark" type="textarea" :rows="2" placeholder="选填（调拨/报损这一路建议写清楚这批货去哪儿、为什么出）"
                  maxlength="500" show-word-limit />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">出库明细</el-divider>
          <div class="items-toolbar">
            <!-- 删掉的行后悔了不用重选销售单（那一下会把整块明细覆盖掉），按销售单重新捞一次还能出的行 -->
            <el-button v-if="isSaleSource" type="primary" plain icon="Plus" size="small" :disabled="!addForm.orderId"
              :loading="itemsLoading" @click="importItems">导入明细</el-button>
            <!-- 非销售那三路没有来源单据可带，规格自己从商品库里挑（口径同商品入库那页的选择器） -->
            <el-button v-else type="primary" plain icon="Plus" size="small" @click="addItem">添加规格</el-button>
          </div>
          <el-table :data="addForm.items" size="small" border v-loading="itemsLoading" class="product-item">
            <el-table-column label="商品规格" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <shop-product-sku-select v-if="!isSaleSource" v-model="row.skuId" :label="skuText(row)"
                  @select="handleItemProductSelect(row, $event)" @clear="handleItemProductClear(row)" />
                <div v-else class="product-option">
                  <span class="product-option__name">{{ row.productName || `已删除规格 #${row.skuId}` }}</span>
                  <span class="product-option__spec">{{ row.specText || '默认规格' }}</span>
                  <span class="product-option__code">{{ row.productCode }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column v-if="isSaleSource" label="销售量" width="90" align="center">
              <template #default="{ row }">
                <span>{{ row.saleQuantity || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="isSaleSource" label="还能出" width="90" align="center">
              <template #default="{ row }">
                <!-- 上限就是这一格的数（销售量 − 这张销售单名下其它出库单已登记的量）；后端建单按同一个数再挡一遍 -->
                <span>{{ row.availableQuantity || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="本次出库数" width="130" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="0" :max="addQuantityMax(row)" :controls="false"
                  style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="出库仓库" width="200" align="center">
              <template #default="{ row }">
                <!-- 销售那一路默认带出销售单上那一格「发货仓库」（开单时定了仓的话），可以改；
                     手工那三路没有默认值，每行自己挑。
                     仓里够不够货不在这里判：建的是待出库的单子，真出库那一步按 (规格, 仓库) 扣账 -->
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
            <el-table-column label="出库成本价" width="140" align="center">
              <template #default="{ row }">
                <!-- 销售那一路默认带出这条规格的加权销售单价，可以改；手工那三路没得默认，必填。
                     真出库时还要按实际发的数结转一次 -->
                <el-input-number v-model="row.costPrice" :min="0" :precision="2" :controls="false"
                  style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="小计" width="120" align="center">
              <template #default="{ row }">
                <span>{{ priceText(addRowAmount(row)) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ $index }">
                <el-button type="danger" link icon="Delete" @click="removeAddItem($index)" />
              </template>
            </el-table-column>
          </el-table>
          <div class="items-footer">
            <span class="items-tip" v-if="!isSaleSource">这一路没有来源销售单：点上面的「添加规格」逐行加，出库数量、出库仓库、成本价自己填（提交后端的额度这道闸不挡，货够不够在点「出库」那一步按仓库扣账时才判）</span>
            <span class="items-tip" v-else-if="!addForm.orderId">请先在上方选择销售单，明细会自动带出</span>
            <span class="items-tip" v-else>这里只是登记，提交后建的是一张「待出库」的单子；出库数量默认按「还能出」填满，这次少出的量下次还能再建一张接着登记（误删的行点上面的「导入明细」找回）</span>
            <span class="items-total">共 {{ addForm.items.length }} 行 · 本次数量 {{ addTotalQuantity }} · 金额
              {{ priceText(addTotalAmount) }}</span>
          </div>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="addOpen = false">取 消</el-button>
            <el-button type="primary" :loading="addSubmitting" @click="submitAdd">确 定</el-button>
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
  listErpSaleOutboundApi,
  detailErpSaleOutboundApi,
  deleteErpSaleOutboundApi,
  outboundErpSaleOutboundApi,
  confirmDeliveryErpSaleOutboundApi,
  saleItemsErpSaleOutboundApi,
  addErpSaleOutboundApi
} from '@/api/erp/saleOutbound'
import { listErpSaleApi } from '@/api/erp/sale'
import { optionsErpWarehouseApi } from '@/api/erp/warehouse'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import TableMoreActions from '@/components/TableMoreActions/index.vue'
import DeliveryDetail from '@/components/DeliveryDetail/index.vue'
import ShopProductSkuSelect from '@/components/ShopProductSkuSelect/index.vue'
import validate from '@/utils/validate'
import { printOutbounds } from '@/utils/print'
import { useDict } from '@/utils/dictCache'
import { useUserStore } from '@/store/modules/user'

/** 操作列走 TableMoreActions，权限得在这里自己判（组件收的是 actions 数组，用不了 v-permission 指令） */
const userStore = useUserStore()
const permissions = computed(() => userStore.user.permissions || [])
const hasPermission = (permission: string): boolean => permissions.value.includes(permission)

/** 出库单状态 / 出库来源；订单状态是既有字典 */
const DICT_TYPE = 'sale_outbound_status'
const DICT_SOURCE = 'sale_outbound_source'
const DICT_ORDER_STATUS = 'order_status'
const DICT_WAREHOUSE = 'warehouse_status'
const dict = useDict(DICT_TYPE, DICT_SOURCE, DICT_ORDER_STATUS, DICT_WAREHOUSE)

/** 出库来源：1 线上购买（支付回调按订单明细自动建的那一路，值域 2026-09-21 重编成六档）；
 *  2 销售出库 是这一页「新建」按销售单建的；3 采购退货 由采购单页那颗按钮建；
 *  4 调拨出库 / 5 报损出库 / 6 其他 也从这一页「新建」建，但没有来源单据（见下面 formSourceOptions） */
const SOURCE_ONLINE_ORDER = 1
const SOURCE_SALE_OUTBOUND = 2

/**
 * 「新建出库单」那一格「出库来源」的候选：只给后台真能手工建的 2/4/5/6 四档。
 * 1 线上购买 由支付回调按订单建、3 采购退货 由采购单页那颗「采购退货」建，从这一颗选它们
 * 等于伪造一张挂不上来源单据的单子，所以下拉里压根不给这两档（列表和查询仍按全字典渲染标签）
 */
const FORM_SOURCE_VALUES = [SOURCE_SALE_OUTBOUND, 4, 5, 6]
const formSourceOptions = computed(() =>
  dict.options(DICT_SOURCE).filter((item: any) => FORM_SOURCE_VALUES.includes(Number(item.value)))
)

const loading = ref(true)
const selectedIds = ref<any[]>([])
const total = ref(0)
const dataList = ref<any[]>([])

/** 出库弹窗：明细行一旦出了货就改不回来（出了多少记多少），所以没有修改弹窗；
 *  建单只有那一颗「新建」，来源给 2/4/5/6 四档（见下面的 addForm） */
const outOpen = ref(false)
const outLoading = ref(false)
const outSubmitting = ref(false)
const outForm = reactive<any>({ items: [] as any[] })

/** 详情弹窗：只读，数据每次点开重新捞一遍（已出库数会被别的操作员推进） */
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<any>({})

/** 这张单那条配送单的详情：id 来自列表 SQL 带回的 deliveryId，弹窗内容交给 DeliveryDetail 组件自己查 */
const deliveryDetailOpen = ref(false)
const deliveryDetailId = ref<number | string>()
// 从「生成配送单」进来才给弹框底下那一颗「确认」（从「配送单」进来只看不派）；
// 确认要打出库单 id，所以把整行留下，不用回头再查一次
const deliveryConfirmable = ref(false)
const deliveryConfirming = ref(false)
const deliveryConfirmRow = ref<any>({})

const deliveryDetailTitle = computed(() => (deliveryConfirmable.value ? '生成配送单' : '配送单详情'))

const openDeliveryDetail = (row: any, confirmable: boolean) => {
  deliveryDetailId.value = row.deliveryId
  deliveryConfirmable.value = confirmable
  deliveryConfirmRow.value = row
  deliveryDetailOpen.value = true
}

const queryFormRef = ref<FormInstance>()

// 查询参数：userId（按操作人筛）后端支持，这一版界面没放，需要时加一格 UserSelect 即可
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  outboundSource: undefined,
  status: undefined
})
const dateRange = ref<string[]>([])

/** 金额的显示口径同商品入库（后端返回的 decimal 是字符串，统一走 Number） */
const priceText = (price: any) => (price == null || price === '' ? '￥0.00' : `￥${Number(price).toFixed(2)}`)

/** 当前这张单是不是线上订单那一路（决定成本价能不能填、上限按不按可用库存卡） */
const isOnlineSource = computed(() => Number(outForm.outboundSource) === SOURCE_ONLINE_ORDER)

/**
 * 行小计。
 *
 * 线上订单：这一行的成本价和金额在支付回调时就按订单明细的实付金额定死了（金额是实付总数，
 * 成本价是它摊到每件的数，摊的时候有分位舍入），所以直接读库里的 amount，
 * 不能再拿「计划数 × 单价」重乘一遍 —— 重乘会把那几分钱的舍入差重新算出来，跟订单对不上。
 * 其它来源：成本价是这里能改的，按计划数 × 成本价现算，改一下就看到金额跟着动（口径同入库单）
 */
const rowAmount = (row: any) => {
  if (isOnlineSource.value) return Number(row.amount || 0).toFixed(2)
  return (Number(row.quantity || 0) * Number(row.costPrice || 0)).toFixed(2)
}

/**
 * 整单是否已出满：totalOut 是列表/详情 SQL 里按明细现汇总出来的那一列。
 * totalQuantity 为 0（没有明细）不算出满，这种单子本来也出不了货
 */
const allOut = (row: any) =>
  Number(row.totalQuantity || 0) > 0 && Number(row.totalOut || 0) >= Number(row.totalQuantity || 0)

/**
 * 出过货（哪怕只出了一件）：删不掉。
 * 后端拦的是「任一明细行 outbound_quantity > 0」和「状态已是 2」两道，totalOut > 0 就是前一道
 */
const hasShipped = (row: any) => Number(row.totalOut || 0) > 0

/** 这张单是不是线上订单那一路（列表上的判断，和弹窗里那个按 outForm 算的 isOnlineSource 同一口径） */
const isOnlineOrder = (row: any) => Number(row.outboundSource) === SOURCE_ONLINE_ORDER

/**
 * 仓库的 2 盘点中 / 3 调拨中 两档把账冻住了：仓里的货正在数或者正在挪，这时候出货，
 * 盘点结果和调拨单都对不上（服务端在库存记账那层拦同样的两档，判据见 com.shop.utils.WarehouseUtils）。
 * 一行一仓，所以主单看的是"有几行冻着"（列表 SQL 的 frozenRows 子查询），明细行看自己的 warehouseStatus
 */
const WAREHOUSE_FROZEN_STATUS = [2, 3]
const warehouseFrozen = (row: any): boolean =>
  WAREHOUSE_FROZEN_STATUS.includes(Number(row?.warehouseStatus))
const frozenRows = (row: any): number => Number(row?.frozenRows || 0)
/** 「出库」置灰时悬浮说清冻了几行、冻在哪两档（哪一行冻的看明细那格的徽标） */
const frozenOutTip = (row: any): string => {
  const statuses = Array.from(new Set([2, 3].map(s => dict.label(DICT_WAREHOUSE, s))))
  return `这张单有 ${frozenRows(row)} 行的发货仓冻着账（${statuses.join(' / ')}），`
    + '先在「仓库管理」里把仓库改回启用才能出库'
}

/**
 * 「来源单据」那一格四路共用：1 订单号 / 2 销售单号 / 3 采购单号（都是后端联表现取的）；
 * 号取不到但 id 有值 = 来源单据被删过了，显示 id 免得看不出来；
 * 4 调拨出库 / 5 报损出库 / 6 其他 是后台手工建的，本来就没有来源单据，直接显示「未关联」
 */
const sourceNoOf = (row: any) =>
  row.orderNo || (row.orderId ? `未关联 #${row.orderId}` : '未关联')

/**
 * 删不掉的原因，返回空串表示可以删。「删除」按钮拿它决定置灰，悬浮提示也拿它说：
 * 先说来源（这一档一票否决，出没出货都删不了），再说有没有出过货。服务端 deleteByIds 同样两道都拦
 */
const deleteTip = (row: any) => {
  if (isOnlineOrder(row)) {
    return '用户已下单，这张出库单是那一笔订单的发货凭证，不可删除'
  }
  if (hasShipped(row)) {
    return `已经出库 ${Number(row.totalOut || 0)} 件，不可删除；货出错了要去库存里冲账`
  }
  return ''
}

/** 删不掉的单子不给勾，进不了「批量删除」的范围（服务端同样拦） */
const canSelect = (row: any) => !deleteTip(row)

/** 整单还剩多少要出 = 计划总数 − 已出库（详情弹窗用；行级别的剩余见 remainingOf） */
const pendingTotal = (row: any) =>
  Math.max(0, Number(row.totalQuantity || 0) - Number(row.totalOut || 0))

/** 明细行还剩多少可出 = 计划 - 已出库 */
const remainingOf = (row: any) =>
  Math.max(0, Number(row.quantity || 0) - Number(row.outboundQuantity || 0))

/**
 * 本次出库数上限。
 *
 * 线上订单这一路不能按「该仓可用库存」卡：统一下单已经把这批货锁在 locked_stock 里
 * （可用 = 总库存 − 锁定，见 ShopErpInventoryMapper#selectAvailableStock），
 * 要出的正是被自己锁住的那一份，按可用卡就成了 0、一格也填不进去。
 * 服务端那边是把锁定量还回去再减账的（ShopErpInventoryServiceImpl#outbound 的 release 参数），
 * 所以这里按「计划 − 已出库」就是真上限。
 *
 * 其它来源（门店 POS、第三方、后台补单）没走过统一下单，仓里本来没锁，
 * 出的是别人的可用量 —— 上限得再对可用库存取一次小，否则提交必被后端挡
 * （可用量出成负数那条闸门）。没有库存行时联不出来 availableStock，按 0 处理。
 */
const maxOf = (row: any) => {
  const remaining = remainingOf(row)
  if (isOnlineSource.value) return remaining
  return Math.min(remaining, Number(row.availableStock ?? 0))
}

const outTip = computed(() => isOnlineSource.value
  ? '线上购买那一路的成本价和小计在支付回调时就按这一行订单明细的实付金额定好了，这一格不用填也改不了；没发的商品把「本次出库数」留 0，剩下的量下次还能继续出'
  : '这一单的货没被订单锁过（采购退货那一路的成本价默认带的是采购单价），出的是该仓的可用量，所以「本次出库数」上限按剩余和可用库存取小的那个')

const outTotalQuantity = computed(() =>
  outForm.items.reduce((sum: number, row: any) => sum + Number(row.thisQuantity || 0), 0)
)
const outTotalAmount = computed(() =>
  outForm.items.reduce((sum: number, row: any) => sum + Number(rowAmount(row)), 0)
)

/** 列表查询 */
const getList = async () => {
  loading.value = true
  try {
    const params: any = { ...queryParams }
    if (dateRange.value && dateRange.value.length === 2) {
      params.beginTime = `${dateRange.value[0]} 00:00:00`
      params.endTime = `${dateRange.value[1]} 23:59:59`
    }
    const { data } = await listErpSaleOutboundApi(params)
    dataList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
  } finally {
    loading.value = false
  }
}

/**
 * 「新建出库单」弹窗：来源四档都从这一颗建（见上面 formSourceOptions），1 线上购买 / 3 采购退货
 * 各有自己的入口。形状照抄商品入库那页的「新增」——那页也是这么按来源分两路的：
 * - 2 销售出库：明细整块从销售单带进来，只有「本次出库数」「出库仓库」「成本价」三格能填，不给加行
 *   （能出的就是销售单里还有余量的那几样，额度 = 销售量 − 名下已登记量）；
 * - 4 调拨 / 5 报损 / 6 其他：没有来源单据，商品自己从商品库里逐行挑，数量/仓库/成本价全自己填。
 * addForm 就是请求体的形状（orderId = 销售单 id、items 每行四列），提交时只挑字段不重排
 */
const addOpen = ref(false)
const addSubmitting = ref(false)
const itemsLoading = ref(false)
const addFormRef = ref<FormInstance>()
const addForm = reactive<any>({
  outboundNo: undefined,
  outboundSource: SOURCE_SALE_OUTBOUND,
  orderId: undefined,
  storeName: undefined,
  remark: undefined,
  items: [] as any[]
})
/** 这一张是不是「销售出库」那一路：它管着销售单那一格必不必填、明细是从销售单带出还是手工加行 */
const isSaleSource = computed(() => Number(addForm.outboundSource) === SOURCE_SALE_OUTBOUND)

const addRules = computed<FormRules>(() => {
  const result: FormRules = {}
  if (isSaleSource.value) {
    result.orderId = [{ required: true, message: '请选择销售单', trigger: 'change' }]
  }
  return result
})

/** 销售单候选取最新多少张（下拉不分页，靠 filterable 在前端过滤，同入库单选采购单那一格） */
const SALE_OPTION_LIMIT = 50

/** 销售单状态：已审核（字典 sale_status 那一档）。货登记满了的会自动流转成「待出库」，不再出现在下拉里 */
const SALE_STATUS_AUDITED = 1

const saleOptions = ref<any[]>([])
const saleLoading = ref(false)
const warehouseOptions = ref<any[]>([])

const getSaleOptions = async () => {
  saleLoading.value = true
  try {
    const { data } = await listErpSaleApi({
      pageNum: 1,
      pageSize: SALE_OPTION_LIMIT,
      status: SALE_STATUS_AUDITED
    })
    saleOptions.value = data.records || []
  } catch (error) {
  } finally {
    saleLoading.value = false
  }
}

/** 仓库候选：options 接口刻意不按 ERP 账号收窄，所以拿到的是全部启用的仓 */
const getWarehouseOptions = async () => {
  try {
    const { data } = await optionsErpWarehouseApi()
    warehouseOptions.value = data || []
  } catch (error) {
  }
}

/**
 * 销售明细行 → 出库明细行，并且按规格合并：同一条规格在销售单里可以占几行（改单不拦重复行），
 * 而后端建单按规格占额度、同一规格提交两行会被直接挡，所以这里先并成一行。
 * 成本价默认取这条规格的加权销售单价（Σ金额 ÷ Σ数量），后端不填时也是取这个数
 */
const toOutboundRows = (list: any[], warehouseId?: any) => {
  const bySku = new Map<string, any>()
  for (const item of list || []) {
    const key = String(item.skuId)
    const merged = bySku.get(key)
    if (merged) {
      merged.saleQuantity += Number(item.quantity || 0)
      merged.availableQuantity += Number(item.availableQuantity || 0)
      merged.amount += Number(item.amount || 0)
      continue
    }
    bySku.set(key, {
      skuId: item.skuId,
      productName: item.productName,
      productCode: item.productCode,
      specText: item.specText,
      saleQuantity: Number(item.quantity || 0),
      availableQuantity: Number(item.availableQuantity || 0),
      amount: Number(item.amount || 0),
      warehouseId
    })
  }
  return [...bySku.values()]
    // 还能出 = 0 的规格已经被名下别的出库单登记满了，带进来只能看不能填（提交必被后端挡），不展示
    .filter((row: any) => row.availableQuantity > 0)
    .map((row: any) => ({
      skuId: row.skuId,
      productName: row.productName,
      productCode: row.productCode,
      specText: row.specText,
      // 这两列不是明细列：只是挂在这一行上显示 + 当上限用，后端按实体反序列化时会忽略
      saleQuantity: row.saleQuantity,
      availableQuantity: row.availableQuantity,
      // 本次出库数默认填满「还能出」（多数情况一次登记完），少出的量下次还能再建一张
      quantity: row.availableQuantity,
      warehouseId: row.warehouseId,
      costPrice: row.saleQuantity > 0 ? Number((row.amount / row.saleQuantity).toFixed(2)) : undefined
    }))
}

const findSaleOption = (saleId: any) =>
  saleOptions.value.find((item: any) => String(item.id) === String(saleId))

/** 选销售单 → 整块明细跟着换（还能出多少由后端按 销售量 − 名下已登记量 算好，默认就填满） */
const handleSaleChange = async (saleId: any) => {
  addForm.storeName = findSaleOption(saleId)?.storeName
  if (!saleId) {
    addForm.items = []
    return
  }
  itemsLoading.value = true
  try {
    const { data } = await saleItemsErpSaleOutboundApi(saleId)
    addForm.items = toOutboundRows(data, findSaleOption(saleId)?.warehouseId)
    if (!addForm.items.length) {
      ElMessage.warning('这张销售单的商品都已经登记过出库了，没有还能出的货')
    }
  } catch (error) {
    addForm.items = []
  } finally {
    itemsLoading.value = false
  }
}

/**
 * 换出库来源：两路明细的来路完全不同（一路从销售单带、一路自己挑），所以整块清掉重来，
 * 免得把销售单带出来的行当成手工行提交出去（那些行还挂着「还能出」当上限）
 */
const handleSourceChange = () => {
  addForm.orderId = undefined
  addForm.storeName = undefined
  addForm.items = []
  // 这一格的规则跟着来源摘掉了（见 addRules），残留的报错要自己清
  addFormRef.value?.clearValidate('orderId')
}

/**
 * 「导入明细」：按销售单重新捞一遍，只补表格里缺的行（同规格已存在的跳过、已有行一律不动，
 * 否则手填的数量会被冲掉）。捞出来的行和选销售单那一下完全一样（见 toOutboundRows）
 */
const importItems = async () => {
  if (!addForm.orderId) {
    ElMessage.warning('请先选择销售单')
    return
  }
  itemsLoading.value = true
  try {
    const { data } = await saleItemsErpSaleOutboundApi(addForm.orderId)
    const candidates = toOutboundRows(data, findSaleOption(addForm.orderId)?.warehouseId)
    if (candidates.length === 0) {
      ElMessage.warning('这张销售单的商品都已经登记过出库了，没有还能出的货')
      return
    }
    const rows = candidates.filter((item: any) =>
      !addForm.items.some((row: any) => String(row.skuId) === String(item.skuId))
    )
    if (rows.length === 0) {
      ElMessage.info('这张销售单还能出的规格已经全在表格里了，没有要补的行')
      return
    }
    addForm.items.push(...rows)
    ElMessage.success(`已导入 ${rows.length} 行`)
  } catch (error) {
    // 拉取失败就维持现状，别把表格里已经填好的行清掉（报错弹窗由请求拦截器统一给）
  } finally {
    itemsLoading.value = false
  }
}

const removeAddItem = (index: number) => {
  addForm.items.splice(index, 1)
}

/** 手工那三路加行：规格自己挑，数量/仓库/成本价留空由人填（后端判据就三条：规格、数量 > 0、成本价必填） */
const addItem = () => {
  addForm.items.push({
    skuId: undefined,
    productName: undefined,
    productCode: undefined,
    specText: undefined,
    quantity: undefined,
    warehouseId: undefined,
    costPrice: undefined
  })
}

/** 这一行给选择器看的文本：一个商品名下可能有好几条规格，光有商品名分不出挑的是哪一条 */
const skuText = (row: any) => `${row.productName || ''}${row.specText ? ` ${row.specText}` : ''}`.trim()

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
 * 「本次出库数」上限：从销售单带出来的行按后端算好的「还能出」卡（同一张销售单下别的出库单
 * 可能已经登记过一部分量，占完了就填不进去）；手工加的行没有这个数，不限
 */
const addQuantityMax = (row: any) =>
  row.availableQuantity == null ? undefined : Number(row.availableQuantity)

/** 行小计：这一路的成本价是这里能改的，按计划数 × 成本价现算（和采购退货那一弹窗同一口径） */
const addRowAmount = (row: any) =>
  (Number(row.quantity || 0) * Number(row.costPrice || 0)).toFixed(2)

const addTotalQuantity = computed(() =>
  addForm.items.reduce((sum: number, row: any) => sum + Number(row.quantity || 0), 0)
)
const addTotalAmount = computed(() =>
  addForm.items.reduce((sum: number, row: any) => sum + Number(addRowAmount(row)), 0)
)

/** 新建按钮：每次点开重新捞一遍销售单候选（刚审核过的那张要能选到）；来源回到默认的 2 销售出库 */
const handleAdd = async () => {
  Object.assign(addForm, {
    outboundNo: undefined,
    outboundSource: SOURCE_SALE_OUTBOUND,
    orderId: undefined,
    storeName: undefined,
    remark: undefined,
    items: []
  })
  addOpen.value = true
  addFormRef.value?.clearValidate()
  await getSaleOptions()
}

/** 提交前的完整性检查（后端 insert 还会按额度、仓库、成本价各挡一遍，这里只为少一次往返） */
const checkAdd = () => {
  if (!addForm.items.length) {
    ElMessage.warning(isSaleSource.value
      ? '这张销售单没有还能出的规格，换一张单或把「本次出库数」填上'
      : '出库明细不能为空，点上面的「添加规格」逐行加')
    return false
  }
  for (let i = 0; i < addForm.items.length; i++) {
    const row = addForm.items[i]
    const quantity = Number(row.quantity || 0)
    if (quantity === 0) continue
    if (!row.skuId) {
      ElMessage.warning(`第 ${i + 1} 行明细还没挑规格`)
      return false
    }
    // 「还能出」只有从销售单带出来的行才有；手工加的行没有额度这道闸（后端同口径）
    if (row.availableQuantity != null && quantity > Number(row.availableQuantity)) {
      ElMessage.warning(`第 ${i + 1} 行明细本次最多出 ${row.availableQuantity} 件（这张销售单还欠这么多）`)
      return false
    }
    if (!row.warehouseId) {
      ElMessage.warning(`请填写「${skuText(row) || `规格 #${row.skuId}`}」从哪个仓库出库`)
      return false
    }
    if (row.costPrice == null || Number(row.costPrice) < 0) {
      ElMessage.warning(`第 ${i + 1} 行明细要出库，出库成本价不能空着且不能为负`)
      return false
    }
  }
  if (addTotalQuantity.value === 0) {
    ElMessage.warning('本次出库数全是 0，没有要登记的货')
    return false
  }
  return true
}

/**
 * 确认新建：只提交 来源/销售单/每行四列/备注，单号、总数、总金额、状态一律后端算（出 0 的行不提交）。
 * 非销售那三路没有来源单据，orderId 不提交（后端把 order_id/order_no 留 NULL）；
 * productName 跟着带过去只为后端报错时能点名这一行（明细表没这一列，落库时会被忽略）
 */
const submitAdd = async () => {
  addFormRef.value?.validate(async (valid: any) => {
    if (!valid) return
    if (!checkAdd()) return
    addSubmitting.value = true
    try {
      await addErpSaleOutboundApi({
        outboundSource: addForm.outboundSource,
        orderId: isSaleSource.value ? addForm.orderId : undefined,
        remark: addForm.remark,
        items: addForm.items
          .filter((row: any) => Number(row.quantity || 0) > 0)
          .map((row: any) => ({
            skuId: row.skuId,
            productName: row.productName,
            warehouseId: row.warehouseId,
            quantity: Number(row.quantity || 0),
            costPrice: row.costPrice
          }))
      })
      ElMessage.success('出库单已创建，点这一行的「出库」登记实际发多少货，货才真离仓')
      addOpen.value = false
      getList()
    } catch (error) {
    } finally {
      addSubmitting.value = false
    }
  })
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
  queryParams.outboundSource = undefined
  queryParams.status = undefined
  dateRange.value = []
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
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
    case 'outbound':
      handleOutbound(row)
      break
    case 'createDelivery':
      // 「生成配送单」不再建单（配送单是下单那一刻建的），点开看一遍、在弹框里点「确认」才派出去
      openDeliveryDetail(row, true)
      break
    case 'deliveryDetail':
      openDeliveryDetail(row, false)
      break
    case 'delete':
      handleDelete(row)
      break
  }
}

/** 详情按钮操作：每次都重新捞 —— 已出库数可能刚被别的操作员推进过，拿列表那一行的旧值会看漏 */
const handleDetail = async (row: any) => {
  detail.value = {}
  detailOpen.value = true
  detailLoading.value = true
  try {
    const { data } = await detailErpSaleOutboundApi(row.id)
    detail.value = data || {}
  } catch (error) {
  } finally {
    detailLoading.value = false
  }
}

/** 打印这一张出库单：弹窗里已经捞好的那份直接交给打印器 */
const handleDetailPrint = () => {
  printOutbounds([detail.value])
}

/**
 * 出库按钮操作：拉一次详情拿最新明细（outbound_quantity 可能刚被别的操作员推进过），
 * 「本次出库数」默认按上限填满 —— 线上订单的货就是这一单锁的那批，多数情况一次出完
 */
const handleOutbound = async (row: any) => {
  Object.assign(outForm, {
    id: undefined,
    outboundNo: undefined,
    orderNo: undefined,
    outboundSource: undefined,
    buyerName: undefined,
    supplierName: undefined,
    items: []
  })
  outOpen.value = true
  outLoading.value = true
  try {
    const { data } = await detailErpSaleOutboundApi(row.id)
    Object.assign(outForm, {
      id: data.id,
      outboundNo: data.outboundNo,
      orderNo: data.orderNo,
      outboundSource: data.outboundSource,
      buyerName: data.buyerName,
      supplierName: data.supplierName
    })
    // 成本价/金额带出库里已有的那一对值：线上订单的是支付回调按订单实付金额定死的（这一格禁用），
    // 非线上是上一批出库结转的，改的话以这次填的为准
    outForm.items = (data.items || []).map((item: any) => ({
      id: item.id,
      skuId: item.skuId,
      productName: item.productName,
      productCode: item.productCode,
      specText: item.specText,
      warehouseId: item.warehouseId,
      warehouseName: item.warehouseName,
      quantity: Number(item.quantity || 0),
      outboundQuantity: Number(item.outboundQuantity || 0),
      availableStock: item.availableStock == null ? null : Number(item.availableStock),
      thisQuantity: 0,
      costPrice: item.costPrice == null ? undefined : Number(item.costPrice),
      amount: item.amount == null ? null : Number(item.amount)
    }))
    // 默认填满：线上订单就是「把这单锁的货发出去」，非线上按可用量卡着填
    outForm.items.forEach((item: any) => {
      item.thisQuantity = maxOf(item)
    })
  } catch (error) {
    outOpen.value = false
  } finally {
    outLoading.value = false
  }
}

/** 出库弹窗本地的完整性检查（后端 outbound 还会按计划余量和库存各挡一遍） */
const checkOutbound = () => {
  if (!outForm.items.length) {
    ElMessage.warning('这张出库单没有明细，无法出库')
    return false
  }
  let planned = 0
  for (let i = 0; i < outForm.items.length; i++) {
    const row = outForm.items[i]
    const thisTime = Number(row.thisQuantity || 0)
    if (thisTime === 0) continue
    planned += thisTime
    if (thisTime > maxOf(row)) {
      ElMessage.warning(`第 ${i + 1} 行明细本次最多出库 ${maxOf(row)} 件（计划余 ${remainingOf(row)} 件）`)
      return false
    }
    // 线上订单那一格是禁用的、成本价由回调早就写在行上了，这里不能拿它当必填去拦
    if (!isOnlineSource.value && (row.costPrice == null || Number(row.costPrice) < 0)) {
      ElMessage.warning(`第 ${i + 1} 行明细要出库，出库成本价不能空着且不能为负`)
      return false
    }
  }
  if (planned === 0) {
    ElMessage.warning('本次出库数全是 0，没有要出的货')
    return false
  }
  return true
}

/** 确认出库：只把 (明细行 id, 本次出库数, 成本价) 三样提交上去，其余都是服务端说了算 */
const submitOutbound = async () => {
  if (!checkOutbound()) return
  outSubmitting.value = true
  try {
    await outboundErpSaleOutboundApi({
      id: outForm.id,
      items: outForm.items.map((row: any) => ({
        id: row.id,
        thisQuantity: Number(row.thisQuantity || 0),
        costPrice: row.costPrice
      }))
    })
    ElMessage.success('出库成功')
    outOpen.value = false
    getList()
  } catch (error) {
  } finally {
    outSubmitting.value = false
  }
}

/**
 * 弹框里那颗「确认」：把这一单绑着的那条配送单从 0 待派单 推成 1 待接单，它这才算进送货员的抢单池。
 *
 * 这一颗不再建单了：配送单在买家下单那一刻就建好了（重量 = Σ(商品重量 × 数量)、发货点 = 分到的那家仓、
 * 距离和配送费都在那一次定死，买家付的钱里就含着那一笔），支付回调只把它换绑到这张出库单上。
 * 所以点确认之前弹框里那几格都只能看不能改（改了也不算钱）；
 * 出满没有、绑没绑上、还是不是 0 待派单，后端 confirmDelivery 里再兜一道。
 */
const handleDeliveryConfirm = async () => {
  deliveryConfirming.value = true
  try {
    await confirmDeliveryErpSaleOutboundApi(deliveryConfirmRow.value.id)
    ElMessage.success('配送单已确认为「待接单」，送货员现在能在抢单池里看见这一趟')
    deliveryDetailOpen.value = false
    getList()
  } catch (error) {
    // 报错文案由请求拦截器弹（后端那句"没出满/已确认过"就在里面），这里只负责别把 loading 留在身上
  } finally {
    deliveryConfirming.value = false
  }
}

/** 删除按钮操作 */
const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确认删除出库单「${row.outboundNo}」吗？明细会一起删掉，已经出过货的单子删不了。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteErpSaleOutboundApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 张出库单吗？明细会一起删掉。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteErpSaleOutboundApi(selectedIds.value)
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
  // 「新建」弹窗里那一列要用，开页就捞一次（仓不会一变再变，弹窗里再挑不用等）
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

/* 时间范围那一行（照抄商品入库的 .search-row）：wrapper 独占整行把日期格顶到第二行，
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

/* 下拉/单元格里的「主文案 + 右侧灰色副文案」那一排（商品名 + 编码共用） */
.product-option {
  // display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  &__name {
    // overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  /* 一行是一个具体规格，同一商品名的几行全靠这一串分出去 */
  &__spec {
    color: var(--el-color-primary);
    font-size: 12px;
  }

  &__code {
    flex: 0 0 auto;
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}

.product-item {
  :deep(.cell.el-tooltip) {
    white-space: pre-line;
  }
}

/* 出满的那一行数字：绿色加粗，扫列表时不用点开就知道哪张单还欠货 */
.stock-done {
  color: var(--el-color-success);
  font-weight: 600;
}

/* 仓库名后面那枚「盘点中 / 调拨中」：挨着名字，不另起一行 */
.warehouse-frozen {
  margin-left: 6px;
}

/* 该仓可用已经没了（0）：标红，免得对着一个填不进数的格子猜原因 */
.stock-empty {
  color: var(--el-color-danger);
  font-weight: 600;
}

/* 「新建出库单」明细表格上面那一排按钮（照抄商品入库那页） */
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
