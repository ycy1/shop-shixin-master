<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="描述/标签" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="搜照片描述或标签" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="所属园区" prop="parkId">
          <el-select v-model="queryParams.parkId" placeholder="全部" clearable>
            <el-option v-for="item in parkOptions" :key="item.id" :label="item.parkName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源" prop="sourceType">
          <el-select v-model="queryParams.sourceType" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_SOURCE)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="审核状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_AUDIT)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="精选" prop="isFeatured">
          <el-select v-model="queryParams.isFeatured" placeholder="全部" clearable>
            <el-option label="精选" :value="1" />
            <el-option label="非精选" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-card class="box-card">
      <!-- 操作工具栏 -->
      <template #header>
        <ButtonGroup>
          <el-button v-permission="['shop:parkPhoto:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['shop:parkPhoto:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格：一行是一张照片。原先这一堆挂在园区表的 images 那一个 json 列上，
           一张一行才挂得住来源、审核状态、精选和标签 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="照片" align="center" width="110">
          <template #default="{ row }">
            <el-image v-if="row.imageUrl" :src="row.imageUrl" :preview-src-list="[row.imageUrl]" fit="cover"
              preview-teleported class="photo-thumb" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="所属园区" align="center" min-width="150">
          <template #default="{ row }">
            <span>{{ row.parkName || (row.parkId ? `已删除园区 #${row.parkId}` : '-') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" align="center" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_SOURCE, row.sourceType)">
              {{ dict.label(DICT_SOURCE, row.sourceType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" align="center" width="110">
          <template #default="{ row }">
            <!-- 前台园区详情只出「通过」那一档，其余三档只有这一页看得见 -->
            <el-tag size="small" :type="dict.tag(DICT_AUDIT, row.status)">
              {{ dict.label(DICT_AUDIT, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="精选" align="center" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isFeatured === 1" size="small" type="warning">精选</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="标签" align="center" min-width="150">
          <template #default="{ row }">
            <template v-if="tagsOf(row.tags).length">
              <el-tag v-for="tag in tagsOf(row.tags)" :key="tag" size="small" class="mini-tag">{{ tag }}</el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="描述" prop="description" align="center" min-width="150">
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="排序" prop="sort" align="center" width="80" />
        <el-table-column label="上传人" align="center" min-width="120">
          <template #default="{ row }">{{ row.userName || '-' }}</template>
        </el-table-column>
        <!-- 三颗按钮走公共「更多」组件（口径同我的票种、优惠券那两页）：
             「通过 / 拒绝」两颗收成一颗「审核」，点开弹窗里选结论 + 写一句审核意见（他 2026-10-29 点的）——
             两颗分开点其实是两次整屏操作，还都来不及给一句原因；一句意见要落库、要随消息通知发给上传人，
             就得有个地方让人写完再提交。三颗都在 maxVisible 之内，所以这一页是逐个直出、不出下拉。
             组件没有 v-permission 那套指令，权限在这一份 actions 的 hidden 里自己判。 -->
        <el-table-column label="操作" align="center" width="230" fixed="right">
          <template #default="{ row }">
            <TableMoreActions :actions="[
              { label: '审核', type: 'success', icon: 'CircleCheck', hidden: !hasPermission('shop:parkPhoto:update'), command: { type: 'audit', row } },
              { label: '修改', icon: 'Edit', hidden: !hasPermission('shop:parkPhoto:update'), command: { type: 'update', row } },
              { label: '删除', type: 'danger', icon: 'Delete', hidden: !hasPermission('shop:parkPhoto:delete'), command: { type: 'delete', row } }
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
      <el-dialog v-model="open" :title="title" width="720px" append-to-body destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="所属园区" prop="parkId">
                <!-- options 只给营业中的；老照片绑着已停业园区时拿列表那格的园区名兜一下，别让这一格空着
                     （口径同票种表单那一格）-->
                <el-select v-model="form.parkId" placeholder="请选择所属园区" filterable style="width: 100%">
                  <el-option v-for="item in parkSelectOptions" :key="item.id" :label="item.parkName" :value="item.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="来源" prop="sourceType">
                <el-select v-model="form.sourceType" placeholder="请选择" style="width: 100%">
                  <el-option v-for="item in dict.options(DICT_SOURCE)" :key="item.value" :label="item.label"
                    :value="Number(item.value)" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="照片" prop="imageUrl">
                <upload-image v-model="form.imageUrl" :size="120" source="park" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="标签" prop="tags">
                <!-- 候选来自服务端 Redis 那串词表；也能自己敲（allow-create），敲进去的新词保存时会并进那一串 -->
                <el-select v-model="tagArr" multiple filterable allow-create default-first-option
                  placeholder="输入后回车加一个，如 打卡 / 夜景 / Molly" style="width: 100%">
                  <el-option v-for="tag in tagOptions" :key="tag" :label="tag" :value="tag" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="审核状态" prop="status">
                <el-radio-group v-model="form.status">
                  <el-radio v-for="item in dict.options(DICT_AUDIT)" :key="item.value" :value="Number(item.value)">
                    {{ item.label }}
                  </el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="精选" prop="isFeatured">
                <el-switch v-model="form.isFeatured" :active-value="1" :inactive-value="0" />
                <span class="form-tip">精选的排在这家园区图集最前，前台园区详情也是这个次序</span>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="排序" prop="sort">
                <el-input-number v-model="form.sort" :min="0" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="描述" prop="description">
                <el-input v-model="form.description" placeholder="图注一句话，选填" maxlength="64" show-word-limit />
              </el-form-item>
            </el-col>
          </el-row>
          <div class="tip">
            所属 IP 不给填：照片属于哪个 IP 跟着园区走，选了园区服务端按园区那一格带过来。
          </div>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="cancel">取 消</el-button>
            <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
          </div>
        </template>
      </el-dialog>

      <!-- 审核窗口：一次把「结论 + 一句意见」交出去，走的是服务端那一条 audit，别的一列都不碰。
           先把这张图摆大一点给人看（点开可放大），免得决定都要做了还得先回列表点大图；
           意见那一格就是落进 audit_msg、并随消息通知发给上传人的那句，留空表示不写。 -->
      <el-dialog v-model="auditOpen" title="审核照片" width="680px" append-to-body destroy-on-close>
        <div class="audit-preview">
          <el-image v-if="auditRow.imageUrl" :src="auditRow.imageUrl" fit="cover" class="audit-image"
            :preview-src-list="[auditRow.imageUrl]" preview-teleported />
          <div v-else class="audit-image audit-empty">无图</div>
          <div class="audit-meta">
            <p><span class="k">所属园区</span>{{ auditRow.parkName || '-' }}</p>
            <p>
              <span class="k">来源</span>{{ dict.label(DICT_SOURCE, auditRow.sourceType) }}
              <span v-if="auditRow.userName"> · {{ auditRow.userName }}</span>
            </p>
            <p><span class="k">上传时间</span>{{ (auditRow.createTime || '-').slice(0, 16) }}</p>
            <p><span class="k">描述</span>{{ auditRow.description || '-' }}</p>
            <p>
              <span class="k">当前状态</span>
              <el-tag size="small" :type="dict.tag(DICT_AUDIT, auditRow.status)">
                {{ dict.label(DICT_AUDIT, auditRow.status) }}
              </el-tag>
            </p>
            <p><span class="k">上一次意见</span>{{ auditRow.auditMsg || '-' }}</p>
          </div>
        </div>

        <el-form label-width="90px" class="audit-form">
          <el-form-item label="审核结论">
            <el-radio-group v-model="auditForm.status">
              <el-radio v-for="item in dict.options(DICT_AUDIT)" :key="item.value" :value="Number(item.value)">
                {{ item.label }}
              </el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="审核意见">
            <el-input v-model="auditForm.auditMsg" type="textarea" :rows="3" maxlength="255" show-word-limit
              placeholder="写一句给上传人看的话，会随消息通知一起发出去；留空就是不写" />
            <span class="form-tip">
              这一句只发给「用户上传」的图：官方上传那几行没有收件人，不发消息。
              上一次写的意见会被这一次覆盖（这次留空就是清掉）。
            </span>
          </el-form-item>
        </el-form>

        <template #footer>
          <div class="dialog-footer">
            <el-button @click="auditOpen = false">取 消</el-button>
            <el-button type="primary" :loading="auditSubmitting" @click="submitAudit">确 定</el-button>
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
  listParkPhotoApi,
  detailParkPhotoApi,
  addParkPhotoApi,
  updateParkPhotoApi,
  auditParkPhotoApi,
  tagOptionsParkPhotoApi,
  deleteParkPhotoApi
} from '@/api/trendy/parkPhoto'
import { optionsShopParkApi } from '@/api/trendy/park'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import TableMoreActions from '@/components/TableMoreActions/index.vue'
import UploadImage from '@/components/Upload/Image.vue'
import { useDict } from '@/utils/dictCache'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
/** 操作列走 TableMoreActions，权限得在这里自己判（组件没有 v-permission 那套指令，只认 actions 上的 hidden） */
const permissions = computed(() => userStore.user.permissions || [])
const hasPermission = (permission: string): boolean => permissions.value.includes(permission)

const DICT_SOURCE = 'source_type'
const DICT_AUDIT = 'audit_status'

const dict = useDict(DICT_SOURCE, DICT_AUDIT)

/** 标签那格库里是 json 数组（["打卡","夜景"]），表单里要的是数组，所以靠 tagArr 中转，提交时再拼回 JSON 文本 */
const tagArr = ref<string[]>([])

/** 拆成字符串数组；库里那列可能整段是空的，脏数据一律当「这张没打标签」，别让一行坏数据把整张表打断 */
function tagsOf(raw: any): string[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter((tag) => typeof tag === 'string' && tag) : []
  } catch (error) {
    return []
  }
}

/** 园区候选：只给营业中的（口径同票种表单） */
const parkOptions = ref<any[]>([])
const getParkOptions = async () => {
  try {
    const { data } = await optionsShopParkApi()
    parkOptions.value = data || []
  } catch (error) {
  }
}

const loading = ref(true)
const submitLoading = ref(false)
const selectedIds = ref<any[]>([])
const total = ref(0)
const dataList = ref<any[]>([])
const title = ref('')
const open = ref(false)

/**
 * 标签候选：读的是服务端 Redis 里那一串词表（/shop/parkPhoto/tagOptions）。
 * 不是字典、也不按当前这一页数出来的行 —— 翻页/筛选都不换候选，新敲的词保存后并进那一串，下次就能选到。
 */
const tagOptions = ref<string[]>([])
const getTagOptions = async () => {
  try {
    const { data } = await tagOptionsParkPhotoApi()
    tagOptions.value = data || []
  } catch (error) {
  }
}

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// 查询参数
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  parkId: undefined,
  sourceType: undefined,
  status: undefined,
  isFeatured: undefined
})

// 表单参数
const form = reactive<any>({})

const rules = reactive<FormRules>({
  parkId: [{ required: true, message: '请选择所属园区', trigger: 'change' }],
  imageUrl: [{ required: true, message: '请上传照片', trigger: 'change' }]
})

/**
 * 园区下拉实际用的候选：options 只列营业中的，所以绑着停业/已删园区的老照片会选不出来。
 * 列表行上有 parkName（服务层回填的那一格），拿它顶一下别让这一格空着
 */
const parkSelectOptions = computed(() => {
  const id = form.parkId
  if (!id) return parkOptions.value
  if (parkOptions.value.some((item: any) => String(item.id) === String(id))) return parkOptions.value
  return [{ id, parkName: form.parkName || `已停业园区 #${id}` }, ...parkOptions.value]
})

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listParkPhotoApi(queryParams)
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
    parkId: undefined,
    parkName: undefined,
    sourceType: Number(dict.defaultValue(DICT_SOURCE, 1)),
    userId: undefined,
    imageUrl: undefined,
    tags: undefined,
    // 审核状态默认给「通过」：后台加的图本来就是官方图，每加一张还要点一次通过是白动作
    //（库里那一格的默认档是 0 待审核，那一档是给还没开的「用户上传」那条路留的）
    status: 1,
    isFeatured: 0,
    sort: 0,
    description: undefined
  })
  formRef.value?.resetFields()
  tagArr.value = []
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
  queryParams.parkId = undefined
  queryParams.sourceType = undefined
  queryParams.status = undefined
  queryParams.isFeatured = undefined
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
}

/** 新增按钮操作 */
const handleAdd = () => {
  reset()
  open.value = true
  title.value = '添加照片'
}

/** 修改按钮操作：每次点开重新捞，别人可能刚把这张改成已通过/换过园区了 */
const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailParkPhotoApi(row.id)
    Object.assign(form, data)
    tagArr.value = tagsOf(data.tags)
    open.value = true
    title.value = '修改照片'
  } catch (error) {
  }
}

