<template>
    <div class="app-container">

      <!-- 搜索表单 -->
      <div class="search-wrapper">
        <el-form :model="queryParams" ref="queryFormRef" inline>
          <el-form-item label="用户昵称" prop="nickname">
            <el-input v-model="queryParams.nickname" placeholder="请输入用户昵称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="评论类型" prop="commentType">
            <el-select v-model="queryParams.commentType" placeholder="评论类型" clearable @change="handleQuery">
              <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
                :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="评论内容" prop="content">
            <el-input v-model="queryParams.content" placeholder="请输入评论内容" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 操作按钮区域 -->
      <el-card class="box-card">
        <template #header>
          <div class="card-header">
            <ButtonGroup>
              <el-button
                v-permission="['sys:comment:delete']"
                type="danger"
                icon="Delete"
                :disabled="selectedIds.length === 0"
                @click="handleBatchDelete"
              >批量删除</el-button>
            </ButtonGroup>
          </div>
        </template>

        <!-- 数据表格 -->
        <el-table
          v-loading="loading"
          :data="commentList"
          style="width: 100%"
          row-key="id"
          :tree-props="{ children: 'children' }"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection"  width="55" align="center" />
          <el-table-column label="用户昵称" align="center" width="120" prop="nickname" show-overflow-tooltip />
          <el-table-column label="回复人昵称" align="center" width="120" prop="replyNickname" show-overflow-tooltip />
          <el-table-column label="评论类型" align="center" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="dict.tag(DICT_TYPE, row.commentType)">
                {{ dict.label(DICT_TYPE, row.commentType) }}
              </el-tag>
            </template>
          </el-table-column>
          <!-- 对象名称由后端按 comment_type 现联商品/门店/品牌/广场四张表带出来（广场那一档取消息标题，没填就是空），对象删了就是空（口径同收藏页） -->
          <el-table-column label="评论对象" align="center" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ row.businessName || `已删除对象 #${row.businessId}` }}</span>
            </template>
          </el-table-column>
          <el-table-column label="评论内容" width="300" align="center">
            <template #default="{ row }">
              <!-- 纯文本插值，不能用 v-html：评论是访客填的，存进去再当 HTML 画就是存储型 XSS。
                   正文里的 [图片](地址) 摘掉后剩下的当字画，图只放一张首图缩略图，整组递给查看器左右切换 -->
              <div class="comment-cell">
                <el-tooltip :content="commentText(row.content)" placement="top">
                  <span class="comment-cell__text">{{ commentText(row.content) }}</span>
                </el-tooltip>
                <el-tooltip v-if="row.imageUrls?.length" :content="`共 ${row.imageUrls.length} 张`" placement="top">
                  <el-image :src="row.imageUrls[0]" :preview-src-list="row.imageUrls" :initial-index="0"
                    preview-teleported fit="cover" class="list-thumb" />
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="点赞数" width="80" align="center" prop="likeCount" />
          <el-table-column label="创建时间" align="center" prop="createTime" width="180" />
          <el-table-column label="操作" align="center" width="280" fixed="right">
            <template #default="scope">
              <TableMoreActions
                :actions="[
                  {
                    label: '编辑',
                    icon: 'Edit',
                    hidden: !hasPermission('sys:comment:update'),
                    command: { type: 'edit', row: scope.row }
                  },
                  {
                    label: '删除',
                    type: 'danger',
                    icon: 'Delete',
                    hidden: !hasPermission('sys:comment:delete'),
                    command: { type: 'delete', row: scope.row }
                  },
                ]"
                @command="handleActionCommand"
              />
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页组件 -->
        <div class="pagination-container">
          <el-pagination
            v-model:current-page="queryParams.pageNum"
            v-model:page-size="queryParams.pageSize"
            :page-sizes="[10, 20, 30, 50]"
            :total="total"
            :background="true"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>

        <!-- 编辑对话框 -->
        <el-dialog v-model="openEditDialog" title="编辑评论" width="600px" append-to-body>
          <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="80px">
            <el-form-item label="评论内容">
              <!-- 图片不另开一格：地址写成 [图片](https://…) 混在正文里，前台就是按这一串拆出来画的 -->
              <el-input type="textarea" :rows="6" v-model="editForm.content"
                placeholder="正文纯文本，可含表情；图片写成 [图片](https://地址)" />
            </el-form-item>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="是否置顶">
                  <el-radio-group v-model="editForm.isStick">
                    <el-radio :label="0">否</el-radio>
                    <el-radio :label="1">是</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="点赞数">
                  <el-input-number v-model="editForm.likeCount" :min="0" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
          <template #footer>
            <div class="dialog-footer">
              <el-button @click="cancelEdit">取 消</el-button>
              <el-button type="primary" @click="submitEdit">确 定</el-button>
            </div>
          </template>
        </el-dialog>
      </el-card>
    </div>
  </template>

  <script setup lang="ts">
  import { ElMessage, ElMessageBox } from 'element-plus'
  import type { FormInstance } from 'element-plus'
  import { useRoute } from 'vue-router'
  import {
    getCommentListApi,
    deleteCommentApi,
    editCommentApi
  } from '@/api/message/comment'
  import TableMoreActions from '@/components/TableMoreActions/index.vue'
  import { useUserStore } from '@/store/modules/user'
  import { useDict } from '@/utils/dictCache'

  /**
   * 评论正文里的图片：以 `[图片](https://…)` 内嵌在同一格 content 上，不建列、不走 v-html。
   * 形状和判据（张数、地址）都在服务层那一份，这里只负责拆出来画；每次都新开正则实例（带 g 的有 lastIndex）。
   */
  const imageMark = () => /\[图片\]\(([^\s)]*)\)/g
  // 摘掉图片串之后剩下的就是人打的字（字数那道闸也按这一份算）
  const commentText = (content: any): string => String(content ?? '').replace(imageMark(), '')
  // 地址不是 http(s) 开头的不当图（后台手工编辑正文时敲错的那一串照原文当字画）
  const commentImageUrls = (content: any): string[] => {
    const text = String(content ?? '')
    const mark = imageMark()
    const urls: string[] = []
    let hit = mark.exec(text)
    while (hit) {
      const url = hit[1] ?? ''
      if (/^https?:\/\//i.test(url)) {
        urls.push(url)
      }
      hit = mark.exec(text)
    }
    return urls
  }

  // 评论类型的四档（1商品 2门店 3品牌 4广场）只在这张表上出现，页面上的文案一律吃字典，不写死
  const DICT_TYPE = 'comment_type'
  const dict = useDict(DICT_TYPE)

  const route = useRoute()
  const queryFormRef = ref<FormInstance>()

  const userStore = useUserStore()
  const permissions = computed(() => userStore.user.permissions || [])

  const hasPermission = (permission: string): boolean => {
    return permissions.value.includes(permission)
  }

  const handleActionCommand = async (action: any) => {
    const { type, row } = action.command

    switch (type) {
      case 'edit':
        handleEdit(row)
        break
      case 'delete':
        handleDelete(row)
        break
    }
  }

  // 查询参数
  const queryParams = reactive({
    pageNum: 1,
    pageSize: 10,
    businessId: undefined as number | undefined,
    commentType: undefined as number | undefined,
    nickname: '',
    content: ''
  })

  const loading = ref(false)
  const total = ref(0)
  const commentList = ref<any[]>([])

  const selectedIds = ref<string[]>([])

  // 图片那一格在模板里要用两处（首图 + 整组递给查看器），逐层算一次挂在行上，别在模板里反复拆串；
  // 后端把回复挂进 children 铺成一棵树，所以子行也要算，只算顶层的话子行那一格永远画不出图
  const withCommentImages = (rows: any[]): any[] => (rows || []).map((row: any) => ({
    ...row,
    imageUrls: commentImageUrls(row.content),
    children: withCommentImages(row.children)
  }))

  const getList = async () => {
    loading.value = true
    try {
      const { data } = await getCommentListApi(queryParams)
      commentList.value = withCommentImages(data.records)
      total.value = data.total
    } catch (error) {
    }
    loading.value = false
  }

  const handleSelectionChange = (selection: any[]) => {
    selectedIds.value = selection.map(item => item.id)
  }

  const handleBatchDelete = () => {
    if (selectedIds.value.length === 0) return

    ElMessageBox.confirm(`是否确认删除 ${selectedIds.value.length} 个评论?`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(async () => {
      try {
        await deleteCommentApi(selectedIds.value)
        ElMessage.success('批量删除成功')
        getList()
        selectedIds.value = []
      } catch (error) {
      }
    })
  }

  const handleDelete = (row: any) => {
    ElMessageBox.confirm(`是否确认删除 ${row.nickname} 的评论?`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(async () => {
      try {
        await deleteCommentApi(row.id)
        ElMessage.success('删除成功')
        getList()
      } catch (error) {
      }
    })
  }

  const handleQuery = () => {
    queryParams.pageNum = 1
    getList()
  }

  const resetQuery = () => {
    queryFormRef.value?.resetFields()
    queryParams.businessId = undefined
    queryParams.nickname = ''
    queryParams.content = ''
    handleQuery()
  }

  const handleSizeChange = (val: number) => {
    queryParams.pageSize = val
    getList()
  }

  const handleCurrentChange = (val: number) => {
    queryParams.pageNum = val
    getList()
  }

  onMounted(() => {
    const { businessId: id } = route.query
    if (id) {
      queryParams.businessId = Number(id)
    }
    getList()
  })

  const openEditDialog = ref(false)
  const editFormRef = ref<FormInstance>()
  const editForm = reactive({
    id: undefined as number | undefined,
    content: '',
    replyUserId: undefined as number | undefined,
    isStick: 0,
    likeCount: 0
  })

  const editRules = reactive({
    content: [
      { required: true, message: '评论内容不能为空', trigger: 'blur' }
    ]
  })

  const handleEdit = (row: any) => {
    editForm.id = row.id
    editForm.content = row.content || ''
    editForm.replyUserId = row.replyUserId
    editForm.isStick = row.isStick || 0
    editForm.likeCount = row.likeCount ?? 0
    openEditDialog.value = true
  }

  const submitEdit = async () => {
    if (!editFormRef.value) return
    await editFormRef.value.validate(async (valid) => {
      if (valid) {
        try {
          await editCommentApi(editForm)
          ElMessage.success('编辑成功')
          openEditDialog.value = false
          getList()
        } catch (error) {
          ElMessage.error('编辑失败')
        }
      }
    })
  }

  const cancelEdit = () => {
    openEditDialog.value = false
    editForm.id = undefined
    editForm.content = ''
    editForm.replyUserId = undefined
    editForm.isStick = 0
    editForm.likeCount = 0
    editFormRef.value?.clearValidate()
  }
  </script>

<style scoped lang="scss">
/* 评论内容那一格：文字一行截断（原来整列靠 show-overflow-tooltip，现在格子里多了缩略图，只能自己排），
   首图跟在文字后面，完整正文由包着文字的那颗 tooltip 给 */
.comment-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.comment-cell__text {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* 列表缩略图：口径同商品页（类是各页 scoped 的，这里自带一份） */
.list-thumb {
  width: 50px;
  height: 50px;
  border-radius: 4px;
  cursor: zoom-in;
  flex-shrink: 0;
}
</style>
