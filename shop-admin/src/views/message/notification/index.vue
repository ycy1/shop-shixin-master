<template>
    <div class="app-container">
        <!-- 搜索表单 -->
        <div class="search-wrapper">
            <el-form :model="queryParams" ref="queryFormRef" inline>
                <el-form-item label="标题" prop="title">
                    <el-input v-model="queryParams.title" placeholder="请输入标题" clearable style="width: 200px"
                        @keyup.enter="handleQuery" @clear="handleQuery" />
                </el-form-item>
                <el-form-item label="发送人" prop="fromNickname">
                    <el-input v-model="queryParams.fromNickname" placeholder="请输入发送人" clearable style="width: 200px"
                        @keyup.enter="handleQuery" @clear="handleQuery" />
                </el-form-item>
                <el-form-item label="业务类型" prop="businessType">
                    <el-select v-model="queryParams.businessType" placeholder="请选择业务类型" clearable style="width: 200px">
                        <el-option v-for="item in businessTypeOptions" :key="item.value" :label="item.label"
                            :value="item.value" />
                    </el-select>
                </el-form-item>
                <el-form-item label="删除标记" prop="delFlag">
                    <el-select v-model="queryParams.delFlag" placeholder="请选择删除标记" clearable style="width: 140px">
                        <el-option label="未删除" :value="0" />
                        <el-option label="已删除" :value="1" />
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
                <el-button type="danger" v-permission="['sys:notification:delete']" icon="Delete"
                    :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除
                </el-button>
            </template>

            <!-- 数据表格 -->
            <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange">
                <el-table-column type="selection" width="55" align="center" />
                <el-table-column label="标题" align="left" prop="title" show-overflow-tooltip width="250" />
                <el-table-column label="发送人" align="center" prop="fromNickname" width="130">
                    <template #default="scope">
                        <span>{{ scope.row.fromNickname || '-' }}</span>
                    </template>
                </el-table-column>
                <el-table-column label="业务类型" align="center" prop="businessType" width="110">
                    <template #default="scope">
                        <el-tag :type="getTypeTag(scope.row.businessType)">{{ getTypeLabel(scope.row.businessType) }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="阅读数" align="center" prop="readCount" width="90">
                    <template #default="scope">
                        <el-link type="primary" :underline="false" @click="openRecords(scope.row)">
                            {{ scope.row.readCount ?? 0 }}
                        </el-link>
                    </template>
                </el-table-column>
                <el-table-column label="删除标记" align="center" prop="delFlag" width="100">
                    <template #default="scope">
                        <el-tag :type="scope.row.delFlag === 1 ? 'danger' : 'success'">
                            {{ scope.row.delFlag === 1 ? '已删除' : '未删除' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="创建时间" align="center" prop="createTime" width="180">
                    <template #default="scope">
                        <span>{{ validate.formatTime(scope.row.createTime, 'YYYY-MM-DD') || '-' }}</span>
                    </template>
                </el-table-column>
                <el-table-column label="操作" align="center" fixed="right">
                    <template #default="scope">
                        <el-button type="info" link icon="Reading" @click="openRecords(scope.row)">阅读记录
                        </el-button>
                        <el-button type="primary" link icon="Edit" v-permission="['sys:notification:update']"
                            @click="handleEdit(scope.row)">修改
                        </el-button>
                        <el-button v-if="scope.row.delFlag === 0" type="danger" link icon="Delete" v-permission="['sys:notification:delete']"
                            @click="handleDelete(scope.row)">删除
                        </el-button>
                    </template>
                </el-table-column>
            </el-table>

            <!-- 分页工具栏 -->
            <div class="pagination-container">
                <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
                    :page-sizes="[10, 20, 30, 50]" :total="total" background
                    layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange"
                    @current-change="handleCurrentChange" />
            </div>
        </el-card>

        <!-- 编辑对话框 -->
        <el-dialog v-model="editOpen" :title="editTitle" width="1000px" append-to-body destroy-on-close>
            <el-form :model="editForm" label-width="90px">
                <el-form-item label="标题" prop="title">
                    <el-input v-model="editForm.title" placeholder="请输入标题" />
                </el-form-item>
                <el-form-item label="消息内容" prop="message">
                    <div style="border: 1px solid #ccc; width: 100%">
                        <WangEditor v-model="editForm.message" />
                    </div>
                </el-form-item>
                <el-form-item label="删除标记" prop="delFlag">
                    <el-select v-model="editForm.delFlag" style="width: 100%">
                        <el-option label="未删除" :value="0" />
                        <el-option label="已删除" :value="1" />
                    </el-select>
                </el-form-item>
                <el-form-item label="接收对象" prop="noticePush">
                    <NoticePushTransfer v-model="editForm.noticePush" />
                </el-form-item>
            </el-form>
            <template #footer>
                <div class="dialog-footer">
                    <el-button type="warning" @click="submitEdit('send')">发 送</el-button>
                    <el-button type="primary" @click="submitEdit('save')">确 定</el-button>
                    <el-button @click="editOpen = false">取 消</el-button>
                </div>
            </template>
        </el-dialog>

        <!-- 阅读记录对话框 -->
        <el-dialog v-model="recordOpen" :title="recordTitle" width="880px" append-to-body>
            <div class="record-toolbar">
                <el-input v-model="recordQuery.keyword" placeholder="按阅读人（昵称/用户名）查询" clearable style="width: 220px"
                    @keyup.enter="handleRecordQuery" @clear="handleRecordQuery">
                    <template #prefix>
                        <el-icon><Search /></el-icon>
                    </template>
                </el-input>
                <el-button type="primary" size="small" icon="Search" @click="handleRecordQuery">搜索</el-button>
                <el-button type="success" size="small" icon="Check" :disabled="recordSelectedIds.length === 0"
                    @click="handleRecordBatchRead">批量已读</el-button>
                <el-button type="danger" size="small" icon="Delete" :disabled="recordSelectedIds.length === 0"
                    @click="handleRecordBatchDelete">批量删除</el-button>
            </div>
            <el-table v-loading="recordLoading" :data="recordList" @selection-change="handleRecordSelectionChange">
                <el-table-column type="selection" width="50" align="center" />
                <el-table-column label="阅读人" align="center" width="150">
                    <template #default="scope">
                        <span>{{ scope.row.nickname || scope.row.username || scope.row.userId || '-' }}</span>
                    </template>
                </el-table-column>
                <el-table-column label="是否读" align="center" width="90">
                    <template #default="scope">
                        <el-tag :type="scope.row.isRead === 1 ? 'success' : 'warning'">
                            {{ scope.row.isRead === 1 ? '已读' : '未读' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="阅读时间" align="center" width="180">
                    <template #default="scope">
                        <span>{{ scope.row.readTime ? validate.formatTime(scope.row.readTime, 'YYYY-MM-DD HH:mm:ss') : '-' }}</span>
                    </template>
                </el-table-column>
                <el-table-column label="是否删除" align="center" width="100">
                    <template #default="scope">
                        <el-tag :type="scope.row.isDeleted === 1 ? 'danger' : 'success'">
                            {{ scope.row.isDeleted === 1 ? '已删除' : '未删除' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" align="center">
                    <template #default="scope">
                        <el-button type="primary" link icon="Check" @click="handleRecordRead(scope.row)">已读</el-button>
                        <el-button type="danger" link icon="Delete" @click="handleRecordDelete(scope.row)">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>
            <div class="record-pagination">
                <el-pagination v-model:current-page="recordQuery.pageNum" v-model:page-size="recordQuery.pageSize"
                    :page-sizes="[10, 20, 50]" :total="recordTotal" background
                    layout="total, sizes, prev, pager, next, jumper" @size-change="handleRecordSizeChange"
                    @current-change="handleRecordCurrentChange" />
            </div>
            <template #footer>
                <el-button @click="recordOpen = false">关 闭</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import {
    getNotificationListApi,
    updateNotificationApi,
    deleteNotificationApi,
    getNotificationReceiverListApi,
    updateNotificationReceiverReadApi,
    deleteNotificationReceiverApi,
} from '@/api/message/notification'
import WangEditor from '@/components/WangEditor/index.vue'
import NoticePushTransfer from '@/components/NoticePushTransfer/index.vue'
import validate from '@/utils/validate'

// 查询参数
const queryParams = reactive({
    pageNum: 1,
    pageSize: 10,
    title: undefined,
    fromNickname: undefined,
    businessType: undefined,
    delFlag: undefined,
})

// 业务类型选项
const businessTypeOptions = [
    { label: '留言', value: 'note' },
    { label: '公告', value: 'notice' },
    { label: '反馈', value: 'feedback' },
]

const loading = ref(false)
const total = ref(0)
const dataList = ref<any[]>([])
const selectedIds = ref<any[]>([])
const queryFormRef = ref()

// 编辑弹窗
const editOpen = ref(false)
const editTitle = ref('')
const editForm = reactive<any>({})

// 阅读记录弹窗
const recordOpen = ref(false)
const recordTitle = ref('')
const recordLoading = ref(false)
const recordList = ref<any[]>([])
const recordTotal = ref(0)
const recordSelectedIds = ref<any[]>([])
const recordNotificationId = ref<number>()
const recordQuery = reactive({
    pageNum: 1,
    pageSize: 10,
    keyword: undefined,
})

// 获取消息通知列表
const getList = async () => {
    loading.value = true
    try {
        const { data } = await getNotificationListApi(queryParams)
        dataList.value = data.records
        total.value = data.total
    } catch (error) {
    }
    loading.value = false
}

// 表格选择项变化
const handleSelectionChange = (selection: any[]) => {
    selectedIds.value = selection.map(item => item.id)
}

// 搜索
const handleQuery = () => {
    queryParams.pageNum = 1
    getList()
}

// 重置
const resetQuery = () => {
    queryFormRef.value?.resetFields()
    handleQuery()
}

// 打开编辑
const handleEdit = (row: any) => {
    Object.assign(editForm, row)
    editForm.send = false
    editOpen.value = true
    editTitle.value = '修改消息通知'
}

// 提交编辑（save=仅保存，send=重新发送，后端会生成新的 send_code 并清空旧的接收记录）
const submitEdit = async (mode: 'save' | 'send') => {
    if (!editForm.id) return
    if (mode === 'send') {
        try {
            await ElMessageBox.confirm('再次发送会取消之前发送记录，是否继续？', '提示', {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning',
            })
        } catch {
            return
        }
    }
    editForm.send = mode === 'send'
    try {
        await updateNotificationApi(editForm)
        ElMessage.success(mode === 'send' ? '发送成功' : '修改成功')
        editOpen.value = false
        getList()
    } catch (error) {
    } finally {
        editForm.send = undefined
    }
}

// 批量删除（逻辑删除）
const handleBatchDelete = () => {
    if (selectedIds.value.length === 0) return
    ElMessageBox.confirm(`是否确认删除 ${selectedIds.value.length} 条消息通知?`, '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
    }).then(async () => {
        try {
            await deleteNotificationApi(selectedIds.value)
            ElMessage.success('批量删除成功')
            selectedIds.value = []
            getList()
        } catch (error) {
        }
    })
}

// 删除（逻辑删除）
const handleDelete = (row: any) => {
    ElMessageBox.confirm('是否确认删除该条消息通知?', '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
    }).then(async () => {
        try {
            await deleteNotificationApi(row.id)
            ElMessage.success('删除成功')
            getList()
        } catch (error) {
        }
    })
}

// 分页大小改变
const handleSizeChange = (val: number) => {
    queryParams.pageSize = val
    getList()
}

// 页码改变
const handleCurrentChange = (val: number) => {
    queryParams.pageNum = val
    getList()
}

// 打开阅读记录弹窗
const openRecords = (row: any) => {
    recordNotificationId.value = row.id
    recordTitle.value = `阅读记录 - ${row.title || row.id}`
    recordQuery.pageNum = 1
    recordQuery.keyword = undefined
    recordSelectedIds.value = []
    recordOpen.value = true
    loadRecords()
}

// 加载阅读记录（分页）
const loadRecords = async () => {
    if (recordNotificationId.value === undefined) return
    recordLoading.value = true
    try {
        const { data } = await getNotificationReceiverListApi(recordNotificationId.value, recordQuery)
        recordList.value = data?.records || []
        recordTotal.value = data?.total || 0
    } catch (error) {
    }
    recordLoading.value = false
}

// 按阅读人搜索
const handleRecordQuery = () => {
    recordQuery.pageNum = 1
    loadRecords()
}

// 阅读记录分页大小改变
const handleRecordSizeChange = (val: number) => {
    recordQuery.pageSize = val
    loadRecords()
}

// 阅读记录页码改变
const handleRecordCurrentChange = (val: number) => {
    recordQuery.pageNum = val
    loadRecords()
}

// 阅读记录多选
const handleRecordSelectionChange = (selection: any[]) => {
    recordSelectedIds.value = selection.map(item => item.id)
}

// 单条标记已读
const handleRecordRead = async (row: any) => {
    try {
        await updateNotificationReceiverReadApi([row.id])
        ElMessage.success('操作成功')
        loadRecords()
    } catch (error) {
    }
}

// 单条删除（该接收人隐藏消息）
const handleRecordDelete = (row: any) => {
    ElMessageBox.confirm('确认删除该条阅读记录？该接收人将隐藏此消息。', '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
    }).then(async () => {
        try {
            await deleteNotificationReceiverApi([row.id])
            ElMessage.success('删除成功')
            loadRecords()
        } catch (error) {
        }
    })
}

// 批量已读
const handleRecordBatchRead = async () => {
    if (recordSelectedIds.value.length === 0) return
    try {
        await updateNotificationReceiverReadApi(recordSelectedIds.value)
        ElMessage.success('批量已读成功')
        loadRecords()
    } catch (error) {
    }
}

// 批量删除
const handleRecordBatchDelete = () => {
    if (recordSelectedIds.value.length === 0) return
    ElMessageBox.confirm(`确认删除选中的 ${recordSelectedIds.value.length} 条阅读记录？`, '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
    }).then(async () => {
        try {
            await deleteNotificationReceiverApi(recordSelectedIds.value)
            ElMessage.success('批量删除成功')
            recordSelectedIds.value = []
            loadRecords()
        } catch (error) {
        }
    })
}

// 业务类型标签文案
function getTypeLabel(type: string): string {
    const item = businessTypeOptions.find(o => o.value === type)
    return item ? item.label : (type || '-')
}

// 业务类型标签颜色
function getTypeTag(type: string): string {
    const map: Record<string, string> = {
        note: 'success',
        notice: 'info',
        feedback: 'danger',
    }
    return map[type] || 'info'
}

onMounted(() => {
    getList()
})
</script>

<style scoped lang="scss">
.record-toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
}

.record-pagination {
    margin-top: 10px;
    display: flex;
    justify-content: flex-end;
}
</style>
