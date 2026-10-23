<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="订单号/商品名称/下单人/手机号" clearable
            @keyup.enter="handleQuery" />
        </el-form-item>
        <!-- 消费者账号不看这一格：后端已把 userId 钉成本人，选了也会被覆盖（见 isConsumerAccount） -->
        <el-form-item v-if="!isConsumerAccount" label="消费者" prop="userId">
          <UserSelect v-model="queryParams.userId" :account-type="3" @select="handleQuery" @clear="handleQuery" />
        </el-form-item>
        <el-form-item label="订单状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(ORDER_DICT_TYPE)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <!-- 这两格都是明细行上的条件（shop_order_item 的列），在订单级的列表上意思是"名下有这样一个商品行" -->
        <el-form-item label="发货状态" prop="shipStatus">
          <el-select v-model="queryParams.shipStatus" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(SHIP_DICT_TYPE)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属门店" prop="storeId">
          <el-select v-model="queryParams.storeId" placeholder="全部" clearable>
            <el-option v-for="item in storeOptions" :key="item.id" :label="item.storeName" :value="item.id" />
          </el-select>
        </el-form-item>

        <!-- 时间范围 + 按钮同一行（照抄购物车、收藏的 .search-row）：这一层 wrapper 独占第二行，
             里面两格都不等分、按控件本身的宽度从左排（样式见文件末尾） -->
        <div class="search-row">
          <el-form-item label="下单时间" prop="dateRange">
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
      <!-- 数据表格：一行是一张订单（sys_order），这单买了哪几样商品在详情弹窗里看。
           这一页对订单本身没有新增/修改/删除（下单由小程序侧的统一下单接口写），只有详情；
           写动作都在详情弹窗底部：「确认收货」推主单状态，「退货」提一张待审核的退款单 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="订单号" prop="orderNo" align="center" min-width="190" />
        <el-table-column label="下单人" align="center" min-width="130">
          <template #default="{ row }">
            <span>{{ row.userName || `已删除用户 #${row.userId}` }}</span>
          </template>
        </el-table-column>
        <el-table-column label="手机号" prop="mobile" align="center" min-width="120">
          <template #default="{ row }">
            <span>{{ row.mobile || '-' }}</span>
          </template>
        </el-table-column>
        <!-- 行数/件数是查询时按 order_id 现汇总明细来的（主表上没有这几列） -->
        <el-table-column label="商品" align="center" width="130">
          <template #default="{ row }">
            <span>共 {{ row.itemCount || 0 }} 行 · {{ row.totalQuantity || 0 }} 件</span>
          </template>
        </el-table-column>
        <!-- <el-table-column label="所属门店" align="center" min-width="140">
          <template #default="{ row }">
            <span>{{ row.storeNames || '-' }}</span>
          </template>
        </el-table-column> -->
        <el-table-column label="订单状态" align="center" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(ORDER_DICT_TYPE, row.status)">
              {{ dict.label(ORDER_DICT_TYPE, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="订单金额" align="right" width="110">
          <template #default="{ row }">
            <span>{{ priceText(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="实付金额" align="right" width="110">
          <template #default="{ row }">
            <span class="pay-amount">{{ priceText(row.payAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="支付时间" align="center" width="160">
          <template #default="{ row }">
            <span>{{ row.payTime ? validate.formatTime(row.payTime, 'YYYY-MM-DD HH:mm') : '未支付' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" align="center" width="160">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.createTime, 'YYYY-MM-DD HH:mm') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="240" fixed="right">
          <template #default="scope">
            <el-button v-permission="['shop:orderItem:detail']" type="primary" link icon="View"
              @click="handleDetail(scope.row)">详情</el-button>
            <!-- 这一单名下的配送单是**下单那一刻**就跟着建出来的（不用等支付、也不用等出库）：
                 配送单表上没有单独的订单列，那一槽在换绑之前装的就是这一单的 id 和订单号，
                 所以列表 SQL 两道都认（还没换绑的按订单配、已经换绑的拐一道出库单配），带回 deliveryId 就给看，
                 还没付钱、还没出库也一样看得见自己那一趟。权限沿用这一页自己的 shop:orderItem:detail：
                 消费者/门店账号手里没有 shop:delivery:detail，按那个判等于永远不给看 -->
            <el-button v-if="scope.row.deliveryId" v-permission="['shop:orderItem:detail']" type="primary"
              link icon="Document" @click="handleDeliveryDetail(scope.row)">配送单</el-button>
            <!-- 这一颗数的是**全部**退款单（不分状态）：被拒绝的、退款失败的也要点得开，
                 所以它和「退货」那格的判据不是一回事（那格看的是明细行上还有没有能退的件数）。
                 两格都是列表 SQL 现汇总 sys_refund 带回来的 -->
            <el-button v-if="scope.row.refundCount > 0" v-permission="['shop:orderItem:detail']" type="primary"
              link icon="RefreshLeft" @click="handleRefundList(scope.row)">退款单</el-button>
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
        详情弹窗：上面一块是订单主表（sys_order）的信息，下面一张表是这单的商品明细行（shop_order_item）。
        字段全部来自详情接口（一次把主单和名下明细都带回来），列表那行没带金额明细和支付信息。
        底部那颗「确认收货」把主单推成已完成，「退货」提一张待审核的退款单（判据分别见 canReceive / canRefund）。
      -->
      <el-dialog v-model="detailOpen" title="订单详情" width="1080px" append-to-body destroy-on-close
        v-loading="detailLoading">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ detail.orderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="下单人">{{ detail.userName || `已删除用户 #${detail.userId}` }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ detail.mobile || '-' }}</el-descriptions-item>
          <el-descriptions-item label="订单类型">{{ detail.orderType == null ? '-' :
            dict.label('order_type', detail.orderType) }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <el-tag size="small" :type="dict.tag(ORDER_DICT_TYPE, detail.status)">
              {{ dict.label(ORDER_DICT_TYPE, detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ validate.formatTime(detail.createTime) }}</el-descriptions-item>
          <!-- 订单金额里含着下面那一格配送费（下单时按当时的配送费规则算好、跟货款一起收走的），
               所以它是"Σ 明细行小计 + 配送费"，比明细那一表的合计大一格 —— 那一格才是货值 -->
          <el-descriptions-item label="订单金额">{{ priceText(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="配送费">{{ priceText(detail.deliveryFee) }}</el-descriptions-item>
          <el-descriptions-item label="优惠金额">{{ priceText(detail.discountAmount) }}</el-descriptions-item>
          <el-descriptions-item label="实付金额">{{ priceText(detail.payAmount) }}</el-descriptions-item>
          <el-descriptions-item label="支付方式">{{ detail.payType == null ? '-' :
            dict.label('pay_type', detail.payType) }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">
            {{ detail.payTime ? validate.formatTime(detail.payTime) : '未支付' }}
          </el-descriptions-item>
          <el-descriptions-item label="商品行数">{{ detail.itemCount || 0 }} 行 · {{ detail.totalQuantity || 0 }} 件
          </el-descriptions-item>
          <el-descriptions-item label="买家留言" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 商品明细：一行一个商品。名称/主图/单价都是下单时的快照，商品以后改名改价不回刷已下的单 -->
        <div class="item-title">商品明细</div>
        <el-table :data="detail.items || []" size="small" border>
          <!-- 表级的 show-overflow-tooltip 会被每一列继承，纯图片列要显式关掉，否则悬停弹一个空 tooltip -->
          <el-table-column label="商品图" align="center" width="80" :show-overflow-tooltip="false">
            <template #default="{ row }">
              <el-image v-if="row.image" :src="row.image" :preview-src-list="[row.image]" preview-teleported
                fit="cover" style="width: 46px; height: 46px; border-radius: 4px" />
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="商品名称" align="center" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ row.productName || '商品已删除' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="商品编码" align="center" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ row.productCode || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="发货状态" align="center" width="95">
            <template #default="{ row }">
              <el-tag size="small" :type="dict.tag(SHIP_DICT_TYPE, row.shipStatus)">
                {{ dict.label(SHIP_DICT_TYPE, row.shipStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="规格" align="center" min-width="110">
            <template #default="{ row }">
              <span>{{ row.specText || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单价" align="right" width="95">
            <template #default="{ row }">{{ priceText(row.price) }}</template>
          </el-table-column>
          <el-table-column label="数量" prop="quantity" align="center" width="70" />
          <!-- 已退件数（钱已经退出去的那一份）；退货弹窗上"还能退几件"要拿数量减它、再减在途占的件数 -->
          <el-table-column label="已退" align="center" width="70">
            <template #default="{ row }">{{ row.returnedQuantity || 0 }}</template>
          </el-table-column>
          <el-table-column label="小计" align="right" width="100">
            <template #default="{ row }">{{ priceText(row.totalAmount) }}</template>
          </el-table-column>
          <el-table-column label="优惠" align="right" width="100">
            <template #default="{ row }">{{ priceText(row.discountAmount) }}</template>
          </el-table-column>
          <!-- 这一行花掉的积分：和左边的优惠金额一样由服务端算，不是买家填的。
               积分抵扣那一路还没接上之前这一格全是空（0 显示成 '-'，别在一片金额里插一排 0） -->
          <el-table-column label="使用积分" align="center" width="90">
            <template #default="{ row }">{{ row.usedPoints || '-' }}</template>
          </el-table-column>
          <!-- 这一行送出去的积分：支付回调送分时按当时商品的档位 × 件数落库的快照，
               退货要收回当初送的分就读这一格，所以它和左边的使用积分一样不是买家填的 -->
          <el-table-column label="赠送积分" align="center" width="90">
            <template #default="{ row }">{{ row.gainPoints || '-' }}</template>
          </el-table-column>
          
          <el-table-column label="所属门店" align="center" min-width="120">
            <template #default="{ row }">{{ row.storeName || '-' }}</template>
          </el-table-column>
          <!-- 发货仓库是下单时按收货地址坐标就近分的（一个商品一行一个仓），所以这一列在行上不在单上 -->
          <!-- <el-table-column label="发货仓库" align="center" min-width="120">
            <template #default="{ row }">
              {{ row.warehouseName || (row.warehouseId ? `已删除仓库 #${row.warehouseId}` : '未分仓') }}
            </template>
          </el-table-column> -->
        </el-table>
        <template #footer>
          <div class="dialog-footer">
            <!-- 两颗按钮都只在"这一单名下每一行的货都已有结论（已发货 / 已退货）"时出现（判据见 canReceive）。
                 确认收货打的是 comm 那一路（/api/comm/** 不过 Sa-Token 拦截器，端点自己没鉴权），
                 越权由服务层按登录态收窄；权限沿用这一页自己的 shop:orderItem:detail（同列表上那颗「配送单」），
                 消费者/门店账号手里没有写权限，按写权限判等于永远不给看。催单先占位，接口后续实现 -->
            <el-button v-if="canReceive" v-permission="['shop:orderItem:detail']" type="primary" :loading="receiving"
              @click="handleReceive">确认收货</el-button>
            <!-- 退货和收货不看同一格：这一单只要付过钱（已支付 / 已完成）、名下还有能退的件数就能提。
                 在途退款单不拦这颗按钮（一张单可以同时挂好几张，各退各的行），额度是按行占的；判据见 canRefund -->
            <el-button v-if="canRefund" v-permission="['shop:orderItem:detail']" type="warning" plain
              @click="handleRefund">退货</el-button>
            <el-button v-if="canReceive" v-permission="['shop:orderItem:detail']" @click="handleUrge">催单</el-button>
            <el-button @click="detailOpen = false">关 闭</el-button>
          </div>
        </template>
      </el-dialog>

      <!--
        退货申请：形状照抄「采购单」的「采购退货」弹窗 —— 不给加行、删行（商品行就是这一单名下的明细），
        只有「本次退货件数」和「退货原因」两格能填。这里只是**登记申请**：提交后落一张 0 待审核 的退款单
        （后台「退款管理」审核、退款成功回调才回写明细的已退件数和发货状态），
        订单状态、库存、配送单在这一刻一分不动。金额不在这里填：每行退多少由后端按
        「小计 − 该行优惠」算（整行退完退小减优惠，只退几件按件数等比例摊），前端不碰钱。
        这次不退的商品把件数留 0 即可，剩下的量下次还能再提 —— 一张订单可以同时挂着好几张在途退款单
        （每次退的商品、件数都可以不一样），所以「还能退」要减已退件数、也要减别的单先占走的件数（那一格叫「在途」）。
      -->
      <el-dialog v-model="refundOpen" title="退货申请" width="920px" append-to-body destroy-on-close>
        <el-form label-width="100px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="订单号">
                <el-input :model-value="detail.orderNo" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="下单人">
                <el-input :model-value="detail.userName" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="退货原因">
                <el-input v-model="refundForm.refundReason" type="textarea" :rows="2" placeholder="选填，会记在退款单上"
                  maxlength="500" show-word-limit />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">退货明细</el-divider>
          <el-table :data="refundForm.items" size="small" border show-overflow-tooltip>
            <el-table-column label="商品名称" prop="productName" align="center" min-width="170" />
            <el-table-column label="商品编码" prop="productCode" align="center" min-width="140" />
            <el-table-column label="规格" align="center" min-width="110">
              <template #default="{ row }">{{ row.specText || '-' }}</template>
            </el-table-column>
            <el-table-column label="购买" align="center" width="80">
              <template #default="{ row }">{{ row.buyQuantity }}</template>
            </el-table-column>
            <el-table-column label="已退" align="center" width="70">
              <template #default="{ row }">{{ row.returnedQuantity }}</template>
            </el-table-column>
            <!-- 在途 = 别的还没处理完的退款单（待审核 / 等退款 / 退款中）先把这几件占走了；
                 那一张被拒、失败或取消就还回来，所以这一格不是"已经退了"，是"这会儿不能再提" -->
            <el-table-column label="在途" align="center" width="70">
              <template #default="{ row }">{{ row.refundingQuantity }}</template>
            </el-table-column>
            <el-table-column label="还能退" align="center" width="90">
              <template #default="{ row }">{{ row.returnableQuantity }}</template>
            </el-table-column>
            <el-table-column label="本次退货件数" align="center" width="130">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="0" :max="Number(row.returnableQuantity || 0)"
                  :controls="false" style="width: 100%" />
              </template>
            </el-table-column>
          </el-table>
          <div class="items-footer">
            <span class="items-tip">这次不退的商品把「本次退货件数」留 0 即可；剩下的量下次还能再提
              <span v-if="Number(detail.pendingRefundCount || 0) > 0">（这一单已有 {{ detail.pendingRefundCount }}
                张在途退款单没处理完，它们占掉的件数已经从上面的「还能退」里减掉了）</span>
            </span>
            <span class="items-total">本次退货件数 {{ refundTotalQuantity }}</span>
          </div>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="refundOpen = false">取 消</el-button>
            <el-button type="primary" :loading="refundSubmitting" @click="submitRefund">提交申请</el-button>
          </div>
        </template>
      </el-dialog>

      <!--
        这一单名下的退款单：形状照抄「采购单」的「关联的入库单」弹窗 —— 外层一行一张退款单，
        点行首箭头摊开看它退了哪几行商品（名称/编码/规格/主图是从订单明细现联的，
        件数和金额读建单那一刻写进 sys_refund.extra 的那份申请快照）。
        只看不动：审核去「退款管理」那页，这里不给改。
        隔色只打在外层那一张表上（样式见文件末尾 .receipt-table 那几段，和采购单那页同一套）。
      -->
      <el-dialog v-model="refundListOpen" title="订单退款单" width="1040px" append-to-body destroy-on-close>
        <div class="receipt-head">
          <span>订单号 {{ refundListOrder.orderNo || '-' }}</span>
          <span>下单人 {{ refundListOrder.userName || '-' }}</span>
          <span>共 {{ refundList.length }} 张退款单</span>
        </div>
        <el-table class="receipt-table" :data="refundList" size="small" border stripe row-key="id"
          v-loading="refundListLoading">
          <el-table-column type="expand">
            <template #default="{ row, $index }">
              <div class="receipt-items-wrap" :class="{ 'is-striped': $index % 2 === 1 }">
                <div class="refund-reason">退货原因：{{ row.refundReason || '（未填写）' }}</div>
                <el-table class="receipt-items" size="small" border :data="row.items || []"
                  empty-text="这张退款单没有登记退货商品行（不是统一退货接口建的，多半是后台手工录的）">
                  <!-- 这一张内层表没开表级 tooltip，所以图片列不用像详情弹窗那张一样显式关掉 -->
                  <el-table-column label="商品图" align="center" width="70">
                    <template #default="{ row: item }">
                      <el-image v-if="item.image" :src="item.image" :preview-src-list="[item.image]"
                        preview-teleported fit="cover" style="width: 42px; height: 42px; border-radius: 4px" />
                      <span v-else>-</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="商品名称" align="center" min-width="170" show-overflow-tooltip>
                    <template #default="{ row: item }">
                      <span>{{ item.productName || (item.orderItemId ? `明细行 #${item.orderItemId} 已不在这一单里` : '-') }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="商品编码" align="center" min-width="140" show-overflow-tooltip>
                    <template #default="{ row: item }">{{ item.productCode || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="规格" align="center" min-width="110">
                    <template #default="{ row: item }">{{ item.specText || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="退货件数" align="center" width="90">
                    <template #default="{ row: item }">{{ item.quantity || 0 }}</template>
                  </el-table-column>
                  <!-- 这一格是建单时按「该行小计 − 该行优惠」再按件数摊出来的，不是这里现乘的 -->
                  <el-table-column label="退款金额" align="right" width="105">
                    <template #default="{ row: item }">{{ priceText(item.amount) }}</template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="退款单号" prop="refundNo" align="center" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" align="center" width="95">
            <template #default="{ row }">
              <el-tag size="small" :type="dict.tag(REFUND_DICT_TYPE, row.status)">
                {{ dict.label(REFUND_DICT_TYPE, row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="类型" align="center" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="Number(row.refundType) === 2 ? 'warning' : 'primary'">
                {{ labelOf(REFUND_TYPE_OPTIONS, row.refundType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="申请金额" align="right" width="105">
            <template #default="{ row }">{{ priceText(row.refundAmount) }}</template>
          </el-table-column>
          <el-table-column label="实退金额" align="right" width="105">
            <template #default="{ row }">
              <span class="pay-amount">{{ priceText(row.actualRefundAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="退货" align="center" width="110">
            <template #default="{ row }">
              <span>{{ row.itemCount || 0 }} 行 · {{ row.returnQuantity || 0 }} 件</span>
            </template>
          </el-table-column>
          <el-table-column label="申请时间" align="center" width="150">
            <template #default="{ row }">{{ validate.formatTime(row.applyTime, 'YYYY-MM-DD HH:mm') || '-' }}</template>
          </el-table-column>
          <el-table-column label="审核人" prop="auditorName" align="center" min-width="100" show-overflow-tooltip />
          <!-- 审核备注和失败原因在同一格：拒绝时那一句就是拒绝原因，退款失败时那一句是渠道给的，
               两者不会同时有值（前端不用猜哪一档该看哪一格） -->
          <el-table-column label="说明" align="center" min-width="160">
            <template #default="{ row }">{{ row.auditRemark || row.failReason || '-' }}</template>
          </el-table-column>
        </el-table>
        <!-- 返券那一拍只在整单退满时走（判据见后端 ShopRefundCallbackServiceImpl#giveBackCoupons），
             所以这一句跟着主单状态说、不逐张退款单说；具体几张没存（券退回时 order_id 就抹掉了，事后联不回来） -->
        <div v-if="Number(refundListOrder.status) === ORDER_STATUS_REFUNDED" class="items-footer">
          <span class="items-tip">这一单已整单退满，下单时用掉的优惠券已随最后一笔退款自动退回买家账户（只退一部分商品时不返券）</span>
        </div>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="refundListOpen = false">关 闭</el-button>
          </div>
        </template>
      </el-dialog>

      <!-- 这一单那条配送单的详情：只读，内容交给公共组件按 id 自己查（和「商品出库」「配送管理」看的是同一个弹窗） -->
      <delivery-detail v-model:visible="deliveryDetailOpen" :id="deliveryDetailId" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { listShopOrderApi, detailShopOrderApi, receiveShopOrderApi, createShopRefundApi, listShopOrderRefundApi } from '@/api/consumer/orderItem'
import { optionsShopStoreApi } from '@/api/store/store'
import UserSelect from '@/components/UserSelect/index.vue'
import DeliveryDetail from '@/components/DeliveryDetail/index.vue'
import validate from '@/utils/validate'
import { useDict } from '@/utils/dictCache'
import { useUserStore } from '@/store/modules/user'

/** 整张订单的状态（sys_order.status）：主表没有"已出库/已发货"，出到哪一步看下面那本明细行的状态 */
const ORDER_DICT_TYPE = 'order_status'
/** 明细行的发货状态（shop_order_item.ship_status）：0待发货 3已出库 1已发货 4部分退货 2已退货 */
const SHIP_DICT_TYPE = 'order_ship_status'
/** 退款单的状态（sys_refund.status）：0待审核 2审核拒绝 3退款中（审核通过就落这一档） 4退款成功 5退款失败 6已取消 */
const REFUND_DICT_TYPE = 'refund_status'
const dict = useDict(ORDER_DICT_TYPE, SHIP_DICT_TYPE, REFUND_DICT_TYPE, 'order_type', 'pay_type')

/**
 * 退款类型（sys_refund.refund_type）：1 全额 / 2 部分。没建字典，口径照抄「退款管理」那页
 * （那一页也是这么写死的，两处一起改才对得上）
 */
const REFUND_TYPE_OPTIONS = [
  { value: 1, label: '全额退款' },
  { value: 2, label: '部分退款' }
]

const labelOf = (options: any[], value: any) =>
  options.find(item => item.value === Number(value))?.label || '-'

const userStore = useUserStore()
/**
 * 当前登录的是否消费者账号（字典 account_type 的 3）。只用来决定显不显消费者筛选那一格，
 * 真正的数据范围在后端：ShopOrderItemServiceImpl 会把 userId 覆盖成登录 id。
 */
const isConsumerAccount = computed(() => userStore.user.accountType === 3)

const loading = ref(true)
const total = ref(0)
const dataList = ref<any[]>([])

// 详情弹窗：一次把主单和名下明细都带回来，items 是下面那张表的数据。
// receiving 是「确认收货」那颗按钮的处理中状态
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<any>({})
const receiving = ref(false)

// 配送单详情：交给公共组件，这里只管开合和传那一单的 deliveryId（列表 SQL 两道配出来的，见上面那颗按钮的注释）
const deliveryDetailOpen = ref(false)
const deliveryDetailId = ref<number | string>()

const queryFormRef = ref<FormInstance>()

// 查询参数
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  userId: undefined,
  status: undefined,
  shipStatus: undefined,
  storeId: undefined
})
const dateRange = ref<string[]>([])

// 门店下拉选项（只取启用中的）
const storeOptions = ref<any[]>([])

/** 金额的显示口径，列表和详情用同一个，避免出现两种小数位 */
const priceText = (amount: any) => (amount == null || amount === '' ? '-' : `￥${Number(amount).toFixed(2)}`)

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const params: any = { ...queryParams }
    if (dateRange.value && dateRange.value.length === 2) {
      params.beginTime = `${dateRange.value[0]} 00:00:00`
      params.endTime = `${dateRange.value[1]} 23:59:59`
    }
    const { data } = await listShopOrderApi(params)
    dataList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
  } finally {
    loading.value = false
  }
}

/** 门店下拉选项 */
const getStoreOptions = async () => {
  try {
    const { data } = await optionsShopStoreApi()
    storeOptions.value = data || []
  } catch (error) {
  }
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
  queryParams.userId = undefined
  queryParams.status = undefined
  queryParams.shipStatus = undefined
  queryParams.storeId = undefined
  dateRange.value = []
  handleQuery()
}

/** 详情：每次点开重新捞 —— 明细行的发货状态会被出库/配送推进，拿列表那行的旧值会看漏 */
const handleDetail = async (row: any) => {
  detail.value = {}
  detailOpen.value = true
  detailLoading.value = true
  try {
    const { data } = await detailShopOrderApi(row.id)
    detail.value = data || {}
  } catch (error) {
  } finally {
    detailLoading.value = false
  }
}

/**
 * 明细行上"货已有结论"的三档（shop_order_item.ship_status）：1 已发货（统一接单写的）、
 * 4 部分退货、2 已退货。退货那两档算放行：退掉的行不会再补发货，卡着整单就永远收不了尾
 * （整单退满的那一张早被退款回调推成 4 已退款，前面 status===已支付 那一格先拦下，不会误标完成）。
 */
const SHIP_STATUS_ARRIVED = [1, 4, 2]
/** 订单的"已支付"（sys_order.status 的 1）：只有这一档能被确认收货，收了推成 2 已完成 */
const ORDER_STATUS_PAID = 1
/** 已完成（sys_order.status 的 2）：这一档也能提退货 —— 收过货才发现要退，退货不受确认收货影响 */
const ORDER_STATUS_FINISHED = 2
/** 已退款（sys_order.status 的 4）：只有整单每一行都退满才会到这一档，退款回调就在这一步把用掉的优惠券退回买家 */
const ORDER_STATUS_REFUNDED = 4

/**
 * 底部两颗按钮的判据：这一单已经支付、名下有商品行、且每一行的货都已有结论（已发货或已退货）。
 * 只看详情接口现捞的 items（列表那行不带明细）。后端 ShopOrderReceiveServiceImpl 会按同一口径再判一次，
 * 这里只是决定按钮显不显，拦不住绕过页面直接打接口的情况。
 */
const canReceive = computed(() => {
  const items = detail.value.items || []
  return detail.value.status === ORDER_STATUS_PAID
    && items.length > 0
    && items.every((item: any) => SHIP_STATUS_ARRIVED.includes(item.shipStatus))
})

/** 确认收货：主表推成 2 已完成，明细行、库存、配送单都不动（口径见后端注释） */
const handleReceive = async () => {
  try {
    await ElMessageBox.confirm('确认已经收到这一单的全部商品？确认后订单记为「已完成」。', '确认收货',
      { confirmButtonText: '确认收货', cancelButtonText: '取消', type: 'warning' })
  } catch (error) {
    return
  }
  receiving.value = true
  try {
    await receiveShopOrderApi(detail.value.id)
    ElMessage.success('已确认收货')
    // 详情和列表都重捞：主表状态从 1 变成 2，弹窗上那一格和列表筛选都得跟着变
    await Promise.all([handleDetail(detail.value), getList()])
  } catch (error) {
  } finally {
    receiving.value = false
  }
}

/** 催单：先占位，接口后续实现（和确认收货同一判据，都在全部已发货时才出现） */
const handleUrge = () => {
  ElMessage.info('催单功能待接入')
}

/**
 * 退货弹窗：items 是从详情那本明细里筛出来的"还能退"的行
 * （购买数量 − 已退件数 − 在途退款单已占掉的件数 > 0），
 * 每行的 quantity 是这一格填的本次退货件数（初始 0），原始购买数量挪到 buyQuantity 只读回显。
 * 整本弹窗只是登记，落库的是一张 0 待审核 的退款单，金额由后端算，所以这里不碰钱。
 */
const refundOpen = ref(false)
const refundSubmitting = ref(false)
const refundForm = reactive<any>({ refundReason: '', items: [] })

/**
 * 还能退的行（详情接口现捞的 items）；一行都没剩就组不出弹窗数据。
 * refundingQuantity 是详情接口按名下那些在途退款单的 extra 现算的（0 待审核 / 3 退款中），
 * 减它是为了弹窗给的 max 和后端肯收的数一致 —— 一张单可以同时挂好几张退款单，各退各的行、各占各的件数
 */
const refundableItems = computed(() => {
  const items = detail.value.items || []
  return items
    .map((item: any) => ({
      orderItemId: item.id,
      productName: item.productName,
      productCode: item.productCode,
      specText: item.specText,
      buyQuantity: Number(item.quantity || 0),
      returnedQuantity: Number(item.returnedQuantity || 0),
      refundingQuantity: Number(item.refundingQuantity || 0),
      returnableQuantity: Number(item.quantity || 0) - Number(item.returnedQuantity || 0)
        - Number(item.refundingQuantity || 0),
      quantity: 0
    }))
    .filter((item: any) => item.returnableQuantity > 0)
})

/**
 * 退货按钮的判据（和后端 ShopRefundCreateServiceImpl 那几道复核同口径，这里只决定按钮显不显）：
 * 这一单付过钱（1 已支付 / 2 已完成）+ 名下还有能退的件数。
 * 2026-09-22 起不再看 pendingRefundCount：同一单可以有好几张在途退款单（每次退的商品和件数都可以不一样），
 * 额度是按行占的，全占满了 refundableItems 自己就空了、这颗按钮自己收掉
 */
const canRefund = computed(() => {
  const status = detail.value.status
  return (status === ORDER_STATUS_PAID || status === ORDER_STATUS_FINISHED)
    && refundableItems.value.length > 0
})

const refundTotalQuantity = computed(() =>
  refundForm.items.reduce((sum: number, item: any) => sum + Number(item.quantity || 0), 0))

/** 打开退货弹窗：每次重新按详情组一遍，免得把上一单填的件数带过来 */
const handleRefund = () => {
  refundForm.refundReason = ''
  refundForm.items = refundableItems.value.map((item: any) => ({ ...item }))
  refundOpen.value = true
}

/**
 * 提交退货申请：只传「哪一行、退几件」和退货原因（金额、订单号、地址都由后端按 orderId 现查现算，
 * addressId 不传 → 回落到这一单当初的收货地址）。成功后详情和列表都重捞：
 * 刚提交的那几件会出现在明细行的 refundingQuantity 上，"还能退"跟着变小；把最后一件也占掉了，这颗按钮才收起来
 */
const submitRefund = async () => {
  const items = refundForm.items.filter((item: any) => Number(item.quantity) > 0)
    .map((item: any) => ({ orderItemId: item.orderItemId, quantity: Number(item.quantity) }))
  if (items.length === 0) {
    ElMessage.warning('请把要退的商品的「本次退货件数」填上')
    return
  }
  refundSubmitting.value = true
  try {
    const { data } = await createShopRefundApi({
      orderId: detail.value.id,
      refundReason: refundForm.refundReason || undefined,
      items
    })
    ElMessage.success(`退货申请已提交（退款单号 ${data?.refundNo || ''}），等后台审核`)
    refundOpen.value = false
    await Promise.all([handleDetail(detail.value), getList()])
  } catch (error) {
  } finally {
    refundSubmitting.value = false
  }
}

/** 配送单：这一单建过配送才有这颗按钮，弹窗自己按 id 查那一条的当前状态 */
const handleDeliveryDetail = (row: any) => {
  deliveryDetailId.value = row.deliveryId
  deliveryDetailOpen.value = true
}

/**
 * 退款单弹窗：一行一张退款单，展开是它退了哪几行商品。
 * 每次点开重新捞 —— 刚提的申请、刚审核完的那张都要立刻看得见（refundCount 只是列表那行上的判据）。
 */
const refundListOpen = ref(false)
const refundListLoading = ref(false)
const refundList = ref<any[]>([])
const refundListOrder = ref<any>({})

const handleRefundList = async (row: any) => {
  // 抬头那排用列表这一行就带的字段（orderNo/userName），不为此多联一次主表
  refundListOrder.value = row
  refundList.value = []
  refundListOpen.value = true
  refundListLoading.value = true
  try {
    const { data } = await listShopOrderRefundApi(row.id)
    refundList.value = data || []
  } catch (error) {
  } finally {
    refundListLoading.value = false
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

onMounted(() => {
  getList()
  getStoreOptions()
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

/* 时间范围那一行（照抄购物车、收藏的 .search-row）：wrapper 独占整行把日期格顶到第二行，
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
     所以 width 之外再压一道 max-width + flex-grow:0。360px 够放"开始日期 至 结束日期"，要调宽窄改同一条规则里的两个 360 */
  .el-date-editor {
    width: 360px !important;
    max-width: 360px !important;
    flex-grow: 0 !important;
  }

  /* 按钮紧贴日期框；想把它顶到行尾，给这格加 margin-left: auto */
  .el-form-item.search-actions {
    margin-right: 0;
  }
}

/* 实付金额是这单真正收的钱，比订单金额（原价）值得多看一眼 */
.pay-amount {
  font-weight: 600;
  color: var(--el-color-danger);
}

/* 详情弹窗里主单和商品表格之间的那个小标题 */
.item-title {
  margin: 16px 0 8px;
  font-weight: 600;
}

/* 退货弹窗表格下面那一排：左边提示、右边本次合计（照抄采购退货那两处） */
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

/* 「退款单」弹窗：抬头那排说明文字（照抄采购单那页的 .receipt-head） */
.receipt-head {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

/*
 * 隔色只有外层那一张表斑马（同上，抄采购单那页）：EP 的条纹是 --el-fill-color-lighter，
 * 默认那点在带边框的小表格里等于没色，所以换成蓝，hover 再压一档。
 * 展开的商品明细不再自己区分：内层表三个底色变量都清成透明，色由 .receipt-items-wrap 那层按
 * 所属那一行的下标补，才会跟着那一行走（涂在单元格上 hover 会闪）
 */
.receipt-table {
  --el-fill-color-lighter: var(--el-color-primary-light-8);
  --el-table-row-hover-bg-color: var(--el-color-primary-light-7);
  --el-table-expanded-cell-bg-color: transparent;

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

  &.is-striped {
    background: var(--el-fill-color-lighter);
  }
}

/* 展开区里那一行退货原因：内层那张表只放商品行，原因得单独说 */
.refund-reason {
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
