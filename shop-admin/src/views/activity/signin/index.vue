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

        <!-- 这一页是按用户分组的签到汇总，一行 = 一个人的所有签到，所以没有新增/删除按钮：
             天数、积分、连续天数都是那一张张签到行现算出来的，手工建一行没有意义。
             唯一的写入口是「签到记录」日历里的补签（只收"补哪一天"），它和正常签到一样给分 -->
      </el-form>
    </div>

    <el-card class="box-card">
      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" row-key="userId" show-overflow-tooltip>
        <el-table-column label="用户" align="center" min-width="140">
          <template #default="{ row }">{{ row.userName || `已删除用户 #${row.userId}` }}</template>
        </el-table-column>
        <el-table-column label="手机号" align="center" width="130">
          <template #default="{ row }">{{ row.mobile || '-' }}</template>
        </el-table-column>
        <!-- 最近签到那一格是这一页的主数：加粗、最近签过的排在前面（服务端 ORDER BY lastSignDate DESC） -->
        <el-table-column label="最近签到" align="center" width="120">
          <template #default="{ row }">
            <span class="latest">{{ dateText(row.lastSignDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="累计签到" align="right" width="100">
          <template #default="{ row }">{{ countText(row.signCount) }} 天</template>
        </el-table-column>
        <el-table-column label="当前连续" align="right" width="100">
          <template #default="{ row }">{{ countText(row.currentContinuous) }} 天</template>
        </el-table-column>
        <!-- 补签那一格单列出来：这一页的数不全是消费者自己签出来的，对账时要能一眼分开 -->
        <el-table-column label="其中补签" align="right" width="100">
          <template #default="{ row }">
            <span :class="{ 'signin-warning': Number(row.makeupCount) > 0 }">{{ countText(row.makeupCount) }} 天</span>
          </template>
        </el-table-column>
        <el-table-column label="签到累计积分" align="right" width="120">
          <template #default="{ row }">{{ countText(row.totalPoints) }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="120" fixed="right">
          <template #default="{ row }">
            <!-- 签到记录：日历形式的明细，点开才捞那个月。过去的日子能补签，签过的那几天只读 -->
            <el-button v-permission="['activity:signin:record']" type="primary" link icon="Calendar"
              @click="handleRecord(row)">签到记录</el-button>
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

    <!-- 签到记录：日历形式的明细，公共组件 SigninCalendar（一格 = 归属某一天，不是操作那一刻，所以补在 9 月的签落在 8 月那一格上）。
         这一页是"给别人看、给别人补"那一头：不限能补的日子、那颗压 activity:signin:makeup -->
    <SigninCalendar v-model:visible="recordOpen" :user-id="recordUserId" :user-text="recordUserText"
      :title="`签到记录 - ${recordUserText}`" @changed="getList" />
  </div>
</template>

<script setup lang="ts">
import SigninCalendar from '@/components/SigninCalendar/index.vue'
import { listShopSigninApi } from '@/api/activity/signin'
import validate from '@/utils/validate'

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

/** 签到记录弹窗（公共组件 SigninCalendar）：这两个只用来定"看哪个人"，捞记录/换月/补签都在组件里 */
const recordOpen = ref(false)
const recordUserId = ref<number>()
const recordUserText = ref('')

/** 天数、积分都是整数，脏数据读到的空格子当 0 说，别让列表里跳出一片空白 */
const countText = (value: any) => {
  const count = Number(value)
  return Number.isFinite(count) ? String(count) : '0'
}

/** 归属日只到日：库里是 date 列，没有时分秒 */
const dateText = (value: any) => validate.formatTime(value, 'YYYY-MM-DD')

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listShopSigninApi(queryParams)
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

/** 签到记录按钮操作：弹窗定位到被点的那个人，组件打开时自己从当月捞 */
const handleRecord = (row: any) => {
  recordUserId.value = row.userId
  recordUserText.value = row.userName || `用户 #${row.userId}`
  recordOpen.value = true
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

.latest {
  font-weight: 600;
}

/* 其中补签那一格：有补签就标一下，这一页的数不全是消费者自己签出来的 */
.signin-warning {
  color: var(--el-color-warning);
  font-weight: 600;
}
</style>
