<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="徽章名称/徽章文字" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="徽章类型" prop="badgeType">
          <el-select v-model="queryParams.badgeType" placeholder="全部" clearable>
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
        <el-form-item class="search-actions">
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-card class="box-card">
      <!-- 操作工具栏 -->
      <template #header>
        <ButtonGroup>
          <el-button v-permission="['activity:badge:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['activity:badge:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <!-- 这一列是"这枚徽章给人看的样子"：有图标显示图标、没图标是那几行字，形状收在 BadgeView 那一处，
             所以不是纯文本列，tooltip 要关掉 -->
        <el-table-column label="徽章" align="center" min-width="150" :show-overflow-tooltip="false">
          <template #default="{ row }">
            <badge-view :icon="row.badgeIcon" :text="row.badgeText" :name="row.badgeName" preview />
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_STATUS, row.status)">
              {{ dict.label(DICT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" align="center" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_TYPE, row.badgeType)">
              {{ dict.label(DICT_TYPE, row.badgeType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标" align="center" min-width="130">
          <template #default="{ row }">{{ targetText(row) }}</template>
        </el-table-column>
        <el-table-column label="达标线" align="center" min-width="120">
          <template #default="{ row }">{{ reachText(row) }}</template>
        </el-table-column>
        <el-table-column label="奖励" align="center" min-width="150">
          <template #default="{ row }">{{ rewardText(row) }}</template>
        </el-table-column>
        <el-table-column label="已获得" align="center" width="90">
          <template #default="{ row }">{{ row.issuedUserCount || 0 }} 人</template>
        </el-table-column>
        <el-table-column label="优先级" prop="priority" align="center" width="80" />

        <el-table-column label="操作" align="center" width="220" fixed="right">
          <template #default="{ row }">
            <!-- 「下发」不在这排，它在「获取记录」弹窗顶上（同优惠券那页）。「已经发出去过的删不掉」是服务层的判据，这里不预告 -->
            <TableMoreActions :actions="[
              { label: '详情', icon: 'View', hidden: !hasPermission('activity:badge:detail'), command: { type: 'detail', row } },
              { label: '获取记录', icon: 'Tickets', hidden: !hasPermission('activity:badge:record'), command: { type: 'record', row } },
              { label: '修改', icon: 'Edit', hidden: !hasPermission('activity:badge:update'), loading: updatingId === row.id, command: { type: 'update', row } },
              { label: '删除', type: 'danger', icon: 'Delete', hidden: !hasPermission('activity:badge:delete'), command: { type: 'delete', row } }
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
    </el-card>

    <!-- 添加或修改对话框 -->
    <el-dialog v-model="open" :title="title" width="900px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="徽章名称" prop="badgeName">
              <el-input v-model="form.badgeName" placeholder="请输入徽章名称" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="徽章类型" prop="badgeType">
              <!-- 这一档说的是"由谁来判达标"，改它就是把这枚徽章换一条判定链路，所以换完要把目标清掉重选 -->
              <el-select v-model="form.badgeType" placeholder="请选择" style="width: 100%" @change="handleTypeChange">
                <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <!-- 目标只有门店/活动两类用得上：签到和消费本来就比的是全平台，服务层会把这一格清成 NULL -->
          <el-col v-if="needTarget" :span="12">
            <el-form-item :label="targetLabel" prop="targetId">
              <el-select v-model="form.targetId" placeholder="请选择" filterable clearable style="width: 100%">
                <el-option v-for="item in targetOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="reachLabel" prop="reachCount">
              <el-input-number v-model="form.reachCount" :min="0" :precision="0" :controls="false"
                :placeholder="reachPlaceholder" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="奖励积分" prop="points">
              <el-input-number v-model="form.points" :min="0" :precision="0" :controls="false"
                placeholder="不发分就填 0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级" prop="priority">
              <el-input-number v-model="form.priority" :min="0" :precision="0" :controls="false"
                placeholder="一个人同时有多枚时，大的排前面" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择" style="width: 100%">
                <el-option v-for="item in dict.options(DICT_STATUS)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <!-- 奖励的券是多选下拉拼出来的（库里那一格是逗号分隔的券模板 id）；勾掉的券从下一排 chips 上点 × 摘 -->
          <el-col :span="24">
            <el-form-item label="奖励优惠券">
              <el-select v-model="pickedCouponIds" multiple filterable placeholder="选择要一并发出的优惠券（可不选）"
                style="width: 100%">
                <el-option v-for="item in couponOptions" :key="item.id" :label="item.couponName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="已选券">
              <div v-if="couponItems.length" class="scope-chips">
                <el-tag v-for="item in couponItems" :key="item.id" closable type="info" @close="removeCoupon(item.id)">
                  {{ item.name || `券 #${item.id}` }}
                </el-tag>
              </div>
              <span v-else class="chips-empty">这一枚不发券</span>
            </el-form-item>
          </el-col>

          <el-divider content-position="left">外观（发到用户手上那一刻会定成快照）</el-divider>
          <el-col :span="12">
            <el-form-item label="徽章文字" prop="badgeText">
              <el-input v-model="form.badgeText" placeholder="显示在徽章上的那几个字，如「新品」" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="徽章图标" prop="badgeIcon">
              <upload-image v-model="form.badgeIcon" :size="120" source="activity" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预览">
              <badge-view :icon="form.badgeIcon" :text="form.badgeText || form.badgeName" :size="90" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="说明" prop="description">
              <el-input v-model="form.description" type="textarea" :rows="2"
                placeholder="给运营自己看的：这一枚怎么拿、什么时候发" maxlength="255" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cancel">取 消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 详情：只读看这一枚的全部登记信息（口径同优惠券、活动那两页的详情弹窗），要改内容走「修改」 -->
    <el-dialog v-model="detailOpen" title="徽章详情" width="900px" append-to-body destroy-on-close>
      <el-descriptions :column="2" border v-loading="detailLoading">
        <el-descriptions-item label="徽章名称" width="150px">{{ detail.badgeName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="dict.tag(DICT_STATUS, detail.status)">
            {{ dict.label(DICT_STATUS, detail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag size="small" :type="dict.tag(DICT_TYPE, detail.badgeType)">
            {{ dict.label(DICT_TYPE, detail.badgeType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="目标">{{ targetText(detail) }}</el-descriptions-item>
        <el-descriptions-item :label="reachTitle(detail)">{{ reachText(detail) }}</el-descriptions-item>
        <el-descriptions-item label="奖励">{{ rewardText(detail) }}</el-descriptions-item>
        <el-descriptions-item label="已获得">{{ detail.issuedUserCount || 0 }} 人</el-descriptions-item>
        <el-descriptions-item label="优先级">{{ detail.priority ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="徽章文字">{{ detail.badgeText || '-' }}</el-descriptions-item>
        <el-descriptions-item label="徽章图标">
          <el-image v-if="detail.badgeIcon" :src="detail.badgeIcon" :preview-src-list="[detail.badgeIcon]"
            preview-teleported fit="contain" style="width: 90px; height: 90px; border-radius: 4px" />
          <span v-else>未上传</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ detail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="说明" :span="2">{{ detail.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="修改时间" :span="2">{{ detail.updateTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 获取记录：这一枚都在谁手上。三条写口子都在这个弹窗里 —— 删除（抹掉一行，当初一起兑现的分和券不收回）、
         「状态」和「是否展示」那两格的开关（各只改一列）；其余三条写入路都在 shop-api：支付回调、签到、下面的「下发」 -->
    <el-dialog v-model="recordOpen" :title="`获取记录 - ${recordBadge.badgeName || ''}`" width="1250px" append-to-body
      destroy-on-close>
      <div class="record-toolbar">
        <span class="record-summary">已有 {{ recordBadge.issuedUserCount || 0 }} 人拿到这一枚</span>
        <div class="record-controls">
          <el-input v-model="recordQuery.keyword" placeholder="昵称/手机号" clearable class="record-keyword"
            @keyup.enter="handleRecordFilter" @clear="handleRecordFilter" />
          <el-select v-model="recordQuery.source" placeholder="全部来源" clearable class="record-filter"
            @change="handleRecordFilter">
            <el-option v-for="item in dict.options(DICT_SOURCE)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
          <el-select v-model="recordQuery.status" placeholder="全部状态" clearable class="record-filter"
            @change="handleRecordFilter">
            <el-option v-for="item in dict.options(DICT_USER_STATUS)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
          <el-button v-permission="['activity:badge:give']" type="primary" plain icon="Promotion" class="record-give"
            @click="handleGive">下发</el-button>
          <el-button v-permission="['activity:badge:recordDelete']" type="danger" plain icon="Delete"
            :disabled="selectedRecordIds.length === 0" @click="handleRecordBatchDelete">批量删除</el-button>
        </div>
      </div>

      <el-table :data="recordList" size="small" border v-loading="recordLoading"
        row-key="id" show-overflow-tooltip empty-text="这一枚徽章还没有获取记录"
        @selection-change="handleRecordSelection">
        <el-table-column type="selection" width="45" align="center" />
        <el-table-column label="持有人" align="center" min-width="120">
          <template #default="{ row }">{{ recordUserText(row) }}</template>
        </el-table-column>
        <el-table-column label="手机号" align="center" width="130">
          <template #default="{ row }">{{ row.mobile || '-' }}</template>
        </el-table-column>
        <!-- 快照那一列：说的是"这一枚到他手上时叫什么、长什么样"，所以不看定义表 -->
        <el-table-column label="徽章（快照）" align="center" min-width="170" :show-overflow-tooltip="false">
          <template #default="{ row }">
            <badge-view :icon="row.badgeIcon" :text="row.badgeText" :name="row.badgeName" preview />
          </template>
        </el-table-column>
        <el-table-column label="获得方式" align="center" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_SOURCE, row.source)">
              {{ dict.label(DICT_SOURCE, row.source) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 这一格也是一改就提交的开关：1 有效 / 0 失效（「已过期」那一档已经下线，过期看右边「有效期到」那一格）。
             失效既不收回当初兑现的分和券、也不抹掉这一行，只是前台/APP 上这一枚变灰、标签跟着念「失效」 -->
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="1" :inactive-value="0"
              :loading="recordStatusLoadingId === row.id" :disabled="!hasPermission('activity:badge:recordStatus')"
              @change="handleRecordStatusChange(row)" />
          </template>
        </el-table-column>
        <!-- 这一格是一改就提交的开关（照公告管理那一列的形状）：只动 is_show，不碰快照、有效期和状态 -->
        <el-table-column label="是否展示" align="center" width="90">
          <template #default="{ row }">
            <el-switch v-model="row.isShow" :active-value="1" :inactive-value="0"
              :loading="recordShowLoadingId === row.id" :disabled="!hasPermission('activity:badge:recordShow')"
              @change="handleRecordShowChange(row)" />
          </template>
        </el-table-column>
        <el-table-column label="获得时间" align="center" width="160">
          <template #default="{ row }">{{ validate.formatTime(row.startTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="有效期到" align="center" width="160">
          <template #default="{ row }">{{ validate.formatTime(row.endTime) || '永久' }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="80" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['activity:badge:recordDelete']" type="danger" link icon="Delete"
              @click="handleRecordDelete(row)">删除</el-button>
          </template>
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

    <!-- 下发：一次给一批用户发一枚徽章，不看达标线。参数只送"哪一枚、发给谁、到什么时候"三格，
         奖励那两格由后端按定义表现取现发（前端传个积分数进来就能凭空给人加一笔分） -->
    <el-dialog v-model="giveOpen" :title="`下发徽章 - ${recordBadge.badgeName || ''}`" width="720px" append-to-body
      destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="选择用户">
          <!-- 多选：弹窗里一次勾一批、确定交回整批，下面那排 chips 跟着换（单条想摘掉点 ×） -->
          <user-select v-model="pickedUserIds" :account-type="3" multiple placeholder="选择要下发的用户"
            @select="handleGiveSelect" />
        </el-form-item>
        <el-form-item label="已选用户">
          <div v-if="giveForm.users.length" class="user-chips">
            <el-tag v-for="item in giveForm.users" :key="item.id" closable type="info" @close="removeGiveUser(item.id)">
              {{ item.nickname || `用户 #${item.id}` }}
            </el-tag>
          </div>
          <span v-else class="chips-empty">还没选人，弹窗里可以一次勾多个</span>
        </el-form-item>
        <el-form-item label="有效期至">
          <!-- 只有这一条路给得出有效期：定义表上没有过期时间那一格，自动判定发出去的那些都是永久的 -->
          <el-date-picker v-model="giveForm.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="不填就是永久有效" style="width: 100%" />
        </el-form-item>
        <el-form-item label=" ">
          <span class="give-tip">
            本次下发 {{ giveForm.users.length }} 人，{{ rewardText(recordBadge) }}；
            已经持有这一枚的人会自动跳过，所以实发人数可能比这个数少
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="giveOpen = false">取 消</el-button>
          <el-button type="primary" :loading="giveLoading" :disabled="giveForm.users.length === 0"
            @click="submitGive">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listShopBadgeApi,
  detailShopBadgeApi,
  addShopBadgeApi,
  updateShopBadgeApi,
  deleteShopBadgeApi,
  listShopBadgeRecordApi,
  deleteShopBadgeRecordApi,
  updateShopBadgeRecordStatusApi,
  updateShopBadgeRecordShowApi,
  giveShopBadgeApi
} from '@/api/activity/badge'
import { listShopCouponApi } from '@/api/activity/coupon'
import { optionsShopStoreApi } from '@/api/store/store'
import { listShopActivityApi } from '@/api/activity/info'
import BadgeView from '@/components/BadgeView/index.vue'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import UploadImage from '@/components/Upload/Image.vue'
import TableMoreActions from '@/components/TableMoreActions/index.vue'
import validate from '@/utils/validate'
import { useDict } from '@/utils/dictCache'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
/** 操作列走 TableMoreActions，权限得在这里自己判（组件没有 v-permission 那套指令，只认 actions 上的 hidden） */
const permissions = computed(() => userStore.user.permissions || [])
const hasPermission = (permission: string): boolean => {
  return permissions.value.includes(permission)
}

const DICT_TYPE = 'badge_type'
const DICT_STATUS = 'badge_status'
/** 「获取记录」弹窗顶上那两个筛选用到的字典：获得来源、用户徽章状态（行上那颗状态开关拨的就是 status 这一列；
 *  「是否展示」不接字典 —— 只有两档，开关本身说得清） */
const DICT_SOURCE = 'badge_source'
const DICT_USER_STATUS = 'user_badge_status'

const dict = useDict(DICT_TYPE, DICT_STATUS, DICT_SOURCE, DICT_USER_STATUS)

/** 判据要和服务层对齐的几档：只有门店(1)/活动(2)两类要绑目标，达标线比的数按类型换单位 */
const TYPE_STORE = 1
const TYPE_ACTIVITY = 2
const TYPE_SIGNIN = 3

const loading = ref(true)
const submitLoading = ref(false)
const selectedIds = ref<any[]>([])
const total = ref(0)
const dataList = ref<any[]>([])
const title = ref('')
const open = ref(false)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<any>({})

/* ---------------- 获取记录（用户徽章那一头）：列表页内的弹窗，写法照优惠券的「发放记录」 ---------------- */
const recordOpen = ref(false)
const recordLoading = ref(false)
const recordList = ref<any[]>([])
const recordTotal = ref(0)
/** 弹窗标题和顶上那行汇总用的是列表那一行带回的数据；下发完会按新数据换一次 */
const recordBadge = ref<any>({})
/** 勾选中的明细行 id（一行就是"一人一枚"，不是用户 id —— 一个人可以有几枚徽章） */
const selectedRecordIds = ref<any[]>([])
/** 「状态」「是否展示」两格各自正在等回话的行 id：开关转圈时把那一格按住，免得连点两下把服务端拨乱 */
const recordStatusLoadingId = ref<any>(null)
const recordShowLoadingId = ref<any>(null)
const recordQuery = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  badgeId: undefined,
  keyword: undefined,
  source: undefined,
  status: undefined
})

/* ---------------- 下发（这一页唯一的一条写入路） ---------------- */
const giveOpen = ref(false)
const giveLoading = ref(false)
const giveForm = reactive<any>({
  users: [] as any[],
  endTime: undefined
})

/**
 * 那颗用户选择器的多选值：勾选清单以 giveForm.users 为准，这里只做双向映射，
 * 所以 chips 上点 × 摘掉一个人，下次开弹窗就不会再被勾上
 */
const pickedUserIds = computed<Array<number | string>>({
  get: () => giveForm.users.map((item: any) => item.id),
  set: (ids) => {
    giveForm.users = giveForm.users.filter((item: any) => ids.some((id) => String(id) === String(item.id)))
  }
})

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// 查询参数
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  badgeType: undefined,
  status: undefined
})

// 表单参数
const form = reactive<any>({})

/** 下拉里 dict.options 给的 value 是字符串，比档位一律先 Number()，不然 '1' === 1 永远不成立 */
const needTarget = computed(() => {
  const type = Number(form.badgeType)
  return type === TYPE_STORE || type === TYPE_ACTIVITY
})

const isStoreTarget = computed(() => Number(form.badgeType) === TYPE_STORE)

const targetLabel = computed(() => (isStoreTarget.value ? '所属门店' : '所属活动'))

/**
 * 达标线那一格的名字跟着类型换：门店/消费比的是买过的件数，签到比的是累计天数，
 * 活动那一类本期没有自动判定（只走下发），那一格就说"参考数"
 */
const reachLabel = computed(() => {
  const type = Number(form.badgeType)
  if (type === TYPE_SIGNIN) return '累计签到天数'
  if (type === TYPE_ACTIVITY) return '达标数（本期不自动判）'
  return '购买件数'
})

const reachPlaceholder = computed(() => '留 0 就是这一枚只走手动下发')

/**
 * 前端这一道只钉"服务层一定会报"的几格，别比后端还严：达标线留 0 是合法的（只走手动下发），
 * 奖励积分和券都可以空着，所以必填只有名称、类型和图标三格
 */
const rules = computed<FormRules>(() => ({
  badgeName: [{ required: true, message: '徽章名称不能为空', trigger: 'blur' }],
  badgeType: [{ required: true, message: '请选择徽章类型：达标判定按这一档分叉', trigger: 'change' }],
  badgeIcon: [{ required: true, message: '请上传徽章图标：前台是按挂件展示的，只有文字的那一枚挂不起来', trigger: 'change' }],
  targetId: needTarget.value
    ? [{ required: true, message: `请选择${targetLabel.value}`, trigger: 'change' }]
    : []
}))

/* ---------------- 目标候选：门店/活动两张表，按类型换数据源 ---------------- */
const storeOptions = ref<any[]>([])
const activityOptions = ref<any[]>([])

/** 目标下拉的候选（形状统一成 {id, name}，上面那一格不用管现在是哪一类） */
const targetOptions = computed(() => {
  if (isStoreTarget.value) {
    return storeOptions.value.map((item: any) => ({ id: item.id, name: item.storeName }))
  }
  return activityOptions.value.map((item: any) => ({ id: item.id, name: item.activityName }))
})

const getStoreOptions = async () => {
  try {
    const { data } = await optionsShopStoreApi()
    storeOptions.value = data || []
  } catch (error) {
  }
}

/** 活动没有不分页的 options，取第一页够用的那一条（和这一页一样按权重倒序，常用的都在前面） */
const getActivityOptions = async () => {
  try {
    const { data } = await listShopActivityApi({ pageNum: 1, pageSize: 200 })
    activityOptions.value = data.records || []
  } catch (error) {
  }
}

/* ---------------- 奖励的券：候选是一页券，已选的是 form.couponIds 那一串 id ---------------- */
const couponOptions = ref<any[]>([])
const couponItems = ref<any[]>([])

const getCouponOptions = async () => {
  try {
    const { data } = await listShopCouponApi({ pageNum: 1, pageSize: 200 })
    couponOptions.value = data.records || []
  } catch (error) {
  }
}

/**
 * 下拉的多选值只做回显，真正的清单归 couponItems，form.couponIds 由它重新拼成逗号串。
 * 多选下拉交回来的是一批 id（不像商品/分类那种公共选择器给整行），所以这一道按候选补名字，
 * 候选里没有的（不在第一页那 200 张里）留空，chips 上回落成「券 #id」
 */
const pickedCouponIds = computed<Array<number | string>>({
  get: () => couponItems.value.map((item: any) => item.id),
  set: (ids) => {
    couponItems.value = (ids || []).map((id: any) => ({
      id,
      name: couponOptions.value.find((item: any) => String(item.id) === String(id))?.couponName || ''
    }))
    syncCouponIds()
  }
})

const syncCouponIds = () => {
  form.couponIds = couponItems.value.length ? couponItems.value.map((item: any) => item.id).join(',') : undefined
}

const removeCoupon = (id: any) => {
  couponItems.value = couponItems.value.filter((item: any) => String(item.id) !== String(id))
  syncCouponIds()
}

/**
 * 修改弹窗打开时把库里那串 id 补成带名字的 chips：券候选已经在手上就现查，
 * 没捞到（模板删了、或者不在第一页那 200 张里）就留空，chips 上回落成「券 #id」。
 * id 要转成数字：下拉候选那儿的 :value 是接口带回的 Number，el-select 回显是全等匹配，
 * 挂着一串字符串就点不上勾（chips 和 form.couponIds 都是按 String 比，不受影响）
 */
const loadCouponItems = async () => {
  couponItems.value = []
  const ids = String(form.couponIds || '').split(',').filter((text: string) => text !== '')
  if (!ids.length) return
  if (!couponOptions.value.length) await getCouponOptions()
  couponItems.value = ids.map((id: string) => ({
    id: Number(id),
    name: couponOptions.value.find((item: any) => String(item.id) === id)?.couponName || ''
  }))
}

/**
 * 换类型就把目标清掉：那一格按类型指向两张不同的表，
 * 从门店改成活动留着原来那家店的 id，落库就成了指向错表的一行
 */
const handleTypeChange = () => {
  form.targetId = undefined
  formRef.value?.clearValidate(['targetId'])
}

/** 目标那一格说人话：签到/消费两类本来就没有目标，按类型显示成「不限」 */
const targetText = (row: any) => {
  const type = Number(row.badgeType)
  if (type !== TYPE_STORE && type !== TYPE_ACTIVITY) return '不限'
  if (row.targetName) return row.targetName
  return row.targetId ? `${type === TYPE_STORE ? '已删除门店' : '已删除活动'} #${row.targetId}` : '没绑'
}

const reachTitle = (row: any) => {
  const type = Number(row.badgeType)
  if (type === TYPE_SIGNIN) return '累计签到天数'
  if (type === TYPE_ACTIVITY) return '达标数'
  return '购买件数'
}

/** 达标线那一格：留 0 的意思是"这一枚只走手动下发"，直接显示 0 会被读成"买一件就给" */
const reachText = (row: any) => {
  const reach = Number(row.reachCount) || 0
  if (reach <= 0) return '只走手动下发'
  const type = Number(row.badgeType)
  if (type === TYPE_SIGNIN) return `累计 ${reach} 天`
  if (type === TYPE_ACTIVITY) return `${reach}（不自动判）`
  return `买满 ${reach} 件`
}

/** 奖励那一格：积分和券各说一段，都没有就说"只有徽章"（那一档是合法配置，不是没填完） */
const rewardText = (row: any) => {
  const parts: string[] = []
  const points = Number(row.points) || 0
  if (points > 0) parts.push(`${points} 分`)
  const ids = String(row.couponIds || '').split(',').filter((text: string) => text !== '')
  if (ids.length) parts.push(`${ids.length} 张券`)
  return parts.length ? parts.join(' + ') : '只有徽章'
}

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listShopBadgeApi(queryParams)
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
    badgeName: undefined,
    badgeType: dict.defaultValue(DICT_TYPE, 1),
    badgeText: undefined,
    badgeIcon: undefined,
    priority: 0,
    targetId: undefined,
    reachCount: 0,
    points: 0,
    couponIds: undefined,
    status: 1,
    description: undefined
  })
  formRef.value?.resetFields()
  // 已选券那一排是本页自己攒的（库里只有 id 串），跟表单一起清；下拉里的勾选跟着这一排走
  couponItems.value = []
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
  queryParams.badgeType = undefined
  queryParams.status = undefined
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
}

/**
 * 三格候选都在打开弹窗前捞：门店是现成的 options，活动取第一页，券取第一页。
 * 返回 Promise 是给修改弹窗用的 —— 已选券那一排要靠候选把 id 补成名字，得等它回来
 */
const loadFormOptions = () => Promise.all([getStoreOptions(), getActivityOptions(), getCouponOptions()])

/** 新增按钮操作 */
const handleAdd = () => {
  reset()
  loadFormOptions()
  open.value = true
  title.value = '添加徽章'
}

/** 正在打开修改弹窗的那一枚徽章 id：详情 + 三格候选都要等，按钮转圈期间顺手挡住连点 */
const updatingId = ref<number | string>()

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  updatingId.value = row.id
  reset()
  try {
    const { data } = await detailShopBadgeApi(row.id)
    // 先等候选回来再补已选券的名字，否则那一排只能显示成「券 #id」
    await loadFormOptions()
    Object.assign(form, data)
    open.value = true
    title.value = '修改徽章'
    await loadCouponItems()
  } catch (error) {
  } finally {
    updatingId.value = undefined
  }
}

/** 详情按钮操作 */
const handleDetail = async (row: any) => {
  detail.value = {}
  detailOpen.value = true
  detailLoading.value = true
  try {
    const { data } = await detailShopBadgeApi(row.id)
    detail.value = data || {}
  } catch (error) {
  } finally {
    detailLoading.value = false
  }
}

/**
 * 「获取记录」：这一枚都在谁手上（一行一个人）。
 * 明细那一行只有发放、删除和改展示会动 —— 新增和整行修改不开（手工插一行等于凭空发一枚徽章，还顺带兑现了它上面的分和券），
 * 删除这一颗抹掉的是"这枚徽章在他手上"这条记录，当初兑现的积分和券留在各人的账上不动。
 */
const handleRecord = (row: any) => {
  recordBadge.value = row
  // 这一份明细只说列表点进来的这一枚：不带 badgeId 后端就不按徽章筛
  recordQuery.badgeId = row.id
  recordQuery.pageNum = 1
  // 换一枚看，筛选项要跟着清掉，否则上一枚筛的「只有手动发放」会跟过来
  recordQuery.keyword = undefined
  recordQuery.source = undefined
  recordQuery.status = undefined
  recordList.value = []
  recordTotal.value = 0
  selectedRecordIds.value = []
  recordOpen.value = true
  getRecordList()
}

const getRecordList = async () => {
  recordLoading.value = true
  try {
    const { data } = await listShopBadgeRecordApi(recordQuery)
    recordList.value = data.records || []
    recordTotal.value = data.total || 0
  } catch (error) {
  } finally {
    recordLoading.value = false
  }
}

/** 弹窗里换了筛选：回到第一页重数，不然停在第 5 页筛完只剩两行会看着像没数据 */
const handleRecordFilter = () => {
  recordQuery.pageNum = 1
  getRecordList()
}

const handleRecordSizeChange = () => {
  recordQuery.pageNum = 1
  getRecordList()
}

/** 记录里那一格用户名：账号被删了就联不上昵称，按 id 说清是谁，别说成"没查出来" */
const recordUserText = (row: any) => {
  if (row.userName) return row.userName
  return row.userId ? `用户 #${row.userId}` : '-'
}

/** 明细表格的勾选（只在本页内攒着：翻页就清空，跨页留勾会让"删掉看不见的那几行"变成一键就能点出来） */
const handleRecordSelection = (selection: { id: any }[]) => {
  selectedRecordIds.value = selection.map((item) => item.id)
}

/**
 * 删完之后的收尾：明细重捞、顶上「已有 N 人」跟着列表那一行刷新（那一格是 SQL 现数出来的，不是库里的列）。
 * 整页都删空了就退回上一页再捞一次，不然停在第 3 页看到空表会读成"这一枚没记录了"
 */
const reloadRecord = async () => {
  selectedRecordIds.value = []
  await getList()
  refreshRecordBadge()
  await getRecordList()
  if (recordList.value.length === 0 && recordQuery.pageNum > 1) {
    recordQuery.pageNum -= 1
    await getRecordList()
  }
}

/** 删除一条获取记录：把这一枚从这个人名下抹掉（分和券不收回，弹窗里说清楚） */
const handleRecordDelete = (row: any) => {
  ElMessageBox.confirm(
    `确认删除「${recordUserText(row)}」手上的这一枚徽章记录吗？当初随徽章一起发出的积分和优惠券不会收回。`,
    '提示',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    await deleteShopBadgeRecordApi(row.id)
    ElMessage.success('删除成功')
    await reloadRecord()
  }).catch(() => { })
}

/** 批量删除勾选中的那些记录 */
const handleRecordBatchDelete = () => {
  ElMessageBox.confirm(
    `确认删除选中的 ${selectedRecordIds.value.length} 条获取记录吗？当初随徽章一起发出的积分和优惠券不会收回。`,
    '提示',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    await deleteShopBadgeRecordApi(selectedRecordIds.value)
    ElMessage.success('删除成功')
    await reloadRecord()
  }).catch(() => { })
}

/**
 * 改一条记录的「状态」：一改就提交（不重捞列表，勾选中的那几行不能跟着清掉），
 * 报错就把开关拨回原来那一档。服务端只动 status，积分和券一律不碰、这一行也不删
 */
const handleRecordStatusChange = async (row: any) => {
  recordStatusLoadingId.value = row.id
  try {
    await updateShopBadgeRecordStatusApi(row.id, row.status)
    ElMessage.success('修改成功')
  } catch (error) {
    row.status = row.status === 1 ? 0 : 1
  } finally {
    recordStatusLoadingId.value = null
  }
}

/**
 * 改一条记录的「是否展示」：一改就提交（不重捞列表，勾选中的那几行不能跟着清掉），
 * 报错就把开关拨回原来那一档。服务端只动 is_show，积分和券一律不碰
 */
const handleRecordShowChange = async (row: any) => {
  recordShowLoadingId.value = row.id
  try {
    await updateShopBadgeRecordShowApi(row.id, row.isShow)
    ElMessage.success('修改成功')
  } catch (error) {
    row.isShow = row.isShow === 1 ? 0 : 1
  } finally {
    recordShowLoadingId.value = null
  }
}

/**
 * 「下发」：弹窗顶上那颗按钮。用户选择器这一格是多选的（一次在弹窗里勾一批、确定交回整批），
 * 交回来的整批直接替掉 users。已经持有这一枚的人后端会跳过，所以返回值是实发人数
 */
const handleGive = () => {
  giveForm.users = []
  giveForm.endTime = undefined
  giveOpen.value = true
}

const handleGiveSelect = (rows: any[]) => {
  giveForm.users = (rows || []).map((user: any) => ({ id: user.id, nickname: user.nickname || user.username }))
}

const removeGiveUser = (id: any) => {
  giveForm.users = giveForm.users.filter((item: any) => String(item.id) !== String(id))
}

/** 提交下发：一颗「确定」在没选人时是禁用状态，所以这一路不需要再走一遍表单校验 */
const submitGive = async () => {
  if (giveForm.users.length === 0) return
  giveLoading.value = true
  try {
    const { data } = await giveShopBadgeApi({
      badgeId: recordBadge.value.id,
      userIds: giveForm.users.map((item: any) => item.id),
      endTime: giveForm.endTime
    })
    ElMessage.success(`已下发给 ${data} 人`)
    giveOpen.value = false
    // 已获得人数是列表那一行现数出来带回的，所以要先重查列表、再按新的那一行刷新弹窗和明细
    await getList()
    refreshRecordBadge()
    getRecordList()
  } catch (error) {
  } finally {
    giveLoading.value = false
  }
}

/** 发完之后把弹窗顶上那行汇总换成列表里刚查回来的那一行（同一个数，只是新值） */
const refreshRecordBadge = () => {
  const fresh = dataList.value.find((item: any) => item.id === recordBadge.value.id)
  if (fresh) {
    recordBadge.value = fresh
  }
}

/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateShopBadgeApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopBadgeApi(form)
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
  ElMessageBox.confirm(`确认删除「${row.badgeName}」这枚徽章吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopBadgeApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 枚徽章吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopBadgeApi(selectedIds.value)
    ElMessage.success('删除成功')
    selectedIds.value = []
    getList()
  }).catch(() => { })
}

/**
 * 操作列按钮分发（TableMoreActions 只抛一个 command 事件，动作认 action.command.type，同优惠券那页）
 */
const handleActionCommand = (action: any) => {
  const { type, row } = action.command
  switch (type) {
    case 'detail':
      handleDetail(row)
      break
    case 'record':
      handleRecord(row)
      break
    case 'update':
      handleUpdate(row)
      break
    case 'delete':
      handleDelete(row)
      break
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

/* 徽章那一格的形状收在 components/BadgeView 那一处，这一页不再自己写 */

/* 获取记录弹窗里表格上面那一排：汇总在左、三个筛选和「下发」那颗按钮在右（同一排，表格自带分页在下面） */
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

  /* 这两格不跟着整行撑满：160px 够放"全部状态"和几个选项名，180px 够放一个手机号 */
  .record-filter {
    width: 160px;
    flex: 0 0 auto;
  }

  .record-keyword {
    width: 180px;
    flex: 0 0 auto;
  }

  .record-give {
    flex: 0 0 auto;
  }
}

/* 已选那一排（下发的用户、表单里的奖励券）：一次勾一批，攒出来的条目在这儿一行行铺开，点多长都行 */
.user-chips,
.scope-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  width: 100%;

  .el-tag {
    margin: 0;
  }
}

.chips-empty {
  color: var(--el-text-color-placeholder);
}

/* 提交前那行说明（发几个人、兑现什么）：颜色压一档，别和必填报错抢 */
.give-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