/**
 * 审核窗口：一次把「结论 + 一句意见」交出去，走的是服务端那一条 audit
 *（不走整行提交 —— 那样会把没带上的人名、描述、上一次的意见抹成 NULL）。
 * 内容直接拿列表那一行填：照片、园区、上传人、描述、当前状态、上一次意见都在那一行里，
 * 不为一颗按钮再捞一次详情（列表那条就是 selectPhotoPage 现联出来的，详情给不出更多）。
 */
const auditOpen = ref(false)
const auditSubmitting = ref(false)
/** 窗口上面那一栏回显用的那一行原始数据 */
const auditRow = ref<any>({})
const auditForm = reactive<{ id: any; status: number; auditMsg: string }>({ id: undefined, status: 0, auditMsg: '' })

const handleAudit = (row: any) => {
  auditRow.value = row
  auditForm.id = row.id
  // 结论默认给这一行现在的档位，不替人预选「通过」：默认值顺手一点就是误操作，窗口里点一下不费事
  auditForm.status = row.status ?? 0
  auditForm.auditMsg = row.auditMsg || ''
  auditOpen.value = true
}

/** 提交审核：失败就把窗口留着（意见写到一半别丢），成功才关窗重拉列表 */
const submitAudit = async () => {
  auditSubmitting.value = true
  try {
    // 意见留空按没写处理（服务端那句 UPDATE 会把它清成 NULL）
    await auditParkPhotoApi(auditForm.id, auditForm.status, auditForm.auditMsg.trim() || undefined)
    ElMessage.success(`已置为「${dict.label(DICT_AUDIT, auditForm.status)}」`)
    auditOpen.value = false
    getList()
  } catch (error) {
  } finally {
    auditSubmitting.value = false
  }
}

