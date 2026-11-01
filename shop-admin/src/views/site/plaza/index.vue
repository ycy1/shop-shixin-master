<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="标题/正文摘要/发布者昵称" clearable
            @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="关联类型" prop="relationType">
          <el-select v-model="queryParams.relationType" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_RELATION)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="审核状态" prop="auditStatus">
          <el-select v-model="queryParams.auditStatus" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_AUDIT)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item class="search-actions">
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-card class="box-card">
      <!-- 操作工具栏：这一页没有「新增」—— 广场上的内容都是前台用户发的，后台只做审核、标记和删 -->
      <template #header>
        <ButtonGroup>
          <el-button v-permission="['sys:plaza:audit']" type="success" plain icon="Stamp"
            :disabled="selectedIds.length === 0" @click="handleBatchAudit">批量审核</el-button>
          <el-button v-permission="['sys:plaza:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格（ref 是给批量审核/批量删除成功后 clearSelection 用的：只清 selectedIds 不清表格，
           表头那颗全选框还挂着勾，看着像没提交成功） -->
      <el-table ref="tableRef" v-loading="loading" :data="dataList" show-overflow-tooltip
        @selection-change="handleSelectionChange">
        <!-- 已删除的那一行不给勾：批量审核那一趟走的是带逻辑删除的读法，捞不回来只能静默跳过，
             与其让人勾了白勾，不如压根不给勾（口径和操作列「已删除不给审核/删除」同一份） -->
        <el-table-column type="selection" width="55" align="center" :selectable="(row: any) => row.isDeleted !== 1" />
        <!-- 广场消息图：发布人自己传的那一组（逗号分隔存在一列上）。列表这一格只铺第一张，
             点开由查看器左右翻整组；前端不传就是这一格为空，服务端不会从正文抓图来补。
             表级的 show-overflow-tooltip 会被每一列继承，纯图片列要显式关掉，否则悬停弹一个空 tooltip -->
        <el-table-column label="广场消息图" align="center" width="80" :show-overflow-tooltip="false">
          <template #default="{ row }">
            <el-image v-if="parseCommaUrls(row.plazaImage).length" :src="parseCommaUrls(row.plazaImage)[0]"
              :preview-src-list="parseCommaUrls(row.plazaImage)" preview-teleported fit="cover" class="list-thumb" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="发布者" align="center" min-width="110">
          <template #default="{ row }">
            {{ row.userName || (row.userId ? `已删除用户 #${row.userId}` : '-') }}
          </template>
        </el-table-column>
        <el-table-column label="标题" align="center" min-width="140">
          <template #default="{ row }">
            <span>{{ row.title || '（没填标题）' }}</span>
          </template>
        </el-table-column>
        <!-- 正文不铺：这一列本来印的是服务端剥好标签的摘要，一屏十条又长又占地方（他点名要撤掉），
             要看正文走「详情」「审核」「修改」那三个弹窗，它们各自捞带 content 的那一条读法 -->
        <el-table-column label="关联对象" align="center" min-width="130">
          <template #default="{ row }">
            <el-tag v-if="row.relationType" size="small" class="relation-tag">
              {{ dict.label(DICT_RELATION, row.relationType) }}
            </el-tag>
            <span>{{ relationText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="标记" align="center" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.isTop === 1" size="small" type="danger">置顶</el-tag>
            <el-tag v-if="row.isRecommend === 1" size="small" type="warning">推荐</el-tag>
            <span v-if="row.isTop !== 1 && row.isRecommend !== 1" class="cell-empty">-</span>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_AUDIT, row.auditStatus)">
              {{ dict.label(DICT_AUDIT, row.auditStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 软删掉的那几条不再靠「标记」列那颗 tag：这一页把已删除的和正常的混在一起列（列表 SQL 那一刀由服务层放开），
             所以 is_deleted=1 直接在状态这一格印「已删除」，其余走 plaza_status 字典 -->
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.isDeleted === 1" size="small" type="info">已删除</el-tag>
            <el-tag v-else size="small" :type="dict.tag(DICT_STATUS, row.status)">
              {{ dict.label(DICT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" align="center" width="150">
          <template #default="{ row }">{{ row.publishTime || '未通过审核' }}</template>
        </el-table-column>

        <el-table-column label="操作" align="center" width="220" fixed="right">
          <template #default="{ row }">
            <!-- 四颗按钮由 TableMoreActions 收成"直出两颗 + 更多"（口径同门店培训）；组件用不上 v-permission，权限在 hidden 里自己判。
                 「审核」不给置灰：待审核、已发布、已拒绝都能重新判一次（改判由服务层保证不重复送分），能不能点只认权限。
                 已删除的那一行不给「审核」「删除」：审核那条读法走的是逻辑删除那一刀（捞不回来，与其报一句"不存在"不如不给点），
                 再删一次是空动作却会回"删除成功"；这一行只剩下「详情」和「修改」能点 -->
            <TableMoreActions :actions="[
              { label: '审核', type: 'success', icon: 'Stamp', hidden: !hasPermission('sys:plaza:audit') || row.isDeleted === 1, command: { type: 'audit', row } },
              { label: '详情', icon: 'View', hidden: !hasPermission('sys:plaza:detail'), command: { type: 'detail', row } },
              { label: '修改', icon: 'Edit', hidden: !hasPermission('sys:plaza:update'), command: { type: 'update', row } },
              { label: '删除', type: 'danger', icon: 'Delete', hidden: !hasPermission('sys:plaza:delete') || row.isDeleted === 1, command: { type: 'delete', row } }
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

    <!--
      审核：通过和拒绝都走这一颗。
      弹窗里铺的是这一条的正文（后端详情那条 SQL 才带 content，列表那一趟不取），
      富文本按「纯文本 + 内嵌图片」两行铺开画，不 v-html —— 内容是前台访客写的，当 HTML 画就是存储型 XSS。
      判成 1 已发布 那一刻服务端补两笔：盖 publish_time + 给发布者 10 积分（只在这一档第一次进时给）。
    -->
    <el-dialog v-model="auditOpen" title="审核广场消息" width="900px" append-to-body destroy-on-close>
      <div v-loading="auditLoading">
        <el-descriptions :column="2" border class="audit-head">
          <el-descriptions-item label="发布者">
            {{ auditRow.userName || (auditRow.userId ? `已删除用户 #${auditRow.userId}` : '-') }}
          </el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ auditRow.publishTime || '未通过审核' }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ auditRow.title || '（没填标题）' }}</el-descriptions-item>
          <!-- 广场消息图也在审核这一屏里给出来：它不进正文，光看下面那排正文图会漏掉广场上配的那一组 -->
          <el-descriptions-item label="广场消息图" :span="2">
            <div v-if="auditColumnImages.length" class="content-images">
              <el-image v-for="(url, index) in auditColumnImages" :key="url" :src="url"
                :preview-src-list="auditColumnImages" :initial-index="index" preview-teleported fit="cover"
                class="content-image" />
            </div>
            <span v-else>没配图</span>
          </el-descriptions-item>
          <el-descriptions-item label="当前审核状态" :span="2">
            <el-tag size="small" :type="dict.tag(DICT_AUDIT, auditRow.auditStatus)">
              {{ dict.label(DICT_AUDIT, auditRow.auditStatus) }}
            </el-tag>
            <span class="audit-hint">
              {{ auditRow.publishTime ? '这条已上过广场，改判不会重复送积分' : '判「通过」后这条才进广场，并给发布者 10 积分' }}
            </span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 正文：图片来自 HTML 里的 img 标签，文字取自服务端剥好标签的那份摘要 -->
        <div v-if="auditImages.length" class="content-images">
          <el-image v-for="(url, index) in auditImages" :key="url" :src="url" :preview-src-list="auditImages"
            :initial-index="index" preview-teleported fit="cover" class="content-image" />
        </div>
        <div class="content-text">{{ auditRow.contentText || '（正文没有文字）' }}</div>

        <el-form ref="auditFormRef" :model="auditForm" :rules="auditRules" label-width="90px">
          <el-form-item label="审核结果" prop="auditStatus">
            <el-radio-group v-model="auditForm.auditStatus">
              <el-radio :value="1">通过（上广场）</el-radio>
              <el-radio :value="2">拒绝</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="审核备注" prop="auditRemark">
            <el-input v-model="auditForm.auditRemark" type="textarea" :rows="2" maxlength="500" show-word-limit
              placeholder="判「拒绝」时写给发布者看的那一句（这一格是前台唯一能看到的说法）" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="auditOpen = false">取 消</el-button>
          <el-button type="primary" :loading="auditSubmitLoading" @click="submitAudit">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    <!--
      批量审核：一次把多选框里那几条判成同一档，形状照文件中心「批量设置过期时间」那一颗
      （单条那个弹窗铺的是某一条的正文，N 条没有「哪一条」可铺，所以另开一颗小的）。
      备注这一格是整批共用一句 —— 一批里既有图文又有纯文、理由各不相同时，该逐条审而不该勾一批。
      默认还是「通过」（和单条那颗同一个默认：来这一页的人多半是在放行）；判通过的每一条各给发布者 10 分、各推一条站内消息。
    -->
    <el-dialog v-model="batchAuditOpen" title="批量审核广场消息" width="520px" append-to-body>
      <el-alert type="info" :closable="false"
        :title="`将把选中的 ${batchAuditIds.length} 条一并判成同一档`" class="batch-audit-tip" />
      <el-form ref="batchAuditFormRef" :model="batchAuditForm" :rules="batchAuditRules" label-width="90px">
        <el-form-item label="审核结果" prop="auditStatus">
          <el-radio-group v-model="batchAuditForm.auditStatus">
            <el-radio :value="1">通过（上广场）</el-radio>
            <el-radio :value="2">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核备注" prop="auditRemark">
          <el-input v-model="batchAuditForm.auditRemark" type="textarea" :rows="2" maxlength="500" show-word-limit
            placeholder="这一句会写到这一批每一条上（发布者在前台看到的就是它）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="batchAuditOpen = false">取 消</el-button>
          <el-button type="primary" :loading="batchAuditLoading" @click="submitBatchAudit">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 详情：只读看这一条（正文带在详情那一条 SQL 里，同审核弹窗、修改弹窗那份读法；已删除的那一条后台也读得回来） -->
    <el-dialog v-model="detailOpen" title="广场消息详情" width="900px" append-to-body destroy-on-close>
      <div v-loading="detailLoading">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="消息ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="发布者">
            {{ detail.userName || (detail.userId ? `已删除用户 #${detail.userId}` : '-') }}
          </el-descriptions-item>
          <el-descriptions-item label="关联类型">{{ dict.label(DICT_RELATION, detail.relationType) }}</el-descriptions-item>
          <el-descriptions-item label="关联对象">{{ relationText(detail) }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ detail.title || '（没填标题）' }}</el-descriptions-item>
          <el-descriptions-item label="广场消息图" :span="2">
            <div v-if="detailColumnImages.length" class="content-images">
              <el-image v-for="(url, index) in detailColumnImages" :key="url" :src="url"
                :preview-src-list="detailColumnImages" :initial-index="index" preview-teleported fit="cover"
                class="content-image" />
            </div>
            <span v-else>没配图</span>
          </el-descriptions-item>
          <el-descriptions-item label="审核状态">
            <el-tag size="small" :type="dict.tag(DICT_AUDIT, detail.auditStatus)">
              {{ dict.label(DICT_AUDIT, detail.auditStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="dict.tag(DICT_STATUS, detail.status)">
              {{ dict.label(DICT_STATUS, detail.status) }}
            </el-tag>
            <el-tag v-if="detail.isDeleted === 1" size="small" type="info" class="deleted-tag">已删除</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="审核备注" :span="2">{{ detail.auditRemark || '无' }}</el-descriptions-item>
          <el-descriptions-item label="点赞数">{{ detail.likeCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="评论数">{{ detail.commentCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="分享数">{{ detail.shareCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="是否置顶">{{ detail.isTop === 1 ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="是否推荐">{{ detail.isRecommend === 1 ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="正文（文字）" :span="2">
            <div class="content-text">{{ detail.contentText || '（正文没有文字）' }}</div>
          </el-descriptions-item>
          <el-descriptions-item v-if="detailImages.length" label="正文（图片）" :span="2">
            <div class="content-images">
              <el-image v-for="(url, index) in detailImages" :key="url" :src="url" :preview-src-list="detailImages"
                :initial-index="index" preview-teleported fit="cover" class="content-image" />
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ detail.publishTime || '未通过审核' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="修改时间" :span="2">{{ detail.updateTime || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!--
      修改：后台动 标题 / 广场消息图 / 正文 / 置顶 / 推荐 / 上下架 这六格（他这一轮点名的口径：后台也改得动消息内容）。
      正文一改，服务端同一趟重算摘要；发布人、关联对象和审核那几格不在这一屏出现 —— 后端那一趟也不认。
      正文只有详情那条 SQL 带（列表那一趟不取 content），所以要等内容回来再开弹窗：
      WangEditor 建实例那一次读走初始内容，之后再换 modelValue 它不接。
    -->
    <el-dialog v-model="open" title="修改广场消息" width="1000px" append-to-body destroy-on-close>
      <el-form ref="formRef" v-loading="formLoading" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="发布者">
          <span>{{ form.userName || (form.userId ? `已删除用户 #${form.userId}` : '-') }}</span>
          <span v-if="form.isDeleted === 1" class="deleted-hint">这一条已删除，改完还是「已删除」（这一屏不清删除标记）</span>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="留空就是没填标题" maxlength="200" show-word-limit clearable />
        </el-form-item>
        <!-- 广场消息图：这一格后端每次都照请求体写，不留神清空就会把发布人传的那一组抹掉，所以点开时要先把原图铺回来 -->
        <el-form-item label="广场消息图" prop="plazaImage">
          <image-list v-model="form.plazaImage" source="plaza" value-mode="comma" :limit="9" />
        </el-form-item>
        <el-form-item label="正文" prop="content">
          <div v-if="contentReady" class="editor-wrap">
            <WangEditor v-model="form.content" />
          </div>
          <div v-else class="editor-placeholder">正文加载中…</div>
        </el-form-item>
        <el-form-item label="是否置顶" prop="isTop">
          <el-switch v-model="form.isTop" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="是否推荐" prop="isRecommend">
          <el-switch v-model="form.isRecommend" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择" style="width: 100%">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cancel">取 消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listShopPlazaApi,
  detailShopPlazaApi,
  auditShopPlazaApi,
  updateShopPlazaApi,
  auditBatchShopPlazaApi,
  deleteShopPlazaApi
} from '@/api/site/plaza'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import TableMoreActions from '@/components/TableMoreActions/index.vue'
import ImageList from '@/views/shop/product/components/ImageList.vue'
import WangEditor from '@/components/WangEditor/index.vue'
import { parseCommaUrls } from '@/utils/imageJson'
import { useDict } from '@/utils/dictCache'
import { useUserStore } from '@/store/modules/user'

/**
 * 站点管理 → 广场消息：前台「广场」那条流的内容都在这里审。
 *
 * 这一页没有新增 —— 消息都是前台用户发的（发布走 /api/comm/shop/plaza/publish，两端共用一份判据），
 * 落库一律是 0 待审核、publish_time 留空；只有这里判成 1 已发布 那一刻才上广场，
 * 同一趟服务端给发布者 10 积分。所以「审核」这颗是这条链唯一给分的入口。
 * 「修改」动 标题 / 广场消息图 / 正文 / 置顶 / 推荐 / 上下架 六格（正文一改摘要跟着重算），
 * 但不动发布人、关联对象和审核那三格，也不因为改了内容就重新打回待审核。
 * 已删除的那一条默认就和正常的混在这一页上（列表 SQL 不填 isDeleted 就不拦），由「状态」那一格印「已删除」认。
 */

const userStore = useUserStore()
/** 操作列走 TableMoreActions，权限得在这里自己判（组件没有 v-permission 那套指令，只认 actions 上的 hidden） */
const permissions = computed(() => userStore.user.permissions || [])
const hasPermission = (permission: string): boolean => {
  return permissions.value.includes(permission)
}

const DICT_RELATION = 'plaza_relation_type'
const DICT_AUDIT = 'plaza_audit_status'
const DICT_STATUS = 'plaza_status'

const dict = useDict(DICT_RELATION, DICT_AUDIT, DICT_STATUS)

/**
 * 状态那一格（搜索栏和修改弹窗共用）：字典里那条 0 已删除 是解释历史值用的，
 * 后台这一格只能在 1 正常 / 2 已隐藏 之间切（服务层也只认这两档），选项里就不出现 0。
 */
const statusOptions = computed(() => dict.options(DICT_STATUS).filter((item: any) => item.value !== 0))

const loading = ref(true)
const submitLoading = ref(false)
/** 修改弹窗那次「捞正文」的遮罩（正文只在详情那条 SQL 里，列表那一趟不取） */
const formLoading = ref(false)
/** 正文到没到：编辑器挂在这一格上，内容是空的时候不挂载（WangEditor 只在建实例那一次读初始内容） */
const contentReady = ref(false)
const selectedIds = ref<any[]>([])
const total = ref(0)
const dataList = ref<any[]>([])
const open = ref(false)

const tableRef = ref()
/** 批量动作成功后要清的是两份：数组 + 表格自己的勾选。只清数组的话表头那颗全选框还挂着勾，看着像没提交成功 */
const clearSelection = () => {
  selectedIds.value = []
  tableRef.value?.clearSelection()
}

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// 查询参数（后端收的是实体上那几格：keyword 是查询专用的非表列，其余都是真列；
// isDeleted 不给 ⇒ 未删除和已删除混在一页上，这一页要的正是这个混着看）
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  relationType: undefined,
  auditStatus: undefined,
  status: undefined
})

// 表单参数（提交后端只认 id / title / plazaImage / content / isTop / isRecommend / status；其余是回显给人认这一条的）
const form = reactive<any>({})

/**
 * 正文判空要和后端那一份对上：编辑器清空后留的是 <p><br></p>，看着有值、剥了标签是空的。
 * 只有图没字的那一条后端放行（<img> 在），这里跟着放。
 */
function validateContent(_rule: any, value: any, callback: any) {
  const html = String(value || '')
  const text = html.replace(/<[^>]*>/g, '').trim()
  if (!text && !/<img/i.test(html)) {
    return callback(new Error('请输入正文'))
  }
  callback()
}

const rules = reactive<FormRules>({
  content: [{ required: true, validator: validateContent, trigger: 'blur' }],
  isTop: [{ required: true, message: '请选择是否置顶', trigger: 'change' }],
  isRecommend: [{ required: true, message: '请选择是否推荐', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
})

/* ---------------------------- 详情 / 审核弹窗（都读带正文的那一条 SQL） ---------------------------- */

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<any>({})

const auditOpen = ref(false)
const auditLoading = ref(false)
const auditSubmitLoading = ref(false)
const auditRow = ref<any>({})
const auditFormRef = ref<FormInstance>()
const auditForm = reactive<any>({ id: undefined, auditStatus: 1, auditRemark: undefined })

const auditRules = reactive<FormRules>({
  auditStatus: [{ required: true, message: '请选择审核结果', trigger: 'change' }]
})

/**
 * 正文里内嵌的图片地址：从富文本 HTML 里把 img 的 src 一个个摘出来，交给查看器左右翻。
 * 只读属性、不把 HTML 交给 v-html —— 这一格出自前台访客之手，当 HTML 画就等于存储型 XSS。
 */
const pickImages = (html: any) => {
  const urls: string[] = []
  if (!html) return urls
  const reg = /<img[^>]+?src\s*=\s*["']?([^"'\s>]+)/gi
  let m = reg.exec(html)
  while (m) {
    if (m[1] && !urls.includes(m[1])) urls.push(m[1])
    m = reg.exec(html)
  }
  return urls
}

const detailImages = computed(() => pickImages(detail.value.content))
const auditImages = computed(() => pickImages(auditRow.value.content))

/** 广场消息图那一列（逗号串）：和上面正文里内嵌的那批各翻各的，合起来看会分不清哪张是配的图 */
const detailColumnImages = computed(() => parseCommaUrls(detail.value.plazaImage))
const auditColumnImages = computed(() => parseCommaUrls(auditRow.value.plazaImage))

/** 关联对象那一格：无关联就是空，对象被删了联不上表只剩 id（口径同评论页的「评论对象」列） */
const relationText = (row: any) => {
  if (!row?.relationType || !row?.relationId) return '无关联'
  return row.relationName || `已删除对象 #${row.relationId}`
}

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const { data } = await listShopPlazaApi({ ...queryParams })
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
  queryParams.relationType = undefined
  queryParams.auditStatus = undefined
  queryParams.status = undefined
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
}

/** 详情按钮操作：列表那一趟不取正文，正文要靠这一条单独捞 */
const handleDetail = async (row: any) => {
  detail.value = {}
  detailOpen.value = true
  detailLoading.value = true
  try {
    const { data } = await detailShopPlazaApi(row.id)
    detail.value = data || {}
  } catch (error) {
  } finally {
    detailLoading.value = false
  }
}

/** 审核按钮操作：同样捞带正文的那一条，让审核的人看到发布者真正写了什么 */
const handleAudit = async (row: any) => {
  auditRow.value = { ...row }
  auditForm.id = row.id
  // 默认给「通过」：这一页绝大多数点开的人就是要放行它，拒绝是少数那一档
  auditForm.auditStatus = 1
  auditForm.auditRemark = undefined
  auditFormRef.value?.clearValidate()
  auditOpen.value = true
  auditLoading.value = true
  try {
    const { data } = await detailShopPlazaApi(row.id)
    auditRow.value = data || {}
  } catch (error) {
  } finally {
    auditLoading.value = false
  }
}

const submitAudit = () => {
  auditFormRef.value?.validate(async (valid: any) => {
    if (!valid) return
    auditSubmitLoading.value = true
    try {
      await auditShopPlazaApi({
        id: auditForm.id,
        auditStatus: auditForm.auditStatus,
        auditRemark: auditForm.auditRemark
      })
      ElMessage.success(auditForm.auditStatus === 1 ? '已通过，这条已上广场' : '已拒绝')
      auditOpen.value = false
      getList()
    } catch (error) {
    } finally {
      auditSubmitLoading.value = false
    }
  })
}

/**
 * 批量审核：多选框里那几条一次判成同一档。
 *
 * 打开前先拦一道（没勾就不给开），提交成功后清的是两份状态：selectedIds 和表格自己的勾选（tableRef.clearSelection）——
 * 只清前者会让表头那颗全选框挂着勾，看着像没提交成功。列表重捞一次：这一批的审核状态、发布时间、
 * 还有审核通过送的那 10 积分都变了，序也跟着状态分档挪（待审核那一档整批走空）。
 */
const batchAuditOpen = ref(false)
const batchAuditLoading = ref(false)
const batchAuditIds = ref<any[]>([])
const batchAuditFormRef = ref<FormInstance>()
const batchAuditForm = reactive<any>({ auditStatus: 1, auditRemark: undefined })

const batchAuditRules = reactive<FormRules>({
  auditStatus: [{ required: true, message: '请选择审核结果', trigger: 'change' }]
})

const handleBatchAudit = () => {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先勾选要审核的广场消息')
    return
  }
  batchAuditIds.value = [...selectedIds.value]
  batchAuditForm.auditStatus = 1
  batchAuditForm.auditRemark = undefined
  batchAuditFormRef.value?.clearValidate()
  batchAuditOpen.value = true
}

const submitBatchAudit = () => {
  batchAuditFormRef.value?.validate(async (valid: any) => {
    if (!valid) return
    batchAuditLoading.value = true
    try {
      await auditBatchShopPlazaApi({
        ids: batchAuditIds.value,
        auditStatus: batchAuditForm.auditStatus,
        auditRemark: batchAuditForm.auditRemark
      })
      const n = batchAuditIds.value.length
      ElMessage.success(batchAuditForm.auditStatus === 1 ? `已通过 ${n} 条，这些都已上广场` : `已拒绝 ${n} 条`)
      batchAuditOpen.value = false
      clearSelection()
      getList()
    } catch (error) {
    } finally {
      batchAuditLoading.value = false
    }
  })
}

/** 表单重置 */
const reset = () => {
  Object.assign(form, {
    id: undefined,
    userId: undefined,
    userName: undefined,
    isDeleted: 0,
    title: '',
    plazaImage: '',
    content: '',
    isTop: 0,
    isRecommend: 0,
    status: 1
  })
  contentReady.value = false
  formRef.value?.resetFields()
}

/** 取消按钮 */
const cancel = () => {
  open.value = false
  reset()
}

/**
 * 修改按钮操作：六格里正文只在详情那条 SQL 里（列表那一趟不取 content），所以点开这一屏要先捞一趟。
 * WangEditor 建实例那一次把初始内容读走，之后再换 modelValue 它不接 —— 所以编辑器挂 contentReady 上，
 * 内容到了才挂载（弹窗先开、里面盖一层 loading，不至于点了没反应）。
 */
const handleUpdate = async (row: any) => {
  reset()
  Object.assign(form, {
    id: row.id,
    userId: row.userId,
    userName: row.userName,
    isDeleted: row.isDeleted,
    title: row.title || '',
    plazaImage: row.plazaImage || '',
    isTop: row.isTop ?? 0,
    isRecommend: row.isRecommend ?? 0,
    status: row.status === 2 ? 2 : 1
  })
  open.value = true
  formLoading.value = true
  try {
    const { data } = await detailShopPlazaApi(row.id)
    if (data) {
      form.title = data.title || ''
      form.plazaImage = data.plazaImage || ''
      form.content = data.content || ''
      form.isTop = data.isTop ?? 0
      form.isRecommend = data.isRecommend ?? 0
      form.status = data.status === 2 ? 2 : 1
      form.isDeleted = data.isDeleted ?? 0
    }
    contentReady.value = true
  } catch (error) {
    // 详情没捞回来就别挂编辑器：那样铺出来的是个空白框，改了等于把正文清空，比不开更糟
    open.value = false
  } finally {
    formLoading.value = false
  }
}

const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    submitLoading.value = true
    try {
      await updateShopPlazaApi({
        id: form.id,
        title: form.title,
        plazaImage: form.plazaImage,
        content: form.content,
        isTop: form.isTop,
        isRecommend: form.isRecommend,
        status: form.status
      })
      ElMessage.success('修改成功')
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
  ElMessageBox.confirm(`确认删除这条广场消息吗？删除后广场上就翻不到它了。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopPlazaApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 条广场消息吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopPlazaApi(selectedIds.value)
    ElMessage.success('删除成功')
    clearSelection()
    getList()
  }).catch(() => { })
}

/**
 * 操作列按钮分发（TableMoreActions 只抛一个 command 事件，动作认 action.command.type，同门店培训那页）
 */
const handleActionCommand = (action: any) => {
  const { type, row } = action.command
  switch (type) {
    case 'audit':
      handleAudit(row)
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

/* 按钮那一格不等分（这一页没有日期框，直接跟在四个筛选项后面） */
.search-form :deep(.el-form-item.search-actions) {
  flex: 0 0 auto;
  margin-left: auto;
}

.relation-tag {
  margin-right: 6px;
}

.cell-empty {
  color: var(--el-text-color-secondary);
}

/* 列表那一格的缩略图（整组只铺第一张）：口径同商品页（类是各页 scoped 的，这里自带一份）；弹窗里那两格用 content-image */
.list-thumb {
  width: 50px;
  height: 50px;
  border-radius: 4px;
  cursor: zoom-in;
}

/* 正文那一块：文字给个能滚的框，图片横排一小图、点开走查看器 */
.content-text {
  margin-top: 12px;
  max-height: 220px;
  overflow-y: auto;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.content-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.content-image {
  width: 96px;
  height: 96px;
  border-radius: 4px;
  cursor: zoom-in;
}

.audit-head {
  margin-bottom: 4px;
}

.audit-hint {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

/* 批量审核弹窗顶上那句「将把选中的 N 条一并判成同一档」和下面的表单之间给一道间距 */
.batch-audit-tip {
  margin-bottom: 16px;
}

/* 修改弹窗里的正文：编辑器自带边框（同公告那页那层 wrapper），这一格要给满整行 */
.editor-wrap {
  width: 100%;
  border: 1px solid #ccc;
}

.editor-placeholder {
  width: 100%;
  min-height: 120px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

/* 「这一条已删除」那一句提示，跟在发布者后面，别再让人以为改完就回广场了 */
.deleted-hint {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.deleted-tag {
  margin-left: 6px;
}
</style>
