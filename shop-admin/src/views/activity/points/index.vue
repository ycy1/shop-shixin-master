<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="昵称/账号/手机号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item class="search-actions">
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>

        <!-- 账户是按用户唯一的一行，一次积分变动才建一行，所以这一页没有新增/删除按钮：
             五个数都只能由积分动作改，后台手工建账会让账和流水对不上（同商品库存那一页）。
             「修改」那颗调的是积分（方向 + 数量 + 原因），不是直接把这五个数填进去 -->
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
        <!-- 可用那一格是这一页的主数：加粗、多的排在前面（服务端 ORDER BY balance DESC） -->
        <el-table-column label="可用积分" align="right" width="110">
          <template #default="{ row }">
            <span class="balance">{{ pointsText(row.balance) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="累计获得" align="right" width="100">
          <template #default="{ row }">{{ pointsText(row.totalPoints) }}</template>
        </el-table-column>
        <el-table-column label="累计消耗" align="right" width="100">
          <template #default="{ row }">{{ pointsText(row.usedPoints) }}</template>
        </el-table-column>
        <el-table-column label="冻结中" align="right" width="100">
          <template #default="{ row }">{{ pointsText(row.frozenPoints) }}</template>
        </el-table-column>
        <el-table-column label="即将过期" align="right" width="100">
          <template #default="{ row }">
            <span :class="{ 'points-warning': Number(row.expirePoints) > 0 }">{{ pointsText(row.expirePoints) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="建账时间" align="center" width="170">
          <template #default="{ row }">{{ validate.formatTime(row.createTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="250" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['activity:points:detail']" type="primary" link icon="View"
              @click="handleDetail(row)">详情</el-button>
            <!-- 修改：改的是积分不是那五个数（方向 + 数量 + 原因），落账和流水同一笔写完 -->
            <el-button v-permission="['activity:points:adjust']" type="primary" link icon="Edit"
              @click="handleAdjust(row)">修改</el-button>
            <!-- 流水：这个人积分的进出明细，只能由积分动作写出来，所以这一格只有查 -->
            <el-button v-permission="['activity:points:record']" type="primary" link icon="Document"
              @click="handleRecord(row)">积分流水</el-button>
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

    <!-- 积分账户详情：只读。五个数读的就是列表这一行（服务端从库里那一格带来，不做减法），
         所以这里不再捞一次接口 —— 这一页只有「修改」会写账，写完就刷新列表，弹窗期间不会有第二版 -->
    <el-dialog v-model="detailOpen" :title="`积分账户详情 - ${detailUserText}`" width="760px" append-to-body
      destroy-on-close>
      <el-descriptions title="用户" :column="2" border>
        <el-descriptions-item label="昵称">{{ detail.userName || `已删除用户 #${detail.userId}` }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.mobile || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ detail.userId }}</el-descriptions-item>
        <el-descriptions-item label="建账时间">
          {{ validate.formatTime(detail.createTime) || '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <el-descriptions title="积分" :column="3" border class="detail-block">
        <el-descriptions-item label="可用积分">
          <span class="balance">{{ pointsText(detail.balance) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="累计获得">{{ pointsText(detail.totalPoints) }}</el-descriptions-item>
        <el-descriptions-item label="累计消耗">{{ pointsText(detail.usedPoints) }}</el-descriptions-item>
        <el-descriptions-item label="冻结中">{{ pointsText(detail.frozenPoints) }}</el-descriptions-item>
        <el-descriptions-item label="即将过期">
          <span :class="{ 'points-warning': Number(detail.expirePoints) > 0 }">
            {{ pointsText(detail.expirePoints) }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="最后改动时间">
          {{ validate.formatTime(detail.updateTime) || '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <!-- 恒等式说在这一页：账上的数对不上这条式子就说明有笔变动只写了账没写流水 -->
      <div class="detail-tip">可用 = 累计获得 − 累计消耗 − 冻结中（这一格由写入方在同一笔变动里保证）</div>
      <template #footer>
        <el-button @click="detailOpen = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 修改：管理员调整积分。表单只有"给谁 / 加还是减 / 多少分 / 为什么（增加时还能给这批分定个过期时间）"，
         那五个账面数字一概填不了 —— 直接填数会让流水里对不上这一笔（写法同商品库存只改预警参数） -->
    <el-dialog v-model="adjustOpen" title="调整用户积分" width="620px" append-to-body destroy-on-close>
      <el-form ref="adjustFormRef" :model="adjustForm" :rules="adjustRules" label-width="100px">
        <el-form-item label="用户">
          <el-input :model-value="adjustUserText" disabled />
        </el-form-item>
        <el-form-item label="当前可用">
          <el-input :model-value="pointsText(adjustForm.balance)" disabled />
        </el-form-item>
        <el-form-item label="调整方向" prop="type">
          <el-radio-group v-model="adjustForm.type">
            <el-radio :value="1">增加</el-radio>
            <el-radio :value="2">减少</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="调整数量" prop="points">
          <!-- 数量只给多少个、正负由方向定，所以最小是 1；减分时上限摆在可用那一格上（服务端还有闸门） -->
          <el-input-number v-model="adjustForm.points" :min="1" :max="adjustMax" :precision="0" :step="10"
            controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="调整原因" prop="reason">
          <el-input v-model="adjustForm.reason" type="textarea" :rows="3" maxlength="200" show-word-limit
            placeholder="这一格会原样写进积分流水，日后对账就看它" />
        </el-form-item>
        <!-- 过期时间只在"增加"这一头才有意义（流水表那一格本来就是获取时记、消耗那几行为空），
             扣减时这一格不出现，选了扣减也不会往服务端带 -->
        <el-form-item v-if="adjustForm.type === 1" label="过期时间" prop="expireTime">
          <el-date-picker v-model="adjustForm.expireTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选填，不填这一笔不过期" style="width: 100%" />
        </el-form-item>
      </el-form>
      <div class="detail-tip">
        增加记进「累计获得」并加到可用，扣减从可用挪进「累计消耗」（累计获得不动，历史上给过的那些分还在）；
        两种都会留一笔「管理员调整」的积分流水，增加那笔的过期时间落在那行流水的过期时间上
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="adjustOpen = false">取 消</el-button>
          <el-button type="primary" :loading="adjustLoading" @click="submitAdjust">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 积分流水：这一行账的进出明细，只读。
         一行 = 一次积分变动，points 带符号（获取正、消耗负），balance 是这一笔之后的余额 ——
         上一笔的余额接下一笔的变动就是这本账的来龙去脉，所以这里不放新增/修改/删除按钮
         （手工插一条流水会让它对不上账户表上那五个数） -->
    <el-dialog v-model="recordOpen" :title="`积分流水 - ${recordUserText}`" width="1000px" append-to-body
      destroy-on-close>
      <div class="record-toolbar">
        <span class="record-summary">
          可用 {{ pointsText(recordAccount.balance) }} / 累计获得 {{ pointsText(recordAccount.totalPoints) }} /
          累计消耗 {{ pointsText(recordAccount.usedPoints) }} / 冻结中 {{ pointsText(recordAccount.frozenPoints) }} /
          即将过期 {{ pointsText(recordAccount.expirePoints) }}
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
        empty-text="这个人还没有积分流水">
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
        <!-- 带符号：获取 +N 绿色、消耗 -N 红色（和左边类型那列的标签颜色是两套东西，别混着看） -->
        <el-table-column label="变动" align="right" width="100">
          <template #default="{ row }">
            <span :class="Number(row.points) < 0 ? 'points-out' : 'points-in'">{{ signedText(row.points) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="变动后余额" align="right" width="110">
          <template #default="{ row }">{{ pointsText(row.balance) }}</template>
        </el-table-column>
        <el-table-column label="关联单号" align="center" min-width="150">
          <template #default="{ row }">{{ row.bizNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="说明" align="center" min-width="160">
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <!-- 只有获取那一头的行有过期时间：消耗/抵扣那几笔是把分花掉，本身不会"过期" -->
        <el-table-column label="过期时间" align="center" width="160">
          <template #default="{ row }">{{ validate.formatTime(row.expireTime) || '-' }}</template>
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
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { listShopPointsApi, listShopPointsRecordApi, adjustShopPointsApi } from '@/api/activity/points'
import { useDict } from '@/utils/dictCache'
import validate from '@/utils/validate'

/** 业务类型是枚举列，标签一律走字典，不在这页里写死中文名 */
const DICT_BIZ_TYPE = 'points_biz_type'
const dict = useDict(DICT_BIZ_TYPE)

const loading = ref(true)
const total = ref(0)
const dataList = ref<any[]>([])

// 查询参数：keyword 匹配昵称/账号/手机号（服务端联 sys_user 现筛，不在这张表里冗余）
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined
})

const queryFormRef = ref<any>()

/** 积分流水弹窗：只读，按被点的那一行账（那个人）查 */
const recordOpen = ref(false)
const recordLoading = ref(false)
const recordList = ref<any[]>([])
const recordTotal = ref(0)
/** 顶上那行汇总读的是列表点进来的那一行账户，弹窗期间不刷新（同优惠券发放记录那一页） */
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

const adjustOpen = ref(false)
const adjustLoading = ref(false)
const adjustUserText = ref('')
const adjustFormRef = ref<FormInstance>()
/** balance 是打开弹窗时读到的那一版，只用来回显和兜上限，服务端扣的是当时账上的可用 */
const adjustForm = reactive<any>({
  userId: undefined,
  balance: undefined,
  type: 1,
  points: undefined,
  reason: undefined,
  expireTime: undefined
})

const adjustRules = reactive<FormRules>({
  type: [{ required: true, message: '请选择是增加还是减少', trigger: 'change' }],
  points: [{ required: true, message: '请填写调整数量', trigger: 'blur' }],
  reason: [{ required: true, message: '请填写调整原因', trigger: 'blur' }]
})

/** 减分时最多只能减到可用那一格（冻结中的那一部分服务端也不让扣）；增加没有上限 */
const adjustMax = computed(() => (adjustForm.type === 2 ? Number(adjustForm.balance) || 0 : undefined))

/** 积分是整数，库里就没有小数位；空格子（脏数据）当 0 说，别让列表里跳出一片空白 */
const pointsText = (value: any) => {
  const count = Number(value)
  return Number.isFinite(count) ? String(count) : '0'
}

/** 流水里的变动数：库里就是带符号存的（获取正、消耗负），这里补个正号让进出一眼能分开 */
const signedText = (value: any) => {
  const count = Number(value)
  if (!Number.isFinite(count)) return '0'
  return count > 0 ? `+${count}` : `${count}`
}

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listShopPointsApi(queryParams)
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
  handleQuery()
}

/** 详情按钮操作：把被点的那一行账整个摆出来（五个数 + 这个人的昵称手机号 + 两头的留痕时间） */
const handleDetail = (row: any) => {
  detail.value = row
  detailUserText.value = row.userName || `用户 #${row.userId}`
  detailOpen.value = true
}

/** 修改按钮操作：表单收 给谁 / 方向 / 数量 / 原因（+ 增加那批分的过期时间），当前可用那一格是拿来限定扣减上限的 */
const handleAdjust = (row: any) => {
  adjustFormRef.value?.resetFields()
  adjustForm.userId = row.userId
  adjustForm.balance = row.balance
  adjustForm.type = 1
  adjustForm.points = undefined
  adjustForm.reason = undefined
  adjustForm.expireTime = undefined
  adjustUserText.value = row.userName || `用户 #${row.userId}`
  adjustOpen.value = true
}

/** 提交调整：账和流水在同一个事务里写完，服务端返回调整后这个用户的可用积分，这里直接刷列表 */
const submitAdjust = () => {
  adjustFormRef.value?.validate(async (valid: any) => {
    if (!valid) return
    adjustLoading.value = true
    try {
      await adjustShopPointsApi({
        userId: adjustForm.userId,
        type: adjustForm.type,
        points: adjustForm.points,
        reason: adjustForm.reason,
        // 扣减那一种不往服务端带过期时间（服务端也会按方向再挡一次）
        expireTime: adjustForm.type === 1 ? adjustForm.expireTime : undefined
      })
      ElMessage.success(adjustForm.type === 1 ? '积分已增加' : '积分已扣减')
      adjustOpen.value = false
      getList()
    } catch (error) {
    } finally {
      adjustLoading.value = false
    }
  })
}

/**
 * 积分流水按钮操作：把弹窗定位到被点的那一行账（那个人），从最新一笔看起。
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
    const { data } = await listShopPointsRecordApi(recordQuery)
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

/* 流水弹窗里表格上面那一排：这个人的五个数在左、类型筛选在右（同优惠券发放记录那一页） */
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

/* 口径说明（恒等式、加减各记进哪一格）：次要信息，放在弹窗最后一行 */
.detail-tip {
  margin-top: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

/* 即将过期那一格：有分快到期就标红，这一页存在的意义就是要去把它花掉 */
.points-warning {
  color: var(--el-color-danger);
  font-weight: 600;
}

/* 流水里的变动数：获取（正）绿、消耗（负）红 */
.points-in {
  color: var(--el-color-success);
  font-weight: 600;
}

.points-out {
  color: var(--el-color-danger);
  font-weight: 600;
}
</style>