/**
 * 操作列按钮分发（TableMoreActions 只抛一个 command 事件，动作认 action.command.type，同优惠券、我的票种那两页）
 */
const handleActionCommand = (action: any) => {
  const { type, row } = action.command
  switch (type) {
    case 'update':
      handleUpdate(row)
      break
    case 'audit':
      handleAudit(row)
      break
    case 'delete':
      handleDelete(row)
      break
  }
}

/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    // 数组拼回 json 文本；一个标签都没打就给 "[]"（空串不是合法 JSON，写进 json 列会直接报错）
    form.tags = JSON.stringify(tagArr.value)
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateParkPhotoApi(form)
        ElMessage.success('修改成功')
      } else {
        await addParkPhotoApi(form)
        ElMessage.success('新增成功')
      }
      open.value = false
      getList()
      // 这一趟可能有新敲的词进了 Redis 那串词表，捞一次，下一次打开弹窗就选得到
      getTagOptions()
    } catch (error) {
    } finally {
      submitLoading.value = false
    }
  })
}

/** 删除按钮操作 */
const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确认删除这张照片吗？删掉之后前台那圈图集里就没有它了。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteParkPhotoApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 张照片吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteParkPhotoApi(selectedIds.value)
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
  getParkOptions()
  getTagOptions()
})
</script>

