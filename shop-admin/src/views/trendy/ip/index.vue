<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="IP名称" prop="ipName">
          <el-input v-model="queryParams.ipName" placeholder="请输入IP名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="授权类型" prop="licenseType">
          <el-select v-model="queryParams.licenseType" placeholder="全部" clearable>
            <el-option v-for="item in dict.options('license_type')" :key="item.value" :label="item.label"
              :value="Number(item.value)" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否热门" prop="isHot">
          <el-select v-model="queryParams.isHot" placeholder="全部" clearable>
            <el-option label="是" :value="1" />
            <el-option label="否" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options('trendy_ip_status')" :key="item.value" :label="item.label"
              :value="Number(item.value)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-card class="box-card">
      <template #header>
        <ButtonGroup>
          <el-button v-permission="['shop:trendyIp:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['shop:trendyIp:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="Logo" align="center" width="80">
          <template #default="{ row }">
            <el-image v-if="row.ipLogo" :src="row.ipLogo" :preview-src-list="[row.ipLogo]" preview-teleported
              fit="contain" style="width: 40px; height: 40px; border-radius: 4px" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="封面" align="center" width="90">
          <template #default="{ row }">
            <el-image v-if="row.ipCover" :src="row.ipCover" :preview-src-list="[row.ipCover]" preview-teleported
              fit="cover" style="width: 50px; height: 50px; border-radius: 4px" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="IP名称" prop="ipName" align="center" min-width="140" />
        <el-table-column label="英文名" align="center" min-width="120">
          <template #default="{ row }">
            <span>{{ row.ipEnName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="版权人/授权人" align="center" min-width="140">
          <template #default="{ row }">
            <!-- 这一格是挂 sys_user.id 的人，到期提醒就发给他；没填就是这条授权没人收提醒 -->
            <span>{{ row.userName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="授权类型" align="center" width="110">
          <template #default="{ row }">
            <el-tag :type="dict.tag('license_type', row.licenseType)" size="small">
              {{ dict.label('license_type', row.licenseType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="授权期限" align="center" width="200">
          <template #default="{ row }">
            <span>{{ licenseText(row) }}</span>
            <!-- 到期是运营要追的事，这里只标出来，不改状态那一列（状态是启用/禁用，两回事） -->
            <el-tag v-if="isExpired(row)" type="danger" size="small" class="ml6">已到期</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="热门" align="center" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.isHot === 1" type="warning" size="small">热门</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="推荐" align="center" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.isRecommend === 1" type="success" size="small">推荐</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="系列数" align="center" width="90">
          <template #default="{ row }">
            <span>{{ row.trendyCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag :type="dict.tag('trendy_ip_status', row.status)" size="small">
              {{ dict.label('trendy_ip_status', row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" align="center" width="110">
          <template #default="{ row }">
            <span>{{ validate.formatTime(row.createTime, 'YYYY-MM-DD') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="260" fixed="right">
          <template #default="{ row }">
            <!-- 公共「更多」组件。这颗「绑定系列」不像系列那页可以按计数收掉：系列表单上没有「所属IP」那一格，
                 这里就是绑定的唯一入口，一个都没绑时更得点得进去（只有没改权限的人不给看） -->
            <TableMoreActions :actions="[
              { label: '绑定系列', icon: 'Connection', hidden: !hasPermission('shop:trendyIp:update'), command: { type: 'trendies', row } },
              { label: '修改', icon: 'Edit', hidden: !hasPermission('shop:trendyIp:update'), command: { type: 'update', row } },
              { label: '删除', type: 'danger', icon: 'Delete', hidden: !hasPermission('shop:trendyIp:delete'), command: { type: 'delete', row } }
            ]" @command="handleActionCommand" />
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination background v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 30, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>

      <!-- IP 本身的新增/修改 -->
      <el-dialog v-model="open" :title="title" width="900px" append-to-body destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="IP名称" prop="ipName">
                <el-input v-model="form.ipName" placeholder="如 Molly、Dimoo" maxlength="100" show-word-limit />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="英文名" prop="ipEnName">
                <el-input v-model="form.ipEnName" placeholder="选填" maxlength="100" show-word-limit />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="IP Logo" prop="ipLogo">
                <upload-image v-model="form.ipLogo" :size="120" source="trendyIp" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="IP封面" prop="ipCover">
                <upload-image v-model="form.ipCover" :size="120" source="trendyIp" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="轮播图" prop="ipBanner">
            <!-- json 列，v-model 直接吃 [{"title":"","url":""}] 这段文本；清空时组件交 "[]" 而不是空串（json 列不收空字符串） -->
            <image-list v-model="form.ipBanner" :limit="9" source="trendyIp" />
          </el-form-item>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="授权类型" prop="licenseType">
                <el-select v-model="form.licenseType" placeholder="请选择" style="width: 100%">
                  <el-option v-for="item in dict.options('license_type')" :key="item.value" :label="item.label"
                    :value="Number(item.value)" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态" prop="status">
                <el-radio-group v-model="form.status">
                  <el-radio v-for="item in dict.options('trendy_ip_status')" :key="item.value"
                    :value="Number(item.value)">{{ item.label }}</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="版权人/授权人" prop="userId">
                <user-select v-model="form.userId" :label="form.userName" placeholder="选填，到期提醒发给他" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="版权登记号" prop="copyrightNo">
                <el-input v-model="form.copyrightNo" placeholder="选填" maxlength="64" show-word-limit />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="授权开始" prop="licenseStart">
                <el-date-picker v-model="form.licenseStart" type="date" value-format="YYYY-MM-DD" placeholder="选填"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="授权结束" prop="licenseEnd">
                <el-date-picker v-model="form.licenseEnd" type="date" value-format="YYYY-MM-DD" placeholder="选填"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="热门IP" prop="isHot">
                <el-switch v-model="form.isHot" :active-value="1" :inactive-value="0" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="推荐" prop="isRecommend">
                <el-switch v-model="form.isRecommend" :active-value="1" :inactive-value="0" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="排序" prop="sort">
                <el-input-number v-model="form.sort" :min="0" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="IP介绍" prop="ipDesc">
            <el-input v-model="form.ipDesc" type="textarea" :rows="4" placeholder="选填" maxlength="2000"
              show-word-limit />
          </el-form-item>
          <el-form-item label="故事背景" prop="ipStory">
            <el-input v-model="form.ipStory" type="textarea" :rows="4" placeholder="选填" maxlength="2000"
              show-word-limit />
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="cancel">取 消</el-button>
            <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
          </div>
        </template>
      </el-dialog>

      <!-- 绑定系列：这一层只列「已绑到这个IP」的系列，操作栏只给解绑；
           要加绑从头顶那颗「绑定」进第二层（还没归属任何IP的候选）。
           两层共用系列列表那条查询（/shop/trendy/list），只是各带 ipId / noIp 一种筛法 -->
      <el-dialog v-model="trendiesOpen" width="1000px" append-to-body destroy-on-close>
        <template #header>
          <span class="el-dialog__title">绑定系列{{ trendiesTitle ? ` - ${trendiesTitle}` : '' }}</span>
        </template>
        <el-form :model="trendyQuery" inline class="search-form">
          <el-form-item label="潮玩名称">
            <el-input v-model="trendyQuery.trendyName" placeholder="请输入潮玩名称" clearable
              @keyup.enter="handleTrendyQuery" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="trendyQuery.status" placeholder="全部" clearable>
              <el-option label="启用" :value="1" />
              <el-option label="禁用" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleTrendyQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetTrendyQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <ButtonGroup class="table-toolbar">
          <el-button type="primary" plain icon="Plus" @click="openPick">绑定</el-button>
          <el-button type="danger" plain icon="Remove" :disabled="mainSelection.length === 0"
            @click="handleBatchUnbind">批量解绑</el-button>
        </ButtonGroup>
        <el-table ref="mainTableRef" v-loading="trendiesLoading" :data="trendyList" row-key="id"
          show-overflow-tooltip max-height="420" @selection-change="handleMainSelectionChange">
          <!-- reserve-selection：翻页、换筛选都不掉已勾的行，所以 selection-change 抛的是跨页累积的完整集合 -->
          <el-table-column type="selection" width="45" reserve-selection />
          <el-table-column label="封面" align="center" width="80">
            <template #default="{ row }">
              <el-image v-if="row.coverImage" :src="row.coverImage" :preview-src-list="[row.coverImage]"
                preview-teleported fit="contain" style="width: 44px; height: 44px; border-radius: 4px" />
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="潮玩名称" prop="trendyName" align="center" min-width="180" />
          <el-table-column label="说明" align="center" min-width="220">
            <template #default="{ row }">
              <span>{{ row.description || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="商品数" align="center" width="90">
            <template #default="{ row }">
              <span>{{ row.productCount || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" align="center" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="90" fixed="right">
            <template #default="scope">
              <el-button type="warning" link icon="Remove" :loading="pickSubmitting"
                @click="handleUnbind(scope.row)">解绑</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-container">
          <el-pagination background v-model:current-page="trendyQuery.pageNum"
            v-model:page-size="trendyQuery.pageSize" :page-sizes="[10, 20, 30, 50]" :total="trendyTotal"
            layout="total, sizes, prev, pager, next, jumper" @size-change="handleTrendySizeChange"
            @current-change="getTrendies" />
        </div>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="trendiesOpen = false">关 闭</el-button>
          </div>
        </template>
      </el-dialog>

      <!-- 候选那一层直接用公共的系列选择弹窗（多选 + 底部确定）：
           trigger=false 只要它的弹窗、不要那颗输入框，由上面那排「绑定」按钮开；
           query 压一条 noIp=true，候选就只剩还没归属任何IP的系列 -->
      <ShopTrendySelect ref="pickRef" v-model="pickIds" multiple :trigger="false" :query="{ noIp: true }"
        title="选择要绑定的潮玩系列" @select="handlePickConfirm" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listShopIpApi,
  detailShopIpApi,
  addShopIpApi,
  updateShopIpApi,
  deleteShopIpApi,
  bindIpTrendiesApi
} from '@/api/trendy/ip'
import { listShopTrendyApi } from '@/api/trendy/trendy'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import TableMoreActions from '@/components/TableMoreActions/index.vue'
import ShopTrendySelect from '@/components/ShopTrendySelect/index.vue'
import UploadImage from '@/components/Upload/Image.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import ImageList from '@/views/shop/product/components/ImageList.vue'
import validate from '@/utils/validate'
import { useDict } from '@/utils/dictCache'
import { useUserStore } from '@/store/modules/user'

const dict = useDict('license_type', 'trendy_ip_status')

const userStore = useUserStore()
const permissions = computed(() => userStore.user.permissions || [])

/** 操作列交给公共「更多」组件渲染，权限在这里自己判（组件只认 actions 上的 hidden） */
const hasPermission = (permission: string): boolean => permissions.value.includes(permission)

const loading = ref(true)
const submitLoading = ref(false)
const total = ref(0)
const dataList = ref<any[]>([])
const selectedIds = ref<any[]>([])
const title = ref('')
const open = ref(false)

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  ipName: undefined,
  licenseType: undefined,
  isHot: undefined,
  status: undefined
})

const form = reactive<any>({})

const rules = reactive<FormRules>({
  ipName: [{ required: true, message: 'IP名称不能为空', trigger: 'blur' }],
  licenseType: [{ required: true, message: '请选择授权类型', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
})

/** 授权期限那一格：两端都没填就说没填，只填一端就报那一端（授权是逐条谈的，不设「默认永久」这种约定） */
const licenseText = (row: any) => {
  if (row.licenseStart && row.licenseEnd) return `${row.licenseStart} ~ ${row.licenseEnd}`
  if (row.licenseEnd) return `~ ${row.licenseEnd}`
  if (row.licenseStart) return `${row.licenseStart} ~`
  return '-'
}

/** 当天零点那条线：就地拼 yyyy-MM-dd，和 license_end 那个 DATE 列回来的形状一致（ISO 日期串可以直接比大小） */
const todayText = () => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

/** 到期日已过：到期当天还算在期内 */
const isExpired = (row: any) => {
  if (!row.licenseEnd) return false
  return String(row.licenseEnd) < todayText()
}

const getList = async () => {
  loading.value = true
  try {
    const { data } = await listShopIpApi(queryParams)
    dataList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
  } finally {
    loading.value = false
  }
}

const reset = () => {
  Object.assign(form, {
    id: undefined,
    ipName: undefined,
    ipEnName: undefined,
    ipLogo: undefined,
    ipCover: undefined,
    ipBanner: '[]',
    ipDesc: undefined,
    ipStory: undefined,
    copyrightNo: undefined,
    userId: undefined,
    userName: undefined,
    licenseType: 1,
    licenseStart: undefined,
    licenseEnd: undefined,
    isHot: 0,
    isRecommend: 0,
    sort: 0,
    status: 1
  })
  formRef.value?.resetFields()
}

const cancel = () => {
  open.value = false
  reset()
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  Object.assign(queryParams, { ipName: undefined, licenseType: undefined, isHot: undefined, status: undefined })
  handleQuery()
}

const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map(item => item.id)
}

const handleSizeChange = () => {
  queryParams.pageNum = 1
  getList()
}

const handleAdd = () => {
  reset()
  open.value = true
  title.value = '添加潮玩IP'
}

const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailShopIpApi(row.id)
    Object.assign(form, data)
    // 详情那条走 getById，不带回填的版权人名字（它是非本表列），所以拿列表那一行已有的名字顶着，
    // 否则修改弹窗里「版权人/授权人」那格只剩一串 id（口径同盲盒那页的负责运营）
    if (!form.userName) form.userName = row.userName
    open.value = true
    title.value = '修改潮玩IP'
  } catch (error) {
  }
}

const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateShopIpApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopIpApi(form)
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

const handleDelete = (row: any) => {
  const 句 = row.trendyCount ? `，但它名下还有 ${row.trendyCount} 个系列，要先在「绑定系列」里解绑` : ''
  ElMessageBox.confirm(`确认删除潮玩IP「${row.ipName}」吗${句}？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopIpApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个潮玩IP吗？名下还绑着系列的会被拦下来。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopIpApi(selectedIds.value)
    ElMessage.success('删除成功')
    selectedIds.value = []
    getList()
  }).catch(() => { })
}

/* ---------------- 绑定系列 ---------------- */

const trendiesOpen = ref(false)
const trendiesLoading = ref(false)
const trendiesTitle = ref('')
const currentIpId = ref<number>()
const trendyList = ref<any[]>([])
const trendyTotal = ref(0)
const trendyQuery = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  trendyName: undefined,
  status: undefined,
  /** 打开面板时钉成当前这个IP，筛选项里没有它，用户改不掉 */
  ipId: undefined
})

/** 第二层交给公共的系列选择弹窗（多选 + 底部确定），这里只留它的 ref 和选中值 */
const pickRef = ref<any>()
const pickIds = ref<number[]>([])
const pickSubmitting = ref(false)

/** 第一层勾中的行（批量解绑用）；表格实例留着是为了提交完能清掉勾选 */
const mainTableRef = ref<any>()
const mainSelection = ref<any[]>([])

const handleMainSelectionChange = (rows: any[]) => {
  mainSelection.value = rows
}

/** 提交完（绑定/解绑都算）清掉勾选：行已经换了一批，留着旧勾选会指向不在列表里的行 */
const clearMainSelection = () => {
  mainTableRef.value?.clearSelection()
  mainSelection.value = []
}

const getTrendies = async () => {
  trendiesLoading.value = true
  try {
    const { data } = await listShopTrendyApi(trendyQuery)
    trendyList.value = data.records || []
    trendyTotal.value = data.total || 0
  } catch (error) {
  } finally {
    trendiesLoading.value = false
  }
}

/** 打开某个IP的绑定面板：第一层就是它名下那些（排序沿用系列列表那一条：启用打头、sort、id） */
const handleTrendies = (row: any) => {
  currentIpId.value = row.id
  trendiesTitle.value = row.ipName
  trendyQuery.ipId = row.id
  clearMainSelection()
  trendiesOpen.value = true
  handleTrendyQuery()
}

const handleTrendyQuery = () => {
  trendyQuery.pageNum = 1
  getTrendies()
}

const handleTrendySizeChange = () => {
  trendyQuery.pageNum = 1
  getTrendies()
}

/** 只清筛选项，不动那格 ipId（它不是给用户改的） */
const resetTrendyQuery = () => {
  trendyQuery.trendyName = undefined
  trendyQuery.status = undefined
  handleTrendyQuery()
}

/** 头顶那排「绑定」：开公共系列弹窗（它自己带 noIp=true 的候选） */
const openPick = () => {
  pickIds.value = []
  pickRef.value?.openDialog()
}

/** 批量绑定/解绑都走这一条：ipId 传当前这个IP，解绑就传 null */
const submitBind = async (trendyIds: number[], ipId: number | null) => {
  pickSubmitting.value = true
  try {
    await bindIpTrendiesApi({ trendyIds, ipId })
    ElMessage.success(ipId ? `已绑定 ${trendyIds.length} 个系列` : `已解绑 ${trendyIds.length} 个系列`)
    clearMainSelection()
    await getTrendies()
    // 外面那一列的「系列数」和那颗按钮的显隐都跟着计数走，所以顺手重拉一次
    getList()
  } catch (error) {
  } finally {
    pickSubmitting.value = false
  }
}

/** 公共弹窗底部「确定」抛上来的整行数组（多选档），直接拿这批 id 绑到当前IP */
const handlePickConfirm = (rows: any[]) => {
  if (!rows?.length) return
  void submitBind(rows.map((row: any) => row.id), currentIpId.value ?? null)
}

const handleUnbind = (row: any) => void submitBind([row.id], null)

/** 批量解绑：勾中的那几行一次撤掉，先确认一句（和列表那棵「批量删除」同形） */
const handleBatchUnbind = () => {
  const ids = mainSelection.value.map((row: any) => row.id);
  ElMessageBox.confirm(`确认解绑选中的 ${ids.length} 个系列吗？（只是解除和这个IP的绑定，系列本身不动）`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    void submitBind(ids, null)
  }).catch(() => { })
}

/** 「更多」组件统一抛 command，按 type 分发 */
const handleActionCommand = (action: any) => {
  const { type, row } = action.command
  switch (type) {
    case 'trendies':
      handleTrendies(row)
      break
    case 'update':
      handleUpdate(row)
      break
    case 'delete':
      handleDelete(row)
      break
  }
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
    min-width: 170px;
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
  :deep(.el-form-item .el-select),
  :deep(.el-form-item .el-tree-select) {
    width: 100% !important;
  }
}

.table-toolbar {
  margin-bottom: 12px;
}

.ml6 {
  margin-left: 6px;
}
</style>
