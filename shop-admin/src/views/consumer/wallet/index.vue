<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="昵称/账号/手机号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="账户状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable @change="handleQuery">
            <el-option v-for="item in dict.options(DICT_STATUS)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item class="search-actions">
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>

        <!-- 账户是按用户唯一的一行，一次钱包变动才建一行，所以这一页没有新增/修改/删除按钮：
             账上那几个数只能由下单冻结、支付结转、退款退回那几笔动作改，
             后台手工建账或填余额会让账和流水对不上（同积分账户、商品库存那两页） -->
      </el-form>
    </div>

    <el-card class="box-card">
      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="用户" align="center" min-width="140">
          <template #default="{ row }">{{ row.userName || `已删除用户 #${row.userId}` }}</template>
        </el-table-column>
        <el-table-column label="手机号" align="center" width="130">
          <template #default="{ row }">{{ row.mobile || '-' }}</template>
        </el-table-column>
        <!-- 可用那一格是这一页的主数：加粗、多的排在前面（服务端 ORDER BY status DESC, balance DESC） -->
        <el-table-column label="可用余额" align="right" width="120">
          <template #default="{ row }">
            <span class="balance">{{ validate.formatAmount(row.balance) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="冻结中" align="right" width="110">
          <template #default="{ row }">
            <span :class="{ 'amount-warning': Number(row.frozenBalance) > 0 }">
              {{ validate.formatAmount(row.frozenBalance) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="累计充值" align="right" width="110">
          <template #default="{ row }">{{ validate.formatAmount(row.totalRecharge) }}</template>
        </el-table-column>
        <el-table-column label="累计消费" align="right" width="110">
          <template #default="{ row }">{{ validate.formatAmount(row.totalConsume) }}</template>
        </el-table-column>
        <el-table-column label="累计提现" align="right" width="110">
          <template #default="{ row }">{{ validate.formatAmount(row.totalWithdraw) }}</template>
        </el-table-column>
        <el-table-column label="赠送余额" align="right" width="110">
          <template #default="{ row }">{{ validate.formatAmount(row.giftBalance) }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_STATUS, row.status)">
              {{ dict.label(DICT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="建账时间" align="center" width="170">
          <template #default="{ row }">{{ validate.formatTime(row.createTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="170" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['shop:wallet:detail']" type="primary" link icon="View"
              @click="handleDetail(row)">详情</el-button>
            <!-- 流水：这个人钱包的进出明细，只能由钱包动作写出来，所以这一格只有查 -->
            <el-button v-permission="['shop:wallet:record']" type="primary" link icon="Document"
              @click="handleRecord(row)">流水</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页工具栏 -->
      <div class="pagination-container">
        <el-pagination background v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 30, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
    </el-card>

    <!-- 钱包账户详情：只读。那几个数读的就是列表这一行（服务端从库里那一格带来，不做减法），
         所以这里不再捞一次接口 —— 这一页没有写入口，弹窗期间不会有第二版 -->
    <el-dialog v-model="detailOpen" :title="`钱包账户详情 - ${detailUserText}`" width="760px" append-to-body
      destroy-on-close>
      <el-descriptions title="用户" :column="2" border>
        <el-descriptions-item label="昵称">{{ detail.userName || `已删除用户 #${detail.userId}` }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.mobile || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ detail.userId }}</el-descriptions-item>
        <el-descriptions-item label="账户状态">
          <el-tag size="small" :type="dict.tag(DICT_STATUS, detail.status)">
            {{ dict.label(DICT_STATUS, detail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="建账时间">
          {{ validate.formatTime(detail.createTime) || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="最后改动时间">
          {{ validate.formatTime(detail.updateTime) || '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <el-descriptions title="钱包" :column="3" border class="detail-block">
        <el-descriptions-item label="可用余额">
          <span class="balance">{{ validate.formatAmount(detail.balance) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="冻结中">
          <span :class="{ 'amount-warning': Number(detail.frozenBalance) > 0 }">
            {{ validate.formatAmount(detail.frozenBalance) }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="赠送余额">{{ validate.formatAmount(detail.giftBalance) }}</el-descriptions-item>
        <el-descriptions-item label="累计充值">{{ validate.formatAmount(detail.totalRecharge) }}</el-descriptions-item>
        <el-descriptions-item label="累计消费">{{ validate.formatAmount(detail.totalConsume) }}</el-descriptions-item>
        <el-descriptions-item label="累计提现">{{ validate.formatAmount(detail.totalWithdraw) }}</el-descriptions-item>
      </el-descriptions>
      <!-- 恒等式说在这一页：账上的数对不上这条式子就说明有笔变动只写了账没写流水 -->
      <div class="detail-tip">
        可用 + 冻结中 + 累计消费 + 累计提现 = 累计充值 + 赠送余额（这一条由写入方在同一笔变动里保证）
      </div>
      <template #footer>
        <el-button @click="detailOpen = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 钱包流水：这一行账的进出明细，只读。
         一行 = 一次余额变动，amount 带符号（收入正、支出负），balance 是这一笔之后的余额 ——
         上一笔的余额接下一笔的变动就是这本账的来龙去脉，所以这里不放新增/修改/删除按钮
         （手工插一条流水会让它对不上账户表上那几个数） -->
    <el-dialog v-model="recordOpen" :title="`钱包流水 - ${recordUserText}`" width="1000px" append-to-body
      destroy-on-close>
      <div class="record-toolbar">
        <span class="record-summary">
          可用 {{ validate.formatAmount(recordAccount.balance) }} / 冻结中
          {{ validate.formatAmount(recordAccount.frozenBalance) }} / 累计充值
          {{ validate.formatAmount(recordAccount.totalRecharge) }} / 累计消费 {{ validate.formatAmount(recordAccount.totalConsume) }}
          / 累计提现 {{ validate.formatAmount(recordAccount.totalWithdraw) }} / 赠送
          {{ validate.formatAmount(recordAccount.giftBalance) }}
        </span>
        <div class="record-controls">
          <el-select v-model="recordQuery.bizType" placeholder="全部类型" clearable class="record-filter"
            @change="handleRecordFilter">
            <el-option v-for="item in dict.options(DICT_BIZ_TYPE)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </div>
      </div>

      <el-table :data="recordList" size="small" border v-loading="recordLoading" show-overflow-tooltip
        empty-text="这个人还没有钱包流水">
        <el-table-column label="时间" align="center" width="160">
          <template #default="{ row }">{{ validate.formatTime(row.createTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="业务类型" align="center" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_BIZ_TYPE, row.bizType)">
              {{ dict.label(DICT_BIZ_TYPE, row.bizType) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 带符号：收入 +¥N 绿色、支出 -¥N 红色（和左边类型那列的标签颜色是两套东西，别混着看） -->
        <el-table-column label="变动" align="right" width="120">
          <template #default="{ row }">
            <span :class="Number(row.amount) < 0 ? 'amount-out' : 'amount-in'">{{ signedAmount(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="变动后余额" align="right" width="120">
          <template #default="{ row }">{{ validate.formatAmount(row.balance) }}</template>
        </el-table-column>
        <el-table-column label="关联单号" align="center" min-width="150">
          <template #default="{ row }">{{ row.bizNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="说明" align="center" min-width="160">
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination background size="small" v-model:current-page="recordQuery.pageNum"
          v-model:page-size="recordQuery.pageSize" :page-sizes="[10, 20, 50]" :total="recordTotal"
          layout="total, sizes, prev, pager, next, jumper" @size-change="handleRecordSizeChange"
          @current-change="getRecordList" />
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="recordOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { listShopWalletApi, listShopWalletRecordApi } from '@/api/consumer/wallet'
import { useDict } from '@/utils/dictCache'
import validate from '@/utils/validate'

/** 业务类型和账户状态都是枚举列，标签一律走字典，不在这页里写死中文名 */
const DICT_BIZ_TYPE = 'wallet_biz_type'
const DICT_STATUS = 'wallet_status'
const dict = useDict(DICT_BIZ_TYPE, DICT_STATUS)

const loading = ref(true)
const total = ref(0)
const dataList = ref<any[]>([])

// 查询参数：keyword 匹配昵称/账号/手机号（服务端联 sys_user 现筛，不在这张表里冗余）
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  status: undefined
})

const queryFormRef = ref<any>()

/** 钱包流水弹窗：只读，按被点的那一行账（那个人）查 */
const recordOpen = ref(false)
const recordLoading = ref(false)
const recordList = ref<any[]>([])
const recordTotal = ref(0)
/** 顶上那行汇总读的是列表点进来的那一行账户，弹窗期间不刷新（同积分流水那一页） */
const recordAccount = ref<any>({})
const recordUserText = ref('')
const recordQuery = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  userId: undefined,
  bizType: undefined
})

/** 详情弹窗：读的是被点的那一行账，不再捞接口 */
const detailOpen = ref(false)
const detail = ref<any>({})
const detailUserText = ref('')

/** 流水里的变动数：库里是带符号存的（收入正、支出负），这里补个正号并把金额摆出来，让进出一眼能分开 */
const signedAmount = (value: any) => {
  const amount = Number(value)
  if (!Number.isFinite(amount)) return '¥0.00'
  const text = `¥${Math.abs(amount).toFixed(2)}`
  return amount > 0 ? `+${text}` : amount < 0 ? `-${text}` : text
}

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listShopWalletApi(queryParams)
    dataList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
  } finally {
    loading.value = false
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
  queryParams.status = undefined
  handleQuery()
}

/** 详情按钮操作：把被点的那一行账整个摆出来（那几个数 + 这个人的昵称手机号 + 两头的留痕时间） */
const handleDetail = (row: any) => {
  detail.value = row
  detailUserText.value = row.userName || `用户 #${row.userId}`
  detailOpen.value = true
}

/**
 * 流水按钮操作：把弹窗定位到被点的那一行账（那个人），从最新一笔看起。
 * 标题和汇总那行都带上被点的人，是为了翻页/筛选之后也知道看的是谁的账
 */
const handleRecord = (row: any) => {
  recordAccount.value = row
  recordUserText.value = row.userName || `用户 #${row.userId}`
  recordQuery.pageNum = 1
  recordQuery.userId = row.userId
  recordQuery.bizType = undefined
  recordList.value = []
  recordTotal.value = 0
  recordOpen.value = true
  getRecordList()
}

/** 流水列表：只读，条件就是这一行账 */
const getRecordList = async () => {
  recordLoading.value = true
  try {
    const { data } = await listShopWalletRecordApi(recordQuery)
    recordList.value = data.records || []
    recordTotal.value = data.total || 0
  } catch (error) {
  } finally {
    recordLoading.value = false
  }
}

/** 类型筛选换了就回到第一页（不回到第一页会停在筛完根本没有的页数上） */
const handleRecordFilter = () => {
  recordQuery.pageNum = 1
  getRecordList()
}

const handleRecordSizeChange = () => {
  recordQuery.pageNum = 1
  getRecordList()
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

/* 按钮那一格（上面 :last-child 把它压成了 0 0 auto，所以选择器多带一个 .el-form-item 才顶得住） */
.search-form :deep(.el-form-item.search-actions) {
  flex: 0 0 auto;
  min-width: 0;
  margin-right: 0;
}

/* 流水弹窗里表格上面那一排：这个人的那几个数在左、类型筛选在右（同积分流水那一页） */
.record-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;

  .record-summary {
    color: var(--el-text-color-secondary);
  }

  .record-controls {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  /* 这一格不跟着整行撑满：160px 够放"全部类型"和几个选项名 */
  .record-filter {
    width: 160px;
    flex: 0 0 auto;
  }
}

.balance {
  font-weight: 600;
}

/* 详情弹窗里两组描述之间的间距 */
.detail-block {
  margin-top: 20px;
}

/* 口径说明（恒等式、各档动的是哪一格）：次要信息，放在弹窗最后一行 */
.detail-tip {
  margin-top: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

/* 冻结中那一格：有钱占着就标黄 —— 那是下单没付的单子，付了才真扣走、取消就退回来 */
.amount-warning {
  color: var(--el-color-warning);
  font-weight: 600;
}

/* 流水里的变动数：收入（正）绿、支出（负）红 */
.amount-in {
  color: var(--el-color-success);
  font-weight: 600;
}

.amount-out {
  color: var(--el-color-danger);
  font-weight: 600;
}
</style>