<style scoped lang="scss">
/* 参照园区管理、票种管理：筛选项等分整行，控件宽度统一由 CSS 控制 */
.search-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;

  :deep(.el-form-item) {
    flex: 1 1 0;
    min-width: 140px;
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

/* 缩略图给个固定格子：照片比例五花八门，不钉死的话行高跟着图片一起抖 */
.photo-thumb {
  width: 84px;
  height: 56px;
  border-radius: 4px;
}

/* 标签一档一个，挨着放别贴死 */
.mini-tag {
  margin: 0 2px;
}

/* 所属 IP 那句说明：字号和颜色都压到占位符一档，别抢表单焦点 */
.tip {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-placeholder);
}

.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

/* 审核窗口顶上那一栏：左边一张够大的图（结论要看清才下得了手），右边几格元信息。
   图钉死 220×150，和列表那格缩略图同理 —— 照片比例五花八门，不钉死窗口高度会跟着跳 */
.audit-preview {
  display: flex;
  gap: 16px;
  margin-bottom: 18px;

  .audit-image {
    flex: 0 0 auto;
    width: 220px;
    height: 150px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
  }

  .audit-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    color: var(--el-text-color-placeholder);
  }

  .audit-meta {
    min-width: 0;
    font-size: 13px;
    line-height: 1.9;
    color: var(--el-text-color-regular);

    p {
      margin: 0;
    }

    .k {
      display: inline-block;
      width: 88px;
      color: var(--el-text-color-secondary);
    }
  }
}
</style>
