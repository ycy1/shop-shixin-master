<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="名称/标题" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="活动位" prop="positionType">
          <el-select v-model="queryParams.positionType" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_POSITION)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="活动类型" prop="activityType">
          <el-select v-model="queryParams.activityType" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_ACTIVITY_TYPE)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_STATUS)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>

        <!-- 时间范围 + 按钮同一行（照抄门店培训、门店招商的 .search-row）：这一层 wrapper 独占第二行，
             里面两格都不等分、按控件本身的宽度从左排（样式见文件末尾） -->
        <div class="search-row">
          <el-form-item label="投放时间" prop="dateRange">
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
          <el-button v-permission="['activity:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['activity:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <!-- 表格级开了 show-overflow-tooltip，纯图片列要显式关掉，否则悬停会弹一个空 tooltip -->
        <el-table-column label="活动图片" align="center" width="90" :show-overflow-tooltip="false">
          <template #default="{ row }">
            <el-image v-if="row.image" :src="row.image" :preview-src-list="[row.image]" preview-teleported
              fit="cover" style="width: 50px; height: 50px; border-radius: 4px" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="活动名称" prop="activityName" align="center" min-width="150" />
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_STATUS, row.status)">
              {{ dict.label(DICT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="活动位" align="center" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_POSITION, row.positionType)">
              {{ dict.label(DICT_POSITION, row.positionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="活动类型" align="center" width="90">
          <template #default="{ row }">{{ dict.label(DICT_ACTIVITY_TYPE, row.activityType) }}</template>
        </el-table-column>
        <el-table-column label="适用门店" align="center" min-width="120">
          <template #default="{ row }">
            <!-- 这一格可空和门店培训不一样：空着是"全平台都投"，不是漏填，所以显示成「全平台」而不是「未指定」。
                 删掉的门店联不上名，按 id 提醒人去门店那页看 -->
            <span>{{ row.storeName || (row.storeId ? `已删除门店 #${row.storeId}` : '全平台') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="跳转" align="center" min-width="140">
          <template #default="{ row }">{{ linkText(row) }}</template>
        </el-table-column>
        <el-table-column label="投放时间" align="center" width="150">
          <template #default="{ row }">{{ rangeText(row.startTime, row.endTime) }}</template>
        </el-table-column>
        

        <el-table-column label="操作" align="center" width="220" fixed="right">
          <template #default="{ row }">
            <!-- 四颗按钮由 TableMoreActions 收成"直出两颗 + 更多"（口径同门店培训、门店招商）。
                 组件收的是 actions 数组、用不上 v-permission 那个指令，权限在 hidden 里自己判；
                 "只有没投放过的能删"是服务层的判据，这里不预告（不给置灰的按钮） -->
            <TableMoreActions :actions="[
              { label: '详情', icon: 'View', hidden: !hasPermission('activity:detail'), command: { type: 'detail', row } },
              { label: '查看记录', icon: 'Tickets', hidden: !hasPermission('activity:record'), command: { type: 'record', row } },
              { label: '修改', icon: 'Edit', hidden: !hasPermission('activity:update'), command: { type: 'update', row } },
              { label: '删除', type: 'danger', icon: 'Delete', hidden: !hasPermission('activity:delete'), command: { type: 'delete', row } }
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
    <el-dialog v-model="open" :title="title" width="1000px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="活动名称" prop="activityName">
              <el-input v-model="form.activityName" placeholder="请输入活动名称" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="活动位" prop="positionType">
              <el-select v-model="form.positionType" placeholder="请选择" style="width: 100%">
                <el-option v-for="item in dict.options(DICT_POSITION)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="活动类型" prop="activityType">
              <el-select v-model="form.activityType" placeholder="请选择" style="width: 100%">
                <el-option v-for="item in dict.options(DICT_ACTIVITY_TYPE)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适用门店" prop="storeId">
              <!-- 可空（和门店培训那个必填的所属门店是两回事）：不选就是全平台都投这一张，所以给清除按钮 -->
              <el-select v-model="form.storeId" placeholder="不选即全平台" filterable clearable style="width: 100%">
                <el-option v-for="item in storeOptions" :key="item.id" :label="item.storeName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="权重" prop="weight">
              <el-input-number v-model="form.weight" :min="0" :controls="false" placeholder="数字越大越靠前"
                style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="活动标题" prop="title">
              <el-input v-model="form.title" placeholder="显示在活动上的文案，选填" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="活动图片" prop="image">
              <upload-image v-model="form.image" :size="120" source="activity" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="跳转类型" prop="linkType">
              <el-select v-model="form.linkType" placeholder="请选择" style="width: 100%" @change="handleLinkTypeChange">
                <el-option v-for="item in dict.options(DICT_LINK_TYPE)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="跳转值" prop="linkValue">
              <!-- 这一格存的是什么由跳转类型决定，控件也跟着类型换：商品/分类直接复用那两个公共选择器
                   （口径同商品页），外链（4）和小程序页面（5）本来就是手填，走文本框 -->
              <shop-product-select v-if="form.linkType === LINK_PRODUCT" v-model="form.linkValue" :label="linkLabel"
                placeholder="选择商品" @select="handleProductSelect" />
              <shop-category-select v-else-if="form.linkType === LINK_CATEGORY" v-model="form.linkValue"
                :data="categoryOptions" placeholder="选择分类" @change="handleCategoryChange" />
              <el-input v-else-if="form.linkType !== LINK_NONE" v-model="form.linkValue"
                :placeholder="linkValuePlaceholder" maxlength="500" />
              <span v-else class="link-none-tip">无跳转，这一格留空</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="投放开始" prop="startTime">
              <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="不填即不限" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="投放结束" prop="endTime">
              <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="不填即不限" style="width: 100%" />
            </el-form-item>
          </el-col>
          <!-- 状态只有修改时才给选：新增一律落成 1 未开始（服务层 insert 里钉死），
               给一个选了的值也会被覆盖，不如不出现 -->
          <el-col v-if="form.id !== undefined" :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择" style="width: 100%">
                <el-option v-for="item in dict.options(DICT_STATUS)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="视频URL" prop="videoUrl">
              <!-- 活动类型选视频时才有用，跟签约文件那一格一样：手填地址 + 一键验货 -->
              <div class="pair-field">
                <el-input v-model="form.videoUrl" placeholder="https://….mp4" maxlength="500" />
                <el-link v-if="form.videoUrl" type="primary" :href="form.videoUrl" target="_blank" class="video-view">
                  查看
                </el-link>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="活动内容" prop="content">
              <!-- 富文本（同公告/消息通知那两页）：文字活动排的那段东西、图片活动的补充说明，库里存编辑器出来的 HTML -->
              <wang-editor v-model="form.content" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="内部备注、投放约定等" maxlength="500"
                show-word-limit />
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

    <!-- 详情：只读看这条活动的全部登记信息（口径同门店培训、门店招商那两页的详情弹窗），要改内容走「修改」 -->
    <el-dialog v-model="detailOpen" title="活动详情" width="900px" append-to-body destroy-on-close>
      <el-descriptions :column="2" border v-loading="detailLoading">
        <el-descriptions-item label="活动名称" width="150px">{{ detail.activityName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="dict.tag(DICT_STATUS, detail.status)">
            {{ dict.label(DICT_STATUS, detail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="活动位">
          <el-tag size="small" :type="dict.tag(DICT_POSITION, detail.positionType)">
            {{ dict.label(DICT_POSITION, detail.positionType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="活动类型">{{ dict.label(DICT_ACTIVITY_TYPE, detail.activityType) }}</el-descriptions-item>
        <el-descriptions-item label="适用门店">
          {{ detail.storeName || (detail.storeId ? `已删除门店 #${detail.storeId}` : '全平台') }}
        </el-descriptions-item>
        <el-descriptions-item label="活动标题">{{ detail.title || '-' }}</el-descriptions-item>
        <el-descriptions-item label="权重">{{ detail.weight ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="活动图片" :span="2">
          <el-image v-if="detail.image" :src="detail.image" :preview-src-list="[detail.image]" preview-teleported
            fit="contain" style="width: 160px; height: 90px; border-radius: 4px" />
          <span v-else>未上传</span>
        </el-descriptions-item>
        <el-descriptions-item label="视频" :span="2">
          <el-link v-if="detail.videoUrl" type="primary" :href="detail.videoUrl" target="_blank">
            {{ detail.videoUrl }}
          </el-link>
          <span v-else>无</span>
        </el-descriptions-item>
        <el-descriptions-item label="跳转" :span="2">
          {{ linkText(detail) }}
        </el-descriptions-item>
        <el-descriptions-item label="投放开始">{{ detail.startTime || '不限' }}</el-descriptions-item>
        <el-descriptions-item label="投放结束">{{ detail.endTime || '不限' }}</el-descriptions-item>
        <el-descriptions-item label="投放时间">{{ detail.launchTime || '还没投放过' }}</el-descriptions-item>
        <el-descriptions-item label="曝光量">{{ detail.viewCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="点击量">{{ detail.clickCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="点击率">{{ rateText(detail.clickRate) }}</el-descriptions-item>
        <el-descriptions-item label="活动内容" :span="2">
          <!-- 这一格现在是富文本（表单里用的 WangEditor），存的就是编辑器出来的 HTML，所以按公告那页的写法 v-html 渲染 -->
          <div v-if="detail.content" class="rich-content" v-html="detail.content"></div>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="修改时间">{{ detail.updateTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 查看记录：这一条活动的曝光/点击流水（一次一行，纯流水没有修改/删除），写法照商品库存那页的「库存日志」。
         顶上那行汇总是列表那一行带回的三个数（和明细同一张表现算，所以对得上） -->
    <el-dialog v-model="recordOpen" :title="`投放记录 - ${recordActivity.activityName || ''}`" width="980px" append-to-body
      destroy-on-close>
      <div class="record-toolbar">
        <span class="record-summary">
          曝光 {{ recordActivity.viewCount || 0 }} / 点击 {{ recordActivity.clickCount || 0 }} / 点击率
          {{ rateText(recordActivity.clickRate) }}
        </span>
        <el-select v-model="recordQuery.eventType" placeholder="全部事件" clearable class="record-filter"
          @change="handleRecordFilter">
          <el-option v-for="item in dict.options(DICT_EVENT)" :key="item.value" :label="item.label"
            :value="item.value" />
        </el-select>
      </div>

      <el-table :data="recordList" size="small" border v-loading="recordLoading" show-overflow-tooltip
        empty-text="这一条活动还没有曝光/点击记录">
        <el-table-column label="时间" align="center" width="170">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.createTime) || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="事件类型" align="center" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_EVENT, row.eventType)">
              {{ dict.label(DICT_EVENT, row.eventType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="用户" align="center" min-width="150">
          <template #default="{ row }">{{ recordUserText(row) }}</template>
        </el-table-column>
        <el-table-column label="IP" align="center" min-width="140">
          <template #default="{ row }">{{ row.ip || '-' }}</template>
        </el-table-column>
        <el-table-column label="IP来源" align="center" min-width="140">
          <template #default="{ row }">{{ row.ipLocation || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作系统" align="center" width="110">
          <template #default="{ row }">{{ row.os || '-' }}</template>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listShopActivityApi,
  detailShopActivityApi,
  addShopActivityApi,
  updateShopActivityApi,
  deleteShopActivityApi,
  listShopActivityRecordApi
} from '@/api/activity/info'
import { optionsShopStoreApi } from '@/api/store/store'
import { getCategoryTreeApi } from '@/api/shop/category'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import UploadImage from '@/components/Upload/Image.vue'
import TableMoreActions from '@/components/TableMoreActions/index.vue'
import ShopProductSelect from '@/components/ShopProductSelect/index.vue'
import ShopCategorySelect from '@/components/ShopCategorySelect/index.vue'
import WangEditor from '@/components/WangEditor/index.vue'
import validate from '@/utils/validate'
import { useDict } from '@/utils/dictCache'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
/** 操作列走 TableMoreActions，权限得在这里自己判（组件没有 v-permission 那套指令，只认 actions 上的 hidden） */
const permissions = computed(() => userStore.user.permissions || [])
const hasPermission = (permission: string): boolean => {
  return permissions.value.includes(permission)
}

const DICT_POSITION = 'position_type'
const DICT_ACTIVITY_TYPE = 'activity_type'
const DICT_LINK_TYPE = 'link_type'
const DICT_STATUS = 'activity_status'
/** 「查看记录」弹窗里那一格事件类型（1曝光 2点击），筛选项和列表列都用它 */
const DICT_EVENT = 'activity_event_type'

const dict = useDict(DICT_POSITION, DICT_ACTIVITY_TYPE, DICT_LINK_TYPE, DICT_STATUS, DICT_EVENT)

/**
 * 跳转类型里需要"挑一个东西"的那几档：商品和分类有表可查，就换成公共选择器，免得让人记 id；
 * 外链（4）和小程序页面（5）本来就是手填，走文本框（3 那一档是「跳本表另一条活动」，已经下线了）
 */
const LINK_PRODUCT = 1
const LINK_CATEGORY = 2
const LINK_NONE = 6
const LINK_VALUE_PLACEHOLDERS: Record<number, string> = {
  4: 'https://…（外部链接）',
  5: '/pages/…（小程序页面路径）'
}

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
const dateRange = ref<string[]>([])

/* ---------------- 查看记录（曝光/点击流水）：列表页内的只读弹窗，口径同商品库存那页的「库存日志」 ---------------- */
const recordOpen = ref(false)
const recordLoading = ref(false)
const recordList = ref<any[]>([])
const recordTotal = ref(0)
/** 弹窗标题带上活动名；顶上那行汇总（曝光/点击/点击率）用的是列表那一行已经带回的三个数，不再发一次请求 */
const recordActivity = ref<any>({})
const recordQuery = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  activityId: undefined,
  eventType: undefined
})

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// 查询参数
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  positionType: undefined,
  activityType: undefined,
  status: undefined
})

// 表单参数
const form = reactive<any>({})

/**
 * 跳转值要不要必填跟着跳转类型走（「无跳转」那一档留空才是对的），判据和服务层那一道一模一样。
 * 所以它写成 computed：换一次跳转类型就得重算，不能是一次性的常量
 */
const rules = computed<FormRules>(() => ({
  activityName: [{ required: true, message: '活动名称不能为空', trigger: 'blur' }],
  positionType: [{ required: true, message: '请选择活动位', trigger: 'change' }],
  activityType: [{ required: true, message: '请选择活动类型', trigger: 'change' }],
  linkType: [{ required: true, message: '请选择跳转类型', trigger: 'change' }],
  // 这一格换了控件（商品/分类是弹窗和树选择器），光靠 blur 触发不到，change 也钉上
  linkValue: form.linkType === LINK_NONE ? [] : [{ required: true, message: '跳转值不能为空', trigger: ['blur', 'change'] }],
  // 两头时间的先后判据在服务层也钉了一道；这两格都不是必填项（"还没定排期"是常态）
  startTime: [{
    validator: (_rule: any, _value: any, callback: any) => {
      if (form.startTime && form.endTime && form.endTime < form.startTime) {
        callback(new Error('投放结束时间不能早于开始时间'))
      } else {
        callback()
      }
    },
    trigger: 'change'
  }]
}))

/** 门店候选：表单那一格「适用门店」用（options 返回启用的门店，口径同门店培训的所属门店） */
const storeOptions = ref<any[]>([])

const getStoreOptions = async () => {
  try {
    const { data } = await optionsShopStoreApi()
    storeOptions.value = data || []
  } catch (error) {
  }
}

/**
 * 商品选择器自己不查名称（编辑回显靠外部传的 label），所以这里留一格名字：
 * 修改弹窗打开时用的是后端联表带回来的 linkName，商品删掉了才退回「商品 #id」
 */
const linkLabel = ref('')

const handleProductSelect = (product: any) => {
  form.linkValue = String(product.id)
  linkLabel.value = product.productName || ''
}

/** 分类候选：跳转类型选「分类」时那一格换成分类树（口径同商品页的所属分类） */
const categoryOptions = ref<any[]>([])

const getCategoryOptions = async () => {
  try {
    const { data } = await getCategoryTreeApi({})
    categoryOptions.value = data || []
  } catch (error) {
  }
}

/** 这一列是字符串，分类树选回来的是数字 id，存之前统一成字符串 */
const handleCategoryChange = (id: any) => {
  form.linkValue = id === undefined || id === null || id === '' ? undefined : String(id)
}

const linkValuePlaceholder = computed(() => LINK_VALUE_PLACEHOLDERS[Number(form.linkType)] || '请输入跳转值')

/**
 * 换了跳转类型就把上一档留下的跳转值清掉：
 * 商品 id 留在外链那一格里，前台点了就是跳到一个坏地址，比空着更难查
 */
const handleLinkTypeChange = () => {
  form.linkValue = undefined
  linkLabel.value = ''
}

/**
 * 列表/详情那一格「跳转」：类型名 + 跳转目标，无跳转就是"不跳转"。
 * 商品和分类那一格库里只有 id，名字是后端联表现取回来的（linkName，不在这张表里冗余快照），
 * 目标被删了就联不上，按 id 提醒人去商品/分类那两页看 —— 同「适用门店」那一格的兜底写法
 */
const linkText = (row: any) => {
  if (row.linkType === LINK_NONE) return '不跳转'
  const type = dict.label(DICT_LINK_TYPE, row.linkType)
  return [type, linkTargetText(row)].filter(Boolean).join(' → ') || '-'
}

const linkTargetText = (row: any) => {
  if (!row.linkValue) return ''
  if (row.linkType === LINK_PRODUCT) return row.linkName || `已删除商品 #${row.linkValue}`
  if (row.linkType === LINK_CATEGORY) return row.linkName || `已删除分类 #${row.linkValue}`
  // 外链和小程序页面这两档，那一格里存的本来就是要显示给人看的内容
  return row.linkValue
}

/** 点击率后端给的是小数（0.25），这一格显示成百分比；除不出来的（没曝光）给 '-'，不显示 0% 骗人 */
const rateText = (rate: any) => (rate == null || rate === '' ? '-' : `${(Number(rate) * 100).toFixed(2)}%`)

/** 投放时间那一格：开始给全、结束只给到日，一格放不下两个完整时间戳 */
const rangeText = (start: any, end: any) => {
  const s = validate.formatTime(start, 'YYYY-MM-DD HH:mm')
  const e = validate.formatTime(end, 'MM-DD HH:mm')
  return [s, e].filter(Boolean).join(' ~ ') || '不限'
}

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const params: any = { ...queryParams }
    if (dateRange.value && dateRange.value.length === 2) {
      // 筛的是 start_time（投放开始时间）；这两个查询字段不叫 beginTime/endTime，
      // 是因为后端实体上 endTime 已经是"投放结束时间"那一列，撞了名
      params.startFrom = `${dateRange.value[0]} 00:00:00`
      params.startTo = `${dateRange.value[1]} 23:59:59`
    }
    const { data } = await listShopActivityApi(params)
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
    activityName: undefined,
    positionType: dict.defaultValue(DICT_POSITION, 1),
    activityType: dict.defaultValue(DICT_ACTIVITY_TYPE, 1),
    storeId: undefined,
    storeName: undefined,
    title: undefined,
    image: undefined,
    videoUrl: undefined,
    content: undefined,
    linkType: 1,
    linkValue: undefined,
    startTime: undefined,
    endTime: undefined,
    weight: 0,
    viewCount: undefined,
    clickCount: undefined,
    clickRate: undefined,
    status: 1,
    launchTime: undefined,
    remark: undefined
  })
  formRef.value?.resetFields()
  // 跳转值那一格显示的名字是本页缓存的（库里只存 id），跟着表单一起清
  linkLabel.value = ''
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
  queryParams.positionType = undefined
  queryParams.activityType = undefined
  queryParams.status = undefined
  dateRange.value = []
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
}

/** 两格候选要在打开弹窗前捞：门店那一格直接下拉，分类那一格是分类树（商品的选择器自己懒加载） */
const loadFormOptions = () => {
  getStoreOptions()
  getCategoryOptions()
}

/** 新增按钮操作 */
const handleAdd = () => {
  reset()
  loadFormOptions()
  open.value = true
  title.value = '添加活动'
}

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailShopActivityApi(row.id)
    loadFormOptions()
    Object.assign(form, data)
    // 商品那一格的选择器自己不查名字，label 由这里给：后端联表带回的就是真名，商品被删了才退回 id
    if (form.linkType === LINK_PRODUCT && form.linkValue) {
      linkLabel.value = data.linkName || `商品 #${form.linkValue}`
    }
    open.value = true
    title.value = '修改活动'
  } catch (error) {
  }
}

/** 详情按钮操作 */
const handleDetail = async (row: any) => {
  detail.value = {}
  detailOpen.value = true
  detailLoading.value = true
  try {
    const { data } = await detailShopActivityApi(row.id)
    detail.value = data || {}
  } catch (error) {
  } finally {
    detailLoading.value = false
  }
}

/**
 * 「查看记录」：这一条活动的曝光/点击流水（一次一行，只读）。
 * 明细由前台埋点写进 shop_activity_click（POST /api/comm/activity/track，曝光和点击共用那一条），
 * 这一页不开新增/删除 —— 手工插一行就能把列表那三格统计编出来。
 * 汇总那三个数直接用列表行已经带回的（同一条 SQL 算出来的），不再发一次请求。
 */
const handleRecord = (row: any) => {
  recordActivity.value = row
  recordQuery.pageNum = 1
  // 换一条活动看，事件类型的筛选项要跟着清掉，否则上一条筛的「只有点击」会跟过来
  recordQuery.eventType = undefined
  recordList.value = []
  recordTotal.value = 0
  recordOpen.value = true
  getRecordList()
}

const getRecordList = async () => {
  recordLoading.value = true
  try {
    const { data } = await listShopActivityRecordApi(recordQuery)
    recordList.value = data.records || []
    recordTotal.value = data.total || 0
  } catch (error) {
  } finally {
    recordLoading.value = false
  }
}

/** 弹窗里换了事件类型：回到第一页重数，不然停在第 5 页筛完只剩两行会看着像没数据 */
const handleRecordFilter = () => {
  recordQuery.pageNum = 1
  getRecordList()
}

const handleRecordSizeChange = () => {
  recordQuery.pageNum = 1
  getRecordList()
}

/** 明细里那一格用户名：未登录的游客没有昵称（库里 user_id 就是 NULL），说清是游客而不是"没查出来" */
const recordUserText = (row: any) => {
  if (row.userName) return row.userName
  return row.userId ? `用户 #${row.userId}` : '未登录（游客）'
}

/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateShopActivityApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopActivityApi(form)
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
  ElMessageBox.confirm(`确认删除「${row.activityName}」这条活动吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopActivityApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 条活动吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopActivityApi(selectedIds.value)
    ElMessage.success('删除成功')
    selectedIds.value = []
    getList()
  }).catch(() => { })
}

/**
 * 操作列按钮分发（TableMoreActions 只抛一个 command 事件，动作认 action.command.type，同门店培训那页）
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

/* 时间范围那一行（照抄门店培训、门店招商的 .search-row）：wrapper 独占整行把日期格顶到第二行，
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

/* 一段拆成两格的字段（视频URL 那格：输入框 + 查看）：里面各块按自身宽度从左排 */
.pair-field {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;

  :deep(.el-input) {
    flex: 1;
    min-width: 0;
  }

  .video-view {
    flex: 0 0 auto;
  }
}

/* 「无跳转」那一档跳转值整格换成这行说明：比一个禁用输入框好读 */
.link-none-tip {
  color: var(--el-text-color-secondary);
}

/* 查看记录弹窗里表格上面那一排：汇总在左、事件类型筛选在右（同一排，表格自带分页在下面） */
.record-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;

  .record-summary {
    color: var(--el-text-color-secondary);
  }

  /* 这一格不跟着整行撑满：160px 够放"全部事件"和两个选项名 */
  .record-filter {
    width: 160px;
    flex: 0 0 auto;
  }
}

/* 详情弹窗里的活动内容（富文本回显）：编辑器插的图按弹窗宽度收住，别把 descriptions 撑破 */
.rich-content {
  line-height: 1.6;

  :deep(img) {
    max-width: 100%;
  }

  :deep(p) {
    margin: 0 0 8px;
  }
}
</style>
