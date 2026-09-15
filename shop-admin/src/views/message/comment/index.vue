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
              <el-option label="全部" :value="undefined" />
              <el-option label="类型1" :value="1" />
              <el-option label="类型2" :value="2" />
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
            <template #default="scope">
              <el-tag :type="scope.row.commentType === 2 ? 'success' : 'primary'" size="small">
                {{ scope.row.commentType === 2 ? '类型2' : '类型1' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="评论内容" width="300" align="center" prop="content" show-overflow-tooltip>
            <template #default="scope">
                <span v-html="scope.row.content"></span>
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
                    command: { type: 'edit', row: scope.row }
                  },
                  {
                    label: '删除',
                    type: 'danger',
                    icon: 'Delete',
                    disabled: !hasPermission('sys:comment:delete'),
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
              <el-input type="textarea" :rows="6" v-model="editForm.content" placeholder="请输入评论内容" />
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
  const commentList = ref([])

  const selectedIds = ref<string[]>([])

  const getList = async () => {
    loading.value = true
    try {
      const { data } = await getCommentListApi(queryParams)
      commentList.value = data.records
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
