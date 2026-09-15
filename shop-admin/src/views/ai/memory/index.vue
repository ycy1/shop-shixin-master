<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" :inline="true">
        <el-form-item label="会话ID" prop="conversationId">
          <el-input v-model="queryParams.conversationId" placeholder="请输入会话ID" clearable style="width:200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span style="font-weight:600">消息记录列表</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" style="width:100%" :max-height="600">
        <el-table-column label="ID" align="center" prop="id" width="70" />
        <el-table-column label="会话ID" align="center" prop="conversationId" width="90" />
        <el-table-column label="角色" align="center" width="90">
          <template #default="{ row }">
            <el-tag :type="row.role === 'user' ? 'primary' : row.role === 'assistant' ? 'success' : 'info'" size="small">
              {{ row.role === 'user' ? '用户' : row.role === 'assistant' ? 'AI' : row.role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="内容" align="center" min-width="400" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="content-cell" v-html="renderMarkdown(row.content)"></div>
          </template>
        </el-table-column>
        <el-table-column label="轮次" align="center" prop="turnNumber" width="70" />
        <el-table-column label="Token" align="center" prop="tokensUsed" width="80" />
        <el-table-column label="创建时间" align="center" prop="createTime" width="180" />
        <el-table-column label="操作" align="center" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
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
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMemoryListApi, deleteMemoryApi } from '@/api/ai/memory'
import { renderMarkdown } from '@/utils/markdown'

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  conversationId: null as string | null
})
const loading = ref(false)
const total = ref(0)
const tableData = ref<any[]>([])
const queryFormRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const params: any = { pageNum: queryParams.pageNum, pageSize: queryParams.pageSize }
    if (queryParams.conversationId) {
      params.conversationId = queryParams.conversationId
    }
    const { data } = await getMemoryListApi(params)
    tableData.value = data.records
    total.value = data.total
  } catch (_) {}
  loading.value = false
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const resetQuery = () => {
  queryParams.conversationId = null
  queryParams.pageNum = 1
  getList()
}
const handleSizeChange = (val: number) => { queryParams.pageSize = val; getList() }
const handleCurrentChange = (val: number) => { queryParams.pageNum = val; getList() }

const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确定删除该消息记录?`, '警告', {
    type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'
  }).then(async () => {
    await deleteMemoryApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => {})
}

onMounted(() => { getList() })
</script>

<style lang="scss" scoped>
.app-container {
  .pagination-container {
    display: flex;
    justify-content: center;
    margin-top: 20px;
  }
  .content-cell {
    font-size: 13px;
    line-height: 1.5;
    text-align: left;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
    p { margin: 0; }
  }
}
</style>
